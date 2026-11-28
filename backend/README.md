# wORGit backend (slices 1A, 1B and 1C)

Spring Boot 3.5 / Java 17, Spring JDBC, PostgreSQL on Supabase, JWT. No registration anywhere:
the owner inserts every account (see `worgit_accounts.sql`).

## What is built
- **1A**: login (`POST /api/auth/login`), role routing, the section 3 permission matrix, domain rules, error handling.
- **1B**: organizations, membership, applications and payments (below).
- **1C**: My Profile and password, peer matching and connect, chat rooms and messages, chat moderation,
  notifications, admin profile (below).
- Not built yet: events, certificates, achievements, the
  engagement report, dashboards, student summary (1D); logo/cover upload to Supabase Storage (needs the
  bucket settings); then the app screens.

## Endpoints added in 1B
Students (`/api/student/...`)

| Call | Screen |
|---|---|
| `GET /organizations?search=&category=` | ST-02 Browse: cards with state JOINED, PENDING, APPLY or JOIN |
| `GET /organizations/mine` | ST-02 My organizations |
| `GET /applications` | ST-02 My applications |
| `GET /organizations/{orgId}` | ST-03 detail (membership card: state, fee status, room id) |
| `POST /organizations/{orgId}/join`, `/apply` | Join (no approval needed) / Apply (approval needed) |
| `DELETE /organizations/{orgId}/membership` | Leave |

Staff (`/api/admin/...`; what each role may do is decided in the services)

| Call | Screen | Who |
|---|---|---|
| `GET /organizations?search=` | AD-02 (rows carry canEditProfile, canEditAllFields, canRemove) | all staff, scoped |
| `POST /organizations`, `PUT /organizations/{id}`, `GET /organizations/{id}` | AD-03 | create: System Admin; edit: System Admin (all fields), Facilitator (profile content) |
| `GET /organizations/{id}/removal-impact`, `DELETE /organizations/{id}` | AD-02 Remove | System Admin; blocked while certificates exist |
| `GET /organizations/{id}/members?search=`, `DELETE .../members/{studentId}` | AD-04 | view: all staff; remove: System Admin |
| `GET /applications?status=&orgId=`, `POST /applications/{id}/approve`, `/reject` | AD-06 | view: all; decide: Facilitator (own), System Admin |
| `GET /payments?orgId=`, `POST /payments/record`, `POST /payments/{id}/mark-paid` | AD-07 | view: all; record: Facilitator (own), System Admin |

## Endpoints added in 1C
Students (`/api/student/...`)

| Call | Screen |
|---|---|
| `GET /profile`, `PUT /profile` `{displayName, bio, interests[], visibleInMatching}` | ST-08 My Profile (the response includes the five interest chips) |
| `POST /profile/password` `{currentPassword, newPassword}` | ST-08 Security |
| `GET /peers` | ST-04 tabs: `matches` and `connected`, ranked by shared interests |
| `POST /peers/{studentId}/connect` | ST-04 Connect; returns the Direct `roomId` and "You connected with {name}." |
| `GET /chats` | ST-05 list in section order Lobby, interest rooms, my organizations, Direct; locked interest rooms carry `lockedMessage` |
| `GET /chats/{roomId}/messages?before=&after=&limit=` | ST-05 messages, oldest first; `after` for polling, `before` for older; `hasOlder` |
| `POST /chats/{roomId}/messages` `{message}` | ST-05 composer (201 with the saved message) |
| `GET /notifications`, `POST /notifications/{id}/read` | ST-09 (`unread` count + newest 100) |

Staff (`/api/admin/...`)

| Call | Screen | Who |
|---|---|---|
| `GET /profile`, `PUT /profile` `{displayName}`, `POST /profile/password` | AD-12 | all staff (role and organizations read-only) |
| `GET /chats`, `GET /chats/{roomId}/messages?before=&after=&limit=` | AD-08 | System Admin: Lobby, interest and all organization rooms; Facilitator: own organization rooms |
| `DELETE /chats/messages/{messageId}` | AD-08 delete | same as above (hard delete) |
| `GET /notifications`, `POST /notifications/{id}/read` | AD-13 | Facilitator, Adviser (not System Admin) |

Scope: a System Administrator sees every organization; a Facilitator or Adviser only the ones in
`admin_managed_orgs`. An out-of-scope request answers 403 `ACCESS_DENIED`.

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

