# Project Constitution

## Governing Principles

These are the non-negotiable engineering standards that every implementation in this project must follow.

### Architecture

1. **Microservices ownership** — Each service owns its database. No shared databases, no direct cross-service database access.
2. **Asynchronous for state changes** — Services communicate state changes (order placed, stock reserved) via Apache Kafka events, never synchronous calls.
3. **Synchronous for queries only** — gRPC is used exclusively for read operations (e.g., stock availability checks). It must never mutate state in the target service.
4. **Polyglot persistence** — Each service chooses the database best suited to its data model. Relational data uses PostgreSQL; document-oriented data uses MongoDB.

### Concurrency & Data Integrity

5. **Optimistic locking over pessimistic** — Use `@Version`-based optimistic locking for concurrent modifications. Never use database-level locks (SELECT FOR UPDATE) in application code.
6. **No distributed transactions** — Use the Saga pattern (choreography-based) for cross-service workflows. Each service manages its own local transactions only.
7. **Eventual consistency is acceptable** — The system favors availability over immediate consistency. Orders may be in a PENDING state until asynchronous confirmation completes.

### Observability

8. **All services must expose health checks** — Spring Boot Actuator health endpoints enabled on every service.
9. **Custom business metrics** — Critical operations (order creation, rejection, processing time) must have Micrometer counters and timers.
10. **Structured logging** — Use SLF4J with meaningful log messages that include entity IDs for traceability.

### Code Quality

11. **Layered architecture** — Controller (validation + routing) → Service (business logic) → Repository (data access). No business logic in controllers, no HTTP concerns in services.
12. **No unnecessary comments** — Code should be self-documenting. Comments are only allowed for non-obvious workarounds, complex business rules, or important warnings.
13. **Virtual threads** — All services run with `spring.threads.virtual.enabled=true`. Do not use reactive programming (WebFlux) — virtual threads handle concurrency transparently.
14. **Integration tests with real infrastructure** — Use Testcontainers for integration tests. Never mock databases or message brokers in integration tests.

### API Design

15. **RESTful endpoints** — Follow REST conventions: proper HTTP verbs, status codes, and resource-oriented URLs.
16. **Validation at the boundary** — Validate all incoming requests using Bean Validation (`@Valid`, `@NotNull`, `@Positive`). Internal service calls trust the data.
17. **Pagination by default** — All list endpoints must support pagination via Spring Data's `Pageable`.
