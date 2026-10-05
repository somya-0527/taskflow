# Skills Log

Notes on what I've learned while building TaskFlow, in the order I learned it.
Each entry covers the concept, why it matters, and the commands and gotchas worth
remembering. See `PROGRESS.md` for what's been *built*. This file tracks what's been *learned*.

Every step starts with a **Concepts to learn** table (what to study before/while building it),
followed by the notes from actually building it.

---

## 1. Docker & Docker Compose (2026-09-23)

### Concepts to learn
| Concept | What to understand |
|---|---|
| **Client vs daemon** | The `docker` CLI only sends commands. The daemon (`dockerd`) runs containers. On macOS it lives in a Linux VM run by Docker Desktop. |
| **Unix socket** | The file (`docker.sock`) the client and daemon talk through. A "failed to connect" error means nothing is listening on it. |
| **CLI plugins** | `docker compose` (v2) is a plugin found in `~/.docker/cli-plugins/`. It's different from the old standalone `docker-compose` (v1). |
| **Contexts** | Which daemon/socket the CLI is pointed at (`docker context ls`). |
| **Healthchecks** | A *started* container isn't necessarily *ready*. `pg_isready` tells you when Postgres accepts connections. |
| **Named volumes** | Data that outlives the container. `down` keeps it, `down -v` deletes it. |

### Docker is two separate programs
- **Client**: the `docker` CLI. It only sends commands.
- **Daemon/engine**: `dockerd`. It actually builds and runs containers.
- On macOS, containers need a Linux kernel, so **Docker Desktop runs the daemon inside a small
  Linux VM**. If Docker Desktop isn't running, there's no daemon.
- The client talks to the daemon through a **Unix socket** (e.g. `/var/run/docker.sock`).

### Telling client problems from daemon problems
| Error | What it means |
|---|---|
| `unknown command` / `unknown shorthand flag` | **Client-side**: the CLI doesn't recognize the command (e.g. a missing plugin) |
| `failed to connect to the docker API at unix:///...docker.sock` | **Daemon-side**: the CLI works, but nothing is listening on the socket (daemon not running, or wrong socket) |

### CLI plugins
- `docker compose` (with a space) is **Compose v2**, a *plugin* to the `docker` CLI.
- `docker-compose` (with a hyphen) is the old standalone v1 tool. It's deprecated, but you'll
  still see it in tutorials.
- The CLI looks for plugins in `~/.docker/cli-plugins/`. Docker Desktop ships them in
  `/Applications/Docker.app/Contents/Resources/cli-plugins/` and normally links them on first launch.
- Manual fix if the link is missing:
  ```bash
  mkdir -p ~/.docker/cli-plugins
  ln -sfn /Applications/Docker.app/Contents/Resources/cli-plugins/docker-compose ~/.docker/cli-plugins/docker-compose
  ```

### Contexts: which daemon the CLI points at
```bash
docker context ls               # shows each context and its socket endpoint
docker context use desktop-linux
```
Docker Desktop may use a per-user socket (`~/.docker/run/docker.sock`) instead of
`/var/run/docker.sock`. You can switch the context, or enable "Allow the default Docker socket"
in Settings → Advanced.

### Compose healthchecks
From this project's `docker-compose.yml`:
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U taskflow"]
```
- A container being **started** doesn't mean it's **ready**. Postgres needs a few seconds before
  it accepts connections.
- `docker compose ps` shows `healthy` once `pg_isready` passes. Start the app *after* that,
  otherwise Spring Boot fails with `Connection refused`.

### Named volumes
- `taskflow_pgdata:/var/lib/postgresql/data` keeps DB data when the container is removed.
- `docker compose down` keeps the data. `docker compose down -v` **deletes** it (use this when
  you want a clean database).

### Everyday commands
```bash
open -a Docker                  # start Docker Desktop (the daemon)
docker info                     # quick check that the daemon is reachable
docker compose up -d            # start services in the background (-d = detached)
docker compose ps               # status + health
docker compose logs -f postgres # follow a service's logs
docker compose down             # stop and remove containers (keep volumes)
```

---

## 2. Spring Boot basics + first endpoint + Spring Security (2026-09-23)

### Concepts to learn
| Concept | What to understand |
|---|---|
| **`@SpringBootApplication`** | = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`. What each part does. |
| **Auto-configuration** | Having a starter on the classpath switches features on (e.g. security locks everything) until you declare your own bean. |
| **Beans & dependency injection** | What a `@Bean` method is, how Spring wires beans together, why constructor injection is preferred. |
| **Component scanning** | Only classes under the main class's package, with stereotype annotations, get picked up. |
| **Profiles** | `application.yml` + `application-{profile}.yml` merging, and `spring.profiles.active`. |
| **Maven lifecycle** | `compile` → `test` → `package`. `spring-boot:run` compiles first. |
| **`@RestController` + mappings** | Class-level prefix vs method-level endpoint, and JSON serialization via Jackson. |
| **Security filter chain** | Requests pass through ordered filters. Your `SecurityFilterChain` bean replaces the default one. |
| **Authentication vs authorization** | Who you are (→ 401) vs what you're allowed to do (→ 403). `AuthenticationEntryPoint`. |
| **CSRF** | Why it matters for cookie-based sessions but not for header-based JWT APIs. |

