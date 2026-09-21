# Connecting WSL to a k3s cluster (Hetzner) or any Kubernetes cluster

`kubectl` and `helm` both read one kubeconfig file. Pointing WSL at a cluster
means getting that cluster's kubeconfig into `~/.kube` and choosing the
context. The steps are the same for k3s on Hetzner, EKS, AKS, kind or
minikube; only where the kubeconfig comes from differs.

Keep the kubeconfig in the Linux filesystem (`~/.kube`), not on `/mnt/c`, so
Windows-side tools do not share or overwrite it.

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

## 2. Install k3s on the Hetzner server

### Option A: k3sup (one step)

k3sup installs k3s over SSH and writes a ready-made kubeconfig.

```bash
curl -sLS https://get.k3sup.dev | sh
sudo install k3sup /usr/local/bin/

k3sup install \
  --ip <HETZNER_IP> --user root \
  --context hetzner-k3s \
  --local-path ~/.kube/hetzner-k3s.yaml
```

### Option B: manual

```bash
ssh root@<HETZNER_IP> 'curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--tls-san <HETZNER_IP>" sh -'

mkdir -p ~/.kube
ssh root@<HETZNER_IP> 'cat /etc/rancher/k3s/k3s.yaml' \
  | sed 's/127.0.0.1/<HETZNER_IP>/; s/default/hetzner-k3s/g' \
  > ~/.kube/hetzner-k3s.yaml
chmod 600 ~/.kube/hetzner-k3s.yaml
```

`--tls-san` adds the public IP to the API server certificate. Without it,
`kubectl` rejects the certificate when it connects to that IP.

## 3. Do not expose the API server to the internet

Port 6443 is the cluster admin API. Use one of these:

**SSH tunnel (safest).** Leave 6443 closed and forward it:

```bash
ssh -N -L 6443:127.0.0.1:6443 root@<HETZNER_IP>
```

Keep `server: https://127.0.0.1:6443` in the kubeconfig. `--tls-san` is not
needed, because the certificate already covers `127.0.0.1`.

**Hetzner Cloud Firewall.** Allow 22 and 6443 only from your own public IP,
and 80/443 from anywhere. WSL2 is behind NAT, so the IP to allow is the
Windows host's public IP, not the WSL address.

## 4. Point kubectl at the cluster

Add to `~/.zshrc` (the login shell here is zsh):

```bash
export KUBECONFIG=$HOME/.kube/config:$HOME/.kube/hetzner-k3s.yaml
```

Reload the shell, then:

```bash
kubectl config get-contexts              # list clusters
kubectl config use-context hetzner-k3s   # switch
kubectl get nodes -o wide                # node should be Ready
```

To merge everything into a single file instead of using `KUBECONFIG`:

```bash
kubectl config view --flatten > ~/.kube/merged && mv ~/.kube/merged ~/.kube/config
chmod 600 ~/.kube/config
```

Other clusters: add their kubeconfig to `KUBECONFIG` (or merge it) and switch
with `use-context`. Managed clusters usually produce it with a CLI, for example
`aws eks update-kubeconfig` or `az aks get-credentials`.

## 5. Deploy the chart

Helm uses the same kubeconfig. `--kube-context` picks the cluster for a single
command without changing your current context.

Checks that need no cluster:

```bash
helm lint deploy/helm/accessible-job-manager
helm template ajm deploy/helm/accessible-job-manager
```

Install or upgrade:

```bash
helm upgrade --install ajm deploy/helm/accessible-job-manager \
  --namespace job-manager --create-namespace \
  --kube-context hetzner-k3s
```

## k3s and Hetzner notes for this chart

- **Ingress.** k3s bundles Traefik and a "servicelb" that binds to the node's
  IP. Hetzner offers no cloud load balancer unless the hcloud
  cloud-controller-manager is installed, so point DNS at the node IP and expose
  the app with an `Ingress`.
- **Storage.** The default `local-path` StorageClass keeps volumes on the one
  node's disk. That is acceptable for a demo, but Postgres and Garage data is
  lost if the node is rebuilt. Use `hcloud-csi` for durable volumes.
- **Secrets.** Do not commit OIDC client secrets, Garage keys or the SSE-C
  master key in `values.yaml`. Create them as Kubernetes `Secret`s, or pass them
  with `--set` or a values file that is git-ignored.

## Screen reader notes (JAWS)

- Prefer plain, linear output: `kubectl get ... -o wide`, `-o yaml`,
  `kubectl logs`, `kubectl describe`.
- Avoid full-screen TUIs such as `k9s`. They redraw the screen and jump the
  cursor, which is the pattern that confuses JAWS; see `~/CLAUDE.md`.
- For long output, use tmux copy-mode (`F2`) so the real cursor moves through
  the text.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `x509: certificate is valid for ..., not <IP>` | k3s was installed without `--tls-san <IP>`. Reinstall with it, or use the SSH tunnel. |
| `connection refused` on 6443 | Tunnel not running, or the firewall blocks your current public IP. |
| `Unable to connect to the server: dial tcp ... i/o timeout` | Hetzner firewall or a VPN changed your public IP. |
| `The connection to the server localhost:8080 was refused` | `KUBECONFIG` is not set in this shell, so `kubectl` has no config. |
| Wrong cluster targeted | Run `kubectl config current-context` before any `helm upgrade`, or always pass `--kube-context`. |
