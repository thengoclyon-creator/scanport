package com.wifiscan.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Module nhận diện tên thiết bị trong mạng Wi-Fi (Tối ưu tốc độ cao, không dùng Emoji gây lỗi font).
 * 1. NetBIOS Name Service (NBNS) UDP 137 (nhận diện máy Windows nhanh trong 100-150ms)
 * 2. Reverse DNS có timeout giới hạn (tránh treo thread)
 * 3. HTTP Server Header (cổng 80) nhận diện Router/Gateway
 * 4. mDNS (Apple/Android)
 */
public class DeviceNameResolver {

    private static final int FAST_TIMEOUT_MS = 150;
    private static final ExecutorService RESOLVER_POOL = Executors.newFixedThreadPool(20);

    /**
     * Giải tên thiết bị nhanh và chuẩn xác.
     */
    public static DeviceInfo resolve(String ip) {
        DeviceInfo info = new DeviceInfo(ip, ip, "Không rõ", "Thiết bị mạng");

        // 1. Thử NetBIOS NBNS UDP 137 (cực nhanh trên mạng LAN)
        DeviceInfo nbns = tryNetBiosName(ip);
        if (nbns != null) {
            info.name = nbns.name;
            info.vendor = nbns.vendor;
            info.deviceHint = nbns.deviceHint;
            return info;
        }

        // 2. Thử Reverse DNS với timeout ngắn 150ms để không bị treo
        String rdns = tryReverseDnsWithTimeout(ip);
        if (rdns != null && !rdns.equals(ip)) {
            info.name = rdns;
            info.deviceHint = guessDeviceHint(rdns, null);
            return info;
        }

        // 3. Thử HTTP Header trên cổng 80 (nhận diện Router, Camera, NAS)
        String httpInfo = tryHttpBanner(ip);
        if (httpInfo != null) {
            info.vendor = httpInfo;
            info.deviceHint = guessDeviceHint(info.name, httpInfo);
        }

        return info;
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. NetBIOS Name Service – UDP port 137
    // ─────────────────────────────────────────────────────────────────
    private static DeviceInfo tryNetBiosName(String ip) {
        byte[] request = new byte[] {
            (byte)0x82, 0x28, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x20, 0x43, 0x4b, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x00, 0x00, 0x21,
            0x00, 0x01
        };

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(FAST_TIMEOUT_MS);
            InetAddress addr = InetAddress.getByName(ip);
            DatagramPacket req = new DatagramPacket(request, request.length, addr, 137);
            socket.send(req);

            byte[] buf = new byte[1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);

            byte[] data = resp.getData();
            int len = resp.getLength();
            if (len < 57) return null;

            int nameCount = data[56] & 0xFF;
            String firstName = null;
            String workgroup = null;

            for (int i = 0; i < nameCount; i++) {
                int offset = 57 + i * 18;
                if (offset + 15 > len) break;

                byte nameType = data[offset + 15];
                String name = new String(data, offset, 15, StandardCharsets.US_ASCII).trim();
                // Loại bỏ các ký tự điều khiển hoặc không in được
                name = name.replaceAll("[^a-zA-Z0-9._-]", "").trim();

                if (!name.isEmpty()) {
                    if (nameType == 0x00 && firstName == null) {
                        firstName = name;
                    } else if (nameType == 0x20) {
                        firstName = name; // Server name (ưu tiên)
                    } else if (nameType == (byte) 0x00) {
                        workgroup = name;
                    }
                }
            }

            if (firstName != null && !firstName.isEmpty()) {
                String hint = "Máy tính Windows (NetBIOS)";
                if (workgroup != null && !workgroup.isEmpty()) {
                    hint += " [" + workgroup + "]";
                }
                return new DeviceInfo(ip, firstName, "Microsoft", hint);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. Reverse DNS có timeout ngắn (tránh nghẽn thread quét)
    // ─────────────────────────────────────────────────────────────────
    private static String tryReverseDnsWithTimeout(String ip) {
        try {
            Future<String> future = RESOLVER_POOL.submit(() -> {
                InetAddress addr = InetAddress.getByName(ip);
                String host = addr.getHostName();
                if (host != null && !host.equals(ip)) {
                    if (host.contains(".")) {
                        String shortName = host.split("\\.")[0];
                        if (shortName.length() > 2) return shortName;
                    }
                    return host;
                }
                return null;
            });
            return future.get(150, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. HTTP Server header grab nhanh trên port 80
    // ─────────────────────────────────────────────────────────────────
    private static String tryHttpBanner(String ip) {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(ip, 80), 120);
            sock.setSoTimeout(120);

            String req = "HEAD / HTTP/1.0\r\nHost: " + ip + "\r\nConnection: close\r\n\r\n";
            sock.getOutputStream().write(req.getBytes(StandardCharsets.UTF_8));
            sock.getOutputStream().flush();

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(sock.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.toLowerCase().startsWith("server:")) {
                        return line.substring(7).trim();
                    }
                    if (line.isEmpty()) break;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Nhận diện loại thiết bị (không dùng emoji để hiển thị chuẩn chữ)
    // ─────────────────────────────────────────────────────────────────
    public static String guessDeviceHint(String name, String serverBanner) {
        String n = name == null ? "" : name.toLowerCase();
        String s = serverBanner == null ? "" : serverBanner.toLowerCase();

        // Router / Access Point
        if (s.contains("router") || s.contains("tp-link") || s.contains("asus") || s.contains("d-link")
                || s.contains("netgear") || s.contains("mikrotik") || s.contains("ubiquiti")
                || n.contains("router") || n.contains("gateway") || n.contains("ap-")
                || s.contains("openwrt") || s.contains("dd-wrt") || s.contains("modem")) {
            return "Router / Access Point";
        }
        // Server / NAS
        if (s.contains("nginx") || s.contains("apache") || s.contains("synology")
                || s.contains("qnap") || n.contains("server") || n.contains("nas")) {
            return "Máy chủ / NAS";
        }
        // Camera IP
        if (s.contains("camera") || s.contains("hikvision") || s.contains("dahua")
                || n.contains("cam") || n.contains("ipcam")) {
            return "Camera IP / CCTV";
        }
        // Apple devices
        if (n.contains("iphone") || n.contains("ipad") || n.contains("macbook")
                || n.contains("apple") || s.contains("apple")) {
            return "Thiết bị Apple";
        }
        // Printer
        if (s.contains("printer") || n.contains("printer") || n.contains("print")
                || s.contains("hp-") || s.contains("epson") || s.contains("canon")) {
            return "Máy in mạng";
        }
        // Windows PC
        if (n.matches("desktop.*") || n.matches("laptop.*") || n.matches("pc.*")
                || n.matches("win.*") || s.contains("microsoft")) {
            return "Máy tính (Windows)";
        }
        // Linux / Android
        if (n.contains("android") || n.contains("linux") || n.contains("ubuntu")) {
            return "Thiết bị Android / Linux";
        }
        // Smart TV / IoT
        if (n.contains("tv") || n.contains("smart") || n.contains("esp") || n.contains("iot")) {
            return "Smart TV / IoT";
        }
        if (!n.isEmpty() && !n.equals("?")) return "Thiết bị Wi-Fi";
        return "Thiết bị mạng";
    }

    public static class DeviceInfo {
        public final String ip;
        public String name;
        public String vendor;
        public String deviceHint;

        public DeviceInfo(String ip, String name, String vendor, String deviceHint) {
            this.ip = ip;
            this.name = name;
            this.vendor = vendor;
            this.deviceHint = deviceHint;
        }
    }
}
