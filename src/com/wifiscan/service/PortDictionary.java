package com.wifiscan.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Từ điển các cổng mạng thông dụng và danh sách preset cổng để quét.
 */
public class PortDictionary {

    private static final Map<Integer, String> SERVICES = new HashMap<>();

    static {
        SERVICES.put(20, "FTP (Data Transfer)");
        SERVICES.put(21, "FTP (Control/Command)");
        SERVICES.put(22, "SSH (Secure Shell / SFTP)");
        SERVICES.put(23, "Telnet (Unencrypted Text)");
        SERVICES.put(25, "SMTP (Simple Mail Transfer)");
        SERVICES.put(53, "DNS (Domain Name System)");
        SERVICES.put(67, "DHCP Server");
        SERVICES.put(68, "DHCP Client");
        SERVICES.put(69, "TFTP (Trivial File Transfer)");
        SERVICES.put(80, "HTTP (World Wide Web)");
        SERVICES.put(88, "Kerberos (Authentication)");
        SERVICES.put(110, "POP3 (Post Office Protocol)");
        SERVICES.put(111, "RPCBind / Portmapper");
        SERVICES.put(123, "NTP (Network Time Protocol)");
        SERVICES.put(135, "MS RPC / EPMAP (Windows)");
        SERVICES.put(137, "NetBIOS Name Service");
        SERVICES.put(138, "NetBIOS Datagram");
        SERVICES.put(139, "NetBIOS Session Service");
        SERVICES.put(143, "IMAP (Internet Message Access)");
        SERVICES.put(161, "SNMP (Simple Network Mgmt)");
        SERVICES.put(389, "LDAP (Lightweight Directory)");
        SERVICES.put(443, "HTTPS (HTTP over TLS/SSL)");
        SERVICES.put(445, "SMB (Server Message Block / File Sharing)");
        SERVICES.put(465, "SMTPS (Secure Mail)");
        SERVICES.put(500, "ISAKMP / IKE (IPsec VPN)");
        SERVICES.put(514, "Syslog");
        SERVICES.put(587, "SMTP (Mail Submission)");
        SERVICES.put(636, "LDAPS (Secure LDAP)");
        SERVICES.put(993, "IMAPS (Secure IMAP)");
        SERVICES.put(995, "POP3S (Secure POP3)");
        SERVICES.put(1433, "Microsoft SQL Server");
        SERVICES.put(1521, "Oracle Database");
        SERVICES.put(1723, "PPTP VPN");
        SERVICES.put(1883, "MQTT (IoT Messaging)");
        SERVICES.put(2049, "NFS (Network File System)");
        SERVICES.put(3000, "Node.js / React / Grafana Dev");
        SERVICES.put(3306, "MySQL / MariaDB");
        SERVICES.put(3389, "RDP (Remote Desktop Protocol)");
        SERVICES.put(5000, "UPnP / Synology / Docker Registry / Flask");
        SERVICES.put(5432, "PostgreSQL Database");
        SERVICES.put(5672, "RabbitMQ");
        SERVICES.put(5900, "VNC Remote Desktop");
        SERVICES.put(6379, "Redis Key-Value Store");
        SERVICES.put(8000, "HTTP Alternate / Django / Spring");
        SERVICES.put(8080, "HTTP Alternate / Apache Tomcat / Proxy");
        SERVICES.put(8443, "HTTPS Alternate / Tomcat SSL");
        SERVICES.put(8888, "HTTP Alternate / Jupyter Notebook");
        SERVICES.put(9000, "Portainer / SonarQube / PHP-FPM");
        SERVICES.put(9090, "Prometheus / Cockpit Web UI");
        SERVICES.put(9200, "Elasticsearch REST API");
        SERVICES.put(27017, "MongoDB NoSQL Database");
    }

    public static final int[] COMMON_PORTS = {
            21, 22, 23, 25, 53, 80, 110, 135, 139, 143,
            443, 445, 993, 995, 1433, 1521, 3306, 3389,
            5000, 5432, 5900, 6379, 8000, 8080, 8443,
            8888, 9000, 9200, 27017
    };

    public static final int[] WEB_PORTS = {
            80, 81, 443, 3000, 5000, 8000, 8080, 8443, 8888, 9000, 9090
    };

    public static final int[] DATABASE_PORTS = {
            1433, 1521, 3306, 5432, 6379, 9200, 27017
    };

    public static String getServiceName(int port) {
        return SERVICES.getOrDefault(port, "Dịch vụ tùy chỉnh / Không rõ");
    }

    public static Map<Integer, String> getAllServices() {
        return Collections.unmodifiableMap(SERVICES);
    }
}
