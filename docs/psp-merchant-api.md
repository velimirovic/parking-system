# PSP — API za klijente (parking sistem, webshop…)

Ovaj dokument opisuje kako se klijent PSP-a (npr. `parking-service`) povezuje sa PSP-om: inicijalizacija
transakcije, provera statusa i preusmeravanje kupca. Odgovara koraku 1 iz specifikacije i dijagramu 5.1
iz dokumenta „SEP Dizajn Sistema".

## Tok

1. Klijent šalje `POST /transactions` (potpisan zahtev). PSP kreira transakciju u statusu `INIT`.
2. Klijent preusmerava kupca na `redirect_url` iz odgovora (checkout stranica PSP-a, `:4201/placanje/{id}`).
3. Kupac bira jednu od metoda na koje je klijent pretplaćen. PSP poziva plugin te metode i šalje kupca
   dalje (npr. na stranicu banke). Transakcija prelazi u `PENDING`.
4. Kada plaćanje završi, PSP upisuje konačan status (`SUCCESS`, `FAILED`, `ERROR`, `EXPIRED`), šalje
   webhook klijentu i vraća kupca na `SUCCESS_URL`, `FAILED_URL` ili `ERROR_URL` klijenta.
5. Klijent u svakom trenutku može da proveri status sa `GET /transactions/{merchant_order_id}`.

`SUCCESS_URL`, `FAILED_URL`, `ERROR_URL` i webhook URL se **ne šalju uz zahtev**: podešavaju se u PSP
portalu (Metode i integracija) i čuvaju se kod klijenta u PSP-u. Pri povratku kupca PSP dodaje
`?merchant_order_id=…&status=…`, samo za prikaz. Sesija se aktivira **isključivo** na osnovu webhook-a
ili provere statusa, a ne na osnovu ovog redirect-a.

## Autentifikacija (svaki zahtev)

| Zaglavlje | Vrednost |
|---|---|
| `X-Merchant-Id` | MERCHANT_ID klijenta, npr. `MRC-00042` |
| `X-Api-Key` | API ključ klijenta (PSP čuva samo njegov hash) |
| `X-Timestamp` | Unix vreme u sekundama; odstupanje veće od 5 min se odbija |
| `X-Signature` | `hex(HMAC-SHA256(tajna, timestamp + "\n" + METHOD + "\n" + path + "\n" + sha256_hex(body)))` |

- `tajna` je **webhook tajna** klijenta (ista kojom PSP potpisuje webhook-ove ka klijentu).
- `path` je putanja PSP API-ja bez query stringa i bez proxy prefiksa, npr. `/transactions` ili
  `/transactions/PRK-20261112-AB12CD`.
- `body` je tačno telo koje se šalje (bajt po bajt); za GET je prazan string.
- Svaka greška u autentifikaciji vraća `401` sa istom porukom i beleži se u audit log.

Demo parking klijent (iz demo podataka PSP-a):

```
MERCHANT_ID   MRC-00042
API ključ     sk_test_parking_demo_0000000000000000
Tajna         whsec_parking_demo_00000000000000000
```

## `POST /transactions` — inicijalizacija

```json
{
  "merchant_order_id": "PRK-20261112-AB12CD",
  "merchant_timestamp": "2026-11-12T20:15:00.000Z",
  "amount": "120.00",
  "currency": "RSD",
  "description": "Parking · Zona 1, 60 min"
}
```

| Polje | Pravilo |
|---|---|
| `merchant_order_id` | 1–64 znaka `[A-Za-z0-9_-]`, jedinstven po klijentu |
| `merchant_timestamp` | ISO-8601, ne sme biti u budućnosti |
| `amount` | > 0, najviše 2 decimale |
| `currency` | ISO 4217 (RSD, EUR, USD…) |
| `description` | opciono, do 200 znakova (prikazuje se kupcu) |

Odgovor `201 Created` (nova transakcija) ili `200 OK` (isti nalog je već poslat):

