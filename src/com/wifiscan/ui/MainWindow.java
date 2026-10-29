package com.wifiscan.ui;

import com.wifiscan.model.*;
import com.wifiscan.service.*;
import com.wifiscan.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Cửa sổ giao diện chính của ứng dụng Quét Cổng và Đo Khoảng Cách Wi-Fi.
 */
public class MainWindow extends JFrame {

    private WifiInfo currentWifi;
    private final WifiDetector wifiDetector = new WifiDetector();
    private final LanScanner lanScanner = new LanScanner();
    private final PortScanner portScanner = new PortScanner();

    // UI Components
    private final WifiBadge wifiBadge = new WifiBadge();
    private final JTabbedPane tabbedPane = new JTabbedPane();

    // Tab 1: Quét LAN
    private final ModernButton btnScanLan = new ModernButton("Bắt đầu quét Wi-Fi", Theme.ACCENT_INDIGO);
    private final ModernButton btnStopLan = new ModernButton("Dừng quét", Theme.DANGER_RED);
    private final JProgressBar lanProgressBar = new JProgressBar(0, 100);
    private final JLabel lblLanProgress = new JLabel("Sẵn sàng quét mạng");
    private final JTextField txtSearchDevice = new JTextField(20);
    private final JLabel lblSearchResult = new JLabel("");
    private DefaultTableModel deviceTableModel;
    private ModernTable deviceTable;
    private TableRowSorter<DefaultTableModel> deviceSorter;
    private final List<NetworkDevice> discoveredDevices = new ArrayList<>();

    // Tab 2: Quét Port
    private final JTextField txtTargetIp = new JTextField(15);
    private final JTextField txtPorts = new JTextField(25);
    private final JSpinner spinTimeout = new JSpinner(new SpinnerNumberModel(300, 50, 3000, 50));
    private final JSpinner spinThreads = new JSpinner(new SpinnerNumberModel(40, 5, 200, 5));
    private final JCheckBox chkGrabBanner = new JCheckBox("Lấy Banner dịch vụ", true);
    private final JCheckBox chkOnlyOpen = new JCheckBox("Chỉ hiện cổng MỞ", true);
    private final ModernButton btnStartPortScan = new ModernButton("Bắt đầu quét Port", Theme.SUCCESS_GREEN);
    private final ModernButton btnStopPortScan = new ModernButton("Dừng quét", Theme.DANGER_RED);
    private final JProgressBar portProgressBar = new JProgressBar(0, 100);
    private final JLabel lblPortStats = new JLabel("Cổng mở: 0 | Đã quét: 0/0 | Tốc độ: 0 ports/s");
    private DefaultTableModel portTableModel;
    private ModernTable portTable;
    private final List<PortResult> currentOpenPorts = new ArrayList<>();

    // Tab 3: Khoảng cách & Radar
    private final RadarPanel radarPanel = new RadarPanel();
    private final DistanceCard distanceCard = new DistanceCard();

    // Tab 4: Logs & Export
    private final JTextArea txtLogs = new JTextArea();
    private final ModernButton btnExportTxt = new ModernButton("Xuất file TXT", Theme.BG_CARD);
    private final ModernButton btnExportCsv = new ModernButton("Xuất file CSV", Theme.BG_CARD);
    private final ModernButton btnExportJson = new ModernButton("Xuất file JSON", Theme.BG_CARD);
    private final ModernButton btnClearLogs = new ModernButton("Xóa nhật ký", Theme.BG_CARD);

    // Tab 5: Quét Wi-Fi Công Cộng
    private final PublicWifiScanner publicWifiScanner = new PublicWifiScanner();
    private final ModernButton btnScanPublic = new ModernButton("Quet Wi-Fi Xung Quanh", new Color(16, 185, 129));
    private final ModernButton btnStopPublic = new ModernButton("Dung Quet", Theme.DANGER_RED);
    private final ModernButton btnCheckPortal = new ModernButton("Kiem tra Captive Portal", new Color(99, 102, 241));
    private final ModernButton btnScanDevicesInPublic = new ModernButton("Dò thiết bị trong mạng này", Theme.ACCENT_CYAN);
    private final JProgressBar publicScanProgress = new JProgressBar(0, 100);
    private final JLabel lblPublicStatus = new JLabel("San sang quet Wi-Fi cong cong...");
    private final JLabel lblPublicSummary = new JLabel(" ");
    private DefaultTableModel publicTableModel;
    private ModernTable publicTable;
    private final List<PublicWifiNetwork> scannedNetworks = new ArrayList<>();

    public MainWindow() {
        super("Wi-Fi Port Scanner & Distance Estimator (LTM)");
        initWindow();
        initUI();
        refreshWifiInfo();
    }

