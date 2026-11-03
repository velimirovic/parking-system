package com.sep.psp.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PspUserRepository extends JpaRepository<PspUser, UUID> {

    @Query("select u from PspUser u where lower(u.email) = lower(:email)")
    Optional<PspUser> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from PspUser u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    boolean existsByRole(Role role);

    List<PspUser> findByMerchantIdIn(List<UUID> merchantIds);

    List<PspUser> findByMerchantId(UUID merchantId);
}
