package com.wifiscan.model;

/**
 * Kết quả ước tính khoảng cách từ Client tới Scanner cùng phân tích khoa học.
 */
public class DistanceEstimate {
    private final double distanceMeters;
    private final double minDistanceMeters;
    private final double maxDistanceMeters;
    private final String zoneDescription;
    private final String calculationMethod;
    private final String confidenceLevel;
    private final int rssiDbm;
    private final double rttMs;
    private final double pathLossExponent;
    private final double referencePowerA;
    private final String mathematicalFormula;
    private final String detailedAnalysis;

    public DistanceEstimate(double distanceMeters, double minDistanceMeters, double maxDistanceMeters,
                            String zoneDescription, String calculationMethod, String confidenceLevel,
                            int rssiDbm, double rttMs, double pathLossExponent, double referencePowerA,
                            String mathematicalFormula, String detailedAnalysis) {
        this.distanceMeters = distanceMeters;
        this.minDistanceMeters = minDistanceMeters;
        this.maxDistanceMeters = maxDistanceMeters;
        this.zoneDescription = zoneDescription;
        this.calculationMethod = calculationMethod;
        this.confidenceLevel = confidenceLevel;
        this.rssiDbm = rssiDbm;
        this.rttMs = rttMs;
        this.pathLossExponent = pathLossExponent;
        this.referencePowerA = referencePowerA;
        this.mathematicalFormula = mathematicalFormula;
        this.detailedAnalysis = detailedAnalysis;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public double getMinDistanceMeters() {
        return minDistanceMeters;
    }

    public double getMaxDistanceMeters() {
        return maxDistanceMeters;
    }

    public String getZoneDescription() {
        return zoneDescription;
    }

    public String getCalculationMethod() {
        return calculationMethod;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public int getRssiDbm() {
        return rssiDbm;
    }

    public double getRttMs() {
        return rttMs;
    }

    public double getPathLossExponent() {
        return pathLossExponent;
    }

    public double getReferencePowerA() {
        return referencePowerA;
    }

    public String getMathematicalFormula() {
        return mathematicalFormula;
    }

    public String getDetailedAnalysis() {
        return detailedAnalysis;
    }

    public String getFormattedDistance() {
        if (distanceMeters < 0.1) {
            return "0.0 m (Chính máy này)";
        }
        return String.format("%.2f mét (Khoảng: %.1f - %.1f m)", distanceMeters, minDistanceMeters, maxDistanceMeters);
    }
}
