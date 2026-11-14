# Parking sistem — implementacija (`parking-service`, `parking-frontend`)

Parking sistem je prodavac (klijent PSP-a). Korisnik bira zonu, vozilo i trajanje, sistem proverava pravila
zone i računa cenu, kreira parking nalog i inicijalizuje transakciju na PSP-u, a zatim preusmerava korisnika na
checkout PSP-a. Sesija se aktivira **isključivo** posle potvrde uspešnog plaćanja od PSP-a.

## Model podataka

| Tabela | Sadržaj |
|---|---|
| `app_user` | korisnik, BCrypt hash lozinke, brojač neuspešnih prijava i zaključavanje, vreme obaveštenja pre isteka (podrazumevano 5 min) |
| `vehicle` | vozila korisnika; oznaka normalizovana (`BG123AB`) i za prikaz (`BG 123-AB`); „meko” brisanje (`deleted_at`) da vozilo ostane u istoriji |
| `parking_zone` | min/max trajanje, korak trajanja, da li je produženje dozvoljeno, max broj produženja, max ukupno trajanje |
| `zone_tariff` | radno vreme naplate i cena/h po opsegu dana (1 = pon … 7 = ned); dan/vreme bez tarife = nema naplate |
| `parking_location` | lokacije u zoni (broj mesta) |
| `parking_session` | vozilo (i oznaka u trenutku kupovine), zona, lokacija, period, ukupni iznos, valuta, broj produženja, status, `@Version` |
| `payment_transaction` | jedna transakcija prema PSP-u (kupovina ili produženje): `merchant_order_id`, iznos, valuta, status, `psp_transaction_id`, `global_transaction_id`, način plaćanja, `redirect_url`, rok plaćanja, ključ idempotentnosti |
| `session_status_log` | nepromenljiv trag svih promena statusa sesije (ko, kada, zašto, koja transakcija) |

Statusi sesije: `PENDING_PAYMENT → ACTIVE → EXPIRED`, odnosno `PENDING_PAYMENT → CANCELLED`.
Statusi transakcije prate PSP: `INIT, PENDING, SUCCESS, FAILED, ERROR, EXPIRED` (+ `REFUNDED`).

## Pravila zone i obračun cene

Obračun radi server (`SessionRules`, `ZonePricing`), a isti kod koriste i prikaz obračuna i kreiranje naloga:

- trajanje mora biti između min i max trajanja zone i deljivo korakom (15 min);
- **sesija ne sme trajati van radnog vremena naplate** — svaki minut perioda mora pasti u neku tarifu;
  ako se naplata završava ranije, korisnik vidi do kada traje naplata i koliko najduže može da plati;
- vozilo ne sme imati aktivnu sesiju niti plaćanje u toku;
- cena se računa minut po minut po tarifi koja u tom minutu važi (cena/h ÷ 60), zbir se zaokružuje na 2 decimale —
  tačno i kada period prelazi granicu dve tarife (npr. dnevna → noćna).

**Prikazana cena = cena za plaćanje:** frontend uz zahtev šalje iznos koji je korisnik video (`expectedAmount`).
Server ponovo računa cenu i ako se razlikuje (npr. u međuvremenu je počela druga tarifa) vraća `409 PRICE_CHANGED`
sa novom cenom — nalog se ne kreira dok korisnik ne potvrdi novu cenu. PSP mora vratiti isti iznos i valutu,
inače se nalog otkazuje (`AMOUNT_MISMATCH`).

## Tok kreiranja naloga (`POST /sessions`)

1. **DB transakcija:** zaključavanje reda vozila (`SELECT … FOR UPDATE`), provera pravila i cene, upis sesije
   (`PENDING_PAYMENT`), transakcije (`INIT`, novi `MERCHANT_ORDER_ID` oblika `PRK-20261114-7F3A92`) i zapisa u trag.
2. **Poziv PSP-a van DB transakcije:** `POST /transactions` potpisan HMAC-SHA256 (`X-Merchant-Id`, `X-Api-Key`,
   `X-Timestamp`, `X-Signature`), telo se gradi bajt po bajt kako bi potpis bio nad tačno poslatim podacima.
