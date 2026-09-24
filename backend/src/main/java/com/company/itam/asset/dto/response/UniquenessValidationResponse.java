package com.company.itam.asset.dto.response;

public class UniquenessValidationResponse {
    private boolean assetTagAvailable;
    private String assetTagMessage;
    private boolean serialNumberAvailable;
    private String serialNumberMessage;

    public UniquenessValidationResponse() {}

    public UniquenessValidationResponse(boolean assetTagAvailable, String assetTagMessage, boolean serialNumberAvailable, String serialNumberMessage) {
        this.assetTagAvailable = assetTagAvailable;
        this.assetTagMessage = assetTagMessage;
        this.serialNumberAvailable = serialNumberAvailable;
        this.serialNumberMessage = serialNumberMessage;
    }

    public boolean isAssetTagAvailable() {
        return assetTagAvailable;
    }

    public void setAssetTagAvailable(boolean assetTagAvailable) {
        this.assetTagAvailable = assetTagAvailable;
    }

    public String getAssetTagMessage() {
        return assetTagMessage;
    }

    public void setAssetTagMessage(String assetTagMessage) {
        this.assetTagMessage = assetTagMessage;
    }

    public boolean isSerialNumberAvailable() {
        return serialNumberAvailable;
    }

    public void setSerialNumberAvailable(boolean serialNumberAvailable) {
        this.serialNumberAvailable = serialNumberAvailable;
    }

    public String getSerialNumberMessage() {
        return serialNumberMessage;
    }

    public void setSerialNumberMessage(String serialNumberMessage) {
        this.serialNumberMessage = serialNumberMessage;
    }
}
