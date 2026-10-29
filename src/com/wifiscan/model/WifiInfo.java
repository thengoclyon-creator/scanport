package com.wifiscan.model;

import java.net.InetAddress;

/**
 * Thông tin chi tiết của card mạng Wi-Fi và kết nối hiện tại.
 */
public class WifiInfo {
    private boolean connected;
    private String interfaceName;
    private String adapterDescription;
    private String ssid;
    private String bssid;
    private int signalPercent;
    private int rssiDbm;
    private String band; // "2.4 GHz", "5 GHz", ...
    private int channel;
    private String radioType;
    private String localIp;
    private String subnetMask;
    private int prefixLength;
    private String gatewayIp;

    public WifiInfo() {
        this.connected = false;
        this.interfaceName = "Wi-Fi";
        this.adapterDescription = "Không tìm thấy";
        this.ssid = "Chưa kết nối";
        this.bssid = "-";
        this.signalPercent = 0;
        this.rssiDbm = -100;
        this.band = "5 GHz";
        this.channel = 0;
        this.radioType = "802.11";
        this.localIp = "0.0.0.0";
        this.subnetMask = "255.255.255.0";
        this.prefixLength = 24;
        this.gatewayIp = "0.0.0.0";
    }

    /**
     * Kiểm tra xem một địa chỉ IP có nằm trong cùng dải mạng con Wi-Fi này không.
     */
    public boolean isIpInWifiSubnet(String ipStr) {
        if (!connected || localIp == null || localIp.equals("0.0.0.0")) {
            return false;
        }
        try {
            InetAddress target = InetAddress.getByName(ipStr);
            InetAddress local = InetAddress.getByName(localIp);
            InetAddress mask = InetAddress.getByName(subnetMask);

            byte[] targetBytes = target.getAddress();
            byte[] localBytes = local.getAddress();
            byte[] maskBytes = mask.getAddress();

            if (targetBytes.length != localBytes.length) {
                return false;
            }

            for (int i = 0; i < targetBytes.length; i++) {
                if ((targetBytes[i] & maskBytes[i]) != (localBytes[i] & maskBytes[i])) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Getters and Setters
    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getAdapterDescription() {
        return adapterDescription;
    }

    public void setAdapterDescription(String adapterDescription) {
        this.adapterDescription = adapterDescription;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getBssid() {
        return bssid;
    }

    public void setBssid(String bssid) {
        this.bssid = bssid;
    }

    public int getSignalPercent() {
        return signalPercent;
    }

    public void setSignalPercent(int signalPercent) {
        this.signalPercent = signalPercent;
    }

    public int getRssiDbm() {
        return rssiDbm;
    }

    public void setRssiDbm(int rssiDbm) {
        this.rssiDbm = rssiDbm;
    }

    public String getBand() {
        return band;
    }

    public void setBand(String band) {
        this.band = band;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public String getRadioType() {
        return radioType;
    }

    public void setRadioType(String radioType) {
        this.radioType = radioType;
    }

    public String getLocalIp() {
        return localIp;
    }

    public void setLocalIp(String localIp) {
        this.localIp = localIp;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    public String getGatewayIp() {
        return gatewayIp;
    }

    public void setGatewayIp(String gatewayIp) {
        this.gatewayIp = gatewayIp;
    }

    public String getSubnetNetworkAddress() {
        if (localIp == null || localIp.equals("0.0.0.0")) return "0.0.0.0";
        try {
            String[] ipParts = localIp.split("\\.");
            return ipParts[0] + "." + ipParts[1] + "." + ipParts[2] + ".";
        } catch (Exception e) {
            return "192.168.1.";
        }
    }
}
