package com.wifiscan.service;

import com.wifiscan.model.NetworkDevice;
import com.wifiscan.model.WifiInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LanScanner v3 – Quét TOÀN BỘ thiết bị Wi-Fi siêu nhanh và chuẩn xác:
 *
 * 1. Đọc bảng ARP hệ thống ngay lập tức (phát hiện thiết bị đã kết nối).
 * 2. Kích hoạt ARP flood đồng thời toàn dải mạng qua ICMP song song.
 * 3. TCP Connect Probe nhanh trên 4 cổng cốt lõi (80, 445, 135, 139) với timeout 90ms.
 * 4. 120 luồng đồng thời: quét xong toàn bộ dải /24 chỉ trong 1-2 giây.
 * 5. Tự động giải tên thiết bị NetBIOS/DNS nhanh mà không bị lỗi font hay dùng emoji vỡ hình.
 */
public class LanScanner {

    // 4 cổng chủ chốt đại diện cho mọi hệ sinh thái thiết bị LAN:
    // 80 (Router, Web, Camera), 445 (Windows SMB/NAS), 135 (Windows RPC), 139 (NetBIOS)
    private static final int[] PROBE_PORTS = { 80, 445, 135, 139 };

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ExecutorService executor;

    public interface ScanProgressListener {
        void onProgress(int current, int total, double percent, String statusMsg);
        void onDeviceFound(NetworkDevice device);
        void onCompleted(List<NetworkDevice> devices);
        void onError(String message);
    }

    public void stopScan() {
        isRunning.set(false);
        if (executor != null) executor.shutdownNow();
    }

    public boolean isScanning() {
        return isRunning.get();
    }

