package com.sep.psp.paymentmethod;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.merchant.MerchantRepository;
import com.sep.psp.merchant.MerchantStatus;
import com.sep.psp.paymentmethod.PaymentMethodDtos.PluginView;
import com.sep.psp.paymentmethod.PaymentMethodDtos.RegisterPluginRequest;
import com.sep.psp.paymentmethod.PaymentMethodDtos.SystemStatus;
import com.sep.psp.paymentmethod.PaymentMethodDtos.UpdatePluginRequest;
import com.sep.psp.webhook.WebhookDeliveryRepository;
import com.sep.psp.webhook.WebhookStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPaymentMethodController {

    private final PaymentMethodAdminService service;
    private final MerchantRepository merchantRepository;
    private final WebhookDeliveryRepository webhookRepository;
    private final DataSource dataSource;

    @GetMapping("/payment-methods")
    public List<PluginView> list() {
        return service.list();
    }

    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    public PluginView register(@Valid @RequestBody RegisterPluginRequest request) {
        return service.register(request);
    }

    @PatchMapping("/payment-methods/{id}")
    public PluginView update(@PathVariable UUID id, @Valid @RequestBody UpdatePluginRequest request) {
        return service.update(id, request);
    }

    /** Stanje instance koja je obradila zahtev (iza load balancer-a svaka instanca vraca svoje ime). */
    @GetMapping("/system")
    public SystemStatus system() {
        String database;
        try (var connection = dataSource.getConnection()) {
            database = connection.isValid(1) ? "UP" : "DOWN";
        } catch (Exception e) {
            database = "DOWN";
        }
        long active = merchantRepository.findAll().stream().filter(m -> m.getStatus() == MerchantStatus.ACTIVE).count();
        return new SystemStatus(instanceId(),
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()),
                database, webhookRepository.countByStatus(WebhookStatus.PENDING), active, merchantRepository.count());
    }

    private static String instanceId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "psp-core";
        }
    }
}
