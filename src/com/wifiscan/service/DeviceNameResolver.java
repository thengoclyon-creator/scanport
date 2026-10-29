package com.wifiscan.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Module nhận diện tên và loại thiết bị trong mạng Wi-Fi (Tối ưu cho cả Wi-Fi Gia đình và Wi-Fi Công cộng).
 * 1. Phân giải MAC Address OUI Vendor (Apple, Samsung, Xiaomi, Intel, Dell, Asus, HP, Cisco, Aruba...)
 * 2. Nhận diện Locally Administered / Private MAC Address (iOS 14+, Android 10+ bảo mật)
 * 3. NetBIOS Name Service (NBNS) UDP 137 (nhận diện máy Windows nhanh trong 100-150ms)
 * 4. mDNS unicast / multicast (Apple iOS/macOS, Android, Google Cast)
 * 5. Reverse DNS có timeout giới hạn (tránh treo thread)
 * 6. HTTP Server Header (cổng 80) nhận diện Router/Gateway
 */
public class DeviceNameResolver {

    private static final int FAST_TIMEOUT_MS = 150;
    private static final ExecutorService RESOLVER_POOL = Executors.newFixedThreadPool(20);

    // Bảng tra cứu OUI (3 bytes đầu của MAC) thông dụng nhất cho thiết bị Wi-Fi và mạng công cộng
    private static final Map<String, String> OUI_VENDORS = new HashMap<>();

