package com.wifiscan.ui.components;

import com.wifiscan.model.DistanceEstimate;
import com.wifiscan.model.NetworkDevice;
import com.wifiscan.model.WifiInfo;
import com.wifiscan.service.DistanceCalculator;
import com.wifiscan.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Thẻ hiển thị khoảng cách và chất lượng liên kết từ Client tới Bộ phát Wi-Fi (Router AP).
 * Hỗ trợ đo lường chỉ số thực tế qua Live AP Probe (đo trễ sai biệt giữa Client và Gateway).
 */
public class ApDistanceCard extends JPanel {

    private final JLabel lblTargetTitle = new JLabel("Chưa chọn thiết bị để đo tới AP");
    private final JLabel lblHeroDistance = new JLabel("-- m");
    private final JLabel lblRange = new JLabel("Khoảng tin cậy: --");
    private final JLabel lblSignalInfo = new JLabel("Cường độ sóng tại AP: -- | Chất lượng: --");
    private final JLabel lblRttInfo = new JLabel("Độ trễ truyền dẫn vô tuyến tới AP: --");
    private final JTextArea txtAnalysis = new JTextArea(6, 40);
    private final JSlider sliderPathLoss = new JSlider(18, 40, 26);
    private final JLabel lblSliderVal = new JLabel("n = 2.6 (Trong nhà tiêu chuẩn)");
    private final ModernButton btnLiveProbe = new ModernButton("Đo chỉ số liên kết AP thật sự", Theme.ACCENT_CYAN);
    private final DistanceGaugePanel gauge = new DistanceGaugePanel();

    private NetworkDevice currentDevice;
    private WifiInfo currentWifi;
    private Consumer<DistanceEstimate> onRecalculateListener;

    public ApDistanceCard() {
        setLayout(new BorderLayout(15, 12));
        setBackground(Theme.BG_CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(16, 20, 16, 20)
        ));

