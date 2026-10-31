# SEP — Parking sistem i Payment Service Provider

Timski projekat iz predmeta Sistemi elektronskog plaćanja (MAS E2/R2, 2026/2027).

Sistem čine tri nezavisna sistema. Svaki ima svoj backend, frontend i bazu:

| Sistem | Backend | Frontend | Baza |
|---|---|---|---|
| Parking sistem (prodavac) | `parking-service` · :8081 | `parking-frontend` · :4200 | `parking-db` · :5433 |
| Payment Service Provider | `psp-service` · :8082 | `psp-frontend` · :4201 | `psp-db` · :5434 |
| Banka (Acquirer / Issuer) | `bank-service` · :8083 | `bank-frontend` · :4202 | `bank-db` · :5435 |


## Tehnologije

- Java 17, Spring Boot 4.1, Maven (wrapper `mvnw`, Maven ne mora da bude instaliran)
- Angular 22, TypeScript, SCSS, Vitest
- PostgreSQL 17, Flyway migracije
- RabbitMQ 4
- Docker + Docker Compose

