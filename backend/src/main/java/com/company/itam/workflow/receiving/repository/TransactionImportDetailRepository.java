package com.company.itam.workflow.receiving.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.itam.workflow.receiving.entity.TransactionImportDetailEntity;

public interface TransactionImportDetailRepository extends JpaRepository<TransactionImportDetailEntity, Long> {}
