# From the Python app to Property Manager Sentry

The Python *Estate Beacon / Inbox Supervisor* app (PyQt6 + LangGraph + google-genai) defined the features. This page
maps each piece to its Java counterpart and lists what was improved along the way.

## Concept map

| Python app | Java app | What to look at |
|---|---|---|
| `pydantic-settings` `AppSettings` + `.env` | `@ConfigurationProperties` record `SentryProperties` + `application.yml`, validated at startup | `config/SentryProperties.java` |
| `QSettings` | `java.util.prefs.Preferences` | `config/UserPreferences.java` |
| Pydantic models | Java **records** with `@JsonPropertyDescription` (doubles as the Gemini response schema) | `triage/*Details.java`, `RoutingDecision.java` |
| `TypedDict` LangGraph state | `TriageState extends AgentState` | `triage/TriageState.java` |
| LangGraph `StateGraph`, `add_conditional_edges` | LangGraph4j `StateGraph`, `addConditionalEdges` | `triage/TriageGraph.java` |
| `genai.Client` + `response_schema` | Spring AI `ChatClient` + `.entity(Record.class)` + `ENABLE_NATIVE_STRUCTURED_OUTPUT` | `triage/GeminiTriageAgents.java` |
| f-string prompts in code | StringTemplate files in `resources/prompts` | `prompts/*.st` |
| `sqlite3` DAO, hand-written `ALTER TABLE` | Spring Data JPA + Flyway migrations on H2 | `ticket/`, `db/migration/` |
| `QThread` workers | Virtual threads (`Fx.background`, `InboxSyncService`) | `ui/Fx.java` |
| Qt signals between workers and the window | Spring application events (`SyncEvent`) forwarded to JavaFX with `Platform.runLater` | `inbox/SyncEvent.java`, `ui/MainWindow.java` |
| `QTimer` auto-fetch | Spring `TaskScheduler` with a runtime-configurable interval | `inbox/AutoSyncScheduler.java` |
| PyQt6 + hand-written dark stylesheet | JavaFX + AtlantaFX Primer theme (dark/light) + small CSS | `ui/`, `resources/ui/sentry.css` |
| PyInstaller | Spring Boot executable jar (`bootJar`) | `build.gradle.kts` |

## Improvements over the Python app

**Correctness**
- **Failed extractions are retried.** In the Python app, if the supervisor succeeded but a specialist failed, the
  ticket was saved with an error and the email marked processed for good. Here any failure leaves the email
  unrecorded, so the next sync retries it (`TriageOutcome.Failed`).
- **The real received date.** The Python app stored the processing time as `date_received`. Here it's Gmail's
  `internalDate`.
- **Read state survives restarts.** The "unread" highlight is stored in the database, not kept in memory.
- **Ticket status is editable** (Open / In Progress / Resolved). The Python app had a status column nothing ever changed.
- **Priority sorting is by severity by construction:** the `Priority` enum is declared LOW to URGENT. There's no weight
  table that can drift (the Python one listed a `CRITICAL` level the schema didn't allow).

**Robustness and cost**
- **Bounded fetch.** At most `max-messages` per sync and a configurable Gmail query. The Python app paged through
  every unread email.
- **Parallel triage** on virtual threads with a concurrency cap, plus a per-email timeout.
- **The graph is compiled once at startup.** The Python app rebuilt it for every email.
- **Long bodies are truncated** before reaching the LLM.
- **Missing configuration fails fast** with a dialog at startup, rather than mid-sync.

**Security**
- **Prompt-injection guard.** A shared system prompt marks the email as untrusted data, and the email is wrapped in
  `<email>` tags it can't break out of (`PromptSanitizer`).
- **CSV formula injection is neutralized** in exports (cells starting with `=`, `+`, `-` or `@`).
- **Real HTML parsing** (jsoup) for HTML-only emails, instead of regexes.
- Specialists also see the sender, subject and the supervisor's property findings, not just the body.

**Maintainability**
- **The UI holds no business logic.** Fetching, triage and persistence live in services that tests drive directly;
  the window only renders state and reacts to events.
- **Interfaces at the edges** (`MailboxClient`, `TriageAgents`), so tests run with no network, LLM or display.
- **Sealed types and exhaustive `switch`es.** Adding a new category without handling it everywhere is a compile error.
- **Schema migrations** with Flyway, plus Hibernate `ddl-auto: validate` to catch entity/schema drift at startup.
