package com.sep.bank.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sep.bank.common.exception.ApiException;
import com.sep.bank.payment.PaymentDtos.CardPaymentForm;
import com.sep.bank.payment.PaymentDtos.PaymentFormView;
import com.sep.bank.payment.PaymentDtos.PaymentResult;

import lombok.RequiredArgsConstructor;

/**
 * Javni endpointi stranice banke (PAYMENT_URL). Pristup daje nasumicni PAYMENT_ID, a forma je
 * vezana za tacno jednu transakciju.
 */
@RestController
@RequestMapping("/payments/{paymentId}")
@RequiredArgsConstructor
public class PaymentController {

    private final CardPaymentService service;

    @GetMapping
    public PaymentFormView view(@PathVariable String paymentId) {
        return service.view(checked(paymentId));
    }

    @PostMapping
    public PaymentResult pay(@PathVariable String paymentId, @RequestBody CardPaymentForm form) {
        return service.pay(checked(paymentId), form);
    }

    @PostMapping("/cancel")
    public PaymentResult cancel(@PathVariable String paymentId) {
        return service.cancel(checked(paymentId));
    }

    private static String checked(String paymentId) {
        if (paymentId == null || !paymentId.matches("PAY(-[A-Z0-9]{4}){4}")) {
            throw ApiException.notFound("Plaćanje ne postoji");
        }
        return paymentId;
    }
}
