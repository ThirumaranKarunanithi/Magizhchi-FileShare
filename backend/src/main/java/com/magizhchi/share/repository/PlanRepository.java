package com.magizhchi.share.repository;

import com.magizhchi.share.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByCode(String code);

    /** The single plan flagged as default — assigned to new + legacy users. */
    Optional<Plan> findFirstByIsDefaultTrue();

    /** Catalog ordered for the upgrade picker. */
    List<Plan> findAllByOrderBySortOrderAsc();
}
