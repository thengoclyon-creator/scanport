package com.wifiscan.service;

import com.wifiscan.model.PublicWifiNetwork;
import com.wifiscan.model.WifiInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quét và thu thập dữ liệu thật từ tất cả điểm truy cập Wi-Fi xung quanh (Public Wi-Fi Scanner).
 *
 * <p>Dữ liệu thu thập thật sự từ:
 * <ul>
 *   <li>{@code netsh wlan show networks mode=bssid} – liệt kê SSID, BSSID, Auth, Encryption,
 *       Signal, Band, Channel, BSS Load (số client, channel utilization)</li>
 *   <li>{@code netsh wlan show interfaces} – xác định mạng đang kết nối</li>
 *   <li>Kiểm tra Captive Portal: HTTP request tới 204-endpoint chuẩn (connectivitycheck)</li>
 * </ul>
 */
public class PublicWifiScanner {

    /** Callback để báo tiến trình và kết quả quét */
    public interface ScanListener {
        void onNetworkFound(PublicWifiNetwork network);
        void onProgress(int percent, String message);
        void onCaptivePortalChecked(PublicWifiNetwork network);
        void onComplete(List<PublicWifiNetwork> allNetworks, String summary);
        void onError(String error);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Kiểm tra xem scanner đang chạy không */
    public boolean isRunning() { return running.get(); }

    /** Dừng quét */
    public void stop() { running.set(false); }

    /**
     * Bắt đầu quét không đồng bộ. Gọi callback trên luồng nền, cần invokeLater cho Swing.
     */
    public void scan(WifiInfo currentWifi, ScanListener listener) {
        if (running.getAndSet(true)) {
            listener.onError("Scanner dang chay! Vui long doi...");
            return;
        }

        Thread t = new Thread(() -> {
            try {
                runScan(currentWifi, listener);
            } finally {
                running.set(false);
            }
        }, "PublicWifi-Scanner");
        t.setDaemon(true);
        t.start();
    }

    private void runScan(WifiInfo currentWifi, ScanListener listener) {
        listener.onProgress(5, "Dang quet cac diem truy cap Wi-Fi xung quanh...");

        List<PublicWifiNetwork> networks = new ArrayList<>();

        // Lấy SSID đang kết nối (để đánh dấu mạng hiện tại)
        String currentSsid = (currentWifi != null && currentWifi.getSsid() != null)
                ? currentWifi.getSsid().trim() : "";
        String gatewayIp = (currentWifi != null) ? currentWifi.getGatewayIp() : "";

        try {
            // Bước 1: Chạy netsh wlan show networks mode=bssid
            List<PublicWifiNetwork> parsed = parseNetshNetworks(currentSsid, gatewayIp);
            listener.onProgress(40, "Tim thay " + parsed.size() + " diem truy cap...");

            if (!running.get()) {
                listener.onComplete(networks, "Quet bi huy.");
                return;
            }

            // Bước 2: Phát hiện từng mạng và báo listener
            for (int i = 0; i < parsed.size(); i++) {
                if (!running.get()) break;
                PublicWifiNetwork net = parsed.get(i);
                networks.add(net);
                listener.onNetworkFound(net);
                int pct = 40 + (int) ((double) i / parsed.size() * 40);
                listener.onProgress(pct, "Dang xu ly: " + safeSsid(net.getSsid()));
            }

            if (!running.get()) {
                listener.onComplete(networks, "Quet bi huy.");
                return;
            }

            // Bước 3: Kiểm tra Captive Portal cho mạng HIỆN TẠI đang kết nối
            listener.onProgress(82, "Dang kiem tra Captive Portal mang hien tai...");
            for (PublicWifiNetwork net : networks) {
                if (!running.get()) break;
                if (net.isCurrentNetwork()) {
                    checkCaptivePortal(net);
                    listener.onCaptivePortalChecked(net);
                    break;
                }
            }

            if (!running.get()) {
                listener.onComplete(networks, "Quet bi huy.");
                return;
            }

            // Bước 4: Kiểm tra khả năng kết nối cổng phổ biến của gateway
            listener.onProgress(92, "Dang kiem tra kha nang ket noi gateway...");
            if (!currentSsid.isEmpty() && !gatewayIp.isEmpty() && !gatewayIp.equals("0.0.0.0")) {
                for (PublicWifiNetwork net : networks) {
                    if (net.isCurrentNetwork()) {
                        checkGatewayPorts(net, gatewayIp);
                        break;
                    }
                }
            }

            // Tổng kết
            long openCount = networks.stream().filter(PublicWifiNetwork::isOpen).count();
            long wpa2Count = networks.stream()
                    .filter(n -> n.getAuthentication() != null && n.getAuthentication().contains("WPA2")).count();
            long wpa3Count = networks.stream()
                    .filter(n -> n.getAuthentication() != null && n.getAuthentication().contains("WPA3")).count();

            String summary = String.format(
                "Ket qua: %d mang | %d mang MO (nguy hiem) | %d WPA2 | %d WPA3 | %d mang khac",
                networks.size(), openCount, wpa2Count, wpa3Count,
                networks.size() - openCount - wpa2Count - wpa3Count
            );

            listener.onProgress(100, "Hoan tat quet Wi-Fi cong cong!");
            listener.onComplete(networks, summary);

        } catch (Exception e) {
            listener.onError("Loi khi quet: " + e.getMessage());
            running.set(false);
        }
    }

