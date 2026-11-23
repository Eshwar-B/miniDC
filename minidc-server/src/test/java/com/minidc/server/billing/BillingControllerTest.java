package com.minidc.server.billing;

import com.minidc.common.enums.Role;
import com.minidc.common.model.WalletInfo;
import com.minidc.common.model.WalletTransactionInfo;
import com.minidc.server.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class BillingControllerTest {

    private BillingService billingService;
    private BillingController controller;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        billingService = Mockito.mock(BillingService.class);
        controller = new BillingController(billingService);
        currentUser = new CurrentUser(UUID.randomUUID(), "borrower@minidc.local", Role.MEMBER);
    }

    @Test
    void shouldReturnWalletInfo() {
        WalletInfo wallet = new WalletInfo(UUID.randomUUID(), currentUser.id(), 1500, Instant.now(), Instant.now());
        when(billingService.getWalletInfo(currentUser.id())).thenReturn(wallet);

        ResponseEntity<WalletInfo> response = controller.getWallet(currentUser);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1500, response.getBody().balanceCredits());
    }

    @Test
    void shouldReturnEstimate() {
        BillingService.RateEstimate estimate = new BillingService.RateEstimate(2, 4096, 20, 2, 10, 1200);
        when(billingService.estimateCost(2, 4096, 20, 2)).thenReturn(estimate);

        ResponseEntity<BillingService.RateEstimate> response = controller.estimate(2, 4096, 20, 2);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().perMinuteCredits());
        assertEquals(1200, response.getBody().totalEstimatedCredits());
    }

    @Test
    void shouldReturnTransactions() {
        WalletTransactionInfo tx = new WalletTransactionInfo(
                UUID.randomUUID(), UUID.randomUUID(), null, 500, "TOPUP", "Top up", Instant.now()
        );
        when(billingService.getTransactions(currentUser.id())).thenReturn(List.of(tx));

        ResponseEntity<List<WalletTransactionInfo>> response = controller.getTransactions(currentUser);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("TOPUP", response.getBody().get(0).type());
    }
}
