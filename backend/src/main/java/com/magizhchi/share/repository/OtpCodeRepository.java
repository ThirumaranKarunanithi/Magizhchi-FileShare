package com.magizhchi.share.repository;

import com.magizhchi.share.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findTopByIdentifierAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(
            String identifier, OtpCode.OtpPurpose purpose);

    long countByIdentifierAndCreatedAtAfter(String identifier, Instant after);

    @Modifying
    @Transactional
    @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