    /**
     * Quét toàn bộ dải mạng Wi-Fi với tốc độ cao.
     */
    public void scanWifiSubnet(WifiInfo wifiInfo, ScanProgressListener listener) {
        if (isRunning.get()) {
            if (listener != null) listener.onError("Quá trình quét mạng đang diễn ra!");
            return;
        }
        isRunning.set(true);

        int threadCount = 120;
        executor = Executors.newFixedThreadPool(threadCount);

        CompletableFuture.runAsync(() -> {
            try {
                List<String> ipList = buildIpList(wifiInfo);
                String gatewayIp = wifiInfo.getGatewayIp();
                String localIp   = wifiInfo.getLocalIp();
                int total = ipList.size();

                notifyProgress(listener, 0, total, 0.0, "Đang khởi tạo danh sách IP và bảng ARP...");

                // Đọc ARP ban đầu
                Map<String, String> arpTable = loadArpTable();

                // Gửi ICMP flood nhanh kích hoạt ARP cache của hệ điều hành
                sendIcmpFloodAsync(ipList, executor);

                // Nghỉ rất ngắn 100ms để OS điền phản hồi ARP
                Thread.sleep(100);

                // Đọc lại ARP để thu thập thêm
                arpTable.putAll(loadArpTable());

                notifyProgress(listener, 0, total, 0.0,
                    String.format("Bắt đầu quét đa luồng %d IP...", total));

                List<NetworkDevice> foundDevices = new CopyOnWriteArrayList<>();
                AtomicInteger completedCount = new AtomicInteger(0);
                Set<String> discovered = ConcurrentHashMap.newKeySet();

                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (String targetIp : ipList) {
                    if (!isRunning.get()) break;

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        if (!isRunning.get()) return;

                        boolean isAlive = false;
                        double rtt = 0.0;

                        try {
                            if (targetIp.equals(localIp)) {
                                isAlive = true;
                                rtt = 0.05;
                            } else if (targetIp.equals(gatewayIp)) {
                                isAlive = true;
                                rtt = measureTcpRtt(targetIp, 80, 150);
                            } else if (arpTable.containsKey(targetIp)) {
                                isAlive = true;
                                rtt = 0.8;
                            } else {
                                // Thử ICMP nhanh (timeout 60ms)
                                try {
                                    long t0 = System.nanoTime();
                                    InetAddress addr = InetAddress.getByName(targetIp);
                                    if (addr.isReachable(60)) {
                                        isAlive = true;
                                        rtt = (System.nanoTime() - t0) / 1_000_000.0;
                                    }
                                } catch (Exception ignored) {}

                                // Thử TCP connect probe nhanh (90ms)
                                if (!isAlive) {
                                    double tcpRtt = probeTcpPorts(targetIp, PROBE_PORTS, 90);
                                    if (tcpRtt >= 0) {
                                        isAlive = true;
                                        rtt = tcpRtt;
                                    }
                                }

                                // Thử UDP probe nhanh
                                if (!isAlive) {
                                    if (probeUdp(targetIp, 137, 80)) {
                                        isAlive = true;
                                        rtt = 1.0;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        // Nếu phát hiện thiết bị sống
                        if (isAlive && isRunning.get() && discovered.add(targetIp)) {
                            String mac = arpTable.getOrDefault(targetIp, "Chưa rõ MAC");

                            NetworkDevice.DeviceType type;
                            String name;
                            String hint;

                            if (targetIp.equals(localIp)) {
                                type = NetworkDevice.DeviceType.LOCAL_SCANNER;
                                String compName = System.getenv("COMPUTERNAME");
                                name = "Máy này" + (compName != null ? " (" + compName + ")" : "");
                                hint = "Scanner Host";
                            } else if (targetIp.equals(gatewayIp)) {
                                type = NetworkDevice.DeviceType.GATEWAY;
                                name = "Router / AP" + (wifiInfo.getSsid() != null ? " (" + wifiInfo.getSsid() + ")" : "");
                                hint = "Router Wi-Fi";
                                if (wifiInfo.getBssid() != null && !wifiInfo.getBssid().equals("-")) {
                                    mac = wifiInfo.getBssid();
                                }
                            } else {
                                type = NetworkDevice.DeviceType.PEER_CLIENT;
                                DeviceNameResolver.DeviceInfo devInfo = DeviceNameResolver.resolve(targetIp);
                                name = devInfo.name;
                                hint = devInfo.deviceHint;
                            }

                            NetworkDevice device = new NetworkDevice(targetIp, name, mac, type);
                            device.setRttMs(rtt > 0 ? rtt : 1.0);
                            device.setDistanceEstimate(DistanceCalculator.calculateDistance(
                                targetIp, device.isGateway(), device.isLocalHost(), wifiInfo, null
                            ));

                            // Đặt tên thiết bị rõ ràng, chuẩn font tiếng Việt, không lỗi icon
                            if (type == NetworkDevice.DeviceType.PEER_CLIENT) {
                                if (name == null || name.isEmpty() || name.equals(targetIp)) {
                                    device.setHostName(hint != null && !hint.isEmpty() ? hint : "Thiết bị Wi-Fi (" + targetIp + ")");
                                } else {
                                    device.setHostName(name + " - " + hint);
                                }
                            }

                            foundDevices.add(device);
                            if (listener != null) listener.onDeviceFound(device);
                        }

                        int done = completedCount.incrementAndGet();
                        double percent = (done * 100.0) / total;
                        int found = foundDevices.size();
                        notifyProgress(listener, done, total, percent,
                            String.format("Đã quét: %d/%d IP | Phát hiện: %d thiết bị", done, total, found));
                    }, executor);

                    futures.add(future);
                }

                // Chờ tất cả luồng hoàn thành
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // Sắp xếp kết quả: Máy này -> Router -> Các thiết bị theo IP
                foundDevices.sort((a, b) -> {
                    if (a.isLocalHost()) return -1;
                    if (b.isLocalHost()) return 1;
                    if (a.isGateway()) return -1;
                    if (b.isGateway()) return 1;
                    return compareIp(a.getIpAddress(), b.getIpAddress());
                });

                if (listener != null && isRunning.get()) {
                    listener.onCompleted(foundDevices);
                }

            } catch (Exception e) {
                if (listener != null) listener.onError("Lỗi quét mạng: " + e.getMessage());
            } finally {
                isRunning.set(false);
                if (executor != null) executor.shutdown();
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Xây danh sách IP theo subnet
    // ──────────────────────────────────────────────────────────────────────
    private static List<String> buildIpList(WifiInfo wifiInfo) {
        List<String> ips = new ArrayList<>();
        try {
            int prefix = wifiInfo.getPrefixLength();
            if (prefix <= 0 || prefix > 30) prefix = 24;

            String localIpStr = wifiInfo.getLocalIp();
            if (localIpStr == null || localIpStr.isEmpty()) localIpStr = "192.168.0.1";

            InetAddress localAddr = InetAddress.getByName(localIpStr);
            byte[] ipBytes = localAddr.getAddress();

            int mask = 0xFFFFFFFF << (32 - prefix);
            byte[] maskBytes = new byte[] {
                (byte)(mask >>> 24),
                (byte)(mask >>> 16),
                (byte)(mask >>> 8),
                (byte)mask
            };

            byte[] networkBytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                networkBytes[i] = (byte)(ipBytes[i] & maskBytes[i]);
            }

            if (prefix == 24) {
                int octet1 = networkBytes[0] & 0xFF;
                int octet2 = networkBytes[1] & 0xFF;
                int octet3 = networkBytes[2] & 0xFF;
                for (int h = 1; h <= 254; h++) {
                    ips.add(octet1 + "." + octet2 + "." + octet3 + "." + h);
                }
            } else if (prefix < 24 && prefix >= 20) {
                int octet1 = networkBytes[0] & 0xFF;
                int octet2 = networkBytes[1] & 0xFF;
                int octet3Start = networkBytes[2] & 0xFF;
                int subnetCount = 1 << (24 - prefix);

                for (int s = 0; s < subnetCount; s++) {
                    for (int h = 1; h <= 254; h++) {
                        ips.add(octet1 + "." + octet2 + "." + (octet3Start + s) + "." + h);
                    }
                }
            } else {
                String base = wifiInfo.getSubnetNetworkAddress();
                for (int i = 1; i <= 254; i++) {
                    ips.add(base + i);
                }
            }
        } catch (Exception e) {
            String base = wifiInfo.getSubnetNetworkAddress();
            for (int i = 1; i <= 254; i++) {
                ips.add(base + i);
            }
        }
        return ips;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Phát ICMP ping song song tới toàn dải (kích hoạt ARP của OS)
    // ──────────────────────────────────────────────────────────────────────
    private static void sendIcmpFloodAsync(List<String> ipList, ExecutorService exec) {
        for (String ip : ipList) {
            exec.submit(() -> {
                try {
                    InetAddress addr = InetAddress.getByName(ip);
                    addr.isReachable(50);
                } catch (Exception ignored) {}
            });
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Đọc bảng ARP từ hệ thống (arp -a)
    // ──────────────────────────────────────────────────────────────────────
    private static Map<String, String> loadArpTable() {
        Map<String, String> map = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("arp", "-a");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    String[] tokens = line.split("\\s+");
                    if (tokens.length >= 2) {
                        String ip  = tokens[0];
                        String mac = tokens[1].replace("-", ":").toUpperCase();
                        if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                                && mac.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) {
                            if (!mac.startsWith("FF:FF") && !mac.startsWith("01:00:5E")) {
                                map.put(ip, mac);
                            }
                        }
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return map;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Probe TCP kết nối tới các cổng chủ chốt (80, 445, 135, 139)
    // ──────────────────────────────────────────────────────────────────────
    private static double probeTcpPorts(String ip, int[] ports, int timeoutMs) {
        for (int port : ports) {
            try (Socket s = new Socket()) {
                s.setReuseAddress(true);
                long t = System.nanoTime();
                s.connect(new InetSocketAddress(ip, port), timeoutMs);
                return (System.nanoTime() - t) / 1_000_000.0;
            } catch (ConnectException e) {
                // Nhận được TCP RST = Cổng đóng nhưng thiết bị CÓ TỒN TẠI!
                return 0.5;
            } catch (SocketTimeoutException | NoRouteToHostException ignored) {
            } catch (Exception ignored) {}
        }
        return -1.0;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Probe UDP nhanh
    // ──────────────────────────────────────────────────────────────────────
    private static boolean probeUdp(String ip, int port, int timeoutMs) {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setSoTimeout(timeoutMs);
            sock.setReuseAddress(true);
            InetAddress addr = InetAddress.getByName(ip);
            byte[] data = new byte[0];
            sock.send(new DatagramPacket(data, 0, addr, port));
            byte[] buf = new byte[64];
            sock.receive(new DatagramPacket(buf, buf.length));
            return true;
        } catch (PortUnreachableException e) {
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Đo RTT TCP
    // ──────────────────────────────────────────────────────────────────────
    private static double measureTcpRtt(String ip, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            long t = System.nanoTime();
            s.connect(new InetSocketAddress(ip, port), timeoutMs);
            return (System.nanoTime() - t) / 1_000_000.0;
        } catch (Exception e) {
            try {
                long t = System.nanoTime();
                if (InetAddress.getByName(ip).isReachable(timeoutMs)) {
                    return (System.nanoTime() - t) / 1_000_000.0;
                }
            } catch (Exception ignored) {}
            return 1.0;
        }
    }

    private static int compareIp(String ip1, String ip2) {
        try {
            String[] p1 = ip1.split("\\.");
            String[] p2 = ip2.split("\\.");
            for (int i = 0; i < 4; i++) {
                int c = Integer.compare(Integer.parseInt(p1[i]), Integer.parseInt(p2[i]));
                if (c != 0) return c;
            }
        } catch (Exception ignored) {}
        return ip1.compareTo(ip2);
    }

    private void notifyProgress(ScanProgressListener listener, int current, int total, double percent, String msg) {
        if (listener != null) {
            listener.onProgress(current, total, percent, msg);
        }
    }
}