    private void initWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 780);
        setMinimumSize(new Dimension(950, 650));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG_DARK);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // 1. Header: Tiêu đề và Thanh trạng thái Wi-Fi
        JPanel northPanel = new JPanel(new BorderLayout(0, 8));
        northPanel.setOpaque(false);
        northPanel.setBorder(new EmptyBorder(12, 16, 4, 16));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JLabel lblTitle = new JLabel("ỨNG DỤNG QUÉT CỔNG & ƯỚC TÍNH KHOẢNG CÁCH WI-FI");
        lblTitle.setFont(Theme.FONT_TITLE);
        lblTitle.setForeground(Theme.TEXT_MAIN);

        JLabel lblSub = new JLabel("Phát hiện thiết bị Wi-Fi • Port Scan đa luồng • Mô hình suy hao sóng & RTT (Java)");
        lblSub.setFont(Theme.FONT_REGULAR);
        lblSub.setForeground(Theme.TEXT_MUTED);

        JPanel titleTextPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titleTextPanel.setOpaque(false);
        titleTextPanel.add(lblTitle);
        titleTextPanel.add(lblSub);

        ModernButton btnRefreshWifi = new ModernButton("Làm mới Wi-Fi", new Color(51, 65, 85));
        btnRefreshWifi.addActionListener(e -> refreshWifiInfo());

        titlePanel.add(titleTextPanel, BorderLayout.WEST);
        titlePanel.add(btnRefreshWifi, BorderLayout.EAST);

        northPanel.add(titlePanel, BorderLayout.NORTH);
        northPanel.add(wifiBadge, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // 2. Tabs
        setupTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        // 3. Status Bar ở đáy
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(24, 33, 47));
        statusBar.setBorder(new EmptyBorder(6, 16, 6, 16));

        JLabel lblStatusMsg = new JLabel("Hệ thống: Sẵn sàng | Chế độ bảo vệ: Chỉ quét trong mạng Wi-Fi");
        lblStatusMsg.setFont(Theme.FONT_REGULAR);
        lblStatusMsg.setForeground(Theme.TEXT_MUTED);

        statusBar.add(lblStatusMsg, BorderLayout.WEST);
        add(statusBar, BorderLayout.SOUTH);
    }

    private void setupTabbedPane() {
        tabbedPane.setBackground(Theme.BG_DARK);
        tabbedPane.setForeground(Theme.TEXT_MAIN);
        tabbedPane.setFont(Theme.FONT_BOLD);

        tabbedPane.addTab("  1. Do Thiet Bi Wi-Fi  ", createLanTab());
        tabbedPane.addTab("  2. Quet Cong (Port Scan)  ", createPortTab());
        tabbedPane.addTab("  3. Do Khoang Cach & Radar  ", createDistanceTab());
        tabbedPane.addTab("  4. Nhat Ky & Xuat File  ", createLogTab());
        tabbedPane.addTab("  5. Quet Wi-Fi Cong Cong  ", createPublicWifiTab());
    }

    // ==========================================
    // TAB 1: DÒ THIẾT BỊ TRONG MẠNG WI-FI
    // ==========================================
    private JPanel createLanTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Control Panel
        JPanel ctrl = new JPanel(new BorderLayout(10, 8));
        ctrl.setBackground(Theme.BG_CARD);
        ctrl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JPanel leftCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftCtrl.setOpaque(false);
        leftCtrl.add(btnScanLan);
        btnStopLan.setEnabled(false);
        leftCtrl.add(btnStopLan);

        ModernButton btnSelectForPortScan = new ModernButton("Chọn IP này để quét Cổng", Theme.ACCENT_CYAN);
        btnSelectForPortScan.addActionListener(e -> selectCurrentDeviceForPortScan());
        leftCtrl.add(btnSelectForPortScan);

        ModernButton btnSelectForDistance = new ModernButton("Xem chi tiết khoảng cách", new Color(147, 51, 234));
        btnSelectForDistance.addActionListener(e -> selectCurrentDeviceForDistance());
        leftCtrl.add(btnSelectForDistance);

        // ── Thanh tiến trình ──
        JPanel progPanel = new JPanel(new BorderLayout(5, 5));
        progPanel.setOpaque(false);
        lanProgressBar.setStringPainted(true);
        lanProgressBar.setBackground(Theme.BG_INPUT);
        lanProgressBar.setForeground(Theme.ACCENT_INDIGO);
        lblLanProgress.setFont(Theme.FONT_REGULAR);
        lblLanProgress.setForeground(Theme.TEXT_MUTED);
        progPanel.add(lanProgressBar, BorderLayout.CENTER);
        progPanel.add(lblLanProgress, BorderLayout.SOUTH);

        // ── Layout ctrl: Nút bấm → Thanh tiến trình ──
        JPanel ctrlWrapper = new JPanel();
        ctrlWrapper.setOpaque(false);
        ctrlWrapper.setLayout(new BoxLayout(ctrlWrapper, BoxLayout.Y_AXIS));

        leftCtrl.setAlignmentX(Component.LEFT_ALIGNMENT);
        ctrlWrapper.add(leftCtrl);
        ctrlWrapper.add(Box.createVerticalStrut(8));

        progPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ctrlWrapper.add(progPanel);

        ctrl.add(ctrlWrapper, BorderLayout.CENTER);
        panel.add(ctrl, BorderLayout.NORTH);


        // Table
        String[] cols = {"Địa chỉ IP", "Tên thiết bị / Loại", "Địa chỉ MAC", "Phân loại", "RTT (ms)", "Khoảng cách ước tính"};

        deviceTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        deviceTable = new ModernTable(deviceTableModel);
        deviceSorter = new TableRowSorter<>(deviceTableModel);
        deviceTable.setRowSorter(deviceSorter);

        deviceTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                NetworkDevice dev = getSelectedDevice();
                if (dev != null) {
                    distanceCard.displayDeviceDistance(dev, currentWifi);
                    radarPanel.setSelectedDevice(dev);
                    txtTargetIp.setText(dev.getIpAddress());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(deviceTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        // ── Thanh tìm kiếm nhanh thiết bị trong mạng Wi-Fi ──
        JPanel searchBar = new JPanel(new BorderLayout(10, 0));
        searchBar.setBackground(Theme.BG_CARD);
        searchBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(6, 12, 6, 12)
        ));

        JPanel searchInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchInputPanel.setOpaque(false);

        JLabel lblSearchIcon = new JLabel("🔍 Tìm kiếm:");
        lblSearchIcon.setFont(Theme.FONT_BOLD);
        lblSearchIcon.setForeground(Theme.ACCENT_CYAN);

        txtSearchDevice.setPreferredSize(new Dimension(300, 30));
        txtSearchDevice.setFont(Theme.FONT_REGULAR);
        txtSearchDevice.setBackground(Theme.BG_INPUT);
        txtSearchDevice.setForeground(Theme.TEXT_MAIN);
        txtSearchDevice.setCaretColor(Color.WHITE);
        txtSearchDevice.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(3, 8, 3, 8)
        ));
        txtSearchDevice.setToolTipText("Tìm theo IP, Tên thiết bị, Hãng (Apple, Samsung...), MAC...");

        ModernButton btnClearSearch = new ModernButton("Xóa tìm", new Color(51, 65, 85));
        btnClearSearch.setPreferredSize(new Dimension(75, 30));
        btnClearSearch.addActionListener(e -> {
            txtSearchDevice.setText("");
            filterDevices("");
        });

        lblSearchResult.setFont(Theme.FONT_REGULAR);
        lblSearchResult.setForeground(Theme.TEXT_MUTED);

        searchInputPanel.add(lblSearchIcon);
        searchInputPanel.add(txtSearchDevice);
        searchInputPanel.add(btnClearSearch);
        searchInputPanel.add(lblSearchResult);

        searchBar.add(searchInputPanel, BorderLayout.CENTER);

        txtSearchDevice.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterDevices(txtSearchDevice.getText()); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterDevices(txtSearchDevice.getText()); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterDevices(txtSearchDevice.getText()); }
        });

        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);
        centerPanel.add(searchBar, BorderLayout.NORTH);
        centerPanel.add(scroll, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);

        // Events
        btnScanLan.addActionListener(e -> startLanScan());
        btnStopLan.addActionListener(e -> stopLanScan());

        return panel;
    }

    // ==========================================
    // TAB 2: QUÉT CỔNG (PORT SCANNER)
    // ==========================================
    private JPanel createPortTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Top Configuration Card
        JPanel configCard = new JPanel(new BorderLayout(10, 10));
        configCard.setBackground(Theme.BG_CARD);
        configCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // Hàng 1: IP mục tiêu và nút kiểm tra
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
        row1.setOpaque(false);

        JLabel lblIp = new JLabel("IP Mục tiêu (Wi-Fi):");
        lblIp.setFont(Theme.FONT_BOLD);
        txtTargetIp.setFont(Theme.FONT_MONO_BOLD);
        txtTargetIp.setBackground(Theme.BG_INPUT);
        txtTargetIp.setForeground(Color.WHITE);
        txtTargetIp.setCaretColor(Color.WHITE);
        txtTargetIp.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel lblPortsTitle = new JLabel("Danh sách Cổng:");
        lblPortsTitle.setFont(Theme.FONT_BOLD);
        txtPorts.setFont(Theme.FONT_MONO);
        txtPorts.setBackground(Theme.BG_INPUT);
        txtPorts.setForeground(Color.WHITE);
        txtPorts.setCaretColor(Color.WHITE);
        txtPorts.setText(formatPortList(PortDictionary.COMMON_PORTS));
        txtPorts.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        row1.add(lblIp);
        row1.add(txtTargetIp);
        row1.add(lblPortsTitle);
        row1.add(txtPorts);

        // Hàng 2: Nút chọn Preset cổng nhanh
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        row2.setOpaque(false);

        JLabel lblPreset = new JLabel("Preset nhanh:");
        lblPreset.setFont(Theme.FONT_REGULAR);
        lblPreset.setForeground(Theme.TEXT_MUTED);
        row2.add(lblPreset);

        ModernButton btnCommon = new ModernButton("Top 30 Cổng phổ biến", new Color(51, 65, 85));
        btnCommon.addActionListener(e -> txtPorts.setText(formatPortList(PortDictionary.COMMON_PORTS)));

        ModernButton btnRouter = new ModernButton("Router Wi-Fi (80, 443, 53...)", new Color(30, 58, 138));
        btnRouter.addActionListener(e -> {
            if (currentWifi != null && currentWifi.getGatewayIp() != null) {
                txtTargetIp.setText(currentWifi.getGatewayIp());
            }
            txtPorts.setText("80, 443, 53, 1900, 8080, 8443");
        });

        ModernButton btnLocalMachine = new ModernButton("Máy tính này (135, 445, 1433, 3306...)", new Color(13, 148, 136));
        btnLocalMachine.addActionListener(e -> {
            if (currentWifi != null && currentWifi.getLocalIp() != null) {
                txtTargetIp.setText(currentWifi.getLocalIp());
            }
            txtPorts.setText("135, 139, 445, 1433, 3306, 5040, 5357, 7680, 8080");
        });

        ModernButton btnMobile = new ModernButton("Điện thoại (5555, 8080, 5000...)", new Color(124, 58, 237));
        btnMobile.addActionListener(e -> txtPorts.setText("80, 443, 5000, 5555, 7000, 8000, 8080, 8888, 62078"));

        ModernButton btnWeb = new ModernButton("Cổng Web", new Color(51, 65, 85));
        btnWeb.addActionListener(e -> txtPorts.setText(formatPortList(PortDictionary.WEB_PORTS)));

        ModernButton btn1024 = new ModernButton("Dải 1 - 1024", new Color(51, 65, 85));
        btn1024.addActionListener(e -> txtPorts.setText("1-1024"));

        row2.add(btnCommon);
        row2.add(btnRouter);
        row2.add(btnLocalMachine);
        row2.add(btnMobile);
        row2.add(btnWeb);
        row2.add(btn1024);

        // Hàng 3: Timeout, Số luồng, Banner và Nút Quét
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
        row3.setOpaque(false);

        JLabel lblTimeout = new JLabel("Timeout (ms):");
        lblTimeout.setFont(Theme.FONT_REGULAR);

        JLabel lblThreads = new JLabel("Số luồng:");
        lblThreads.setFont(Theme.FONT_REGULAR);

        chkGrabBanner.setFont(Theme.FONT_REGULAR);
        chkGrabBanner.setForeground(Theme.TEXT_MAIN);
        chkGrabBanner.setOpaque(false);

        chkOnlyOpen.setFont(Theme.FONT_REGULAR);
        chkOnlyOpen.setForeground(Theme.TEXT_MAIN);
        chkOnlyOpen.setOpaque(false);

        btnStopPortScan.setEnabled(false);

        row3.add(lblTimeout);
        row3.add(spinTimeout);
        row3.add(lblThreads);
        row3.add(spinThreads);
        row3.add(chkGrabBanner);
        row3.add(chkOnlyOpen);
        row3.add(Box.createHorizontalStrut(10));
        row3.add(btnStartPortScan);
        row3.add(btnStopPortScan);

        JPanel configRows = new JPanel(new GridLayout(3, 1, 0, 4));
        configRows.setOpaque(false);
        configRows.add(row1);
        configRows.add(row2);
        configRows.add(row3);

        // Thanh tiến trình Port
        JPanel portProgPanel = new JPanel(new BorderLayout(5, 4));
        portProgPanel.setOpaque(false);
        portProgressBar.setStringPainted(true);
        portProgressBar.setBackground(Theme.BG_INPUT);
        portProgressBar.setForeground(Theme.SUCCESS_GREEN);

        lblPortStats.setFont(Theme.FONT_REGULAR);
        lblPortStats.setForeground(Theme.TEXT_MUTED);

        portProgPanel.add(portProgressBar, BorderLayout.CENTER);
        portProgPanel.add(lblPortStats, BorderLayout.SOUTH);

        configCard.add(configRows, BorderLayout.CENTER);
        configCard.add(portProgPanel, BorderLayout.SOUTH);

        panel.add(configCard, BorderLayout.NORTH);

        // Bảng kết quả cổng mở
        String[] portCols = {"Số Port", "Giao thức", "Trạng thái", "Tên dịch vụ", "Độ trễ RTT (ms)", "Banner / Chi tiết máy chủ"};
        portTableModel = new DefaultTableModel(portCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        portTable = new ModernTable(portTableModel);

        JScrollPane scroll = new JScrollPane(portTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        panel.add(scroll, BorderLayout.CENTER);

        // Events
        btnStartPortScan.addActionListener(e -> startPortScan());
        btnStopPortScan.addActionListener(e -> stopPortScan());

        return panel;
    }

    // ==========================================
    // TAB 3: ĐO KHOẢNG CÁCH & RADAR
    // ==========================================
    private JPanel createDistanceTab() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 12, 12));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Bên trái: Màn hình Radar
        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(Theme.BG_CARD);
        left.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel lblRadarTitle = new JLabel("Bản đồ Radar Khoảng Cách Vô Tuyến 360°");
        lblRadarTitle.setFont(Theme.FONT_TITLE);
        lblRadarTitle.setForeground(Theme.TEXT_MAIN);
        lblRadarTitle.setBorder(new EmptyBorder(0, 4, 8, 0));

        left.add(lblRadarTitle, BorderLayout.NORTH);
        left.add(radarPanel, BorderLayout.CENTER);

        // Bên phải: Thẻ hiển thị số liệu chi tiết
        distanceCard.setOnRecalculateListener(est -> {
            radarPanel.repaint();
            updateDeviceDistanceInTable(est);
        });

        panel.add(left);
        panel.add(distanceCard);

        return panel;
    }

    // ==========================================
    // TAB 5: QUET WI-FI CONG CONG
    // ==========================================
    private JPanel createPublicWifiTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // ── Control card ──
        JPanel ctrlCard = new JPanel(new BorderLayout(10, 8));
        ctrlCard.setBackground(Theme.BG_CARD);
        ctrlCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        // Tiêu đề card
        JLabel lblCardTitle = new JLabel("QUET WI-FI CONG CONG - Du lieu thuc te tu netsh wlan");
        lblCardTitle.setFont(Theme.FONT_BOLD);
        lblCardTitle.setForeground(new Color(16, 185, 129));
        lblCardTitle.setBorder(new EmptyBorder(0, 0, 6, 0));

        // Nút bấm
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnStopPublic.setEnabled(false);
        btnRow.add(btnScanPublic);
        btnRow.add(btnStopPublic);
        btnRow.add(btnCheckPortal);
        btnRow.add(btnScanDevicesInPublic);

        // Chú giải màu bảo mật
        JPanel legendRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        legendRow.setOpaque(false);
        addLegendItem(legendRow, new Color(239, 68, 68),   "MO - Khong bao mat (Open/None)");
        addLegendItem(legendRow, new Color(251, 191, 36),  "WPA / WEP - Yeu");
        addLegendItem(legendRow, new Color(16, 185, 129),  "WPA2 / WPA3 - An toan");
        addLegendItem(legendRow, new Color(99, 102, 241),  "Mang hien tai");

        // Thanh tiến trình
        publicScanProgress.setStringPainted(true);
        publicScanProgress.setBackground(Theme.BG_INPUT);
        publicScanProgress.setForeground(new Color(16, 185, 129));
        lblPublicStatus.setFont(Theme.FONT_REGULAR);
        lblPublicStatus.setForeground(Theme.TEXT_MUTED);

        JPanel ctrlContent = new JPanel();
        ctrlContent.setOpaque(false);
        ctrlContent.setLayout(new BoxLayout(ctrlContent, BoxLayout.Y_AXIS));
        lblCardTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        legendRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblPublicStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        publicScanProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
        ctrlContent.add(lblCardTitle);
        ctrlContent.add(Box.createVerticalStrut(6));
        ctrlContent.add(btnRow);
        ctrlContent.add(Box.createVerticalStrut(6));
        ctrlContent.add(legendRow);
        ctrlContent.add(Box.createVerticalStrut(8));
        ctrlContent.add(publicScanProgress);
        ctrlContent.add(Box.createVerticalStrut(4));
        ctrlContent.add(lblPublicStatus);

        ctrlCard.add(ctrlContent, BorderLayout.CENTER);
        panel.add(ctrlCard, BorderLayout.NORTH);

        // ── Bảng kết quả ──
        String[] cols = {
            "SSID (Ten mang)", "BSSID (MAC AP)", "Bao mat", "Ma hoa", "Tin hieu",
            "RSSI (dBm)", "Tan so / Kenh", "Clients / Kenh%", "Chuan", "Trang thai"
        };
        publicTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        publicTable = new ModernTable(publicTableModel);

        // Renderer tô màu theo bảo mật
        publicTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                setFont(Theme.FONT_REGULAR);
                if (!isSelected && row < scannedNetworks.size()) {
                    PublicWifiNetwork net = scannedNetworks.get(row);
                    if (net.isCurrentNetwork()) {
                        c.setBackground(new Color(49, 46, 129, 180));
                        c.setForeground(new Color(196, 181, 253));
                    } else if (net.isOpen()) {
                        c.setBackground(new Color(127, 29, 29, 150));
                        c.setForeground(new Color(252, 165, 165));
                    } else if (net.getAuthentication() != null &&
                            (net.getAuthentication().contains("WPA2") || net.getAuthentication().contains("WPA3"))) {
                        c.setBackground(Theme.BG_CARD);
                        c.setForeground(new Color(110, 231, 183));
                    } else {
                        c.setBackground(new Color(78, 68, 24, 100));
                        c.setForeground(new Color(253, 224, 71));
                    }
                } else if (isSelected) {
                    c.setBackground(Theme.ACCENT_INDIGO);
                    c.setForeground(Color.WHITE);
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(publicTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        panel.add(scroll, BorderLayout.CENTER);

        // ── Footer tóm tắt ──
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(15, 23, 42));
        footer.setBorder(new EmptyBorder(6, 12, 6, 12));
        lblPublicSummary.setFont(Theme.FONT_REGULAR);
        lblPublicSummary.setForeground(new Color(100, 116, 139));
        footer.add(lblPublicSummary, BorderLayout.WEST);
        panel.add(footer, BorderLayout.SOUTH);

        // ── Events ──
        btnScanPublic.addActionListener(e -> startPublicWifiScan());
        btnStopPublic.addActionListener(e -> stopPublicWifiScan());
        btnCheckPortal.addActionListener(e -> checkPortalForSelected());
        btnScanDevicesInPublic.addActionListener(e -> scanDevicesFromPublicTab());

        return panel;
    }

    private void addLegendItem(JPanel parent, Color color, String label) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        item.setOpaque(false);
        JLabel dot = new JLabel("   ");
        dot.setOpaque(true);
        dot.setBackground(color);
        dot.setPreferredSize(new Dimension(14, 14));
        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.FONT_REGULAR);
        lbl.setForeground(Theme.TEXT_MUTED);
        item.add(dot);
        item.add(lbl);
        parent.add(item);
    }

    // ==========================================
    // TAB 4: NHAT KY & XUAT FILE
    // ==========================================
    private JPanel createLogTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Thanh công cụ xuất file
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolBar.setBackground(Theme.BG_CARD);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        btnExportTxt.addActionListener(e -> exportToFile("txt"));
        btnExportCsv.addActionListener(e -> exportToFile("csv"));
        btnExportJson.addActionListener(e -> exportToFile("json"));
        btnClearLogs.addActionListener(e -> txtLogs.setText(""));

        toolBar.add(btnExportTxt);
        toolBar.add(btnExportCsv);
        toolBar.add(btnExportJson);
        toolBar.add(btnClearLogs);

        panel.add(toolBar, BorderLayout.NORTH);

        txtLogs.setFont(Theme.FONT_MONO);
        txtLogs.setBackground(Theme.BG_INPUT);
        txtLogs.setForeground(new Color(226, 232, 240));
        txtLogs.setEditable(false);
        txtLogs.setCaretColor(Color.WHITE);
        txtLogs.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane scroll = new JScrollPane(txtLogs);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ==========================================
    // BUSINESS LOGIC & EVENT HANDLERS
    // ==========================================

    private void refreshWifiInfo() {
        log("Đang kiểm tra kết nối card mạng Wi-Fi...");
        SwingUtilities.invokeLater(() -> {
            currentWifi = WifiDetector.detectActiveWifi();
            wifiBadge.updateWifi(currentWifi);

            if (currentWifi.isConnected()) {
                log(String.format("Đã kết nối Wi-Fi: %s (BSSID: %s, RSSI: %d dBm, Signal: %d%%)",
                        currentWifi.getSsid(), currentWifi.getBssid(), currentWifi.getRssiDbm(), currentWifi.getSignalPercent()));
                log(String.format("Dải mạng Wi-Fi: %s (Scanner IP: %s, Gateway: %s)",
                        currentWifi.getCidrNotation(), currentWifi.getLocalIp(), currentWifi.getGatewayIp()));

                if (txtTargetIp.getText().trim().isEmpty() || txtTargetIp.getText().equals("127.0.0.1")) {
                    txtTargetIp.setText(currentWifi.getGatewayIp());
                }
            } else {
                log("CẢNH BÁO: Chưa tìm thấy card Wi-Fi đang kết nối!");
            }
        });
    }

    private void startLanScan() {
        if (!currentWifi.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Máy tính chưa kết nối Wi-Fi! Vui lòng kết nối Wi-Fi trước khi quét.",
                    "Lỗi kết nối Wi-Fi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnScanLan.setEnabled(false);
        btnStopLan.setEnabled(true);
        deviceTableModel.setRowCount(0);
        discoveredDevices.clear();
        txtSearchDevice.setText("");
        filterDevices("");
        lblSearchResult.setText("");
        lanProgressBar.setValue(0);
        lblLanProgress.setText("Đang khởi động dò tìm thiết bị Wi-Fi (Tối ưu Wi-Fi công cộng)...");
        log(String.format("Bắt đầu dò tìm thiết bị trong mạng Wi-Fi: %s (Gateway: %s, Subnet Mask: %s)...",
                currentWifi.getCidrNotation(), currentWifi.getGatewayIp(), currentWifi.getSubnetMask()));
        if (currentWifi.getPrefixLength() < 24) {
            log(String.format("-> Phát hiện mạng Wi-Fi công cộng dải rộng (/%d): Tối ưu quét đa phân vùng (Gateway AP + Client Local + Multicast Wakeup + Đồng bộ ARP thời gian thực).",
                    currentWifi.getPrefixLength()));
        }

        lanScanner.scanWifiSubnet(currentWifi, new LanScanner.ScanProgressListener() {
            @Override
            public void onProgress(int current, int total, double percent, String statusMsg) {
                SwingUtilities.invokeLater(() -> {
                    lanProgressBar.setValue((int) percent);
                    lblLanProgress.setText(statusMsg);
                });
            }

            @Override
            public void onDeviceFound(NetworkDevice device) {
                SwingUtilities.invokeLater(() -> {
                    discoveredDevices.add(device);
                    DistanceEstimate est = device.getDistanceEstimate();
                    String distStr = est != null ? est.getFormattedDistance() : "--";

                    deviceTableModel.addRow(new Object[]{
                            device.getIpAddress(),
                            device.getHostName(),
                            device.getMacAddress(),
                            device.getDeviceType(),
                            String.format("%.2f ms", device.getRttMs()),
                            distStr
                    });

                    radarPanel.setDevices(discoveredDevices);
                    filterDevices(txtSearchDevice.getText());
                    log(String.format("Phát hiện thiết bị Wi-Fi: %s (%s) - %s | Khoảng cách: %s",
                            device.getIpAddress(), device.getHostName(), device.getDeviceType().getLabel(), distStr));
                });
            }

            @Override
            public void onDeviceUpdated(NetworkDevice device) {
                SwingUtilities.invokeLater(() -> {
                    DistanceEstimate est = device.getDistanceEstimate();
                    String distStr = est != null ? est.getFormattedDistance() : "--";

                    for (int i = 0; i < deviceTableModel.getRowCount(); i++) {
                        if (deviceTableModel.getValueAt(i, 0).equals(device.getIpAddress())) {
                            deviceTableModel.setValueAt(device.getHostName(), i, 1);
                            deviceTableModel.setValueAt(device.getMacAddress(), i, 2);
                            deviceTableModel.setValueAt(device.getDeviceType(), i, 3);
                            deviceTableModel.setValueAt(String.format("%.2f ms", device.getRttMs()), i, 4);
                            deviceTableModel.setValueAt(distStr, i, 5);
                            break;
                        }
                    }
                    filterDevices(txtSearchDevice.getText());
                    radarPanel.setDevices(discoveredDevices);
                });
            }

            @Override
            public void onCompleted(List<NetworkDevice> devices) {
                SwingUtilities.invokeLater(() -> {
                    btnScanLan.setEnabled(true);
                    btnStopLan.setEnabled(false);
                    lblLanProgress.setText(String.format("Hoàn tất! Tìm thấy %d thiết bị trên Wi-Fi.", devices.size()));
                    log("Quét mạng Wi-Fi hoàn tất! Tổng cộng: " + devices.size() + " thiết bị.");
                    filterDevices(txtSearchDevice.getText());
                    radarPanel.setDevices(devices);
                    if (!devices.isEmpty()) {
                        deviceTable.setRowSelectionInterval(0, 0);
                    }
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    btnScanLan.setEnabled(true);
                    btnStopLan.setEnabled(false);
                    lblLanProgress.setText("Lỗi: " + message);
                    log("LỖI QUÉT LAN: " + message);
                    JOptionPane.showMessageDialog(MainWindow.this, message, "Lỗi quét mạng", JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    private void stopLanScan() {
        lanScanner.stopScan();
        btnScanLan.setEnabled(true);
        btnStopLan.setEnabled(false);
        lblLanProgress.setText("Đã dừng quét thiết bị.");
        log("Người dùng đã dừng tiến trình dò thiết bị Wi-Fi.");
    }

    private void startPortScan() {
        String targetIp = txtTargetIp.getText().trim();

        // Kiểm tra hợp lệ Wi-Fi trước khi scan
        try {
            WifiDetector.validateWifiTarget(currentWifi, targetIp);
        } catch (IllegalArgumentException ex) {
            log("[TỪ CHỐI SCAN]: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Chặn quét ngoài Wi-Fi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<Integer> ports = parsePorts(txtPorts.getText().trim());
        if (ports.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Danh sách cổng không hợp lệ! Vui lòng nhập số cổng (VD: 80, 443 hoặc 1-100).",
                    "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int timeout = (int) spinTimeout.getValue();
        int threads = (int) spinThreads.getValue();
        boolean grabBanner = chkGrabBanner.isSelected();
        boolean onlyOpen = chkOnlyOpen.isSelected();

        btnStartPortScan.setEnabled(false);
        btnStopPortScan.setEnabled(true);
        portTableModel.setRowCount(0);
        currentOpenPorts.clear();
        portProgressBar.setValue(0);
        lblPortStats.setText("Đang chuẩn bị quét...");

        log(String.format("Bắt đầu quét %d cổng trên IP Wi-Fi: %s (Timeout: %dms, Luồng: %d, Chỉ hiện cổng mở: %s)...",
                ports.size(), targetIp, timeout, threads, onlyOpen ? "Bật" : "Tắt"));

        portScanner.scanPorts(targetIp, ports, timeout, threads, grabBanner, onlyOpen, currentWifi, new PortScanner.PortScanListener() {
            @Override
            public void onPortFound(PortResult result) {
                SwingUtilities.invokeLater(() -> {
                    if (result.getState() == PortState.OPEN) {
                        currentOpenPorts.add(result);
                    }
                    portTableModel.addRow(new Object[]{
                            result.getPort(),
                            result.getProtocol(),
                            result.getState(),
                            result.getServiceName(),
                            String.format("%.2f ms", result.getLatencyMs()),
                            result.getBanner()
                    });
                    if (result.getState() == PortState.OPEN) {
                        log(String.format(">>> CỔNG MỞ: %d/%s - %s (%.2f ms) | %s",
                                result.getPort(), result.getProtocol(), result.getServiceName(), result.getLatencyMs(), result.getBanner()));
                    }
                });
            }

            @Override
            public void onProgress(int scanned, int total, double percent, double portsPerSec) {
                SwingUtilities.invokeLater(() -> {
                    portProgressBar.setValue((int) percent);
                    lblPortStats.setText(String.format("Cổng mở: %d | Đã quét: %d/%d (%.1f%%) | Tốc độ: %.1f ports/s",
                            currentOpenPorts.size(), scanned, total, percent, portsPerSec));
                });
            }

            @Override
            public void onCompleted(List<PortResult> openPorts, int totalScanned, long durationMs) {
                SwingUtilities.invokeLater(() -> {
                    btnStartPortScan.setEnabled(true);
                    btnStopPortScan.setEnabled(false);
                    if (!openPorts.isEmpty()) {
                        lblPortStats.setText(String.format("Hoàn tất! Tìm thấy %d cổng MỞ trên %d cổng trong %.2f giây.",
                                openPorts.size(), totalScanned, durationMs / 1000.0));
                        log(String.format("Quét cổng hoàn tất! Cổng mở: %d / %d trong %.2f giây.",
                                openPorts.size(), totalScanned, durationMs / 1000.0));

                        int fastestPort = openPorts.get(0).getPort();
                        double[] probe = DistanceCalculator.measureRttAndTtl(targetIp, fastestPort, 3);
                        log(String.format("Hiệu chỉnh độ trễ handshake qua cổng mở %d: RTT = %.2f ms, TTL = %d",
                                fastestPort, probe[0], (int) probe[1]));
                    } else {
                        lblPortStats.setText(String.format("Quét xong %d cổng: Không có cổng MỞ (Các cổng đều ĐÓNG hoặc LỌC trên %s).",
                                totalScanned, targetIp));
                        log("Quét cổng hoàn tất! Thiết bị " + targetIp + " đóng/chặn tất cả các cổng trong danh sách.");
                        log("GỢI Ý: Chọn preset 'Máy tính này' hoặc 'Router Wi-Fi' để thấy các cổng mở thật như 80, 135, 445, 1433, 3306!");
                    }
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    btnStartPortScan.setEnabled(true);
                    btnStopPortScan.setEnabled(false);
                    lblPortStats.setText("Lỗi: " + message);
                    log("LỖI QUÉT CỔNG: " + message);
                    JOptionPane.showMessageDialog(MainWindow.this, message, "Lỗi quét cổng", JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    private void stopPortScan() {
        portScanner.stopScan();
        btnStartPortScan.setEnabled(true);
        btnStopPortScan.setEnabled(false);
        lblPortStats.setText("Da dung quet cong.");
        log("Nguoi dung da dung tien trinh quet cong.");
    }

    // ==========================================
    // TAB 5 LOGIC: QUET WI-FI CONG CONG
    // ==========================================

    private void startPublicWifiScan() {
        btnScanPublic.setEnabled(false);
        btnStopPublic.setEnabled(true);
        publicTableModel.setRowCount(0);
        scannedNetworks.clear();
        publicScanProgress.setValue(0);
        lblPublicStatus.setText("Dang khoi dong quet Wi-Fi xung quanh...");
        lblPublicSummary.setText("Dang quet...");
        log("[TAB 5] Bat dau quet Wi-Fi cong cong bang netsh wlan...");

        publicWifiScanner.scan(currentWifi, new PublicWifiScanner.ScanListener() {
            @Override
            public void onNetworkFound(PublicWifiNetwork net) {
                SwingUtilities.invokeLater(() -> {
                    scannedNetworks.add(net);
                    String connectedMark = net.isCurrentNetwork() ? "[DANG KET NOI]" : "";
                    String stationInfo = net.getConnectedStations() >= 0
                            ? net.getConnectedStations() + " clients"
                            : "N/A";
                    String utilInfo = net.getChannelUtilization() >= 0
                            ? net.getChannelUtilization() + "%"
                            : "N/A";
                    String bandCh = (net.getBand() != null ? net.getBand() : "?") +
                            " / Ch." + (net.getChannel() > 0 ? net.getChannel() : "?");

                    publicTableModel.addRow(new Object[]{
                            net.getSsid(),
                            net.getBssid() != null ? net.getBssid() : "N/A",
                            net.getAuthentication() != null ? net.getAuthentication() : "N/A",
                            net.getEncryption() != null ? net.getEncryption() : "N/A",
                            net.getSignalBars(),
                            net.getRssiDbm() + " dBm",
                            bandCh,
                            stationInfo + " / " + utilInfo,
                            net.getRadioType() != null ? net.getRadioType() : "N/A",
                            connectedMark + (net.isOpen() ? "MO" : net.getSecurityRating())
                    });

                    String secureLabel = net.isOpen() ? "[CANH BAO: MANG MO]" : "[Bao mat: " + net.getAuthentication() + "]";
                    log(String.format("[TAB 5] Tim thay: %-32s | BSSID: %s | %s | Signal: %d%% (%d dBm) | %s",
                            net.getSsid(), net.getBssid(), net.getBand(), net.getSignalPercent(), net.getRssiDbm(), secureLabel));
                });
            }

            @Override
            public void onProgress(int percent, String message) {
                SwingUtilities.invokeLater(() -> {
                    publicScanProgress.setValue(percent);
                    lblPublicStatus.setText(message);
                });
            }

            @Override
            public void onCaptivePortalChecked(PublicWifiNetwork net) {
                SwingUtilities.invokeLater(() -> {
                    if (net.hasCaptivePortal()) {
                        log("[TAB 5] Captive Portal phat hien! URL chuyen huong: " + net.getCaptivePortalUrl());
                        lblPublicStatus.setText("Captive Portal phat hien: " + net.getCaptivePortalUrl());
                        // Cap nhat dong hien tai trong bang
                        updatePublicNetworkRow(net);
                    } else {
                        log("[TAB 5] Mang hien tai KHONG co Captive Portal - Ket noi Internet thong.");
                    }
                    if (net.getCaptivePortalUrl() != null && !net.getCaptivePortalUrl().isEmpty()) {
                        updatePublicNetworkRow(net);
                    }
                });
            }

            @Override
            public void onComplete(List<PublicWifiNetwork> all, String summary) {
                SwingUtilities.invokeLater(() -> {
                    btnScanPublic.setEnabled(true);
                    btnStopPublic.setEnabled(false);
                    publicScanProgress.setValue(100);
                    lblPublicStatus.setText("Hoan tat! Tim thay " + all.size() + " diem truy cap.");
                    lblPublicSummary.setText(summary);
                    log("[TAB 5] HOAN TAT QUET WI-FI CONG CONG. " + summary);
                });
            }

            @Override
            public void onError(String error) {
                SwingUtilities.invokeLater(() -> {
                    btnScanPublic.setEnabled(true);
                    btnStopPublic.setEnabled(false);
                    lblPublicStatus.setText("Loi: " + error);
                    log("[TAB 5] LOI: " + error);
                    JOptionPane.showMessageDialog(MainWindow.this, error, "Loi quet Wi-Fi", JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    private void stopPublicWifiScan() {
        publicWifiScanner.stop();
        btnScanPublic.setEnabled(true);
        btnStopPublic.setEnabled(false);
        lblPublicStatus.setText("Da dung quet.");
        log("[TAB 5] Nguoi dung da dung quet Wi-Fi cong cong.");
    }

    private void checkPortalForSelected() {
        int row = publicTable.getSelectedRow();
        if (row < 0 || row >= scannedNetworks.size()) {
            JOptionPane.showMessageDialog(this,
                "Vui long chon mot mang Wi-Fi trong bang truoc!\n" +
                "(Luu y: Chi kiem tra duoc Captive Portal cua mang DANG KET NOI)",
                "Thong bao", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        PublicWifiNetwork net = scannedNetworks.get(row);
        if (!net.isCurrentNetwork()) {
            JOptionPane.showMessageDialog(this,
                "Chi co the kiem tra Captive Portal cua mang DANG KET NOI!\n" +
                "Mang duoc chon: '" + net.getSsid() + "' khong phai mang hien tai.",
                "Gioi han", JOptionPane.WARNING_MESSAGE);
            return;
        }
        lblPublicStatus.setText("Dang kiem tra Captive Portal cho: " + net.getSsid() + "...");
        log("[TAB 5] Dang kiem tra Captive Portal: " + net.getSsid());
        Thread t = new Thread(() -> {
            publicWifiScanner.checkCaptivePortal(net);
            SwingUtilities.invokeLater(() -> {
                updatePublicNetworkRow(net);
                if (net.hasCaptivePortal()) {
                    lblPublicStatus.setText("Captive Portal phat hien: " + net.getCaptivePortalUrl());
                    log("[TAB 5] Captive Portal: " + net.getCaptivePortalUrl());
                    JOptionPane.showMessageDialog(MainWindow.this,
                        "Captive Portal phat hien!\n" +
                        "URL: " + net.getCaptivePortalUrl() + "\n\n" +
                        "Mang Wi-Fi nay yeu cau dang nhap truoc khi truy cap Internet.",
                        "Captive Portal", JOptionPane.WARNING_MESSAGE);
                } else {
                    lblPublicStatus.setText("Ket noi Internet thong - Khong co Captive Portal.");
                    log("[TAB 5] Ket noi thong. Khong phat hien Captive Portal.");
                    JOptionPane.showMessageDialog(MainWindow.this,
                        "Khong co Captive Portal!\nKet noi Internet hoan toan thong.",
                        "Ket qua kiem tra", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        }, "PortalCheck");
        t.setDaemon(true);
        t.start();
    }

    private void updatePublicNetworkRow(PublicWifiNetwork net) {
        for (int i = 0; i < scannedNetworks.size(); i++) {
            if (scannedNetworks.get(i) == net) {
                String statusStr;
                if (net.hasCaptivePortal()) {
                    statusStr = "[CAPTIVE PORTAL] " + net.getCaptivePortalUrl();
                } else if (net.getCaptivePortalUrl() != null && !net.getCaptivePortalUrl().isEmpty()) {
                    statusStr = net.getCaptivePortalUrl();
                } else {
                    statusStr = net.isCurrentNetwork() ? "[DANG KET NOI]" : net.getSecurityRating();
                }
                publicTableModel.setValueAt(statusStr, i, 9);
                break;
            }
        }
    }

    private void scanDevicesFromPublicTab() {
        int row = publicTable.getSelectedRow();
        if (row >= 0 && row < scannedNetworks.size()) {
            PublicWifiNetwork net = scannedNetworks.get(row);
            if (!net.isCurrentNetwork()) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Mạng '" + net.getSsid() + "' không phải mạng Wi-Fi bạn đang kết nối!\n" +
                        "Ứng dụng chỉ có thể dò quét thiết bị bên trong mạng Wi-Fi mà máy đang kết nối (" +
                        (currentWifi.isConnected() ? currentWifi.getSsid() : "Chưa kết nối") + ").\n\n" +
                        "Bạn có muốn chuyển sang Tab 1 để quét mạng đang kết nối hiện tại không?",
                        "Thông báo mạng Wi-Fi", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }
        }
        tabbedPane.setSelectedIndex(0); // Chuyển sang Tab 1: Dò thiết bị Wi-Fi
        startLanScan();
    }

    private NetworkDevice getSelectedDevice() {
        int viewRow = deviceTable.getSelectedRow();
        if (viewRow < 0) return null;
        try {
            int modelRow = deviceTable.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < deviceTableModel.getRowCount()) {
                String ip = (String) deviceTableModel.getValueAt(modelRow, 0);
                for (NetworkDevice d : discoveredDevices) {
                    if (d.getIpAddress().equals(ip)) return d;
                }
                if (modelRow < discoveredDevices.size()) return discoveredDevices.get(modelRow);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void filterDevices(String text) {
        if (deviceSorter == null) return;
        if (text == null || text.trim().isEmpty()) {
            deviceSorter.setRowFilter(null);
            lblSearchResult.setText(discoveredDevices.isEmpty()
                    ? ""
                    : String.format("Tổng cộng: %d thiết bị", discoveredDevices.size()));
        } else {
            String q = text.trim();
            try {
                deviceSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(q)));
                int count = deviceTable.getRowCount();
                lblSearchResult.setText(String.format("Khớp: %d / %d thiết bị", count, discoveredDevices.size()));
            } catch (Exception ignored) {
                deviceSorter.setRowFilter(null);
            }
        }
    }

    private void selectCurrentDeviceForPortScan() {
        NetworkDevice dev = getSelectedDevice();
        if (dev != null) {
            txtTargetIp.setText(dev.getIpAddress());
            tabbedPane.setSelectedIndex(1); // Chuyển sang Tab 2
            log("Đã chọn thiết bị: " + dev.getIpAddress() + " (" + dev.getHostName() + ") để quét cổng.");
        } else {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thiết bị trong bảng trước!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void selectCurrentDeviceForDistance() {
        NetworkDevice dev = getSelectedDevice();
        if (dev != null) {
            distanceCard.displayDeviceDistance(dev, currentWifi);
            radarPanel.setSelectedDevice(dev);
            tabbedPane.setSelectedIndex(2); // Chuyển sang Tab 3
        } else {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thiết bị trong bảng trước!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void updateDeviceDistanceInTable(DistanceEstimate est) {
        int viewRow = deviceTable.getSelectedRow();
        if (viewRow >= 0) {
            try {
                int modelRow = deviceTable.convertRowIndexToModel(viewRow);
                if (modelRow >= 0 && modelRow < deviceTableModel.getRowCount()) {
                    deviceTableModel.setValueAt(est.getFormattedDistance(), modelRow, 5);
                }
            } catch (Exception ignored) {}
        }
    }

    private List<Integer> parsePorts(String input) {
        List<Integer> list = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) return list;

        String[] parts = input.split("[,;\\s]+");
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;

            if (p.contains("-")) {
                String[] range = p.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        if (start > end) { int tmp = start; start = end; end = tmp; }
                        start = Math.max(1, start);
                        end = Math.min(65535, end);
                        for (int i = start; i <= end; i++) {
                            list.add(i);
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                try {
                    int val = Integer.parseInt(p);
                    if (val >= 1 && val <= 65535) {
                        list.add(val);
                    }
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private String formatPortList(int[] ports) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ports.length; i++) {
            sb.append(ports[i]);
            if (i < ports.length - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String line = "[" + timestamp + "] " + message + "\n";
        SwingUtilities.invokeLater(() -> {
            txtLogs.append(line);
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    private void exportToFile(String format) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("wifi_scan_report_" + System.currentTimeMillis() + "." + format));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(file)) {
                if ("csv".equalsIgnoreCase(format)) {
                    writer.println("IP,Hostname,MAC,Type,RTT_ms,Distance");
                    for (NetworkDevice d : discoveredDevices) {
                        writer.printf("%s,%s,%s,%s,%.2f,%s\n",
                                d.getIpAddress(), d.getHostName(), d.getMacAddress(),
                                d.getDeviceType().name(), d.getRttMs(),
                                d.getDistanceEstimate() != null ? d.getDistanceEstimate().getDistanceMeters() : 0);
                    }
                } else if ("json".equalsIgnoreCase(format)) {
                    writer.println("{\n  \"wifi\": {");
                    writer.printf("    \"ssid\": \"%s\",\n", currentWifi.getSsid());
                    writer.printf("    \"rssi\": %d,\n", currentWifi.getRssiDbm());
                    writer.printf("    \"localIp\": \"%s\"\n", currentWifi.getLocalIp());
                    writer.println("  },\n  \"devices\": [");
                    for (int i = 0; i < discoveredDevices.size(); i++) {
                        NetworkDevice d = discoveredDevices.get(i);
                        writer.printf("    {\"ip\": \"%s\", \"host\": \"%s\", \"distance\": %.2f}%s\n",
                                d.getIpAddress(), d.getHostName(),
                                d.getDistanceEstimate() != null ? d.getDistanceEstimate().getDistanceMeters() : 0,
                                (i < discoveredDevices.size() - 1 ? "," : ""));
                    }
                    writer.println("  ]\n}");
                } else {
                    writer.println("=== BÁO CÁO QUÉT MẠNG WI-FI & ĐO KHOẢNG CÁCH ===");
                    writer.println("Thời gian: " + new Date());
                    writer.println("SSID: " + currentWifi.getSsid());
                    writer.println("RSSI: " + currentWifi.getRssiDbm() + " dBm");
                    writer.println("Scanner IP: " + currentWifi.getLocalIp());
                    writer.println("Gateway: " + currentWifi.getGatewayIp());
                    writer.println("\nDANH SÁCH THIẾT BỊ WI-FI:");
                    for (NetworkDevice d : discoveredDevices) {
                        writer.printf("- %s (%s) | %s | RTT: %.2f ms | KC: %s\n",
                                d.getIpAddress(), d.getHostName(), d.getDeviceType().getLabel(),
                                d.getRttMs(), d.getDistanceEstimate() != null ? d.getDistanceEstimate().getFormattedDistance() : "--");
                    }
                    writer.println("\nDANH SÁCH CỔNG MỞ TRÊN MỤC TIÊU (" + txtTargetIp.getText() + "):");
                    for (PortResult p : currentOpenPorts) {
                        writer.printf("- Cổng %d/%s (%s): %.2f ms | %s\n",
                                p.getPort(), p.getProtocol(), p.getServiceName(), p.getLatencyMs(), p.getBanner());
                    }
                }
                log("Đã xuất báo cáo thành công tới: " + file.getAbsolutePath());
                JOptionPane.showMessageDialog(this, "Đã lưu báo cáo thành công!", "Xuất file", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log("Lỗi xuất file: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu file: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
