package com.openclassrooms.tourguide.controller;

import java.util.UUID;

public class NearByAttractionsDTO {

    private UUID attractionId;
    private UUID userId;
    private String attractionName;
    private double attractionLongitude;
    private double attractionLatitude;
    private double userLongitude;
    private double userLatitude;
    private double distance;
    private int rewardPoints;

    public String getAttractionName() {
        return attractionName;
    }

    public double getAttractionLongitude() {
        return attractionLongitude;
    }

    public void setAttractionLongitude(double attractionLongitude) {
        this.attractionLongitude = attractionLongitude;
    }

    public double getAttractionLatitude() {
        return attractionLatitude;
    }

    public void setAttractionLatitude(double attractionLatitude) {
        this.attractionLatitude = attractionLatitude;
    }

    public double getUserLongitude() {
        return userLongitude;
    }

    public void setUserLongitude(double userLongitude) {
        this.userLongitude = userLongitude;
    }

    public double getUserLatitude() {
        return userLatitude;
    }

    public void setUserLatitude(double userLatitude) {
        this.userLatitude = userLatitude;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }
}