    /**
     * Phân tích đầu ra của {@code netsh wlan show networks mode=bssid}.
     * Hỗ trợ cả đầu ra tiếng Anh và tiếng Việt của Windows.
     */
    private List<PublicWifiNetwork> parseNetshNetworks(String currentSsid, String gatewayIp) throws IOException {
        List<PublicWifiNetwork> result = new ArrayList<>();

        // Thử UTF-8 trước, fallback sang cp1252 / OEM encoding nếu cần
        Charset[] charsets = {StandardCharsets.UTF_8, Charset.forName("windows-1252"), Charset.forName("IBM850")};

        String output = null;
        for (Charset cs : charsets) {
            try {
                output = runCommand(cs, "netsh", "wlan", "show", "networks", "mode=bssid");
                if (output != null && (output.contains("SSID") || output.contains("Authentication"))) break;
            } catch (Exception ignored) {}
        }

        if (output == null || output.trim().isEmpty()) return result;

        // Tách từng khối SSID (mỗi khối bắt đầu bằng "SSID N :")
        String[] lines = output.split("\\r?\\n");

        PublicWifiNetwork current = null;
        String currentBssidBlock = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Phát hiện dòng SSID chính (không phải BSSID)
            if ((line.startsWith("SSID") && !line.startsWith("BSSID") && line.contains(":") &&
                    !line.toUpperCase().startsWith("BSSID"))) {

                // Nếu có mạng trước đó chưa lưu -> thêm vào list
                if (current != null) {
                    finalizeNetwork(current, currentSsid, gatewayIp);
                    result.add(current);
                }

                current = new PublicWifiNetwork();
                currentBssidBlock = null;

                String val = extractValue(line);
                // Loại bỏ prefix "N :" nếu có (e.g. "SSID 1 : Tên mạng")
                val = val.replaceFirst("^\\d+\\s*:\\s*", "").trim();
                current.setSsid(val);
                continue;
            }

            if (current == null) continue;

            // Các trường chung (áp dụng cho cả mạng lẫn BSSID block)
            String lineLower = line.toLowerCase();

            if (lineLower.startsWith("network type") || lineLower.startsWith("loai mang")) {
                current.setNetworkType(extractValue(line));
            } else if (lineLower.startsWith("authentication") || lineLower.startsWith("xac thuc") || lineLower.startsWith("lo\u1ea1i xác")) {
                current.setAuthentication(extractValue(line));
            } else if (lineLower.startsWith("encryption") || lineLower.startsWith("ma hoa") || lineLower.startsWith("mã hóa")) {
                current.setEncryption(extractValue(line));
            } else if (lineLower.startsWith("bssid")) {
                // Bắt đầu block BSSID mới – chỉ lưu BSSID đầu tiên
                String bssidVal = extractValue(line);
                if (current.getBssid() == null || current.getBssid().isEmpty()) {
                    current.setBssid(bssidVal);
                }
                currentBssidBlock = bssidVal;
            } else if (lineLower.startsWith("signal") || lineLower.startsWith("tin hieu") || lineLower.startsWith("tín hiệu")) {
                // Lấy signal của BSSID đầu tiên
                if (currentBssidBlock != null && currentBssidBlock.equals(current.getBssid())) {
                    String sig = extractValue(line).replace("%", "").trim();
                    try { current.setSignalPercent(Integer.parseInt(sig)); } catch (Exception ignored) {}
                }
            } else if (lineLower.startsWith("radio type") || lineLower.startsWith("loai vo tuyen")) {
                if (currentBssidBlock != null && currentBssidBlock.equals(current.getBssid())) {
                    current.setRadioType(extractValue(line));
                }
            } else if (lineLower.startsWith("band") || lineLower.startsWith("dai tan") || lineLower.startsWith("dải")) {
                if (currentBssidBlock != null && currentBssidBlock.equals(current.getBssid())) {
                    current.setBand(extractValue(line));
                }
            } else if (lineLower.startsWith("channel") || lineLower.startsWith("kenh") || lineLower.startsWith("kênh")) {
                if (currentBssidBlock != null && currentBssidBlock.equals(current.getBssid())) {
                    try { current.setChannel(Integer.parseInt(extractValue(line))); } catch (Exception ignored) {}
                }
            } else if (lineLower.contains("connected stations") || lineLower.contains("tram da ket noi")) {
                try { current.setConnectedStations(Integer.parseInt(extractValue(line).trim())); } catch (Exception ignored) {}
            } else if (lineLower.contains("channel utilization") || lineLower.contains("su dung kenh")) {
                String util = extractValue(line).trim();
                // Format: "241 (94 %)" – lấy con số trong ngoặc
                try {
                    if (util.contains("(")) {
                        String pct = util.replaceAll(".*\\((\\d+).*\\).*", "$1");
                        current.setChannelUtilization(Integer.parseInt(pct));
                    } else {
                        current.setChannelUtilization(Integer.parseInt(util.split("\\s")[0]));
                    }
                } catch (Exception ignored) {}
            } else if (lineLower.startsWith("basic rates")) {
                current.setBasicRates(extractValue(line));
            } else if (lineLower.startsWith("other rates")) {
                current.setOtherRates(extractValue(line));
            }
        }