    static {
        // Apple (iPhone, iPad, MacBook, Apple Watch, iMac)
        String[] applePrefixes = {
            "000393", "000502", "000A27", "000A95", "000D93", "0010FA", "001124", "001451", "0016CB", "0017F2",
            "0019E3", "001B63", "001CB3", "001E52", "001F5B", "001FF3", "0021E9", "002241", "002312", "002332",
            "00236C", "0023DF", "002436", "002500", "00254B", "0025BC", "002608", "00264A", "0026B0", "0026BB",
            "040CCE", "041552", "042665", "045453", "080007", "086698", "0C5101", "0C74C2", "101C0C", "1040F3",
            "1093E9", "1094BB", "14109F", "147DDA", "183451", "186590", "18AF61", "1C1AC0", "20A2E4", "24A074",
            "280B5C", "286ABA", "30D9D9", "3408BC", "38CADA", "3C0754", "406C8F", "40B4CD", "442A60", "444C0C",
            "48605F", "48746E", "4C3275", "50EAD6", "542696", "5855CA", "5CE91E", "600308", "60334B", "64A5C3",
            "685B35", "6C709F", "701124", "70DEE2", "748D08", "784F43", "7C6D62", "804971", "843835", "88665A",
            "8C8590", "90FD61", "94E979", "9801A7", "9CF387", "A0999B", "A4C361", "A85B78", "AC63BE", "B065BD",
            "B4F0AB", "B8098A", "BC52B7", "C0847A", "C4B301", "C86F1D", "CC088D", "D0034B", "D4619D", "D89695",
            "DCA904", "E0B655", "E4CE8F", "E8802E", "EC3586", "F01898", "F40F24", "F82793", "FCFC48"
        };
        for (String p : applePrefixes) OUI_VENDORS.put(p, "Apple, Inc.");

        // Samsung (Galaxy Phone, Tab, Smart TV)
        String[] samsungPrefixes = {
            "0007AB", "0012FB", "001599", "001632", "0017C9", "001A8A", "001D25", "002119", "0023D7", "0418D6",
            "08373D", "0C1420", "1449E0", "18227E", "244B03", "2CAE2B", "3423BA", "380146", "400E85", "4844F7",
            "50B7C3", "58C5CB", "608334", "68EBAE", "702C1F", "7840E4", "8425DB", "88365F", "94652D", "9C0298",
            "A87C01", "AC5F3E", "B479A7", "BC4486", "C4731E", "CC07AB", "D022BE", "D857EF", "E458E7", "ECE09B",
            "F47B5E", "FCF136"
        };
        for (String p : samsungPrefixes) OUI_VENDORS.put(p, "Samsung Electronics");

        // Xiaomi / Redmi / POCO
        String[] xiaomiPrefixes = {
            "009EC8", "04CF8C", "0C9838", "14F65A", "185936", "2082C0", "286C07", "3480B3", "3CBD3E", "44237C",
            "508F4C", "584498", "64CC2E", "70BBE9", "7802F8", "84F3EB", "8CBEBE", "98FAE3", "A4C494", "ACC1EE",
            "B46077", "BC25E0", "C40BCB", "D4970B", "DC2C26", "E4AAEC", "F460E2", "FC643A"
        };
        for (String p : xiaomiPrefixes) OUI_VENDORS.put(p, "Xiaomi Communications");

        // OPPO / Realme / OnePlus
        String[] oppoPrefixes = {
            "00EC0A", "0CD746", "140C76", "1C77F6", "24E271", "300ED5", "44334C", "509EA7", "60F189", "708A09",
            "8096B1", "90CDB6", "A475B9", "B0D59D", "C85972", "DC5285", "E8641D", "F8A2D6"
        };
        for (String p : oppoPrefixes) OUI_VENDORS.put(p, "OPPO / OnePlus / Realme");

        // Vivo
        String[] vivoPrefixes = {
            "14ABC5", "28FF3E", "38295A", "543AD0", "6CC7EC", "7451BA", "84DBAC", "9487E0", "A086C6", "B83765",
            "D81399", "ECD3BF"
        };
        for (String p : vivoPrefixes) OUI_VENDORS.put(p, "Vivo Mobile");

        // Huawei / Honor
        String[] huaweiPrefixes = {
            "001E10", "00259E", "00464B", "0819A6", "101B54", "1C8E5C", "24DF6A", "342EB6", "404D7F", "4846FB",
            "548998", "60DE44", "70723C", "78D752", "8853D4", "94772B", "A4999B", "B8BC1B", "C4072F", "D02DB3",
            "E0247F", "F4559C"
        };
        for (String p : huaweiPrefixes) OUI_VENDORS.put(p, "Huawei Technologies");

        // Intel (Card Wi-Fi laptop phổ biến nhất: Dell, HP, Lenovo, Asus, Acer...)
        String[] intelPrefixes = {
            "001500", "001E67", "00216A", "002314", "0024D7", "00270E", "081196", "1002B5", "28704E", "3413E8",
            "3CA9F4", "4074E0", "4851B7", "548CA0", "6805CA", "701CE8", "7CB0C2", "8086F2", "88B111", "94E6F7",
            "A0A8CD", "B88584", "C0B883", "C85B76", "D0ABD5", "E82A44", "F4D108", "F894C2"
        };
        for (String p : intelPrefixes) OUI_VENDORS.put(p, "Intel Corporation");

        // TP-Link
        String[] tplinkPrefixes = {
            "000AEB", "001D0F", "002719", "147590", "1C3BF3", "30B5C2", "50C7BF", "54C80F", "6032B1", "704F57",
            "7405A5", "8416F9", "909A4A", "984827", "A42BB0", "B04E26", "B4B024", "C006C3", "D80D17", "E4C32A",
            "F4EC38", "F81A67"
        };
        for (String p : tplinkPrefixes) OUI_VENDORS.put(p, "TP-Link Technologies");

        // Cisco / Meraki (Phổ biến tại trường học, sân bay, bệnh viện, quán cafe lớn)
        String[] ciscoPrefixes = {
            "00000C", "000142", "0001C7", "00044D", "000652", "000A41", "000C85", "001BD4", "002414", "00260B",
            "00270D", "188090", "2C3311", "487746", "68BDAB", "706979", "74A2E6", "88F031", "B4DE31", "C84C75",
            "E05BB5"
        };
        for (String p : ciscoPrefixes) OUI_VENDORS.put(p, "Cisco Systems / Meraki");

        // Aruba Networks (HPE - Rất phổ biến trong Wi-Fi công cộng doanh nghiệp)
        String[] arubaPrefixes = {
            "000B86", "001A1E", "24DEC6", "40E3D6", "6CF37F", "703A0E", "84D47E", "94B40F", "A44C11", "B08BCF",
            "C4B9CD", "D8C7C8", "EC13DB"
        };
        for (String p : arubaPrefixes) OUI_VENDORS.put(p, "Aruba Networks (HPE)");

        // Ubiquiti Networks (UniFi - Phổ biến tại cafe sang trọng, văn phòng, khách sạn)
        String[] ubiquitiPrefixes = {
            "00156D", "002722", "24A43C", "44D9E7", "687251", "7483C2", "788A20", "802AA8", "B4FBE4", "DC9FDB",
            "E063DA", "F09FC2"
        };
        for (String p : ubiquitiPrefixes) OUI_VENDORS.put(p, "Ubiquiti (UniFi AP)");

        // DrayTek (Router phổ biến tại Việt Nam)
        String[] draytekPrefixes = { "001D4C", "00507F", "1C497B" };
        for (String p : draytekPrefixes) OUI_VENDORS.put(p, "DrayTek Corp.");

        // Dell
        String[] dellPrefixes = { "001422", "001E4F", "00219B", "0023AE", "180373", "24B6FD", "3417EB", "44A842", "544810", "74867A", "847BEB", "B82A72", "C8F750", "D4BED9", "E454E8", "F8DB88" };
        for (String p : dellPrefixes) OUI_VENDORS.put(p, "Dell Inc.");

        // HP
        String[] hpPrefixes = { "0001E6", "00110A", "0017A4", "001E0B", "00215A", "101F74", "1CC1DE", "2C59E5", "3C5282", "40A8F0", "5820B1", "705A0F", "80E650", "9CB654", "A0D3C1", "C01803", "C8D3FF", "D8D385" };
        for (String p : hpPrefixes) OUI_VENDORS.put(p, "HP Inc.");

        // ASUS
        String[] asusPrefixes = { "0015F2", "001A92", "001D60", "002618", "04D9F5", "08606E", "107B44", "14DDA9", "1C872C", "2C4D54", "305A3A", "40167E", "54A050", "60A44C", "704D7B", "88D7F6", "AC9E17", "BCEE7B", "D850E6", "E03F49", "F07959" };
        for (String p : asusPrefixes) OUI_VENDORS.put(p, "ASUSTeK Computer");

        // Lenovo
        String[] lenovoPrefixes = { "0021CC", "002699", "28D244", "38BAF8", "54EE75", "6CC217", "80CE62", "A483E7", "E0D55E", "F0761C" };
        for (String p : lenovoPrefixes) OUI_VENDORS.put(p, "Lenovo");

        // Google (Pixel, Chromecast, Nest)
        String[] googlePrefixes = { "001A11", "3C5AB4", "546009", "74427F", "94EBCD", "A47733", "D83C69", "E4F042", "F88FC2" };
        for (String p : googlePrefixes) OUI_VENDORS.put(p, "Google, Inc.");

        // Sony
        String[] sonyPrefixes = { "00014A", "00041F", "001315", "0019C5", "0024BE", "0498F3", "307512", "709E29", "A8E3EE", "BC60A7", "FC0FE6" };
        for (String p : sonyPrefixes) OUI_VENDORS.put(p, "Sony Corporation");

        // LG
        String[] lgPrefixes = { "001C62", "001F6B", "10F96F", "203956", "34FC9F", "58A2B5", "64995D", "88C9D0", "A823FE", "B40EDD", "C4366C", "CC2D8C" };
        for (String p : lgPrefixes) OUI_VENDORS.put(p, "LG Electronics");

        // Espressif / Tuya (Thiết bị IoT, Smart Home trong quán cafe)
        String[] iotPrefixes = { "18FE34", "240AC4", "2462AB", "246F28", "2CF432", "30AEA4", "3C71BF", "483FDA", "500291", "5443B2", "5CCF7F", "600194", "68C63A", "840D8E", "94B97E", "A4CF12", "AC67B2", "B4E62D", "BCDD2C", "C44F33", "CC50E3", "D8A01D", "DC4F22", "E8DB84", "ECFABC" };
        for (String p : iotPrefixes) OUI_VENDORS.put(p, "Thiết bị IoT (Espressif/Tuya)");

        // Raspberry Pi
        String[] rpiPrefixes = { "B827EB", "DCA632", "E45F01", "28CDC1" };
        for (String p : rpiPrefixes) OUI_VENDORS.put(p, "Raspberry Pi Foundation");

        // Microsoft
        String[] msPrefixes = { "00155D", "001D42", "281878", "7CE9D3", "DC5360", "E84B06" };
        for (String p : msPrefixes) OUI_VENDORS.put(p, "Microsoft Surface/Xbox");
    }

