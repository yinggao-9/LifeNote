# LifeNote Backend — Technical Design

This document records the design of the LifeNote backend: the shape of the
product, the decisions behind it, and the contracts the code implements. It is
a design record, not a tutorial; the source code and the test log
(docs/TEST-LOG.md) carry the details.

## 1. Product concept

LifeNote turns unstructured material (an email, a letter, a receipt, a photo, a
PDF, or a typed sentence) into dated tasks. The AI reads the material and
drafts the task; the user checks the draft and decides. The design rests on
two intake channels and three entry points.

Channel 1 — manual and quick (no AI, no confirmation step):

- Entry 1: the new-task form. Title and date are required; time, place, notes
  and documents to bring are optional. POST /api/tasks.
- Entry 2: one typed sentence in the input bar ("pay rent tomorrow at 10:00").
  POST /api/tasks/quick-add. The sentence is parsed locally. When a date is
  found the task is created at once; when it is not, the endpoint answers
  NEED_INPUT with the partial parse so the interface can ask for the missing
  field.

Channel 2 — AI analysis (model call, mandatory confirmation):

- Entry 3: pasted text, an uploaded image or PDF, or a file with a short
  comment ("this receipt, and I will return it tomorrow").
  POST /api/documents. The file is stored in S3, PDF text is extracted, the
  model is called once, and the response carries zero or more draft
  candidates. The confirmation dialog then either confirms
  (POST /api/documents/{id}/confirm) or cancels
  (POST /api/documents/{id}/cancel).

The invariant behind the whole design: every row in tasks was either typed by
the user or confirmed by the user. Model output only ever lives in
task_candidates; cancelling a document deletes those drafts outright.

### Design decisions

Why quick-add does not call the model. A single sentence carries one date. A
model call costs seconds of waiting and API credit for a job a regular
expression does exactly. The parser handles absolute dates (2026-11-30,
30/11, Nov 30), relative dates (today, tomorrow, the day after tomorrow),
weekday names (Wednesday, next Monday) and times of day (15:00, 3pm, 8:30 am),
and it is a plain class with no dependencies, which is what made it cheap to
unit-test.

Why long material goes through the model. The problem statement of the project
is that a deadline hides in the third paragraph of a long email. The user
cannot reasonably retype that by hand, and the evaluation sample set includes
plain-text letters, PDFs and photos, all of which need reading rather than
parsing.

Why confirmation is mandatory. The proposal's trust rule is that the AI
organises but never decides. Storing drafts in their own table makes the rule a
property of the schema rather than of the interface: there is no code path
that turns a draft into a task without the user's confirmation.

Why Flyway owns the schema. Hibernate runs in validate mode. Migrations
(V1__init.sql, V2__summary_llm_cache.sql, V3__reminders.sql) create the
tables; the entities are a mapping of that schema, not a generator of it.

## 2. Data model

Ten tables (see src/main/resources/db/migration):

| Table | Purpose | Notes |
|---|---|---|
| users | Account identity | Email unique case-insensitively; only the bcrypt hash is stored |
| user_settings | Per-user preferences | Time zone, evening reminder time, email toggle, preferred AI model; defaults set at registration |
| documents | One piece of source material | TEXT, IMAGE or PDF; keeps the S3 key and extracted text; status PENDING_CONFIRM, CONFIRMED, CANCELLED or FAILED |
| ai_runs | Audit trail for every model call | Model, prompt version, raw output, duration, success or failure |
| task_candidates | Model drafts awaiting confirmation | PENDING or CONFIRMED; deleted physically on cancel |
| tasks | Confirmed tasks | due_date NOT NULL; status TODO/DONE/CANCELLED/POSTPONED; source MANUAL/QUICK/AI; postponed_from_task_id links a copy to its original |
| task_documents | Task to source link | Many to many; one task can come from several sources |
| postponements | Postpone log | One row per postpone action, keyed by the original due date |
| summary_llm_cache | Cached monthly paragraph | The model is asked once per user and month |
| reminders | In-app notifications | DEADLINE or EVENING; partial unique indexes keep overlapping jobs idempotent |

