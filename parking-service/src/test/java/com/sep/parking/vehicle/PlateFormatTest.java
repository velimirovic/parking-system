package com.sep.parking.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sep.parking.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class PlateFormatTest {

    @Test
    void normalizesDifferentInputs() {
        assertThat(PlateFormat.parse("bg 123-ab").display()).isEqualTo("BG 123-AB");
        assertThat(PlateFormat.parse("BG123AB").normalized()).isEqualTo("BG123AB");
        assertThat(PlateFormat.parse("ns-4567-č").display()).isEqualTo("NS 4567-Č");
    }

    @Test
    void rejectsInvalidPlate() {
        assertThatThrownBy(() -> PlateFormat.parse("123-ABC")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PlateFormat.parse("")).isInstanceOf(ApiException.class);
    }
}