        // Thêm mạng cuối cùng
        if (current != null) {
            finalizeNetwork(current, currentSsid, gatewayIp);
            result.add(current);
        }

        return result;
    }

    private void finalizeNetwork(PublicWifiNetwork net, String currentSsid, String gatewayIp) {
        if (!currentSsid.isEmpty() && currentSsid.equalsIgnoreCase(safeSsid(net.getSsid()))) {
            net.setCurrentNetwork(true);
            net.setGatewayIp(gatewayIp);
        }
    }

    /**
     * Kiểm tra Captive Portal theo chuẩn HTTP 204 (giống Android, iOS, Windows).
     * URL: http://connectivitycheck.gstatic.com/generate_204
     */
    public void checkCaptivePortal(PublicWifiNetwork network) {
        String[] checkUrls = {
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://www.msftconnecttest.com/connecttest.txt",
            "http://captive.apple.com/hotspot-detect.html"
        };

        for (String urlStr : checkUrls) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                int code = conn.getResponseCode();
                String location = conn.getHeaderField("Location");
                conn.disconnect();

                if (code == 302 || code == 301 || code == 307) {
                    // Bị redirect -> có captive portal
                    network.setHasCaptivePortal(true);
                    network.setCaptivePortalUrl(location != null ? location : urlStr);
                    return;
                } else if (code == 200 && urlStr.contains("apple")) {
                    // Apple endpoint: nếu trả 200 mà không phải success body
                    network.setHasCaptivePortal(true);
                    network.setCaptivePortalUrl(urlStr);
                    return;
                } else if (code == 204 || code == 200) {
                    // Kết nối thông -> không có captive portal
                    network.setHasCaptivePortal(false);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Kiểm tra một số cổng phổ biến của Gateway (Router).
     */
    private void checkGatewayPorts(PublicWifiNetwork net, String gatewayIp) {
        // Chỉ kiểm tra các cổng phổ biến của router
        int[] ports = {80, 443, 8080, 8443};
        StringBuilder openPorts = new StringBuilder();
        for (int port : ports) {
            if (!running.get()) break;
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress(gatewayIp, port), 600);
                openPorts.append(port).append(" ");
            } catch (Exception ignored) {}
        }
        // Lưu kết quả vào captive portal URL field tạm (vì đây là mạng hiện tại)
        if (openPorts.length() > 0) {
            net.setCaptivePortalUrl("Gateway " + gatewayIp + " ports open: [" + openPorts.toString().trim() + "]");
        }
    }

    // -------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------

    private String extractValue(String line) {
        int idx = line.indexOf(":");
        if (idx >= 0 && idx < line.length() - 1) {
            return line.substring(idx + 1).trim();
        }
        return line.trim();
    }

    private String safeSsid(String ssid) {
        if (ssid == null) return "";
        return ssid.replace("<An>  [SSID An]", "").trim();
    }

    private String runCommand(Charset charset, String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), charset))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        proc.waitFor();
        return sb.toString();
    }
}
