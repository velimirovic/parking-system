package com.sep.psp.merchantapi;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.common.exception.ApiException;
import com.sep.psp.merchant.Merchant;
import com.sep.psp.merchantapi.MerchantApiDtos.InitTransactionRequest;
import com.sep.psp.merchantapi.MerchantApiDtos.TransactionResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Merchant API - koriste ga klijenti PSP-a (parking-service, webshop...) servis-servis pozivima.
 * Telo se cita kao sirov tekst jer se HMAC potpis racuna nad tacnim bajtovima koje je klijent poslao.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class MerchantApiController {

    private static final int MAX_BODY_LENGTH = 8_192;

    private final MerchantApiAuthenticator authenticator;
    private final TransactionInitService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /** Inicijalizacija transakcije: 201 za novu, 200 za vec postojeci isti nalog (idempotentno). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionResponse> initiate(@RequestBody String body, HttpServletRequest request) {
        if (body.length() > MAX_BODY_LENGTH) {
            throw ApiException.badRequest("BODY_TOO_LARGE", "Telo zahteva je preveliko");
        }
        Merchant merchant = authenticator.authenticate(request, "/transactions", body);
        TransactionInitService.InitResult result = service.initiate(merchant, parse(body));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.transaction());
    }

    /** Status transakcije po MERCHANT_ORDER_ID - klijent vidi samo svoje transakcije. */
    @GetMapping("/{merchantOrderId}")
    public TransactionResponse status(@PathVariable String merchantOrderId, HttpServletRequest request) {
        Merchant merchant = authenticator.authenticate(request, "/transactions/" + merchantOrderId, "");
        return service.status(merchant, merchantOrderId);
    }

    private InitTransactionRequest parse(String body) {
        InitTransactionRequest parsed;
        try {
            parsed = objectMapper.readValue(body, InitTransactionRequest.class);
        } catch (JacksonException e) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Telo zahteva nije ispravan JSON nalog");
        }
        if (parsed == null) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Telo zahteva je prazno");
        }
        Set<ConstraintViolation<InitTransactionRequest>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_FAILED", violations.stream()
                    .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; ")));
        }
        return parsed;
    }
}
