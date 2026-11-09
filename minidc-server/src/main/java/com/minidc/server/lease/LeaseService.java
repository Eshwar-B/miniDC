package com.minidc.server.lease;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.enums.NodeStatus;
import com.minidc.common.ws.payload.CreateVmPayload;
import com.minidc.server.audit.AuditService;
import com.minidc.server.config.AppProperties;
import com.minidc.server.crypto.AesGcmCrypto;
import com.minidc.server.lease.dto.CreateLeaseRequest;
import com.minidc.server.lease.dto.LeaseResponse;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.offer.Offer;
import com.minidc.server.offer.OfferRepository;
import com.minidc.server.user.User;
import com.minidc.server.user.UserRepository;
import com.minidc.server.ws.AgentCommandSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.minidc.common.enums.LeaseState.ACTIVE;
import static com.minidc.common.enums.LeaseState.APPROVED;
import static com.minidc.common.enums.LeaseState.CANCELLED;
import static com.minidc.common.enums.LeaseState.ENDED;
import static com.minidc.common.enums.LeaseState.EXPIRING;
import static com.minidc.common.enums.LeaseState.FAILED;
import static com.minidc.common.enums.LeaseState.PROVISIONING;
import static com.minidc.common.enums.LeaseState.REJECTED;
import static com.minidc.common.enums.LeaseState.REQUESTED;

/**
 * Core business service managing the lease lifecycle, capacity checks, and hypervisor coordination.
 */
@Service
public class LeaseService {

    private static final Map<LeaseState, Set<LeaseState>> ALLOWED_TRANSITIONS = Map.of(
            REQUESTED, Set.of(APPROVED, REJECTED, CANCELLED),
            APPROVED, Set.of(PROVISIONING, CANCELLED),
            PROVISIONING, Set.of(ACTIVE, FAILED),
            ACTIVE, Set.of(EXPIRING, ENDED),
            EXPIRING, Set.of(ENDED)
    );

    private final LeaseRepository leaseRepository;
    private final OfferRepository offerRepository;
    private final NodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AesGcmCrypto aesGcmCrypto;
    private final AgentCommandSender agentCommandSender;
    private final AppProperties properties;
    private final com.minidc.server.redis.DistributedLockService distributedLockService;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.minidc.server.kafka.KafkaEventPublisher kafkaEventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.minidc.server.ws.stomp.StompNotificationService stompNotificationService;

    public void setKafkaEventPublisher(com.minidc.server.kafka.KafkaEventPublisher kafkaEventPublisher) {
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    public void setStompNotificationService(com.minidc.server.ws.stomp.StompNotificationService stompNotificationService) {
        this.stompNotificationService = stompNotificationService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaseService(LeaseRepository leaseRepository,
                        OfferRepository offerRepository,
                        NodeRepository nodeRepository,
                        UserRepository userRepository,
                        AuditService auditService,
                        AesGcmCrypto aesGcmCrypto,
                        AgentCommandSender agentCommandSender,
                        AppProperties properties,
                        com.minidc.server.redis.DistributedLockService distributedLockService) {
        this.leaseRepository = leaseRepository;
        this.offerRepository = offerRepository;
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.aesGcmCrypto = aesGcmCrypto;
        this.agentCommandSender = agentCommandSender;
        this.properties = properties;
        this.distributedLockService = distributedLockService != null
                ? distributedLockService
                : new com.minidc.server.redis.DistributedLockService(new com.minidc.server.redis.RedisStateService(null, properties));
    }

    public LeaseService(LeaseRepository leaseRepository,
                        OfferRepository offerRepository,
                        NodeRepository nodeRepository,
                        UserRepository userRepository,
                        AuditService auditService,
                        AesGcmCrypto aesGcmCrypto,
                        AgentCommandSender agentCommandSender,
                        AppProperties properties) {
        this(leaseRepository, offerRepository, nodeRepository, userRepository, auditService,
                aesGcmCrypto, agentCommandSender, properties, null);
    }

    @Transactional
    public Lease transition(UUID id, LeaseState to, String reason) {
        Lease lease = leaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + id));

        LeaseState current = lease.getState();
        Set<LeaseState> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(to)) {
            throw new InvalidStateException(current, to);
        }

        lease.setState(to);
        lease.setUpdatedAt(Instant.now());

        if (to == FAILED) {
            lease.setFailReason(reason);
        }

        if (to == ACTIVE && lease.getStartsAt() == null) {
            Instant now = Instant.now();
            lease.setStartsAt(now);
            lease.setEndsAt(now.plus(lease.getRequestedHours(), ChronoUnit.HOURS));
        }

