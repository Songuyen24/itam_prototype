package com.company.itam.asset.repository;

import com.company.itam.asset.entity.AssetSoftwareDetailsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetSoftwareDetailsRepository extends JpaRepository<AssetSoftwareDetailsEntity, Long> {
}
