# Software Requirements Specification

## EarthScan Bharat — Land Intelligence & Agricultural Advisory Platform

| | |
|---|---|
| **Version** | 1.0 |
| **Date** | July 2026 |
| **Status** | Baseline |
| **Prepared for** | Spring Boot microservices migration of the EarthScan Bharat platform |

---

## Table of contents

1. [Introduction](#1-introduction)
2. [Overall description](#2-overall-description)
3. [Functional requirements](#3-functional-requirements)
4. [Non-functional requirements](#4-non-functional-requirements)
5. [External interface requirements](#5-external-interface-requirements)
6. [System architecture](#6-system-architecture)
7. [Data requirements](#7-data-requirements)
8. [Security requirements](#8-security-requirements)
9. [Constraints, assumptions and dependencies](#9-constraints-assumptions-and-dependencies)
10. [Known limitations and deferred requirements](#10-known-limitations-and-deferred-requirements)
11. [Appendices](#11-appendices)

---

## 1. Introduction

### 1.1 Purpose

This document specifies the requirements for **EarthScan Bharat**, a web platform that helps Indian
agricultural stakeholders make land decisions using data rather than intuition.

It serves as the baseline specification for the migration of an existing ASP.NET Core 8 monolith to a
Spring Boot microservices architecture. It is written for developers, testers, reviewers and
assessors of the system.

### 1.2 Scope

The platform provides:

- **Land intelligence scoring** — a composite 0–100 agricultural suitability score and an estimated
  borewell success probability, computed from soil classification, groundwater depth, rainfall and
  infrastructure
- **Land marketplace** — search, filter, compare and shortlist agricultural listings
- **Investment analysis** — benchmarking a listing's price per acre against comparable parcels in the
  same district
- **Community advisory** — a Q&A forum connecting farmers to agriculture experts
- **Multilingual interface** — English, Hindi and Marathi
- **Role-based administration** — user management and platform analytics

Explicitly **out of scope** for this version: payment processing, legal title verification, live
satellite imagery ingestion, native mobile applications, and SMS or email delivery of notifications
(notifications are in-app only).

### 1.3 Definitions and acronyms

| Term | Meaning |
|---|---|
| **LIS** | Land Intelligence Score — composite 0–100 agricultural suitability rating |
| **BSP** | Borewell Success Probability — estimated 10–95% chance a borewell yields usable water |
| **Mandi** | Government-regulated agricultural produce market |
| **Regur** | Black cotton soil, characteristic of the Deccan plateau |
| **Murum** | Weathered rock layer beneath shallow soils |
| **Acre** | 4,047 m²; the unit Indian land transactions use |
| **JWT** | JSON Web Token — the stateless authentication credential |
| **RBAC** | Role-Based Access Control |
| **DLQ** | Dead Letter Queue — where repeatedly failing messages are parked |
| **Guntha** | 1/40 acre; a common local subdivision (not currently modelled) |

### 1.4 References

- IEEE 830-1998, Recommended Practice for Software Requirements Specifications
- Spring Boot 3.3 and Spring Cloud 2023.0 reference documentation
- OpenAPI Specification 3.1
- RFC 7519 (JSON Web Token)

---

## 2. Overall description

### 2.1 Product perspective

EarthScan Bharat replaces a common failure in Indian land transactions: buyers and farmers assessing
a parcel's agricultural viability from the seller's description and a site visit alone. Groundwater
depth and soil classification are the two variables that most determine whether land can be farmed
profitably, and neither is visible from the surface.

This version is a **re-architecture, not a rewrite of intent**. All existing user-facing behaviour is
preserved. The changes are structural: service decomposition, asynchronous messaging, schema
normalisation, input validation, and a scoring model that distinguishes cases the previous one
conflated.

### 2.2 User classes and characteristics

| Role | Profile | Technical proficiency | Primary needs |
|---|---|---|---|
| **Farmer** | Owns or cultivates land, often 2–15 acres. Frequently on a mobile device over a variable connection. May prefer Marathi or Hindi | Low to moderate | Water planning, crop and fertiliser guidance, mandi prices, expert answers |
| **Land Buyer** | Investor or prospective cultivator evaluating parcels | Moderate | Search, comparison, investment analysis, shortlisting |
| **Agriculture Expert** | Agronomist or extension officer | Moderate to high | Answering farmer queries, curating crop reference data |
| **Admin** | Platform operator | High | User management, listing verification, analytics |

**Design implication.** The Farmer class drives several decisions that would otherwise look
arbitrary: gzip compression on all text assets, the modest password complexity rule (length over
character classes, since four-class rules push mobile users towards `Password@1`), and Devanagari
support requiring `utf8mb4` rather than MySQL's legacy 3-byte `utf8`.

### 2.3 Operating environment

| Component | Requirement |
|---|---|
| Server runtime | Java 17+ (JRE sufficient at runtime) |
| Relational store | MySQL 8.0+ |
| Document store | MongoDB 6.0+ |
| Message broker | RabbitMQ 3.12+ |
| Client | Any browser supporting ES2020 — Chrome 90+, Firefox 88+, Safari 14+, Edge 90+ |
| Container platform | Docker Engine 24+, Compose v2 |
| Minimum server resources | 4 vCPU, 8 GB RAM for the full stack |

---

## 3. Functional requirements

Priority: **M** = Mandatory, **S** = Should have, **C** = Could have.

### 3.1 Authentication and account management

| ID | Requirement | Priority |
|---|---|---|
| FR-1.1 | The system shall allow registration with name, email, password and one of the four platform roles | M |
| FR-1.2 | The system shall reject a registration whose email already exists, returning HTTP 409 | M |
| FR-1.3 | The system shall hash passwords with BCrypt before storage and shall never persist or log plaintext | M |
| FR-1.4 | The system shall validate all registration input on both client and server: name 2–100 chars, valid email ≤150 chars, password 8–72 chars containing at least one letter and one digit | M |
| FR-1.5 | The system shall issue a signed JWT on successful authentication, containing the user id, name, email and roles | M |
| FR-1.6 | The system shall return an identical error message for an unknown email and an incorrect password, and shall take comparable time in both cases | M |
| FR-1.7 | The system shall refuse authentication for a disabled account | M |
| FR-1.8 | The system shall expose an endpoint allowing a client to validate a stored token and retrieve the current profile | M |
| FR-1.9 | The system shall allow a password reset | M |
| FR-1.10 | The password reset shall require proof of email ownership via a single-use, time-limited token | **S — not implemented; see §10.1** |
| FR-1.11 | The system shall normalise email addresses to lower case before storage and comparison | S |

### 3.2 Authorization

| ID | Requirement | Priority |
|---|---|---|
| FR-2.1 | The system shall enforce role-based access control on every endpoint that is not explicitly public | M |
| FR-2.2 | The system shall restrict all user-administration endpoints to the Admin role | M |
| FR-2.3 | The system shall restrict listing creation to the Farmer and Admin roles | M |
| FR-2.4 | The system shall permit modification or deletion of a listing only by its owner or an Admin | M |
| FR-2.5 | The system shall restrict listing verification to the Admin role, so an owner cannot verify their own listing | M |
| FR-2.6 | The system shall scope every saved-search and notification operation to the authenticated user, returning HTTP 404 rather than 403 for another user's resource | M |
| FR-2.7 | The system shall validate the token independently in each service, so a service reached directly remains protected | M |
| FR-2.8 | The system shall prevent an Admin from deleting or disabling their own account | M |
| FR-2.9 | The system shall prevent removal or demotion of the last remaining Admin account | M |
| FR-2.10 | The system shall permit only the author of a forum thread, or an Admin, to mark it resolved | S |

### 3.3 Land listings and search

| ID | Requirement | Priority |
|---|---|---|
| FR-3.1 | The system shall allow unauthenticated browsing and viewing of active listings | M |
| FR-3.2 | The system shall support paged search with optional filters: free text, district, soil type, price range, size range, minimum score, irrigation, verified-only | M |
| FR-3.3 | The system shall return only ACTIVE listings in search results | M |
| FR-3.4 | The system shall reject an inverted price or size range with HTTP 400 rather than silently returning no results | M |
| FR-3.5 | The system shall validate listing input: title 5–150 chars, price ≥ ₹1,000, size > 0 and ≤ 100,000 acres, groundwater depth 0–1,000 m, coordinates within India's bounding box (6–38°N, 68–98°E) | M |
| FR-3.6 | The system shall derive listing ownership from the authenticated token and shall not accept an owner id from the request body | M |
| FR-3.7 | The system shall compute both derived scores server-side and shall not accept them from the request body | M |
| FR-3.8 | The system shall re-compute both scores whenever a listing is updated | M |
| FR-3.9 | The system shall support listing lifecycle states: ACTIVE, SOLD, DRAFT, WITHDRAWN | S |
| FR-3.10 | The system shall expose the soil-type reference data, including the indices the scoring engine uses | S |
| FR-3.11 | The system shall expose the list of districts that currently have listings | C |
| FR-3.12 | The system shall allow a listing owner to retrieve their own listings regardless of status | S |

### 3.4 Land intelligence scoring

| ID | Requirement | Priority |
|---|---|---|
| FR-4.1 | The system shall compute a Land Intelligence Score in the range 0–100 | M |
| FR-4.2 | The LIS shall be a weighted composite of soil fertility (30%), water access (30%), rainfall (15%), moisture retention (15%) and accessibility (10%) | M |
| FR-4.3 | The component weights shall sum to exactly 1.0, verified at class-initialisation time | M |
| FR-4.4 | The LIS shall be monotonic in each input: improving any single factor shall never lower the score | M |
| FR-4.5 | The system shall compute a Borewell Success Probability in the range 10–95 | M |
| FR-4.6 | The BSP shall vary continuously with groundwater depth, with no discontinuity at any threshold | M |
| FR-4.7 | The BSP shall never return 100, since no heuristic can guarantee water | M |
| FR-4.8 | Groundwater depth shall remain the dominant factor in the BSP, with soil and rainfall acting only as modest recharge modifiers | M |
| FR-4.9 | The system shall derive soil fertility and water-retention figures from the database, so they are correctable without a code change | M |
| FR-4.10 | Scoring shall be deterministic: identical inputs shall always yield identical outputs | M |
| FR-4.11 | The system shall round both scores to one decimal place, not implying greater precision than the model supports | S |
| FR-4.12 | The system shall record when a listing was last scored | S |
| FR-4.13 | Where an input is unknown, scoring shall treat it neutrally for the LIS and pessimistically for the BSP | S |

**Rationale for FR-4.13.** A neutral LIS avoids penalising a listing for incomplete data. A
pessimistic BSP avoids encouraging someone to spend money drilling on the basis of absent evidence.

### 3.5 Investment analysis

| ID | Requirement | Priority |
|---|---|---|
| FR-5.1 | The system shall compute price per acre for any listing | M |
| FR-5.2 | The system shall benchmark a listing's price per acre against the mean for active listings in the same district | M |
| FR-5.3 | The system shall identify comparable listings as those in the same district within ±50% of the subject's area | S |
| FR-5.4 | The system shall classify a listing as UNDERPRICED, FAIRLY_PRICED or OVERPRICED using a ±15% tolerance band | M |
| FR-5.5 | The system shall return INSUFFICIENT_DATA rather than a verdict when fewer than three comparable listings exist | M |
| FR-5.6 | The system shall return a human-readable rationale stating how many comparables the verdict rests on | M |
| FR-5.7 | All monetary arithmetic shall use exact decimal types, never binary floating point | M |

**Rationale for FR-5.5.** A "20% overpriced" verdict derived from one neighbouring listing is worse
than no verdict, because the user cannot see how thin the evidence was.

### 3.6 Saved searches and shortlists

| ID | Requirement | Priority |
|---|---|---|
| FR-6.1 | The system shall persist saved searches server-side, so they follow the user across devices | M |
| FR-6.2 | The system shall allow saving either reusable search criteria or a specific shortlisted listing | S |
| FR-6.3 | The system shall reject a duplicate shortlist entry for the same listing with HTTP 409 | S |
| FR-6.4 | The system shall limit each user to 100 saved searches | S |
| FR-6.5 | The system shall record whether the user wishes to be notified when a new listing matches | C |
| FR-6.6 | The client shall cache the list locally so it renders immediately and degrades gracefully when the API is unreachable | S |

### 3.7 Community forum

| ID | Requirement | Priority |
|---|---|---|
| FR-7.1 | The system shall require authentication for all forum access | M |
| FR-7.2 | The system shall allow any authenticated user to open a thread with a title, body and category | M |
| FR-7.3 | The system shall allow any authenticated user to reply to a thread | M |
| FR-7.4 | The system shall derive author identity and role from the token, never from the request body | M |
| FR-7.5 | The system shall validate thread input: title 5–200 chars, content 10–5,000 chars, reply 2–3,000 chars | M |
| FR-7.6 | The system shall return replies in chronological order | M |
| FR-7.7 | The system shall provide a queue of unanswered threads for Agriculture Experts | S |
| FR-7.8 | The system shall permit deletion of a thread only by its author or an Admin | M |
| FR-7.9 | The system shall permit deletion of a reply only by that reply's author or an Admin — owning the thread shall not confer moderation rights over others' answers | M |
| FR-7.10 | The system shall allow filtering the feed by category | S |
| FR-7.11 | The system shall provide a paged feed variant that omits reply bodies | S |

### 3.8 Notifications

| ID | Requirement | Priority |
|---|---|---|
| FR-8.1 | The system shall generate a notification when a user registers, tailored to their role | M |
| FR-8.2 | The system shall notify a user when an Admin changes their role, instructing them to re-authenticate | M |
| FR-8.3 | The system shall notify a thread author when someone replies to their thread | M |
| FR-8.4 | The system shall not notify a user about their own reply to their own thread | M |
| FR-8.5 | The system shall notify a listing owner when their listing is published, including both scores | S |
| FR-8.6 | The system shall expose no endpoint for creating a notification; notifications shall originate only from domain events | M |
| FR-8.7 | The system shall allow a user to list, count unread, mark read and delete their own notifications | M |
| FR-8.8 | The system shall not create a duplicate notification when an event is redelivered | M |

**Rationale for FR-8.2.** A JWT carries the role it was minted with. After a role change the existing
token remains valid with the *old* role until it expires, so the instruction to sign out and back in
is functionally necessary, not cosmetic.

### 3.9 User administration

| ID | Requirement | Priority |
|---|---|---|
| FR-9.1 | The system shall allow an Admin to list all users | M |
| FR-9.2 | The system shall provide a paged, searchable user list matching on name or email | S |
| FR-9.3 | The system shall allow an Admin to change any user's role, subject to FR-2.8 and FR-2.9 | M |
| FR-9.4 | The system shall allow an Admin to delete a user, subject to FR-2.8 and FR-2.9 | M |
| FR-9.5 | The system shall allow an Admin to enable or disable an account as a reversible alternative to deletion | S |
| FR-9.6 | The system shall provide user counts by role, aggregated in the database rather than in the client | S |
| FR-9.7 | Deleting a user shall remove their listings and saved searches, anonymise their forum contributions, and purge their notifications | M |

**Rationale for FR-9.6.** The previous analytics page downloaded every user row to count roles in the
browser. That works at demo scale and fails at any real scale.

### 3.10 Cross-service data consistency

| ID | Requirement | Priority |
|---|---|---|
| FR-10.1 | Services shall communicate state changes asynchronously via a message broker, not synchronous calls | M |
| FR-10.2 | All domain events shall be published to a single topic exchange with hierarchical routing keys | M |
| FR-10.3 | Every event consumer shall be idempotent, tolerating at-least-once delivery | M |
| FR-10.4 | Every queue shall have a dead-letter binding | M |
| FR-10.5 | Failed message processing shall be retried with bounded exponential backoff before dead-lettering | M |
| FR-10.6 | A message that exhausts its retries shall be dead-lettered, not requeued | M |
| FR-10.7 | A broker failure during publication shall not fail the originating user-facing operation | M |
| FR-10.8 | A consumer receiving a malformed event shall log and acknowledge it rather than dead-lettering repeatedly | S |

**Rationale for FR-10.7.** The business transaction has already committed by the time an event is
published. Failing the HTTP request because the broker hiccuped would show the user an error for an
operation that actually succeeded, and they would probably retry it.

---

## 4. Non-functional requirements

### 4.1 Performance

| ID | Requirement |
|---|---|
| NFR-1.1 | Token validation shall be a local operation requiring no network call to the identity service |
| NFR-1.2 | Listing search shall use database indexes for all filter and sort combinations exposed by the API |
| NFR-1.3 | Search filters shall be applied only for parameters actually supplied, rather than as `(:param IS NULL OR ...)` clauses that defeat index selection |
| NFR-1.4 | Reply counts shall be available without loading reply bodies |
| NFR-1.5 | Aggregate counts shall be computed in the database, not by transferring rows to the client |
| NFR-1.6 | Client search input shall be debounced to avoid one request per keystroke |
| NFR-1.7 | Static frontend assets shall be gzip-compressed and cached with immutable hashed filenames |

### 4.2 Reliability and availability

| ID | Requirement |
|---|---|
| NFR-2.1 | Failure of one service shall not prevent unrelated functionality — a forum outage shall not block authentication |
| NFR-2.2 | Services shall expose health endpoints suitable for container orchestration probes |
| NFR-2.3 | Container startup shall be gated on dependency health, not merely on dependency container start |
| NFR-2.4 | Events shall be durably queued so a consumer outage delays processing rather than losing data |
| NFR-2.5 | Message queues and exchanges shall be durable, surviving broker restart |
| NFR-2.6 | Topology declarations shall be idempotent, so any service may create them and startup order is unconstrained |
| NFR-2.7 | A transient failure of the identity service shall not terminate existing user sessions |

**Rationale for NFR-2.7.** The frontend ends a session only on a definitive 401, 403 or 404 — never
on a network error. Otherwise every deployment would sign all users out.

### 4.3 Security

Detailed in [§8](#8-security-requirements).

### 4.4 Maintainability

| ID | Requirement |
|---|---|
| NFR-4.1 | Cross-cutting concerns (JWT handling, exception mapping, event topology, logging filters) shall be defined once in a shared library |
| NFR-4.2 | Exchange, queue and routing-key names shall be compile-time constants shared between publishers and consumers |
| NFR-4.3 | All configuration shall be externalised as environment variables with development defaults |
| NFR-4.4 | The system shall have unit tests covering authentication, authorization, scoring and event idempotency |
| NFR-4.5 | All API endpoints shall be documented in OpenAPI 3 with request, response and error schemas |
| NFR-4.6 | Documentation shall be aggregated at a single Swagger UI endpoint |
| NFR-4.7 | Deliberate trade-offs and known weaknesses shall be documented in code and in the README |

**Rationale for NFR-4.2.** Publishers and consumers live in different services. Hard-coding the same
string in both is the classic way an event silently stops being delivered; a shared constant makes a
rename a compile error instead of a runtime mystery.

### 4.5 Observability

| ID | Requirement |
|---|---|
| NFR-5.1 | The gateway shall assign a correlation id to every request and propagate it to all downstream services |
| NFR-5.2 | Every log line shall include the correlation id, so one user action can be traced across services |
| NFR-5.3 | Logs shall include the authenticated user id where available |
| NFR-5.4 | Expected failures (4xx) shall log at WARN without a stack trace; unexpected failures (5xx) shall log at ERROR with one |
| NFR-5.5 | Access logs shall exclude request bodies and query strings |
| NFR-5.6 | Health check and documentation endpoints shall be excluded from access logging |
| NFR-5.7 | Event publication and consumption shall be logged with the event id |

**Rationale for NFR-5.5.** Login and password-reset bodies contain plaintext passwords. Logging them
would be a far worse problem than the missing debugging detail.

### 4.6 Usability and accessibility

| ID | Requirement |
|---|---|
| NFR-6.1 | The interface shall be responsive across mobile, tablet and desktop viewports |
| NFR-6.2 | The interface shall support English, Hindi and Marathi |
| NFR-6.3 | Validation errors shall be reported per field, not as a single generic message |
| NFR-6.4 | Server-side field errors shall be surfaced to the user rather than replaced with a generic failure |
| NFR-6.5 | Loading and empty states shall be distinguishable from error states |
| NFR-6.6 | The system shall report the actual outcome of an action rather than optimistically assuming success |

### 4.7 Portability

| ID | Requirement |
|---|---|
| NFR-7.1 | Every service shall be deployable as a container image |
| NFR-7.2 | Containers shall run as a non-root user |
| NFR-7.3 | The JVM shall respect container memory limits |
| NFR-7.4 | The full stack shall be startable with a single command |
| NFR-7.5 | No environment-specific value shall require a code change |

---

## 5. External interface requirements

### 5.1 User interfaces

React 19 single-page application, served by nginx. All application routes are protected by
role-aware route guards. Unmatched paths fall through to the SPA entry point so a page refresh on a
deep link works.

### 5.2 API interface

| Property | Value |
|---|---|
| Style | REST over HTTP/1.1 |
| Payload format | JSON (UTF-8) |
| Authentication | `Authorization: Bearer <JWT>` |
| Entry point | API gateway — clients never address a service directly |
| Error format | Uniform `ApiErrorResponse` across all services |
| Documentation | OpenAPI 3, aggregated Swagger UI |
| Pagination | `page`, `size`, `sort` query parameters; Spring `Page` response envelope |
| Correlation | `X-Correlation-Id` request and response header |

**Standard error payload:**

```json
{
  "timestamp": "2026-07-28T09:15:30.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/auth/register",
  "correlationId": "3f7a9c12-4b8e-4d1a-9e5f-2c8d7b6a1f04",
  "fieldErrors": {
    "password": ["Password must be between 8 and 72 characters"],
    "email": ["Email must be a valid address"]
  }
}
```

`message` is a top-level string because the existing frontend reads
`error.response.data.message`. The remaining fields are additive, so no client breaks.

### 5.3 Message broker interface

| Property | Value |
|---|---|
| Protocol | AMQP 0-9-1 |
| Exchange | `earthscan.events` (topic, durable) |
| Serialisation | JSON via Jackson, with a type header for polymorphic dispatch |
| Dead letter | `earthscan.events.dlx` → `earthscan.dlq` |
| Delivery guarantee | At-least-once; consumers are idempotent |

### 5.4 Event catalogue

| Routing key | Publisher | Consumers | Payload |
|---|---|---|---|
| `user.registered` | auth | notification | userId, name, email, role |
| `user.role-changed` | auth | notification | userId, name, email, previousRole, newRole |
| `user.deleted` | auth | land, forum, notification | userId, name, email |
| `land.listed` | land | notification | landId, title, location, ownerId, price, size, LIS, BSP |
| `forum.post.created` | forum | notification | postId, title, category, authorId, authorName, authorRole |
| `forum.comment.added` | forum | notification | postId, postTitle, commentId, postAuthorId, commenterId, commenterName, commenterRole |

All events carry `eventId` (UUID) and `occurredAt` (timestamp) from a common base type.

---

## 6. System architecture

### 6.1 Service decomposition

| Service | Port | Responsibility | Store |
|---|---|---|---|
| `discovery-server` | 8761 | Service registry | — |
| `api-gateway` | 8080 | Routing, CORS, correlation ids, coarse authentication | — |
| `auth-service` | 8081 | Identity, JWT issuance, user administration | MySQL |
| `land-service` | 8082 | Listings, scoring, saved searches, investment analysis | MySQL |
| `forum-service` | 8083 | Community Q&A | MongoDB |
| `notification-service` | 8084 | Event-driven notifications | MongoDB |

### 6.2 Decomposition rationale

Boundaries follow the existing domain rather than being drawn for their own sake:

- **Identity** changes rarely, must be highly available, and is the only component permitted to mint
  credentials
- **Land** carries the computational core and the heaviest query load
- **Forum** has a different data shape, a different availability requirement, and a different
  read pattern
- **Notification** is a pure consumer, which is what makes the message bus worth its complexity

### 6.3 Defence in depth for authorization

Three independent layers, deliberately redundant:

1. **Gateway** — rejects requests to protected routes carrying no valid token
2. **Service filter chain** — re-validates the same token, so a service reached directly is still
   protected
3. **Method and service layer** — `@PreAuthorize` for role rules, explicit ownership checks for
   resource rules, since the gateway cannot know whether user 7 owns land 42

### 6.4 Architectural trade-off statement

**For a system at this scale, a well-structured monolith would be simpler to operate and would be the
right production choice.** The microservice split is a deliberate architectural exercise satisfying
an explicit requirement.

The real costs are: six processes instead of one; distributed debugging (mitigated by correlation
ids); eventual rather than immediate consistency across service boundaries; no cross-service
transactions or joins; and a service registry plus a message broker as additional operational
dependencies. These are documented rather than glossed over.

---

## 7. Data requirements

Full schema, DDL, index rationale and normalisation analysis are in
[`ER-DIAGRAM.md`](ER-DIAGRAM.md). Requirements summary:

| ID | Requirement |
|---|---|
| DR-1 | The relational schema shall be normalised to third normal form |
| DR-2 | Roles shall be stored in their own table, not as a string column on the user row |
| DR-3 | Soil classifications shall be stored in their own table with the numeric indices the scoring engine consumes |
| DR-4 | Monetary values shall use exact decimal types with two decimal places |
| DR-5 | Character encoding shall support the full Devanagari range and supplementary planes (`utf8mb4`) |
| DR-6 | Cross-service references shall not use database foreign keys; integrity shall be maintained by events |
| DR-7 | Each database shall be owned by exactly one service; no service shall read another's tables |
| DR-8 | Forum threads shall embed their replies in a single document |
| DR-9 | Notifications shall carry the source event id under a unique constraint, providing consumer idempotency |
| DR-10 | Derived score columns shall be recomputed on every write path |
| DR-11 | Least-privilege database accounts shall be available, with DML but not DDL grants |
| DR-12 | Reference data shall be seeded idempotently on startup |
| DR-13 | Demo data seeding shall default to off, so fabricated prices cannot pollute district averages |

---

## 8. Security requirements

### 8.1 Implemented controls

| ID | Requirement |
|---|---|
| SR-1.1 | Passwords shall be hashed with BCrypt at cost factor 10 |
| SR-1.2 | Plaintext passwords shall not appear in any entity, log, or error message |
| SR-1.3 | Authentication shall take comparable time whether or not the account exists, closing the timing side channel that reveals which addresses hold accounts |
| SR-1.4 | Authentication failure messages shall not distinguish unknown email from wrong password |
| SR-1.5 | JWTs shall be signed with HMAC-SHA256 and validated for signature, issuer, audience and expiry |
| SR-1.6 | The signing secret shall be at least 32 bytes; services shall refuse to start otherwise |
| SR-1.7 | The signing secret shall be supplied by the environment and shall never be committed |
| SR-1.8 | An invalid token shall result in an unauthenticated request, never an unhandled error |
| SR-1.9 | An unrecognised role claim shall be ignored rather than invalidating the whole token |
| SR-2.1 | CORS shall use an explicit origin allow-list, configured only at the gateway |
| SR-2.2 | Client-supplied identity headers shall be stripped on unauthenticated routes to prevent spoofing |
| SR-2.3 | All input shall be validated server-side regardless of client validation |
| SR-2.4 | Database errors shall not be exposed to clients, since they reveal table, column and constraint names |
| SR-2.5 | Stack traces shall never reach a client response |
| SR-2.6 | Ownership queries shall be scoped by user id, returning 404 rather than 403 so id enumeration reveals nothing |
| SR-2.7 | Server-authoritative fields — ownership, scores, verification status — shall not be settable from a request body |
| SR-2.8 | Containers shall run as a non-root user |
| SR-2.9 | Requests shall be authorized on the token's claims, never on a client-supplied user identifier |
| SR-2.10 | Sessions shall be stateless; no server-side session state shall be maintained |

### 8.2 Documented weaknesses

Carried forward deliberately, with the trade-off stated:

| ID | Weakness | Consequence | Mitigation path |
|---|---|---|---|
| SW-1 | Password reset performs no ownership verification (FR-1.10 unmet) | Knowing an email is sufficient to take over an account | Emailed single-use time-limited token. **Must be fixed before public deployment** |
| SW-2 | Symmetric HS256 with a shared secret | Every service can mint tokens, not just validate them | RS256, with only auth-service holding the private key |
| SW-3 | 7-day token lifetime, no revocation | A leaked token is valid for a week | Short-lived access tokens plus refresh tokens and a revocation list |
| SW-4 | Tokens stored in `localStorage` | Readable by any XSS | `httpOnly` cookies with CSRF protection |
| SW-5 | No rate limiting | Login and reset endpoints are unthrottled | Gateway `RequestRateLimiter` or a WAF |
| SW-6 | Events published after commit, not via an outbox | A crash in the window loses an event | Transactional outbox with a relay |
| SW-7 | Password reset reveals whether an account exists | Enables enumeration | Return success regardless, and send mail only if the account exists |

---

## 9. Constraints, assumptions and dependencies

### 9.1 Constraints

| ID | Constraint |
|---|---|
| C-1 | Backend must be Java with Spring Boot, Spring Data JPA and Spring Security |
| C-2 | Architecture must comprise at least two microservices |
| C-3 | RabbitMQ must be used for asynchronous inter-service communication |
| C-4 | Two database technologies must be used |
| C-5 | Frontend must be React |
| C-6 | Existing frontend API contracts must be preserved to avoid a rewrite |
| C-7 | Existing BCrypt password hashes must remain valid after migration |
| C-8 | Deployment target is AWS |

**Note on C-7.** This is why BCrypt was retained rather than moving to Argon2id, which is otherwise
the stronger modern choice. Changing algorithms would invalidate every existing password.

### 9.2 Assumptions

| ID | Assumption |
|---|---|
| A-1 | Each user holds exactly one role, though the schema permits several |
| A-2 | Listings are predominantly in Maharashtra; the state field defaults accordingly |
| A-3 | Land areas are expressed in acres |
| A-4 | Currency is Indian Rupees; no multi-currency support |
| A-5 | Soil reference figures are ordinal calibrations, not survey data |
| A-6 | Forum threads remain well within MongoDB's 16 MB document limit |
| A-7 | Notifications are in-app only; no email or SMS delivery |
| A-8 | Clocks across services are reasonably synchronised, as JWT expiry depends on it |

### 9.3 Dependencies

Spring Boot 3.3.4 · Spring Cloud 2023.0.3 · JJWT 0.12.6 · springdoc-openapi 2.6.0 · MySQL Connector/J
· MongoDB Java driver · React 19 · Vite 8 · axios · MUI 9 · react-router 7 · i18next · Leaflet ·
Recharts

---

## 10. Known limitations and deferred requirements

### 10.1 Unmet requirement

**FR-1.10 — verified password reset.** Not implemented. The endpoint reproduces the original
behaviour, where knowing an email address is sufficient to change that account's password. This is
the single most important thing to address, and it is called out in the README, in the endpoint's
OpenAPI description, and in the DTO's Javadoc. **The endpoint should not be exposed publicly as it
stands.**

### 10.2 Deferred features

| Feature | Status |
|---|---|
| Saved-search match notifications | Criteria and the notify flag are persisted; the matching batch job is not implemented. Fanning out synchronously in the event handler would write thousands of documents per listing and block the queue |
| Expert category subscriptions | Deferred. Notifying every expert about every question without subscriptions degenerates into spam that gets muted, taking useful notifications with it |
| Crop and fertiliser recommendation engine | Frontend pages exist with static content; no backend service |
| Mandi price integration | Requires a government data feed |
| Land comparison persistence | Currently `sessionStorage` only |
| Image upload for listings | Placeholder graphics only |
| Flyway migrations | DDL is documented; `ddl-auto=update` is the current mechanism |
| Distributed tracing | Correlation ids are propagated; no OpenTelemetry spans |
| Integration and contract tests | Unit tests only; Testcontainers-based integration tests would strengthen coverage |

### 10.3 Model calibration

The scoring weights and reference depths are **informed heuristics, not a model fitted to observed
outcomes**. They rank listings sensibly relative to one another, which is what the feature needs, but
they are not validated predictions.

Genuine calibration requires historical borewell drilling outcomes joined to geological survey data,
which the platform does not collect. Until it does, output must be presented as decision support for
comparing parcels — as the API documentation does — and never as a prediction of drilling success to
a farmer deciding whether to spend money on a borewell.

---

## 11. Appendices

### 11.1 Role permission matrix

| Capability | Farmer | Land Buyer | Agriculture Expert | Admin |
|---|:---:|:---:|:---:|:---:|
| Browse and view listings | ✓ | ✓ | ✓ | ✓ |
| Create listing | ✓ | | | ✓ |
| Edit own listing | ✓ | | | ✓ |
| Edit any listing | | | | ✓ |
| Verify listing | | | | ✓ |
| Delete own listing | ✓ | | | ✓ |
| Save searches / shortlist | ✓ | ✓ | | ✓ |
| Investment analysis | ✓ | ✓ | ✓ | ✓ |
| Read forum | ✓ | ✓ | ✓ | ✓ |
| Post and reply in forum | ✓ | ✓ | ✓ | ✓ |
| Unanswered queue | | | ✓ | ✓ |
| Delete own content | ✓ | ✓ | ✓ | ✓ |
| Moderate any content | | | | ✓ |
| Manage users | | | | ✓ |
| Platform analytics | | | | ✓ |

### 11.2 HTTP status code usage

| Code | Used when |
|---|---|
| 200 | Successful read or update |
| 201 | Resource created |
| 204 | Successful delete, no body |
| 400 | Validation failure, malformed body, semantic error such as an inverted range |
| 401 | Missing, malformed, expired or invalid token; failed authentication |
| 403 | Authenticated but the role or ownership rule forbids the operation |
| 404 | Resource does not exist, **or** exists but belongs to another user |
| 409 | Conflict with existing state — duplicate email, already shortlisted |
| 500 | Unexpected server error; generic message, full trace logged server-side |
| 503 | Downstream service unreachable (gateway circuit-breaker fallback) |

### 11.3 Scoring model reference

**Land Intelligence Score components**

| Component | Weight | Source |
|---|---|---|
| Soil fertility | 30% | `soil_types.fertility_index` |
| Water access | 30% | Groundwater depth, linear 30 m → 250 m |
| Rainfall | 15% | Annual mm, saturating at 400 and 1,200 |
| Moisture retention | 15% | `soil_types.water_retention_index` |
| Accessibility | 10% | Irrigation (60) + road access (40) |

**Borewell Success Probability**

| Element | Behaviour |
|---|---|
| Base | Linear decay from 95% at ≤30 m to 10% at ≥250 m |
| Retention modifier | ±3 points max, from soil water retention |
| Rainfall modifier | ±3 points max, from annual rainfall |
| Irrigation bonus | +3 points where irrigation already exists |
| Bounds | Hard-clamped to 10–95 |

### 11.4 Requirements traceability

| Requirement group | Verified by |
|---|---|
| FR-1.x authentication | `AuthServiceTest`, `JwtTokenProviderTest` |
| FR-2.x authorization | `LandServiceTest`, `ForumServiceTest`, `UserAdminServiceTest`, `RoleNameTest` |
| FR-3.x listings | `LandServiceTest` |
| FR-4.x scoring | `LandScoringServiceTest` |
| FR-5.x investment analysis | `LandServiceTest` (`Analyse` nested class) |
| FR-7.x forum | `ForumServiceTest` |
| FR-8.x notifications | `NotificationEventListenerTest` |
| FR-9.x administration | `UserAdminServiceTest` |
| FR-10.x consistency | `NotificationEventListenerTest` (`Idempotency` nested class) |

---

*End of document.*
