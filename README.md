# EarthScan Bharat — Spring Boot Microservices Platform

Land intelligence and agricultural advisory platform for Indian farmers, land buyers and
agriculture experts. This repository is a migration of an ASP.NET Core 8 monolith to a Spring Boot
microservices architecture, retaining the existing React frontend.

> **Start here:** [`documentation/VERIFICATION-REPORT.md`](documentation/VERIFICATION-REPORT.md)
> records what the architectural audit found, what was changed, the production-readiness score and
> its reasoning, and — importantly — the one open security vulnerability and the fact that this code
> has not yet been compiled. Read it before deploying anything.

---

## Table of contents

- [What this is](#what-this-is)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Quick start with Docker](#quick-start-with-docker)
- [Running locally without Docker](#running-locally-without-docker)
- [Service reference](#service-reference)
- [API documentation](#api-documentation)
- [Roles and permissions](#roles-and-permissions)
- [Asynchronous messaging](#asynchronous-messaging)
- [Databases](#databases)
- [Testing](#testing)
- [Configuration reference](#configuration-reference)
- [Deployment to AWS](#deployment-to-aws)
- [Security notes and known limitations](#security-notes-and-known-limitations)
- [What changed from the ASP.NET version](#what-changed-from-the-aspnet-version)
- [Project layout](#project-layout)
- [Troubleshooting](#troubleshooting)

---

## What this is

EarthScan Bharat scores agricultural land on two dimensions — a composite **Land Intelligence
Score** and a **Borewell Success Probability** — and surrounds that with land search, investment
analysis, and a community Q&A forum connecting farmers to agronomists.

The platform serves four roles: **Farmer**, **Land Buyer**, **Agriculture Expert** and **Admin**.

---

## Architecture

```
                            ┌──────────────────────┐
                            │   React (Vite)       │
                            │   nginx :5173        │
                            └──────────┬───────────┘
                                       │  HTTPS / JSON
                                       ▼
                            ┌──────────────────────┐
                            │  api-gateway :8080   │  routes, CORS,
                            │  Spring Cloud GW     │  correlation ids,
                            └──────────┬───────────┘  coarse JWT gate
                                       │
              ┌────────────────┬───────┴────────┬─────────────────────┐
              ▼                ▼                ▼                     ▼
     ┌────────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐
     │ auth-service   │ │ land-service │ │forum-service │ │notification-service│
     │     :8081      │ │    :8082     │ │    :8083     │ │       :8084        │
     └───────┬────────┘ └──────┬───────┘ └──────┬───────┘ └─────────┬──────────┘
             │                 │                │                   │
             ▼                 ▼                ▼                   ▼
      ┌─────────────┐   ┌─────────────┐  ┌─────────────┐   ┌──────────────────┐
      │   MySQL     │   │   MySQL     │  │  MongoDB    │   │     MongoDB      │
      │earthscan_   │   │earthscan_   │  │earthscan_   │   │earthscan_        │
      │   auth      │   │   land      │  │   forum     │   │  notifications   │
      └─────────────┘   └─────────────┘  └─────────────┘   └──────────────────┘

             └──────────────── RabbitMQ ──────────────────────────┘
                        earthscan.events (topic)

                        ┌──────────────────────┐
                        │ discovery-server     │  Eureka registry
                        │       :8761          │
                        └──────────────────────┘
```

**Why these service boundaries.** The split follows the existing domain rather than being drawn for
its own sake. Identity, land, and community discussion have genuinely different data shapes, change
at different rates, and have different availability requirements — the forum being down should not
stop anyone logging in. `notification-service` is the piece that justifies the message bus: it is a
pure consumer, so registration, listing creation and forum replies all complete without waiting on
it.

**Honest caveat.** For a system at this scale, a well-structured monolith would be simpler to
operate and would be the right production choice. The microservice split here is a deliberate
architectural exercise; the costs (distributed debugging, eventual consistency across services, six
processes instead of one) are real and are documented rather than glossed over.

---

## Technology stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 17 |
| Framework | Spring Boot 3.3.4, Spring Framework 6 |
| Cloud | Spring Cloud 2023.0.3 (Gateway, Netflix Eureka) |
| Security | Spring Security 6, JJWT 0.12.6, BCrypt |
| Persistence | Spring Data JPA / Hibernate 6 (MySQL 8.4), Spring Data MongoDB (MongoDB 7) |
| Messaging | RabbitMQ 3.13 via Spring AMQP |
| Documentation | springdoc-openapi 2.6.0 (Swagger UI) |
| Boilerplate / mapping | Lombok, MapStruct 1.5.5 (compile-time, `unmappedTargetPolicy=ERROR`) |
| Testing | JUnit 5, Mockito 5, AssertJ, spring-security-test, H2, embedded MongoDB |
| Build | Maven (multi-module reactor) |
| Frontend | React 19, Vite 8, MUI 9, React Bootstrap, react-router 7, i18next, Leaflet, Recharts |
| Containers | Docker, Docker Compose, nginx |

---

## Prerequisites

**With Docker (recommended):**
- Docker Engine 24+ and Docker Compose v2
- 6 GB RAM available to Docker (six JVMs, MySQL, MongoDB and RabbitMQ)

**Without Docker:**
- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- MongoDB 6+
- RabbitMQ 3.12+
- Node.js 20+

---

## Quick start with Docker

```bash
git clone <your-repository-url>
cd earthscan-bharat

# 1. Create your environment file. Compose and its env file live in docker/.
cd docker
cp .env.example .env
```

**Edit `.env` before continuing.** At minimum set `JWT_SECRET` to at least 32 characters — the
services refuse to start with a shorter key rather than signing tokens with a weak secret:

```bash
# Generate a strong secret
openssl rand -base64 48
```

Also set `BOOTSTRAP_ADMIN_PASSWORD` if you want an administrator account created on first startup.
Leaving it blank means no admin is seeded — this is intentional, because a hard-coded default admin
password is how demo deployments get compromised.

```bash
# 2. Build and start everything, from inside docker/
docker compose up --build
```

All compose paths are relative to `docker/`. Backend images build from `../backend` (the Maven
reactor, because every service needs `common-lib` present to resolve); the frontend image builds from
the repository root because it needs both `frontend/` and `docker/nginx.conf`.

First build takes 5–10 minutes (Maven downloads the dependency tree once). Subsequent builds are
much faster thanks to the layered Dockerfile.

**Verify the stack is healthy:**

```bash
docker compose ps                                  # all services should be "healthy"
curl http://localhost:8761                         # Eureka: 6 registered instances
curl http://localhost:8080/actuator/health         # gateway
```

| Interface | URL |
|---|---|
| Frontend | http://localhost:5173 |
| API Gateway | http://localhost:8080 |
| Swagger UI (all services) | http://localhost:8080/swagger-ui.html |
| Eureka dashboard | http://localhost:8761 |
| RabbitMQ management | http://localhost:15672 (guest/guest by default) |

**Stopping:**

```bash
docker compose down            # stop, keep data
docker compose down -v         # stop and wipe all database volumes
```

---

## Running locally without Docker

Start the infrastructure first:

```bash
cd docker
docker compose up -d mysql mongodb rabbitmq
```

Build the whole reactor once:

```bash
cd backend
mvn clean install
```

Then start the services **in this order** — the gateway needs Eureka, and the business services
need their databases:

```bash
# All from backend/. Terminal 1 — must be first.
mvn -pl discovery-server spring-boot:run

# Terminals 2-5
mvn -pl auth-service spring-boot:run
mvn -pl land-service spring-boot:run
mvn -pl forum-service spring-boot:run
mvn -pl notification-service spring-boot:run

# Terminal 6 — must be last
mvn -pl api-gateway spring-boot:run
```

Export a secret first, or the services will fail fast:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export BOOTSTRAP_ADMIN_PASSWORD="choose-something-strong"
```

Every service takes a Spring profile. `dev` is the sensible local default:

```bash
mvn -pl auth-service spring-boot:run -Dspring-boot.run.profiles=dev
```

| Profile | Schema | Logging | Actuator | Notes |
|---|---|---|---|---|
| *(none)* | `ddl-auto=update` | plain console | health, info, metrics | Clone-and-run default |
| `dev` | `ddl-auto=update` | coloured console, SQL + bound params | everything | Local development |
| `test` | H2 in-memory, `create-drop` | WARN only | — | Broker and Eureka disabled |
| `prod` | **`ddl-auto=validate`** | structured JSON to stdout, async | health, info, prometheus | Schema managed by migrations |

`prod` deliberately refuses to modify the schema. Apply `database/mysql/schema/` first — see
[Schema management](#schema-management).

Frontend:

```bash
cd frontend
cp .env.example .env
npm install
npm run dev            # http://localhost:5173
```

---

## Service reference

| Service | Port | Database | Publishes | Consumes |
|---|---|---|---|---|
| `discovery-server` | 8761 | — | — | — |
| `api-gateway` | 8080 | — | — | — |
| `auth-service` | 8081 | MySQL `earthscan_auth` | `user.registered`, `user.role-changed`, `user.deleted` | — |
| `land-service` | 8082 | MySQL `earthscan_land` | `land.listed` | `user.deleted` |
| `forum-service` | 8083 | MongoDB `earthscan_forum` | `forum.post.created`, `forum.comment.added` | `user.deleted` |
| `notification-service` | 8084 | MongoDB `earthscan_notifications` | — | `user.#`, `land.#`, `forum.#` |

### Key endpoints

**auth-service**
```
POST   /api/auth/register            public
POST   /api/auth/login               public   → { token, expiresInSeconds, user }
POST   /api/auth/reset-password      public
GET    /api/auth/me                  authenticated
GET    /api/admin/users              ADMIN
GET    /api/admin/users/page         ADMIN    paged + searchable
GET    /api/admin/stats              ADMIN    counts by role
PUT    /api/admin/users/{id}         ADMIN    change role
PATCH  /api/admin/users/{id}/enabled ADMIN    enable/disable
DELETE /api/admin/users/{id}         ADMIN
```

**land-service**
```
GET    /api/lands                    public   paged search, 10 optional filters
GET    /api/lands/{id}               public
GET    /api/lands/{id}/analysis      public   investment benchmark
GET    /api/lands/soil-types         public   reference data
GET    /api/lands/districts          public
GET    /api/lands/mine               authenticated
POST   /api/lands                    FARMER, ADMIN
PUT    /api/lands/{id}               owner or ADMIN
PATCH  /api/lands/{id}/status        owner or ADMIN
PATCH  /api/lands/{id}/verified      ADMIN only
DELETE /api/lands/{id}               owner or ADMIN
GET    /api/saved-searches           LAND_BUYER, FARMER, ADMIN
POST   /api/saved-searches           LAND_BUYER, FARMER, ADMIN
DELETE /api/saved-searches/{id}      owner only
```

**forum-service** — all endpoints require authentication
```
GET    /api/forum/posts                          full feed with replies
GET    /api/forum/posts/feed                     paged, no reply bodies
GET    /api/forum/posts/unanswered               expert work queue
GET    /api/forum/posts/{id}
GET    /api/forum/categories
POST   /api/forum/posts
POST   /api/forum/posts/{postId}/comments
PATCH  /api/forum/posts/{postId}/resolved        author or ADMIN
DELETE /api/forum/posts/{postId}                 author or ADMIN
DELETE /api/forum/posts/{postId}/comments/{id}   comment author or ADMIN
```

**notification-service** — all endpoints require authentication
```
GET    /api/notifications                 own notifications, paged
GET    /api/notifications/unread-count
PATCH  /api/notifications/{id}/read
PATCH  /api/notifications/read-all
DELETE /api/notifications/{id}
```

There is deliberately no endpoint to *create* a notification — they arrive only from the event bus,
so no client can inject one into another user's inbox.

---

## API documentation

Swagger UI on the gateway aggregates all four services:

```
http://localhost:8080/swagger-ui.html
```

Per-service documents:

| Service | OpenAPI JSON | Swagger UI |
|---|---|---|
| auth | http://localhost:8081/v3/api-docs | http://localhost:8081/swagger-ui.html |
| land | http://localhost:8082/v3/api-docs | http://localhost:8082/swagger-ui.html |
| forum | http://localhost:8083/v3/api-docs | http://localhost:8083/swagger-ui.html |
| notification | http://localhost:8084/v3/api-docs | http://localhost:8084/swagger-ui.html |

**To call protected endpoints from Swagger:** `POST /api/auth/login`, copy the `token` from the
response, click **Authorize**, and paste it. The bearer scheme is pre-configured.

---

## Roles and permissions

| Capability | Farmer | Land Buyer | Agriculture Expert | Admin |
|---|:---:|:---:|:---:|:---:|
| Browse listings (unauthenticated too) | ✓ | ✓ | ✓ | ✓ |
| Create / edit own listing | ✓ | | | ✓ |
| Verify a listing | | | | ✓ |
| Save searches / shortlist | ✓ | ✓ | | ✓ |
| Read and post in the forum | ✓ | ✓ | ✓ | ✓ |
| Answer queries queue | | | ✓ | ✓ |
| Manage users | | | | ✓ |
| View platform analytics | | | | ✓ |

Authorization is enforced in **three** places, deliberately:

1. **The gateway** rejects requests to protected routes with no valid token.
2. **Each service's `SecurityFilterChain`** re-validates the same token independently, so a service
   reached directly is still protected.
3. **`@PreAuthorize` on controllers**, plus ownership checks in the service layer, because the
   gateway has no idea whether user 7 owns land 42.

---

## Asynchronous messaging

All events flow through one topic exchange, `earthscan.events`.

| Routing key | Publisher | Consumers | Effect |
|---|---|---|---|
| `user.registered` | auth | notification | Role-specific welcome notification |
| `user.role-changed` | auth | notification | "Sign out and back in" notice |
| `user.deleted` | auth | land, forum, notification | Cascade cleanup across three services |
| `land.listed` | land | notification | Publication confirmation with scores |
| `forum.post.created` | forum | notification | Acknowledgement to the author |
| `forum.comment.added` | forum | notification | "Your question was answered" |

**`user.deleted` is the event that earns the architecture.** An admin deletes a user;
`land-service` removes their listings and saved searches, `forum-service` anonymises their posts
while preserving thread readability, and `notification-service` purges their inbox. Synchronously,
the delete endpoint would be coupled to the availability of all three.

**Reliability measures:**
- Every queue has a dead-letter binding to `earthscan.events.dlx` → `earthscan.dlq`
- Bounded retry with exponential backoff (4 attempts, 2s → 20s)
- `default-requeue-rejected: false`, so an exhausted message goes to the DLQ rather than
  poison-looping
- Consumers are **idempotent**: RabbitMQ guarantees at-least-once delivery, so redelivery is normal.
  Notifications carry a `sourceEventId` with a unique index; cleanup handlers are naturally
  idempotent because deleting already-deleted rows affects zero rows.

**Inspecting the bus:** RabbitMQ management UI at http://localhost:15672. A non-empty
`earthscan.dlq` means something is failing repeatedly and is worth investigating.

**Known trade-off:** events are published after the database transaction commits, not through a
transactional outbox. A crash in the narrow window between commit and publish loses an event. This
is accepted because none of these events carry money or state the publisher owns; a real outbox
would be the right call if they did.

---

## Databases

Two database technologies, chosen per workload rather than for the sake of variety.

### MySQL — `earthscan_auth`

Normalised to 3NF. The original schema stored `Role` as a `VARCHAR(20)` on the user row; promoting
it to its own table means the set of legal roles is enforced by the schema instead of by convention.

```
roles                          users                        user_roles
─────                          ─────                        ──────────
id            PK               id             PK            user_id  PK,FK → users
name          UNIQUE           name                         role_id  PK,FK → roles
display_name                   email          UNIQUE
description                    password_hash
                               enabled
                               created_at
                               updated_at
```

### MySQL — `earthscan_land`

```
soil_types                     lands                            saved_searches
──────────                     ─────                            ──────────────
id             PK              id                  PK           id              PK
name           UNIQUE          title                            user_id         (logical)
fertility_index                description                      label
water_retention_index          location                         location_query
description                    district                         soil_type_name
                               state                            min_price
                               latitude, longitude              max_price
                               price      DECIMAL(18,2)         min_score
                               size_in_acres                    land_id         (logical)
                               soil_type_id        FK →          notify_on_match
                               groundwater_level_depth           created_at
                               annual_rainfall_mm
                               irrigation_available
                               road_access
                               land_intelligence_score
                               borewell_success_probability
                               scored_at
                               owner_id            (logical)
                               status, verified
                               created_at, updated_at
```

`owner_id` and `user_id` are plain `BIGINT`, not foreign keys. The referenced user lives in another
service's schema, and a cross-schema constraint would re-couple the two databases at exactly the
layer the split exists to separate. Integrity is maintained by the `user.deleted` event instead —
which is why those consumers must be idempotent.

`price` is `DECIMAL(18,2)`, never a float: land prices run to crores and binary floating point
cannot represent them exactly.

### MongoDB — `earthscan_forum`

A thread is one document with its replies embedded:

```javascript
{
  _id: ObjectId,
  title, content, category,
  authorId, authorName, authorRole,
  createdAt, updatedAt,
  commentCount,                    // denormalised for the feed
  resolved, anonymised,
  comments: [ { id, content, authorId, authorName, authorRole, createdAt, anonymised } ]
}
```

**Why document, not relational, here:** a thread is read as a whole and essentially never joined.
Relationally that was two tables and a join on every page load, with comment rows existing only to
be reassembled into their parent. Embedding makes the feed one query with no joins.

**The trade-off, stated plainly:** MongoDB caps a document at 16 MB, so a thread cannot grow without
bound, and there are no cross-document transactions in play. Both are fine for a Q&A thread. Neither
would be acceptable for user or land data, which is why those stayed relational.

Indexes: `{category, createdAt}` compound, `authorId`, `createdAt` descending.

### MongoDB — `earthscan_notifications`

```javascript
{
  _id: ObjectId,
  userId, type, title, message, actionPath,
  read, createdAt, readAt,
  sourceEventId                    // unique with userId — this is what makes consumers idempotent
}
```

Indexes: `{userId, createdAt}`, `{userId, read}`, and **unique** `{sourceEventId, userId}`.

### Schema management

`spring.jpa.hibernate.ddl-auto=update` by default, which keeps first-run friction low. It never
drops or narrows a column so it cannot destroy data, but it also cannot express a rename or a
backfill.

**For anything beyond local development, set `JPA_DDL_AUTO=validate` and manage the schema with
Flyway.** The runnable DDL in `database/mysql/schema/` reproduces the schema exactly and is the
natural baseline for a `V1__baseline.sql`. The `prod` profile already sets `validate`.

---

## Testing

```bash
cd backend
mvn test                              # all modules
mvn -pl land-service test             # one module
mvn -pl common-lib test -Dtest=JwtTokenProviderTest
mvn verify                            # full reactor
```

**14 test classes, 190 test methods, across five layers.** Each layer exists because the one below it
structurally cannot cover what it covers:

| Layer | Classes | What only this layer can prove |
|---|---|---|
| Unit (Mockito) | 5 | Business logic and branch behaviour in isolation |
| Contract (`ScoringFactorContractTest`) | 1 | Every Strategy implementation honours the 0–100 invariant — LSP made executable |
| Repository (`@DataJpaTest`, `@DataMongoTest`) | 2 | Derived query names parse, constraints exist, embedded-array queries match |
| Controller (`@WebMvcTest` + MockMvc) | 2 | Status codes, JSON field names, whether validation annotations actually fire, whether `?page=2&size=5` binds |
| Integration (`@SpringBootTest`) | 1 | The real filter chain rejects a forged token; RBAC returns 403 not 500 |

**JUnit 5 + Mockito 5 + AssertJ.** Coverage focuses on logic that can actually be wrong:

| Test class | What it proves |
|---|---|
| `JwtTokenProviderTest` | Real crypto: rejects forged signatures, wrong issuer/audience, expiry, `alg:none` |
| `RoleNameTest` | Display-name mapping and the `ROLE_` prefix that `hasRole()` depends on |
| `AuthServiceTest` | Password hashing, duplicate-email conflict, timing-attack mitigation, disabled accounts |
| `UserAdminServiceTest` | Last-admin and self-deletion guards |
| `LandScoringServiceTest` | Monotonicity and bounds of the scoring model |
| `LandServiceTest` | Ownership authorization, server-side scoring, analysis verdicts |
| `ForumServiceTest` | Author identity from token, anonymisation preserving content |
| `NotificationEventListenerTest` | Idempotency under redelivery and concurrent duplicates |

A note on test style: the scoring tests assert **properties** (wetter land never scores worse than
drier land; probability never reaches 100%) rather than exact values. Asserting that a given input
yields exactly `73.4` would break on every legitimate recalibration, while the properties must hold
for any calibration and are what actually matters.

---

## Configuration reference

Every setting is an environment variable with a development default. Nothing needs a code change to
deploy.

### Shared

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | dev placeholder | **Required in production.** ≥32 chars or startup fails |
| `JWT_ISSUER` | `EarthScanBackend` | |
| `JWT_AUDIENCE` | `EarthScanUsers` | |
| `JWT_EXPIRATION_MS` | `604800000` (7 days) | Consider shortening for production |
| `EUREKA_SERVER_URL` | `http://localhost:8761/eureka/` | |
| `RABBITMQ_HOST` / `_PORT` / `_USER` / `_PASSWORD` | `localhost` / `5672` / `guest` / `guest` | |
| `LOG_LEVEL_EARTHSCAN` | `INFO` | |

### auth-service / land-service

| Variable | Default |
|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3306` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `root` / `root` |
| `MYSQL_AUTH_DB` / `MYSQL_LAND_DB` | `earthscan_auth` / `earthscan_land` |
| `JPA_DDL_AUTO` | `update` — use `validate` in production |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@earthscan.in` |
| `BOOTSTRAP_ADMIN_PASSWORD` | *(empty — no admin seeded)* |
| `SEED_DEMO_LISTINGS` | `false` |

### forum-service / notification-service

| Variable | Default |
|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/earthscan_forum` |
| `MONGODB_NOTIFICATIONS_URI` | `mongodb://localhost:27017/earthscan_notifications` |

### api-gateway

| Variable | Default |
|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` — **must include your real frontend origin** |

### frontend

| Variable | Default | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Inlined at **build** time, not runtime |

---

## Deployment to AWS

See **[`documentation/DEPLOYMENT.md`](documentation/DEPLOYMENT.md)** for full walkthroughs. Summary of options:

| Option | Best for | Effort |
|---|---|---|
| **Single EC2 + Docker Compose** | Demos, coursework, small pilots | Lowest |
| **ECS Fargate + ALB** | Production, no server management | Medium |
| **EKS** | Existing Kubernetes practice | Highest |

The quickest path — a `t3.large` running the compose stack — is documented step by step, including
security groups, Elastic IP, and switching to RDS MySQL plus DocumentDB and Amazon MQ when you
outgrow containerised data stores.

**Before any public deployment, work through the security checklist in `documentation/DEPLOYMENT.md`.** The
most important items: rotate `JWT_SECRET`, put the gateway behind HTTPS, set
`CORS_ALLOWED_ORIGINS` to your real origin, change the RabbitMQ credentials, switch
`JPA_DDL_AUTO` to `validate`, and fix the password-reset flow described below.

---

## Security notes and known limitations

These are documented rather than hidden. Read this section before deploying anything publicly.

### Carried over from the original, and still a problem

**`POST /api/auth/reset-password` performs no ownership verification.** Knowing an email address is
sufficient to change that account's password. This was reproduced for feature parity, and it is the
single most important thing to fix. A correct implementation needs an emailed, single-use,
time-limited token. **Do not expose this endpoint to the internet as it stands.**

### Deliberate trade-offs

| Decision | Trade-off |
|---|---|
| Symmetric HS256 with a shared secret | Every service can validate locally with no network hop, but every service can also *mint* tokens. Asymmetric RS256 with only auth-service holding the private key would be stronger. |
| 7-day token lifetime, no refresh tokens | Fewer logins, but a leaked token is valid for a week and there is no revocation. Shorten it, or add refresh tokens with a revocation list. |
| Tokens in `localStorage` | Survives reloads and works with the existing frontend, but is readable by any XSS. `httpOnly` cookies plus CSRF protection would be stronger. |
| Events published post-commit | Simple, but a crash between commit and publish loses an event. A transactional outbox would close this. |
| `ddl-auto=update` | Zero-setup first run, but no migration history. Flyway before production. |
| No rate limiting | Login and reset-password are unthrottled. Add Spring Cloud Gateway's `RequestRateLimiter` or a WAF. |

### What is actively defended

- BCrypt (strength 10) password hashing, plaintext never leaving the DTO layer
- Constant-time login: a dummy hash comparison runs even when the email does not exist, closing the
  timing side channel that otherwise reveals which addresses hold accounts
- Uniform "Invalid email or password" — never revealing which half was wrong
- Server-side scoring and ownership assignment: clients cannot inflate their own listing's score or
  claim another user's listing
- Ownership queries scoped by user id (`findByIdAndUserId`), returning 404 rather than 403, so an
  attacker enumerating ids learns nothing
- Explicit CORS allow-list, replacing the original's reflect-any-origin behaviour
- Identity headers stripped at the gateway on unauthenticated routes, preventing `X-User-Id`
  spoofing
- Generic error responses: database messages, SQL and stack traces never reach the client
- Last-admin and self-deletion guards, preventing an admin from locking every administrator out
- Passwords and query strings excluded from access logs
- Non-root container user
- `mandatory` publishing with a returns callback, so an unroutable message is logged rather than
  silently dropped

---

## What changed from the ASP.NET version

| Area | Before | After |
|---|---|---|
| Architecture | Single ASP.NET Core 8 project | 4 business services + gateway + registry |
| Role storage | `VARCHAR(20)` on the user row | `roles` + `user_roles`, 3NF |
| Soil type | Free text; scoring did `SoilType.Contains("Black")` | `soil_types` table with fertility and retention indices |
| Scoring | Base 50, flat `+20` bumps | Five weighted components; weights asserted to sum to 1.0 |
| Borewell probability | Three buckets (90/65/30%) | Continuous curve — 49.9 m and 50.1 m no longer differ by 25 points |
| Input validation | None on any DTO | Bean Validation throughout, mirrored client-side |
| Error handling | Per-controller ad hoc | One `@RestControllerAdvice`, uniform `ApiErrorResponse` |
| Write authorization | `POST /api/Lands` unauthenticated; entity bound from body | Role + ownership checks; scores and `ownerId` server-side only |
| CORS | `SetIsOriginAllowed(origin => true)` | Explicit allow-list at the gateway |
| Logging | Framework defaults | Correlation ids via MDC, access log, structured levels |
| Tests | None | 8 test classes, JUnit 5 + Mockito |
| Saved searches | Browser `localStorage` only | Server-persisted, enabling match notifications |
| API docs | Swashbuckle defaults | Annotated OpenAPI, aggregated at the gateway |
| Frontend API calls | `http://localhost:5130` hard-coded in 9 places | One configurable axios client with interceptors |
| Last-admin protection | A `// Optional:` comment | Enforced |

### One incompatible change

Forum post ids are now **MongoDB ObjectId strings** (`"66a3f1c2e8b4a51d3c7f9012"`) rather than
integers. The frontend uses them opaquely as React keys and URL segments so nothing breaks there,
but any external client doing arithmetic on a post id needs updating.

---

## Project layout

```
earthscan-bharat/
├── backend/                     Maven reactor — 7 modules, 151 types
│   ├── pom.xml                  Lombok + MapStruct processors, explicitly ordered
│   ├── common-lib/              JWT, exceptions, events, RabbitMQ topology,
│   │                            ApiResponse envelope, shared logback-spring.xml
│   ├── discovery-server/        Eureka registry
│   ├── api-gateway/             Spring Cloud Gateway — routes, CORS, coarse auth
│   ├── auth-service/            Identity, JWT issuance, admin              [MySQL]
│   │   ├── security/            UserDetails adapter + UserDetailsService
│   │   └── mapper/              MapStruct
│   ├── land-service/            Listings, scoring, saved searches          [MySQL]
│   │   ├── service/scoring/     5 ScoringFactor strategies + borewell calculator
│   │   └── mapper/              MapStruct
│   ├── forum-service/           Community Q&A                            [MongoDB]
│   └── notification-service/    Pure event consumer                      [MongoDB]
│       └── factory/             5 NotificationFactory beans + registry
│
├── frontend/                    React 19 + Vite
│   └── src/api/                 Centralised axios client + per-service modules
│
├── database/
│   ├── mysql/init/              Container bootstrap, least-privilege grants
│   ├── mysql/schema/            Runnable DDL — Flyway baseline starting point
│   └── mongodb/init/            Collections and indexes as DDL
│
├── docker/                      Run compose from HERE
│   ├── docker-compose.yml
│   ├── backend.Dockerfile       Parameterised — one file builds any service
│   ├── frontend.Dockerfile
│   ├── nginx.conf
│   └── .env.example
│
└── documentation/
    ├── SRS.md                   Software Requirements Specification
    ├── ER-DIAGRAM.md            Schema, DDL, normalisation analysis
    ├── ARCHITECTURE.md          Service boundaries and event flows
    ├── DEPLOYMENT.md            AWS walkthroughs + security checklist
    ├── VERIFICATION-REPORT.md   Audit findings and readiness score
    └── presentation/            Project deck
```

---

## Troubleshooting

**Services keep restarting on `docker compose up`**
Almost always `JWT_SECRET` being unset or under 32 characters — the services fail fast by design.
Check with `docker compose logs auth-service | head -30`.

**`Table 'earthscan_land.lands' doesn't exist`**
MySQL was not ready when the service connected. `docker compose restart land-service`. The
healthchecks should prevent this; if it recurs, raise the MySQL `start_period`.

**Gateway returns 503 for every route**
Services have not registered with Eureka yet. Check http://localhost:8761 — you should see six
instances. Registration takes up to 30 seconds after a service starts.

**401 on every request after logging in**
The services are using different `JWT_SECRET` values. In compose they all read the same `.env`; when
running locally, export the variable in every terminal.

**CORS errors in the browser console**
Add your frontend origin to `CORS_ALLOWED_ORIGINS` and restart the gateway. Note that CORS is
configured *only* at the gateway — enabling it in a service too produces duplicate
`Access-Control-Allow-Origin` headers, which browsers reject.

**`earthscan.dlq` has messages in it**
A consumer is failing repeatedly. Inspect the message in the RabbitMQ UI and check that service's
logs for the correlation id.

**Forum returns 401 but land search works**
Expected. The forum is members-only; land browsing is public.

**Frontend calls `localhost:8080` after deploying**
`VITE_API_BASE_URL` is inlined at build time. Rebuild the image with the correct value — changing
the environment variable on a running container has no effect.

---

## Licence

MIT
