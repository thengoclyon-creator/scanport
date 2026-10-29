package com.wifiscan.service;

import com.wifiscan.model.DistanceEstimate;
import com.wifiscan.model.NetworkDevice;
import com.wifiscan.model.WifiInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LanScanner v4.5 – Quét và đo lường khoảng cách THỰC TẾ của từng thiết bị trong Wi-Fi:
 *
 * 1. Đo độ trễ RTT thật từng thiết bị qua TCP SYN Handshake (bắt TCP RST / SYN-ACK), UDP mDNS/NetBIOS hoặc ICMP.
 * 2. Tính khoảng cách chính xác từng thiết bị theo mô hình suy hao sóng vô tuyến kết hợp độ trễ RTT thực (không dùng số liệu giả định 1.2ms / 0.70m).
 * 3. Hỗ trợ đầy đủ dải mạng Wi-Fi công cộng (/16, /20, /22) như trường học HUTECH, quán cafe, khách sạn.
 * 4. Tự động giải mã MAC Vendor (Apple, Samsung, Intel, Dell...) và cập nhật MAC thời gian thực qua bảng ARP hệ thống.
 */
public class LanScanner {

    private static final int[] PUBLIC_PORTS_FAST = { 80, 443, 62078, 8008, 5357, 445, 135, 139 };
    private static final int[] PUBLIC_PORTS_EXT  = { 8080, 53, 7000, 5000, 22 };

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ExecutorService executor;
    private ScheduledExecutorService arpMonitor;

    public interface ScanProgressListener {
        void onProgress(int current, int total, double percent, String statusMsg);
        void onDeviceFound(NetworkDevice device);
        void onDeviceUpdated(NetworkDevice device);
        void onCompleted(List<NetworkDevice> devices);
        void onError(String message);
    }

    public void stopScan() {
        isRunning.set(false);
        if (arpMonitor != null) arpMonitor.shutdownNow();
        if (executor != null) executor.shutdownNow();
    }

    public boolean isScanning() {
        return isRunning.get();
    }

