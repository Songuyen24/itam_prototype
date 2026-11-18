package com.company.itam.notification.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.itam.common.enums.EmailStatus;
import com.company.itam.notification.entity.EmailLogEntity;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLogEntity, Long> {
    Page<EmailLogEntity> findByTransactionTransactionId(Long transactionId, Pageable pageable);

    Page<EmailLogEntity> findByRecipient(String recipient, Pageable pageable);

    List<EmailLogEntity> findByStatus(EmailStatus status);

    List<EmailLogEntity> findByTransactionTransactionIdOrderByCreatedAtDesc(Long transactionId);
}
