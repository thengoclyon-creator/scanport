package com.wifiscan.model;

/**
 * Đại diện cho một thiết bị phát hiện được trên mạng Wi-Fi.
 */
public class NetworkDevice {

    public enum DeviceType {
        GATEWAY("Bộ phát Wi-Fi (Router/AP)"),
        LOCAL_SCANNER("Máy Scanner hiện tại"),
        PEER_CLIENT("Thiết bị Client Wi-Fi");

        private final String label;
        DeviceType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final String ipAddress;
    private String hostName;
    private String macAddress;
    private DeviceType deviceType;
    private double rttMs;
    private int ttl;
    private DistanceEstimate distanceEstimate;

    public NetworkDevice(String ipAddress, String hostName, String macAddress, DeviceType deviceType) {
        this.ipAddress = ipAddress;
        this.hostName = hostName != null ? hostName : ipAddress;
        this.macAddress = macAddress != null ? macAddress : "Chưa rõ";
        this.deviceType = deviceType;
        this.rttMs = 0.0;
        this.ttl = 64;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public double getRttMs() {
        return rttMs;
    }

    public void setRttMs(double rttMs) {
        this.rttMs = rttMs;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public DistanceEstimate getDistanceEstimate() {
        return distanceEstimate;
    }

    public void setDistanceEstimate(DistanceEstimate distanceEstimate) {
        this.distanceEstimate = distanceEstimate;
    }

    public boolean isGateway() {
        return deviceType == DeviceType.GATEWAY;
    }

    public boolean isLocalHost() {
        return deviceType == DeviceType.LOCAL_SCANNER;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - %s", ipAddress, hostName, deviceType.getLabel());
    }
}
