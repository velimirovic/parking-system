package com.sep.parking.zone;

import com.sep.parking.zone.ZoneDtos.ZoneResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Javni pregled zona, cenovnika, pravila i lokacija. */
@RestController
@RequestMapping("/zones")
public class ZoneController {

    private final ZoneService service;

    public ZoneController(ZoneService service) {
        this.service = service;
    }

    @GetMapping
    public List<ZoneResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ZoneResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
