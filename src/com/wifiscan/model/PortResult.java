package com.wifiscan.model;

/**
 * Kết quả chi tiết của một cổng sau khi quét.
 */
public class PortResult {
    private final int port;
    private final String protocol;
    private final PortState state;
    private final String serviceName;
    private final double latencyMs;
    private final String banner;

    public PortResult(int port, String protocol, PortState state, String serviceName, double latencyMs, String banner) {
        this.port = port;
        this.protocol = protocol;
        this.state = state;
        this.serviceName = serviceName;
        this.latencyMs = latencyMs;
        this.banner = banner;
    }

    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public PortState getState() {
        return state;
    }

    public String getServiceName() {
        return serviceName;
    }

    public double getLatencyMs() {
        return latencyMs;
    }

    public String getBanner() {
        return banner != null && !banner.isEmpty() ? banner : "-";
    }

    @Override
    public String toString() {
        return String.format("Port %d/%s [%s]: %s (%.2f ms) %s",
                port, protocol, state, serviceName, latencyMs, getBanner());
    }
}