```json
{
  "psp_transaction_id": "7c0f…",
  "merchant_order_id": "PRK-20261112-AB12CD",
  "status": "INIT",
  "amount": "120.00",
  "currency": "RSD",
  "payment_method": null,
  "redirect_url": "http://localhost:4201/placanje/7c0f…",
  "expires_at": "2026-11-12T20:30:00Z",
  "global_transaction_id": null,
  "acquirer_timestamp": null,
  "created_at": "2026-11-12T20:15:00.123Z"
}
```

**Idempotentnost (dupli klik, ponovljen zahtev posle timeout-a):** isti `merchant_order_id` sa istim iznosom i
valutom vraća postojeću transakciju. Sa drugačijim iznosom ili valutom vraća `409 ORDER_ID_CONFLICT`. Novi
pokušaj posle neuspeha mora imati novi `merchant_order_id`.

Ostale greške: `400 VALIDATION_FAILED / INVALID_CURRENCY / INVALID_AMOUNT / MALFORMED_REQUEST`,
`403 MERCHANT_INACTIVE`, `422 NO_PAYMENT_METHODS`.

## `GET /transactions/{merchant_order_id}` — status (pull)

Isti oblik odgovora. `redirect_url` je popunjen samo dok je transakcija otvorena (`INIT`/`PENDING`).
Klijent ovo koristi za reconciliation (specijalni slučajevi 2, 3 i 5).

## Webhook PSP → klijent

`POST {webhook_url}` sa zaglavljima `X-Psp-Timestamp` i `X-Psp-Signature`. Potpis se računa istom formulom
kao gore, tajnom klijenta, gde je `path` putanja webhook URL-a. Telo:

```json
{
  "event": "PAYMENT_SUCCESS",
  "psp_transaction_id": "7c0f…",
  "merchant_order_id": "PRK-20261112-AB12CD",
  "status": "SUCCESS",
  "amount": "120.00",
  "currency": "RSD",
  "payment_method": "CARD",
  "global_transaction_id": "…",
  "acquirer_timestamp": "…",
  "occurred_at": "…"
}
```

`event`: `PAYMENT_SUCCESS`, `PAYMENT_FAILED` (i za `EXPIRED`), `PAYMENT_ERROR`. Klijent mora da proveri potpis,
iznos i valutu, da obradu uradi idempotentno i da odgovori `2xx`. Zapis za webhook (outbox) PSP upisuje u istoj
DB transakciji kao i status, pa se obaveštenje ne gubi. **Slanje outbox zapisa dolazi uz plaćanje karticom.**

## Testiranje bez parking-service-a

```bash
python scripts/psp_merchant.py init --amount 120 --currency RSD --description "Parking · Zona 1, 60 min"
python scripts/psp_merchant.py status PRK-20261112-AB12CD
```

Otvorite `redirect_url` iz odgovora u pregledaču.

## Ugovor PSP Core ↔ plugin (za vlasnike metoda plaćanja)

Core poziva `POST {plugin_base_url}/payments` sa zaglavljima `X-Psp-Timestamp` i `X-Psp-Signature`
(ista formula, deljena tajna `PLUGIN_SHARED_SECRET`, `path` = `/payments`):

```json
{
  "pspTransactionId": "7c0f…",
  "merchantId": "MRC-00042",
  "amount": 120.00,
  "currency": "RSD",
  "merchantConfig": { "bankMerchantId": "BNK-PSP-007781" },
  "returnUrl": "http://localhost:4201/placanje/7c0f…/rezultat",
  "expiresAt": "2026-11-12T20:30:00Z"
}
```

Plugin vraća `{"status": "PENDING", "redirectUrl": "…"}`, a kupca na kraju vraća na `returnUrl`.
Ako je odbijen, vraća `{"status": "FAILED", "message": "…"}`. Ako plugin nije dostupan ili odbije zahtev,
transakcija ostaje `INIT` i kupac može da izabere drugu metodu.
