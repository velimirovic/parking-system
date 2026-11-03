package com.sep.psp.transaction;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sep.psp.common.web.PageResponse;
import com.sep.psp.transaction.MerchantTransactionService.TransactionDetail;
import com.sep.psp.transaction.MerchantTransactionService.TransactionRow;
import com.sep.psp.transaction.MerchantTransactionService.TransactionStats;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/merchant/transactions")
@RequiredArgsConstructor
public class MerchantTransactionController {

    private final MerchantTransactionService service;

    @GetMapping
    public PageResponse<TransactionRow> search(@RequestParam(defaultValue = "") String q,
                                               @RequestParam(required = false) TransactionStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.search(q, status, page, size);
    }

    @GetMapping("/stats")
    public TransactionStats stats() {
        return service.todayStats();
    }

    @GetMapping("/{id}")
    public TransactionDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "") String q,
                                         @RequestParam(required = false) TransactionStatus status) {
        byte[] body = service.exportCsv(q, status).getBytes(StandardCharsets.UTF_8);
        String filename = "transakcije-" + LocalDate.now(MerchantTransactionService.ZONE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
