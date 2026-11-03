package com.sep.psp.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sep.psp.audit.AuditAction;
import com.sep.psp.audit.AuditOutcome;
import com.sep.psp.audit.AuditService;
import com.sep.psp.common.config.BootstrapProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kreira prvog super admina ako ne postoji. Lozinka iz konfiguracije je privremena:
 * mora se promeniti pri prvoj prijavi, a zatim se uparuje Authenticator (PCI DSS 2.2.2 / 8.3.5).
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private final PspUserRepository userRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final BootstrapProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            return;
        }
        PspUser admin = new PspUser();
        admin.setEmail(properties.superAdminEmail());
        admin.setFullName("Super administrator");
        admin.setRole(Role.SUPER_ADMIN);
        admin.setMustChangePassword(true);
        admin.setEnabled(true);
        admin.setPasswordHash("");
        userRepository.save(admin);
        authService.applyNewPassword(admin, properties.superAdminPassword());
        auditService.record(AuditService.ACTOR_SYSTEM, "bootstrap", AuditAction.USER_BOOTSTRAP, "PSP_USER",
                admin.getId().toString(), AuditOutcome.SUCCESS, "Kreiran prvi super admin");
        log.warn("Kreiran super admin {} - privremena lozinka se mora promeniti pri prvoj prijavi",
                properties.superAdminEmail());
    }
}
