package com.sep.parking.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PspSignerTest {

    /** Ista vrednost kao scripts/psp_merchant.py (referentna implementacija potpisa). */
    @Test
    void signatureMatchesReferenceImplementation() {
        String signature = PspSigner.sign("whsec_parking_demo_00000000000000000", "1700000000", "POST",
                "/transactions", "{\"a\":1}");
        assertThat(signature).isEqualTo("d7d5331dfda9d049db0224a675a286076a654b8dff7a65ebfb02657077fe1b20");
        assertThat(PspSigner.matches(signature, signature.toUpperCase())).isTrue();
    }

    @Test
    void jsonEscaping() {
        assertThat(PspClient.json("Parking \"Zona 1\"")).isEqualTo("\"Parking \\\"Zona 1\\\"\"");
    }
}
