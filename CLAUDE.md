# Property Manager Sentry

Java 25 / Spring Boot 4 / Spring AI 2 / LangGraph4j desktop app (JavaFX) that triages a Gmail inbox with Gemini.
See README.md for setup and docs/agentic-ai-guide.md for the architecture.

## Workflow

- `develop` is the default branch. Never commit to it directly.
- Make every change on a feature branch created from `develop` (e.g. `feature/<short-name>`), then open a pull
  request into `develop`. CI (`.github/workflows/build.yml`) must pass before merging.

## Build and test

- `./gradlew build`: compile and run all tests (no network, API key or display needed).
- `./gradlew test`: tests only.
- `GOOGLE_API_KEY=dummy ./gradlew bootRun --args='--spring.profiles.active=demo --sentry.data-dir=/tmp/sentry-demo'`:
  run the UI with sample data.
- Compiler warnings are enabled (`-Xlint:all`); keep the build warning-free.

## Conventions

- Personal config and secrets live in `config/application.yml` and `data/` (or `~/.property-manager-sentry/`);
  all are git-ignored. Never commit API keys or `credentials.json`.
- Database changes go in a new Flyway migration under `src/main/resources/db/migration/`; Hibernate only validates.
- LLM prompts live in `src/main/resources/prompts/`; agent output contracts are records in `triage/`.