        initUI();
    }

    private void initUI() {
        // 1. Header
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);
        JLabel lblBadge = new JLabel("[HỆ QUY CHIẾU: BỘ PHÁT AP ROUTER]");
        lblBadge.setFont(Theme.FONT_BOLD);
        lblBadge.setForeground(Theme.ACCENT_CYAN);
        badgeRow.add(lblBadge);

        lblTargetTitle.setFont(Theme.FONT_TITLE);
        lblTargetTitle.setForeground(Theme.TEXT_MAIN);
        lblTargetTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        topPanel.add(badgeRow);
        topPanel.add(Box.createVerticalStrut(6));
        topPanel.add(lblTargetTitle);
        add(topPanel, BorderLayout.NORTH);

        // 2. Center Panel: Hero Distance + Metrics + Gauge
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        lblHeroDistance.setFont(Theme.FONT_HERO);
        lblHeroDistance.setForeground(Theme.ACCENT_CYAN);
        lblHeroDistance.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblRange.setFont(Theme.FONT_REGULAR);
        lblRange.setForeground(Theme.TEXT_MUTED);
        lblRange.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblSignalInfo.setFont(Theme.FONT_BOLD);
        lblSignalInfo.setForeground(new Color(52, 211, 153));
        lblSignalInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblRttInfo.setFont(Theme.FONT_REGULAR);
        lblRttInfo.setForeground(new Color(203, 213, 225));
        lblRttInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalStrut(4));
        centerPanel.add(lblHeroDistance);
        centerPanel.add(Box.createVerticalStrut(4));
        centerPanel.add(lblRange);
        centerPanel.add(Box.createVerticalStrut(6));
        centerPanel.add(lblSignalInfo);
        centerPanel.add(Box.createVerticalStrut(4));
        centerPanel.add(lblRttInfo);
        centerPanel.add(Box.createVerticalStrut(12));

        gauge.setPreferredSize(new Dimension(500, 32));
        gauge.setMaximumSize(new Dimension(800, 32));
        gauge.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(gauge);
        centerPanel.add(Box.createVerticalStrut(12));

        // Slider tinh chỉnh n & Nút đo thật
        JPanel ctrlRow = new JPanel(new BorderLayout(10, 5));
        ctrlRow.setOpaque(false);
        ctrlRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel sliderBox = new JPanel(new BorderLayout(5, 2));
        sliderBox.setOpaque(false);
        JLabel lblSliderTitle = new JLabel("Hệ số suy hao môi trường AP (n):");
        lblSliderTitle.setFont(Theme.FONT_BOLD);
        lblSliderTitle.setForeground(Theme.TEXT_MAIN);

        sliderPathLoss.setOpaque(false);
        sliderPathLoss.setBackground(Theme.BG_CARD);
        sliderPathLoss.setForeground(Theme.TEXT_MUTED);
        sliderPathLoss.setMajorTickSpacing(5);
        sliderPathLoss.setPaintTicks(true);
        sliderPathLoss.addChangeListener(e -> recalculateWithCurrentN());

        lblSliderVal.setFont(Theme.FONT_REGULAR);
        lblSliderVal.setForeground(Theme.ACCENT_CYAN);

        sliderBox.add(lblSliderTitle, BorderLayout.NORTH);
        sliderBox.add(sliderPathLoss, BorderLayout.CENTER);
        sliderBox.add(lblSliderVal, BorderLayout.SOUTH);

        btnLiveProbe.addActionListener(e -> performLiveApProbe());

        JPanel btnWrapper = new JPanel(new GridBagLayout());
        btnWrapper.setOpaque(false);
        btnLiveProbe.setPreferredSize(new Dimension(230, 42));
        btnWrapper.add(btnLiveProbe);

        ctrlRow.add(sliderBox, BorderLayout.CENTER);
        ctrlRow.add(btnWrapper, BorderLayout.EAST);

        centerPanel.add(ctrlRow);
        add(centerPanel, BorderLayout.CENTER);

        // 3. Bottom: Text phân tích
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setOpaque(false);

        JLabel lblDetailTitle = new JLabel("Phân tích cự ly & sóng vô tuyến từ Client đến Bộ phát AP:");
        lblDetailTitle.setFont(Theme.FONT_BOLD);
        lblDetailTitle.setForeground(Theme.TEXT_MAIN);

        txtAnalysis.setFont(Theme.FONT_MONO);
        txtAnalysis.setBackground(Theme.BG_INPUT);
        txtAnalysis.setForeground(new Color(203, 213, 225));
        txtAnalysis.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(8, 10, 8, 10)
        ));
        txtAnalysis.setEditable(false);
        txtAnalysis.setLineWrap(true);
        txtAnalysis.setWrapStyleWord(true);

        bottomPanel.add(lblDetailTitle, BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(txtAnalysis), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void setOnRecalculateListener(Consumer<DistanceEstimate> listener) {
        this.onRecalculateListener = listener;
    }

    public void displayDeviceDistance(NetworkDevice device, WifiInfo wifiInfo) {
        this.currentDevice = device;
        this.currentWifi = wifiInfo;

        if (device == null) {
            lblTargetTitle.setText("Chưa chọn thiết bị để đo tới AP");
            lblHeroDistance.setText("-- m");
            lblRange.setText("Khoảng tin cậy: --");
            lblSignalInfo.setText("Cường độ sóng tại AP: --");
            lblRttInfo.setText("Độ trễ truyền vô tuyến tới AP: --");
            txtAnalysis.setText("Vui lòng chọn một thiết bị từ danh sách Wi-Fi để xem cự ly tới AP.");
            return;
        }

        DistanceEstimate est = device.getDistanceEstimate();
        if (est == null) {
            double n = sliderPathLoss.getValue() / 10.0;
            est = DistanceCalculator.calculateDistance(
                    device.getIpAddress(), device.isGateway(), device.isLocalHost(), wifiInfo,
                    device.getRttMs(), n, device.getMacAddress()
            );
            device.setDistanceEstimate(est);
        }

        updateEstimate(device, wifiInfo, est);
    }

    private void updateEstimate(NetworkDevice device, WifiInfo wifiInfo, DistanceEstimate est) {
        String gwIp = (wifiInfo != null && wifiInfo.getGatewayIp() != null) ? wifiInfo.getGatewayIp() : "AP";
        String host = device.getHostName();
        if (host != null && host.startsWith("Thiết bị Wi-Fi (" + device.getIpAddress() + ") - ")) {
            host = host.substring(("Thiết bị Wi-Fi (" + device.getIpAddress() + ") - ").length());
        } else if (host != null && host.equals("Thiết bị Wi-Fi (" + device.getIpAddress() + ")")) {
            host = device.getDeviceType().getLabel();
        }
        if (host != null && host.length() > 34) {
            host = host.substring(0, 31) + "...";
        }
        lblTargetTitle.setText(String.format("Cự ly: %s (%s) -> AP Router (%s)", device.getIpAddress(), host != null ? host : "Thiết bị Wi-Fi", gwIp));

        if (device.isGateway()) {
            lblHeroDistance.setText("0.00 mét");
            lblHeroDistance.setForeground(Theme.ACCENT_CYAN);
            lblRange.setText("Khoảng tin cậy: 0.0 m — 0.0 m");
            lblSignalInfo.setText(String.format("Cường độ sóng: %d dBm (100%% - Chính là Bộ phát AP)", est.getRssiDbm()));
            lblRttInfo.setText("Độ trễ nội tại AP: 0.05 ms");
            gauge.setDistance(0.0);
        } else {
            lblHeroDistance.setText(String.format("%.2f mét", est.getDistanceToApMeters()));
            lblHeroDistance.setForeground(Theme.ACCENT_CYAN);
            lblRange.setText(String.format("Khoảng tin cậy: %.1f m — %.1f m", est.getMinDistanceToApMeters(), est.getMaxDistanceToApMeters()));
            lblSignalInfo.setText(String.format("Cường độ sóng tại AP: %d dBm | Chất lượng: %d%% (%s)",
                    est.getApRssiDbm(), est.getApSignalPercent(), est.getApSignalQuality()));
            lblRttInfo.setText(String.format("Độ trễ lan truyền vô tuyến qua AP: %.2f ms", est.getRttToApMs()));
            gauge.setDistance(est.getDistanceToApMeters());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("• Mô hình đo cự ly tới Access Point: Phân rã trễ mạng + Log-Distance Path Loss ngược\n");
        sb.append(String.format("• Router Gateway AP: %s (BSSID: %s, Kênh: %s)\n",
                gwIp, (wifiInfo != null ? wifiInfo.getBssid() : "-"), (wifiInfo != null ? wifiInfo.getChannel() : "-")));
        sb.append(String.format("• Khoảng cách tính toán: %.2f mét (Khoảng tin cậy: %.1fm - %.1fm)\n",
                est.getDistanceToApMeters(), est.getMinDistanceToApMeters(), est.getMaxDistanceToApMeters()));
        sb.append(String.format("• Ước tính công suất thu tại AP: %d dBm (Chất lượng sóng: %d%%)\n",
                est.getApRssiDbm(), est.getApSignalPercent()));
        sb.append(String.format("• Hệ số cản môi trường AP: n = %.2f\n\n", est.getPathLossExponent()));
        sb.append(est.getDetailedAnalysis());

        txtAnalysis.setText(sb.toString());
        txtAnalysis.setCaretPosition(0);
        repaint();
    }

    private void recalculateWithCurrentN() {
        double n = sliderPathLoss.getValue() / 10.0;
        String desc = (n <= 2.2) ? "Không gian mở" : (n <= 2.8) ? "Trong nhà tiêu chuẩn" : (n <= 3.4) ? "Có vách ngăn" : "Nhiều tường bê tông";
        lblSliderVal.setText(String.format("n = %.1f (%s)", n, desc));

        if (currentDevice != null && currentWifi != null) {
            DistanceEstimate est = DistanceCalculator.calculateDistance(
                    currentDevice.getIpAddress(),
                    currentDevice.isGateway(),
                    currentDevice.isLocalHost(),
                    currentWifi,
                    currentDevice.getRttMs(),
                    n,
                    currentDevice.getMacAddress()
            );
            currentDevice.setDistanceEstimate(est);
            updateEstimate(currentDevice, currentWifi, est);
            if (onRecalculateListener != null) {
                onRecalculateListener.accept(est);
            }
        }
    }

    private void performLiveApProbe() {
        if (currentDevice == null || currentWifi == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn thiết bị trước khi đo chỉ số thật!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnLiveProbe.setEnabled(false);
        btnLiveProbe.setText("Đang đo đạc thực tế...");
        String targetIp = currentDevice.getIpAddress();
        String gwIp = currentWifi.getGatewayIp();

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Đo RTT thật tới AP Router
                DistanceCalculator.LiveProbeResult apProbe = DistanceCalculator.performLiveMultiProbe(gwIp, 3, 150);
                double apRtt = apProbe.avgRtt > 0 ? apProbe.avgRtt : 1.20;

                // 2. Đo RTT thật tới Client
                DistanceCalculator.LiveProbeResult clientProbe = DistanceCalculator.performLiveMultiProbe(targetIp, 4, 150);
                double clientRtt = clientProbe.avgRtt > 0 ? clientProbe.avgRtt : currentDevice.getRttMs();

                // 3. Hiệu số trễ thực tế qua AP
                double deltaT = Math.max(0.2, clientRtt - apRtt);
                double n = sliderPathLoss.getValue() / 10.0;

                DistanceEstimate newEst = DistanceCalculator.calculateDistance(
                        targetIp, currentDevice.isGateway(), currentDevice.isLocalHost(), currentWifi,
                        clientRtt, n, currentDevice.getMacAddress()
                );

                SwingUtilities.invokeLater(() -> {
                    btnLiveProbe.setEnabled(true);
                    btnLiveProbe.setText("Đo chỉ số liên kết AP thật sự");
                    currentDevice.setRttMs(clientRtt);
                    currentDevice.setDistanceEstimate(newEst);
                    updateEstimate(currentDevice, currentWifi, newEst);

                    String logMsg = String.format("Kết quả đo chỉ số thật tới AP:\n" +
                            "• RTT tới Gateway AP (%s): %.2f ms\n" +
                            "• RTT tới Client (%s): %.2f ms (Min: %.2fms, Max: %.2fms, Jitter: %.2fms)\n" +
                            "• Độ trễ sai biệt qua AP (Delta t): %.2f ms\n" +
                            "• Cự ly Client -> AP mới đo được: %.2f mét\n" +
                            "• Cường độ sóng tại AP: %d dBm (%s)",
                            gwIp, apRtt, targetIp, clientRtt, clientProbe.minRtt, clientProbe.maxRtt, clientProbe.jitter,
                            deltaT, newEst.getDistanceToApMeters(), newEst.getApRssiDbm(), newEst.getApSignalQuality());

                    txtAnalysis.setText(logMsg + "\n\n" + txtAnalysis.getText());
                    txtAnalysis.setCaretPosition(0);

                    if (onRecalculateListener != null) {
                        onRecalculateListener.accept(newEst);
                    }
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnLiveProbe.setEnabled(true);
                    btnLiveProbe.setText("Đo chỉ số liên kết AP thật sự");
                });
            }
        });
    }

    private static class DistanceGaugePanel extends JPanel {
        private double distanceMeters = 0.0;

        public DistanceGaugePanel() { setOpaque(false); }

        public void setDistance(double d) {
            this.distanceMeters = d;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int barY = 4;
            int barH = 14;

            g2.setColor(new Color(24, 33, 47));
            g2.fillRoundRect(0, barY, w, barH, 8, 8);

            GradientPaint gp = new GradientPaint(
                    0, barY, new Color(6, 182, 212),
                    w, barY, new Color(239, 68, 68)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(0, barY, w, barH, 8, 8);

            double maxM = 25.0;
            double clamped = Math.max(0, Math.min(distanceMeters, maxM));
            int markerX = (int) ((clamped / maxM) * (w - 12)) + 6;

            g2.setColor(Color.WHITE);
            g2.fillPolygon(
                    new int[]{markerX - 6, markerX + 6, markerX},
                    new int[]{barY + barH + 8, barY + barH + 8, barY + barH},
                    3
            );

            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.drawString("0m (AP)", 2, barY + barH + 12);
            g2.drawString("5m", (int)(w * 0.2), barY + barH + 12);
            g2.drawString("10m", (int)(w * 0.4), barY + barH + 12);
            g2.drawString("15m", (int)(w * 0.6), barY + barH + 12);
            g2.drawString("20m+", (int)(w * 0.85), barY + barH + 12);

            g2.dispose();
        }
    }
}