Reading: Spring Security docs, "Servlet Security: The Big Picture".

### Component scanning
- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.
- Component scan only finds classes **in or under the package of the main class** (`com.taskflow`),
  and only if they're annotated with `@Configuration`, `@RestController`, `@Service`, `@Component`, etc.
- A class with **no `package` line**, or **no stereotype annotation**, is silently ignored.

### Imports vs annotations: compile time vs runtime
- **Imports** are for the **compiler** ("where does `HttpSecurity` live?"). If one is missing,
  you get a compile error.
- **Annotations** like `@Configuration` are for **Spring at runtime** ("what should I do with this class?").
  If one is missing, the code **compiles fine and is ignored** at runtime. That's the harder bug to spot.

### Annotation targets
- Every annotation declares where it's allowed with `@Target`:
  - `@Configuration` / `@EnableWebSecurity` / `@RestController` → `TYPE` (on the class)
  - `@Bean` / `@GetMapping` → `METHOD`
- Put one on the wrong element and you get: `annotation interface not applicable to this kind of declaration`.
- Cmd+click an annotation in the IDE to see its `@Target`.

### Controllers: class-level vs method-level mappings
```java
@RestController
@RequestMapping("/api/health")   // prefix only - does NOT create an endpoint
public class HealthController {
    @GetMapping                   // this is what makes GET /api/health an endpoint
    public Map<String, Object> health() { ... }
}
```
- If the method has no mapping, you get a **404**, even when security allows the request.
- `@RestController` = `@Controller` + `@ResponseBody`. Return values are turned into JSON by Jackson.
- Spring Boot 3 matches paths **exactly**. `/api/health.` and `/api/health/` are *not* `/api/health`.

### Spring Security auto-configuration
- Having `spring-boot-starter-security` on the classpath is enough to **lock every endpoint**. The default
  setup uses HTTP Basic + form login + sessions + a generated password (printed in the log).
- Declaring your own `SecurityFilterChain` `@Bean` **replaces** that default, and the generated password
  line disappears from the log.
- **You can tell from the response headers which setup handled the request:**
  - `WWW-Authenticate: Basic realm="Realm"` → the default config (HTTP Basic) is active
  - `Set-Cookie: JSESSIONID=...` → a session was created, so the config is *not* `STATELESS`
  - Neither header → your custom stateless chain is active