        auditService.log(lease.getBorrowerId(), lease.getId(), lease.getNodeId(), "LEASE_" + to, reason);
        Lease saved = leaseRepository.save(lease);

        if (kafkaEventPublisher != null) {
            try {
                kafkaEventPublisher.publishLeaseLifecycle(new com.minidc.common.event.LeaseLifecycleEvent(
                        saved.getId(),
                        saved.getOfferId(),
                        saved.getBorrowerId(),
                        saved.getNodeId(),
                        to,
                        reason,
                        Instant.now()
                ));
            } catch (Exception ignored) {
            }
        }

        if (stompNotificationService != null) {
            try {
                stompNotificationService.broadcastLease(saved, "STATE_CHANGED");
            } catch (Exception ignored) {
            }
        }

        if (to == PROVISIONING) {
            String plainPassword = aesGcmCrypto.decrypt(saved.getVmPasswordEnc());
            CreateVmPayload payload = new CreateVmPayload(
                    saved.getId(),
                    saved.getRamMb(),
                    saved.getCores(),
                    saved.getDiskGb(),
                    saved.getRequestedHours(),
                    saved.getVmUser(),
                    plainPassword
            );
            agentCommandSender.sendCreateVm(saved.getNodeId(), payload);
        } else if (to == ENDED || to == FAILED) {
            agentCommandSender.sendDestroyVm(saved.getNodeId(), saved.getId());
        }

