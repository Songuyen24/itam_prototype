package com.company.itam.audit.repository;

import com.company.itam.audit.entity.AssetHistoryEntity;
import com.company.itam.common.enums.AssetAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistoryEntity, Long> {
    Page<AssetHistoryEntity> findByAssetAssetIdOrderByCreatedAtDesc(Long assetId, Pageable pageable);
    Page<AssetHistoryEntity> findByTransactionTransactionIdOrderByCreatedAtDesc(Long transactionId, Pageable pageable);
    Page<AssetHistoryEntity> findByActorUserUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<AssetHistoryEntity> findByAction(AssetAction action, Pageable pageable);

    @Query("SELECT h FROM AssetHistoryEntity h WHERE h.asset.assetId = :assetId ORDER BY h.createdAt DESC")
    List<AssetHistoryEntity> findLatestByAssetId(@Param("assetId") Long assetId, Pageable pageable);
}
