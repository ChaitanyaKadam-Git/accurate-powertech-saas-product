# Quotation SaaS (Spring Boot + Angular + JWT + Docker + AWS)

Each company signs up, edits its standard text once (Settings), then creates statements: client details, any number
of items (with optional photos), GST totals, and a PDF that is generated on the server.

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

## Before real client data
- Replace `ddl-auto: update` with Flyway migrations.
- Move the JWT from localStorage to an httpOnly cookie, or keep it short-lived with refresh tokens.
- Add rate limiting on /api/auth/*, and automated tests (a QA background will help here).
