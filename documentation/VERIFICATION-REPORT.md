# Architectural Audit & Refactor — Verification Report

**Project:** EarthScan Bharat
**Date:** 29 July 2026
**Scope:** Full architectural audit, restructure and refactor against the stated requirements

---

## 0. Read this first — the honest caveat

**None of this code has been compiled.** The environment this work was performed in has a Java
runtime but no `javac`, no Maven, and no network access to resolve dependencies. The same applies to
the frontend: `npm install` has never run.

Every claim below is verified by **static inspection** — 151 Java types indexed, every internal
import resolved against a declared type, brace and paren balance checked across every file, and every
reference to a refactored symbol confirmed removed. That is real verification, and it is not the same
thing as a green build.

**Two items on your list are annotation processors and therefore the highest-risk changes here.**
Lombok and MapStruct were both entirely absent and are now wired in. They are configured explicitly
in `backend/pom.xml` — declared processor paths, Lombok ordered before MapStruct,
`lombok-mapstruct-binding` present — precisely because the default behaviour fails silently rather
than loudly. If the build breaks, look there first. Section 9 explains the specific failure mode.

**`mvn clean install` is step one.** Please run it and send me the errors.

---

## 1. What the audit actually found

You asked me not to assume anything was already implemented. Good instruction — the audit found
substantial genuine gaps, not cosmetic ones:

| Requirement | State before this pass |
|---|---|
| Lombok | **Entirely absent** |
| MapStruct | **Entirely absent** |
| Profiles (dev / test / prod) | **Absent** — one `application.yml` per service, no profile blocks |
| Logback configuration | **Absent** — Spring Boot defaults only |
| Custom `UserDetailsService` | **Absent** — and a code comment actively arguing against having one |
| Centralized API response structure | **Absent** |
| `@WebMvcTest` / MockMvc | **Zero files** |
| `@DataJpaTest` | **Zero files** |
| `@DataMongoTest` | **Zero files** |
| `@SpringBootTest` (integration) | **Zero files** |
| Service interfaces (Dependency Inversion) | **Absent** — every service a concrete class |
| Strategy pattern | **Absent** |
| Factory pattern | **Absent** |
| Facade pattern | **Absent** (see §5 for why it stayed absent) |
| Project structure `backend/frontend/database/docker/documentation` | **Absent** — flat layout |

Everything in that table has been addressed except the Facade, which I deliberately did not add and
explain below.

---

## 2. Requirement verification

Status key: **Already Present** · **Improved** · **Refactored** · **Newly Added**

