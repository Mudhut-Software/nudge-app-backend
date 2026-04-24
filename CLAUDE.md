# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Spring Boot 3.4.2 on Java 17, written in Kotlin (1.9.25). PostgreSQL + Hibernate/JPA, Spring Security with JWT, Thymeleaf for a handful of server-rendered auth pages, Maven (wrapper included). `spring-dotenv` loads environment from a `.env` file at the project root — see `SET_UP.md` for the full variable list.

## Commands

All commands assume the Maven Wrapper; they work from the repo root.

```bash
./mvnw spring-boot:run                                        # run with default profile (dev)
./mvnw spring-boot:run -Dspring-boot.run.profiles=test        # dev | test | staging | prod
./mvnw clean package                                          # build JAR to target/nudge-0.0.1-SNAPSHOT.jar
./mvnw test                                                   # run all tests (JaCoCo report at target/site/jacoco/)
./mvnw test -Dtest=BusinessControllerTest                     # single test class
./mvnw test -Dtest=BusinessControllerTest#createBusiness      # single test method
./mvnw compile -q                                             # what CI uses as the build step
```

Tests and CI both require a reachable PostgreSQL (`TEST_DB_URL`) — the `test` profile uses `ddl-auto=create-drop` so the schema is recreated per run. CI spins up `postgres:15` and sets `SPRING_PROFILES_ACTIVE=test`; mirror that locally if you're debugging CI failures.

## Profiles and environment

Four profiles in `src/main/resources/application-*.properties` differ mainly by `spring.jpa.hibernate.ddl-auto` (dev: `update`, test: `create-drop`, staging: `validate`, prod: `none`) and by email transport. The `dev` profile is active by default. `EnvConfig` validates `JWT_SECRET` (≥32 chars) and token expiries at startup via `@PostConstruct` — the app will fail to start if `JWT_SECRET` is missing/short.

## Architecture

### Module layout

Feature-first packaging under `com.mudhut.nudge`:

- `users/` — registration, login, JWT, password reset, email/phone verification. Entry point: `UserController` at `/api/v1/auth/**`.
- `businesses/` — businesses, categories, members, invitations, phone numbers. Controllers mount under `/api/v1/businesses`, `/api/v1/categories`, `/api/v1/invitations`, `/api/v1/users/me/businesses`.
- `config/` — `SecurityConfig`, `JwtAuthenticationFilter`, `WebConfig` (CORS + view controllers), `EnvConfig`.
- `email/` — `IEmailService` has two implementations: `JavaEmailService` (SMTP, `@Primary`) and `MailerSendEmailService`. Any change to which sender is used must move the `@Primary` annotation — there is no profile-based selection.
- `utils/exceptions/` — `GlobalExceptionHandler` (`@ControllerAdvice`) is the single place mapping exceptions to `ErrorResponse` + HTTP status. Add new domain exceptions here when introducing them.
- `profiles/` — stub package; `ProfilesController` is an empty class, not a real controller.

### Two independent authorization layers

This is the non-obvious part of the codebase — do not conflate them:

1. **Platform role** (`UserRole`: `SUPER_ADMIN`, `ADMIN`, `SUPPORT`, `BASIC_USER`). `NudgeUserDetailsService` exposes this as a single Spring Security authority (`ROLE_<name>`). Enforced declaratively in `SecurityConfig` — currently only category writes require `SUPER_ADMIN`/`ADMIN`.
2. **Per-business role** (`BusinessRole`: `OWNER` > `ADMIN` > `MANAGER` > `STAFF`). Stored in `BusinessMember`. Enforced programmatically by `BusinessService.requireRole(businessId, userEmail, minimumRole)` at the start of service methods. The hierarchy is a list index comparison — OWNER is index 0 and passes every check.

Most business endpoints are `.authenticated()` in `SecurityConfig` and then call `requireRole` inside the service. Don't try to move this into annotations; the checks are per-resource.

### Principal convention

Controllers receive `Authentication authentication` and call `authentication.name` everywhere — that name is the user's **email**, because `NudgeUserDetailsService` uses email as the UserDetails username. Services then re-resolve the `User` via `userRepository.findByEmail(...)`. If you add a new controller, follow the same pattern; don't pass user IDs from clients.

### Entity relationships

- `Business` — `@ManyToOne` owner (User) and category; `@OneToMany` phone numbers (max 5, enforced in service); `@ElementCollection` serviceAreas (must be non-empty).
- `BusinessMember` — join table (User × Business) with unique constraint on `(user_id, business_id)` and a `BusinessRole`. Owner gets a membership row auto-created with `BusinessRole.OWNER` when the business is created.
- `BusinessInvitation` — token-based; UUID stored in `token`, emailed via `urlService.buildUrlWithParam`. `resolveInvitationsForNewUser` runs during registration so invites sent to a not-yet-registered email auto-link when the user signs up.

### Registration & verification flow

`RegistrationService.createUser` sets `isActive = false`, saves the user, resolves any pending invitations for that email, then creates a verification token and sends an email. The user cannot log in until verified — `LoginService` rejects `!user.isActive` with `IllegalStateException` (→ 403 in the global handler). `AuthTemplatesController` renders a Thymeleaf `verification-result` page after the `/verify-email` GET and builds a "Go to Login" URL from `nudge.frontend-url`.

### Testing pattern

Controller tests use `@WebMvcTest(SomeController::class)` + `@Import(SecurityConfig::class, JwtAuthenticationFilter::class)` and `@MockitoBean` for services. This means security rules are exercised in tests — if you add a new endpoint with auth requirements, expect to provide `@WithMockUser(roles=...)` in tests. Integration-style tests use Mockito for dependencies rather than a real `@SpringBootTest` context.

## Docs

Design and implementation notes for in-flight features live in `docs/plans/` (dated filenames). Check there for the intended shape of a subsystem before restructuring it.
