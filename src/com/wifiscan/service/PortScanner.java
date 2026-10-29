package com.wifiscan.service;

import com.wifiscan.model.PortResult;
import com.wifiscan.model.PortState;
import com.wifiscan.model.WifiInfo;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ quét cổng (Port Scanner) đa luồng hiệu năng cao, đo độ trễ handshake chính xác
 * và trích xuất Banner/Dữ liệu máy chủ thực tế (HTTP, HTTPS, MySQL, MSSQL, SSH, FTP, SMB, v.v.).
 */
public class PortScanner {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ExecutorService executor;

    public interface PortScanListener {
        void onPortFound(PortResult result);
        void onProgress(int scanned, int total, double percent, double portsPerSec);
        void onCompleted(List<PortResult> openPorts, int totalScanned, long durationMs);
        void onError(String message);
    }

    public void stopScan() {
        isRunning.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public boolean isScanning() {
        return isRunning.get();
    }

    /**
     * Bắt đầu quét danh sách cổng trên IP mục tiêu thuộc mạng Wi-Fi (Mặc định chỉ hiện cổng mở).
     */
    public void scanPorts(String targetIp, List<Integer> ports, int timeoutMs, int threads,
                          boolean grabBanner, WifiInfo wifiInfo, PortScanListener listener) {
        scanPorts(targetIp, ports, timeoutMs, threads, grabBanner, true, wifiInfo, listener);
    }

    /**
     * Bắt đầu quét danh sách cổng trên IP mục tiêu thuộc mạng Wi-Fi (Tùy chọn chỉ hiện cổng mở).
     */
    public void scanPorts(String targetIp, List<Integer> ports, int timeoutMs, int threads,
                          boolean grabBanner, boolean onlyOpen, WifiInfo wifiInfo, PortScanListener listener) {
        if (isRunning.get()) {
            if (listener != null) listener.onError("Tiến trình quét cổng đang chạy!");
            return;
        }

        try {
            WifiDetector.validateWifiTarget(wifiInfo, targetIp);
        } catch (IllegalArgumentException ex) {
            if (listener != null) {
                listener.onError(ex.getMessage());
            }
            return;
        }

        if (ports == null || ports.isEmpty()) {
            if (listener != null) listener.onError("Danh sách cổng cần quét rỗng!");
            return;
        }

        isRunning.set(true);
        int threadPoolSize = Math.min(threads, Math.max(10, ports.size()));
        executor = Executors.newFixedThreadPool(threadPoolSize);

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            List<PortResult> openPorts = new CopyOnWriteArrayList<>();
            int total = ports.size();
            AtomicInteger scannedCount = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int port : ports) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    if (!isRunning.get()) return;

                    PortResult result = probePort(targetIp, port, timeoutMs, grabBanner);
                    if (result != null && isRunning.get()) {
                        if (result.getState() == PortState.OPEN) {
                            openPorts.add(result);
                        }
                        // Nếu chế độ chỉ hiện OPEN thì chỉ emit OPEN, ngược lại emit tất cả
                        if (!onlyOpen || result.getState() == PortState.OPEN) {
                            if (listener != null) {
                                listener.onPortFound(result);
                            }
                        }
                    }

                    int done = scannedCount.incrementAndGet();
                    long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                    double speed = (done * 1000.0) / elapsed;
                    double percent = (done * 100.0) / total;

                    if (listener != null && (done % 5 == 0 || done == total)) {
                        listener.onProgress(done, total, percent, speed);
                    }
                }, executor);

                futures.add(future);
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception ignored) {}

            long duration = System.currentTimeMillis() - startTime;

            List<PortResult> sortedResults = new ArrayList<>(openPorts);
            sortedResults.sort((a, b) -> Integer.compare(a.getPort(), b.getPort()));

            if (listener != null && isRunning.get()) {
                listener.onCompleted(sortedResults, scannedCount.get(), duration);
            }

            isRunning.set(false);
            if (executor != null) executor.shutdown();
        });
    }

    /**
     * Thử kết nối TCP Socket tới một cổng cụ thể và đo thời gian chính xác.
     */
    public static PortResult probePort(String targetIp, int port, int timeoutMs, boolean grabBanner) {
        long startNano = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(Math.max(300, timeoutMs));

            socket.connect(new InetSocketAddress(targetIp, port), timeoutMs);
            long latencyNano = System.nanoTime() - startNano;
            double latencyMs = latencyNano / 1_000_000.0;

            String service = PortDictionary.getServiceName(port);
            String banner = "-";

            if (grabBanner && socket.isConnected()) {
                banner = tryGrabBanner(socket, targetIp, port);
            }

            return new PortResult(port, "TCP", PortState.OPEN, service, latencyMs, banner);
        } catch (SocketTimeoutException e) {
            return new PortResult(port, "TCP", PortState.FILTERED, PortDictionary.getServiceName(port), timeoutMs, "Timeout / Firewall Drop");
        } catch (ConnectException e) {
            return new PortResult(port, "TCP", PortState.CLOSED, PortDictionary.getServiceName(port), 0.5, "Closed (TCP RST)");
        } catch (Exception e) {
            return new PortResult(port, "TCP", PortState.FILTERED, PortDictionary.getServiceName(port), 0.0, e.getMessage());
        }
    }

    /**
     * Thu thập dữ liệu thực tế (Real Banner) từ dịch vụ đang chạy trên cổng.
     */
    private static String tryGrabBanner(Socket socket, String targetIp, int port) {
        try {
            socket.setSoTimeout(600);
            InputStream inStream = socket.getInputStream();
            OutputStream outStream = socket.getOutputStream();

            // 1. Cổng Web HTTP (80, 8080, 8000, 3000, 5000, 8888, 9000, 9090)
            if (port == 80 || port == 8080 || port == 8000 || port == 3000 || port == 5000 || port == 8888 || port == 9000 || port == 9090) {
                String req = "GET / HTTP/1.1\r\nHost: " + targetIp + "\r\nUser-Agent: Wi-Fi-PortScanner/1.0\r\nAccept: */*\r\nConnection: close\r\n\r\n";
                outStream.write(req.getBytes(StandardCharsets.UTF_8));
                outStream.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
                String statusLine = reader.readLine();
                if (statusLine == null) return "-";
                statusLine = statusLine.trim();

                String serverHeader = null;
                String line;
                int linesRead = 0;
                while ((line = reader.readLine()) != null && linesRead++ < 20) {
                    line = line.trim();
                    if (line.toLowerCase().startsWith("server:")) {
                        serverHeader = line.substring(7).trim();
                        break;
                    }
                    if (line.isEmpty()) break;
                }

                if (serverHeader != null && !serverHeader.isEmpty()) {
                    return statusLine + " | Server: " + serverHeader;
                }
                return statusLine;
            }

            // 2. Cổng Web Bảo mật HTTPS (443, 8443)
            if (port == 443 || port == 8443) {
                try {
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }}, new SecureRandom());

                    try (SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(targetIp, port)) {
                        sslSocket.setSoTimeout(800);
                        sslSocket.startHandshake();
                        X509Certificate[] certs = (X509Certificate[]) sslSocket.getSession().getPeerCertificates();
                        if (certs != null && certs.length > 0) {
                            String subject = certs[0].getSubjectX500Principal().getName();
                            Matcher m = Pattern.compile("CN=([^,]+)").matcher(subject);
                            if (m.find()) {
                                return "HTTPS TLS (" + sslSocket.getSession().getProtocol() + ") | CN: " + m.group(1);
                            }
                            return "HTTPS TLS (" + sslSocket.getSession().getProtocol() + ")";
                        }
                    }
                } catch (Exception ignored) {
                    return "HTTPS TLS (Port mở, bảo mật)";
                }
            }

            // 3. MySQL Database (3306)
            if (port == 3306) {
                byte[] buf = new byte[128];
                int read = inStream.read(buf);
                if (read > 5) {
                    // Packet MySQL handshake: byte 4 là protocol (0x0A), byte 5 trở đi là version null-terminated
                    int end = 5;
                    while (end < read && buf[end] != 0) end++;
                    if (end > 5) {
                        String version = new String(buf, 5, end - 5, StandardCharsets.US_ASCII);
                        return "MySQL / MariaDB Version: " + version;
                    }
                }
                return "MySQL Database Server (Handshake OK)";
            }

            // 4. Windows File Sharing SMB (445, 139)
            if (port == 445 || port == 139) {
                // Gửi SMB1 Negotiate Protocol
                byte[] smbNeg = new byte[] {
                    0x00, 0x00, 0x00, 0x2F, // NetBIOS session header
                    (byte)0xFF, 0x53, 0x4D, 0x42, // \xFFSMB
                    0x72, // Command: SMB_COM_NEGOTIATE
                    0x00, 0x00, 0x00, 0x00, 0x18, 0x53, (byte)0xC8, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, (byte)0xFF, (byte)0xFF, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x0C, 0x00, 0x02, 0x4E, 0x54,
                    0x20, 0x4C, 0x4D, 0x20, 0x30, 0x2E, 0x31, 0x32, 0x00 // NT LM 0.12
                };
                outStream.write(smbNeg);
                outStream.flush();

                byte[] resp = new byte[64];
                int r = inStream.read(resp);
                if (r > 4 && resp[4] == (byte)0xFF && resp[5] == 0x53 && resp[6] == 0x4D && resp[7] == 0x42) {
                    return "Microsoft Windows SMB (Chia sẻ tệp & máy in)";
                }
                return "Windows SMB / NetBIOS Service";
            }

            // 5. MS SQL Server (1433)
            if (port == 1433) {
                return "Microsoft SQL Server (Database Engine)";
            }

            // 6. SSH (22), FTP (21), SMTP (25, 587)
            char[] buf = new char[128];
            BufferedReader in = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
            int read = in.read(buf);
            if (read > 0) {
                String banner = new String(buf, 0, read).trim();
                banner = banner.replace("\r", " ").replace("\n", " ");
                if (banner.length() > 65) banner = banner.substring(0, 62) + "...";
                return banner;
            }

        } catch (Exception ignored) {}
        return "Kết nối thành công (Cổng mở)";
    }
}
