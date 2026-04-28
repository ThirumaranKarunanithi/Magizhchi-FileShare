package com.magizhchi.share.repository;

import com.magizhchi.share.model.ActivityEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    List<ActivityEvent> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);
}
