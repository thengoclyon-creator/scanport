package com.wifiscan.ui.components;

import com.wifiscan.model.DistanceEstimate;
import com.wifiscan.model.NetworkDevice;
import com.wifiscan.model.WifiInfo;
import com.wifiscan.service.DistanceCalculator;
import com.wifiscan.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Thẻ hiển thị khoảng cách từ Client đến Scanner với biểu đồ vạch và tinh chỉnh hệ số môi trường.
 */
public class DistanceCard extends JPanel {

    private final JLabel lblTargetTitle = new JLabel("Chưa chọn thiết bị");
    private final JLabel lblHeroDistance = new JLabel("-- m");
    private final JLabel lblRange = new JLabel("Khoảng tin cậy: --");
    private final JLabel lblZone = new JLabel("Khu vực: --");
    private final JLabel lblConfidence = new JLabel("Độ tin cậy: --");
    private final JTextArea txtAnalysis = new JTextArea(6, 40);
    private final JSlider sliderPathLoss = new JSlider(18, 40, 26);
    private final JLabel lblSliderVal = new JLabel("n = 2.6 (Trong nhà tiêu chuẩn)");

    private NetworkDevice currentDevice;
    private WifiInfo currentWifi;
    private Consumer<DistanceEstimate> onRecalculateListener;

    public DistanceCard() {
        setLayout(new BorderLayout(15, 15));
        setBackground(Theme.BG_CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(16, 20, 16, 20)
        ));

