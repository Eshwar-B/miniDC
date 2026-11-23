package com.minidc.server.billing;

import com.minidc.common.model.WalletInfo;
import com.minidc.common.model.WalletTransactionInfo;
import com.minidc.server.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for compute credit wallet balances, instant top-ups, transaction history, and pricing estimation.
 */
@RestController
@RequestMapping("/api")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    public record TopUpRequest(long amount) {}

    @GetMapping("/wallet")
    public ResponseEntity<WalletInfo> getWallet(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(billingService.getWalletInfo(user.id()));
    }

    @PostMapping("/wallet/topup")
    public ResponseEntity<WalletInfo> topUp(
            @RequestBody TopUpRequest request,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return ResponseEntity.ok(billingService.topUp(user.id(), request.amount()));
    }

    @GetMapping("/wallet/transactions")
    public ResponseEntity<List<WalletTransactionInfo>> getTransactions(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(billingService.getTransactions(user.id()));
    }

    @GetMapping("/billing/estimate")
    public ResponseEntity<BillingService.RateEstimate> estimate(
            @RequestParam int cores,
            @RequestParam int ramMb,
            @RequestParam int diskGb,
            @RequestParam int hours
    ) {
        return ResponseEntity.ok(billingService.estimateCost(cores, ramMb, diskGb, hours));
    }
}
