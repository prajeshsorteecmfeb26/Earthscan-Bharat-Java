# Deployment Guide

EarthScan Bharat — AWS deployment options and the pre-flight checklist.

---

## Contents

- [Pre-deployment security checklist](#pre-deployment-security-checklist)
- [Option A — Single EC2 instance with Docker Compose](#option-a--single-ec2-instance-with-docker-compose)
- [Option B — ECS Fargate with an Application Load Balancer](#option-b--ecs-fargate-with-an-application-load-balancer)
- [Option C — Managed data services](#option-c--managed-data-services)
- [HTTPS](#https)
- [CI/CD with GitHub Actions](#cicd-with-github-actions)
- [Cost estimates](#cost-estimates)
- [Monitoring and operations](#monitoring-and-operations)

---

## Pre-deployment security checklist

**Work through this before exposing anything to the internet.** Items 1–4 are not optional.

- [ ] **1. Fix or disable password reset.** `POST /api/auth/reset-password` lets anyone who knows an
      email address change that account's password. Either implement token-based verification or
      block the route at the gateway/ALB until you have.
- [ ] **2. Generate a fresh `JWT_SECRET`.** `openssl rand -base64 48`. Never reuse the development
      default; every service must receive the same value.
- [ ] **3. Terminate TLS.** JWTs travel in the `Authorization` header. Over plain HTTP they are
      readable by anything on the path. See [HTTPS](#https).
- [ ] **4. Set `CORS_ALLOWED_ORIGINS` to your real frontend origin.** Not a wildcard.
- [ ] 5. Change RabbitMQ credentials from `guest`/`guest`.
- [ ] 6. Set strong database passwords; use the least-privilege accounts in
      `infra/mysql/init/01-create-databases.sql` rather than `root`.
- [ ] 7. Set `JPA_DDL_AUTO=validate` and manage the schema with migrations.
- [ ] 8. Set `SEED_DEMO_LISTINGS=false`, so fabricated prices do not pollute district averages.
- [ ] 9. Change the bootstrap admin password immediately after first login, or leave
      `BOOTSTRAP_ADMIN_PASSWORD` empty and create the admin another way.
- [ ] 10. Do not expose ports 8081–8084, 8761, 5672 or 15672 publicly. Only the gateway (8080) and
      the frontend need inbound access.
- [ ] 11. Shorten `JWT_EXPIRATION_MS` from 7 days. There is no revocation mechanism.
- [ ] 12. Add rate limiting on `/api/auth/**` — AWS WAF, or Spring Cloud Gateway's
      `RequestRateLimiter`.
- [ ] 13. Store secrets in AWS Secrets Manager or SSM Parameter Store, not in a `.env` file on disk.
- [ ] 14. Enable automated database backups.

---

## Option A — Single EC2 instance with Docker Compose

Simplest path. Appropriate for demos, coursework and small pilots. Everything runs on one host, so
the instance is a single point of failure.

### A.1 Launch the instance

| Setting | Value |
|---|---|
| AMI | Ubuntu Server 24.04 LTS |
| Instance type | `t3.large` (2 vCPU, 8 GB) — **`t3.micro` will not work**; six JVMs plus MySQL and MongoDB need the RAM |
| Storage | 30 GB gp3 |
| Key pair | Create or select one |

### A.2 Security group

| Type | Port | Source | Purpose |
|---|---|---|---|
| SSH | 22 | **Your IP only** | Administration |
| HTTP | 80 | 0.0.0.0/0 | Redirect to HTTPS |
| HTTPS | 443 | 0.0.0.0/0 | Public traffic |
| Custom TCP | 8080 | 0.0.0.0/0 | Gateway (drop once nginx fronts it) |
| Custom TCP | 5173 | 0.0.0.0/0 | Frontend (drop once nginx fronts it) |

Do **not** open 8081–8084, 8761, 5672 or 15672. Reach those over an SSH tunnel:

```bash
ssh -i key.pem -L 15672:localhost:15672 -L 8761:localhost:8761 ubuntu@<elastic-ip>
```

### A.3 Allocate an Elastic IP

Without one, the public IP changes on every stop/start, breaking `CORS_ALLOWED_ORIGINS` and
`VITE_API_BASE_URL` — and the frontend's API URL is baked in at build time, so a changed IP means a
rebuild.

### A.4 Install Docker

```bash
ssh -i key.pem ubuntu@<elastic-ip>

sudo apt-get update && sudo apt-get upgrade -y
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker ubuntu
newgrp docker
docker --version && docker compose version
```

### A.5 Add swap

A `t3.large` builds six Maven modules; without swap the build can be OOM-killed.

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### A.6 Deploy

```bash
git clone <your-repository-url>
cd earthscan-springboot

cp .env.example .env
nano .env
```

Set at minimum:

```bash
JWT_SECRET=<output of: openssl rand -base64 48>
MYSQL_ROOT_PASSWORD=<strong password>
RABBITMQ_USER=earthscan
RABBITMQ_PASSWORD=<strong password>
BOOTSTRAP_ADMIN_PASSWORD=<strong password>
SEED_DEMO_LISTINGS=false
JPA_DDL_AUTO=validate

# Both of these need your real public address
CORS_ALLOWED_ORIGINS=https://your-domain.com
VITE_API_BASE_URL=https://your-domain.com/api
```

```bash
docker compose up --build -d
docker compose ps          # wait for all services to report "healthy"
docker compose logs -f api-gateway
```

Verify:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8761        # should list 6 registered instances
```

### A.7 Restart on reboot

```bash
sudo tee /etc/systemd/system/earthscan.service > /dev/null <<'EOF'
[Unit]
Description=EarthScan Bharat stack
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ubuntu/earthscan-springboot
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down
User=ubuntu

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable earthscan.service
```

---

## Option B — ECS Fargate with an Application Load Balancer

No servers to patch, per-service scaling, and rolling deployments. More AWS surface area to configure.

### B.1 Push images to ECR

```bash
export AWS_REGION=ap-south-1
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

for svc in discovery-server api-gateway auth-service land-service forum-service notification-service; do
  aws ecr create-repository --repository-name earthscan/$svc --region $AWS_REGION || true
done
aws ecr create-repository --repository-name earthscan/frontend --region $AWS_REGION || true

aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR

for svc in discovery-server api-gateway auth-service land-service forum-service notification-service; do
  docker build --build-arg MODULE=$svc -t $ECR/earthscan/$svc:latest .
  docker push $ECR/earthscan/$svc:latest
done

docker build --build-arg VITE_API_BASE_URL=https://your-domain.com \
  -t $ECR/earthscan/frontend:latest ./frontend
docker push $ECR/earthscan/frontend:latest
```

`ap-south-1` (Mumbai) is the sensible region for this platform's users.

### B.2 Networking

- VPC with two public and two private subnets across two availability zones
- Public: ALB and NAT gateway. Private: ECS tasks and data services
- Security groups: ALB accepts 443 from the internet; tasks accept traffic only from the ALB security
  group and from each other

### B.3 Replace Eureka with ECS Service Connect

On Fargate, Eureka is redundant — ECS Service Connect or Cloud Map already provides discovery, and
running a registry inside the cluster duplicates it. To use Service Connect instead:

1. Do not deploy `discovery-server`
2. Enable Service Connect on the cluster and each service
3. Change the gateway routes from `lb://auth-service` to `http://auth-service:8081` and set
   `eureka.client.enabled=false`

Keep Eureka only if you want the deployment to mirror the Compose topology exactly.

### B.4 Secrets

Store `JWT_SECRET` and database passwords in Secrets Manager and reference them from the task
definition:

```json
"secrets": [
  { "name": "JWT_SECRET",     "valueFrom": "arn:aws:secretsmanager:...:secret:earthscan/jwt-secret" },
  { "name": "MYSQL_PASSWORD", "valueFrom": "arn:aws:secretsmanager:...:secret:earthscan/mysql" }
]
```

Never put a secret in the task definition's `environment` block — those values are visible to anyone
with `ecs:DescribeTaskDefinition`.

### B.5 Task sizing

| Service | CPU | Memory |
|---|---|---|
| api-gateway | 512 | 1024 |
| auth-service | 512 | 1024 |
| land-service | 512 | 1024 |
| forum-service | 256 | 512 |
| notification-service | 256 | 512 |
| frontend | 256 | 512 |

Health check path is `/actuator/health` for backend services and `/` for the frontend. Set the grace
period to at least 90 seconds — Spring Boot with JPA needs that long to become healthy, and a shorter
grace period will kill tasks during a normal start.

### B.6 ALB routing

| Path pattern | Target |
|---|---|
| `/api/*` | api-gateway target group |
| `/actuator/*` | api-gateway target group |
| `/*` (default) | frontend target group |

---

## Option C — Managed data services

Once containerised databases become the limiting factor:

| Self-hosted | AWS managed | Notes |
|---|---|---|
| MySQL container | **RDS for MySQL 8.0** | Automated backups, point-in-time recovery, Multi-AZ |
| MongoDB container | **DocumentDB** | MongoDB-compatible. Verify your queries: DocumentDB does not implement the entire MongoDB API |
| RabbitMQ container | **Amazon MQ for RabbitMQ** | Managed broker with the same AMQP interface |

Only the connection strings change:

```bash
MYSQL_HOST=earthscan-db.abc123.ap-south-1.rds.amazonaws.com
MONGODB_URI=mongodb://user:pass@docdb-cluster...:27017/earthscan_forum?tls=true&retryWrites=false
RABBITMQ_HOST=b-abc123.mq.ap-south-1.amazonaws.com
```

**Two DocumentDB caveats:** `retryWrites=false` is required, and TLS needs the Amazon RDS CA bundle
in the container's truststore.

---

## HTTPS

### With an ALB (Option B)

Request a certificate in ACM, attach it to an HTTPS:443 listener, and redirect HTTP:80 to it. ACM
certificates renew automatically.

### With a single EC2 instance (Option A)

Use Caddy, which obtains and renews Let's Encrypt certificates automatically:

```bash
sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt-get update && sudo apt-get install -y caddy
```

```bash
sudo tee /etc/caddy/Caddyfile > /dev/null <<'EOF'
your-domain.com {
    handle /api/* {
        reverse_proxy localhost:8080
    }
    handle /actuator/* {
        reverse_proxy localhost:8080
    }
    handle {
        reverse_proxy localhost:5173
    }
}
EOF

sudo systemctl reload caddy
```

Then remove 8080 and 5173 from the security group and rebuild the frontend with
`VITE_API_BASE_URL=https://your-domain.com`.

---

## CI/CD with GitHub Actions

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      - name: Build and test
        run: mvn -B verify
        env:
          # Tests construct JwtTokenProvider, which fails fast on a short secret.
          JWT_SECRET: TestOnlySigningKeyForEarthScanBharatCI2026Pipeline
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: '**/target/surefire-reports/*.xml'

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - working-directory: frontend
        run: npm ci
      - working-directory: frontend
        run: npm run lint
      - working-directory: frontend
        run: npm run build
```

Deployment workflow, on a push to `main`: configure AWS credentials via an OIDC role (not long-lived
access keys), build and push each image to ECR, then `aws ecs update-service --force-new-deployment`
for each service.

---

## Cost estimates

Rough monthly figures for `ap-south-1`, on-demand pricing. Verify against the AWS pricing calculator.

**Option A — single EC2**

| Item | Monthly (USD) |
|---|---|
| `t3.large` | ~$60 |
| 30 GB gp3 | ~$3 |
| Elastic IP (attached) | $0 |
| Data transfer | ~$5 |
| **Total** | **~$68** |

A `t3.medium` (4 GB) at roughly $30 can work if you drop `discovery-server` and
`notification-service` and route the gateway directly — worth knowing for a demo budget.

**Option B — ECS Fargate with managed data**

| Item | Monthly (USD) |
|---|---|
| Fargate (6 tasks, ~0.35 vCPU avg) | ~$75 |
| ALB | ~$20 |
| RDS MySQL `db.t4g.micro` Multi-AZ | ~$50 |
| DocumentDB `db.t4g.medium` | ~$60 |
| Amazon MQ `mq.t3.micro` | ~$18 |
| NAT gateway | ~$35 |
| **Total** | **~$258** |

The NAT gateway is often an unwelcome surprise. VPC endpoints for ECR and Secrets Manager reduce it
substantially.

---

## Monitoring and operations

### Health endpoints

```bash
curl http://<host>:8080/actuator/health     # gateway
curl http://<host>:8081/actuator/health     # auth
# ... 8082, 8083, 8084
```

### Logs

```bash
docker compose logs -f auth-service
docker compose logs --tail=200 | grep <correlation-id>
```

Every log line carries the correlation id assigned by the gateway, so grepping one id across all
services reconstructs a single user action in order. This is the main reason distributed debugging is
tractable here.

For ECS, use the `awslogs` driver and query in CloudWatch Logs Insights:

```
fields @timestamp, @message
| filter @message like /3f7a9c12-4b8e-4d1a-9e5f-2c8d7b6a1f04/
| sort @timestamp asc
```

### RabbitMQ

Watch `earthscan.dlq`. A non-empty dead-letter queue means a consumer is failing repeatedly — inspect
the message in the management UI and grep the owning service's logs for its correlation id.

### Backups

```bash
# MySQL
docker compose exec mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --databases earthscan_auth earthscan_land > backup-$(date +%F).sql

# MongoDB
docker compose exec mongodb mongodump --archive=/tmp/mongo-$(date +%F).archive \
  --db=earthscan_forum --db=earthscan_notifications
docker compose cp mongodb:/tmp/mongo-$(date +%F).archive ./
```

Automate these to S3 with lifecycle rules. On RDS and DocumentDB, enable automated snapshots instead.

### Scaling notes

- **Stateless services** (gateway, auth, land, forum) scale horizontally without change
- **notification-service** already runs 2–5 concurrent consumers per queue; the unique
  `{sourceEventId, userId}` index makes multiple instances safe
- **MySQL** read replicas would serve the land-search load well, since it is read-heavy
- **The gateway** is the throughput ceiling in Option A, being a single instance

---

## Post-deployment verification

```bash
# 1. Gateway is up
curl https://your-domain.com/actuator/health

# 2. Registration works
curl -X POST https://your-domain.com/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Test User","email":"test@example.com","password":"testpass123","role":"Farmer"}'

# 3. Login returns a token
TOKEN=$(curl -s -X POST https://your-domain.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"testpass123"}' | jq -r .token)

# 4. Authenticated request
curl https://your-domain.com/api/auth/me -H "Authorization: Bearer $TOKEN"

# 5. Public search needs no token
curl https://your-domain.com/api/lands

# 6. Forum requires one — expect 401
curl -i https://your-domain.com/api/forum/posts

# 7. The event bus produced a welcome notification
curl https://your-domain.com/api/notifications -H "Authorization: Bearer $TOKEN"
```

Step 7 is the end-to-end proof that RabbitMQ is working: the notification exists only because
auth-service published `user.registered` and notification-service consumed it.