### My first `SecurityFilterChain`
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
```
- Rules are checked **top to bottom, and the first match wins**. `anyRequest()` must come last.

### 401 vs 403 (reproduced: `/api/anything-else` → 403)
- **401 Unauthorized** = "I don't know who you are." You should log in or send credentials.
- **403 Forbidden** = "I know who you are, and you're not allowed."
- What happens to a request with no credentials:
  1. `AnonymousAuthenticationFilter` puts an *anonymous* user into the `SecurityContext`.
  2. The authorization check (`.authenticated()`) fails and throws `AccessDeniedException`.
  3. `ExceptionTranslationFilter` catches it. Because the user is anonymous, it calls the
     **`AuthenticationEntryPoint`** to ask the client to authenticate.
  4. Login mechanisms like `httpBasic()` or `formLogin()` normally register an entry point (which sends
     401 + `WWW-Authenticate`). **My chain configures none**, so Spring falls back to
     `Http403ForbiddenEntryPoint` → **403**.
- Fix (for a JWT API): register an entry point that returns 401:
  ```java
  .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
  ```

### Debugging habits
- **Maven compile errors:** `File.java:[line,col] message`. Fix them top-down.
  - `cannot find symbol` + class → missing import
  - `cannot find symbol` + method → typo (watch for **Option+S = `ß`** on Mac!)
- `mvn spring-boot:run` **compiles first**. If compilation fails, whatever's on :8080 is an *old* process.
- If the line numbers in an error don't match what's in the editor, the **file isn't saved**.
  Turn on VS Code **File → Auto Save**.
- `lsof -nP -iTCP:8080 -sTCP:LISTEN` shows which process is using the port.

---

## 3. JPA & the `User` entity (Step 1, done 2026-09-23)

### Concepts to learn
| Concept | What to understand |
|---|---|
| **ORM / JPA / Hibernate** | JPA is the *specification* (annotations and interfaces). Hibernate is the *implementation*. Spring Data JPA is a layer on top that generates repositories for you. |
| **Entity** | A Java class mapped to a table. Each instance maps to one row. JPA needs a no-argument constructor. |
| **Persistence context** | Hibernate's in-memory "unit of work". `save()` doesn't necessarily run SQL right away. Changes are **flushed** later (usually at commit). |
| **`ddl-auto: update`** | Hibernate creates/alters tables from entities. Fine for dev, dangerous in prod. Replaced by Flyway in Step 8. |
| **ID generation** | `IDENTITY` (the database auto-increments) vs `SEQUENCE` (Hibernate's preference, allows batch inserts). |
| **Enum mapping** | `EnumType.STRING` vs `ORDINAL`, and why `ORDINAL` breaks when the enum is reordered. |
| **Reserved words** | `user` is reserved in Postgres, so the table is named `users`. |
| **Lombok on entities** | Use `@Getter`/`@Setter`, not `@Data`. `equals`/`hashCode`/`toString` over all fields breaks with relationships and mutable IDs. |
| **Spring Data repositories** | `JpaRepository<T, ID>` gives CRUD for free. Derived queries (`findByEmail`) are generated from the method name. Return `Optional` for "might not exist". |
| **Slice tests** | `@DataJpaTest` starts only JPA, swaps in H2, and rolls back each test. `saveAndFlush` forces SQL so constraints actually fire. |

Reading: Spring Data JPA reference, "Defining Query Methods". Vlad Mihalcea, "The best way to map an enum with JPA".

### From annotations to schema (what `\d users` showed)
| Java | Postgres |
|---|---|
| `@Table(name = "users")` | table `public.users` |
| `@GeneratedValue(IDENTITY)` on `Long id` | `bigint ... generated by default as identity` + primary key |
| `@Column(unique = true)` on `email` | `UNIQUE CONSTRAINT` (Hibernate gives it a random name like `uk6dotk...`) |
| `@Column(length = 100)` | `character varying(100)`. The default length is 255 |
| `@Enumerated(STRING)` + `length = 20` | `varchar(20)` **+ a `CHECK` constraint** limiting it to `'USER'`/`'ADMIN'` |
| `Instant` | `timestamp(6) with time zone` |
| `passwordHash` (camelCase) | `password_hash`. Spring Boot's naming strategy converts it automatically |

- Gotcha: because of that `CHECK` constraint, adding a new enum value later (e.g. `MANAGER`) with
  `ddl-auto: update` **won't update the constraint**. Inserts with the new value would fail.
  This is one reason to move to Flyway migrations.
- Inspect the real schema: `docker exec -it taskflow-postgres psql -U taskflow -d taskflow -c '\d users'`

### Repository = interface only
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);   // derived query: WHERE email = ?
    boolean existsByEmail(String email);
}
```

### Maven layout & dependency scopes (learned from a compile error)
- Tests go in **`src/test/java`**, not `src/main/java`. Put a test in `main` and you get
  `package org.junit.jupiter.api does not exist`.
- Maven compiles `main` and `test` separately. `<scope>test</scope>` dependencies (JUnit, AssertJ,
  `spring-boot-starter-test`, H2) are **only on the test classpath**, so they never ship in the JAR.