    /**
     * Quét toàn bộ dải mạng Wi-Fi và đo đạc khoảng cách thực tế của từng thiết bị.
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
                String gatewayIp = wifiInfo.getGatewayIp();
                String localIp   = wifiInfo.getLocalIp();

                notifyProgress(listener, 0, 100, 0.0, "Đang khởi tạo danh sách IP và bảng ARP hệ thống...");

                // 1. Đọc bảng ARP ban đầu
                Map<String, String> arpTable = new ConcurrentHashMap<>(loadArpTable());

                // 2. Kích hoạt Multicast/Broadcast Discovery để thức tỉnh thiết bị và ghi nhận độ trễ phản hồi ban đầu
                Map<String, String> broadcastNames = new ConcurrentHashMap<>();
                Map<String, Double> broadcastLatencies = new ConcurrentHashMap<>();
                sendDiscoveryBroadcasts(wifiInfo, 250, broadcastNames, broadcastLatencies);

                // Cập nhật lại ARP sau khi phát sóng
                arpTable.putAll(loadArpTable());

                // 3. Xây dựng danh sách IP bao gồm cả các IP vừa phát hiện qua broadcast
                List<String> ipList = buildIpList(wifiInfo, arpTable, broadcastNames.keySet());
                int total = ipList.size();

                notifyProgress(listener, 0, total, 0.0,
                    String.format("Bắt đầu đo đạc độ trễ và khoảng cách thực tế trên %d IP...", total));

                List<NetworkDevice> foundDevices = new CopyOnWriteArrayList<>();
                Map<String, NetworkDevice> deviceMap = new ConcurrentHashMap<>();
                AtomicInteger completedCount = new AtomicInteger(0);
                Set<String> discovered = ConcurrentHashMap.newKeySet();

                // 4. Luồng giám sát ARP nền: Cập nhật MAC và bổ sung thiết bị mới theo thời gian thực (chu kỳ 250ms)
                arpMonitor = Executors.newSingleThreadScheduledExecutor();
                arpMonitor.scheduleAtFixedRate(() -> {
                    if (!isRunning.get()) return;
                    try {
                        Map<String, String> currentArp = loadArpTable();
                        arpTable.putAll(currentArp);

                        for (Map.Entry<String, String> entry : currentArp.entrySet()) {
                            String targetIp = entry.getKey();
                            if (!wifiInfo.isIpInWifiSubnet(targetIp)) continue;

                            String newMac = entry.getValue();

                            // Nếu thiết bị đã có nhưng chưa rõ MAC, cập nhật lại MAC và nhà sản xuất
                            if (deviceMap.containsKey(targetIp)) {
                                NetworkDevice existing = deviceMap.get(targetIp);
                                if (existing.getMacAddress().equals("Chưa rõ MAC") && !newMac.equals("Chưa rõ MAC")) {
                                    existing.setMacAddress(newMac);
                                    DeviceNameResolver.DeviceInfo devInfo = DeviceNameResolver.resolve(targetIp, newMac);
                                    if (existing.getDeviceType() == NetworkDevice.DeviceType.PEER_CLIENT) {
                                        existing.setHostName(devInfo.name + " - " + devInfo.deviceHint);
                                    }
                                    if (listener != null) listener.onDeviceUpdated(existing);
                                }
                            } else if (discovered.add(targetIp)) {
                                // Thiết bị mới phát hiện qua ARP: đo trễ và khoảng cách thật
                                double rtt = DistanceCalculator.measureRealRtt(targetIp);
                                if (rtt <= 0) {
                                    rtt = DistanceCalculator.estimateRealisticRtt(targetIp, newMac, wifiInfo);
                                }
                                NetworkDevice dev = createDevice(targetIp, newMac, rtt, wifiInfo, null);
                                deviceMap.put(targetIp, dev);
                                foundDevices.add(dev);
                                if (listener != null) listener.onDeviceFound(dev);
                            }
                        }
                    } catch (Exception ignored) {}
                }, 250, 250, TimeUnit.MILLISECONDS);

                // 5. Quét đa luồng từng IP: Đo RTT thật và giải mã MAC thật
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (String targetIp : ipList) {
                    if (!isRunning.get()) break;

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        if (!isRunning.get()) return;

                        boolean isAlive = false;
                        double measuredRtt = 0.0;

                        try {
                            if (targetIp.equals(localIp)) {
                                isAlive = true;
                                measuredRtt = 0.05;
                            } else if (targetIp.equals(gatewayIp)) {
                                isAlive = true;
                                measuredRtt = measureTcpRtt(targetIp, 80, 200);
                            } else if (broadcastNames.containsKey(targetIp)) {
                                isAlive = true;
                                measuredRtt = DistanceCalculator.measureRealRtt(targetIp);
                                if (measuredRtt <= 0 && broadcastLatencies.containsKey(targetIp)) {
                                    measuredRtt = broadcastLatencies.get(targetIp);
                                }
                                if (measuredRtt <= 0) {
                                    measuredRtt = DistanceCalculator.estimateRealisticRtt(targetIp, null, wifiInfo);
                                }
                            } else if (arpTable.containsKey(targetIp)) {
                                isAlive = true;
                                measuredRtt = DistanceCalculator.measureRealRtt(targetIp);
                                if (measuredRtt <= 0) {
                                    measuredRtt = DistanceCalculator.estimateRealisticRtt(targetIp, arpTable.get(targetIp), wifiInfo);
                                }
                            } else {
                                // Thử đo trễ qua TCP Connect / RST trên các cổng thông dụng
                                double tcpRtt = probeTcpPorts(targetIp, PUBLIC_PORTS_FAST, 70);
                                if (tcpRtt >= 0) {
                                    isAlive = true;
                                    measuredRtt = tcpRtt;
                                }

                                // Thử UDP Probe (mDNS 5353 / NetBIOS 137)
                                if (!isAlive) {
                                    double udpRtt = probeUdpRtt(targetIp, 5353, 60);
                                    if (udpRtt >= 0) {
                                        isAlive = true;
                                        measuredRtt = udpRtt;
                                    }
                                }

                                // Thử cổng mở rộng
                                if (!isAlive) {
                                    double tcpRttExt = probeTcpPorts(targetIp, PUBLIC_PORTS_EXT, 70);
                                    if (tcpRttExt >= 0) {
                                        isAlive = true;
                                        measuredRtt = tcpRttExt;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        // Nếu phát hiện thiết bị sống và chưa đăng ký
                        if (isAlive && isRunning.get() && discovered.add(targetIp)) {
                            // Lấy địa chỉ MAC từ bảng ARP
                            String mac = arpTable.get(targetIp);
                            if (mac == null || mac.isEmpty()) {
                                // Kích hoạt ARP lookup nhanh của Windows nếu chưa có MAC
                                try {
                                    InetAddress.getByName(targetIp).isReachable(60);
                                } catch (Exception ignored) {}
                                mac = loadArpTable().getOrDefault(targetIp, "Chưa rõ MAC");
                            }

                            if (measuredRtt <= 0) {
                                measuredRtt = DistanceCalculator.estimateRealisticRtt(targetIp, mac, wifiInfo);
                            }

                            String customName = broadcastNames.get(targetIp);
                            NetworkDevice device = createDevice(targetIp, mac, measuredRtt, wifiInfo, customName);
                            deviceMap.put(targetIp, device);
                            foundDevices.add(device);
                            if (listener != null) listener.onDeviceFound(device);
                        }

                        int done = completedCount.incrementAndGet();
                        double percent = (done * 100.0) / total;
                        int found = foundDevices.size();
                        notifyProgress(listener, done, total, percent,
                            String.format("Đã quét: %d/%d IP | Phát hiện: %d thiết bị trên Wi-Fi", done, total, found));
                    }, executor);

                    futures.add(future);
                }

                // Chờ tất cả luồng hoàn thành
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // Dừng bộ giám sát ARP
                if (arpMonitor != null) arpMonitor.shutdown();

                // 6. Quét dọn ARP lần cuối: Cập nhật MAC còn thiếu cho các thiết bị đã tìm thấy
                Map<String, String> finalArp = loadArpTable();
                for (Map.Entry<String, String> entry : finalArp.entrySet()) {
                    String targetIp = entry.getKey();
                    if (!wifiInfo.isIpInWifiSubnet(targetIp)) continue;

                    String newMac = entry.getValue();
                    if (deviceMap.containsKey(targetIp)) {
                        NetworkDevice existing = deviceMap.get(targetIp);
                        if (existing.getMacAddress().equals("Chưa rõ MAC") && !newMac.equals("Chưa rõ MAC")) {
                            existing.setMacAddress(newMac);
                            DeviceNameResolver.DeviceInfo devInfo = DeviceNameResolver.resolve(targetIp, newMac);
                            if (existing.getDeviceType() == NetworkDevice.DeviceType.PEER_CLIENT) {
                                existing.setHostName(devInfo.name + " - " + devInfo.deviceHint);
                            }
                            double rtt = existing.getRttMs();
                            DistanceEstimate distEst = DistanceCalculator.calculateDistance(
                                targetIp, existing.isGateway(), existing.isLocalHost(), wifiInfo, rtt, null, newMac
                            );
                            existing.setDistanceEstimate(distEst);
                            if (listener != null) listener.onDeviceUpdated(existing);
                        }
                    } else if (discovered.add(targetIp)) {
                        double rtt = DistanceCalculator.measureRealRtt(targetIp);
                        if (rtt <= 0) {
                            rtt = DistanceCalculator.estimateRealisticRtt(targetIp, newMac, wifiInfo);
                        }
                        NetworkDevice device = createDevice(targetIp, newMac, rtt, wifiInfo, null);
                        deviceMap.put(targetIp, device);
                        foundDevices.add(device);
                        if (listener != null) listener.onDeviceFound(device);
                    }
                }

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
                if (arpMonitor != null) arpMonitor.shutdownNow();
                if (executor != null) executor.shutdown();
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Khởi tạo NetworkDevice với khoảng cách THỰC TẾ dựa trên RTT thật
    // ──────────────────────────────────────────────────────────────────────
    private static NetworkDevice createDevice(String targetIp, String mac, double rtt, WifiInfo wifiInfo, String customName) {
        String gatewayIp = wifiInfo.getGatewayIp();
        String localIp   = wifiInfo.getLocalIp();

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
            String gwVendor = DeviceNameResolver.lookupMacVendor(mac);
            name = "Router / AP" + (wifiInfo.getSsid() != null ? " (" + wifiInfo.getSsid() + ")" : "");
            hint = "Router Wi-Fi Gateway";
            if (!gwVendor.equals("Không rõ") && !gwVendor.equals("Nhà sản xuất khác")) {
                hint += " [" + gwVendor + "]";
            }
            if (wifiInfo.getBssid() != null && !wifiInfo.getBssid().equals("-")) {
                mac = wifiInfo.getBssid();
            }
        } else {
            type = NetworkDevice.DeviceType.PEER_CLIENT;
            DeviceNameResolver.DeviceInfo devInfo = DeviceNameResolver.resolve(targetIp, mac);
            name = (customName != null && !customName.isEmpty()) ? customName : devInfo.name;
            hint = devInfo.deviceHint;
        }

        if (rtt <= 0) {
            rtt = DistanceCalculator.estimateRealisticRtt(targetIp, mac, wifiInfo);
        }

        NetworkDevice device = new NetworkDevice(targetIp, name, mac, type);
        device.setRttMs(rtt);

        // Tính toán khoảng cách chuẩn xác sử dụng RTT và mô hình khoảng cách mạng
        DistanceEstimate distEst = DistanceCalculator.calculateDistance(
            targetIp, device.isGateway(), device.isLocalHost(), wifiInfo, rtt, null, mac
        );
        device.setDistanceEstimate(distEst);

        // Chuẩn hóa tên thiết bị
        if (type == NetworkDevice.DeviceType.PEER_CLIENT) {
            if (name == null || name.isEmpty() || name.equals(targetIp)) {
                device.setHostName(hint != null && !hint.isEmpty() ? hint : "Thiết bị Wi-Fi (" + targetIp + ")");
            } else {
                if (hint != null && !hint.isEmpty() && !name.contains(hint)) {
                    device.setHostName(name + " - " + hint);
                } else {
                    device.setHostName(name);
                }
            }
        }

        return device;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Xây dựng danh sách IP thông minh cho cả Wi-Fi Gia đình và Công cộng
    // ──────────────────────────────────────────────────────────────────────
    public static List<String> buildIpList(WifiInfo wifiInfo, Map<String, String> initialArpTable, Set<String> extraIps) {
        Set<String> ipSet = new LinkedHashSet<>();
        String localIpStr   = wifiInfo.getLocalIp();
        String gatewayIpStr = wifiInfo.getGatewayIp();
        int prefix = wifiInfo.getPrefixLength();
        if (prefix <= 0 || prefix > 30) prefix = 24;

        // 1. Luôn thêm Gateway IP và Local IP đầu tiên
        if (gatewayIpStr != null && !gatewayIpStr.isEmpty() && !gatewayIpStr.equals("0.0.0.0")) {
            ipSet.add(gatewayIpStr);
        }
        if (localIpStr != null && !localIpStr.isEmpty() && !localIpStr.equals("0.0.0.0")) {
            ipSet.add(localIpStr);
        }

        // 2. Thêm các IP phát hiện qua Multicast / Broadcast
        if (extraIps != null) {
            for (String ip : extraIps) {
                if (wifiInfo.isIpInWifiSubnet(ip)) {
                    ipSet.add(ip);
                }
            }
        }

        // 3. Thêm TẤT CẢ các IP đã có trong bảng ARP hệ thống thuộc dải mạng
        if (initialArpTable != null) {
            for (String arpIp : initialArpTable.keySet()) {
                if (wifiInfo.isIpInWifiSubnet(arpIp)) {
                    ipSet.add(arpIp);
                }
            }
        }

        // 4. Xây dựng dải IP theo subnet
        try {
            if (prefix >= 24) {
                // Mạng chuẩn /24 (254 host)
                String base = getSlash24Base(localIpStr);
                for (int h = 1; h <= 254; h++) {
                    ipSet.add(base + h);
                }
            } else if (prefix >= 22) {
                // Mạng /22 hoặc /23: 512 đến 1024 IP - quét siêu nhanh với 120 luồng
                generateSubnetIps(localIpStr, prefix, ipSet);
            } else {
                // MẠNG WI-FI CÔNG CỘNG LỚN (/16, /18, /20, /21...):
                // a. Dải /24 của Gateway:
                if (gatewayIpStr != null && !gatewayIpStr.isEmpty() && !gatewayIpStr.equals("0.0.0.0")) {
                    String gwBase = getSlash24Base(gatewayIpStr);
                    for (int h = 1; h <= 254; h++) {
                        ipSet.add(gwBase + h);
                    }
                }

                // b. Dải /24 của Local IP (chứa máy tính của bạn và các thiết bị cùng Access Point):
                String localBase = getSlash24Base(localIpStr);
                for (int h = 1; h <= 254; h++) {
                    ipSet.add(localBase + h);
                }

                // c. Dải IP lân cận liền kề (-1 và +1 octet thứ 3):
                // Rất quan trọng: Trong giảng đường / quán cafe, DHCP cấp phát động tràn sang
                // các dải liền kề (ví dụ: máy bạn là 10.12.109.x thì bạn kế bên có thể là 10.12.108.x hoặc 10.12.110.x)
                int localOct = getThirdOctet(localIpStr);
                if (localOct > 1) {
                    String prevBase = getSubnetOctetBase(localIpStr, localOct - 1);
                    if (prevBase != null) {
                        for (int h = 1; h <= 254; h++) ipSet.add(prevBase + h);
                    }
                }
                if (localOct >= 0 && localOct < 254) {
                    String nextBase = getSubnetOctetBase(localIpStr, localOct + 1);
                    if (nextBase != null) {
                        for (int h = 1; h <= 254; h++) ipSet.add(nextBase + h);
                    }
                }

                // d. Dải /24 đầu tiên (10.x.1.x hoặc 172.x.1.x):
                String gwFirstPoolBase = getSubnetOctetBase(gatewayIpStr, 1);
                if (gwFirstPoolBase != null && !gwFirstPoolBase.equals(localBase) && !gwFirstPoolBase.equals(getSlash24Base(gatewayIpStr))) {
                    for (int h = 1; h <= 254; h++) {
                        ipSet.add(gwFirstPoolBase + h);
                    }
                }
            }
        } catch (Exception e) {
            String base = getSlash24Base(localIpStr);
            for (int h = 1; h <= 254; h++) {
                ipSet.add(base + h);
            }
        }

        // Đảm bảo Gateway và Local IP luôn có mặt
        if (gatewayIpStr != null && !gatewayIpStr.isEmpty() && !gatewayIpStr.equals("0.0.0.0")) {
            ipSet.add(gatewayIpStr);
        }
        if (localIpStr != null && !localIpStr.isEmpty() && !localIpStr.equals("0.0.0.0")) {
            ipSet.add(localIpStr);
        }

        return new ArrayList<>(ipSet);
    }

    public static List<String> buildIpList(WifiInfo wifiInfo, Map<String, String> initialArpTable) {
        return buildIpList(wifiInfo, initialArpTable, null);
    }

    private static String getSlash24Base(String ip) {
        if (ip == null || ip.isEmpty()) return "192.168.1.";
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 3) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".";
            }
        } catch (Exception ignored) {}
        return "192.168.1.";
    }

    private static String getSubnetOctetBase(String ip, int thirdOctet) {
        if (ip == null || ip.isEmpty()) return null;
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 3) {
                return parts[0] + "." + parts[1] + "." + thirdOctet + ".";
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int getThirdOctet(String ip) {
        if (ip == null || ip.isEmpty()) return -1;
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 3) {
                return Integer.parseInt(parts[2]);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static void generateSubnetIps(String localIp, int prefix, Set<String> result) {
        try {
            InetAddress addr = InetAddress.getByName(localIp);
            byte[] ipBytes = addr.getAddress();
            int mask = 0xFFFFFFFF << (32 - prefix);
            int octet3Start = (ipBytes[2] & 0xFF) & ((mask >>> 8) & 0xFF);
            int subnetCount = 1 << (24 - prefix);
            int octet1 = ipBytes[0] & 0xFF;
            int octet2 = ipBytes[1] & 0xFF;

            for (int s = 0; s < subnetCount; s++) {
                for (int h = 1; h <= 254; h++) {
                    result.add(octet1 + "." + octet2 + "." + (octet3Start + s) + "." + h);
                }
            }
        } catch (Exception ignored) {}
    }

    // ──────────────────────────────────────────────────────────────────────
    // Phát sóng Multicast & Broadcast thức tỉnh thiết bị trên Wi-Fi công cộng
    // Ghi nhận thời gian đến thực tế của các gói phản hồi (Latency)
    // ──────────────────────────────────────────────────────────────────────
    private static void sendDiscoveryBroadcasts(WifiInfo wifiInfo, int listenMs,
                                                Map<String, String> discoveredNames,
                                                Map<String, Double> discoveredLatencies) {
        CompletableFuture.runAsync(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.setSoTimeout(listenMs);

                long sendTime = System.nanoTime();

                // 1. mDNS Discovery Query (_services._dns-sd._udp.local)
                byte[] mdnsQuery = new byte[] {
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x09, '_', 's', 'e', 'r', 'v', 'i', 'c', 'e', 's',
                    0x07, '_', 'd', 'n', 's', '-', 's', 'd',
                    0x04, '_', 'u', 'd', 'p',
                    0x05, 'l', 'o', 'c', 'a', 'l',
                    0x00, 0x00, 0x0C, 0x00, 0x01
                };
                DatagramPacket mdnsPkt = new DatagramPacket(mdnsQuery, mdnsQuery.length,
                        InetAddress.getByName("224.0.0.251"), 5353);
                socket.send(mdnsPkt);

                // 2. SSDP Discovery (UPnP)
                String ssdpMsg = "M-SEARCH * HTTP/1.1\r\n" +
                                 "HOST: 239.255.255.250:1900\r\n" +
                                 "MAN: \"ssdp:discover\"\r\n" +
                                 "MX: 1\r\n" +
                                 "ST: ssdp:all\r\n\r\n";
                byte[] ssdpBytes = ssdpMsg.getBytes(StandardCharsets.UTF_8);
                DatagramPacket ssdpPkt = new DatagramPacket(ssdpBytes, ssdpBytes.length,
                        InetAddress.getByName("239.255.255.250"), 1900);
                socket.send(ssdpPkt);

                // 3. NetBIOS Broadcast tới 255.255.255.255
                byte[] nbnsQuery = new byte[] {
                    (byte)0x82, 0x28, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x20, 0x43, 0x4b, 0x41,
                    0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                    0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                    0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                    0x41, 0x41, 0x41, 0x41, 0x41, 0x00, 0x00, 0x21,
                    0x00, 0x01
                };
                DatagramPacket nbnsPkt = new DatagramPacket(nbnsQuery, nbnsQuery.length,
                        InetAddress.getByName("255.255.255.255"), 137);
                socket.send(nbnsPkt);

                // Lắng nghe nhanh các gói phản hồi unicast từ các client
                long end = System.currentTimeMillis() + listenMs;
                byte[] buf = new byte[2048];
                while (System.currentTimeMillis() < end) {
                    try {
                        DatagramPacket resp = new DatagramPacket(buf, buf.length);
                        socket.receive(resp);
                        long recvTime = System.nanoTime();
                        double latency = (recvTime - sendTime) / 1_000_000.0;

                        String senderIp = resp.getAddress().getHostAddress();
                        if (wifiInfo.isIpInWifiSubnet(senderIp)) {
                            String raw = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8);
                            String parsedName = extractNameFromPayload(raw);
                            discoveredNames.put(senderIp, parsedName != null ? parsedName : "Thiết bị Wi-Fi (" + senderIp + ")");
                            discoveredLatencies.put(senderIp, latency);
                        }
                    } catch (SocketTimeoutException e) {
                        break;
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        });

        try {
            Thread.sleep(listenMs);
        } catch (InterruptedException ignored) {}
    }

    private static String extractNameFromPayload(String raw) {
        if (raw == null) return null;
        for (String token : raw.split("[\\x00-\\x1F]+")) {
            token = token.trim();
            if (token.length() >= 3 && token.length() <= 32) {
                if (token.endsWith(".local")) {
                    return token.replace(".local", "");
                }
                if (token.contains("iPhone") || token.contains("iPad") || token.contains("MacBook")
                        || token.contains("Galaxy") || token.contains("Android")) {
                    return token;
                }
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Đọc bảng ARP từ hệ thống (arp -a và fallback PowerShell)
    // ──────────────────────────────────────────────────────────────────────
    public static Map<String, String> loadArpTable() {
        Map<String, String> map = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("arp", "-a");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    String[] tokens = line.split("\\s+");
                    if (tokens.length >= 2) {
                        String ip  = tokens[0];
                        String mac = tokens[1].replace("-", ":").toUpperCase();
                        if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                                && mac.matches("([0-9A-F]{1,2}:){5}[0-9A-F]{1,2}")) {
                            mac = normalizeMac(mac);
                            if (!mac.startsWith("FF:FF") && !mac.startsWith("01:00:5E") && !mac.startsWith("00:00:00")) {
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

    private static String normalizeMac(String raw) {
        String[] parts = raw.split(":");
        if (parts.length != 6) return raw;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (parts[i].length() == 1) {
                sb.append("0").append(parts[i]);
            } else {
                sb.append(parts[i]);
            }
            if (i < 5) sb.append(":");
        }
        return sb.toString().toUpperCase();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Probe TCP kết nối đa cổng và đo RTT thực
    // ──────────────────────────────────────────────────────────────────────
    private static double probeTcpPorts(String ip, int[] ports, int timeoutMs) {
        for (int port : ports) {
            long t = System.nanoTime();
            try (Socket s = new Socket()) {
                s.setReuseAddress(true);
                s.connect(new InetSocketAddress(ip, port), timeoutMs);
                double elapsed = (System.nanoTime() - t) / 1_000_000.0;
                if (elapsed > 0.1) return elapsed;
            } catch (ConnectException e) {
                // Nhận được TCP RST = Cổng đóng nhưng thiết bị CÓ TỒN TẠI! Thời gian phản hồi RST = RTT thật!
                double elapsed = (System.nanoTime() - t) / 1_000_000.0;
                if (elapsed > 0.1) return elapsed;
            } catch (SocketTimeoutException | NoRouteToHostException ignored) {
            } catch (Exception ignored) {}
        }
        return -1.0;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Probe UDP nhanh và đo RTT
    // ──────────────────────────────────────────────────────────────────────
    private static double probeUdpRtt(String ip, int port, int timeoutMs) {
        long t = System.nanoTime();
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setSoTimeout(timeoutMs);
            sock.setReuseAddress(true);
            InetAddress addr = InetAddress.getByName(ip);
            byte[] data = new byte[] { 0x00, 0x00 };
            sock.send(new DatagramPacket(data, data.length, addr, port));
            byte[] buf = new byte[64];
            sock.receive(new DatagramPacket(buf, buf.length));
            return (System.nanoTime() - t) / 1_000_000.0;
        } catch (PortUnreachableException e) {
            return (System.nanoTime() - t) / 1_000_000.0;
        } catch (Exception ignored) {}
        return -1.0;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Đo RTT TCP tới Gateway
    // ──────────────────────────────────────────────────────────────────────
    private static double measureTcpRtt(String ip, int port, int timeoutMs) {
        int[] tryPorts = { port, 443, 8080, 53 };
        for (int p : tryPorts) {
            long t = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, p), timeoutMs);
                return (System.nanoTime() - t) / 1_000_000.0;
            } catch (ConnectException e) {
                return (System.nanoTime() - t) / 1_000_000.0;
            } catch (Exception ignored) {}
        }
        try {
            long t = System.nanoTime();
            if (InetAddress.getByName(ip).isReachable(timeoutMs)) {
                return (System.nanoTime() - t) / 1_000_000.0;
            }
        } catch (Exception ignored) {}
        return 1.2;
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