Schema: `organizations.interest_name` holds the organization's category (one of the five interests). The API
and the Java code still call it `category`, as the spec does. `db/worgit_schema_empty.sql` is the schema your
Supabase was built from.

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
| ROOM_ACCESS on an interest room | 403 | the inline locked panel (message = the panel text) |
| CERTIFICATE_NOT_ELIGIBLE | 422 | inline |
| TOO_MANY_ATTEMPTS | 429 | inline on Log in |

## ASSUMED in slice 1A (list them in the final summary)
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

## ASSUMED in slice 1B
- An organization card shows PENDING (not Apply) while the student's application is open.
- Joining an organization that needs approval, or applying to one that does not, is an error (wording assumed).
- Fees: an UNPAID payment record for the fee is created when a student joins or is approved, only if the
  student has no earlier payment record for that organization. The Record form settles the member's unpaid
  record (or creates a PAID one); "Mark paid" sets today's date (Asia/Manila). Fee status: UNPAID if any unpaid
  record, else PAID, NONE without records.
- Leaving keeps payments, messages, participation and certificates.
- Removing an organization is blocked while it has issued certificates (your decision).
- A Facilitator who sends a changed name, category, approval flag or fee gets 403; a missing fee means "unchanged".
- Organization name: required, at most 250 characters, unique ignoring case. Social links need a platform and
  an http(s) URL and are listed alphabetically by platform.
- Renaming an organization does not rename its chat room (the app shows the organization's name).
- Notifications: Facilitators of the organization on a new application; the student and the Advisers of the
  organization on a decision (wording assumed).
- Approving an application of someone who is already a member only marks it approved.
- Staff list endpoints take an optional `orgId`; omitted means every organization the staff member may see.

## ASSUMED in slice 1C
- Your answers to the 1C questions are still open; these are the defaults I proposed (change any of them):
  chat moderation built in 1C, Direct chats never visible to staff, Facilitators moderate only their own
  organizations' rooms; display names exactly as stored; "Current password is incorrect." for a wrong current
  password; AD-12 edits only the display name (and password); no avatars in 1C; staff never post in chats.
- ST-04: Matches = visible students sharing at least one interest (connected ones stay there, flagged
  `connected`); Connected = every connected student, even hidden or with nothing shared. Hiding yourself only
  hides you from others. Connect works only on someone listed under Matches (anything else is 404).
- "You are already connected with {name}." is an `INVALID_INPUT` 400 (the spec lists no exception class for it).
- The PEER_CONNECTION notification goes to the initiator only, worded "You connected with {name}." (section 6).
- Chat paging: 50 messages by default, at most 100; ordered by message id; `before` and `after` together is
  `INVALID_REQUEST`. A locked interest room's last message is not shown in the list.
- Direct room title = the other student's display name; organization room title = the organization's current name.
- A student who leaves an organization loses access to its room at once (messages stay).
- Message delete is a hard delete, so it also lowers the sender's engagement score (messages x 2).
- Notifications list: newest 100. "Mark all as read" is the app calling markRead per item (no extra endpoint).
- Password change: tokens already issued stay valid until they expire; wrong current passwords are not
  rate-limited (the caller is already logged in).
- Profile PUT saves the whole form; a missing `visibleInMatching` keeps the current value, a missing bio clears it.

## Open
- Whether a Facilitator may remove members of their organizations (denied until decided).
- Logo and cover upload: needs your Supabase Storage bucket name and whether it is public.

## Smoke test against the real database
With the server running: `powershell -ExecutionPolicy Bypass -File scripts\smoke-1b.ps1`
(asks for the three temporary passwords; creates and deletes "Smoke Test Org").
Slice 1C: `powershell -ExecutionPolicy Bypass -File scripts\smoke-1c.ps1` (same three accounts; posts one Lobby
message and deletes it; changes no password; asks before the permanent Connect).

## Verification status
Slice 1A compiled and passed all 61 tests on the owner's machine and logged in against Supabase.
Slice 1B: the services and their 54 tests were compiled and run against stand-ins for Spring, JUnit and
AssertJ (all pass), and every file passes a syntax parse. The SQL in the JDBC repositories, the controllers
and the real JUnit/Spring run have NOT been executed: run `mvn test`, then the smoke script, and send back
any failure.
Slice 1C: all main code compiles against stand-ins for Spring; the 1B and 1C service tests (115) pass against
stand-ins for JUnit and AssertJ; every 1C SQL statement was run against this schema on a local PostgreSQL 16.
Not yet run: the real `mvn test`, the JDBC code through the real driver, the controllers, and the smoke script.
