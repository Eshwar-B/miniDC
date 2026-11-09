package com.minidc.server.kafka;

import com.minidc.common.event.TelemetryMetricEvent;
import com.minidc.server.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for retrieving live time-series telemetry metrics and ingesting metric streams.
 */
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final KafkaEventPublisher kafkaEventPublisher;

    public TelemetryController(TelemetryService telemetryService, KafkaEventPublisher kafkaEventPublisher) {
        this.telemetryService = telemetryService;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @GetMapping("/leases/{leaseId}")
    public List<TelemetryMetricEvent> getLeaseTelemetry(
            @PathVariable UUID leaseId,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return telemetryService.getRecentMetricsForLease(leaseId);
    }

    @GetMapping("/leases/{leaseId}/latest")
    public TelemetryMetricEvent getLatestLeaseTelemetry(
            @PathVariable UUID leaseId,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return telemetryService.getLatestMetricForLease(leaseId);
    }

    @GetMapping("/nodes/{nodeId}")
    public List<TelemetryMetricEvent> getNodeTelemetry(
            @PathVariable UUID nodeId,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return telemetryService.getRecentMetricsForNode(nodeId);
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void ingestMetric(@RequestBody TelemetryMetricEvent metric) {
        kafkaEventPublisher.publishTelemetry(metric);
    }
}

