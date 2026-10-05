# TaskFlow Backend

A Jira/Trello-style project management platform, built as a learning + portfolio project.
See `PROGRESS.md` for what's been built so far and what's next.

## Prerequisites

- Java 21 (`java -version` to check)
- Maven
- Docker + Docker Compose (for local Postgres)

## Running it locally

1. Start Postgres:
   ```
   docker compose up -d
   ```
2. Run the app:
   ```
   mvn spring-boot:run
   ```

No endpoints exist yet - this is bare scaffolding, reset back to just the build setup
after Step 2 was removed. See `PROGRESS.md` for what to build next.

## Project structure

Organized **package-by-feature**, not package-by-layer - each feature area (auth, user,
workspace, etc.) will hold its own entity, repository, service, and controller together,
instead of splitting all entities into one `entities` package, all controllers into
another, etc. This scales much better as the project grows, and it's how most real
production Java codebases are actually organized.

```
com.taskflow
└── TaskflowApplication.java   # entry point - nothing else exists yet
```
