package com.sep.psp.checkout;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.checkout.CheckoutDtos.CheckoutView;
import com.sep.psp.checkout.CheckoutDtos.SelectMethodRequest;
import com.sep.psp.checkout.CheckoutDtos.SelectMethodResponse;
import com.sep.psp.common.exception.ApiException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Javni endpointi checkout stranice (S01 na dizajnu). Kupac nije korisnik PSP-a, pa nema prijave -
 * pristup daje samo nasumicni UUID transakcije iz redirect-a klijenta.
 */
@RestController
@RequestMapping("/checkout/{id}")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService service;

    @GetMapping
    public CheckoutView view(@PathVariable String id) {
        return service.view(parse(id));
    }

    @PostMapping("/method")
    public SelectMethodResponse selectMethod(@PathVariable String id, @Valid @RequestBody SelectMethodRequest request) {
        return new SelectMethodResponse(service.selectMethod(parse(id), request.code()));
    }

    @PostMapping("/cancel")
    public CheckoutView cancel(@PathVariable String id) {
        return service.cancel(parse(id));
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Plaćanje ne postoji");
        }
    }
}