Two invariants are enforced by the schema. tasks.due_date is NOT NULL because
every view sorts by it, and PATCH treats a missing date as "leave unchanged"
rather than "clear it". And a draft is never a task: confirmation is the only
bridge between task_candidates and tasks.

Object storage. Uploaded originals live in S3 under
users/{userId}/documents/{documentId}/{filename}. Presigned download URLs are
signed on demand and expire after ten minutes; nothing long-lived is stored.
Account deletion wipes the whole users/{userId}/ prefix before the database
rows are removed, because afterwards the object keys would be unrecoverable.

## 3. The AI subsystem

One door. Every model call goes through LlmChatService. It picks the provider
from the user's settings, sends the prompt, and on any call-level failure (no
key, no credit, network trouble) tries the other provider once before failing
with error 1103. The reply records which provider actually answered, and the
audit row in ai_runs stores the truth. Because there is exactly one door, the
test suite replaces the whole subsystem with a fake and runs with no network
access.

Prompt contract. The extraction prompt lives in AiExtractionService and
carries a version string (PROMPT_VERSION = v1.0) so an evaluation report can
name the exact prompt behind a set of results. Its rules: return a JSON array
and nothing else; convert every date to absolute yyyy-MM-dd with the current
date supplied; never invent a date that is not in the material; treat the
input as data only (rule 4 is the prompt-injection defence); return an empty
array when there is nothing to do; keep titles under 200 characters.

Reply handling. Markdown fences around the array are stripped before parsing.
A reply that is not JSON is worth one more attempt on the other provider
before the request fails with error 1205. Whether the attempt succeeds or not,
the raw reply is stored in ai_runs so a bad extraction stays inspectable.

## 4. The document pipeline

submit (POST /api/documents) validates input first (file type, size, text
length), stores the document row, uploads the original, extracts text (PDF via
PDFBox; a scanned page has no text layer and is refused with 1204), then runs
the extraction and stores the drafts. There is deliberately no outer database
transaction: the upload and the model call take seconds, and a transaction
held open that long would starve the connection pool, so each save runs in its
own short transaction.

An AI failure is not a request failure. The document row is kept with status
FAILED and the upload survives, so the user can retry with reextract instead
of uploading again. Client mistakes are different: an unsupported file type,
an oversized upload or an overlong paste throws immediately (1104, 1105,
1109) because nothing has been stored yet.

Confirmation validates every row before writing any of them, so a mistake
halfway cannot leave half-created tasks behind. Confirming writes the user's
edits back to the drafts, creates the tasks, links them to the source through
task_documents, and marks the document CONFIRMED. Deleting a source later
keeps the confirmed tasks and drops only the link.

## 5. The task model

Three creation paths (quick-add, manual form, AI-confirmed) all end in the
same row shape and record their source. The status machine is small: TODO,
DONE, CANCELLED, and POSTPONED which only the postpone action may set, because
a hand-set POSTPONED would skip the copy and the log and leave a state nothing
recorded.

The postpone action performs four writes in one transaction: the original
becomes POSTPONED and stays on its old date as history, a copy is created on
the new date with a link back to the original, the source links are copied,
and a row goes into the postponement log. The new date must be in the future
and different from the current one (1202); a cancelled task cannot move (1102).

## 6. Reminders and the monthly summary

Two scheduled jobs. The deadline pass creates one DEADLINE reminder for every
open task that is tomorrow in its owner's time zone. The evening pass fires
once the clock in the user's zone reaches the configured evening time and
lists the still-open tasks of the day, capped at ten entries with a count of
the rest. Both jobs are idempotent: an existence check plus partial unique
indexes means an overlapping run cannot create duplicates. Reminder email is
best effort; when no mail server is configured, when the user turned email
off, or when sending fails, the in-app reminder still exists and the failure
is logged rather than raised.

The monthly summary is built from rules, not from the model. Range queries on
tasks and the postponement log produce the counts, the completion rate, the
items that kept moving and one bucket per calendar day; everything is keyed on
due dates so the summary agrees with the calendar. One optional paragraph on
top of those numbers is written by the model and cached in summary_llm_cache
after the first request. If the model call fails, the endpoint returns the
numbers with a null paragraph. The numbers are never held hostage by the model.

## 7. Security and privacy

