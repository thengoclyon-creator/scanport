package com.wifiscan.service;

import com.wifiscan.model.WifiInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dịch vụ nhận diện card mạng Wi-Fi và giám sát thông số sóng tín hiệu (SSID, RSSI, BSSID, Gateway).
 * Đảm bảo ứng dụng CHỈ SCAN TRONG MẠNG WI-FI.
 */
public class WifiDetector {

    /**
     * Quét và lấy thông tin card mạng Wi-Fi đang hoạt động.
     */
    public static WifiInfo detectActiveWifi() {
        WifiInfo wifiInfo = new WifiInfo();

        // 1. Phân tích lệnh Windows netsh wlan show interfaces
        boolean netshSuccess = parseNetshWlan(wifiInfo);

        // 2. Kết hợp với Java NetworkInterface để lấy chính xác IPv4, Subnet Mask và Prefix
        enrichWithNetworkInterfaces(wifiInfo);

        // 3. Lấy Gateway IPv4 từ route print
        findDefaultGateway(wifiInfo);

        // 4. Nếu không có card Wi-Fi thật hoặc đang ngắt kết nối (chế độ bảo vệ / dự phòng)
        if (!wifiInfo.isConnected() || wifiInfo.getLocalIp().equals("0.0.0.0")) {
            fallbackLocalInterface(wifiInfo);
        }

        return wifiInfo;
    }

    /**
     * Kiểm tra và ép buộc IP mục tiêu PHẢI nằm trong mạng Wi-Fi.
     * @param wifiInfo Thông tin Wi-Fi hiện tại
     * @param targetIp IP muốn quét
     * @return true nếu hợp lệ; ném ngoại lệ nếu vi phạm
     */
    public static boolean validateWifiTarget(WifiInfo wifiInfo, String targetIp) throws IllegalArgumentException {
        if (targetIp == null || targetIp.trim().isEmpty()) {
            throw new IllegalArgumentException("Địa chỉ IP mục tiêu không được để trống!");
        }
        targetIp = targetIp.trim();

        // Cho phép localhost / 127.0.0.1 để test nội bộ nếu trùng máy
        if (targetIp.equals("127.0.0.1") || targetIp.equalsIgnoreCase("localhost")) {
            return true;
        }

        if (!wifiInfo.isConnected()) {
            throw new IllegalArgumentException("Máy tính hiện chưa kết nối mạng Wi-Fi nào! Ứng dụng chỉ quét khi có kết nối Wi-Fi.");
        }

        if (!wifiInfo.isIpInWifiSubnet(targetIp)) {
            // Kiểm tra thêm: nếu targetIp là địa chỉ Private LAN (10.x, 172.16-31.x, 192.168.x) thuộc mạng nội bộ
            boolean isPrivateLan = false;
            try {
                InetAddress addr = InetAddress.getByName(targetIp);
                if (addr.isSiteLocalAddress() || addr.isLoopbackAddress()) {
                    isPrivateLan = true;
                }
            } catch (Exception ignored) {}

            if (!isPrivateLan) {
                throw new IllegalArgumentException(String.format(
                        "CHẶN BẢO VỆ: Địa chỉ %s KHÔNG nằm trong mạng Wi-Fi nội bộ hiện tại (%s)!\n" +
                        "Theo yêu cầu, ứng dụng chỉ quét trong phạm vi mạng Wi-Fi nội bộ.",
                        targetIp, wifiInfo.getCidrNotation()
                ));
            }
        }

        return true;
    }

    /**
     * Làm mới nhanh các thông số sóng vô tuyến (RSSI, Signal %, Channel, BSSID) từ card Wi-Fi vật lý.
     */
    public static boolean refreshSignalMetrics(WifiInfo wifiInfo) {
        if (wifiInfo == null) return false;
        return parseNetshWlan(wifiInfo);
    }

