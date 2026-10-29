# Quotation SaaS (Spring Boot + Angular + JWT + Docker + AWS)

Each company signs up, edits its standard text once (Settings), then creates statements: client details, any number
of items (with optional photos), GST totals, and a PDF that is generated on the server.

## 0. Administration & access control (new)
The login page now has **Employee login** and **Admin login** tabs.
- **Admin login** opens the admin console: Overview, Users, Roles & rights, Activity log. A company's first admin is created with
  "Create a new company account" on the Admin tab. Accounts that existed before this upgrade become the admin of their own company automatically.
- **Users**: add one at a time or by Excel/CSV upload (standard template, row-level validation for duplicates, mandatory fields and email/mobile format,
  error report before anything is created). New users get a temporary password (shown once to the admin; bulk import offers a credentials CSV)
  and must set their own password at first sign-in. Admins can activate/deactivate users and reset passwords.
- **Roles & rights**: Admin (built in), Manager and Sales Executive are created for every company; add your own. Rights per module:
  view, create, edit, delete, download, send. A role also decides whether quotations are visible as "own only" or "all".
- **Grant / revoke per person**: open a user, tick or untick rights. The grid shows what differs from the role.
- **Activity log**: sign-in/sign-out history with date, time and IP; audit trail of create/edit/delete/download/send with filters
  (user, module, action, date range, text) and CSV export.
- Every API call is checked on the server (`@Requires(module, right)` / `@AdminOnly`); the menus and buttons only mirror that.
- Behind a proxy set `PROXY_HOPS` (default 1 = one nginx or ALB in front; 2 = ALB then nginx) so the IP in the log cannot be forged.
- Upgrade note: tokens issued before this version stop working, so everyone signs in once more.

## 1. Run locally (no Docker)
Backend (needs Java 17, Maven, Postgres with db/user `quotes`):
    cd backend && mvn spring-boot:run

Frontend (Angular 17+; the project was not scaffolded with angular.json, so create it once):
    npm i -g @angular/cli
    ng new quote-ui --routing --style=css --ssr=false     # run in a temp folder
    # copy the generated files into ./frontend (package.json, angular.json, tsconfig*, src/index.html, src/main.ts)
    # then overwrite src/app/* and src/styles.css with the files from this repo
    # make sure main.ts bootstraps AppComponent from './app/app.component' with appConfig
    cd frontend && npm install && ng serve --proxy-config proxy.conf.json
Open http://localhost:4200 and create an account.

## 2. Run everything with Docker
    cp .env.example .env     # set DB_PASS and JWT_SECRET
    docker compose up --build
Open http://localhost.

## 3. Deploy on AWS (simple, production-shaped)
1. RDS PostgreSQL (db `quotes`) in private subnets.
2. S3 bucket (block all public access) for photos.
3. ECR: push the `backend` and `frontend` images.
4. Secrets Manager: DB_PASS, JWT_SECRET (>= 32 chars).
5. ECS Fargate, two services behind one Application Load Balancer (HTTPS via ACM certificate):
   - ALB rule `/api/*` -> backend target group (port 8080, health check `/actuator/health`)
   - default rule -> frontend target group (port 80)
   Backend env: DB_URL=jdbc:postgresql://<rds-endpoint>:5432/quotes, DB_USER, DB_PASS, JWT_SECRET,
   STORAGE=s3, S3_BUCKET=<bucket>, AWS_REGION=ap-south-1, CORS_ORIGINS=https://your-domain
   Frontend env: BACKEND_URL=http://localhost:8080 (unused when the ALB routes /api, but required by the nginx template)
6. Backend task role needs only: s3:GetObject and s3:PutObject on arn:aws:s3:::<bucket>/*
7. Route 53 record -> ALB. Security groups: ALB open on 443; tasks accept traffic only from the ALB; RDS accepts only from the backend tasks.
Cheaper alternative for a first client: one EC2 (t3.small) running `docker compose` with RDS + S3.

## 4. CI/CD (GitHub Actions)
- `.github/workflows/ci.yml`: every PR and branch push builds the backend (`mvn verify`) and the frontend (`ng build`).
- `.github/workflows/deploy.yml`: every push to `main` runs CI, pushes `backend` and `frontend` images to GHCR,
  then SSHes to the server and runs `docker compose pull && docker compose up -d` with `docker-compose.prod.yml`.

One-time setup:
1. Server: Linux with Docker + Compose plugin; deploy user in the `docker` group; port 80 open.
2. Repo Settings -> Environments -> create `production`. Add secrets:
   `SSH_HOST`, `SSH_USER`, `SSH_KEY` (private key, its public key in the server's `~/.ssh/authorized_keys`),
   `DB_PASS`, `JWT_SECRET` (>= 32 chars), optional `SSH_PORT`, `SSH_KNOWN_HOSTS` (output of `ssh-keyscan <host>`).
3. Optional variables: `CORS_ORIGINS` (e.g. `https://your-domain`), `DEPLOY_PATH` (default `~/accurate-powertech`),
   `PROXY_HOPS`, `HTTP_PORT`.
The workflow writes `.env` on the server on each deploy; the database and uploads live in Docker volumes and survive redeploys.

## Before real client data
- Replace `ddl-auto: update` with Flyway migrations.
- Move the JWT from localStorage to an httpOnly cookie, or keep it short-lived with refresh tokens.
- Add rate limiting on /api/auth/*, and automated tests (a QA background will help here).
