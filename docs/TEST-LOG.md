# LifeNote — Test Log

**Project:** LifeNote — AI-assisted personal task management
**Author:** Ying Gao (X25121413)
**Test phase:** 16 November 2026 (interim), continuous since the first commit
**Environment:** Java 21, Spring Boot 4.1.1, PostgreSQL 16 (Testcontainers), JUnit 5 + Mockito,
MockMvc (no real server), fake LLM / S3 / mail (no external API is ever called)

**How to reproduce**

```
mvn test                                        # whole suite
mvn test jacoco:report surefire-report:report-only   # + HTML reports
java tools/TestReportGenerator.java             # regenerate the PNG charts
```

**Result summary (16 Nov 2026)**

| Measure | Value |
|---|---|
| Test classes | 15 |
| Test cases | **142** |
| Passed | **142 (100%)** |
| Failed / errors / skipped | 0 / 0 / 0 |
| Line coverage (JaCoCo) | **87.2%** (1,169 of 1,340 lines) |
| Unit / Integration-system / Security / Malicious-intent cases | 81 / 30 / 18 / 13 |

Charts: `docs/test-report/*.png`. Browsable reports: `target/site/jacoco/index.html`
(coverage), `target/reports/surefire.html` (case list).

---

## 1. Unit tests — rules and parsing (81 cases)

Pure logic, no Spring, no database. These are the rules the proposal calls
"rule-based, therefore coverable by unit tests".

| Class | Cases | What is proven | Result |
|---|---|---|---|
| QuickAddParserTest | 29 | Quick-add sentence parsing: absolute + relative dates (today/tomorrow/day after, weekdays, next week, Chinese expressions), times (15:00, 3pm, 8:30 am), title stripping, "no date found" branch | PASS |
| ReminderServiceTest | 13 | Deadline window (owner time zone), evening digest cap of 10 + "…and N more", idempotency, "nothing left" body, bell-panel read flag, cross-user 1301 | PASS |
| LlmChatServiceTest | 11 | Provider choice from settings, fallback to the other provider on failure, 1103 when both fail, reply carries provider + model | PASS |
| AiExtractionServiceTest | 9 | Prompt contract (DATA ONLY, JSON-only output), JSON + markdown-fence parsing, retry on the other provider on prose, 1205 on invalid JSON, multimodal media overload, raw reply kept for the audit row | PASS |
| SummaryServiceTest | 7 | Counts / completion rate / postponement report, daily buckets, top-postponed ranking, LLM paragraph failure degrades to null | PASS |
| JwtUtilsTest | 4 | Round-trip claims, expired token rejected, wrong-secret and tampered signatures rejected | PASS |
| IcsExportServiceTest | 5 | All-day events end exclusively the next day (RFC 5545), timed events default to 1 hour, notes become DESCRIPTION, one VEVENT per task, empty export still valid | PASS |
| PdfTextExtractorTest | 3 | Text layer extracted, scanned page refused with 1204, corrupt bytes refused with 1204 | PASS |

## 2. Integration / system tests (30 cases)

Whole system boots against a **real PostgreSQL 16 in Docker** (Flyway migrations
run for real); only the three external systems are faked. Every case drives the
HTTP API with MockMvc, exactly as the front end will.

| Class | Cases | What is proven | Result |
|---|---|---|---|
| DocumentFlowTest | 20 | Endpoints 1–9: submit text/PDF/image → drafts → edit → confirm → tasks with source links; cancel saves nothing; 1104/1105/1107/1108/1109 input rules; AI down → FAILED → reextract; prose instead of JSON → 1205 after one retry; PDF text layer; deleting a source keeps confirmed tasks; confirm-after-confirm 1102 | PASS |
| TaskFlowTest | 12 | Endpoints 10–19: quick-add (OK / NEED_INPUT), manual create, range + status listing, PATCH semantics, status machine (completedAt set/cleared), POSTPONED cannot be set by hand (1203), postpone four-write transaction + log row + 1202/1102 rules, board buckets with postponed excluded from total, .ics export content | PASS |
| SummaryReminderTest | 8 | Monthly summary numbers match seeded data exactly (proposal 5.3 pass condition), LLM paragraph cached after one call, numbers survive an LLM outage, 1109 month format, deadline reminder only for "tomorrow" and idempotent, evening digest body + no duplicates, read flag, settings round-trip + 1007/1008 | PASS |
| SmokeTest | 1 | The integration machinery itself: container up, Flyway ran, register → login round trip | PASS |
| LifeNoteApplicationTests | 1 | Application context loads with the test profile | PASS |

