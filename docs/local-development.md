# Local Development

Setting up the project on your own machine, and the DevPod-based
alternative that runs the same toolchain in a container.

Back to the [project overview](../Readme.md).

## Prerequisites

- Java 26
- Node 24+
- Docker + Docker Compose
- A running Authentik instance (`dev/authentik.yml` provides one)
- Ollama running locally with a pulled model (only needed for job posting import, e.g. `ollama pull qwen2.5:3b`)

## 1. Start dev infrastructure

```bash
cd dev
docker compose up -d
docker compose -f authentik.yml up -d   # OIDC provider, separate stack
```

This starts:

| Service      | Port | Description                    |
|--------------|------|--------------------------------|
| PostgreSQL   | 5432 | Main database                  |
| Garage       | 3900 | S3-compatible object storage   |
| Garage Admin | 3901 | Garage admin API               |
| Garage WebUI | 3909 | Storage browser UI             |
| Gotenberg    | 3000 | LibreOffice PDF conversion     |
| Traefik      | 80   | Reverse proxy                  |
| Authentik    | 9000 | OIDC provider (`authentik.yml`)|

## 2. Configure OIDC

Edit `src/main/resources/application.yml` and set your Authentik client credentials, or pass them as environment variables:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          authentik:
            client-id: <your-client-id>
            client-secret: <your-client-secret>
        provider:
          authentik:
            issuer-uri: http://localhost:9000/application/o/<your-app>/
```

The Authentik application must expose a `groups` claim on the OIDC token. Create groups named `Advisor` and `Reviewer`; users in neither group are treated as regular users (`USER`).

## 3. Configure document storage (S3 or Azure)

Set `STORAGE_PROVIDER` to `s3` (default, Garage) or `azure`.

For S3/Garage:

```
S3_ENDPOINT=http://localhost:3900
S3_BUCKET=job-manager
ACCESS_KEY=<garage-access-key>
SECRET_KEY=<garage-secret-key>
```

### Bootstrapping Garage

A fresh Garage container has no storage layout, no bucket and no access key, so
every upload fails with a 403 until the steps below have run once. The values
used here are the defaults in `application.yml`, so afterwards the application
works without setting any `S3_*` variables at all.

Run these from `dev/`. Garage ships as a shell-less container, so the binary
`/garage` has to be named explicitly in `docker compose exec`:

```bash
docker compose exec -T garage /garage status
docker compose exec -T garage /garage node id -q
```

Take the part of the node ID **before** the `@` and assign a storage layout —
without a role the node accepts no data:

```bash
docker compose exec -T garage /garage layout assign -z dc1 -c 10G <NODE_ID>
docker compose exec -T garage /garage layout apply --version 1
```

Create the bucket, then import the key. `key import` rather than `key create`,
because the ID and secret have to match the ones the application expects:

```bash
docker compose exec -T garage /garage bucket create job-manager
docker compose exec -T garage /garage key import GK671d588f544252fc82ebfdf7 f50541d1c15b75814e2c9c758dc42ff4302d3002391ca3e60db305e642a569a0 -n job-manager-key --yes
docker compose exec -T garage /garage bucket allow --read --write --owner job-manager --key job-manager-key
```

Verify:

```bash
docker compose exec -T garage /garage bucket info job-manager
```

If `key import` rejects `--yes`, repeat the command without that flag — the
option only exists in newer Garage releases.

For the local deployment stack, add `-f local-setup.yml` to every command above,
for example:

```bash
docker compose -f local-setup.yml exec -T garage /garage bucket info job-manager
```

The steps are idempotent in effect: layout, bucket and key already exist on a
second run, and Garage reports that rather than replacing anything.

For Azure Blob Storage:

```
STORAGE_PROVIDER=azure
AZURE_STORAGE_CONNECTION_STRING=<your-connection-string>
```

## 4. Run the backend

```bash
./gradlew bootRun
```

Backend starts on `http://localhost:8060`.

## 5. Run the frontend (dev mode)

```bash
cd AppClient
npm install
npm start
```

Frontend dev server starts on `http://localhost:4200`. The proxy config forwards `/api`, `/login`, `/logout` and `/oauth2` to the backend.

## 6. Build for production (embedded frontend)

```bash
./gradlew bootRun   # triggers npmBuild + copyFrontend automatically
```

The Angular build output is copied into `src/main/resources/static/` and served by Spring Boot at `http://localhost:8060`.

## 7. Run the frontend tests

Karma runs through the `@angular/build:karma` builder configured in
`angular.json`; there is no `karma.conf.js`. Watch mode opens a real browser:

```bash
cd AppClient
npm test
```

