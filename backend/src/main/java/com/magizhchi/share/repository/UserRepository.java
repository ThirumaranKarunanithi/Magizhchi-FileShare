package com.magizhchi.share.repository;

import com.magizhchi.share.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMobileNumber(String mobileNumber);
    Optional<User> findByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "u.mobileNumber LIKE CONCAT('%', :q, '%') OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<User> searchUsers(@Param("q") String query);

    /** Find by mobile OR email (used at login) */
    @Query("SELECT u FROM User u WHERE u.mobileNumber = :identifier OR u.email = :identifier")
    Optional<User> findByMobileOrEmail(@Param("identifier") String identifier);

    /**
     * Atomically add {@code delta} bytes to the user's storage counter.
     * Uses GREATEST(0, ...) so the counter can never go negative (safe for deletes).
     * Returns the number of rows updated (always 1 if the user exists).
     */
    @Modifying
    @Query(value = "UPDATE users SET storage_used_bytes = GREATEST(0, storage_used_bytes + :delta) WHERE id = :userId",
           nativeQuery = true)
    int adjustStorageUsed(@Param("userId") Long userId, @Param("delta") long delta);
}
