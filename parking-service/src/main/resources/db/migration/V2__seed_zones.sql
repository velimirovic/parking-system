-- Zone, cenovnici i lokacije (Beograd) - podaci sa dizajna P03/P04.
-- Zona 3 ima naplatu 0-24h svakog dana da bi se sistem mogao testirati i van radnog vremena.

INSERT INTO parking_zone (code, name, area, description, latitude, longitude, currency,
                          min_duration_minutes, max_duration_minutes, duration_step_minutes,
                          extension_allowed, max_extensions, max_total_minutes, sort_order)
VALUES ('Z0', 'Zona 0 — Ekstra', 'Trg Republike, Knez Mihailova',
        'Najstrože regulisana zona u centru grada. Produženje nije dozvoljeno.',
        44.816500, 20.460100, 'RSD', 15, 120, 15, FALSE, 0, 120, 0),
       ('Z1', 'Zona 1 — Crvena', 'Stari grad, Savski venac',
        'Centralna zona. Produženje je moguće dok je sesija aktivna i ukupno trajanje ne pređe 180 min.',
        44.817600, 20.456900, 'RSD', 15, 120, 15, TRUE, 2, 180, 1),
       ('Z2', 'Zona 2 — Žuta', 'Vračar, Palilula',
        'Šira gradska zona sa dužim dozvoljenim trajanjem.',
        44.798200, 20.476700, 'RSD', 15, 180, 15, TRUE, 3, 360, 2),
       ('Z3', 'Zona 3 — Zelena', 'Novi Beograd, Zvezdara',
        'Rubna zona, naplata svakog dana. Broj produženja nije ograničen.',
        44.810300, 20.403900, 'RSD', 15, 240, 15, TRUE, NULL, NULL, 3);

-- Zona 0: pon-sub 07-21
INSERT INTO zone_tariff (zone_id, label, day_from, day_to, start_minute, end_minute, price_per_hour)
SELECT id, 'Pon – sub', 1, 6, 420, 1260, 150.00 FROM parking_zone WHERE code = 'Z0';

-- Zona 1: pon-pet 07-21 (120), subota 07-14 (90), nedelja bez naplate
INSERT INTO zone_tariff (zone_id, label, day_from, day_to, start_minute, end_minute, price_per_hour)
SELECT id, 'Pon – pet', 1, 5, 420, 1260, 120.00 FROM parking_zone WHERE code = 'Z1';
INSERT INTO zone_tariff (zone_id, label, day_from, day_to, start_minute, end_minute, price_per_hour)
SELECT id, 'Subota', 6, 6, 420, 840, 90.00 FROM parking_zone WHERE code = 'Z1';

-- Zona 2: pon-pet 07-21 (90), subota 07-14 (70)
INSERT INTO zone_tariff (zone_id, label, day_from, day_to, start_minute, end_minute, price_per_hour)
SELECT id, 'Pon – pet', 1, 5, 420, 1260, 90.00 FROM parking_zone WHERE code = 'Z2';
INSERT INTO zone_tariff (zone_id, label, day_from, day_to, start_minute, end_minute, price_per_hour)
SELECT id, 'Subota', 6, 6, 420, 840, 70.00 FROM parking_zone WHERE code = 'Z2';

-- Zona 3: dnevna i nocna tarifa svakog dana
INSERT INTO zone_tariff (zone_id, label, day_from, day_to, start_minute, end_minute, price_per_hour)
SELECT id, 'Pon – ned, dan', 1, 7, 420, 1260, 60.00 FROM parking_zone WHERE code = 'Z3';
INSERT INTO zone_tariff (zone_id, label, day_from, day_to, start_minute, end_minute, price_per_hour)
SELECT id, 'Pon – ned, noć', 1, 7, 1260, 1440, 40.00 FROM parking_zone WHERE code = 'Z3';
INSERT INTO zone_tariff (zone_id, label, day_from, day_to, start_minute, end_minute, price_per_hour)
SELECT id, 'Pon – ned, noć', 1, 7, 0, 420, 40.00 FROM parking_zone WHERE code = 'Z3';

INSERT INTO parking_location (zone_id, name, address, capacity)
SELECT z.id, l.name, l.address, l.capacity
FROM parking_zone z
JOIN (VALUES ('Z0', 'Trg Republike', 'Trg Republike bb', 40),
             ('Z0', 'Knez Mihailova', 'Knez Mihailova 2', 28),
             ('Z1', 'Kosančićev venac', 'Kosančićev venac 1', 64),
             ('Z1', 'Kralja Petra', 'Kralja Petra 12', 38),
             ('Z1', 'Gospodar Jevremova', 'Gospodar Jevremova 20', 52),
             ('Z2', 'Kalenić pijaca', 'Njegoševa 54', 70),
             ('Z2', 'Bulevar kralja Aleksandra', 'Bulevar kralja Aleksandra 73', 85),
             ('Z2', 'Tašmajdan', 'Ilije Garašanina 26', 60),
             ('Z3', 'Ušće', 'Bulevar Mihajla Pupina 4', 210),
             ('Z3', 'Arena', 'Bulevar Arsenija Čarnojevića 58', 160),
             ('Z3', 'Zvezdara — Bulevar', 'Bulevar kralja Aleksandra 240', 90)
     ) AS l (zone_code, name, address, capacity) ON l.zone_code = z.code;
