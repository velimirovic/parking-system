package com.sep.parking.session;

import com.sep.parking.common.security.CurrentUser;
import com.sep.parking.session.SessionDtos.CreateSessionRequest;
import com.sep.parking.session.SessionDtos.CreateSessionResponse;
import com.sep.parking.session.SessionDtos.OrderResponse;
import com.sep.parking.session.SessionDtos.QuoteRequest;
import com.sep.parking.session.SessionDtos.QuoteResponse;
import com.sep.parking.session.SessionDtos.SessionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QuoteRequest request) {
        return service.quote(CurrentUser.id(jwt), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSessionResponse create(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody CreateSessionRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/current")
    public List<SessionResponse> current(@AuthenticationPrincipal Jwt jwt) {
        return service.current(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public SessionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    /** Proverava konacan status naloga na PSP-u i vraca azurno stanje. */
    @PostMapping("/orders/{merchantOrderId}/sync")
    public OrderResponse syncOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable String merchantOrderId) {
        service.syncOrder(CurrentUser.id(jwt), merchantOrderId);
        return service.order(CurrentUser.id(jwt), merchantOrderId);
    }

    @GetMapping("/orders/{merchantOrderId}")
    public OrderResponse order(@AuthenticationPrincipal Jwt jwt, @PathVariable String merchantOrderId) {
        return service.order(CurrentUser.id(jwt), merchantOrderId);
    }
}
