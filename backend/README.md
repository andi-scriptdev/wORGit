# wORGit backend (foundation, login, roles, errors)

Spring Boot 3.5 / Java 17, Spring JDBC, PostgreSQL on Supabase, JWT. No registration anywhere:
the owner inserts every account (see `worgit_accounts.sql`).

## What is in this
- Login: `POST /api/auth/login` `{ "userId": "...", "password": "..." }` -> token, role, `home` (ST-01 or AD-01).
- Role routing: `/api/student/**` needs a student, `/api/admin/**` needs a staff role. The user's role and
  managed organizations are re-read from the database on every request (a changed role applies at once).
- `Permissions` (the section 3 role matrix) and `AuthenticatedUser.canManage(orgId)` for organization scope.
- Domain rules (`Rules`, `Achievements`): password, message, display name, bio, interests, 250-character
  organization text, score, participation rate, milestones.
- The exception hierarchy of the class diagram and one JSON error shape (below).
- Not built yet: repositories and services for organizations, applications, payments, peers, chat, events,
  certificates, notifications, profile, report (slice 1B), then the screens.

## Run
1. Run the SQL files in order: `db/worgit_schema_empty.sql`, `worgit_seed_required.sql`,
   `worgit_seed_organizations.sql`, `worgit_accounts.sql`.
2. Set environment variables (never commit them):
   - `WORGIT_DB_URL` e.g. `jdbc:postgresql://HOST:5432/postgres?currentSchema=worgit`
     (transaction pooler: add `&prepareThreshold=0`)
   - `WORGIT_DB_USER`, `WORGIT_DB_PASSWORD` (the database user, not the Supabase anon/service key)
   - `WORGIT_JWT_SECRET` at least 32 bytes
   - optional `WORGIT_CORS_ORIGINS` (default: Expo dev origins on localhost:8081 and :19006)
3. `mvn test` then `mvn spring-boot:run`.

The server starts even if the database is paused; requests then answer 503 `DATA_STORE` after 5 seconds.

## Error shape and what the app does with it
`{ "status": 403, "code": "ACCESS_DENIED", "message": "...", "field": "bio" (optional), "timestamp": "..." }`

| code | HTTP | app |
|---|---|---|
| AUTHENTICATION_FAILED | 401 | inline on Log in |
| UNAUTHENTICATED | 401 | token missing, expired or account gone: back to Log in |
| ACCESS_DENIED, ROOM_ACCESS | 403 | SYS-01 (interest rooms use the inline locked panel) |
| INVALID_REQUEST | 400 | SYS-02 |
| NOT_FOUND | 404 | SYS-04 |
| DATA_STORE, SERVICE_ERROR | 503, 500 | SYS-03, or an inline banner on forms |
| INVALID_INPUT, INVALID_PASSWORD, INVALID_MESSAGE, INVALID_PROFILE | 400 | inline, under `field` |
| DUPLICATE_MEMBERSHIP | 409 | inline |
| CERTIFICATE_NOT_ELIGIBLE | 422 | inline |
| TOO_MANY_ATTEMPTS | 429 | inline on Log in |

## ASSUMED (list them in the final summary)
- Token lifetime 8 h, no refresh; logout is the app deleting its token.
- Login lock: 5 failures per 15 min per user id + client IP, in memory, one server instance; 429 `TOO_MANY_ATTEMPTS`.
- Role and organization scope read from the database on every request, not from the token.
- Exceptions are unchecked; `ApiException` is a common parent added above the class diagram's hierarchy.
- Messages are trimmed before counting; the p45 message is split by cause: "Message cannot be empty." and
  "Max 300 characters (312/300)."
- Wording not given by the spec: display-name length, unknown interest, too many attempts, unauthenticated,
  access denied, invalid request, service error, "{label} must be 250 characters or fewer".
- 500 `SERVICE_ERROR` for unexpected errors; wrong HTTP method or media type counts as INVALID_REQUEST.
- CORS: only the configured origins. Spring Boot 3.5.0, Java 17, jjwt 0.12.6, pool size 5.

## Open
- Whether a Facilitator may remove members of their organizations (denied until decided).

## Verification status
The domain and exception code was compiled and exercised without Spring (108 checks). Every file passes a
syntax parse. The Spring-dependent code and all JUnit tests were NOT compiled or run (no network in the
authoring sandbox): run `mvn test` first and send back any failure.
