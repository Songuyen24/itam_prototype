package com.company.itam.notification.repository;

import com.company.itam.notification.entity.EmailLogEntity;
import com.company.itam.common.enums.EmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLogEntity, Long> {
    Page<EmailLogEntity> findByTransactionTransactionId(Long transactionId, Pageable pageable);
    Page<EmailLogEntity> findByRecipient(String recipient, Pageable pageable);
    List<EmailLogEntity> findByStatus(EmailStatus status);
}