### 2.1 Technology stack

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | Spring Boot (latest stable) | Already Present | 3.3.4 in `backend/pom.xml` |
| 2 | Spring Security | **Refactored** | Now includes `UserDetailsService` + `DaoAuthenticationProvider`; see §2.6 |
| 3 | Spring Data JPA | Already Present | `auth-service`, `land-service` |
| 4 | Hibernate | Already Present | Via JPA starter; dialect and batch size configured |
| 5 | React.js | Improved | React 19 + Vite; API layer centralized |
| 6 | REST APIs | Improved | Score-breakdown endpoint added; response envelope available |
| 7 | Maven | Already Present | Multi-module reactor, 7 modules |
| 8 | MySQL | Already Present | Two independent schemas |
| 9 | MongoDB | Already Present | Two databases, genuinely document-shaped |
| 10 | RabbitMQ | **Refactored** | Listeners now transport-only; content moved to factories |
| 11 | Docker | Improved | Relocated to `docker/`, profile activation added |
| 12 | JWT Authentication | Already Present | HS256, issuer/audience/expiry validated |
| 13 | JUnit 5 | Improved | 14 test classes, 190 test methods |
| 14 | Mockito | Already Present | 5 pure-unit test classes |
| 15 | **Lombok** | **Newly Added** | `@Getter`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` |
| 16 | **MapStruct** | **Newly Added** | 3 mappers, `unmappedTargetPolicy=ERROR` |
| 17 | OpenAPI / Swagger | Already Present | springdoc 2.6.0, aggregated at gateway |

### 2.2 Architecture

| Requirement | Status | Notes |
|---|---|---|
| Layered architecture | Already Present | Controller → Service → Repository throughout |
| Controller → Service → Repository | Already Present | No controller touches a repository |
| DTO pattern | Improved | Java records; now populated by MapStruct rather than static factories |
| Repository pattern | Already Present | Spring Data interfaces only, no hand-rolled DAOs |
| Dependency injection | **Improved** | Constructor injection everywhere; now against interfaces in the scoring path |
| Global exception handling | Already Present | One `@RestControllerAdvice` in `common-lib`, 12 handlers |
| Configuration classes | Already Present | `SecurityConfig`, `OpenApiConfig`, `RabbitConfig`, topology configs |
| Utility classes | Already Present | `SecurityUtils`, `ScoringMath`, `OpenApiFactory` — all `final` with private constructors |
| Validation layer | Already Present | Bean Validation on every DTO, mirrored client-side |
| Logging | **Newly Added** | `logback-spring.xml` with per-profile appenders |
| **Centralized API response** | **Newly Added** | `ApiResponse<T>` + `PageMetadata` |

**On the response envelope — a deliberate deviation from a naive reading of your requirement.** I did
*not* retrofit `ApiResponse<T>` onto every existing endpoint. Doing so would change the JSON shape of
every response and break all 20 pages of the existing React application in a single commit. The
envelope exists, is documented, and is used by new endpoints; existing endpoints keep their shape so
the frontend can migrate one call at a time. If you want the breaking change applied wholesale, say
so and I'll do it — but it should be a decision, not a side effect.

### 2.3 SOLID

| Principle | Status | What changed |
|---|---|---|
| Single Responsibility | **Refactored** | `LandScoringService` was computing five factors *and* borewell probability *and* combining them. Now: 5 factor classes + `BorewellProbabilityCalculator` + a combiner. RabbitMQ listeners no longer compose message text. |
| Open/Closed | **Refactored** | A new scoring factor is a new `@Component` — no edit to the combiner. A new notification type is a new factory — no edit to any listener. Previously both meant editing existing classes. |
| Liskov Substitution | Improved | `ScoringFactorContractTest` applies the same invariants to every implementation, so a substitute cannot violate the contract silently. This is LSP made *executable* rather than asserted in prose. |
| Interface Segregation | Improved | `ScoringFactor` has 4 methods; `NotificationFactory` has 2. Neither forces an implementor to stub anything. |
| Dependency Inversion | **Refactored** | `LandService` now depends on `LandScoringUseCase`, not the implementation. |

**Honest limitation:** I did **not** extract interfaces for `AuthService`, `LandService`,
`ForumService` or `UserAdminService`. A single-implementation interface added purely to satisfy a DIP
checkbox is a well-known anti-pattern — it doubles the file count, adds an indirection every reader
must follow, and inverts nothing, because there is no second implementation and no plausible one.
DIP was applied where a substitution is genuinely foreseeable (scoring, which will be replaced by a
calibrated model). If your rubric requires an interface per service regardless, tell me and I'll add
them, but I'd be adding code I think makes the project worse.

### 2.4 Design patterns

| Pattern | Status | Where, and why it belongs there |
|---|---|---|
| Repository | Already Present | Spring Data across MySQL and MongoDB |
| DTO | Already Present | Records for every request and response |
| Dependency Injection | Already Present | Constructor-only |
| Singleton | Already Present | Spring-managed beans |
| **Strategy** | **Newly Added** | `ScoringFactor` — 5 implementations composed at runtime |
| **Factory** | **Newly Added** | `NotificationFactory` + registry, 5 implementations |
| **Builder** | **Newly Added** | Lombok `@Builder` on `ApiResponse`, `ScoringContext`, `ScoreBreakdown` |
| Observer | Already Present | RabbitMQ publish/subscribe, 6 event types |
| Adapter | **Newly Added** | `EarthScanUserDetails` adapts the JPA entity to Spring Security |
| Registry | **Newly Added** | `NotificationFactoryRegistry` — fails at startup on a duplicate claim |
| **Facade** | **Deliberately not added** | See below |

**Why no Facade.** The honest answer is that there is nothing for it to hide. A facade earns its place
when it simplifies a genuinely complex subsystem interaction — several services that must be called
in a particular order, with shared error handling. Here the controllers each call one service, and
that service is already the coarse-grained entry point a facade would be. Adding
`LandFacade → LandService` would be a pass-through class: one more file, one more hop, zero
simplification. Your requirement said "if beneficial," and I judged it isn't. Happy to be
overruled.

### 2.5 Microservices

| Requirement | Status |
|---|---|
| At least two services | Already Present — **four** business services plus gateway and registry |
| Independent responsibility | Already Present |
| Independent configuration | **Improved** — each now has dev/test/prod profiles |
| Independent database | Already Present — 4 databases, no shared schema, no cross-service FK |
| REST and/or RabbitMQ communication | Already Present |
| Independent startup | Already Present — health-gated ordering in compose |
| Proper package structure | Improved |

### 2.6 Spring Security

| Requirement | Status | Notes |
|---|---|---|
| Security configuration | Already Present | Per-service `SecurityFilterChain` |
| JWT authentication | Already Present | HS256; signature, issuer, audience, expiry all validated |
| JWT authorization | Already Present | Roles as authorities |
| Role-based access control | Already Present | `@PreAuthorize` + ownership checks |
| BCrypt | Already Present | Strength 10 |
| Authentication filter | Already Present | `JwtAuthenticationFilter` (deliberately not a `@Component` — see below) |
| Authorization filter | Already Present | `@EnableMethodSecurity` |
| **Custom `UserDetailsService`** | **Newly Added** | `EarthScanUserDetailsService` + `DaoAuthenticationProvider` |
| Stateless authentication | Already Present | `SessionCreationPolicy.STATELESS` |
| Protected / public endpoints | Already Present | Explicit matchers |
| CORS | Already Present | Explicit allow-list at the gateway only |
| CSRF | Already Present | Disabled with justification — no cookie session to protect |

**The `UserDetailsService` refactor removed hand-written security code, which is the real win.** The
previous `AuthService.login()` compared BCrypt hashes itself and carried a hand-rolled
timing-attack mitigation: a dummy hash comparison so that "no such user" took as long to reject as
"wrong password". That code was correct, but `DaoAuthenticationProvider` already does exactly this
(`mitigateAgainstTimingAttack`). Keeping a second copy of a subtle security control is the worst kind
of duplication — the two can drift, and only one gets reviewed. The custom code is gone and the
framework's is used.

`hideUserNotFoundExceptions` is left at its default `true`, with a comment saying why: flipping it for
nicer error messages turns the login endpoint into a user-enumeration oracle.

**Note on `JwtAuthenticationFilter` not being a `@Component`:** any bean of type `Filter` is also
picked up by Boot's servlet auto-registration and would run twice — once outside the security chain.
It is instantiated explicitly in each `SecurityFilterChain`. This was already correct and is
called out because it looks like an omission.

### 2.7 Database

| Requirement | Status |
|---|---|
| MySQL entity relationships | Already Present |
| Constraints | **Improved** — `CHECK` constraints added to the reference DDL |
| Indexes | Already Present — 4 on `lands` including one composite |
| Normalization | Already Present — 3NF/BCNF, documented |
| Spring Data JPA | Already Present |
| MongoDB collections | Already Present |
| Meaningful NoSQL usage | Already Present — embedded replies, genuinely document-shaped |
| Repository configuration | Already Present |
| **Runnable schema DDL** | **Newly Added** | `database/mysql/schema/`, `database/mongodb/init/` |

### 2.8 REST APIs

| Requirement | Status |
|---|---|
| CRUD | Already Present |
| Correct HTTP methods | Already Present — including `PATCH` for partial state changes |
| Standard status codes | Already Present — 200/201/204/400/401/403/404/409/500/503 |
| Validation | Already Present |
| Exception handling | Already Present |
| Pagination | Already Present — **now actually tested** at request level |
| Sorting | Already Present |
| Filtering | Already Present — 10 optional filters via JPA Specifications |
| Standard response format | **Newly Added** — opt-in, see §2.2 |

### 2.9 Testing

| Layer | Before | After |
|---|---|---|
| Service (Mockito) | 5 classes | 5 classes, updated for refactors |
| **Repository (`@DataJpaTest`)** | **0** | **`UserRepositoryTest`** — 11 tests |
| **Repository (`@DataMongoTest`)** | **0** | **`ForumPostRepositoryTest`** — 9 tests |
| **Controller (`@WebMvcTest`)** | **0** | **`AuthControllerTest`, `LandControllerTest`** — 25 tests |
| **Integration (`@SpringBootTest`)** | **0** | **`AuthIntegrationTest`** — 17 tests, real filter chain |
| **Contract (Strategy invariants)** | **0** | **`ScoringFactorContractTest`** — 11 tests |
| **Total** | 8 classes | **14 classes, 190 test methods** |

The new tests were chosen to cover what mocks structurally cannot:

- `UserRepositoryTest` proves the `countByRoles_Name` property path resolves across the join table.
  That path is what the last-admin guard depends on; if it stopped resolving, the guard would
  silently always pass and an admin could lock every administrator out.
- `ForumPostRepositoryTest` proves `{ 'comments.authorId': ?0 }` matches inside an embedded array.
  That query drives account-deletion anonymisation — if it broke, a deleted user's name would stay
  visible on every reply they ever wrote, with no error anywhere.
- `AuthIntegrationTest` proves a token forged with a foreign secret is rejected by the real chain.
  Every unit test can pass while the application accepts forged tokens because a filter is
  registered in the wrong order.
- `LandControllerTest` proves `?page=2&size=5` actually binds into a `Pageable`. A service test
  receives whatever `Pageable` it is handed and can never detect broken query-parameter wiring.

**Coverage is untested, not just unmeasured.** With no Maven I cannot run JaCoCo or the suite itself.
I will not quote a coverage percentage I have not measured.

### 2.10 Production readiness

| Requirement | Status |
|---|---|
| Environment-based configuration | Already Present |
| Externalized configuration | Already Present |
| **Profiles (dev/test/prod)** | **Newly Added** |
| **Logging configuration** | **Newly Added** — JSON to stdout in prod |
| Exception logging | Already Present — 4xx WARN no trace, 5xx ERROR with trace |
| API documentation | Already Present |
| Input validation | Already Present |
| Secure configuration | Improved — `prod` forbids `ddl-auto=update`, restricts actuator |
| Clean architecture | Improved |
| Package structure | Improved |
| No duplicate code | **Improved** — removed a duplicated timing-attack mitigation and 5 duplicated notification-message blocks |
| No dead code | Improved — removed unused `formatRupees` helper |
| Optimized queries | Already Present |
| Performance | Already Present |

---

## 3. Project structure

```
earthscan-bharat/
├── backend/                    Maven reactor, 7 modules, 151 types
│   ├── pom.xml                 Lombok + MapStruct processors, explicitly ordered
│   ├── common-lib/             Cross-cutting: JWT, exceptions, events, ApiResponse, logback
│   ├── discovery-server/       Eureka
│   ├── api-gateway/            Spring Cloud Gateway
│   ├── auth-service/           + security/ (UserDetails), mapper/     [MySQL]
│   ├── land-service/           + service/scoring/ (5 strategies), mapper/  [MySQL]
│   ├── forum-service/          + mapper/                             [MongoDB]
│   └── notification-service/   + factory/ (5 factories + registry)   [MongoDB]
├── frontend/                   React 19 + Vite
├── database/
│   ├── mysql/init/             Container bootstrap, least-privilege grants
│   ├── mysql/schema/           Runnable DDL — Flyway baseline starting point
│   └── mongodb/init/           Collections and indexes as DDL
├── docker/
│   ├── docker-compose.yml      Repathed for the new layout
│   ├── backend.Dockerfile      Parameterised — one file builds any service
│   ├── frontend.Dockerfile
│   ├── nginx.conf
│   └── .env.example
└── documentation/
    ├── README.md · SRS.md · ER-DIAGRAM.md · ARCHITECTURE.md · DEPLOYMENT.md
    ├── VERIFICATION-REPORT.md  (this file)
    └── presentation/
