package com.wifiscan.model;

/**
 * Đại diện một điểm truy cập Wi-Fi (Access Point) được quét thấy trong không gian xung quanh.
 * Bao gồm dữ liệu thật từ netsh wlan show networks mode=bssid và kiểm tra captive portal.
 */
public class PublicWifiNetwork {

    private String ssid;
    private String bssid;
    private String networkType;      // Infrastructure / Ad hoc
    private String authentication;   // Open, WPA2-Personal, WPA3, WPA2-Enterprise, ...
    private String encryption;       // None, CCMP, TKIP
    private int signalPercent;       // 0-100%
    private String radioType;        // 802.11a/b/g/n/ac/ax
    private String band;             // 2.4 GHz / 5 GHz / 6 GHz
    private int channel;
    private int connectedStations;   // Số client đang kết nối (BSS Load)
    private int channelUtilization;  // % channel đang bị chiếm dụng (BSS Load)
    private String basicRates;
    private String otherRates;
    private boolean isOpen;          // true nếu Authentication = Open và Encryption = None
    private boolean hasCaptivePortal;// true nếu phát hiện captive portal (khi đã kết nối)
    private String captivePortalUrl; // URL của trang đăng nhập captive portal (nếu có)
    private String securityRating;   // "Nguy hiểm", "Cần thận", "An toàn"
    private boolean isCurrentNetwork;// true nếu đây là mạng đang kết nối
    private String gatewayIp;        // IP gateway nếu đang kết nối vào mạng này
    private int rssiDbm;             // RSSI tính từ Signal%

    public PublicWifiNetwork() {
        this.signalPercent = 0;
        this.channel = 0;
        this.connectedStations = -1;
        this.channelUtilization = -1;
        this.isOpen = false;
        this.hasCaptivePortal = false;
        this.captivePortalUrl = "";
        this.securityRating = "An toan";
        this.isCurrentNetwork = false;
        this.gatewayIp = "";
        this.rssiDbm = -100;
    }

    // --- Getters & Setters ---
    public String getSsid() { return ssid; }
    public void setSsid(String ssid) { this.ssid = (ssid == null || ssid.isEmpty()) ? "<An>  [SSID An]" : ssid; }

    public String getBssid() { return bssid; }
    public void setBssid(String bssid) { this.bssid = bssid; }

    public String getNetworkType() { return networkType; }
    public void setNetworkType(String networkType) { this.networkType = networkType; }

    public String getAuthentication() { return authentication; }
    public void setAuthentication(String auth) {
        this.authentication = auth;
        updateDerivedFields();
    }

    public String getEncryption() { return encryption; }
    public void setEncryption(String encryption) {
        this.encryption = encryption;
        updateDerivedFields();
    }

    public int getSignalPercent() { return signalPercent; }
    public void setSignalPercent(int signalPercent) {
        this.signalPercent = signalPercent;
        // Chuyển đổi Signal % -> dBm (Windows: 100% = -50dBm, 0% = -100dBm)
        this.rssiDbm = (signalPercent / 2) - 100;
    }

    public String getRadioType() { return radioType; }
    public void setRadioType(String radioType) { this.radioType = radioType; }

    public String getBand() { return band; }
    public void setBand(String band) { this.band = band; }

    public int getChannel() { return channel; }
    public void setChannel(int channel) { this.channel = channel; }

    public int getConnectedStations() { return connectedStations; }
    public void setConnectedStations(int connectedStations) { this.connectedStations = connectedStations; }

    public int getChannelUtilization() { return channelUtilization; }
    public void setChannelUtilization(int channelUtilization) { this.channelUtilization = channelUtilization; }

    public String getBasicRates() { return basicRates; }
    public void setBasicRates(String basicRates) { this.basicRates = basicRates; }

    public String getOtherRates() { return otherRates; }
    public void setOtherRates(String otherRates) { this.otherRates = otherRates; }

    public boolean isOpen() { return isOpen; }
    public boolean hasCaptivePortal() { return hasCaptivePortal; }
    public void setHasCaptivePortal(boolean hasCaptivePortal) { this.hasCaptivePortal = hasCaptivePortal; }

    public String getCaptivePortalUrl() { return captivePortalUrl; }
    public void setCaptivePortalUrl(String captivePortalUrl) { this.captivePortalUrl = captivePortalUrl; }

    public String getSecurityRating() { return securityRating; }
    public boolean isCurrentNetwork() { return isCurrentNetwork; }
    public void setCurrentNetwork(boolean currentNetwork) { isCurrentNetwork = currentNetwork; }

    public String getGatewayIp() { return gatewayIp; }
    public void setGatewayIp(String gatewayIp) { this.gatewayIp = gatewayIp; }

    public int getRssiDbm() { return rssiDbm; }

    /**
     * Cập nhật các trường phái sinh từ Authentication và Encryption.
     */
    private void updateDerivedFields() {
        if (authentication != null && encryption != null) {
            String authLower = authentication.toLowerCase();
            String encLower = encryption.toLowerCase();
            this.isOpen = (authLower.contains("open") || authLower.equals("mo") || authLower.equals("")) &&
                          (encLower.contains("none") || encLower.equals("khong") || encLower.equals(""));
            if (isOpen) {
                this.securityRating = "Nguy hiem";
            } else if (authLower.contains("wpa3")) {
                this.securityRating = "An toan";
            } else if (authLower.contains("wpa2")) {
                this.securityRating = "An toan";
            } else if (authLower.contains("wpa")) {
                this.securityRating = "Can than";
            } else if (authLower.contains("wep")) {
                this.securityRating = "Nguy hiem";
            } else {
                this.securityRating = "Can than";
            }
        }
    }

    /**
     * Chuỗi hiển thị chất lượng tín hiệu dạng thanh text.
     */
    public String getSignalBars() {
        if (signalPercent >= 80) return "[||||]  " + signalPercent + "%";
        if (signalPercent >= 60) return "[|||.]  " + signalPercent + "%";
        if (signalPercent >= 40) return "[||..]  " + signalPercent + "%";
        if (signalPercent >= 20) return "[|...]  " + signalPercent + "%";
        return "[....] " + signalPercent + "%";
    }

    /**
     * Mô tả bảo mật ngắn gọn.
     */
    public String getSecuritySummary() {
        if (isOpen) return "MO - Khong bao mat";
        if (authentication == null) return "Khong xac dinh";
        return authentication + (encryption != null ? " / " + encryption : "");
    }

    @Override
    public String toString() {
        return String.format("PublicWifiNetwork{SSID='%s', BSSID='%s', Auth='%s', Signal=%d%%, Channel=%d}",
                ssid, bssid, authentication, signalPercent, channel);
    }
}
