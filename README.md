# TaskFlow Backend

A Jira/Trello-style project management platform, built as a learning + portfolio project.

**Tech:** Java 21 · Spring Boot 3.3 · Spring Security (stateless JWT) · Spring Data JPA / Hibernate ·
PostgreSQL 16 · Maven · Docker Compose · springdoc-openapi (Swagger UI)

| Doc | What's in it |
|---|---|
| `PROGRESS.md` | What has been built, step by step, and what's next |
| `SKILLS.md` | Learning log: concepts per step, and what I hit while building it |
| `NOTES.md` | Study guide: the Spring Boot theory behind everything built so far |

## Prerequisites

- Java 21 (`java -version` to check)
- Maven
- Docker Desktop (running)

## Running it locally

1. Start Postgres and wait until it shows `healthy`:
   ```bash
   docker compose up -d
   docker compose ps
   ```
2. Run the app:
   ```bash
   mvn spring-boot:run
   ```
3. Open Swagger UI at http://localhost:8080/swagger-ui.html, or use curl (below).

Tables are currently created automatically by Hibernate (`ddl-auto: update`, dev profile only).
This will be replaced by Flyway migrations.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/health` | Public | Liveness check |
| `POST` | `/api/auth/register` | Public | Create an account → `201`, `409` if the email is taken, `400` if invalid |
| `POST` | `/api/auth/login` | Public | Email + password → JWT access token, `401` if wrong |
| `GET` | `/api/users/me` | **Bearer token** | The current user's profile |

Example:
```bash
# register
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123","fullName":"Alice"}'

# login → copy accessToken from the response
curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123"}'

# call a protected endpoint
curl localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"
```

## Configuration

| Property | Env var | Default | Notes |
|---|---|---|---|
| `app.jwt.secret` | `JWT_SECRET` | dev-only placeholder | **Must** be set to a random value of 32+ bytes outside local dev |
| `app.jwt.expiration-ms` | – | `3600000` (1h) | Access token lifetime |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `dev` | `dev` points at the Docker Postgres |

## Tests

```bash
mvn test
```
Tests use an in-memory H2 database, so Docker doesn't need to be running.

## Project structure

Organized **package-by-feature**: each feature keeps its controller, service, DTOs and repository
together, instead of splitting by layer.

```
com.taskflow
├── TaskflowApplication.java      entry point
├── config/
│   ├── SecurityConfig.java       security filter chain, PasswordEncoder
│   └── JwtProperties.java        app.jwt.* settings
├── health/
│   └── HealthController.java     GET /api/health
├── auth/
│   ├── AuthController.java       POST /api/auth/register, /login
│   ├── AuthService.java          registration + login logic
│   ├── JwtService.java           create / parse JWTs
│   ├── JwtAuthenticationFilter.java  Bearer token → SecurityContext
│   ├── AuthenticatedUser.java    the logged-in principal
│   └── RegisterRequest / LoginRequest / AuthResponse   DTOs
└── user/
    ├── User.java, Role.java      entity + enum
    ├── UserRepository.java       Spring Data repository
    ├── UserService.java          user lookups
    ├── UserController.java       GET /api/users/me
    └── UserResponse.java         DTO (never exposes the password hash)
```
