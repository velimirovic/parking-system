package com.sep.psp.paymentmethod;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.paymentmethod.PaymentMethodDtos.MerchantMethodsResponse;
import com.sep.psp.paymentmethod.PaymentMethodDtos.ToggleMethodRequest;
import com.sep.psp.paymentmethod.PaymentMethodDtos.UpdateConfigRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/merchant/methods")
@RequiredArgsConstructor
public class MerchantMethodController {

    private final MerchantMethodService service;

    @GetMapping
    public MerchantMethodsResponse list() {
        return service.list();
    }

    @PutMapping("/{code}")
    public MerchantMethodsResponse toggle(@PathVariable String code, @Valid @RequestBody ToggleMethodRequest request) {
        return service.toggle(code, request.active());
    }

    @PutMapping("/{code}/config")
    public MerchantMethodsResponse updateConfig(@PathVariable String code,
                                                @Valid @RequestBody UpdateConfigRequest request) {
        return service.updateConfig(code, request.values());
    }
}
