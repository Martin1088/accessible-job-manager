# Local Development

Setting up the project on your own machine, and the DevPod-based
alternative that runs the same toolchain in a container.

Back to the [project overview](../Readme.md).

## Prerequisites

- Java 26
- Node 24+
- Docker + Docker Compose
- A running Authentik instance (`dev/authentik.yml` provides one)
- Ollama with a pulled model — only needed for job posting import, see [section 8](#8-optional-ollama-for-job-posting-import)

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

Nothing to click. `dev/authentik/blueprints/access-job-manager.yaml` is mounted into
both Authentik containers at `/blueprints/custom` and applied automatically, so a
fresh instance comes up already carrying:

- the OAuth2/OIDC provider, with the client ID and secret that `application.yml`
  uses as its defaults, and the redirect URI `http://localhost:8060/login/oauth2/code/authentik`
- the application under the slug `access-job-manager`, which makes the issuer
  `http://localhost:9000/application/o/access-job-manager/`
- the groups `Advisor` and `Reviewer` — users in neither are treated as `USER`

The `groups` claim rides along with the standard `profile` scope: Authentik's
default profile mapping already emits every group the user belongs to, which is why
the backend only requests `openid, email, profile`.

Log in at `http://localhost:9000` with `akadmin` / the `AUTHENTIK_BOOTSTRAP_PASSWORD`
from `authentik.yml` to assign users to those groups.

To point the stack at different credentials, set the same variables the backend
reads — the Compose file passes them through to the blueprint:

```bash
OIDC_CLIENT_ID=… OIDC_CLIENT_SECRET=… docker compose -f authentik.yml up -d
```

The blueprint is idempotent and matched by name and slug, so re-applying it updates
the existing objects instead of duplicating them. To apply it by hand after an edit,
without restarting anything:

```bash
docker compose -f authentik.yml exec authentik-worker ak apply_blueprint custom/access-job-manager.yaml
```

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
sets nothing. The devcontainer installs Chromium on create and sets `CHROME_BIN`
in `.devcontainer/devcontainer.json`, so tests run there without further setup —
an existing container built before that change still needs it done by hand:

```bash
sudo apt-get update && sudo apt-get install -y chromium
export CHROME_BIN=/usr/bin/chromium
```

On macOS with Homebrew the path is `/opt/homebrew/bin/chromium` instead.

## 8. Optional: Ollama for job posting import

`POST /api/posting/overview` sends the fetched page text to a local Ollama
model. Only that one endpoint needs it — everything else runs without it.

Ollama is not part of `docker-compose.yml`, but `local-setup.yml` defines it,
and the service can be started on its own:

```bash
cd dev
docker compose -f local-setup.yml up -d ollama
```

Pull a model once (`qwen2.5:3b` is the default in `application.yml`, roughly
2 GB):

```bash
docker compose -f local-setup.yml exec ollama ollama pull qwen2.5:3b
```

Check that it answers:

```bash
docker compose -f local-setup.yml exec ollama ollama list
curl http://localhost:11434/api/tags
```

The container publishes `127.0.0.1:11434`, which matches the `OLLAMA_URL`
default of `http://localhost:11434` — no environment variable needed. Stop it
again when you are done, it is the heaviest service in the stack:

```bash
docker compose -f local-setup.yml stop ollama
```

Inference runs on the CPU here; there is no GPU passthrough in the Compose
definition. A 3B model is usable that way, larger ones get slow. In a
devcontainer, check the free space before pulling — models land on the same
volume as the images and `node_modules`:

```bash
df -h /
```

---

## Remote Development with DevPod

`.devcontainer/devcontainer.json` describes the full toolchain — JDK 26
(Temurin), Node 24, Docker-in-Docker and the Claude Code CLI, so
`dev/docker-compose.yml` and `dev/authentik.yml` can be started inside the
workspace. It works with any
[DevPod](https://devpod.sh) provider; `docker` runs it on your own machine,
`hetzner` and the other cloud providers run it on a rented server.

### Creating a workspace

Pass the **repository URL**, not a local path:

```bash
devpod up https://github.com/Martin1088/accessible-job-manager.git@feature/develop --id ajm --ide none
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

Claude Code is installed by the
`ghcr.io/anthropics/devcontainer-features/claude-code` feature and is on `PATH`:

```bash
cd /workspaces/ajm
claude
```

The machine has no browser, so the login prints a URL to open on your own
machine and paste the resulting code back into the terminal. Setting
`ANTHROPIC_API_KEY` skips that step. The credentials live in `~/.claude` inside
the container: `devpod stop`/`up` keeps them, `--recreate` and `devpod delete`
do not.

The clone is an ordinary git checkout — `git fetch` and `git checkout <branch>`
work as usual, and DevPod injects your git credentials. Note that switching to
a branch with a different `devcontainer.json` does **not** rebuild the
container; use `devpod up ajm --recreate` for that.

Nothing syncs back to your own machine. Commit and push, or the work is lost
when the workspace is deleted.

### Where the data lives

Inside the workspace, Docker runs as Docker-in-Docker, and that inner daemon keeps
its entire state — images and named volumes alike — in a single outer volume tied to
the container's identity (`dind-var-lib-docker-<devcontainerId>`). Rebuilding the
container with `devpod up --recreate` therefore hands you a fresh, empty Docker
daemon: every database in it is gone at once, without any command having deleted
anything.

The dev stacks avoid that by binding their data into the workspace folder rather
than into named volumes — `dev/postgres-data`, `dev/authentik/*`, `dev/meta` and
`dev/garage-data`. The workspace folder is mounted from the machine's disk
(`/.devpod/agent/contexts/…/content`), so it outlives the container and survives a
recreate. Only `devpod delete` removes it, together with the machine's volume.

Verify which of the two a path is on:

```bash
awk '$5 ~ /^\/(workspaces|var\/lib\/docker)$/ {print $5"  <=  "$4}' /proc/self/mountinfo
```

Authentik is the one stack that would be painful to lose regardless, since its
configuration is database state. That is what the blueprint in
[section 2](#2-configure-oidc) is for: a wiped instance reconfigures itself on
startup, and only the user accounts and group assignments have to be recreated.

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

