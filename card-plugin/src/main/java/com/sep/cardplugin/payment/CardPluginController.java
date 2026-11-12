package com.sep.cardplugin.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sep.cardplugin.common.ApiException;
import com.sep.cardplugin.common.PluginProperties;
import com.sep.cardplugin.common.SignedRequests;
import com.sep.cardplugin.payment.CardPaymentService.BankUnavailableException;
import com.sep.cardplugin.payment.CardPaymentService.UnknownPaymentException;
import com.sep.cardplugin.payment.Dtos.BankStatus;
import com.sep.cardplugin.payment.Dtos.InitiateRequest;
import com.sep.cardplugin.payment.Dtos.InitiateResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Ulazne tacke plugin-a. Oba endpointa su servis-servis i proveravaju HMAC potpis nad sirovim telom.
 */
@RestController
@RequiredArgsConstructor
public class CardPluginController {

    private final CardPaymentService service;
    private final PluginProperties properties;
    private final ObjectMapper objectMapper;

    /** Ugovor plugin-a: PSP Core otvara placanje i dobija adresu na koju salje kupca. */
    @PostMapping(path = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InitiateResponse initiate(@RequestBody String body, HttpServletRequest request) {
        SignedRequests.verify(request, properties.pspSharedSecret(), "X-Psp-Timestamp", "X-Psp-Signature",
                "/payments", body, properties.maxClockSkewSeconds());
        try {
            return service.initiate(parse(body, InitiateRequest.class));
        } catch (BankUnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BANK_UNAVAILABLE", "Banka trenutno nije dostupna");
        }
    }

    /** Banka javlja rezultat placanja (status, STAN, GLOBAL_TRANSACTION_ID, ACQUIRER_TIMESTAMP). */
    @PostMapping(path = "/bank/notifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> bankNotification(@RequestBody String body, HttpServletRequest request) {
        SignedRequests.verify(request, properties.bankHmacSecret(), "X-Timestamp", "X-Signature",
                "/bank/notifications", body, properties.maxClockSkewSeconds());
        try {
            service.onBankStatus(parse(body, BankStatus.class));
        } catch (UnknownPaymentException e) {
            throw ApiException.notFound(e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    private <T> T parse(String body, Class<T> type) {
        try {
            T value = objectMapper.readValue(body, type);
            if (value == null) {
                throw ApiException.badRequest("MALFORMED_REQUEST", "Prazno telo zahteva");
            }
            return value;
        } catch (JacksonException e) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Telo zahteva nije ispravan JSON");
        }
    }
}
