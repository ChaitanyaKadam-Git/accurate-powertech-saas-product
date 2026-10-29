# Deploy: OCCPL dev server

The app runs at **https://accurate.occplsoftech.com** on the OCCPL dev server (the same EC2 instance as
`courier.dev.occplsoftech.com`), next to the platform and courier stacks but separate from them:

| Part | Accurate Powertech | Shared with the platform |
|---|---|---|
| Folder | `/home/occpl/accurate` (this `deploy/` folder) | nothing in `/opt/occpl` except one Caddy file |
| Containers | compose project `accurate`: `db` (Postgres 16), `backend`, `frontend` | none |
| Data | volumes `accurate_pgdata`, `accurate_uploads` | none |
| HTTPS | site block `/opt/occpl/extra/accurate.caddy` | the platform's Caddy (network `occpl_default`) |
| Deploy key | own key, forced command `bin/accurate-deploy.sh` | none |

Memory is capped (Postgres 512 MB, backend 768 MB, frontend 128 MB) so the courier and platform containers keep theirs.

## Release flow
Every merge to `main` runs `.github/workflows/release.yml`: CI, then both images to GHCR
(`ghcr.io/chaitanyakadam-git/accurate-powertech-saas-product-backend|frontend`, tags: commit SHA and `latest`), then over SSH
`backend <sha>` and `frontend <sha>`. The server pulls the image, waits until it is healthy and rolls back to the previous
release if not. A smoke test checks the site afterwards. To roll back by hand: Actions, *Release*, Run workflow, a full commit SHA.

## One-time setup
1. **DNS** (Route 53, zone `occplsoftech.com`): A record `accurate.occplsoftech.com` -> `65.0.187.198`.
2. **Images**: after the first *Release* run, check both packages are readable by the server. The repository is public,
   so the packages should be public too (GitHub, profile, Packages, package settings, *Change visibility*). The server is
   logged in to `ghcr.io` only for the OCCPL packages.
3. **Server files** (from a clone of this repository):
   ```bash
   ssh -i ~/.ssh/occpl_vps occpl@65.0.187.198 'mkdir -p ~/accurate/bin'
   scp -i ~/.ssh/occpl_vps deploy/docker-compose.yml deploy/accurate.caddy occpl@65.0.187.198:accurate/
   scp -i ~/.ssh/occpl_vps deploy/bin/*.sh occpl@65.0.187.198:accurate/bin/
   ssh -i ~/.ssh/occpl_vps occpl@65.0.187.198 'cd ~/accurate && chmod +x bin/*.sh \
     && bin/accurate-setup.sh accurate.occplsoftech.com ghcr.io/chaitanyakadam-git/accurate-powertech-saas-product'
   ```
   `accurate-setup.sh` keeps values already in `.env`, generates `DB_PASS` and `JWT_SECRET`, and installs the Caddy site
   only when the whole Caddy config validates.
4. **Deploy key**: `ssh-keygen -t ed25519 -N '' -C accurate-deploy -f accurate-deploy`, then append one line to
   `/home/occpl/.ssh/authorized_keys` on the server:
   `command="/home/occpl/accurate/bin/accurate-deploy.sh",restrict ssh-ed25519 AAAA... accurate-deploy`
   The courier key's line stays as it is.
5. **GitHub** (this repository, Settings, Environments, new environment **dev**): secret `VPS_SSH_KEY` = the private key file,
   secret `VPS_KNOWN_HOSTS` = output of `ssh-keyscan -t ed25519 65.0.187.198`, variable `VPS_HOST` = `65.0.187.198`,
   variable `APP_URL` = `https://accurate.occplsoftech.com`. Delete the private key file afterwards.

When the files in `deploy/` change, copy them to the server again (step 3); the workflow deploys images only.