    /**
     * Tra cứu nhà sản xuất thiết bị từ địa chỉ MAC (OUI Lookup).
     * Đặc biệt hỗ trợ nhận diện Locally Administered / Randomized MAC Address (bảo mật riêng tư trên điện thoại).
     */
    public static String lookupMacVendor(String mac) {
        if (mac == null || mac.isEmpty() || mac.equals("Chưa rõ MAC") || mac.equals("-")) {
            return "Không rõ";
        }
        String clean = mac.replace(":", "").replace("-", "").toUpperCase();
        if (clean.length() < 6) return "Không rõ";

        // Kiểm tra bit Locally Administered Address (LAA / Randomized MAC):
        // Nếu bit thứ 2 của byte đầu tiên = 1 (ví dụ: x2, x6, xA, xE),
        // đây là địa chỉ MAC ngẫu nhiên bảo vệ riêng tư (Private MAC) mặc định trên iOS 14+ và Android 10+
        try {
            int firstByte = Integer.parseInt(clean.substring(0, 2), 16);
            if ((firstByte & 0x02) != 0) {
                return "Thiết bị di động (MAC riêng tư bảo mật - iOS / Android)";
            }
        } catch (Exception ignored) {}

        String prefix = clean.substring(0, 6);
        String vendor = OUI_VENDORS.get(prefix);
        if (vendor != null) return vendor;

        return "Nhà sản xuất khác";
    }

