# Test run: deploying the chart on a throwaway Hetzner server

A runbook you execute yourself. Create a server, install k3s and the operators,
deploy the chart, confirm the parts that have never run, destroy everything.

Scope is a **smoke test**: no DNS, no TLS, no OIDC login. The app is reached
through `kubectl port-forward`, which keeps cert-manager, a DNS record and an
identity provider out of the run — none of them exercise chart logic.

Hetzner bills hourly with a monthly cap, so a few hours costs a small fraction of
the monthly price. Expect roughly 1–2 hours, most of it waiting on the model pull.

For a real deployment — DNS, TLS, an OIDC provider, a cluster you keep — see
`k3s-setup.md` in this directory. This runbook deliberately skips all of that.

---

## What this run is for

The chart passes `helm lint` and `helm template` across every value combination,
but **nothing in it has ever run**. These are the specific things only a live
cluster can settle, hardest-to-guess first:

| # | Unknown | Settled in |
| --- | --- | --- |
| 1 | Non-root `podSecurityContext` (UID 1000 + `fsGroup`) on garage and ollama — added blind | Stage 2 + 3 |
| 2 | Garage bootstrap script: flags read from `main-v2` source but run against pinned `v2.1.0`, the `layout show` parsing, the `info` exit-code guards, `json-api ImportKey -` over stdin | Stage 4 |
| 3 | Ollama's relocated `HOME=/models` and the init-container model pull | Stage 3 |
| 4 | CNPG really produces `<cluster>-app` with `username`/`password` | Stage 3 |
| 5 | `automountServiceAccountToken: false` breaks nothing | Stage 3 |
| 6 | Probes: gotenberg `/health`, ollama `/`, app `/index.html` | Stage 3 |
| 7 | NetworkPolicy actually enforced by k3s's kube-router | Stage 5 |

---

## Stage 0 — local tooling

`kubectl`, `helm`, `ssh` and `openssl` are already installed. `hcloud` is not:

```bash
curl -sSLO https://github.com/hetznercloud/cli/releases/latest/download/hcloud-linux-amd64.tar.gz
tar xzf hcloud-linux-amd64.tar.gz hcloud && sudo install -m 0755 hcloud /usr/local/bin/ && rm hcloud*
hcloud context create ajm-test      # paste a read+write API token
```

Create the token in the Hetzner Console first (Security → API tokens, read+write).

---

## Stage 1 — server and k3s

**CPX41** (8 vCPU / 16 GB) — everything fits with headroom and the run finishes
sooner. CPX31 (4 / 8) works too, but only with `postgres.instances=1` and the 3b
model, and it will be tight.

```bash
hcloud ssh-key create --name ajm-test --public-key-from-file ~/.ssh/id_ed25519.pub
hcloud server create --name ajm-test --type cpx41 --image ubuntu-24.04 --ssh-key ajm-test

IP=$(hcloud server ip ajm-test)
echo "$IP"

ssh root@$IP 'curl -sfL https://get.k3s.io | \
  INSTALL_K3S_EXEC="--secrets-encryption --write-kubeconfig-mode 600" sh -'
```

Reach the API through an SSH tunnel instead of opening 6443 — no firewall rules,
and no `--tls-san` needed since the certificate already covers `127.0.0.1`:

```bash
ssh root@$IP 'cat /etc/rancher/k3s/k3s.yaml' | sed 's/default/ajm-test/g' > ~/.kube/ajm-test.yaml
chmod 600 ~/.kube/ajm-test.yaml

ssh -f -N -L 6443:127.0.0.1:6443 root@$IP     # backgrounded; killed at teardown

export KUBECONFIG=$HOME/.kube/ajm-test.yaml
kubectl get nodes -o wide
```

✅ **Expect:** one node, `Ready`.

---

## Stage 2 — operators, and prove storage before the chart

cert-manager is **not** needed here (ingress stays disabled).

```bash
kubectl -n kube-system create secret generic hcloud --from-literal=token=<API_TOKEN>

helm repo add hcloud https://charts.hetzner.cloud && helm repo update hcloud
helm install hcloud-csi hcloud/hcloud-csi -n kube-system --version 2.23.0

kubectl apply --server-side -f \
  https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.30/releases/cnpg-1.30.0.yaml
kubectl -n cnpg-system rollout status deployment/cnpg-controller-manager

kubectl get storageclass         # hcloud-volumes, marked default
```