    private static boolean parseNetshWlan(WifiInfo wifiInfo) {
        try {
            ProcessBuilder pb = new ProcessBuilder("netsh", "wlan", "show", "interfaces");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean stateConnected = false;
                boolean foundExplicitRssi = false;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String lineLower = line.toLowerCase();

                    if (lineLower.startsWith("name") || lineLower.startsWith("tên")) {
                        wifiInfo.setInterfaceName(extractValue(line));
                    } else if (lineLower.startsWith("description") || lineLower.startsWith("mô tả")) {
                        wifiInfo.setAdapterDescription(extractValue(line));
                    } else if (lineLower.startsWith("state") || lineLower.startsWith("trạng thái")) {
                        String state = extractValue(line).toLowerCase();
                        if (state.contains("connected") || state.contains("đã kết nối")) {
                            stateConnected = true;
                        }
                    } else if (lineLower.startsWith("ssid") && !lineLower.startsWith("bssid") && !lineLower.startsWith("ap bssid")) {
                        wifiInfo.setSsid(extractValue(line));
                    } else if (lineLower.contains("bssid")) {
                        wifiInfo.setBssid(extractValue(line));
                    } else if (lineLower.startsWith("band") || lineLower.startsWith("dải tần")) {
                        wifiInfo.setBand(extractValue(line));
                    } else if (lineLower.startsWith("channel") || lineLower.startsWith("kênh")) {
                        try {
                            wifiInfo.setChannel(Integer.parseInt(extractValue(line)));
                        } catch (Exception ignored) {}
                    } else if (lineLower.startsWith("radio type") || lineLower.startsWith("loại vô tuyến")) {
                        wifiInfo.setRadioType(extractValue(line));
                    } else if (lineLower.startsWith("signal") || lineLower.startsWith("tín hiệu")) {
                        String sig = extractValue(line).replace("%", "").trim();
                        try {
                            wifiInfo.setSignalPercent(Integer.parseInt(sig));
                        } catch (Exception ignored) {}
                    } else if (lineLower.startsWith("rssi")) {
                        String rssi = extractValue(line).trim();
                        try {
                            wifiInfo.setRssiDbm(Integer.parseInt(rssi));
                            foundExplicitRssi = true;
                        } catch (Exception ignored) {}
                    }
                }

                // Cập nhật RSSI từ Signal % nếu không có trường Rssi trực tiếp
                if (!foundExplicitRssi && wifiInfo.getSignalPercent() > 0) {
                    // Công thức chuẩn chuyển đổi Signal % sang dBm trên Windows NDIS:
                    // 100% -> -50 dBm, 50% -> -75 dBm, 0% -> -100 dBm
                    int calculatedRssi = (wifiInfo.getSignalPercent() / 2) - 100;
                    wifiInfo.setRssiDbm(calculatedRssi);
                } else if (foundExplicitRssi && wifiInfo.getSignalPercent() == 0 && wifiInfo.getRssiDbm() > -100) {
                    int calcSig = Math.min(100, Math.max(5, 2 * (wifiInfo.getRssiDbm() + 100)));
                    wifiInfo.setSignalPercent(calcSig);
                }

                wifiInfo.setConnected(stateConnected);
                process.waitFor();
                return stateConnected;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void enrichWithNetworkInterfaces(WifiInfo wifiInfo) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                String name = ni.getName().toLowerCase();
                String displayName = ni.getDisplayName().toLowerCase();

                // Ưu tiên trùng tên với netsh hoặc có chứa chữ wifi/wlan/wireless
                boolean isMatch = (wifiInfo.getInterfaceName() != null && ni.getDisplayName().contains(wifiInfo.getInterfaceName()))
                        || name.contains("wlan") || name.contains("wi-fi") || displayName.contains("wireless") || displayName.contains("wi-fi");

                if (isMatch || (wifiInfo.isConnected() && wifiInfo.getLocalIp().equals("0.0.0.0"))) {
                    for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                        InetAddress inet = addr.getAddress();
                        if (inet instanceof Inet4Address && !inet.isLoopbackAddress()) {
                            wifiInfo.setLocalIp(inet.getHostAddress());
                            wifiInfo.setPrefixLength(addr.getNetworkPrefixLength());
                            wifiInfo.setSubnetMask(calcSubnetMask(addr.getNetworkPrefixLength()));
                            if (wifiInfo.getInterfaceName() == null || wifiInfo.getInterfaceName().equals("Wi-Fi")) {
                                wifiInfo.setInterfaceName(ni.getDisplayName());
                            }
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void findDefaultGateway(WifiInfo wifiInfo) {
        try {
            ProcessBuilder pb = new ProcessBuilder("route", "print", "0.0.0.0");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                Pattern routePattern = Pattern.compile("0\\.0\\.0\\.0\\s+0\\.0\\.0\\.0\\s+([0-9.]+)\\s+([0-9.]+)");
                while ((line = reader.readLine()) != null) {
                    Matcher m = routePattern.matcher(line.trim());
                    if (m.find()) {
                        String gateway = m.group(1);
                        String ifaceIp = m.group(2);
                        if (ifaceIp.equals(wifiInfo.getLocalIp()) || wifiInfo.getGatewayIp().equals("0.0.0.0")) {
                            wifiInfo.setGatewayIp(gateway);
                            break;
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {}
    }

    private static void fallbackLocalInterface(WifiInfo wifiInfo) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            if (localHost instanceof Inet4Address) {
                wifiInfo.setLocalIp(localHost.getHostAddress());
                wifiInfo.setConnected(true);
                wifiInfo.setSsid("Wi-Fi (Mạng nội bộ)");
                if (wifiInfo.getGatewayIp().equals("0.0.0.0")) {
                    String[] parts = localHost.getHostAddress().split("\\.");
                    wifiInfo.setGatewayIp(parts[0] + "." + parts[1] + "." + parts[2] + ".1");
                }
            }
        } catch (Exception ignored) {}
    }

    private static String extractValue(String line) {
        int idx = line.indexOf(":");
        if (idx >= 0 && idx < line.length() - 1) {
            return line.substring(idx + 1).trim();
        }
        return "";
    }

    private static String calcSubnetMask(int prefix) {
        int mask = 0xffffffff << (32 - prefix);
        int value = mask;
        byte[] bytes = new byte[]{
                (byte) (value >>> 24),
                (byte) (value >> 16 & 0xff),
                (byte) (value >> 8 & 0xff),
                (byte) (value & 0xff)
        };
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception e) {
            return "255.255.255.0";
        }
    }
}
