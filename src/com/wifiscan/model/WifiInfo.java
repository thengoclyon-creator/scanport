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
        if (ipStr == null || ipStr.trim().isEmpty()) {
            return false;
        }
        ipStr = ipStr.trim();
        // Luôn chấp nhận Gateway hoặc Local IP hoặc localhost
        if (gatewayIp != null && gatewayIp.equals(ipStr)) {
            return true;
        }
        if (localIp.equals(ipStr) || ipStr.equals("127.0.0.1") || ipStr.equalsIgnoreCase("localhost")) {
            return true;
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

            // 1. Kiểm tra theo Subnet Mask hiện tại
            boolean maskMatch = true;
            for (int i = 0; i < targetBytes.length; i++) {
                if ((targetBytes[i] & maskBytes[i]) != (localBytes[i] & maskBytes[i])) {
                    maskMatch = false;
                    break;
                }
            }
            if (maskMatch) {
                return true;
            }

            // 2. Hỗ trợ mạng Wi-Fi trường học / doanh nghiệp dải rộng (/16 hoặc đa VLAN có Gateway chung)
            // Lấy Gateway làm tham chiếu thêm nếu có
            byte[] gwBytes = null;
            if (gatewayIp != null && !gatewayIp.equals("0.0.0.0")) {
                try {
                    gwBytes = InetAddress.getByName(gatewayIp).getAddress();
                } catch (Exception ignored) {}
            }

            if (targetBytes.length == 4) {
                int t0 = targetBytes[0] & 0xFF;
                int t1 = targetBytes[1] & 0xFF;
                int l0 = localBytes[0] & 0xFF;
                int l1 = localBytes[1] & 0xFF;

                // Dải 10.x.x.x (Class A Private - rất phổ biến ở đại học/trường học như 10.12.x.x)
                if (t0 == 10 && (l0 == 10 || (gwBytes != null && (gwBytes[0] & 0xFF) == 10))) {
                    if (t1 == l1 || (gwBytes != null && t1 == (gwBytes[1] & 0xFF))) {
                        return true;
                    }
                }

                // Dải 172.16.x.x - 172.31.x.x (Class B Private)
                if (t0 == 172 && (l0 == 172 || (gwBytes != null && (gwBytes[0] & 0xFF) == 172))) {
                    if (t1 == l1 || (gwBytes != null && t1 == (gwBytes[1] & 0xFF))) {
                        return true;
                    }
                }

                // Dải 192.168.x.x (Class C Private)
                if (t0 == 192 && t1 == 168 && (l0 == 192 && l1 == 168)) {
                    return true;
                }
            }

            return false;
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

    /**
     * Lấy địa chỉ mạng (Network Address) chuẩn theo Subnet Mask và Prefix Length.
     * Hỗ trợ chuẩn xác các dải mạng lớn như /16, /20, /22 trong Wi-Fi công cộng.
     */
    public String getNetworkAddress() {
        if (localIp == null || localIp.equals("0.0.0.0")) return "0.0.0.0";
        try {
            InetAddress local = InetAddress.getByName(localIp);
            byte[] ipBytes = local.getAddress();
            int prefix = prefixLength > 0 && prefixLength <= 32 ? prefixLength : 24;
            int mask = prefix == 0 ? 0 : 0xFFFFFFFF << (32 - prefix);
            byte[] netBytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                netBytes[i] = (byte)(ipBytes[i] & ((mask >>> (24 - i * 8)) & 0xFF));
            }
            return InetAddress.getByAddress(netBytes).getHostAddress();
        } catch (Exception e) {
            return getSubnetNetworkAddress() + "0";
        }
    }

    /**
     * Trả về định dạng CIDR, ví dụ: 10.12.0.0/16 hoặc 192.168.1.0/24
     */
    public String getCidrNotation() {
        return getNetworkAddress() + "/" + (prefixLength > 0 ? prefixLength : 24);
    }
}