    /**
     * Giải tên thiết bị kết hợp cả địa chỉ IP và địa chỉ MAC (chuẩn xác cho Wi-Fi công cộng).
     */
    public static DeviceInfo resolve(String ip, String mac) {
        String vendor = lookupMacVendor(mac);
        String initialHint = guessHintFromVendor(vendor);
        DeviceInfo info = new DeviceInfo(ip, ip, vendor, initialHint);

        // 1. Thử NetBIOS NBNS UDP 137 (cực nhanh trên máy Windows)
        DeviceInfo nbns = tryNetBiosName(ip);
        if (nbns != null) {
            info.name = nbns.name;
            info.vendor = "Microsoft";
            info.deviceHint = nbns.deviceHint;
            return info;
        }

        // 2. Thử mDNS unicast tới port 5353 (Apple iOS, macOS, Android, Google Cast)
        String mdnsName = tryMdnsUnicast(ip);
        if (mdnsName != null && !mdnsName.isEmpty()) {
            info.name = mdnsName;
            info.deviceHint = guessDeviceHint(mdnsName, vendor);
            return info;
        }

        // 3. Thử Reverse DNS với timeout ngắn 150ms
        String rdns = tryReverseDnsWithTimeout(ip);
        if (rdns != null && !rdns.equals(ip)) {
            info.name = rdns;
            info.deviceHint = guessDeviceHint(rdns, vendor);
            return info;
        }

        // 4. Thử HTTP Header trên cổng 80 (nhận diện Router, Camera, NAS)
        String httpInfo = tryHttpBanner(ip);
        if (httpInfo != null) {
            info.vendor = httpInfo;
            info.deviceHint = guessDeviceHint(info.name, httpInfo);
            return info;
        }

        // 5. Nếu không có tên qua mạng, sử dụng định danh từ Vendor MAC
        if (!vendor.equals("Không rõ") && !vendor.equals("Nhà sản xuất khác")) {
            info.name = vendor;
        }

        return info;
    }

    /**
     * Phương thức tương thích ngược chỉ truyền IP.
     */
    public static DeviceInfo resolve(String ip) {
        return resolve(ip, null);
    }

