package com.company.itam.workflow.disposal.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.itam.workflow.disposal.entity.TransactionDisposalDetailEntity;

public interface TransactionDisposalDetailRepository extends JpaRepository<TransactionDisposalDetailEntity, Long> {}
