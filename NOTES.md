# Spring Boot Study Notes

Theory behind everything built in TaskFlow so far, written to study from.
Each section explains *how it works underneath*, not just what to type.

- `SKILLS.md` is the step-by-step learning log: what I hit and when.
- `NOTES.md` (this file) is the textbook version, organized by topic.

**Covered so far:** Docker basics → Maven → Spring core (IoC/DI, beans) → Spring Boot
(auto-configuration, configuration, profiles) → Spring MVC (REST controllers, JSON) → Spring Security
(filter chain, 401/403, CSRF, stateless) → JPA/Hibernate (entities, persistence context,
repositories) → Building a feature (layers, DTOs, validation, BCrypt, `@Transactional`) → Lombok →
Testing (`@DataJpaTest`).

---

## Table of contents

1. [Docker for local development](#1-docker-for-local-development)
2. [Maven: build tool and dependency manager](#2-maven-build-tool-and-dependency-manager)
3. [Spring core: IoC, DI, and beans](#3-spring-core-ioc-di-and-beans)
4. [Spring Boot: auto-configuration and startup](#4-spring-boot-auto-configuration-and-startup)
5. [Configuration and profiles](#5-configuration-and-profiles)
6. [Spring MVC: how a request reaches your controller](#6-spring-mvc-how-a-request-reaches-your-controller)
7. [Spring Security](#7-spring-security)
8. [JPA and Hibernate](#8-jpa-and-hibernate)
9. [Spring Data JPA repositories](#9-spring-data-jpa-repositories)
10. [Building a feature: layers, DTOs, validation, password hashing](#10-building-a-feature-layers-dtos-validation-password-hashing)
11. [Lombok](#11-lombok)
12. [Testing](#12-testing)
13. [Java language features used so far](#13-java-language-features-used-so-far)
14. [Debugging playbook](#14-debugging-playbook)
15. [Self-test questions](#15-self-test-questions)

---

## 1. Docker for local development

### Why Docker here
The app needs PostgreSQL. Installing Postgres directly on the Mac works, but Docker gives you a
**disposable, versioned, identical-everywhere** database: one command to start it, and one to wipe it.

### Core vocabulary
| Term | Meaning |
|---|---|
| **Image** | A read-only template (e.g. `postgres:16-alpine`): the filesystem plus the startup command. |
| **Container** | A running instance of an image. You can run many containers from one image. |
| **Port mapping** `"5432:5432"` | `host:container`. Traffic to `localhost:5432` on the Mac goes to port 5432 inside the container. |
| **Volume** | Storage that lives *outside* the container's lifecycle. Delete the container and the data survives. |
| **Docker Compose** | A YAML file describing one or more services, started together with `docker compose up`. |

### Architecture: client, daemon, socket
```
 docker CLI  ──(HTTP over Unix socket /var/run/docker.sock)──►  Docker daemon (dockerd)
 (client)                                                       runs inside a Linux VM
                                                                managed by Docker Desktop
```
- Containers share the host's **Linux** kernel. macOS isn't Linux, so Docker Desktop runs a light
  Linux VM, and the daemon lives there.
- **Client error** (e.g. `unknown command`): the CLI itself is broken or missing a plugin.
- **Daemon error** (`failed to connect to the docker API`): the CLI is fine, but nothing is listening.
  Start Docker Desktop.
- `docker compose` (v2) is a **CLI plugin** found in `~/.docker/cli-plugins/`. `docker-compose` (with a hyphen)
  is the deprecated v1 standalone tool.

### This project's `docker-compose.yml`, explained
```yaml
services:
  postgres:
    image: postgres:16-alpine          # official image, Alpine Linux base (small)
    environment:                       # the image reads these on FIRST start to create the DB/user
      POSTGRES_DB: taskflow
      POSTGRES_USER: taskflow
      POSTGRES_PASSWORD: taskflow_dev_password
    ports:
      - "5432:5432"
    volumes:
      - taskflow_pgdata:/var/lib/postgresql/data   # where Postgres stores its files
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U taskflow"]
```
- **Gotcha:** the `POSTGRES_*` variables only take effect when the volume is **empty**. Change the
  password later and nothing happens until you run `docker compose down -v`, which wipes the data.
- **Healthcheck:** "container started" ≠ "database accepting connections". `docker compose ps` shows
  `healthy` once `pg_isready` succeeds.

---

## 2. Maven: build tool and dependency manager

### What Maven does
1. **Dependency management:** downloads the libraries declared in `pom.xml` (and *their* dependencies,
   called transitive dependencies) into `~/.m2/repository`.
2. **Build lifecycle:** compiles, tests, and packages the app in a fixed order.

### Standard directory layout
```
src/main/java        → production code
src/main/resources   → application.yml, static files, etc. (copied onto the classpath)
src/test/java        → test code
src/test/resources   → test-only config
target/              → build output (generated, git-ignored)
```
Every Maven and Gradle project follows this. Code in `src/test/java` and `src/main/java` can share a
**package** (e.g. `com.taskflow.user`). They're two folders merged into one package at compile time.

### The lifecycle
Phases run **in order**. Asking for a later phase runs all the earlier ones first.
```
validate → compile → test-compile → test → package → verify → install → deploy
```
| Command | What happens |
|---|---|
| `mvn compile` | Compiles `src/main/java` into `target/classes` |
| `mvn test` | compile + compile tests + run tests (reports in `target/surefire-reports/`) |
| `mvn package` | ...and builds `target/taskflow-backend-0.1.0.jar` |
| `mvn clean` | Deletes `target/` |
| `mvn spring-boot:run` | A **plugin goal**, not a phase. It compiles first, then starts the app |

If compilation fails, `spring-boot:run` never starts the app. Any server still answering on :8080 is
an **old** process.

### Dependency scopes
| Scope | On compile classpath? | At runtime? | In the final JAR? | Example in this project |
|---|---|---|---|---|
| `compile` (default) | ✅ | ✅ | ✅ | `spring-boot-starter-web` |
| `runtime` | ❌ | ✅ | ✅ | `postgresql`, `jjwt-impl` |
| `test` | only for tests | only for tests | ❌ | `spring-boot-starter-test`, `h2` |
| `provided` | ✅ | ❌ (expected to be supplied) | ❌ | (servlet API in a WAR) |
| `optional=true` | ✅ | ✅ | not passed on to dependents | `lombok` |

- `runtime` makes sense for the Postgres driver: your code only talks to the JDBC **interfaces**, and the
  driver implementation is only needed when the app runs.
- A test placed in `src/main/java` fails with `package org.junit.jupiter.api does not exist`, because
  `test`-scoped libraries aren't visible to main code.

### The parent POM and starters
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
</parent>
```
- The parent provides **dependency management**: a curated list of compatible versions. That's why most
  dependencies in `pom.xml` have no `<version>`.
- **Starters** (`spring-boot-starter-web`, `-data-jpa`, `-security`, ...) are dependency bundles.
  `starter-web` brings in Spring MVC, embedded Tomcat, and Jackson. (Validation is **not** included
  since Boot 2.3. That's why `pom.xml` has a separate `spring-boot-starter-validation`.)
- Libraries outside Spring's list (jjwt, springdoc) need explicit versions.

---

## 3. Spring core: IoC, DI, and beans

This is the foundation everything else rests on.

### Inversion of Control (IoC)
Normally your code creates its own collaborators:
```java
AuthService service = new AuthService(new UserRepositoryImpl(...), new BCryptPasswordEncoder());
```
With IoC, **the framework creates and connects objects**, and your class only *declares* what it needs.
The object that does this is the **`ApplicationContext`** (the "Spring container").

### Beans
A **bean** is any object the `ApplicationContext` creates and manages. There are two ways to declare one:

**1. Stereotype annotation on a class**, picked up by component scanning:
| Annotation | Meaning |
|---|---|
| `@Component` | Generic bean |
| `@Service` | Business logic (same as `@Component`, just more descriptive) |
| `@Repository` | Data access (also translates DB exceptions into Spring's `DataAccessException`) |
| `@Controller` / `@RestController` | Web request handler |
| `@Configuration` | A class that *declares other beans* via `@Bean` methods |

**2. `@Bean` method inside a `@Configuration` class.** Use this for objects you can't annotate
(library classes) or that need setup code:
```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception { ... }
}
```
Spring calls the method once at startup and stores the returned object as a bean. `HttpSecurity` is
itself a bean, so Spring **injects** it as the method parameter.

### Dependency Injection (DI)
Spring provides a bean's dependencies to it. **Constructor injection** is the recommended style:
```java
@Service
public class AuthService {
    private final UserRepository userRepository;     // final = must be set, never changes

    public AuthService(UserRepository userRepository) {   // Spring calls this with the bean
        this.userRepository = userRepository;
    }
}
```
Why constructor injection beats field injection (`@Autowired private UserRepository repo;`):
- Fields can be `final`, so the object is never half-built.
- Dependencies are explicit, so too many constructor parameters signals a class doing too much.
- It's easy to unit test: `new AuthService(mockRepo)` with no Spring needed.
- A class with a single constructor needs **no `@Autowired`** at all.

### Bean scope and lifecycle
- The default scope is **singleton**: one instance per application context, shared by all threads.
  → Beans must be **stateless** (no per-request data in fields).
- Lifecycle: instantiate → inject dependencies → `@PostConstruct` → in use → `@PreDestroy` on shutdown.

### Component scanning
`@SpringBootApplication` on `com.taskflow.TaskflowApplication` scans **`com.taskflow` and all its
sub-packages** for stereotype annotations.
- A class in another package, or with **no `package` declaration**, is never found.
- A class without a stereotype annotation is ignored, even if it has `@Bean` methods inside.
  That's why `SecurityConfig` did nothing until it got `@Configuration`.

### Annotation targets
Every annotation declares *where* it may be used with `@Target`:
- `@Configuration`, `@RestController`, `@Entity`, `@EnableWebSecurity` → `TYPE` (classes)
- `@Bean`, `@GetMapping`, `@Test` → `METHOD`
- `@Id`, `@Column` → `FIELD` (or `METHOD`)

Using one in the wrong place causes the compile error
`annotation interface not applicable to this kind of declaration`.

### Compile time vs runtime
- **Imports** help the *compiler* find classes. If one is missing, you get a **compile error**.
- **Annotations** tell *Spring at runtime* what to do. If one is missing, the code **compiles fine and is
  silently ignored**. This kind of bug is harder to spot, so learn to recognize it from behaviour.

---

## 4. Spring Boot: auto-configuration and startup

### Spring vs Spring Boot
- **Spring Framework**: IoC, MVC, data access, etc. Powerful, but historically needed lots of configuration.
- **Spring Boot**: opinionated defaults on top of Spring. Embedded server, starters,
  **auto-configuration**, and externalized config. "Just run it."

### `@SpringBootApplication`
It's three annotations in one:
```java
@SpringBootConfiguration    // = @Configuration: this class can declare beans
@EnableAutoConfiguration    // turn on auto-configuration
@ComponentScan              // scan this package and sub-packages
```

### How auto-configuration works
Spring Boot ships hundreds of auto-configuration classes. Each one is **conditional**:
```java
@AutoConfiguration
@ConditionalOnClass(DataSource.class)              // only if this class is on the classpath
@ConditionalOnMissingBean(DataSource.class)         // only if YOU didn't define one
public class DataSourceAutoConfiguration { ... }
```
The two key ideas:
1. **Classpath-driven:** adding a starter to `pom.xml` adds classes to the classpath, which switches
   features on. Adding `spring-boot-starter-security` immediately locked every endpoint, without a
   single line of code.
2. **Backs off when you define your own:** `@ConditionalOnMissingBean` means your bean wins. Defining
   a `SecurityFilterChain` bean replaced Boot's default chain, and the
   `Using generated security password` log line disappeared.

To see which auto-configurations matched and why, run with `--debug` (or `debug: true` in YAML) to
get the **condition evaluation report**.

### Embedded server
`spring-boot-starter-web` includes **Tomcat**. The app is a plain `main()` method that starts Tomcat
inside the same JVM on port 8080 (`server.port`). There's no external server install and no WAR deployment.

### What happens at startup (`SpringApplication.run`)
1. Load configuration (`application.yml`, the active profile's file, env vars, ...).
2. Create the `ApplicationContext`.
3. Component scan + evaluate auto-configurations → register bean definitions.
4. Create singleton beans and inject dependencies. JPA connects to the DB here, and Hibernate runs
   `ddl-auto`.
5. Start embedded Tomcat → the app is ready (`Started TaskflowApplication in X seconds`).

---

## 5. Configuration and profiles

### `application.yml`
Externalized configuration lets you change behaviour without recompiling. Spring Boot reads
`src/main/resources/application.yml` (or `.properties`) automatically.

### Profiles
A profile is a named set of configuration, e.g. `dev`, `test`, `prod`.
```yaml
# application.yml (always loaded)
spring:
  profiles:
    active: dev        # ← activates the dev profile
```
→ Spring then also loads **`application-dev.yml`**, whose values **override** matching keys in
`application.yml`.

In this project:
| File | Holds |
|---|---|
| `application.yml` | Shared settings: app name, port, `open-in-view: false`, swagger path, JWT settings |
| `application-dev.yml` | Dev-only settings: local Postgres URL/credentials, `ddl-auto: update`, SQL logging |

Override the active profile without editing files:
`SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run`.

### Property precedence (highest wins, simplified)
1. Command-line arguments (`--server.port=9090`)
2. Environment variables (`SERVER_PORT=9090`). Boot maps `SERVER_PORT` → `server.port` (relaxed binding)
3. Profile-specific files (`application-dev.yml`)
4. `application.yml`

### Placeholders with defaults
```yaml
secret: ${JWT_SECRET:dev-only-secret-...}
```
This means: use the `JWT_SECRET` environment variable if it's set, otherwise the text after the `:`. It's a common
pattern for secrets, since real values come from the environment and are **never committed**.

### Settings in this project worth understanding
| Setting | Meaning |
|---|---|
| `spring.jpa.hibernate.ddl-auto: update` | Hibernate alters the schema to match entities. Dev-only (see §8) |
| `spring.jpa.show-sql` / `format_sql` | Print generated SQL. Great for learning what JPA really does |
| `spring.jpa.open-in-view: false` | See below |
| `logging.level.com.taskflow: DEBUG` | Log level per package |

**Open Session in View (OSIV):** when enabled (Boot's default, with a warning), the database
session stays open during the **entire HTTP request**, including JSON serialization. Lazy relationships
then "just work" in controllers, but they fire hidden SQL queries outside your service layer and hold DB
connections longer. Setting it to `false` forces you to load what you need inside `@Transactional`
service methods. It's stricter, but more predictable. You'll feel this in Step 5 (relationships).

---

## 6. Spring MVC: how a request reaches your controller

### The request path
```
HTTP request
   │
   ▼
Tomcat (embedded servlet container)
   │
   ▼
Servlet filters  ← Spring Security lives HERE, before MVC (see §7)
   │
   ▼
DispatcherServlet  ("front controller": the single entry point for all MVC requests)
   │  asks HandlerMapping: "which method handles GET /api/health?"
   ▼
HealthController.health()
   │  returns Map<String, Object>
   ▼
HttpMessageConverter (Jackson) → serializes the return value to JSON
   │
   ▼
HTTP response  200, Content-Type: application/json
```

### Controllers
```java
@RestController                    // = @Controller + @ResponseBody
@RequestMapping("/api/health")     // class-level: URL PREFIX for every method in the class
public class HealthController {

    @GetMapping                    // method-level: actually creates the endpoint GET /api/health
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now());
    }
}
```
- `@Controller` methods normally return a *view name* (HTML template). `@ResponseBody` says "write the
  return value directly into the response body". `@RestController` applies that to every method.
- **The class-level mapping alone creates no endpoint.** Without `@GetMapping` on the method → **404**.
- Mapping annotations: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`.
- **Exact matching:** Spring Boot 3 removed trailing-slash and suffix matching. `/api/health/` and
  `/api/health.` are **not** `/api/health`.

### JSON with Jackson
- Jackson (included in `starter-web`) converts objects ↔ JSON. It works with `Map`s, POJOs with getters, and records.
- Boot configures Jackson to write `java.time` types like `Instant` as **ISO-8601 strings**
  (`"2026-09-23T12:49:02.278777Z"`), not numbers.
- The JSON key is whatever string or field name you used. A typo like `"timestampß"` shows up in the API exactly as written.

### HTTP status codes used so far
| Code | Meaning | When |
|---|---|---|
| 200 OK | Success | `GET /api/health` |
| 401 Unauthorized | "Who are you?" Not authenticated | Request to a protected URL with no or invalid credentials |
| 403 Forbidden | "I know who you are; you can't do this" | Authenticated, but lacking permission |
| 404 Not Found | No handler for this URL | Missing `@GetMapping`, wrong path |

(The name "Unauthorized" for 401 is a historical misnomer. It really means *unauthenticated*.)

---

## 7. Spring Security

### Where security sits
Spring Security is a set of **servlet filters** that run **before** the `DispatcherServlet`. A request
that security rejects never reaches your controller.

```
Tomcat
  └─ DelegatingFilterProxy        (a normal servlet filter that hands off to a Spring bean)
       └─ FilterChainProxy         (the "springSecurityFilterChain" bean)
            └─ SecurityFilterChain (YOUR bean: an ordered list of security filters)
                 ├─ ... SecurityContextHolderFilter
                 ├─ ... (authentication filters: Basic, form login, later your JWT filter)
                 ├─ AnonymousAuthenticationFilter
                 ├─ ExceptionTranslationFilter
                 └─ AuthorizationFilter      ← checks your authorizeHttpRequests rules
  └─ DispatcherServlet → your controller
```

### Key concepts
| Concept | Meaning |
|---|---|
| **Authentication** | Proving *who* you are (password, token). |
| **Authorization** | Deciding *what* you may do (rules, roles). |
| **`Authentication` object** | Represents the current user: principal, credentials, authorities (roles). |
| **`SecurityContextHolder`** | Holds the current `Authentication` for the request's thread. Filters put it there, and your code reads it. |
| **`AuthenticationEntryPoint`** | What to send when an *unauthenticated* user hits a protected URL ("please authenticate"). |
| **`AccessDeniedHandler`** | What to send when an *authenticated* user lacks permission (403). |

### The default configuration (no `SecurityFilterChain` bean)
Spring Boot's auto-configuration:
- Protects **every** URL.
- Creates a single in-memory user `user` with a random password printed in the log.
- Enables **form login** (for browsers) and **HTTP Basic** (for API clients).
- Uses **HTTP sessions** (`JSESSIONID` cookie).
- Enables CSRF protection.

You can identify it from the response headers:
- `WWW-Authenticate: Basic realm="Realm"`: the HTTP Basic entry point is active.
- `Set-Cookie: JSESSIONID=...`: a server-side session was created.

### This project's configuration
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```
Line by line:
- **`HttpSecurity`** is a builder. Each call configures part of the filter chain, and `http.build()`
  produces the `SecurityFilterChain`. The `x -> x.something()` style is the **lambda DSL**, the
  current way to configure it (the old `.and()` chaining is deprecated).
- **`csrf.disable()`**: see CSRF below.
- **`STATELESS`**: Spring Security never creates an HTTP session or reads one. Every request
  must carry its own credentials (a JWT, from Step 2b). That's why `JSESSIONID` disappeared.
- **`authenticationEntryPoint(... UNAUTHORIZED)`**: unauthenticated requests get a plain **401**.
- **`authorizeHttpRequests`**: URL rules, checked **top to bottom; the first match wins**.
  `anyRequest()` must be last, as the catch-all.
  - `permitAll()`: anyone, including anonymous users.
  - `authenticated()`: any logged-in user.
  - (later) `hasRole("ADMIN")`: a specific role.
- `/**` matches any number of path segments, and `*` matches one.

### 401 vs 403: the full story (why you got 403 before the fix)
What happens to a request with **no credentials** to `/api/anything-else`:

1. No authentication filter finds credentials.
2. **`AnonymousAuthenticationFilter`** places an `AnonymousAuthenticationToken` in the
   `SecurityContext`. The user isn't "nobody"; they're "anonymous".
3. **`AuthorizationFilter`** checks `anyRequest().authenticated()`. Anonymous doesn't count, so it throws
   `AccessDeniedException`.
4. **`ExceptionTranslationFilter`** catches it and asks: *is the current user anonymous?*
   - **Yes** → call the **`AuthenticationEntryPoint`** ("go authenticate").
   - No (a real, logged-in user) → call the **`AccessDeniedHandler`** → 403.
5. Which entry point? Login mechanisms register one: `httpBasic()` registers one that returns
   401 + `WWW-Authenticate: Basic`. **Our chain configured no login mechanism**, so Spring used its
   fallback, **`Http403ForbiddenEntryPoint`** → **403**. That's wrong for an anonymous user.
6. Fix: register `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` → **401**.

### CSRF (Cross-Site Request Forgery)
- **The attack:** you're logged in to `bank.com` (session cookie in the browser). You visit `evil.com`,
  which contains a hidden form that POSTs to `bank.com/transfer`. The **browser automatically attaches
  the `bank.com` cookie**, so the bank sees a valid, authenticated request you never meant to send.
- **The defence:** a CSRF token. The server requires a secret value in the request that `evil.com`
  can't read or guess.
- **Why disabling it is fine for this API:** the vulnerability exists because **browsers send cookies
  automatically**. This API will use a JWT in the `Authorization: Bearer ...` header, which the browser
  **never** adds by itself, so `evil.com` can't make an authenticated request. No automatic
  credentials means no CSRF.
- **When it's NOT fine:** if you ever store the JWT in a cookie, or use sessions, CSRF protection is
  needed again.

### Default security response headers
Even on a 200 response, Spring Security adds protective headers:
| Header | Purpose |
|---|---|
| `X-Content-Type-Options: nosniff` | Browser must trust the declared `Content-Type`, not guess it |
| `X-Frame-Options: DENY` | Page can't be embedded in an iframe (clickjacking protection) |
| `Cache-Control: no-cache, no-store, ...` | Responses with possibly sensitive data aren't cached |
| `X-XSS-Protection: 0` | Disables an old, buggy browser XSS filter (modern advice) |

### The `/error` path (relevant from Step 2a)
When a controller throws (e.g. 400 validation failure), Spring Boot **forwards** the request to
`/error` to render the error response. That forward goes through the security chain **again**. If
`/error` isn't `permitAll()`, the anonymous user is blocked and your 400 turns into a **401**.

---

## 8. JPA and Hibernate

### The layers
```
Your code ──► Spring Data JPA (repositories)        generates repository implementations
                   │
                   ▼
              JPA (jakarta.persistence.*)           the SPECIFICATION: annotations + EntityManager API
                   │
                   ▼
              Hibernate                              the IMPLEMENTATION (ORM engine), generates SQL
                   │
                   ▼
              JDBC + PostgreSQL driver               sends SQL to the database
                   │
                   ▼
              PostgreSQL
```
**ORM (Object-Relational Mapping):** maps classes ↔ tables, objects ↔ rows, and fields ↔ columns, so you
work with Java objects and Hibernate writes the SQL.

`jakarta.persistence` vs `javax.persistence`: Spring Boot 3 uses **Jakarta EE**. Old tutorials using
`javax.*` imports are for Spring Boot 2.

### Entities
```java
@Entity                        // this class is persistent
@Table(name = "users")         // table name (default would be "user", a Postgres reserved word!)
@Getter @Setter @NoArgsConstructor
public class User {

    @Id                                                   // primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // DB generates it (auto-increment)
    private Long id;

    @Column(nullable = false, unique = true)              // NOT NULL + UNIQUE constraint
    private String email;

    @Column(nullable = false)
    private String passwordHash;                          // → column password_hash

    @Column(nullable = false, length = 100)               // varchar(100). Default length is 255
    private String fullName;

    @Enumerated(EnumType.STRING)                          // store "USER", not 0
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;                        // Java-side default

    @CreationTimestamp                                    // Hibernate sets it on INSERT
    @Column(nullable = false, updatable = false)          // never included in UPDATE statements
    private Instant createdAt;

    @UpdateTimestamp                                      // Hibernate sets it on every UPDATE
    @Column(nullable = false)
    private Instant updatedAt;
}
```
**Rules for entities:**
- Must have a **no-argument constructor** (Hibernate creates the object first, then fills in the fields).
- Must have an `@Id`.
- Should not be `final` (Hibernate creates subclass proxies for lazy loading).
- Use wrapper types (`Long`, not `long`) for IDs. `null` means "not saved yet".

### Annotation → schema (verified with `\d users`)
| Java | PostgreSQL |
|---|---|
| `@Table(name = "users")` | `public.users` |
| `Long id` + `IDENTITY` | `bigint generated by default as identity` + `PRIMARY KEY` |
| `@Column(unique = true)` | `UNIQUE CONSTRAINT` (auto-named like `uk6dotkott...`) |
| `String` (default) | `varchar(255)` |
| `length = 100` | `varchar(100)` |
| `@Enumerated(STRING)` | `varchar(20)` **+ `CHECK (role IN ('USER','ADMIN'))`** |
| `Instant` | `timestamp(6) with time zone` |
| `passwordHash` | `password_hash` (Spring Boot's naming strategy converts camelCase to snake_case) |

### ID generation strategies
| Strategy | How | Trade-off |
|---|---|---|
| `IDENTITY` | DB auto-increment column | Simple. But Hibernate must run the `INSERT` **immediately** on `persist` to learn the ID, which prevents JDBC batch inserts |
| `SEQUENCE` | DB sequence object. Hibernate fetches IDs ahead of time | Supports batching. Hibernate's recommended choice for Postgres |
| `UUID` | Random 128-bit ID generated in Java | Globally unique, not guessable. Bigger, and random order hurts index locality |
| `AUTO` | Hibernate picks | Avoid. Be explicit |

### Enum mapping: STRING vs ORDINAL
- `ORDINAL` (the default!) stores the position: `USER=0, ADMIN=1`. Insert `MANAGER` between them later
  and every existing `ADMIN` row (1) now reads as `MANAGER`. **Silent data corruption.**
- `STRING` stores the name. It's safe to reorder and readable in SQL. You only need to be careful when *renaming*.
- Hibernate 6 also adds a `CHECK` constraint for the allowed values. Adding a new enum constant later
  won't update that constraint under `ddl-auto: update`, so inserts with it fail. You need a migration.

### Date/time types
- **`Instant`**: a point on the UTC timeline. Use it for "when did this happen" (`createdAt`).
- `LocalDateTime`: date and time **without** a timezone. Ambiguous for events. Avoid it for timestamps.
- `LocalDate`: a calendar date (e.g. a task's due date).

### `ddl-auto`
| Value | Behaviour |
|---|---|
| `none` | Do nothing (production + Flyway) |
| `validate` | Check that entities match the schema; fail at startup if not |
| `update` | Add missing tables and columns. **Never** drops or renames. Can leave stale constraints. Dev only |
| `create` | Drop and recreate the schema at startup |
| `create-drop` | Like `create`, plus drop on shutdown (tests) |

Why `update` is dangerous in production: it guesses, it can't rename (it adds a new column instead and orphans the old
data), it has no history, and it can't be reviewed. Flyway (Step 8) replaces it with versioned SQL scripts.

### The persistence context
The most important concept for understanding JPA behaviour.

- Each transaction has a **persistence context**: Hibernate's in-memory cache of the entities it's
  currently managing (the "unit of work").
- **Entity states:**
  | State | Meaning |
  |---|---|
  | Transient | `new User()`, not known to Hibernate |
  | Managed | Saved or loaded inside the current persistence context. Changes are tracked |
  | Detached | Was managed, but the context closed (e.g. after the transaction ended) |
  | Removed | Scheduled for deletion |
- **Dirty checking:** modify a *managed* entity (`user.setFullName("x")`) and Hibernate automatically
  issues an `UPDATE` at flush time. No `save()` call is needed.
- **Flush:** writing pending changes as SQL to the database. It happens automatically **before commit**, before
  queries that could be affected, or manually with `flush()` / `saveAndFlush()`.
- **Flush ≠ commit:** flushed SQL is still inside the transaction and can be rolled back.
- **First-level cache:** `findById(1)` twice in the same transaction → only one `SELECT`.

**Why `saveAndFlush` in the duplicate-email test:** the second `save()` may only *queue* the change.
The constraint violation only happens when the SQL actually reaches the database. In a
`@DataJpaTest`, the transaction is rolled back at the end and never committed, so without a flush
the error might never surface. `saveAndFlush` forces the SQL to run *now*, inside `assertThrows`.
*(Precise note: with `IDENTITY`, Hibernate already runs the `INSERT` immediately on `persist`, as
explained above, so here plain `save()` would also have thrown. The flush is still the right habit:
with `SEQUENCE` IDs, or for `UPDATE`s, the SQL really is deferred.)*

### Transactions
- A transaction is all-or-nothing: either every change commits, or none does.
- In Spring, `@Transactional` on a (service) method starts a transaction before the method and
  commits after it. A `RuntimeException` causes a **rollback**.
- Spring Data repository methods are already transactional individually. `@Transactional` on a service
  groups *several* calls into one unit (see §10).

---

## 9. Spring Data JPA repositories

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```
- **There's no implementation class.** At startup, Spring Data generates a proxy that implements the
  interface and registers it as a bean, ready to inject.
- `JpaRepository<User, Long>` = entity type + ID type. You get `save`, `saveAll`, `saveAndFlush`,
  `findById`, `findAll` (with paging and sorting), `count`, `existsById`, `delete`, ... for free.

### Derived query methods
Spring parses the **method name** into a query:
| Method | Generated JPQL (roughly) |
|---|---|
| `findByEmail(String e)` | `select u from User u where u.email = ?1` |
| `existsByEmail(String e)` | a `select ... limit 1` that returns true/false |
| `findByFullNameContainingIgnoreCase(String s)` | `where upper(u.fullName) like upper('%s%')` |
| `findByRoleAndCreatedAtAfter(Role r, Instant t)` | `where u.role = ?1 and u.createdAt > ?2` |
| `countByRole(Role r)` | `select count(u) ... where u.role = ?1` |

- Names refer to **Java field names** (`fullName`), not column names (`full_name`).
- A typo in the method name → the app **fails at startup** (`No property 'emial' found for type 'User'`).
- For complex queries, use `@Query("select ...")` instead of long method names.

### `Optional` return types
`Optional<User>` forces the caller to deal with "not found":
```java
User u = userRepository.findByEmail(email)
        .orElseThrow(() -> new SomethingNotFoundException(...));
```
Returning `null` makes it easy to forget the check, which leads to a `NullPointerException` later.

---

## 10. Building a feature: layers, DTOs, validation, password hashing

The register endpoint (`POST /api/auth/register`) was the first real feature. It uses a pattern
that every later feature will repeat.

### Layered architecture
```
HTTP ──► Controller ──► Service ──► Repository ──► Database
         (web)          (rules)      (data access)
```
| Layer | Class | Responsibility | Must NOT |
|---|---|---|---|
| Controller | `AuthController` | Map URL → method, turn JSON into objects, trigger validation, choose the status code | Contain business rules or talk to repositories |
| Service | `AuthService` | Business rules: normalize input, check duplicates, hash passwords, decide what to save | Know about HTTP details like request objects or headers (*`ResponseStatusException` is a temporary exception to this, replaced in Step 3*) |
| Repository | `UserRepository` | Read and write data | Contain business rules |

**Why separate them?**
- **Single responsibility:** each class has one reason to change.
- **Reuse:** the same service method could be called from a REST controller, a scheduled job, or a test.
- **Testability:** the service can be unit-tested with a fake repository, without HTTP or a database.
- **Transactions** belong on the service, because that's where "one unit of work" is defined.

Package-by-feature still applies: `auth/` holds the controller, service, and request DTO for auth.
`UserResponse` lives in `user/` because it describes a user, and other features will reuse it.

### DTOs (Data Transfer Objects)
A DTO is a simple object whose only job is to carry data across a boundary (here, HTTP ↔ Java).

| DTO | Direction | Purpose |
|---|---|---|
| `RegisterRequest` | client → server | Defines exactly which fields a client **may send** |
| `UserResponse` | server → client | Defines exactly which fields a client **may see** |

**Why not just use the `User` entity in the controller?**
1. **Leaking data:** returning `User` would serialize `passwordHash` into the JSON.
2. **Mass assignment:** accepting `User` as a request body would let a client send
   `"role":"ADMIN"` or `"id":1` and have it bound straight onto the entity.
3. **Coupling:** renaming a database column would silently change your public API, and vice versa.
4. **Lazy-loading issues:** serializing entities with relationships triggers unexpected queries
   or exceptions (especially with `open-in-view: false`).

The mapping lives in a static factory on the DTO:
```java
public record UserResponse(Long id, String email, String fullName, Role role, Instant createdAt) {
    public static UserResponse from(User user) { ... }
}
```

### Java records as DTOs
```java
public record RegisterRequest(String email, String password, String fullName) { }
```
The compiler generates private final fields, a constructor, accessors (`email()`, **not** `getEmail()`),
`equals`, `hashCode`, and `toString`.
- **Immutable:** perfect for data that shouldn't change after it's received.
- Jackson can create records from JSON (via the constructor) and serialize them (via the accessors).
- **Not for JPA entities:** entities need a no-arg constructor and mutable fields.

### Bean Validation (Jakarta Validation)
The rules are declared on the DTO:
```java
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String fullName
) { }
```
They're enforced with `@Valid` on the controller parameter:
```java
public UserResponse register(@Valid @RequestBody RegisterRequest request)
```
| Annotation | Rule |
|---|---|
| `@NotNull` | not `null` (but `""` is OK) |
| `@NotEmpty` | not null and not empty (but `" "` is OK) |
| `@NotBlank` | not null and has at least one non-whitespace character. Use this for strings |
| `@Email` | looks like an email (a loose check) |
| `@Size(min, max)` | string length or collection size |
| `@Min` / `@Max` / `@Positive` | numbers |
| `@Pattern(regexp=...)` | regex match |

**What happens:** Spring MVC converts the JSON, then the validator (Hibernate Validator) checks every
annotation. Any violation → `MethodArgumentNotValidException` → Spring answers **400 Bad Request**
*before your method body runs*.

**Without `@Valid`, the annotations do nothing.** It's the same lesson as `@Configuration`: labels need
something that reads them.

Validation (does the input make sense?) belongs at the boundary. Business rules (is the email already
taken?) belong in the service.

### `@RequestBody` and `@ResponseStatus`
- `@RequestBody` means: read the HTTP body and convert it from JSON (via Jackson) into this parameter.
  A wrong `Content-Type` → 415. Malformed JSON → 400.
- `@ResponseStatus(HttpStatus.CREATED)` replaces the default 200 with **201 Created**, the correct
  status when a new resource is made.
- `ResponseStatusException(HttpStatus.CONFLICT, "...")` lets you throw an exception that turns into a given
  status. **409 Conflict** means "the request clashes with the current state", like a duplicate email.

### Password hashing with BCrypt
**Hashing vs encryption:**
- *Encryption* can be reversed with a key. If the key leaks, every password leaks.
- *Hashing* is one-way. You can't get the password back, you can only check a guess.

**How login will check a password (Step 2b):** hash what the user typed and compare it with the stored hash.
`passwordEncoder.matches(rawPassword, storedHash)`.

**Why BCrypt and not SHA-256?**
| Property | Why it matters |
|---|---|
| **Salted** | A random salt is generated per password and stored inside the hash. The same password gives a **different hash** every time, so precomputed "rainbow tables" are useless and identical passwords can't be spotted. |
| **Deliberately slow** | The *cost factor* (the `10` in `$2a$10$`) means 2¹⁰ rounds. One login check takes about 100 ms, which is fine for users but makes brute-forcing billions of guesses impractical. Raise it as hardware gets faster. |
| **72-byte limit** | BCrypt ignores everything after 72 bytes. That's why `@Size(max = 72)`. |

The hash format, `$2a$10$N9qo8uLOickgx2ZMRZoMye...`, has these parts:
`$2a$` = algorithm version · `10$` = cost · the next 22 chars = salt · the rest = hash.

`PasswordEncoder` is an **interface**. The service depends on the interface, and `SecurityConfig` decides
the implementation (`new BCryptPasswordEncoder()`). You could swap in Argon2 later without touching the service.
That's dependency injection paying off.

### Normalizing input
```java
String email = request.email().trim().toLowerCase(Locale.ROOT);
```
- Without this, `Alice@Example.com` and `alice@example.com` would be two accounts, and logging in with
  different capitalization would fail.
- **`Locale.ROOT`**: case conversion depends on the language. In a Turkish locale, `"I".toLowerCase()` gives
  `ı` (dotless i), so `"ADMIN@X.COM"` → `"admın@x.com"`. `Locale.ROOT` means language-neutral rules.
- Normalize **once, at the service boundary**, and store the normalized form.

### `@Transactional` on service methods
```java
@Transactional
public UserResponse register(RegisterRequest request) { ... }
```
- Spring wraps the bean in a **proxy**. Calling `register` → the proxy starts a transaction → your
  method runs → the proxy **commits**, or **rolls back** if a `RuntimeException` escaped.
- Everything inside (the `existsByEmail` check + `save`) shares one transaction and one persistence context.
- **Proxy gotcha:** calling a `@Transactional` method from *another method of the same class*
  (`this.register(...)`) bypasses the proxy, so there's **no transaction**. It only works when called from outside the bean.
- Checked exceptions **don't** trigger a rollback by default, only unchecked (`RuntimeException`) ones.
- Use `@Transactional(readOnly = true)` for read-only service methods (it allows some optimizations).

### The check-then-act race condition
```java
if (userRepository.existsByEmail(email)) throw ...;   // ① check
userRepository.save(user);                             // ② act
```
Two simultaneous requests for the same email can **both** pass ① before either reaches ②. The
`existsByEmail` check gives a nice 409 in the normal case, but the **database unique constraint** is the
real guarantee: the second insert fails with `DataIntegrityViolationException`. Right now that case surfaces
as a 500. Step 3 (global error handling) will map it to 409 as well.

General rule: **any uniqueness rule must be enforced by the database**. Application checks are only
for friendly error messages.

### The `/error` forward (seen in practice)
When validation fails, Spring Boot **forwards** the request internally to `/error` to build the error
response. That forward passes through the security filter chain again as an anonymous request. If `/error`
isn't in `permitAll`, the 400 is replaced with a **401**. That's why `SecurityConfig` permits `/error`.

### The register flow, end to end
```
POST /api/auth/register {"email":"Alice@Example.com", ...}
 → Security filter chain: /api/auth/** is permitAll → pass
 → DispatcherServlet → AuthController.register
 → Jackson: JSON → RegisterRequest
 → @Valid: rules OK? (no → 400)
 → AuthService.register (proxy opens a transaction)
     normalize email → existsByEmail? (yes → 409)
     BCrypt-hash password → new User → repository.save → INSERT
   (proxy commits)
 → UserResponse.from(user) → Jackson → JSON
 → 201 Created
```

---

## 11. Lombok

Lombok generates boilerplate **at compile time** through an annotation processor. The generated methods exist in
the `.class` file, not in your source.

| Annotation | Generates |
|---|---|
| `@Getter` / `@Setter` | getters/setters for all fields |
| `@NoArgsConstructor` | `public User() {}`, required by JPA |
| `@RequiredArgsConstructor` | a constructor for all `final` fields. Perfect for constructor injection |
| `@AllArgsConstructor` | a constructor for every field |
| `@Builder` | a fluent builder (`User.builder().email("x").build()`) |
| `@Data` | `@Getter` + `@Setter` + `@RequiredArgsConstructor` + `@ToString` + `@EqualsAndHashCode` |

### Why not `@Data` on entities
- **`toString`** includes all fields. Once relationships exist, printing an entity can trigger lazy
  loading (surprise SQL), or loop forever (`Project.toString → Task.toString → Project...`), causing a `StackOverflowError`.
- **`equals`/`hashCode`** over all fields, including the mutable `id`: put a new entity in a `HashSet`, save it
  (the `id` changes from null to 5), and its hash changes, so the set can't find it anymore.
- Only generate what you need: `@Getter`, `@Setter`, `@NoArgsConstructor`.
- `@Data` is fine on simple, non-JPA classes. For DTOs, prefer Java `record`s.

`pom.xml` excludes Lombok from the final JAR (`spring-boot-maven-plugin` → `<excludes>`), because it's only
needed at compile time.

---

## 12. Testing

### The testing pyramid
```
        ▲  few, slow     End-to-end / full app (@SpringBootTest + real HTTP)
       ▲▲▲               Integration / slice tests (@DataJpaTest, @WebMvcTest)
     ▲▲▲▲▲▲▲  many, fast  Unit tests (plain JUnit, no Spring)
```

### Libraries in `spring-boot-starter-test`
| Library | Role |
|---|---|
| **JUnit 5** (Jupiter) | Test framework: `@Test`, lifecycle, assertions |
| **AssertJ** | Readable, chainable assertions: `assertThat(x).isPresent()` |
| **Mockito** | Mocks for unit tests |
| **Spring Test** | Starts Spring contexts in tests, MockMvc |

### `@DataJpaTest`, a slice test
```java
@DataJpaTest
class UserRepositoryTest {
    @Autowired private UserRepository userRepository;
    ...
}
```
- A **slice** starts only part of the application: here JPA (entities, repositories, the
  `EntityManager`). There are **no** controllers, no security, and no services, so it's fast.
- Replaces the configured DataSource with an **embedded in-memory DB (H2)**. Tests don't need
  Docker. (The downside: H2 isn't Postgres, and dialect differences can hide bugs. Testcontainers
  in Step 9 fixes this.)
- Every test method runs in a **transaction that is rolled back** at the end, so tests are isolated
  and don't see each other's data.
- Field injection with `@Autowired` is normal in tests, because the test class is created by JUnit, not by your code.

### Writing tests
- Structure each test as **Given / When / Then** (Arrange / Act / Assert).
- Name tests after the behaviour: `rejectsDuplicateEmail`, not `test3`.
- Test the error paths too, not only the happy path.
- `assertThrows(SomeException.class, () -> code())` checks that the code throws.
- `DataIntegrityViolationException` is Spring's database-independent wrapper for constraint violations.
  Spring translates vendor-specific SQL errors into its `DataAccessException` hierarchy, so your code doesn't
  depend on Postgres-specific exceptions.

### Running tests
- `mvn test` runs everything in `src/test/java` whose class name matches `*Test`/`*Tests`.
- Results go to `target/surefire-reports/` (`.txt` summary + `.xml`).

---

## 13. Java language features used so far

| Feature | Example | Note |
|---|---|---|
| **Annotations** | `@Entity`, `@Bean` | Metadata read by the compiler or by frameworks at runtime |
| **Generics** | `JpaRepository<User, Long>`, `Optional<User>` | Type parameters, checked at compile time |
| **Lambdas** | `csrf -> csrf.disable()` | Short anonymous functions, used by the security DSL |
| **`Optional<T>`** | `findByEmail` | A container that may or may not hold a value |
| **Enums** | `Role { USER, ADMIN }` | A fixed set of constants |
| **`var`** | `var found = repo.findByEmail(...)` | Local type inference (the type is still static) |
| **Static imports** | `import static ...Assertions.assertThat;` | Use static methods without the class prefix |
| **Immutable collections** | `Map.of("status", "UP")` | Can't be modified, and doesn't allow null keys or values |
| **Records** (Step 2a) | `record RegisterRequest(String email, ...)` | Immutable data classes. Ideal for DTOs |
| **Text blocks** (Step 2a) | `""" {...} """` | Multi-line strings |

Naming conventions: classes are `PascalCase` (`HealthController`), methods and fields are `camelCase`,
constants are `UPPER_SNAKE_CASE`, and packages are all lowercase. A **public class's file name must match the class name**.

---

## 14. Debugging playbook

Real problems hit in this project, and how to diagnose them:

| Symptom | Likely cause | How to confirm |
|---|---|---|
| `unknown shorthand flag: 'd'` from `docker compose` | Compose plugin missing | `docker compose version` |
| `failed to connect to the docker API` | Docker Desktop not running | `docker info`, `docker context ls` |
| `cannot find symbol: class X` | Missing import | Check the line/column in `[line,col]` |
| `cannot find symbol: method x` | Typo (watch for **Option+S = `ß`** on a Mac) | Read the method name in the error character by character |
| `package org.junit... does not exist` | Test file in `src/main/java` | Check the file path |
| `annotation ... not applicable to this kind of declaration` | Annotation on the wrong element | Check its `@Target` |
| Error line numbers don't match the editor | File not saved | Turn on VS Code Auto Save |
| Code change has no effect | Stale process still running, or the build failed | `lsof -nP -iTCP:8080 -sTCP:LISTEN` |
| `WWW-Authenticate: Basic` + `JSESSIONID` | Default security config, so your `SecurityFilterChain` isn't loaded | Missing `@Configuration`/`package`? Look for the generated password in the log |
| 404 on an endpoint that "exists" | No method-level mapping, or the path doesn't match exactly | Check `@GetMapping`, trailing slash/dot |
| 403 for an anonymous user | No `AuthenticationEntryPoint` configured | See §7 |
| A 400/409 turns into a 401 (curl only) | `/error` not permitted | Add `/error` to `permitAll` |
| Validation annotations are ignored (bad data accepted) | Missing `@Valid` on the controller parameter | Add `@Valid` before `@RequestBody` |
| 415 Unsupported Media Type | Request missing `Content-Type: application/json` | Add the `-H` header in curl |
| Duplicate registration under load gives 500 | Check-then-act race; the DB constraint fired | Expected until Step 3 maps it to 409 |

General method:
1. **Read the error top-down.** Fix the first one, because later ones are often consequences of it.
2. **Use the evidence:** line numbers, response headers, and log lines tell you *which* code is actually running.
3. **Check your assumptions:** is the file saved? Is the app restarted? Is the right profile active?

---

## 15. Self-test questions

Try answering before opening.

<details><summary>1. What's the difference between a Docker image and a container?</summary>

An image is a read-only template. A container is a running instance of it. You can run many containers from one image.
</details>

<details><summary>2. Why is the Postgres driver declared with <code>runtime</code> scope?</summary>

Your code compiles only against the JDBC interfaces (`java.sql.*`). The Postgres implementation is
only needed when the app runs, so keeping it off the compile classpath stops you from accidentally depending on
driver-specific classes.
</details>

<details><summary>3. What does <code>@SpringBootApplication</code> consist of?</summary>

`@SpringBootConfiguration` (= `@Configuration`) + `@EnableAutoConfiguration` + `@ComponentScan`
(of the class's package and sub-packages).
</details>

<details><summary>4. You define a <code>SecurityFilterChain</code> bean. Why does the "generated security password" disappear?</summary>

Spring Boot's security auto-configuration is conditional on there being no user-defined security
configuration (`@ConditionalOnMissingBean`). Your bean makes it back off.
</details>

<details><summary>5. Why is constructor injection preferred over field injection?</summary>

Dependencies can be `final`, the object is never half-built, dependencies are explicit, and the
class can be unit tested with plain `new` without Spring.
</details>

<details><summary>6. A class with <code>@Bean</code> methods isn't creating any beans. What are two possible causes?</summary>

It's missing `@Configuration`, or it's outside the component-scan base package (e.g. no `package`
declaration).
</details>

<details><summary>7. What's the difference between 401 and 403?</summary>

401 means not authenticated (we don't know who you are). 403 means authenticated but not allowed.
</details>

<details><summary>8. Why did an anonymous request get 403 before the fix?</summary>

The `AuthorizationFilter` threw `AccessDeniedException`. `ExceptionTranslationFilter` saw an anonymous user
and called the `AuthenticationEntryPoint`. No login mechanism had registered one, so the
fallback `Http403ForbiddenEntryPoint` returned 403.
</details>

<details><summary>9. Why is disabling CSRF safe for a JWT-in-header API?</summary>

CSRF works because browsers automatically attach cookies to cross-site requests. A JWT in the
`Authorization` header is never attached automatically, so a malicious site can't make an
authenticated request.
</details>

<details><summary>10. What does <code>SessionCreationPolicy.STATELESS</code> change?</summary>

Spring Security never creates or uses an HTTP session (no `JSESSIONID`). Every request must carry its own
credentials.
</details>

<details><summary>11. Why <code>@Table(name = "users")</code>?</summary>

`user` is a reserved word in PostgreSQL.
</details>

<details><summary>12. What breaks with <code>EnumType.ORDINAL</code>?</summary>

It stores positions. Inserting or reordering constants changes what existing numbers mean, which is silent data
corruption.
</details>

<details><summary>13. What's the persistence context, and what's "dirty checking"?</summary>

The per-transaction set of managed entities Hibernate tracks. Dirty checking: changes to managed
entities are detected and written as `UPDATE`s at flush time, without calling `save()`.
</details>

<details><summary>14. Flush vs commit?</summary>

Flush sends pending SQL to the database inside the transaction (it can still be rolled back). Commit makes
the transaction permanent.
</details>

<details><summary>15. Why shouldn't you use <code>ddl-auto: update</code> in production?</summary>

It guesses, it can't rename or drop, it leaves stale constraints, and it has no history or review. Use versioned
migrations (Flyway).
</details>

<details><summary>16. Where does the <code>UserRepository</code> implementation come from?</summary>

Spring Data JPA generates a proxy at startup and derives queries from method names like
`findByEmail`.
</details>

<details><summary>17. Why not use Lombok <code>@Data</code> on entities?</summary>

The generated `toString` and `equals`/`hashCode` over all fields cause lazy-loading surprises, infinite
recursion with relationships, and broken hash-based collections when the `id` changes.
</details>

<details><summary>18. What does <code>@DataJpaTest</code> give you?</summary>

Only the JPA slice, an embedded H2 database instead of Postgres, and each test wrapped in a transaction
that's rolled back.
</details>

<details><summary>19. With <code>open-in-view: false</code>, what happens if a controller touches a lazy relationship?</summary>

`LazyInitializationException`, because the session is already closed. Load what you need inside a
`@Transactional` service method.
</details>

<details><summary>20. <code>@RequestMapping("/api/health")</code> is on the class, but GET /api/health returns 404. Why?</summary>

A class-level mapping is only a prefix. The method needs `@GetMapping` (or another method mapping)
to become an endpoint.
</details>

<details><summary>21. Why return a <code>UserResponse</code> DTO instead of the <code>User</code> entity?</summary>

So `passwordHash` (and any future internal fields) never leak into the API, the API shape is
independent of the database schema, and lazy relationships aren't accidentally serialized.
</details>

<details><summary>22. Why accept a <code>RegisterRequest</code> instead of a <code>User</code> as the request body?</summary>

To prevent mass assignment. A client could otherwise send `"role":"ADMIN"` or an `id` and have it bound
onto the entity. The DTO lists exactly what may be sent.
</details>

<details><summary>23. You put <code>@NotBlank</code> on a field but empty values are still accepted. Why?</summary>

`@Valid` is missing on the controller parameter, so nothing triggers validation.
</details>

<details><summary>24. Registering the same password twice: are the BCrypt hashes equal?</summary>

No. Each hash has its own random salt, so the results differ. `matches()` reads the salt from the
stored hash to check a password.
</details>

<details><summary>25. What does the <code>10</code> in <code>$2a$10$</code> mean?</summary>

The cost factor: 2¹⁰ hashing rounds. Raising it makes each hash slower, both for you and for an
attacker brute-forcing passwords.
</details>

<details><summary>26. Why <code>toLowerCase(Locale.ROOT)</code> and not just <code>toLowerCase()</code>?</summary>

Without a locale it uses the machine's default language rules. In Turkish, `I` lower-cases to a dotless
`ı`, which would corrupt emails.
</details>

<details><summary>27. <code>existsByEmail</code> already checks for duplicates. Why keep the unique constraint?</summary>

Check-then-act isn't atomic. Two concurrent requests can both pass the check. Only the database
constraint truly guarantees uniqueness.
</details>

<details><summary>28. A <code>@Transactional</code> method is called from another method in the same class. What happens?</summary>

The call bypasses Spring's proxy, so no transaction is started. `@Transactional` only applies when the
method is called through the bean from outside.
</details>

<details><summary>29. Which layer decides "email is already taken", and which decides "email has a valid format"?</summary>

Format: the DTO plus Bean Validation at the controller boundary. Already taken: the service, because it's a business
rule that needs the database.
</details>