- Scopes: `compile` (default, everywhere), `runtime` (needed only when running, e.g. the Postgres driver
  and `jjwt-impl`), `test`, `provided`.
- Same package, different folder: `src/test/java/com/taskflow/user` shares the package with
  `src/main/java/com/taskflow/user`, so there's no import needed for `User`/`UserRepository`.

### First test: `@DataJpaTest`
- Slice test: only JPA starts (no web, no security). Postgres is swapped for **in-memory H2**, and each
  test is **rolled back** afterwards.
- `saveAndFlush` vs `save`: without a flush, SQL can stay queued in the persistence context and never
  reach the database (the test transaction is rolled back, never committed), so a constraint would never fire.
  *Correction:* with `IDENTITY` IDs, Hibernate runs the `INSERT` immediately anyway (it needs the generated ID),
  so plain `save()` would also have thrown here. Flushing is still the right habit, because with `SEQUENCE` IDs
  and for `UPDATE`s the SQL really is deferred. See NOTES.md §8.
- Result: `Tests run: 3, Failures: 0`. Reports are in `target/surefire-reports/`.

---

## 4. Registration: DTOs, validation, service layer, BCrypt (Step 2a, done 2026-10-05)

### Concepts to learn
| Concept | What to understand |
|---|---|
| **Layers: controller → service → repository** | The controller handles HTTP only. The service holds business rules. The repository handles data access. Why logic shouldn't live in controllers. |
| **DTOs (as Java `record`s)** | Never expose or accept entities directly. The request DTO controls what clients can *send*, and the response DTO controls what they *see* (never `passwordHash`). |
| **Bean Validation** | `@NotBlank`, `@Email`, `@Size` on the DTO + `@Valid` on the controller parameter. An invalid body → `MethodArgumentNotValidException` → 400. |
| **Password hashing** | Why hashing and not encryption. BCrypt = salted + deliberately slow. The `$2a$10$...` format. BCrypt ignores anything past 72 bytes. |
| **`PasswordEncoder` bean** | Declared once in config and injected wherever it's needed. It's the same mechanism as `SecurityFilterChain`. |
| **Constructor injection + Lombok `@RequiredArgsConstructor`** | `final` fields → one constructor → Spring injects through it. It makes code testable and immutable. |
| **`@Transactional`** | What a transaction boundary is, why it belongs on service methods, and rollback on runtime exceptions. |
| **Normalizing input** | Lower-casing/trimming email so `Alice@X.com` and `alice@x.com` count as the same user. |
| **Check-then-act race** | `existsByEmail` then `save` isn't atomic. The DB unique constraint is the real guard. |
| **HTTP status codes** | `201 Created`, `400 Bad Request`, `409 Conflict`. `ResponseStatusException`. |
| **The `/error` dispatch** | Spring Boot forwards errors to `/error`. If security blocks it, a 400/409 turns into a **401**. |
| **MockMvc integration tests** | `@SpringBootTest` + `@AutoConfigureMockMvc`. It sends fake HTTP requests through the whole stack, but it's **not** a real server (no `/error` forwarding). |

Reading: Spring docs "Validation" (Jakarta Bean Validation), OWASP "Password Storage Cheat Sheet",
Baeldung "Spring @Transactional".

### What I built
```
auth/RegisterRequest.java   request DTO (record) + validation rules
auth/AuthService.java       normalize email → duplicate check (409) → BCrypt hash → save
auth/AuthController.java    POST /api/auth/register → 201
user/UserResponse.java      response DTO (no passwordHash) + from(User) factory
config/SecurityConfig.java  + PasswordEncoder bean, + /api/auth/** and /error in permitAll
```

### Verified by hand (curl + psql)
| Request | Result |
|---|---|
| Register `Alice@Example.com` | **201**, email returned as `alice@example.com`, no password in the JSON |
| Register `ALICE@example.com` again | **409**, normalization caught the different capitalization |
| Invalid email / short password / empty name | **400**, Bean Validation |
| `GET /api/anything-else` | **401**, still protected |
| `select ... from users` | `password_hash` starts with `$2a$10$`, not the raw password |

