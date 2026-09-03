package com.company.itam.asset.dto.request;

public class ValidateUniquenessRequest {
    private String assetTag;
    private String serialNumber;
    private Long excludeAssetId;

    public ValidateUniquenessRequest() {}

    public ValidateUniquenessRequest(String assetTag, String serialNumber, Long excludeAssetId) {
        this.assetTag = assetTag;
        this.serialNumber = serialNumber;
        this.excludeAssetId = excludeAssetId;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public void setAssetTag(String assetTag) {
        this.assetTag = assetTag;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Long getExcludeAssetId() {
        return excludeAssetId;
    }

    public void setExcludeAssetId(Long excludeAssetId) {
        this.excludeAssetId = excludeAssetId;
    }
}
