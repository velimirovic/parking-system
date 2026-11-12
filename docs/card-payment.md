# Plaćanje platnom karticom

Tok odgovara koracima 1–6 iz specifikacije i dijagramima 5.1, 5.2, 5.9, 5.10 i 5.13 iz „SEP Dizajn Sistema".

```
Kupac ─► Parking ─► PSP Core ─► checkout (:4201) ─► card-plugin (:9101) ─► Banka (:8083)
                                                                              │
          stranica banke (:4202/placanje/PAY-…) ◄─────────── PAYMENT_URL ─────┘
          kupac unosi karticu ─► Banka: provera, jedan pokušaj, rezervacija sredstava
          Banka ─► card-plugin (/bank/notifications) ─► PSP Core (/internal/plugins/CARD/results)
          PSP Core ─► webhook klijentu (outbox) ; kupac ─► PSP rezultat ─► SUCCESS/FAILED_URL klijenta
```

## Učesnici i tajne

| Veza | Autentifikacija | Tajna (`.env`) |
|---|---|---|
| Klijent → PSP Core | API ključ + HMAC tela | webhook tajna klijenta (PSP portal) |
| PSP Core ↔ card plugin | HMAC (`X-Psp-Timestamp`, `X-Psp-Signature`) | `PSP_PLUGIN_SHARED_SECRET` |
| card plugin ↔ banka | HMAC (`X-Timestamp`, `X-Signature`) | `BANK_PSP_HMAC_SECRET` |
| PSP Core → klijent (webhook) | HMAC | webhook tajna klijenta |

Svi potpisi koriste isti kanonski oblik: `timestamp \n METHOD \n path \n sha256_hex(body)`. Zahtev stariji
od 5 minuta se odbija.

## Banka

**Zahtev PSP-a (tabela 2)** – `POST /payment-requests`:
`merchant_id` (MERCHANT_ID banke, npr. `BNK-PSP-007781`), `amount`, `currency`, `stan`, `psp_timestamp`,
`result_url` (povratak kupca na PSP) i `notify_url` (gde banka javlja rezultat). Odgovor sadrži `payment_id`,
`payment_url` i `expires_at`. Isti `merchant_id + stan + psp_timestamp` vraća već kreiran zahtev.

**Provera statusa** – `GET /payment-requests/status?merchant_id=…&stan=…&psp_timestamp=…`.

**Forma za plaćanje** (`:4202/placanje/{PAYMENT_ID}`):
- logo-i prihvaćenih kartica (Visa, Mastercard, DinaCard), a brend se prepoznaje iz broja kartice;
- PAN se proverava Lunovom formulom (13–19 cifara), datum je u formatu MM/YY i ne sme biti prošao,
  CVV ima 3 cifre. Sve se proverava i u pregledaču i na serveru;
- iznos dolazi iz zahteva PSP-a i ne može se menjati;
- forma važi 10 minuta i dozvoljava **jedan pokušaj**. Pokušaj se zauzima atomskim `UPDATE`-om, pa dupli klik
  ili drugi tab dobijaju odbijenicu. Neispravan format ne troši pokušaj;
- „Odustani" zatvara formu (kod `17`), a istekle forme se zatvaraju automatski.

**Autorizacija:** kartica se traži po tokenu PAN-a. Zatim se proveravaju datum isteka, ime vlasnika i CVV
(HMAC), pa status kartice. Na kraju se sredstva rezervišu uslovnim `UPDATE`-om
(`balance - reserved >= iznos`), pa paralelna plaćanja sa istog računa ne mogu da odu u minus. Plaćanje u
stranoj valuti preračunava se po kursnoj listi banke.

Kodovi odgovora: `00` odobreno, `14` neispravna kartica, `17` kupac odustao, `51` nedovoljno sredstava,
`54` istekla kartica, `62` blokirana kartica.

**Zaštita podataka kartice (PCI DSS 3):** PAN se čuva samo kao HMAC token (za pretragu) i AES-GCM šifrat,
a prikazuju se poslednje 4 cifre. CVV se nikad ne čuva, već samo HMAC koji izdavalac koristi za proveru.
PSP i parking sistem nikada ne vide podatke kartice.

**Obaveštenje PSP-u (korak 6)** šalje se posle commit-a. Ako ne uspe, banka ga ponavlja sa rastućim
razmakom, a PSP može i sam da proveri status.

## Card plugin

- Pre poziva banke upisuje STAN i PSP_TIMESTAMP. Ako odgovor banke izostane, ponovljen poziv šalje iste
  vrednosti, pa banka ne otvara drugi zahtev.
- Proverava da se STAN, iznos i valuta iz rezultata banke poklapaju sa poslatim. Ako se ne poklapaju, ishod
  je `ERROR`, a ne uspeh.
- Ako banka ne javi rezultat, plugin posle isteka forme sam proverava status kod banke.
- Rezultat prosleđuje Core-u, a ponavlja slanje dok ga Core ne potvrdi.
- Svoju tabelu `card_payment` čuva u šemi `card_plugin` baze PSP-a.

## Test podaci (demo)

| Kartica | Važi do | CVV | Vlasnik | Ishod |
|---|---|---|---|---|
| `4111 1111 1111 1111` (Visa) | 12/29 | 123 | PETAR PETROVIC | odobreno (50.000 RSD na računu) |
| `9891 0000 0000 0011` (DinaCard) | 10/28 | 456 | MILOS NIKOLIC | odobreno (20.000 RSD) |
| `5555 5555 5555 4444` (Mastercard) | 06/28 | 321 | JELENA JOVANOVIC | odbijeno – nedovoljno sredstava (100 RSD) |
| `2221 0000 0000 0009` (Mastercard) | 09/29 | 999 | PETAR PETROVIC | odbijeno – blokirana kartica |
| `4012 8888 8888 1881` (Visa) | 01/25 | 111 | PETAR PETROVIC | forma odbija – kartica je istekla |

Pogrešan CVV, datum ili ime daju kod `14`. Kupci (`petar`, `jelena`, `milos`) imaju lozinku `Kupac-2026!`
za buduću mobilnu aplikaciju banke.

Prodavci u banci: `BNK-PSP-007781` → Maluvufy Parking d.o.o., račun `160-0000000123456-54`;
`BNK-PSP-007782` → Demo Webshop d.o.o., račun `160-0000000654321-05`.

## Proba celog toka

```bash
docker compose up -d --build
python scripts/psp_merchant.py init --amount 350 --description "Parking · Zona 1, 60 min"
```

Otvorite `redirect_url`, izaberite „Platna kartica", unesite test karticu i proverite status:

```bash
python scripts/psp_merchant.py status <merchant_order_id>
```