## 3. Security tests (18 cases)

Proposal §5.4 (user and access-control testing) as automated tests.

| Area | Cases | What is proven | Result |
|---|---|---|---|
| AuthSecurityTest | 15 | Register/login, duplicate email 1006, wrong password 1001, unknown email deliberately gives the same 1001 (no user enumeration), missing/garbage/expired/forged/tampered tokens all 401 before any controller runs, legacy bare `token` header accepted, password change invalidates the old password, validation 400s | PASS |
| AccountSecurityTest | 4 | Delete requires the password (1001 otherwise), deletion wipes S3 originals and all rows and blocks re-login, no list endpoint (tasks, documents, reminders, board) ever shows another user's data, profile updates affect only the caller | PASS |
| Cross-user isolation (in Document/Task/SummaryReminder flows) | 6 | Foreign document → 1101 on every verb, foreign task → 1201 on read/edit/status/postpone, foreign reminder → 1301; the owner's data is unchanged after every attempt | PASS |

## 4. Malicious-intent tests (13 cases)

| Case | What is proven | Result |
|---|---|---|
| forged / tampered / expired / garbage tokens | rejected with 401 before any controller code runs | PASS |
| prompt injection payload ("Ignore all previous instructions…") | stays in the user message as data; system prompt rules untouched; no side effects | PASS |
| 11 MB file upload | rejected with 1105 | PASS |
| unsupported content type (CSV) | rejected with 1104 | PASS |
| 20,001-character text | rejected with 1109 | PASS |
| candidate-id tampering across documents | rejected with 1106; nothing confirmed | PASS |
| hand-set status POSTPONED / unknown status | rejected with 1203 (state machine cannot be bypassed) | PASS |
| postpone to the past / to the same date / of a cancelled task | rejected with 1202 / 1202 / 1102 | PASS |

## 5. Mapping to the proposal's evaluation plan (§5)

| Proposal section | Status at interim | Evidence |
|---|---|---|
| 5.1 Task creation accuracy | Covered for the local parser and the API contract (three creation paths, confirm/cancel rules). The 60-sample AI accuracy study needs live LLM keys → final phase | TaskFlowTest, DocumentFlowTest, QuickAddParserTest |
| 5.2 AI extraction reliability across two models | Failure/fallback contract covered with a fake LLM; live two-model accuracy run → final phase (keys unfunded at interim) | AiExtractionServiceTest, LlmChatServiceTest, DocumentFlowTest |
| 5.3 Monthly summary and calendar accuracy | **Covered**: numbers must match seeded data exactly | SummaryReminderTest.summaryNumbersMatchTheSeededDataExactly, TaskFlowTest ics/board cases |
| 5.4 User and access-control testing | **Covered** as automation | AuthSecurityTest, AccountSecurityTest, cross-user cases |
| 5.5 Integration tests (full flow) | **Covered** end to end: submit → extract → confirm → task → reminder → summary | DocumentFlowTest + SummaryReminderTest |
| End-user evaluation (5–8 participants) | Scheduled for week 8 (24–30 Nov) with the online demo | — |

## 6. Known limitations

- Test data is authored by the student and simpler than real-world letters.
- The fake LLM proves the *contract and failure handling*, not model accuracy;
  accuracy evaluation requires funded provider keys (final phase).
- End-user testing has not started at the interim point.
- Coverage is line-based (JaCoCo); the uncovered remainder is mostly the
  application entry point, error-handler fallbacks and config glue.
