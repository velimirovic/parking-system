package com.sep.psp.paymentmethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.paymentmethod.PaymentMethodDtos.PluginView;
import com.sep.psp.paymentmethod.PaymentMethodDtos.RegisterPluginRequest;
import com.sep.psp.paymentmethod.PaymentMethodDtos.UpdatePluginRequest;

import lombok.RequiredArgsConstructor;

/**
 * Registar plugin-ova (super admin). Nova metoda placanja se dodaje upisom u PAYMENT_METHOD
 * u toku rada - bez izmene koda i bez gasenja PSP Core-a.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentMethodAdminService {

    private final PaymentMethodRepository repository;
    private final MerchantPaymentMethodRepository merchantMethodRepository;
    private final PluginHealthChecker healthChecker;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PluginView> list() {
        List<PaymentMethod> methods = repository.findAllByOrderByCreatedAtAscCodeAsc();
        Map<UUID, Long> subscribers = merchantMethodRepository.findAll().stream()
                .filter(MerchantPaymentMethod::isActive)
                .collect(Collectors.groupingBy(mpm -> mpm.getPaymentMethod().getId(), Collectors.counting()));
        Map<String, PluginHealthChecker.Health> health = healthChecker.check(methods);
        return methods.stream()
                .map(m -> toView(m, subscribers.getOrDefault(m.getId(), 0L), health.get(m.getCode())))
                .toList();
    }

    public PluginView register(RegisterPluginRequest request) {
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCode(code)) {
            throw ApiException.conflict("PLUGIN_EXISTS", "Metoda sa kodom " + code + " je već registrovana");
        }
        PaymentMethod method = new PaymentMethod();
        method.setCode(code);
        method.setDisplayName(request.displayName().trim());
        method.setPluginBaseUrl(validateBaseUrl(request.pluginBaseUrl()));
        method.setSupportsRefund(request.supportsRefund());
        method.setActive(true);
        repository.save(method);
        auditService.userEvent(AuditAction.PLUGIN_REGISTER, "PAYMENT_METHOD", code, AuditOutcome.SUCCESS,
                method.getPluginBaseUrl());
        return toView(method, 0, healthChecker.check(List.of(method)).get(code));
    }

    public PluginView update(UUID id, UpdatePluginRequest request) {
        PaymentMethod method = repository.findById(id).orElseThrow(() -> ApiException.notFound("Plugin ne postoji"));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            method.setDisplayName(request.displayName().trim());
        }
        if (request.pluginBaseUrl() != null && !request.pluginBaseUrl().isBlank()) {
            method.setPluginBaseUrl(validateBaseUrl(request.pluginBaseUrl()));
        }
        if (request.supportsRefund() != null) {
            method.setSupportsRefund(request.supportsRefund());
        }
        if (request.active() != null && request.active() != method.isActive()) {
            if (!request.active()) {
                List<UUID> dependent = merchantMethodRepository.findMerchantsDependingOnlyOn(method.getId());
                if (!dependent.isEmpty()) {
                    throw ApiException.conflict("PLUGIN_IN_USE", "Plugin je jedina aktivna metoda za "
                            + dependent.size() + " klijent(a) - ne može se isključiti");
                }
            }
            method.setActive(request.active());
        }
        auditService.userEvent(AuditAction.PLUGIN_UPDATE, "PAYMENT_METHOD", method.getCode(), AuditOutcome.SUCCESS,
                "aktivan=" + method.isActive() + ", url=" + method.getPluginBaseUrl());
        long subscribers = merchantMethodRepository.findAll().stream()
                .filter(mpm -> mpm.isActive() && mpm.getPaymentMethod().getId().equals(method.getId()))
                .count();
        return toView(method, subscribers, healthChecker.check(List.of(method)).get(method.getCode()));
    }

    private static String validateBaseUrl(String url) {
        String trimmed = url.trim();
        if (!trimmed.matches("https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?(/[\\w\\-./]*)?")) {
            throw ApiException.badRequest("INVALID_URL", "Adresa plugin-a mora biti oblika http(s)://host:port");
        }
        return trimmed;
    }

    private static PluginView toView(PaymentMethod m, long subscribers, PluginHealthChecker.Health health) {
        return new PluginView(m.getId(), m.getCode(), m.getDisplayName(), m.getPluginBaseUrl(), m.isSupportsRefund(),
                m.isActive(), subscribers, health, m.getCreatedAt());
    }
}