### Key takeaways
- **Controller = HTTP, Service = rules, Repository = data.** Each layer has one job.
- **DTOs guard both directions:** the request DTO stops clients setting fields like `role`, and the response DTO stops
  `passwordHash` leaking.
- **`@Valid` is what activates the validation annotations.** Without it, they're just labels.
- **BCrypt:** salted (same password → different hash each time), slow on purpose (cost `10` = 2¹⁰ rounds),
  72-byte limit.
- **`@Transactional`** works through a proxy, so self-calls inside the same class bypass it.
- **The DB unique constraint is the real duplicate guard.** `existsByEmail` only gives a friendly 409, and the
  concurrent-request race still becomes a 500 until Step 3.
- How to check without tests: two terminals (one runs the app, one sends curl), plus `psql` to look at the DB.

Full theory: NOTES.md §10.

---

## 5. Login + JWT authentication (Step 2b, in progress, started 2026-10-05)

### Concepts to learn
| Concept | What to understand |
|---|---|
| **Stateless authentication** | The server keeps no session. Every request proves who it is by carrying a token. |
| **JWT structure** | `header.payload.signature`, Base64URL-encoded. The payload is **readable by anyone**, so never put secrets in it. |
| **Claims** | `sub` (subject = user id/email), `iat` (issued at), `exp` (expiry), plus custom claims like role. |
| **Signing (HMAC-SHA256)** | The signature proves the token was made by the server and not modified. The secret key must stay secret and be at least 32 bytes. |
| **Signed ≠ encrypted** | The signature stops *tampering*, not *reading*. |
| **Bearer tokens** | The client sends `Authorization: Bearer <token>` on every request. |
| **`@ConfigurationProperties`** | Binding `app.jwt.*` from YAML into a typed Java class. |
| **Checking a password at login** | Load the user by email, then `passwordEncoder.matches(raw, hash)`. We do this by hand in `AuthService` first. Spring's built-in version of the same idea (`AuthenticationManager` + `UserDetailsService`) comes later. |
| **Principal & authorities** | The object representing "who is logged in" (`AuthenticatedUser`) and their permissions (`ROLE_USER`). |
| **Custom filter (`OncePerRequestFilter`)** | Reads the `Authorization` header, validates the JWT, and puts an `Authentication` into the `SecurityContext`. |
| **Filter ordering** | `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`. |
| **Double registration gotcha** | `@Component` on a filter makes Boot register it *twice* (as a servlet filter too). |
| **Login error messages** | Same message for "no such user" and "wrong password", so attackers can't find out which emails exist. |
| **Reading the current user** | `@AuthenticationPrincipal` / `SecurityContextHolder` in a controller (`GET /api/users/me`). |
| **Token expiry & its limits** | Short expiry (1h here). JWTs can't be revoked before expiry, which is why refresh tokens and denylists exist (later). |

Reading: jwt.io introduction (paste a token into the debugger), Spring Security docs "Servlet
Authentication Architecture", jjwt README (0.12 API).

---

## Lessons carried over from the earlier build (before the reset)

I hit these once already. Keep them in mind when rebuilding auth:
- **JWT filter registered twice**: putting `@Component` on a `OncePerRequestFilter` makes Spring
  Boot auto-register it as a servlet filter, *in addition to* adding it to the security chain.
  → Don't annotate it with `@Component`. Create it as a bean and add it only through
  `http.addFilterBefore(...)` (or disable the auto-registration with a `FilterRegistrationBean`).
- **403 instead of 401 for unauthenticated requests**: reproduced and explained in section 2 (above).

---

## Up next

- [x] **Step 0**: Spring Security defaults, first `@RestController`, minimal `SecurityFilterChain`,
      401 vs 403. See section 2.
  - [ ] Still to cover: profiles (`application.yml` + `application-dev.yml`), the Maven lifecycle
  - [x] Apply the 401 entry-point fix to `SecurityConfig`
- [x] **Step 1**: JPA basics, `User` entity, repository, first `@DataJpaTest`. See section 3.
- [x] **Step 2a**: register endpoint, verified by hand. See section 4.
  - [ ] Tests (`AuthControllerTest`) postponed, to do later
- [ ] **Step 2b** (in progress): login + JWT + protected `/api/users/me`. See section 5.
- [ ] Housekeeping: `git init` + first commit; update `PROGRESS.md`/`README.md`