For a single non-interactive run — the form used in CI:

```bash
cd AppClient
npx ng test --watch=false --browsers=ChromeHeadless
```

Restrict the run to one component while working on it:

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include="**/company-form/**"
```

Karma needs a Chrome or Chromium binary and finds it through `CHROME_BIN` when
it is not on `PATH`. GitHub's runners ship one, so `.github/workflows/build.yml`
sets nothing; a plain devcontainer does not, and the run fails before the first
spec. Install one and point the variable at it:

```bash
sudo apt-get update && sudo apt-get install -y chromium
export CHROME_BIN=/usr/bin/chromium
```

On macOS with Homebrew the path is `/opt/homebrew/bin/chromium` instead.

---

## Remote Development with DevPod

`.devcontainer/devcontainer.json` describes the full toolchain — JDK 26
(Temurin), Node 24 and Docker-in-Docker, so `dev/docker-compose.yml` and
`dev/authentik.yml` can be started inside the workspace. It works with any
[DevPod](https://devpod.sh) provider; `docker` runs it on your own machine,
`hetzner` and the other cloud providers run it on a rented server.

### Creating a workspace

Pass the **repository URL**, not a local path:

```bash
devpod up https://github.com/Martin1088/accessible-job-manager.git@feature/html-template --id ajm --ide none
```

With a local path (`devpod up .`) DevPod uploads the working directory to the
machine instead of cloning it, and it does so unfiltered — `AppClient/node_modules`
and `build/` included, which is well over 800 MB. The upload then races the
container build, and `postCreateCommand` can fail on files that have not
arrived yet. Cloning from the remote transfers a fraction of that and is the
supported path for remote providers. The trade-off is that only pushed commits
reach the workspace.

`--ide none` builds the workspace without launching an IDE.

### Working in the workspace

```bash
devpod ssh ajm
```

The first start runs `chmod +x gradlew && ./gradlew --version && npm ci --prefix AppClient`,
so Gradle and the npm dependencies are in place. Verify with:

```bash
ls gradlew && java -version && node -v
./gradlew build --no-daemon
```

The clone is an ordinary git checkout — `git fetch` and `git checkout <branch>`
work as usual, and DevPod injects your git credentials. Note that switching to
a branch with a different `devcontainer.json` does **not** rebuild the
container; use `devpod up ajm --recreate` for that.

Nothing syncs back to your own machine. Commit and push, or the work is lost
when the workspace is deleted.

### Ports

`forwardPorts` covers 8060 (backend), 9000/9443 (Authentik), 5432, 3900/3901/3909
(Garage) and 3000 (Gotenberg). Port 80 is deliberately absent: ports below 1024
cannot be bound by the forwarding process on the host side and produce a
`bind: permission denied` on every connect. Reach Traefik from inside the
workspace instead.

A port that is not in the list can be tunnelled ad hoc without rebuilding:

```bash
ssh -L 9000:localhost:9000 ajm.devpod
```

### Connecting IntelliJ through JetBrains Gateway

DevPod writes an SSH host entry (`ajm.devpod`) whose `ProxyCommand` calls the
DevPod binary. Gateway can use it, provided **Settings → Tools → SSH
Configurations → Parse config file ~/.ssh/config** is enabled. Then use
*Connect via SSH* with host `ajm.devpod`, user `root` and project directory
`/workspaces/ajm`.

Under WSL there is an extra step, because DevPod runs on the Linux side while
Gateway runs on Windows and cannot use a Linux `ProxyCommand`. Mirror the entry
into the Windows SSH config (`C:\Users\<you>\.ssh\config`):

```
Host ajm.devpod
  User root
  StrictHostKeyChecking no
  UserKnownHostsFile NUL
  HostKeyAlgorithms rsa-sha2-256,rsa-sha2-512,ssh-rsa
  ProxyCommand wsl.exe -d <DISTRO> -e /home/<you>/.local/bin/devpod ssh --stdio --context default --user root ajm
```

`UserKnownHostsFile` is `NUL` rather than `/dev/null` here. Test it from
PowerShell with `ssh ajm.devpod` before opening Gateway — if that fails, the
problem is in the SSH config, not in Gateway.

### Cost and cleanup

On a cloud provider the machine bills for as long as it exists:

```bash
devpod stop ajm     # stop the machine, keep the workspace (volume still bills)
devpod delete ajm   # remove workspace, machine and volume
```

If `devpod up` aborts while the machine is being created, the provider may
already have created a server and a volume that DevPod never registered.
`devpod list` shows nothing in that case; check the provider's own console or
API and delete the leftovers by hand.

