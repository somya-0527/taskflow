# Build Progress

What's been **built**, step by step. For what's been *learned*, see `SKILLS.md` (learning log) and
`NOTES.md` (theory/study guide).

## Status at a glance

| Step | What | Status |
|---|---|---|
| 0 | Docker Postgres, health endpoint, stateless security config | ✅ Done, verified by hand |
| 1 | `User` entity + repository | ✅ Done, `UserRepositoryTest` (3 tests) passing |
| 2a | Registration (`POST /api/auth/register`) | ✅ Done, verified by hand (201/409/400) |
| 2b | Login + JWT + `GET /api/users/me` | 🔄 Code written, not yet compiling/verified |
| 3 | Global error handling | ⬜ Next |
| 4+ | Workspaces → projects/boards → tasks → comments | ⬜ |
| 8 | Flyway migrations (replace `ddl-auto: update`) | ⬜ |
| 9 | Testcontainers + controller tests | ⬜ (tests for 2a/2b deliberately postponed) |
| 10 | Dockerfile + CI | ⬜ |

---

## Step 0: Base setup, health endpoint, security (2026-09-23)

- `docker-compose.yml`: Postgres 16 with a healthcheck and a named volume.
- `health/HealthController`: `GET /api/health` → `{"status":"UP","timestamp":...}`.
- `config/SecurityConfig`: custom `SecurityFilterChain` that replaces Spring Boot's default:
  - CSRF disabled (token-in-header API), `STATELESS` sessions.
  - `permitAll` for health, `/api/auth/**`, `/error`, Swagger. Everything else requires auth.
  - `HttpStatusEntryPoint(401)`, so unauthenticated requests get **401**, not 403.

Bugs hit: `SecurityConfig` ignored (missing `package`/`@Configuration`), annotations on the wrong
element, stray `ß` characters (Option+S), unsaved files. All are written up in `NOTES.md` §14.

## Step 1: `User` entity (2026-09-23)

- `user/User`: `users` table (`user` is reserved in Postgres). Identity PK, unique `email`, `passwordHash`,
  `fullName`, `role` (`EnumType.STRING`), `createdAt`/`updatedAt` via Hibernate timestamps.
- `user/Role`: `USER`, `ADMIN`.
- `user/UserRepository`: `findByEmail`, `existsByEmail` (derived queries).
- `UserRepositoryTest` (`@DataJpaTest`, H2): save + find, exists, duplicate email rejected.

## Step 2a: Registration (2026-10-05)

- `POST /api/auth/register` → **201** with a `UserResponse` (never includes the password hash).
- `RegisterRequest` record with Bean Validation (`@Email`, password 8–72 chars, name ≤ 100) → **400** on bad input.
- `AuthService.register`: lower-cases/trims the email, rejects duplicates with **409**, hashes with BCrypt.
- `PasswordEncoder` bean (`BCryptPasswordEncoder`) in `SecurityConfig`.
- Verified by hand: 201 → 409 (also for different capitalization) → 400; DB stores `$2a$10$...`.

Known gap: two simultaneous registrations with the same email can both pass the `existsByEmail`
check. The DB unique constraint stops the second one, but it surfaces as a **500** until Step 3.

## Step 2b: Login + JWT (in progress, started 2026-10-05)

Written:
- `config/JwtProperties`: binds `app.jwt.secret` / `app.jwt.expiration-ms` from YAML.
- `auth/JwtService`: creates (HS256, `sub` = user id, `email` and `role` claims, 1h expiry) and parses tokens.
- `auth/LoginRequest`, `auth/AuthResponse` (`accessToken`, `tokenType`, `expiresIn`).
- `AuthService.login` + `POST /api/auth/login`: same 401 message for unknown email and wrong password.
- `auth/JwtAuthenticationFilter`: reads `Authorization: Bearer ...` and sets the `SecurityContext`.
  Deliberately **not** a `@Component` (avoids the double-registration bug from the first build).
- `user/UserService` + `user/UserController`: `GET /api/users/me` (first protected endpoint).

Still to do: fix the compile errors, wire the filter into `SecurityConfig`, run the curl checks
(login 200/401, `/me` with/without/tampered token).

---

## Lessons from the first build (before the 2026-09-22 reset)

The project was built once before and reset back to scaffolding. Two bugs found back then are
worth remembering:
- `@Component` on the JWT filter → registered twice (servlet container + security chain).
- 403 instead of 401 for anonymous requests → no `AuthenticationEntryPoint` configured.
