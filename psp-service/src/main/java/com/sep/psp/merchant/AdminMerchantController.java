package com.sep.psp.merchant;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.merchant.MerchantDtos.CreateMerchantRequest;
import com.sep.psp.merchant.MerchantDtos.MerchantCredentials;
import com.sep.psp.merchant.MerchantDtos.MerchantSummary;
import com.sep.psp.merchant.MerchantDtos.UpdateRateLimitRequest;
import com.sep.psp.merchant.MerchantDtos.UpdateStatusRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/merchants")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantAdminService service;

    @GetMapping
    public List<MerchantSummary> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantCredentials create(@Valid @RequestBody CreateMerchantRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/status")
    public MerchantSummary updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @PatchMapping("/{id}/rate-limit")
    public MerchantSummary updateRateLimit(@PathVariable UUID id, @Valid @RequestBody UpdateRateLimitRequest request) {
        return service.updateRateLimit(id, request.rateLimitPerSec());
    }

    @PostMapping("/{id}/reset-access")
    public MerchantCredentials resetAccess(@PathVariable UUID id) {
        return service.resetAccess(id);
    }
}
