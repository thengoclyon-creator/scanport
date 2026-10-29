package com.wifiscan.model;

/**
 * Kết quả đo lường và ước tính khoảng cách hai chiều:
 * 1. Khoảng cách Client -> Máy Quét (Client to Scanner)
 * 2. Khoảng cách Client -> Bộ phát Wi-Fi (Client to Access Point Router)
 * Cùng các chỉ số đo đạc thực tế: RSSI, RTT, Jitter, chất lượng sóng.
 */
public class DistanceEstimate {

    // 1. Chỉ số Client -> Máy Quét (Scanner)
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

    // 2. Chỉ số Client -> AP Router (Access Point)
    private final double distanceToApMeters;
    private final double minDistanceToApMeters;
    private final double maxDistanceToApMeters;
    private final int apRssiDbm;
    private final int apSignalPercent;
    private final double rttToApMs;

    // Constructor đầy đủ cả 2 chiều khoảng cách (Scanner và AP)
    public DistanceEstimate(double distanceMeters, double minDistanceMeters, double maxDistanceMeters,
                            String zoneDescription, String calculationMethod, String confidenceLevel,
                            int rssiDbm, double rttMs, double pathLossExponent, double referencePowerA,
                            String mathematicalFormula, String detailedAnalysis,
                            double distanceToApMeters, double minDistanceToApMeters, double maxDistanceToApMeters,
                            int apRssiDbm, int apSignalPercent, double rttToApMs) {
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
        this.distanceToApMeters = distanceToApMeters;
        this.minDistanceToApMeters = minDistanceToApMeters;
        this.maxDistanceToApMeters = maxDistanceToApMeters;
        this.apRssiDbm = apRssiDbm;
        this.apSignalPercent = apSignalPercent;
        this.rttToApMs = rttToApMs;
    }

    // Constructor tương thích ngược (12 tham số)
    public DistanceEstimate(double distanceMeters, double minDistanceMeters, double maxDistanceMeters,
                            String zoneDescription, String calculationMethod, String confidenceLevel,
                            int rssiDbm, double rttMs, double pathLossExponent, double referencePowerA,
                            String mathematicalFormula, String detailedAnalysis) {
        this(distanceMeters, minDistanceMeters, maxDistanceMeters,
             zoneDescription, calculationMethod, confidenceLevel,
             rssiDbm, rttMs, pathLossExponent, referencePowerA,
             mathematicalFormula, detailedAnalysis,
             distanceMeters, minDistanceMeters, maxDistanceMeters,
             rssiDbm, Math.max(10, Math.min(100, 2 * (rssiDbm + 100))), rttMs);
    }

    // Getters cho chiều Client -> Máy Quét
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

    // Getters cho chiều Client -> Bộ phát Wi-Fi (AP Router)
    public double getDistanceToApMeters() {
        return distanceToApMeters;
    }

    public double getMinDistanceToApMeters() {
        return minDistanceToApMeters;
    }

    public double getMaxDistanceToApMeters() {
        return maxDistanceToApMeters;
    }

    public int getApRssiDbm() {
        return apRssiDbm;
    }

    public int getApSignalPercent() {
        return apSignalPercent;
    }

    public double getRttToApMs() {
        return rttToApMs;
    }

    public String getFormattedDistanceToAp() {
        if (distanceToApMeters < 0.1) {
            return "0.0 m (Chính là AP Router)";
        }
        return String.format("%.2f mét (Khoảng: %.1f - %.1f m)", distanceToApMeters, minDistanceToApMeters, maxDistanceToApMeters);
    }

    public String getApSignalQuality() {
        if (apSignalPercent >= 80) return "Rất tốt (Sóng mạnh - Gần AP)";
        if (apSignalPercent >= 60) return "Tốt (Ổn định - Cùng phòng AP)";
        if (apSignalPercent >= 40) return "Trung bình (Có vật cản / Tường mỏng)";
        return "Yếu (Khoảng cách xa / Rìa vùng phủ)";
    }
}
