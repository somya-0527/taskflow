# Build Progress

## Reset — 2026-09-22

Everything except the base project setup was removed on request, back to bare
scaffolding. What's still here:

- `pom.xml`: Spring Boot 3.3.4 on Java 21, with web, data-jpa, validation, security,
  JWT (jjwt), Postgres driver, Lombok, and springdoc-openapi already declared as
  dependencies - so the next build won't need any pom.xml changes for Step 1/2-style
  work, just new Java files.
- `docker-compose.yml`: local Postgres 16 for dev.
- `application.yml` + `application-dev.yml`: profile-based config, already pointing at
  the Dockerized Postgres, with the `app.jwt.*` settings already in place for whenever
  JWT code comes back.
- `TaskflowApplication.java`: the bare entry point. No controllers, entities, or
  security config exist right now.

Previously built and removed: a `User` entity, JWT-based register/login auth
(`AuthController`/`AuthService`/`JwtService`/`JwtAuthenticationFilter`), a
`SecurityConfig`, a `/api/health` endpoint, and a passing integration test suite (all
verified working via `mvn clean test` - 4/4 passed - before being removed). Worth
knowing that combination was confirmed correct, including two real bugs that were
found and fixed along the way: a double filter-registration issue from putting
`@Component` on the JWT filter, and a 401-vs-403 issue from Spring Security's default
anonymous-authentication behavior. Revisit those two specifically if this gets rebuilt
and the same symptoms show up again.

## Next

Whatever you build next starts from a clean, verified-working base - `mvn clean
compile` should succeed immediately since the dependencies are already resolved and
correct.
