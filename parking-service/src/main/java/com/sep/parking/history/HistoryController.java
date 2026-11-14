package com.sep.parking.history;

import com.sep.parking.common.security.CurrentUser;
import com.sep.parking.history.HistoryDtos.HistoryResponse;
import com.sep.parking.history.HistoryDtos.PaymentRow;
import com.sep.parking.session.SessionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/history")
public class HistoryController {

    private final HistoryService service;

    public HistoryController(HistoryService service) {
        this.service = service;
    }

    @GetMapping
    public HistoryResponse history(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(required = false) String month,
                                   @RequestParam(required = false) UUID vehicleId,
                                   @RequestParam(required = false) SessionStatus status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        return service.history(CurrentUser.id(jwt), month, vehicleId, status, page, size);
    }

    @GetMapping("/payments")
    public List<PaymentRow> payments(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(defaultValue = "5") int limit) {
        return service.recentPayments(CurrentUser.id(jwt), limit);
    }
}
