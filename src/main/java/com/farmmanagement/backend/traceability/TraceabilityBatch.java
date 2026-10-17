package com.farmmanagement.backend.traceability;

import java.util.*;

public class TraceabilityBatch {
    private String id;
    private String productName;
    private String variety;
    private String season;
    private String cropDuration;
    private String farmLocation;
    private String gpsCoordinates;
    private String pucCode;
    private String certification;
    private String certNumber;
    private String manager;
    private String inspector;
    private String productImg;
    private String productStory;
    private String harvestActualTime;
    private String harvestActualYield;
    private String harvestGradeRatio;
    private String packaging;
    private String storageCondition;
    private String expiryDate;
    private List<Map<String, Object>> journey = new ArrayList<>();
    private List<Map<String, Object>> activities = new ArrayList<>();
    private List<Map<String, Object>> fertilizers = new ArrayList<>();
    private List<Map<String, Object>> pesticides = new ArrayList<>();

    public TraceabilityBatch() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getVariety() { return variety; }
    public void setVariety(String variety) { this.variety = variety; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getCropDuration() { return cropDuration; }
    public void setCropDuration(String cropDuration) { this.cropDuration = cropDuration; }

    public String getFarmLocation() { return farmLocation; }
    public void setFarmLocation(String farmLocation) { this.farmLocation = farmLocation; }

    public String getGpsCoordinates() { return gpsCoordinates; }
    public void setGpsCoordinates(String gpsCoordinates) { this.gpsCoordinates = gpsCoordinates; }

    public String getPucCode() { return pucCode; }
    public void setPucCode(String pucCode) { this.pucCode = pucCode; }

    public String getCertification() { return certification; }
    public void setCertification(String certification) { this.certification = certification; }

    public String getCertNumber() { return certNumber; }
    public void setCertNumber(String certNumber) { this.certNumber = certNumber; }

    public String getManager() { return manager; }
    public void setManager(String manager) { this.manager = manager; }

    public String getInspector() { return inspector; }
    public void setInspector(String inspector) { this.inspector = inspector; }

    public String getProductImg() { return productImg; }
    public void setProductImg(String productImg) { this.productImg = productImg; }

    public String getProductStory() { return productStory; }
    public void setProductStory(String productStory) { this.productStory = productStory; }

    public String getHarvestActualTime() { return harvestActualTime; }
    public void setHarvestActualTime(String harvestActualTime) { this.harvestActualTime = harvestActualTime; }

    public String getHarvestActualYield() { return harvestActualYield; }
    public void setHarvestActualYield(String harvestActualYield) { this.harvestActualYield = harvestActualYield; }

    public String getHarvestGradeRatio() { return harvestGradeRatio; }
    public void setHarvestGradeRatio(String harvestGradeRatio) { this.harvestGradeRatio = harvestGradeRatio; }

    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }

    public String getStorageCondition() { return storageCondition; }
    public void setStorageCondition(String storageCondition) { this.storageCondition = storageCondition; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public List<Map<String, Object>> getJourney() { return journey; }
    public void setJourney(List<Map<String, Object>> journey) { this.journey = journey; }

    public List<Map<String, Object>> getActivities() { return activities; }
    public void setActivities(List<Map<String, Object>> activities) { this.activities = activities; }

    public List<Map<String, Object>> getFertilizers() { return fertilizers; }
    public void setFertilizers(List<Map<String, Object>> fertilizers) { this.fertilizers = fertilizers; }

    public List<Map<String, Object>> getPesticides() { return pesticides; }
    public void setPesticides(List<Map<String, Object>> pesticides) { this.pesticides = pesticides; }
}
