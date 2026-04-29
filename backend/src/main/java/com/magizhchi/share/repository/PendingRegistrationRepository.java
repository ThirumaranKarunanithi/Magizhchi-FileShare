package com.magizhchi.share.repository;

import com.magizhchi.share.model.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByMobileNumber(String mobileNumber);
    Optional<PendingRegistration> findByEmail(String email);

    /** Used at verify-time to locate the row by whichever channel the user submitted. */
    @Query("SELECT p FROM PendingRegistration p WHERE p.mobileNumber = :id OR p.email = :id")
    Optional<PendingRegistration> findByMobileOrEmail(@Param("id") String identifier);

    /** Sweeper for the nightly cleanup. Returns the number of rows removed. */
    @Modifying
    @Query("DELETE FROM PendingRegistration p WHERE p.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
