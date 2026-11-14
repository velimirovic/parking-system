# SEP — Parking sistem i Payment Service Provider

Timski projekat iz predmeta Sistemi elektronskog plaćanja (MAS E2/R2, 2026/2027).

Sistem čine tri nezavisna sistema. Svaki ima svoj backend, frontend i bazu:

| Sistem | Backend | Frontend | Baza |
|---|---|---|---|
| Parking sistem (prodavac) | `parking-service` · :8081 | `parking-frontend` · :4200 | `parking-db` · :5433 |
| Payment Service Provider | `psp-service` · :8082 | `psp-frontend` · :4201 | `psp-db` · :5434 |
| Banka (Acquirer / Issuer) | `bank-service` · :8083 | `bank-frontend` · :4202 | `bank-db` · :5435 |

PSP metode plaćanja su zasebni plugin servisi: `card-plugin` · :9101 (šema `card_plugin` u `psp-db`).


## Tehnologije

- Java 17, Spring Boot 4.1, Maven (wrapper `mvnw`, Maven ne mora da bude instaliran)
- Angular 22, TypeScript, SCSS, Vitest
- PostgreSQL 17, Flyway migracije
- RabbitMQ 4
- Docker + Docker Compose


## Pokretanje

Potrebno: Docker Desktop (pokrenut).

```bash
cp .env.example .env          # samo prvi put
docker compose up -d --build  # ceo sistem
```

| Šta | Adresa |
|---|---|
| Parking aplikacija | http://localhost:4200 |
| PSP portal (klijenti i super admin) | http://localhost:4201 |
| Banka | http://localhost:4202 |
| RabbitMQ UI | http://localhost:15672 |

Korisne komande:

```bash
docker compose up -d --build psp-service   # ponovo izgradi samo jedan servis posle izmene koda
docker compose logs -f psp-service         # logovi servisa
docker compose down                        # zaustavi sve (podaci ostaju)
docker compose down -v                     # zaustavi i obriši baze (kreće od nule)
```

Za razvoj iz IDE-a pokrenuti samo bazu (i RabbitMQ za PSP), a servis i frontend lokalno:

```bash
docker compose up -d psp-db rabbitmq
cd psp-service && ./mvnw spring-boot:run   # backend se kači na localhost:5434
cd psp-frontend && npm start               # http://localhost:4201, /api ide na :8082
```

### Parking aplikacija

Korisnik se registruje na http://localhost:4200 (Registracija), dodaje vozilo i pokreće sesiju.
Zone, cenovnici i lokacije se upisuju Flyway migracijom (`V2__seed_zones.sql`); Zona 3 ima naplatu 0–24h
svakog dana, pa se tok može testirati i van radnog vremena ostalih zona.

`parking-service` je klijent PSP-a sa demo nalogom `MRC-00042` (vidi ispod). U PSP portalu, za tog klijenta
(Metode i integracija), povratni URL-ovi treba da budu:

| Parametar | Vrednost |
|---|---|
| `SUCCESS_URL` | `http://localhost:4200/placanje/uspeh` |
| `FAILED_URL` | `http://localhost:4200/placanje/neuspeh` |
| `ERROR_URL` | `http://localhost:4200/placanje/greska` |

API, pravila zona i tok kreiranja naloga su opisani u [docs/parking-system.md](docs/parking-system.md).

### PSP portal - prva prijava

Pri prvom pokretanju nad praznom bazom PSP kreira super admina i demo klijente sa transakcijama
(`PSP_DEMO_DATA_ENABLED=false` u `.env` isključuje demo podatke). Lozinke su privremene: pri prvoj
prijavi se postavlja nova lozinka i skenira QR kod u Google ili Microsoft Authenticator aplikaciji.

| Nalog | Privremena lozinka |
|---|---|
| `superadmin@sep.rs` | `SuperAdmin-2026!` |
| `ana.jovanovic@maluvufy.rs` (Maluvufy Parking d.o.o., MRC-00042) | `Demo-Klijent-2026!` |
| `marko.petrovic@webshop.sep.rs` (Demo Webshop, MRC-00043) | `Demo-Klijent-2026!` |

Demo parking klijent ima fiksni API ključ `sk_test_parking_demo_0000000000000000` i webhook tajnu
`whsec_parking_demo_00000000000000000` (za konfiguraciju `parking-service`-a).

Tajne (`PSP_JWT_SECRET`, `PSP_ENCRYPTION_KEY`, `PSP_SUPER_ADMIN_PASSWORD`, `PSP_PLUGIN_SHARED_SECRET`) imaju razvojne vrednosti u
`docker-compose.yml`; van lokalnog razvoja obavezno ih postaviti u `.env`.

### PSP - integracija klijenta

Inicijalizacija transakcije, provera statusa, checkout kupca, webhook i ugovor sa plugin-ovima su opisani u
[docs/psp-merchant-api.md](docs/psp-merchant-api.md). Za probu bez `parking-service`-a:

```bash
python scripts/psp_merchant.py init --amount 120 --currency RSD
```

Otvoriti `redirect_url` iz odgovora (checkout na http://localhost:4201/placanje/{id}).

### Plaćanje karticom

Tok, kodovi odgovora banke i test kartice su opisani u [docs/card-payment.md](docs/card-payment.md).
Najkraća proba: `python scripts/psp_merchant.py init --amount 350`, otvoriti `redirect_url`, izabrati
„Platna kartica" i uneti `4111 1111 1111 1111`, `12/29`, CVV `123`, ime `PETAR PETROVIC`.
