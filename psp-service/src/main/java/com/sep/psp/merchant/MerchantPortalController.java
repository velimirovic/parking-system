package com.sep.psp.merchant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.merchant.MerchantDtos.MerchantProfile;
import com.sep.psp.merchant.MerchantDtos.RotatedSecret;
import com.sep.psp.merchant.MerchantDtos.UpdateUrlsRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantPortalController {

    private final MerchantPortalService service;

    @GetMapping("/profile")
    public MerchantProfile profile() {
        return service.profile();
    }

    @PutMapping("/urls")
    public MerchantProfile updateUrls(@Valid @RequestBody UpdateUrlsRequest request) {
        return service.updateUrls(request);
    }

    @PostMapping("/api-key/rotate")
    public RotatedSecret rotateApiKey() {
        return service.rotateApiKey();
    }

    @PostMapping("/webhook-secret/rotate")
    public RotatedSecret rotateWebhookSecret() {
        return service.rotateWebhookSecret();
    }
}
