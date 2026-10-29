package com.wifiscan;

import com.wifiscan.model.DistanceEstimate;
import com.wifiscan.model.PortResult;
import com.wifiscan.model.WifiInfo;
import com.wifiscan.service.DistanceCalculator;
import com.wifiscan.service.PortDictionary;
import com.wifiscan.service.PortScanner;
import com.wifiscan.service.WifiDetector;
import com.wifiscan.ui.MainWindow;
import com.wifiscan.ui.Theme;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Điểm khởi chạy chính của ứng dụng WiFi Port Scanner & Distance Estimator.
 * Hỗ trợ cả giao diện đồ họa GUI hiện đại và chế độ Console CLI.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equalsIgnoreCase(args[0])) {
            runCliMode(args);
        } else {
            runGuiMode();
        }
    }

    private static void runGuiMode() {
        Theme.setupGlobalStyles();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}

            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }

    private static void runCliMode(String[] args) {
        System.out.println("=============================================================");
        System.out.println("   WI-FI PORT SCANNER & DISTANCE ESTIMATOR (CLI MODE)        ");
        System.out.println("=============================================================");

        WifiInfo wifi = WifiDetector.detectActiveWifi();
        System.out.println("[+] Card Wi-Fi: " + wifi.getInterfaceName() + " (" + wifi.getAdapterDescription() + ")");
        System.out.println("[+] SSID: " + wifi.getSsid() + " | RSSI: " + wifi.getRssiDbm() + " dBm (" + wifi.getSignalPercent() + "%)");
        System.out.println("[+] Scanner IP: " + wifi.getLocalIp() + " | Gateway: " + wifi.getGatewayIp());
        System.out.println("[+] Dải mạng Wi-Fi: " + wifi.getCidrNotation());
        System.out.println("-------------------------------------------------------------");

        String targetIp = wifi.getGatewayIp();
        if (args.length > 1) {
            targetIp = args[1];
        }

        try {
            WifiDetector.validateWifiTarget(wifi, targetIp);
        } catch (IllegalArgumentException e) {
            System.err.println("[!] LỖI BẢO VỆ: " + e.getMessage());
            return;
        }

        boolean isGateway = targetIp.equals(wifi.getGatewayIp());
        boolean isLocal = targetIp.equals(wifi.getLocalIp());

        System.out.println("[*] Đang tính toán khoảng cách tới: " + targetIp + "...");
        DistanceEstimate est = DistanceCalculator.calculateDistance(targetIp, isGateway, isLocal, wifi, null);
        System.out.println("    -> Khoảng cách ước tính: " + est.getFormattedDistance());
        System.out.println("    -> Vùng: " + est.getZoneDescription());
        System.out.println("    -> Độ tin cậy: " + est.getConfidenceLevel());
        System.out.println("    -> Phương pháp: " + est.getCalculationMethod());
        System.out.println("-------------------------------------------------------------");

        System.out.println("[*] Bắt đầu quét các cổng thông dụng trên " + targetIp + "...");
        List<Integer> ports = new ArrayList<>();
        for (int p : PortDictionary.COMMON_PORTS) ports.add(p);

        PortScanner scanner = new PortScanner();
        CountDownLatch latch = new CountDownLatch(1);
        scanner.scanPorts(targetIp, ports, 400, 20, true, wifi, new PortScanner.PortScanListener() {
            @Override
            public void onPortFound(PortResult result) {
                System.out.printf("  [OPEN] Cổng %d/%s (%s): %.2f ms | %s\n",
                        result.getPort(), result.getProtocol(), result.getServiceName(),
                        result.getLatencyMs(), result.getBanner());
            }

            @Override
            public void onProgress(int scanned, int total, double percent, double portsPerSec) {
                if (scanned % 5 == 0 || scanned == total) {
                    System.out.printf("\r  -> Tiến độ: %d/%d (%.1f%%) - Tốc độ: %.1f ports/s",
                            scanned, total, percent, portsPerSec);
                }
            }

            @Override
            public void onCompleted(List<PortResult> openPorts, int totalScanned, long durationMs) {
                System.out.println("\n-------------------------------------------------------------");
                System.out.printf("[+] Hoàn tất! Tìm thấy %d cổng mở trên tổng số %d cổng (%.2fs)\n",
                        openPorts.size(), totalScanned, durationMs / 1000.0);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                System.err.println("\n[!] Lỗi quét: " + message);
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException ignored) {}
    }
}
