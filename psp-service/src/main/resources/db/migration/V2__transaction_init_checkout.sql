-- Inicijalizacija transakcije (merchant API) i checkout stranica

-- Opis kupovine koji merchant salje uz nalog (prikazuje se na checkout stranici, npr. "Parking - Zona 1, 60 min")
ALTER TABLE psp_transaction ADD COLUMN description VARCHAR(200);

-- Adresa na koju plugin preusmerava kupca (npr. PAYMENT_URL banke). Cuva se da bi ponovljen izbor
-- metode (dupli klik, refresh) vratio isti URL umesto da se kod banke otvori novi zahtev.
ALTER TABLE psp_transaction ADD COLUMN payment_redirect_url VARCHAR(1000);

-- Pretraga transakcija koje cekaju istek (INIT/PENDING posle expires_at)
CREATE INDEX ix_psp_transaction_open_expiry ON psp_transaction (expires_at)
    WHERE status IN ('INIT', 'PENDING');
