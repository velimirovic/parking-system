package com.sep.psp.paymentmethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.common.crypto.AesGcmEncryptor;
import com.sep.psp.common.exception.ApiException;
import com.sep.psp.common.web.Masking;
import com.sep.psp.common.web.RequestContext;
import com.sep.psp.merchant.Merchant;
import com.sep.psp.merchant.MerchantRepository;
import com.sep.psp.paymentmethod.PaymentMethodDtos.ConfigEntry;
import com.sep.psp.paymentmethod.PaymentMethodDtos.MerchantMethodView;
import com.sep.psp.paymentmethod.PaymentMethodDtos.MerchantMethodsResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Pregled i upravljanje metodama placanja klijenta. Pravilo iz specifikacije: klijent uvek mora
 * imati bar jednu aktivnu metodu. Provera se radi pod lock-om na redu merchanta, pa dva paralelna
 * zahteva za deaktivaciju ne mogu oba da prodju.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MerchantMethodService {

    private static final java.util.regex.Pattern CONFIG_KEY = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,39}");

    private final MerchantRepository merchantRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final MerchantPaymentMethodRepository merchantMethodRepository;
    private final AesGcmEncryptor encryptor;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MerchantMethodsResponse list() {
        return buildResponse(RequestContext.merchantId());
    }

    public MerchantMethodsResponse toggle(String code, boolean active) {
        UUID merchantId = RequestContext.merchantId();
        Merchant merchant = merchantRepository.findByIdForUpdate(merchantId)
                .orElseThrow(() -> ApiException.notFound("Klijent ne postoji"));
        PaymentMethod method = findMethod(code);
        MerchantPaymentMethod subscription = merchantMethodRepository
                .findByMerchantIdAndPaymentMethodId(merchantId, method.getId())
                .orElse(null);

        if (active) {
            if (!method.isActive()) {
                throw ApiException.conflict("METHOD_UNAVAILABLE", "Metoda trenutno nije dostupna na PSP-u");
            }
            if (subscription == null) {
                subscription = new MerchantPaymentMethod();
                subscription.setMerchantId(merchantId);
                subscription.setPaymentMethod(method);
            }
            subscription.setActive(true);
            merchantMethodRepository.save(subscription);
        } else {
            if (subscription == null || !subscription.isActive()) {
                return buildResponse(merchantId);
            }
            boolean usable = method.isActive();
            if (usable && merchantMethodRepository.countUsable(merchantId) <= 1) {
                auditService.userEvent(AuditAction.CONFIG_CHANGE, "MERCHANT", merchant.getMerchantCode(),
                        AuditOutcome.FAILURE, "Pokusaj iskljucivanja poslednje aktivne metode " + method.getCode());
                throw ApiException.conflict("LAST_ACTIVE_METHOD",
                        "Mora ostati bar jedna aktivna metoda plaćanja");
            }
            subscription.setActive(false);
            merchantMethodRepository.saveAndFlush(subscription);
        }
        auditService.userEvent(AuditAction.CONFIG_CHANGE, "MERCHANT", merchant.getMerchantCode() + " · " + method.getCode(),
                AuditOutcome.SUCCESS, active ? "Metoda ukljucena" : "Metoda iskljucena");
        return buildResponse(merchantId);
    }

    public MerchantMethodsResponse updateConfig(String code, Map<String, String> values) {
        UUID merchantId = RequestContext.merchantId();
        Merchant merchant = merchantRepository.findByIdForUpdate(merchantId)
                .orElseThrow(() -> ApiException.notFound("Klijent ne postoji"));
        PaymentMethod method = findMethod(code);
        MerchantPaymentMethod subscription = merchantMethodRepository
                .findByMerchantIdAndPaymentMethodId(merchantId, method.getId())
                .orElseThrow(() -> ApiException.conflict("NOT_SUBSCRIBED", "Klijent nije pretplaćen na ovu metodu"));

        // vrednosti se klijentu prikazuju samo maskirane, pa prazno polje znaci "bez promene"
        Map<String, String> clean = new LinkedHashMap<>(decryptConfig(subscription));
        values.forEach((key, value) -> {
            if (!CONFIG_KEY.matcher(key).matches() || value == null || value.length() > 200) {
                throw ApiException.badRequest("INVALID_CONFIG", "Neispravan parametar konfiguracije: " + key);
            }
            if (!value.isBlank()) {
                clean.put(key, value.trim());
            }
        });
        if (clean.size() > 10) {
            throw ApiException.badRequest("INVALID_CONFIG", "Najviše 10 parametara konfiguracije");
        }
        subscription.setConfigEncrypted(clean.isEmpty() ? null : encryptor.encrypt(objectMapper.writeValueAsString(clean)));
        merchantMethodRepository.save(subscription);
        // u audit log idu samo nazivi polja, ne i vrednosti
        auditService.userEvent(AuditAction.CONFIG_CHANGE, "MERCHANT", merchant.getMerchantCode() + " · " + method.getCode(),
                AuditOutcome.SUCCESS, "Konfiguracija: " + String.join(", ", clean.keySet()));
        return buildResponse(merchantId);
    }

    private PaymentMethod findMethod(String code) {
        return paymentMethodRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Metoda ne postoji"));
    }

    private MerchantMethodsResponse buildResponse(UUID merchantId) {
        Map<UUID, MerchantPaymentMethod> subscriptions = merchantMethodRepository.findByMerchantId(merchantId).stream()
                .collect(Collectors.toMap(mpm -> mpm.getPaymentMethod().getId(), Function.identity()));
        long activeCount = subscriptions.values().stream()
                .filter(s -> s.isActive() && s.getPaymentMethod().isActive())
                .count();
        List<MerchantMethodView> views = paymentMethodRepository.findAllByOrderByCreatedAtAscCodeAsc().stream()
                // globalno ugasene metode se prikazuju samo klijentima koji su vec pretplaceni
                .filter(pm -> pm.isActive() || subscriptions.containsKey(pm.getId()))
                .map(pm -> {
                    MerchantPaymentMethod s = subscriptions.get(pm.getId());
                    boolean isActive = s != null && s.isActive();
                    return new MerchantMethodView(pm.getCode(), pm.getDisplayName(), s != null, isActive,
                            pm.isActive(), pm.isSupportsRefund(), isActive && pm.isActive() && activeCount == 1,
                            s == null ? List.of() : maskedConfig(s));
                })
                .toList();
        return new MerchantMethodsResponse(views, activeCount);
    }

    private List<ConfigEntry> maskedConfig(MerchantPaymentMethod subscription) {
        return decryptConfig(subscription).entrySet().stream()
                .map(e -> new ConfigEntry(e.getKey(), Masking.maskValue(e.getValue())))
                .toList();
    }

    private Map<String, String> decryptConfig(MerchantPaymentMethod subscription) {
        if (subscription.getConfigEncrypted() == null) {
            return Map.of();
        }
        return objectMapper.readValue(encryptor.decrypt(subscription.getConfigEncrypted()),
                new TypeReference<LinkedHashMap<String, String>>() {
                });
    }
}