        initUI();
    }

    private void initUI() {
        // 1. Header
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        lblTargetTitle.setFont(Theme.FONT_TITLE);
        lblTargetTitle.setForeground(Theme.TEXT_MAIN);

        lblConfidence.setFont(Theme.FONT_BOLD);
        lblConfidence.setForeground(Theme.ACCENT_CYAN);
        lblConfidence.setHorizontalAlignment(SwingConstants.RIGHT);

        topPanel.add(lblTargetTitle, BorderLayout.WEST);
        topPanel.add(lblConfidence, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // 2. Center: Big distance display + Gauge bar
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        lblHeroDistance.setFont(Theme.FONT_HERO);
        lblHeroDistance.setForeground(Theme.SUCCESS_GREEN);
        lblHeroDistance.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblRange.setFont(Theme.FONT_REGULAR);
        lblRange.setForeground(Theme.TEXT_MUTED);
        lblRange.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblZone.setFont(Theme.FONT_BOLD);
        lblZone.setForeground(new Color(226, 232, 240));
        lblZone.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalStrut(5));
        centerPanel.add(lblHeroDistance);
        centerPanel.add(Box.createVerticalStrut(5));
        centerPanel.add(lblRange);
        centerPanel.add(Box.createVerticalStrut(8));
        centerPanel.add(lblZone);
        centerPanel.add(Box.createVerticalStrut(15));

        // Thanh trực quan khoảng cách (Gauge)
        DistanceGaugePanel gauge = new DistanceGaugePanel();
        gauge.setPreferredSize(new Dimension(500, 32));
        gauge.setMaximumSize(new Dimension(800, 32));
        gauge.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(gauge);
        centerPanel.add(Box.createVerticalStrut(15));

        // 3. Slider điều chỉnh hệ số suy hao n (Path Loss Exponent)
        JPanel sliderPanel = new JPanel(new BorderLayout(10, 5));
        sliderPanel.setOpaque(false);
        sliderPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel lblSliderTitle = new JLabel("Tinh chỉnh môi trường (Path Loss Exponent n):");
        lblSliderTitle.setFont(Theme.FONT_BOLD);
        lblSliderTitle.setForeground(Theme.TEXT_MAIN);

        sliderPathLoss.setOpaque(false);
        sliderPathLoss.setBackground(Theme.BG_CARD);
        sliderPathLoss.setForeground(Theme.TEXT_MUTED);
        sliderPathLoss.setMajorTickSpacing(5);
        sliderPathLoss.setPaintTicks(true);

        sliderPathLoss.addChangeListener(e -> {
            double n = sliderPathLoss.getValue() / 10.0;
            String desc;
            if (n <= 2.1) desc = "Không gian mở (Free Space)";
            else if (n <= 2.7) desc = "Trong nhà tiêu chuẩn (Indoor LOS)";
            else if (n <= 3.3) desc = "Có vách ngăn / tường mỏng";
            else desc = "Nhiều tường bê tông / vật cản (NLOS)";

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
                gauge.setDistance(est.getDistanceMeters());
                if (onRecalculateListener != null) {
                    onRecalculateListener.accept(est);
                }
            }
        });

        lblSliderVal.setFont(Theme.FONT_REGULAR);
        lblSliderVal.setForeground(Theme.ACCENT_CYAN);

        sliderPanel.add(lblSliderTitle, BorderLayout.NORTH);
        sliderPanel.add(sliderPathLoss, BorderLayout.CENTER);
        sliderPanel.add(lblSliderVal, BorderLayout.SOUTH);

        centerPanel.add(sliderPanel);
        add(centerPanel, BorderLayout.CENTER);

        // 4. Bottom: Chi tiết công thức toán học và thông số đo
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setOpaque(false);

        JLabel lblDetailTitle = new JLabel("Chi tiết phân tích toán học & kỹ thuật:");
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
            lblTargetTitle.setText("Chưa chọn thiết bị để đo khoảng cách");
            lblHeroDistance.setText("-- m");
            lblRange.setText("Khoảng tin cậy: --");
            lblZone.setText("Khu vực: --");
            lblConfidence.setText("Độ tin cậy: --");
            txtAnalysis.setText("Vui lòng chọn một thiết bị từ danh sách Wi-Fi để xem khoảng cách.");
            return;
        }

        DistanceEstimate est = device.getDistanceEstimate();
        if (est == null) {
            double n = sliderPathLoss.getValue() / 10.0;
            est = DistanceCalculator.calculateDistance(
                    device.getIpAddress(),
                    device.isGateway(),
                    device.isLocalHost(),
                    wifiInfo,
                    device.getRttMs(),
                    n,
                    device.getMacAddress()
            );
            device.setDistanceEstimate(est);
        }

        updateEstimate(device, wifiInfo, est);
    }

    private void updateEstimate(NetworkDevice device, WifiInfo wifiInfo, DistanceEstimate est) {
        lblTargetTitle.setText(String.format("Khoảng cách tới: %s (%s)", device.getIpAddress(), device.getHostName()));

        if (device.isLocalHost()) {
            lblHeroDistance.setText("0.00 mét");
            lblHeroDistance.setForeground(new Color(167, 139, 250));
        } else {
            lblHeroDistance.setText(String.format("%.2f mét", est.getDistanceMeters()));
            lblHeroDistance.setForeground(Theme.SUCCESS_GREEN);
        }

        lblRange.setText(String.format("Khoảng tin cậy: %.1f m — %.1f m", est.getMinDistanceMeters(), est.getMaxDistanceMeters()));
        lblZone.setText("Vùng ước lượng: " + est.getZoneDescription());
        lblConfidence.setText("Độ tin cậy: " + est.getConfidenceLevel());

        StringBuilder sb = new StringBuilder();
        sb.append("• Phương pháp: ").append(est.getCalculationMethod()).append("\n");
        sb.append("• Công thức áp dụng: ").append(est.getMathematicalFormula()).append("\n\n");
        sb.append(est.getDetailedAnalysis());

        txtAnalysis.setText(sb.toString());
        txtAnalysis.setCaretPosition(0);
        repaint();
    }

    /**
     * Vạch màu hiển thị vị trí khoảng cách từ 0m đến 30m.
     */
    private static class DistanceGaugePanel extends JPanel {
        private double distanceMeters = 0.0;

        public DistanceGaugePanel() {
            setOpaque(false);
        }

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
            int h = getHeight();
            int barY = 4;
            int barH = 14;

            // Nền thanh đo
            g2.setColor(new Color(24, 33, 47));
            g2.fillRoundRect(0, barY, w, barH, 8, 8);

            // Dải gradient từ xanh lá (gần) -> vàng -> đỏ (xa)
            GradientPaint gp = new GradientPaint(
                    0, barY, new Color(16, 185, 129),
                    w, barY, new Color(244, 63, 94)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(0, barY, w, barH, 8, 8);

            // Vẽ kim chỉ vị trí khoảng cách (tối đa mốc 25m)
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
            g2.drawString("0m", 2, barY + barH + 12);
            g2.drawString("5m", (int)(w * 0.2), barY + barH + 12);
            g2.drawString("10m", (int)(w * 0.4), barY + barH + 12);
            g2.drawString("15m", (int)(w * 0.6), barY + barH + 12);
            g2.drawString("20m+", (int)(w * 0.85), barY + barH + 12);

            g2.dispose();
        }
    }
}
