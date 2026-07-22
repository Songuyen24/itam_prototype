package com.company.itam.asset.repository;

import com.company.itam.asset.entity.AssetHardwareDetailsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetHardwareDetailsRepository extends JpaRepository<AssetHardwareDetailsEntity, Long> {
    Optional<AssetHardwareDetailsEntity> findBySerialNumber(String serialNumber);
    boolean existsBySerialNumber(String serialNumber);
}
