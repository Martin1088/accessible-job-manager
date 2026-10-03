# Setting up k3s on Hetzner and deploying the chart

End-to-end: provision a node, install k3s, install the three operators the chart
depends on, create the secrets it requires, deploy, and verify. The kubeconfig
part applies to any cluster (EKS, AKS, kind, minikube); only where it comes from
differs.

Versions below were current when this was written — check for newer ones rather
than pasting blindly.

> Trying the chart for the first time? Follow `hetzner-test-run.md` instead: a
> throwaway server, a smoke test with no DNS/TLS/OIDC, and teardown. This
> document is the real deployment.

## 0. What the chart needs

| Requirement | Why | Section |
| --- | --- | --- |
| CloudNativePG operator | `postgres.deployCnpg=true` renders a `Cluster` CR; without the CRD the install fails on an unknown kind | [4](#4-operators) |
| `hcloud-volumes` StorageClass | every `storageClass:` in `values.yaml` names it | [4](#4-operators) |
| cert-manager + a ClusterIssuer | `ajm.ingress.annotations` requests `letsencrypt-prod` | [4](#4-operators) |
| An OIDC provider | the app has no local login; `ajm.oidc.existingSecret` is mandatory | [5](#5-secrets) |
| A DNS record | `ajm.ingress.host` must resolve to the node | [3](#3-dns) |

**Node sizing.** Resource *requests* in `values.yaml` add up to roughly 2.3 vCPU
and 5.8 GiB, before k3s, Traefik, CoreDNS and the operators (~0.5 vCPU / 1 GiB).
Ollama alone requests 1 vCPU / 3 GiB.

- **8 vCPU / 16 GB** (e.g. Hetzner CPX41) — comfortable.
- **4 vCPU / 8 GB** (e.g. CPX31) — the practical minimum, and only with
  `llm.ollama.model=qwen2.5:3b` and `postgres.instances=1`.
- Less than that: set `llm.ollama.enabled=false` and point
  `llm.provider=azure` at a hosted model.

On a **single node, set `postgres.instances=1`.** Two CNPG instances on one node
give no availability benefit and double the storage.

## 1. Tools in WSL

```bash
kubectl version --client
helm version
```

If `kubectl` is missing:

```bash
curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -m 0755 kubectl /usr/local/bin/kubectl && rm kubectl
```

Keep the kubeconfig in the Linux filesystem (`~/.kube`), not on `/mnt/c`, so
Windows-side tools do not share or overwrite it.

## 2. Install k3s on the Hetzner server

### Option A: k3sup (one step)

```bash
curl -sLS https://get.k3sup.dev | sh
sudo install k3sup /usr/local/bin/

k3sup install \
  --ip <HETZNER_IP> --user root \
  --context hetzner-k3s \
  --k3s-extra-args '--secrets-encryption --write-kubeconfig-mode 600' \
  --local-path ~/.kube/hetzner-k3s.yaml
```

### Option B: manual

```bash
ssh root@<HETZNER_IP> 'curl -sfL https://get.k3s.io | \
  INSTALL_K3S_EXEC="--tls-san <HETZNER_IP> --secrets-encryption --write-kubeconfig-mode 600" sh -'

mkdir -p ~/.kube
ssh root@<HETZNER_IP> 'cat /etc/rancher/k3s/k3s.yaml' \
  | sed 's/127.0.0.1/<HETZNER_IP>/; s/default/hetzner-k3s/g' \
  > ~/.kube/hetzner-k3s.yaml
chmod 600 ~/.kube/hetzner-k3s.yaml
```

`--tls-san` adds the public IP to the API server certificate; without it
`kubectl` rejects the certificate when connecting to that IP.

`--secrets-encryption` matters here: k3s otherwise stores Secrets unencrypted in
its datastore, and this chart keeps the DB password, the Garage tokens, the S3
credentials and (if enabled) the SSE-C master key there. Enabling it later means
`k3s secrets-encrypt` plus a re-encrypt pass.

**NetworkPolicy is enforced** — k3s runs an embedded kube-router policy
controller alongside Flannel unless started with `--disable-network-policy`. The
chart's Gotenberg isolation policy depends on that; do not disable it.

### Do not expose the API server to the internet

Port 6443 is the cluster admin API.

**SSH tunnel (safest).** Leave 6443 closed and forward it:

```bash
ssh -N -L 6443:127.0.0.1:6443 root@<HETZNER_IP>
```

Keep `server: https://127.0.0.1:6443` in the kubeconfig; `--tls-san` is then
unnecessary, since the certificate already covers `127.0.0.1`.

**Hetzner Cloud Firewall.** Allow 22 and 6443 only from your own public IP, and
80/443 from anywhere. WSL2 is behind NAT, so the address to allow is the Windows
host's public IP, not the WSL one.

### Point kubectl at the cluster

Add to `~/.zshrc` (the login shell here is zsh; `.bashrc` alone will not do):

```bash
export KUBECONFIG=$HOME/.kube/config:$HOME/.kube/hetzner-k3s.yaml
```

Reload, then:

```bash
kubectl config get-contexts
kubectl config use-context hetzner-k3s
kubectl get nodes -o wide          # Ready
```

To merge into one file instead:

```bash
kubectl config view --flatten > ~/.kube/merged && mv ~/.kube/merged ~/.kube/config
chmod 600 ~/.kube/config
```

## 3. DNS

Point an A record for `ajm.ingress.host` at the node's public IP. Traefik and
cert-manager both need it: the HTTP-01 challenge is served over port 80 on that
name, so certificate issuance fails until DNS resolves.

```bash
dig +short ajm.example.org        # must return the node IP
```

## 4. Operators

### hcloud-csi (durable volumes)

Without it, `hcloud-volumes` does not exist and every PVC stays `Pending`. The
built-in `local-path` class works but keeps data on the node's disk, so Postgres
and Garage lose everything if the node is rebuilt.

Create a **read+write** API token in the Hetzner Console first.

```bash
kubectl -n kube-system create secret generic hcloud --from-literal=token=<API_TOKEN>

helm repo add hcloud https://charts.hetzner.cloud
helm repo update hcloud
helm install hcloud-csi hcloud/hcloud-csi -n kube-system --version 2.23.0

kubectl get storageclass          # hcloud-volumes should be listed
```

k3s uses the standard kubelet directory, so no `node.kubeletDir` override is
needed (k0s and microk8s do need one). If mounts fail with missing-path errors,
that value is the thing to check.

### CloudNativePG

```bash
kubectl apply --server-side -f \
  https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.30/releases/cnpg-1.30.0.yaml

kubectl -n cnpg-system rollout status deployment/cnpg-controller-manager
```

### cert-manager and a ClusterIssuer

```bash
helm install cert-manager oci://quay.io/jetstack/charts/cert-manager \
  --version v1.21.2 --namespace cert-manager --create-namespace \
  --set crds.enabled=true

kubectl -n cert-manager rollout status deployment/cert-manager
```

Then the issuer the chart's annotation names:

```bash
kubectl apply -f - <<'EOF'
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: you@example.org
    privateKeySecretRef:
      name: letsencrypt-prod-account-key
    solvers:
      - http01:
          ingress:
            class: traefik
EOF
```

Use the staging endpoint (`https://acme-staging-v02.api.letsencrypt.org/directory`)
while testing — production has strict rate limits and a failed loop will lock you
out for a week.

## 5. Secrets

Create these **before** installing; the chart deliberately refuses to generate
anything whose loss would be unrecoverable.

```bash
kubectl create namespace job-manager
```

**OIDC (required).** From your provider's application settings:

```bash
kubectl -n job-manager create secret generic ajm-oidc \
  --from-literal=client-secret='<OIDC_CLIENT_SECRET>'
```

The app has no local login, so an OIDC issuer must be reachable **both** from
inside the cluster and from the user's browser. `deploy/docker-compose.yml` and
`dev/authentik.yml` show the Authentik setup used in development.

**SSE-C (only if `storage.s3.encryption.mode=sse-c`).**

```bash
kubectl -n job-manager create secret generic ajm-sse \
  --from-literal=sse-c-key="$(openssl rand -base64 32)"
```

**Back this key up with the database.** Garage keeps no copy; losing it loses
every stored document, and switching the mode on later does not convert objects
already in the bucket — they become unreadable.

**Adzuna (optional)**, for the advisor job search:

```bash
kubectl -n job-manager create secret generic ajm-adzuna \
  --from-literal=app-key='<ADZUNA_APP_KEY>'
```

Garage's own tokens and the S3 access key are generated by the chart and kept
stable across upgrades, so nothing is needed for those.

## 6. Deploy

Checks that need no cluster:

```bash
helm lint deploy/helm/accessible-job-manager
helm template ajm deploy/helm/accessible-job-manager --set ajm.oidc.existingSecret=ajm-oidc
```

Write a values file rather than a long `--set` line, and keep it out of git:

```yaml
# ajm-prod.yaml
ajm:
  image:
    tag: ""                      # empty = Chart.yaml's appVersion
  ingress:
    host: ajm.example.org
  oidc:
    issuerUri: https://auth.example.org/application/o/accessible-job-manager/
    authUri: https://auth.example.org/application/o/authorize/
    clientId: <client-id>
    existingSecret: ajm-oidc
postgres:
  instances: 1                   # single node
llm:
  ollama:
    model: qwen2.5:3b
storage:
  s3:
    encryption:
      mode: sse-c
      existingSecret: ajm-sse
gotenberg:
  networkPolicy:
    # The default deny-list covers private ranges only. Add the node's own
    # PUBLIC IP, or a posting URL can redirect Chromium back at services bound
    # to it, including the k3s API on :6443.
    extraExcludedCIDRs: ["<HETZNER_IP>/32"]
```

```bash
helm upgrade --install ajm deploy/helm/accessible-job-manager \
  -n job-manager --create-namespace \
  -f ajm-prod.yaml --kube-context hetzner-k3s
```

**Use `helm install`/`upgrade`, never `helm template | kubectl apply`.** The
generated Garage tokens and S3 credentials are read back from the cluster on
each upgrade; rendering offline mints new ones every time, and the new S3 key
would no longer match the one imported into Garage.

## 7. Verify

First install pulls a multi-GB model, so Ollama sits in `Init` for a while — it
is downloading, not stuck.

```bash
kubectl -n job-manager get pods -o wide
kubectl -n job-manager get pvc                    # all Bound
kubectl -n job-manager get cluster                # CNPG: Cluster in healthy state
kubectl -n job-manager get certificate            # READY=True
```

The Garage bootstrap runs as a post-install hook and deletes itself on success,
so check it while it runs or check its effects afterwards:

```bash
kubectl -n job-manager logs job/ajm-accessible-job-manager-garage-bootstrap

POD=ajm-accessible-job-manager-garage-0
kubectl -n job-manager exec $POD -c garage -- /garage status       # node has a role
kubectl -n job-manager exec $POD -c garage -- /garage bucket list  # documents
kubectl -n job-manager exec $POD -c garage -- /garage key list
```

End-to-end:

```bash
helm test ajm -n job-manager
curl -I https://ajm.example.org/     # 200, valid certificate
```

Then open the site and complete a login through the OIDC provider.

## 8. Upgrade, rollback, uninstall

```bash
helm upgrade ajm deploy/helm/accessible-job-manager -n job-manager -f ajm-prod.yaml
helm history ajm -n job-manager
helm rollback ajm <revision> -n job-manager
```

```bash
helm uninstall ajm -n job-manager
```

**Uninstall does not delete data.** StatefulSet PVCs (Garage, Ollama) and the
CNPG cluster's volumes survive on purpose. Removing them is deliberate and
irreversible:

```bash
kubectl -n job-manager get pvc
kubectl -n job-manager delete pvc <name>
```

## Screen reader notes (JAWS)

- Prefer plain, linear output: `kubectl get ... -o wide`, `-o yaml`,
  `kubectl logs`, `kubectl describe`.
- Avoid full-screen TUIs such as `k9s`; they redraw the screen and move the
  cursor, which is the pattern that confuses JAWS (see `~/CLAUDE.md`).
- `kubectl logs -f` appends linearly and reads well; `kubectl get -w` rewrites
  rows in place and does not.
- For long output, use tmux copy-mode (`F2`) so the real cursor tracks the text.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `x509: certificate is valid for ..., not <IP>` | k3s installed without `--tls-san <IP>`. Reinstall with it, or use the SSH tunnel. |
| `connection refused` on 6443 | Tunnel not running, or the firewall blocks your current public IP. |
| `dial tcp ... i/o timeout` | Hetzner firewall, or a VPN changed your public IP. |
| `The connection to the server localhost:8080 was refused` | `KUBECONFIG` not set in this shell. |
| `no matches for kind "Cluster"` | CloudNativePG not installed. |
| PVC stuck `Pending` | `hcloud-volumes` missing (CSI driver or its `hcloud` token secret), or the node has no free volume slots. |
| Pods `Pending`, events say `Insufficient cpu/memory` | Node too small — see sizing in [0](#0-what-the-chart-needs). |
| Ollama stuck in `Init` for minutes | Normal on first start; it is pulling the model. `kubectl logs <pod> -c pull-model` shows progress. |
| Garage or Ollama `CrashLoopBackOff` with permission errors | The non-root `podSecurityContext` cannot write its volume. Override with `--set storage.garage.podSecurityContext=null` / `--set llm.ollama.podSecurityContext=null` and report it. |
| `certificate` never becomes Ready | DNS not pointing at the node yet, or port 80 blocked, so the HTTP-01 challenge cannot complete. |
| Login redirects to the wrong scheme | `ajm.ingress.tls.enabled=false` makes the redirect URI `http://`; the provider's registered callback must match exactly. |
| App logs `storage.s3.encryption.key ...` at boot | The SSE-C key is missing or not Base64 of exactly 32 bytes. |
| Bootstrap job fails on `layout apply` | A layout already exists at a different version. Inspect with `/garage layout show` before re-running. |
| Wrong cluster targeted | `kubectl config current-context`, or always pass `--kube-context`. |