```

**Docker paths changed and this matters.** Run compose from inside `docker/`. Backend images build
from `../backend`; the frontend image builds from the repository root because it needs both
`frontend/` and `docker/nginx.conf`.

---

## 4. Requested checklist

| Item | Status | Verdict |
|---|---|---|
| Spring Boot | ✔ | Already Present |
| Spring Security | ✔ | **Refactored** |
| JWT Authentication | ✔ | Already Present |
| REST APIs | ✔ | Improved |
| MySQL | ✔ | Already Present |
| MongoDB | ✔ | Already Present |
| RabbitMQ | ✔ | **Refactored** |
| Docker | ✔ | Improved |
| React | ✔ | Improved |
| Spring Data JPA | ✔ | Already Present |
| Hibernate | ✔ | Already Present |
| Microservices (min 2) | ✔ | Already Present — 4 business services |
| SOLID | ✔ | **Refactored** — with one documented deviation (§2.3) |
| Design Patterns | ✔ | **Newly Added** — Strategy, Factory, Builder, Adapter, Registry |
| JUnit Testing | ✔ | Improved — 8 → 14 classes |
| Mockito Testing | ✔ | Already Present |
| Swagger/OpenAPI | ✔ | Already Present |
| Production Config | ✔ | **Newly Added** — profiles |
| Logging | ✔ | **Newly Added** — logback-spring.xml |
| Validation | ✔ | Already Present |
| Security | ⚠ | **Refactored, one open vulnerability** — see §6 |
| Clean Architecture | ✔ | Improved |
| Performance | ✔ | Already Present |
| Code Quality | ✔ | Improved |
| Documentation | ✔ | Improved |
| Lombok | ✔ | **Newly Added** |
| MapStruct | ✔ | **Newly Added** |

---

## 5. Production Readiness Score

## **72 / 100**

You asked for a score. A 100% would be dishonest, so here is the arithmetic.

| Category | Weight | Score | Why not full marks |
|---|---:|---:|---|
| Architecture & design | 15 | 14/15 | Sound and well-documented. Microservices are arguably over-engineered for this scale, which is noted in the docs rather than hidden. |
| Code quality & SOLID | 15 | 13/15 | Strategy/Factory refactors are genuine. One deliberate DIP deviation (§2.3). |
| Security | 20 | **9/20** | **The single biggest deduction.** Password reset is an account-takeover vector. No rate limiting. Symmetric JWT means every service can mint tokens. 7-day tokens with no revocation. |
| Testing | 15 | 9/15 | 190 tests across 5 layers — but **not one has ever been executed**. No coverage measurement. No contract tests between services. |
| Data layer | 10 | 8/10 | Normalized, indexed, documented. `ddl-auto` instead of Flyway migrations. |
| Observability | 10 | 7/10 | Correlation IDs and structured JSON logging. No metrics dashboards, no distributed tracing, no alerting. |
| Deployment & config | 10 | 8/10 | Profiles, Docker, health-gated ordering, AWS guides. Secrets still in `.env` rather than a secrets manager. |
| Documentation | 5 | 5/5 | README, SRS, ER, architecture, deployment, this report. |

**What would move this to 85+:** fix the password reset (+6), get the build and test suite green
(+4), add rate limiting (+2), adopt Flyway (+2).

**What would move it to 95+:** RS256 with an asymmetric key (+3), short-lived tokens with refresh
and revocation (+3), secrets in AWS Secrets Manager (+2), metrics and alerting (+2).

**The score cannot honestly exceed ~75 while the code has never been compiled.** A system that has
not been built is not production-ready by definition, regardless of how good the code reads.

---

## 6. Open vulnerability — please read

**`POST /api/auth/reset-password` performs no ownership verification.** Knowing an email address is
sufficient to change that account's password and take it over.

This is inherited from the original ASP.NET application, not introduced by this work. It is
reproduced for feature parity and flagged in five places: the README, `SRS.md` §10.1, the endpoint's
OpenAPI description, the DTO Javadoc, and the deployment checklist.

**Do not expose this endpoint to the internet as it stands.** A correct fix needs an emailed,
single-use, time-limited token — which needs an email provider I cannot configure for you. Until
then, block the route at the gateway or load balancer.

Other documented trade-offs, all with reasoning in `README.md`: symmetric HS256, 7-day tokens without
revocation, tokens in `localStorage`, events published post-commit rather than through a transactional
outbox, no rate limiting.

---

## 7. What was removed

Deleting code is part of a refactor, so for the record:

| Removed | Replaced by | Why |
|---|---|---|
| Hand-rolled BCrypt comparison + dummy-hash timing mitigation in `AuthService` | `DaoAuthenticationProvider` | Duplicated a subtle security control the framework already implements |
| Five inline notification-message blocks in RabbitMQ listeners | 5 `NotificationFactory` beans | Listeners had two responsibilities; new event types meant editing existing classes |
| Five private scoring methods in `LandScoringService` | 5 `ScoringFactor` beans | Same Open/Closed problem |
| `LandEventListener.formatRupees` | — | Dead code, never called |
| `ForumPostRepository.findDistinctCategoryBy()` | `MongoTemplate.findDistinct` | Derived single-field projection is not reliably supported and would fail at context startup, not compile time |
| Static `UserResponse.from()` / `LandResponse.from()` as the primary path | MapStruct mappers | Untestable in isolation; a new DTO field silently returned null instead of failing the build |

---

## 8. Next steps, in order

1. **`cd backend && mvn clean install`** — send me the errors. Expect the annotation processors first.
2. **`mvn test`** — 190 methods, none yet executed.
3. **`cd frontend && npm install && npm run build`**.
4. **`cd docker && cp .env.example .env`**, set `JWT_SECRET` (≥32 chars), then
   `docker compose up --build`.
5. **Fix or block the password reset endpoint** before any public exposure.
6. Push to GitHub and deploy — `documentation/DEPLOYMENT.md` has the walkthroughs.

---

## 9. Where the build will most likely break first

Ranked by my own estimate of likelihood, so you know where to look:

1. **Lombok / MapStruct annotation processing.** If `lombok.version` does not resolve from the Boot
   parent, or your JDK is newer than the pinned Lombok supports, you get "cannot find symbol" on
   every generated getter. The specific silent failure to watch for: if Lombok runs *after* MapStruct,
   the generated mappers compile fine and map **nothing** — every field null at runtime, green build.
   That is why the ordering is explicit in `backend/pom.xml`.
2. **MapStruct `unmappedTargetPolicy=ERROR`.** This is set deliberately to catch unmapped fields, and
   it will fail the build on any target property I have not accounted for. Read the error — it names
   the property. This is the setting doing its job, not a misconfiguration.
3. **`@DataMongoTest` needing embedded MongoDB.** `de.flapdoodle.embed.mongo.spring3x` 4.13.1
   downloads a MongoDB binary on first run. Behind a proxy, it fails.
4. **`AuthIntegrationTest` H2 dialect.** The `test` profile uses H2 in MySQL mode. A MySQL-specific
   DDL construct in an entity mapping would surface here.
5. **`ScoringContext` as a record with a `@Builder`.** Lombok's `@Builder` on a record works, but is
   less travelled than on a class.

I would rather tell you where to look than pretend this is guaranteed to build.
