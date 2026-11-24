package com.minidc.server.config;

import com.minidc.server.billing.BillingService;
import com.minidc.server.lease.LeaseService;
import com.minidc.server.node.NodeService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics configuration for MiniDC custom application metrics.
 * Exposes business-level metrics for Prometheus scraping and Grafana dashboards.
 */
@Configuration
public class MetricsConfig {

    /**
     * Gauge tracking the number of currently active (running) sandbox leases.
     */
    @Bean
    public Gauge activeLeasesGauge(MeterRegistry registry, LeaseService leaseService) {
        return Gauge.builder("minidc_active_leases_total", leaseService, service -> service.countActiveLeases())
                .description("Total number of currently active sandbox leases")
                .tag("component", "lease")
                .register(registry);
    }

    /**
     * Gauge tracking the number of connected edge agent nodes.
     */
    @Bean
    public Gauge connectedNodesGauge(MeterRegistry registry, NodeService nodeService) {
        return Gauge.builder("minidc_connected_nodes_total", nodeService, service -> service.countOnlineNodes())
                .description("Total number of online edge agent nodes")
                .tag("component", "node")
                .register(registry);
    }

    /**
     * Counter tracking total system credit consumption per minute (burn rate).
     * Incremented by the billing scheduler.
     */
    @Bean
    public Counter creditBurnRateCounter(MeterRegistry registry) {
        return Counter.builder("minidc_credit_burn_rate_total")
                .description("Total credits burned per minute across all active leases")
                .tag("component", "billing")
                .register(registry);
    }

    /**
     * Timer measuring VM provisioning duration (from REQUESTED to ACTIVE).
     */
    @Bean
    public Timer vmProvisioningTimer(MeterRegistry registry) {
        return Timer.builder("minidc_vm_provisioning_duration_seconds")
                .description("VM provisioning latency from request to active state")
                .tag("component", "provisioning")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofSeconds(10))
                .maximumExpectedValue(Duration.ofMinutes(10))
                .register(registry);
    }

    /**
     * Counter for lease lifecycle state transitions.
     */
    @Bean
    public Counter leaseStateCounter(MeterRegistry registry) {
        return Counter.builder("minidc_lease_state_total")
                .description("Total leases by current state")
                .tag("component", "lease")
                .register(registry);
    }

    /**
     * Counter for node CPU usage (updated by telemetry consumer).
     */
    @Bean
    public Gauge nodeCpuUsageGauge(MeterRegistry registry) {
        return Gauge.builder("minidc_node_cpu_usage_percent", new AtomicLong(0), AtomicLong::get)
                .description("Node CPU usage percentage")
                .tag("component", "telemetry")
                .register(registry);
    }

    /**
     * Counter for node memory usage (updated by telemetry consumer).
     */
    @Bean
    public Gauge nodeMemoryUsageGauge(MeterRegistry registry) {
        return Gauge.builder("minidc_node_memory_usage_percent", new AtomicLong(0), AtomicLong::get)
                .description("Node memory usage percentage")
                .tag("component", "telemetry")
                .register(registry);
    }

    /**
     * Counter for abuse alerts.
     */
    @Bean
    public Counter abuseAlertsCounter(MeterRegistry registry) {
        return Counter.builder("minidc_abuse_alerts_total")
                .description("Total abuse alerts detected")
                .tag("component", "security")
                .register(registry);
    }

    /**
     * Gauge for node reliability score.
     */
    @Bean
    public Gauge nodeReliabilityScoreGauge(MeterRegistry registry) {
        return Gauge.builder("minidc_node_reliability_score", new AtomicLong(0), AtomicLong::get)
                .description("Node reliability score (0-100)")
                .tag("component", "matchmaking")
                .register(registry);
    }

    /**
     * Counter for telemetry events received.
     */
    @Bean
    public Counter telemetryEventsCounter(MeterRegistry registry) {
        return Counter.builder("minidc_telemetry_events_total")
                .description("Total telemetry events received from agents")
                .tag("component", "telemetry")
                .register(registry);
    }

    /**
     * Counter for total credits in circulation.
     */
    @Bean
    public Gauge totalCreditsCirculatingGauge(MeterRegistry registry, BillingService billingService) {
        return Gauge.builder("minidc_total_credits_circulating", billingService, service -> service.getTotalCreditsInCirculation())
                .description("Total credits currently in circulation across all wallets")
                .tag("component", "billing")
                .register(registry);
    }

    /**
     * Gauge for total wallet balance.
     */
    @Bean
    public Gauge totalWalletBalanceGauge(MeterRegistry registry, BillingService billingService) {
        return Gauge.builder("minidc_wallet_balance", billingService, service -> service.getTotalWalletBalance())
                .description("Sum of all wallet balances")
                .tag("component", "billing")
                .register(registry);
    }

    /**
     * Counter for wallet transactions by type.
     */
    @Bean
    public Counter walletTransactionsCounter(MeterRegistry registry) {
        return Counter.builder("minidc_wallet_transactions_total")
                .description("Total wallet transactions by type")
                .tag("component", "billing")
                .register(registry);
    }

    /**
     * Counter for lender earnings.
     */
    @Bean
    public Counter lenderEarningsCounter(MeterRegistry registry) {
        return Counter.builder("minidc_lender_earnings_total")
                .description("Total earnings credited to lenders")
                .tag("component", "billing")
                .register(registry);
    }

    /**
     * Counter for wallet top-ups.
     */
    @Bean
    public Counter walletTopupCounter(MeterRegistry registry) {
        return Counter.builder("minidc_wallet_topup_total")
                .description("Total wallet top-up transactions")
                .tag("component", "billing")
                .register(registry);
    }
}