    // ─────────────────────────────────────────────────────────────────
    // Gợi ý loại thiết bị từ Vendor MAC
    // ─────────────────────────────────────────────────────────────────
    private static String guessHintFromVendor(String vendor) {
        if (vendor == null) return "Thiết bị Wi-Fi";
        String vLower = vendor.toLowerCase();
        if (vLower.contains("apple")) return "Thiết bị Apple (iPhone / iPad / Mac)";
        if (vLower.contains("samsung")) return "Thiết bị Samsung (Galaxy)";
        if (vLower.contains("xiaomi")) return "Thiết bị Xiaomi / Redmi / POCO";
        if (vLower.contains("oppo")) return "Thiết bị OPPO / OnePlus / Realme";
        if (vLower.contains("vivo")) return "Thiết bị Vivo";
        if (vLower.contains("huawei")) return "Thiết bị Huawei";
        if (vLower.contains("intel")) return "Máy tính Laptop (Intel Wi-Fi)";
        if (vLower.contains("dell") || vLower.contains("lenovo") || vLower.contains("asus") || vLower.contains("hp inc"))
            return "Máy tính Laptop / PC";
        if (vLower.contains("cisco") || vLower.contains("aruba") || vLower.contains("ubiquiti"))
            return "Bộ phát Wi-Fi công cộng (Enterprise AP)";
        if (vLower.contains("tp-link") || vLower.contains("draytek"))
            return "Router / Access Point Wi-Fi";
        if (vLower.contains("mac riêng tư"))
            return "Smartphone (iOS / Android bảo mật)";
        if (vLower.contains("iot") || vLower.contains("espressif"))
            return "Thiết bị IoT / Nhà thông minh";
        if (vLower.contains("google"))
            return "Thiết bị Google (Pixel / Cast)";
        return "Thiết bị Wi-Fi";
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
    // 2. Thử mDNS unicast truy vấn tên máy trực tiếp – UDP port 5353
    // ─────────────────────────────────────────────────────────────────
    private static String tryMdnsUnicast(String ip) {
        byte[] mdnsQuery = new byte[] {
            0x00, 0x00, // ID
            0x00, 0x00, // Flags (standard query)
            0x00, 0x01, // Questions: 1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // _services._dns-sd._udp.local
            0x09, '_', 's', 'e', 'r', 'v', 'i', 'c', 'e', 's',
            0x07, '_', 'd', 'n', 's', '-', 's', 'd',
            0x04, '_', 'u', 'd', 'p',
            0x05, 'l', 'o', 'c', 'a', 'l',
            0x00,
            0x00, 0x0C, // Type: PTR
            0x00, 0x01  // Class: IN
        };

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(100);
            InetAddress addr = InetAddress.getByName(ip);
            DatagramPacket req = new DatagramPacket(mdnsQuery, mdnsQuery.length, addr, 5353);
            socket.send(req);

            byte[] buf = new byte[1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);

            // Phân tích chuỗi có nghĩa từ payload phản hồi mDNS
            String raw = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8);
            String extracted = extractReadableHostName(raw);
            if (extracted != null && !extracted.isEmpty()) {
                return extracted;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String extractReadableHostName(String raw) {
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

    // ─────────────────────────────────────────────────────────────────
    // 3. Reverse DNS có timeout ngắn (tránh nghẽn thread quét)
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
            return future.get(120, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. HTTP Server header grab nhanh trên port 80
    // ─────────────────────────────────────────────────────────────────
    private static String tryHttpBanner(String ip) {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(ip, 80), 100);
            sock.setSoTimeout(100);

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
    // Nhận diện loại thiết bị
    // ─────────────────────────────────────────────────────────────────
    public static String guessDeviceHint(String name, String serverBanner) {
        String n = name == null ? "" : name.toLowerCase();
        String s = serverBanner == null ? "" : serverBanner.toLowerCase();

        // Router / Access Point
        if (s.contains("router") || s.contains("tp-link") || s.contains("asus") || s.contains("d-link")
                || s.contains("netgear") || s.contains("mikrotik") || s.contains("ubiquiti")
                || s.contains("cisco") || s.contains("aruba") || s.contains("draytek")
                || n.contains("router") || n.contains("gateway") || n.contains("ap-")
                || s.contains("openwrt") || s.contains("dd-wrt") || s.contains("modem")) {
            return "Router / Access Point Wi-Fi";
        }
        // Server / NAS
        if (s.contains("nginx") || s.contains("apache") || s.contains("synology")
                || s.contains("qnap") || n.contains("server") || n.contains("nas")) {
            return "Máy chủ / NAS";
        }
        // Camera IP
        if (s.contains("camera") || s.contains("hikvision") || s.contains("dahua")
                || n.contains("cam") || n.contains("ipcam") || s.contains("tapo")) {
            return "Camera IP / CCTV";
        }
        // Apple devices
        if (n.contains("iphone") || n.contains("ipad") || n.contains("macbook")
                || n.contains("apple") || s.contains("apple")) {
            return "Thiết bị Apple (iPhone / iPad / Mac)";
        }
        // Printer
        if (s.contains("printer") || n.contains("printer") || n.contains("print")
                || s.contains("hp-") || s.contains("epson") || s.contains("canon")) {
            return "Máy in mạng Wi-Fi";
        }
        // Windows PC
        if (n.matches("desktop.*") || n.matches("laptop.*") || n.matches("pc.*")
                || n.matches("win.*") || s.contains("microsoft")) {
            return "Máy tính (Windows)";
        }
        // Linux / Android
        if (n.contains("android") || n.contains("galaxy") || n.contains("xiaomi")
                || n.contains("oppo") || n.contains("vivo") || n.contains("huawei") || s.contains("samsung")) {
            return "Điện thoại thông minh (Smartphone)";
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
