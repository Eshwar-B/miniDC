package com.minidc.server.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data repository for managing registration invite codes.
 */
@Repository
public interface InviteRepository extends JpaRepository<Invite, String> {
    Optional<Invite> findByCode(String code);
}
