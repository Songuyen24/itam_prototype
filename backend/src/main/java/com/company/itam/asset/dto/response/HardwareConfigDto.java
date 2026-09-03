package com.company.itam.asset.dto.response;

public class HardwareConfigDto {
    private String defaultCpu;
    private String defaultRam;
    private String defaultStorage;
    private String defaultGraphicsCard;

    private String actualCpu;
    private String actualRam;
    private String actualStorage;
    private String actualGraphicsCard;

    private String effectiveCpu;
    private String effectiveRam;
    private String effectiveStorage;
    private String effectiveGraphicsCard;

    public HardwareConfigDto() {}

    public String getDefaultCpu() {
        return defaultCpu;
    }

    public void setDefaultCpu(String defaultCpu) {
        this.defaultCpu = defaultCpu;
    }

    public String getDefaultRam() {
        return defaultRam;
    }

    public void setDefaultRam(String defaultRam) {
        this.defaultRam = defaultRam;
    }

    public String getDefaultStorage() {
        return defaultStorage;
    }

    public void setDefaultStorage(String defaultStorage) {
        this.defaultStorage = defaultStorage;
    }

    public String getDefaultGraphicsCard() {
        return defaultGraphicsCard;
    }

    public void setDefaultGraphicsCard(String defaultGraphicsCard) {
        this.defaultGraphicsCard = defaultGraphicsCard;
    }

    public String getActualCpu() {
        return actualCpu;
    }

    public void setActualCpu(String actualCpu) {
        this.actualCpu = actualCpu;
    }

    public String getActualRam() {
        return actualRam;
    }

    public void setActualRam(String actualRam) {
        this.actualRam = actualRam;
    }

    public String getActualStorage() {
        return actualStorage;
    }

    public void setActualStorage(String actualStorage) {
        this.actualStorage = actualStorage;
    }

    public String getActualGraphicsCard() {
        return actualGraphicsCard;
    }

    public void setActualGraphicsCard(String actualGraphicsCard) {
        this.actualGraphicsCard = actualGraphicsCard;
    }

    public String getEffectiveCpu() {
        return effectiveCpu;
    }

    public void setEffectiveCpu(String effectiveCpu) {
        this.effectiveCpu = effectiveCpu;
    }

    public String getEffectiveRam() {
        return effectiveRam;
    }

    public void setEffectiveRam(String effectiveRam) {
        this.effectiveRam = effectiveRam;
    }

    public String getEffectiveStorage() {
        return effectiveStorage;
    }

    public void setEffectiveStorage(String effectiveStorage) {
        this.effectiveStorage = effectiveStorage;
    }

    public String getEffectiveGraphicsCard() {
        return effectiveGraphicsCard;
    }

    public void setEffectiveGraphicsCard(String effectiveGraphicsCard) {
        this.effectiveGraphicsCard = effectiveGraphicsCard;
    }
}