### Settle unknown 1 cheaply, before the chart

A throwaway pod with the exact security context the chart uses. If this fails,
garage and ollama would both crash-loop — far easier to see here:

```bash
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: PersistentVolumeClaim
metadata: { name: fsgroup-probe }
spec:
  accessModes: ["ReadWriteOnce"]
  storageClassName: hcloud-volumes
  resources: { requests: { storage: 10Gi } }
---
apiVersion: v1
kind: Pod
metadata: { name: fsgroup-probe }
spec:
  securityContext: { runAsNonRoot: true, runAsUser: 1000, runAsGroup: 1000, fsGroup: 1000 }
  restartPolicy: Never
  containers:
    - name: p
      image: busybox
      command: ["sh","-c","touch /data/ok && ls -ln /data"]
      volumeMounts: [{ name: d, mountPath: /data }]
  volumes:
    - name: d
      persistentVolumeClaim: { claimName: fsgroup-probe }
EOF

kubectl logs fsgroup-probe
kubectl delete pod/fsgroup-probe pvc/fsgroup-probe
```

✅ **Expect:** a file listed as owned by `1000 1000`.
❌ **If it fails:** the chart needs `--set storage.garage.podSecurityContext=null
--set llm.ollama.podSecurityContext=null`, and that finding goes back into the chart.

> `hcloud-volumes` uses `WaitForFirstConsumer`, so a PVC sitting `Pending` until
> its pod is scheduled is normal, not a fault.

---

## Stage 3 — deploy with the bootstrap hook OFF

Deliberately separating the two big unknowns. A failed post-install hook leaves
the Helm release in `failed` state, which makes the next `helm upgrade` awkward —
so bring the pods up first, drive Garage by hand, and only then let the hook try.

```bash
kubectl create namespace job-manager
kubectl -n job-manager create secret generic ajm-oidc --from-literal=client-secret=dummy

helm install ajm deploy/helm/accessible-job-manager -n job-manager \
  --set ajm.oidc.existingSecret=ajm-oidc \
  --set ajm.ingress.enabled=false \
  --set postgres.instances=1 \
  --set llm.ollama.model=qwen2.5:3b \
  --set storage.garage.bootstrap.enabled=false

kubectl -n job-manager get pods -w
```

`client-secret=dummy` is fine — it is only read when someone attempts a login.

✅ **Expect,** after the model pull finishes:

```bash
kubectl -n job-manager get pods          # all Running, all Ready
kubectl -n job-manager get pvc           # all Bound
kubectl -n job-manager get cluster       # CNPG healthy, 1 instance
```

Notes while waiting:

- Ollama sits in `Init` for minutes pulling ~2 GB: `kubectl -n job-manager logs
  <ollama-pod> -c pull-model` shows progress. This settles unknown 3.
- The app may restart a few times until Postgres accepts connections. That is
  expected; it must settle into `Ready`.
- `CrashLoopBackOff` on garage or ollama with permission errors ⇒ unknown 1,
  see the override in Stage 2.

Settles unknowns 1, 3, 4, 5 and 6.

---

## Stage 4 — drive the Garage bootstrap by hand

Run the script's commands one at a time so a wrong flag surfaces immediately,
rather than inside a Job that dies mid-sequence. This is unknown 2.

```bash
POD=ajm-accessible-job-manager-garage-0
G="kubectl -n job-manager exec $POD -c garage -- /garage"

$G status
$G layout show
```

✅ **Check `layout show` prints the two strings the script greps for:**
`No nodes currently have a role` and `Current cluster layout version: 0`.

```bash
NODE_ID=$($G node id -q | cut -d@ -f1); echo "$NODE_ID"
$G layout assign -z dc1 -c 20GB "$NODE_ID"
$G layout apply --version 1

$G bucket create documents
$G bucket info documents  >/dev/null; echo "bucket info exit=$?   # want 0"
$G key info nosuchkey     >/dev/null 2>&1; echo "key info exit=$?  # want non-zero"
```

Those two exit codes are what the script's idempotence guards depend on.