3. Upis `psp_transaction_id`, `redirect_url` i roka plaćanja; frontend preusmerava korisnika na checkout PSP-a.

Otpornost:

| Situacija | Ponašanje |
|---|---|
| Dvostruki klik / dva taba | isti `requestId` vraća isti nalog (jedinstven `(user_id, idempotency_key)`); zaključavanje vozila serijalizuje paralelne zahteve |
| PSP nije dostupan / timeout | nalog ostaje `INIT`, korisnik dobija `503 PSP_UNAVAILABLE`; „Pokušaj ponovo” šalje isti `requestId`, a PSP je idempotentan po `merchant_order_id` |
| PSP odbije zahtev (4xx) | transakcija `ERROR`, sesija `CANCELLED` sa razlogom u tragu |
| Korisnik zatvori tab na PSP-u | nalog blokira vozilo samo do isteka roka plaćanja (15 min), posle toga vozilo je slobodno |
| Aktivna sesija istekne | posao `SessionExpiryJob` (svakih 30 s) prebacuje je u `EXPIRED` uz optimističko zaključavanje |

## API

Sve osim prijave, registracije, pregleda zona i health-a zahteva `Authorization: Bearer <JWT>`.
Greške su RFC 9457 `ProblemDetail` sa poljima `code` i `detail` (i `fields` za validaciju).

| Metoda i putanja | Opis |
|---|---|
| `POST /auth/register` | registracija (lozinka: 12+ znakova, veliko i malo slovo, broj, simbol) |
| `POST /auth/login` | prijava; posle 5 neuspešnih pokušaja nalog se zaključava na 15 min (`423 ACCOUNT_LOCKED`) |
| `GET /auth/me`, `PUT /auth/me/preferences` | podaci korisnika; vreme obaveštenja pre isteka |
| `GET /zones`, `GET /zones/{id}` | zone sa cenovnikom, pravilima, lokacijama i da li se trenutno naplaćuje |
| `GET/POST /vehicles`, `PUT/DELETE /vehicles/{id}` | vozila (brisanje nije moguće dok vozilo ima sesiju ili plaćanje u toku) |
| `POST /sessions/quote` | obračun i provera pravila (ništa ne kreira) |
| `POST /sessions` | kreiranje naloga + inicijalizacija transakcije na PSP-u, vraća `redirectUrl` |
| `GET /sessions/current` | aktivne sesije i nalozi koji čekaju plaćanje |
| `GET /sessions/{id}` | sesija sa transakcijama i tragom statusa |
| `GET /sessions/orders/{merchantOrderId}` | stanje naloga za stranicu statusa plaćanja |
| `POST /sessions/orders/{merchantOrderId}/sync` | odmah proverava konačan status naloga na PSP-u i vraća ažurno stanje |
| `POST /psp/webhook` | potvrda plaćanja od PSP-a (javno, HMAC potpis) |
| `GET /history?month=2026-11&vehicleId=&status=&page=&size=` | istorija sa zbirnim podacima |
| `GET /history/payments?limit=5` | poslednja plaćanja |

## Frontend (dizajn P01–P08)

| Ruta | Ekran |
|---|---|
| `/prijava` | P01 prijava i registracija |
| `/pregled` | P02 aktivne sesije sa odbrojavanjem, nalozi koji čekaju plaćanje, poslednja plaćanja |
| `/zone` | P03 zone, cenovnik, radno vreme naplate, pravila, lokacije |
| `/nova-sesija` | P04 izbor zone/lokacije, vozila i trajanja, obračun uživo, prelazak na plaćanje |
| `/istorija` | P05 istorija sa transakcijama i tragom promena statusa |
| `/vozila` | P06 vozila i vreme obaveštenja |
| `/placanje/uspeh`, `/placanje/neuspeh`, `/placanje/greska` | P08 povratak sa PSP-a; prikaz zavisi samo od stanja naloga u parking sistemu, a ne od URL-a |

## Aktiviranje sesije

Potvrda plaćanja (webhook PSP-a i provera statusa), pravila aktivacije, refundacija i dijagram sekvence su
opisani u [session-activation.md](session-activation.md).
