package com.earthscan.notification.repository;

import com.earthscan.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    java.util.Optional<Notification> findByIdAndUserId(String id, Long userId);

    java.util.List<Notification> findByUserIdAndReadFalse(Long userId);

    boolean existsBySourceEventIdAndUserId(String sourceEventId, Long userId);

    long deleteByUserId(Long userId);
}
