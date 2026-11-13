package com.sep.bank.paymentrequest;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sep.bank.common.exception.ApiException;
import com.sep.bank.paymentrequest.PspApiDtos.CreatePaymentRequest;
import com.sep.bank.paymentrequest.PspApiDtos.CreatePaymentResponse;
import com.sep.bank.paymentrequest.PspApiDtos.PaymentStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** API banke za PSP (card / QR plugin). Svaki zahtev mora biti potpisan HMAC-om. */
@RestController
@RequestMapping("/payment-requests")
@RequiredArgsConstructor
public class PspApiController {

    private final PspAuthenticator authenticator;
    private final PaymentRequestService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /** Zahtev za PAYMENT_URL i PAYMENT_ID (tabela 2). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePaymentResponse create(@RequestBody String body, HttpServletRequest request) {
        authenticator.authenticate(request, "/payment-requests", body);
        return service.create(parse(body));
    }

    /** Status po MERCHANT_ID + STAN + PSP_TIMESTAMP. */
    @GetMapping("/status")
    public PaymentStatus status(@RequestParam("merchant_id") String merchantId,
                                @RequestParam("stan") String stan,
                                @RequestParam("psp_timestamp") String pspTimestamp,
                                HttpServletRequest request) {
        authenticator.authenticate(request, "/payment-requests/status", "");
        try {
            return service.status(merchantId, stan, Instant.parse(pspTimestamp));
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("INVALID_TIMESTAMP", "psp_timestamp mora biti ISO-8601");
        }
    }

    private CreatePaymentRequest parse(String body) {
        CreatePaymentRequest parsed;
        try {
            parsed = objectMapper.readValue(body, CreatePaymentRequest.class);
        } catch (JacksonException e) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Telo zahteva nije ispravan JSON");
        }
        Set<ConstraintViolation<CreatePaymentRequest>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_FAILED", violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().collect(Collectors.joining("; ")));
        }
        return parsed;
    }
}