```bash
AK=$(kubectl -n job-manager get secret ajm-accessible-job-manager-s3 -o jsonpath='{.data.access-key}' | base64 -d)
SK=$(kubectl -n job-manager get secret ajm-accessible-job-manager-s3 -o jsonpath='{.data.secret-key}' | base64 -d)

printf '{"accessKeyId":"%s","secretAccessKey":"%s","name":"%s"}' "$AK" "$SK" accessible-job-manager \
  | kubectl -n job-manager exec -i $POD -c garage -- /garage json-api ImportKey -

$G bucket allow --read --write --owner documents --key accessible-job-manager
$G key info accessible-job-manager
```

Fix `templates/garage/bootstrap-job.yaml` for whatever differed, then prove the
automated path — and that a re-run is a genuine no-op over the state you just
created by hand:

```bash
helm upgrade ajm deploy/helm/accessible-job-manager -n job-manager \
  --reuse-values --set storage.garage.bootstrap.enabled=true

kubectl -n job-manager logs job/ajm-accessible-job-manager-garage-bootstrap
helm upgrade ajm deploy/helm/accessible-job-manager -n job-manager --reuse-values
```

✅ **Expect** the second run to report "already exists" / "already holds a role"
at every step and exit 0.

---

## Stage 5 — NetworkPolicy and app smoke

The gotenberg image runs non-root and may carry no HTTP client, so test the policy
from a pod wearing the same labels — which is what makes the policy apply to it:

```bash
kubectl -n job-manager run np-probe --rm -it --restart=Never --image=busybox \
  --labels="app.kubernetes.io/name=accessible-job-manager,app.kubernetes.io/instance=ajm,app.kubernetes.io/component=gotenberg" \
  -- sh -c '
    wget -T4 -qO- http://ajm-accessible-job-manager-garage:3900 >/dev/null 2>&1; echo "garage=$?   want non-zero"
    wget -T4 -qO- https://example.com >/dev/null 2>&1;                          echo "internet=$? want 0"
    nslookup kubernetes.default >/dev/null 2>&1;                                echo "dns=$?      want 0"'
```

✅ **Expect:** garage unreachable, internet reachable, DNS working. That is
unknown 7, and it is the deployment requirement `Readme.md` calls non-negotiable.

Then the app:

```bash
helm test ajm -n job-manager

kubectl -n job-manager logs deploy/ajm-accessible-job-manager \
  | grep -iE "error|exception|s3|garage|jdbc" | head -20

kubectl -n job-manager port-forward deploy/ajm-accessible-job-manager 8060:8060 &
curl -I http://127.0.0.1:8060/index.html
```

✅ **Expect:** `helm test` passes, `curl` returns 200, and the logs show **no** S3
or JDBC errors. Clean startup is the real signal that Garage, CNPG and the
generated credentials all line up.

---

## Stage 6 — teardown, in this order

**Deleting the server does not delete the volumes.** `hcloud-volumes` is
`reclaimPolicy: Delete`, so removing the PVCs removes the paid volumes. Skip that
and they keep billing, attached to nothing.

```bash
helm uninstall ajm -n job-manager
kubectl -n job-manager delete pvc --all
kubectl get pv                    # expect none

hcloud volume list                # MUST be empty before the next line
hcloud server delete ajm-test
hcloud ssh-key delete ajm-test

pkill -f 'ssh -f -N -L 6443'
```

Optional, if several attempts look likely: after Stage 2, run
`hcloud server create-image --type snapshot --description ajm-operators ajm-test`.
A retry then restores a server that already has the operators installed.

---

## Result checklist

- [ ] 1 — `fsgroup-probe` wrote its file as UID 1000 (Stage 2)
- [ ] 1 — garage and ollama reached `Ready` non-root (Stage 3)
- [ ] 3 — ollama pulled the model into `/models` and serves `/`
- [ ] 4 — CNPG `-app` secret bound; no JDBC errors in the app log
- [ ] 5 — no pod broke with its service-account token removed
- [ ] 6 — all probes green; `helm test` passes
- [ ] 2 — every Garage command matched the script; hook run and re-run clean
- [ ] 7 — gotenberg cannot reach garage, can reach the internet

## What this run will not prove

Ingress, Let's Encrypt TLS and OIDC login (excluded by choice); SSE-C, which needs
a real document upload and therefore a login; and any multi-node Garage layout.
Those belong to the full deployment in `deploy/helm/k3s-setup.md`.

## Afterwards

Fold the results back into `deploy/helm/k3s-setup.md` — especially any
troubleshooting row that proved wrong — and, if the bootstrap needed edits, update
the verified command sequence in `templates/garage/bootstrap-job.yaml`.
