package com.magizhchi.share.repository;

import com.magizhchi.share.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