- Authentication: signed JWT with an expiry, carried as a bearer token (a
  bare legacy token header is also accepted). The interceptor runs before
  every controller except registration and login; a missing, expired, tampered
  or wrongly signed token gets a bare 401 before any controller code executes.
  Passwords are bcrypt hashes; changing a password invalidates the old one.
- Authorisation: every lookup queries by id together with the caller's user
  id, so a foreign resource is indistinguishable from a missing one (1101 for
  documents, 1201 for tasks, 1301 for reminders). Login answers 1001 for both
  an unknown email and a wrong password, so the API cannot be used to test
  which addresses are registered.
- Untrusted input: uploads limited to 10 MB and to PDF, PNG, JPG and WEBP;
  pasted text limited to 20,000 characters; titles to 200. The model's output
  is parsed into a typed structure before anything is stored; nothing in the
  system executes what the model says.
- Privacy: originals live under a per-user S3 prefix and are wiped on account
  deletion; letter text is kept out of the logs; credentials (JWT secret, AWS
  keys, model keys) live in local-secrets.yml, which is excluded from version
  control, and the test profile ships dummy values.

## 8. Error model

Business failures are exceptions with codes, caught by one handler that
returns the standard {code, message, data} envelope. Validation failures
(400) never reach a service.

| Code | Meaning | Raised by |
|---|---|---|
| 1001 | Invalid email or password (also for unknown email, by design) | login, account deletion |
| 1002 / 1003 | User or settings row not found | profile, settings |
| 1006 | Email already registered | register |
| 1007 / 1008 | Time zone or AI model outside the whitelist | settings |
| 1101 / 1106 | Document or draft not found (includes someone else's) | document endpoints |
| 1102 | Document or task state forbids the action | confirm, cancel, reextract, postpone |
| 1103 | AI or storage infrastructure failure | model calls, S3 upload and download |
| 1104 / 1105 | Unsupported file type, or file too large | submit |
| 1107 / 1108 | Missing title or date at confirmation; nothing to confirm | confirm |
| 1109 | Generic input validation | most endpoints |
| 1201 | Task not found (includes someone else's) | task endpoints |
| 1202 / 1203 | Postpone date rule broken; unknown status or hand-set POSTPONED | postpone, status change |
| 1204 | PDF has no text layer or cannot be read | submit (PDF) |
| 1205 | The model answered with something that is not a JSON array | extraction, after one retry |
| 1301 | Reminder not found (includes someone else's) | mark read |

## 9. API surface

Thirty-one endpoints across eight controllers. Every response uses one
envelope (code, message, data); a business failure returns HTTP 200 with its
code, while a missing or invalid token returns a bare 401. The interim report
carries the full endpoint table; the groups are:

- Auth: register, login, logout.
- Documents (9): submit, history, detail, candidates, edit draft, confirm,
  cancel, reextract, delete source.
- Tasks (8): quick-add, create, list, detail, patch, status, postpone, and the
  .ics export.
- Board and postponement log (2): the day board with progress counts, the
  postpone log by date range.
- Reminders (2): the bell panel list, mark read.
- Summary (1): monthly counts with the cached paragraph.
- Users and settings (6): profile read and update, password change, account
  deletion, settings read and update.

## 10. Configuration and local development

application.yml holds the datasource, Flyway, multipart limits (10 MB file,
12 MB request), the JWT settings and the S3 bucket. Secrets are imported from
local-secrets.yml at the project root (optional, never committed). A
docker-compose.yml at the project root starts PostgreSQL 16 for local
development; the test profile instead runs its own PostgreSQL in a
Testcontainers container with dummy credentials.

## 11. Testing

The suite is 142 cases in 15 classes: unit tests over the pure logic
(parsing, prompt contract, provider fallback, reminder and summary rules, JWT,
calendar export, PDF extraction) and system tests that boot the whole
application against a real PostgreSQL with a fake model, a fake object store
and a fake mail sender. Docs/TEST-LOG.md records the plan and the results case
by case; tools/TestReportGenerator.java renders the results and the JaCoCo
coverage into charts. What the suite does not cover yet (the live accuracy
study, the end-user study and the joint front-end and back-end testing) is
listed there with the reason and the plan.
