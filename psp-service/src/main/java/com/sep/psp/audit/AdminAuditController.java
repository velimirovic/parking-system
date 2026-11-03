package com.sep.psp.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Pregled audit loga - samo SUPER_ADMIN (pravilo u SecurityConfig).
 */
@RestController
@RequestMapping("/admin/audit-log")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditLogRepository repository;
    private final AuditService auditService;

    @GetMapping
    public PageResponse<AuditEntryResponse> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size,
                                                 @RequestParam(defaultValue = "") String action,
                                                 @RequestParam(required = false) AuditOutcome outcome,
                                                 @RequestParam(defaultValue = "") String actor) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.of(
                repository.search(action.trim().toUpperCase(), outcome, actor.trim().toLowerCase(),
                        PageRequest.of(Math.max(page, 0), safeSize)),
                AuditEntryResponse::from);
    }

    @GetMapping("/verify")
    public AuditService.ChainVerification verify() {
        AuditService.ChainVerification result = auditService.verifyChain();
        auditService.userEvent(AuditAction.AUDIT_VERIFY, "AUDIT_LOG", null,
                result.valid() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE,
                "Provereno zapisa: " + result.checkedEntries());
        return result;
    }

    public record AuditEntryResponse(UUID id, long seq, Instant occurredAt, String actorType, String actorId,
                                     String action, String resourceType, String resourceId, String sourceIp,
                                     AuditOutcome outcome, String details, String hash) {
        static AuditEntryResponse from(AuditLog a) {
            return new AuditEntryResponse(a.getId(), a.getSeq(), a.getOccurredAt(), a.getActorType(),
                    a.getActorId(), a.getAction(), a.getResourceType(), a.getResourceId(), a.getSourceIp(),
                    a.getOutcome(), a.getDetails(), a.getHash());
        }
    }
}