        return saved;
    }

    @Transactional
    public LeaseResponse createLease(UUID borrowerId, CreateLeaseRequest request) {
        return distributedLockService.withLock(
                "offer:" + request.offerId(),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                () -> doCreateLease(borrowerId, request)
        );
    }

    private LeaseResponse doCreateLease(UUID borrowerId, CreateLeaseRequest request) {
        User borrower = userRepository.findById(borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + borrowerId));
        if (borrower.isBanned()) {
            throw new IllegalArgumentException("Forbidden: User account is banned");
        }

        long activeCount = leaseRepository.countByBorrowerIdAndStateIn(
                borrowerId,
                List.of(REQUESTED, APPROVED, PROVISIONING, ACTIVE, EXPIRING)
        );
        if (activeCount >= properties.maxActiveLeasesPerUser()) {
            throw new CapacityExceededException("User reached maximum active leases limit of " + properties.maxActiveLeasesPerUser());
        }

        Offer offer = offerRepository.findByIdForUpdate(request.offerId())
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + request.offerId()));

        if (!offer.isActive()) {
            throw new IllegalArgumentException("Offer is not active");
        }

        if (request.ramMb() > offer.getMaxRamMb()
                || request.cores() > offer.getMaxCores()
                || request.diskGb() > offer.getMaxDiskGb()
                || request.requestedHours() > offer.getMaxHours()) {
            throw new CapacityExceededException("Requested resources exceed offer maximum limits");
        }

        if (offer.getAllowedUserIds() != null && !offer.getAllowedUserIds().isEmpty()
                && !offer.getAllowedUserIds().contains(borrowerId)) {
            throw new IllegalArgumentException("Forbidden: User is not authorized for this offer");
        }

        Node node = nodeRepository.findById(offer.getNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + offer.getNodeId()));

        if (node.getStatus() != NodeStatus.ONLINE) {
            throw new IllegalArgumentException("Node is currently offline");
        }

        if (offer.getWindowStart() != null && offer.getWindowEnd() != null) {
            LocalTime now = LocalTime.now();
            boolean inWindow;
            if (offer.getWindowStart().isBefore(offer.getWindowEnd())) {
                inWindow = !now.isBefore(offer.getWindowStart()) && !now.isAfter(offer.getWindowEnd());
            } else {
                inWindow = !now.isBefore(offer.getWindowStart()) || !now.isAfter(offer.getWindowEnd());
            }
            if (!inWindow) {
                throw new IllegalArgumentException("Request is outside the daily availability window");
            }
        }

        List<Lease> activeLeases = leaseRepository.findByNodeIdAndStateIn(
                node.getId(),
                List.of(APPROVED, PROVISIONING, ACTIVE)
        );

        int currentRam = activeLeases.stream().mapToInt(Lease::getRamMb).sum();
        int currentCores = activeLeases.stream().mapToInt(Lease::getCores).sum();
        int currentDisk = activeLeases.stream().mapToInt(Lease::getDiskGb).sum();

        if (currentRam + request.ramMb() > offer.getMaxRamMb()
                || currentCores + request.cores() > offer.getMaxCores()
                || currentDisk + request.diskGb() > offer.getMaxDiskGb()) {
            throw new CapacityExceededException("Node capacity exceeded for requested resources");
        }

        UUID leaseId = UUID.randomUUID();
        String vmUser = "minidc";
        byte[] pwdBytes = new byte[12];
        secureRandom.nextBytes(pwdBytes);
        String vmPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(pwdBytes);
        String vmPasswordEnc = aesGcmCrypto.encrypt(vmPassword);

        LeaseState initialState = offer.isAutoApprove() ? APPROVED : REQUESTED;

        Lease lease = new Lease(
                leaseId,
                offer.getId(),
                borrowerId,
                node.getId(),
                request.ramMb(),
                request.cores(),
                request.diskGb(),
                request.requestedHours(),
                initialState,
                "lease-" + leaseId.toString().substring(0, 8),
                null,
                vmUser,
                vmPasswordEnc,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null
        );

        Lease saved = leaseRepository.save(lease);
        auditService.log(borrowerId, leaseId, node.getId(), "LEASE_CREATED", "Lease created in state " + initialState);

        if (stompNotificationService != null) {
            try {
                stompNotificationService.broadcastLease(saved, "CREATED");
            } catch (Exception ignored) {
            }
        }

        if (initialState == APPROVED) {
            transition(saved.getId(), PROVISIONING, "Auto-approved");
        }

        return LeaseResponse.from(saved);
    }

    @Transactional
    public Lease approve(UUID leaseId, UUID lenderId) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));
        Node node = nodeRepository.findById(lease.getNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + lease.getNodeId()));

        if (!node.getOwnerId().equals(lenderId)) {
            throw new IllegalArgumentException("Unauthorized to approve lease on node: " + node.getId());
        }

        transition(leaseId, APPROVED, "Approved by lender");
        return transition(leaseId, PROVISIONING, "Beginning VM provisioning");
    }

    @Transactional
    public Lease reject(UUID leaseId, UUID lenderId, String reason) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));
        Node node = nodeRepository.findById(lease.getNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + lease.getNodeId()));

        if (!node.getOwnerId().equals(lenderId)) {
            throw new IllegalArgumentException("Unauthorized to reject lease on node: " + node.getId());
        }

        return transition(leaseId, REJECTED, reason != null ? reason : "Rejected by lender");
    }

    @Transactional
    public Lease kill(UUID leaseId, UUID userId, String reason) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

        Node node = nodeRepository.findById(lease.getNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + lease.getNodeId()));

        if (!node.getOwnerId().equals(userId)) {
            User caller = userRepository.findById(userId).orElse(null);
            if (caller == null || caller.getRole() != com.minidc.common.enums.Role.ADMIN) {
                throw new IllegalArgumentException("Unauthorized: Only lender or admin can kill this lease");
            }
        }

        lease.setState(ENDED);
        lease.setFailReason(reason != null ? reason : "Killed by lender/admin");
        lease.setUpdatedAt(Instant.now());
        auditService.log(userId, lease.getId(), lease.getNodeId(), "LEASE_KILLED", reason);
        agentCommandSender.sendDestroyVm(lease.getNodeId(), lease.getId());
        return leaseRepository.save(lease);
    }

    @Transactional
    public Lease endLease(UUID leaseId, UUID borrowerId) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

        if (!lease.getBorrowerId().equals(borrowerId)) {
            throw new IllegalArgumentException("Unauthorized: Only the lease borrower can end this lease");
        }

        if (lease.getState() == ACTIVE || lease.getState() == EXPIRING) {
            return transition(leaseId, ENDED, "Ended by borrower");
        } else if (lease.getState() == REQUESTED || lease.getState() == APPROVED) {
            return transition(leaseId, CANCELLED, "Cancelled by borrower");
        } else {
            throw new InvalidStateException(lease.getState(), ENDED);
        }
    }

    @Transactional(readOnly = true)
    public Lease getLease(UUID leaseId) {
        return leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));
    }

    @Transactional(readOnly = true)
    public List<Lease> getMyLeases(UUID borrowerId) {
        return leaseRepository.findByBorrowerId(borrowerId);
    }

    @Transactional(readOnly = true)
    public List<Lease> getIncomingLeases(UUID lenderId) {
        List<Node> myNodes = nodeRepository.findByOwnerId(lenderId);
        List<UUID> nodeIds = myNodes.stream().map(Node::getId).toList();
        if (nodeIds.isEmpty()) {
            return List.of();
        }
        return leaseRepository.findByNodeIdIn(nodeIds);
    }
}
