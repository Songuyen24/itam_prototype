package com.company.itam.workflow.recovery.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.itam.workflow.recovery.entity.TransactionRecoveryDetailEntity;

public interface TransactionRecoveryDetailRepository extends JpaRepository<TransactionRecoveryDetailEntity, Long> {}
