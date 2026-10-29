package com.wifiscan.ui.components;

import com.wifiscan.model.NetworkDevice;
import com.wifiscan.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Radar quét Wi-Fi trực quan 360 độ:
 * Hỗ trợ 2 chế độ hiển thị:
 * 1. CLIENT_TO_SCANNER: Tâm là Máy Quét này (Scanner - 0m)
 * 2. CLIENT_TO_AP: Tâm là Bộ phát Wi-Fi (Router AP - 0m)
 */
public class RadarPanel extends JPanel {

    public enum RadarMode {
        CLIENT_TO_SCANNER,
        CLIENT_TO_AP
    }

    private final List<NetworkDevice> devices = new ArrayList<>();
    private NetworkDevice selectedDevice = null;
    private double sweepAngle = 0; // độ
    private final Timer animationTimer;
    private RadarMode radarMode = RadarMode.CLIENT_TO_SCANNER;

    public RadarPanel() {
        this(RadarMode.CLIENT_TO_SCANNER);
    }

    public RadarPanel(RadarMode mode) {
        this.radarMode = mode;
        setOpaque(false);
        setBackground(Theme.BG_DARK);

        // Animation Timer xoay radar ở tốc độ ~40 FPS
        animationTimer = new Timer(25, e -> {
            sweepAngle = (sweepAngle + 2.5) % 360;
            repaint();
        });
        animationTimer.start();

        // Xử lý click chọn thiết bị trên màn hình radar
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int maxRadius = Math.min(cx, cy) - 30;

                for (int i = 0; i < devices.size(); i++) {
                    NetworkDevice dev = devices.get(i);
                    double dist = getDeviceDistance(dev);
                    double angleRad = Math.toRadians((i * 360.0 / Math.max(1, devices.size())) - 90);
                    double rRatio = Math.min(dist / 20.0, 1.0);
                    int px = cx + (int) (rRatio * maxRadius * Math.cos(angleRad));
                    int py = cy + (int) (rRatio * maxRadius * Math.sin(angleRad));

                    if (Point2D.distance(e.getX(), e.getY(), px, py) <= 14) {
                        selectedDevice = dev;
                        repaint();
                        break;
                    }
                }
            }
        });
    }

    public void setRadarMode(RadarMode mode) {
        this.radarMode = mode;
        repaint();
    }

    public RadarMode getRadarMode() {
        return radarMode;
    }

    public void setDevices(List<NetworkDevice> devList) {
        devices.clear();
        if (devList != null) {
            devices.addAll(devList);
        }
        repaint();
    }

    public void setSelectedDevice(NetworkDevice dev) {
        this.selectedDevice = dev;
        repaint();
    }

    private double getDeviceDistance(NetworkDevice dev) {
        if (dev.getDistanceEstimate() == null) return 2.5;
        if (radarMode == RadarMode.CLIENT_TO_AP) {
            return dev.getDistanceEstimate().getDistanceToApMeters();
        } else {
            return dev.getDistanceEstimate().getDistanceMeters();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        int maxRadius = Math.min(cx, cy) - 35;

        // 1. Vẽ nền radar tối
        g2.setColor(new Color(15, 23, 42));
        g2.fillOval(cx - maxRadius, cy - maxRadius, maxRadius * 2, maxRadius * 2);

        // 2. Vẽ các vòng tròn khoảng cách: 2m, 5m, 10m, 15m, 20m
        double[] distanceRings = {2.0, 5.0, 10.0, 15.0, 20.0};
        String[] ringLabels = {"2m", "5m", "10m", "15m", "20m+"};

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        for (int i = 0; i < distanceRings.length; i++) {
            int r = (int) ((distanceRings[i] / 20.0) * maxRadius);
            g2.setColor(new Color(30, 41, 59, 180));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            g2.setColor(new Color(100, 116, 139, 200));
            g2.drawString(ringLabels[i], cx + 4, cy - r + 12);
        }

        // 3. Vẽ 2 trục chữ thập (Crosshair)
        g2.setColor(new Color(51, 65, 85, 120));
        g2.drawLine(cx - maxRadius, cy, cx + maxRadius, cy);
        g2.drawLine(cx, cy - maxRadius, cx, cy + maxRadius);

        // 4. Vẽ quạt quét Radar Sweep
        int sweepR = maxRadius;
        double radSweep = Math.toRadians(sweepAngle);
        int sx = cx + (int) (sweepR * Math.cos(radSweep));
        int sy = cy + (int) (sweepR * Math.sin(radSweep));

        Polygon sweepPoly = new Polygon();
        sweepPoly.addPoint(cx, cy);
        for (int a = 0; a <= 25; a += 5) {
            double trailRad = Math.toRadians(sweepAngle - a);
            sweepPoly.addPoint(
                    cx + (int) (sweepR * Math.cos(trailRad)),
                    cy + (int) (sweepR * Math.sin(trailRad))
            );
        }

        Color sweepColor = (radarMode == RadarMode.CLIENT_TO_AP) ? new Color(6, 182, 212) : new Color(16, 185, 129);
        g2.setPaint(new RadialGradientPaint(
                new Point2D.Float(cx, cy), maxRadius,
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(sweepColor.getRed(), sweepColor.getGreen(), sweepColor.getBlue(), 90),
                           new Color(sweepColor.getRed(), sweepColor.getGreen(), sweepColor.getBlue(), 0)}
        ));
        g2.fill(sweepPoly);

        // Tia quét chính
        g2.setColor(sweepColor);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(cx, cy, sx, sy);

        // 5. Vẽ Đối tượng ở tâm (0m) theo chế độ
        if (radarMode == RadarMode.CLIENT_TO_AP) {
            // Tâm là Router / AP (Bộ phát Wi-Fi)
            g2.setColor(Theme.ACCENT_CYAN);
            g2.fillOval(cx - 8, cy - 8, 16, 16);
            g2.setColor(Color.WHITE);
            g2.drawOval(cx - 8, cy - 8, 16, 16);
            g2.setFont(Theme.FONT_BOLD);
            g2.setColor(new Color(165, 243, 252));
            g2.drawString("Bộ phát AP Router (0m)", cx - 62, cy + 24);
        } else {
            // Tâm là Scanner (Máy này)
            g2.setColor(new Color(99, 102, 241));
            g2.fillOval(cx - 7, cy - 7, 14, 14);
            g2.setColor(Color.WHITE);
            g2.drawOval(cx - 7, cy - 7, 14, 14);
            g2.setFont(Theme.FONT_BOLD);
            g2.setColor(new Color(199, 210, 254));
            g2.drawString("Scanner (Máy này - 0m)", cx - 62, cy + 24);
        }

        // 6. Vẽ các thiết bị lên Radar
        int totalDevs = devices.size();
        for (int i = 0; i < totalDevs; i++) {
            NetworkDevice dev = devices.get(i);

            // Bỏ qua nếu thiết bị trùng với tâm
            if (radarMode == RadarMode.CLIENT_TO_AP && dev.isGateway()) continue;
            if (radarMode == RadarMode.CLIENT_TO_SCANNER && dev.isLocalHost()) continue;

            double dist = getDeviceDistance(dev);
            double angleRad = Math.toRadians((i * 360.0 / Math.max(1, totalDevs)) - 90);

            // Bán kính hiển thị tỉ lệ theo khoảng cách thực
            double rRatio = Math.min(Math.max(dist / 20.0, 0.08), 0.95);
            int px = cx + (int) (rRatio * maxRadius * Math.cos(angleRad));
            int py = cy + (int) (rRatio * maxRadius * Math.sin(angleRad));

            boolean isSel = (dev == selectedDevice);

            // Vòng hào quang nếu đang được chọn
            if (isSel) {
                g2.setColor(new Color(251, 191, 36, 160));
                g2.fillOval(px - 11, py - 11, 22, 22);
            }

            // Màu chấm thiết bị
            if (dev.isGateway()) {
                g2.setColor(Theme.ACCENT_CYAN); // Router AP màu cyan
            } else if (dev.isLocalHost()) {
                g2.setColor(new Color(167, 139, 250)); // Máy Scanner màu tím nhạt
            } else {
                g2.setColor(Theme.SUCCESS_GREEN); // Client màu xanh lá
            }
            g2.fillOval(px - 6, py - 6, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(px - 6, py - 6, 12, 12);

            // Nhãn tên và khoảng cách
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String label = String.format("%s (%.1fm)", dev.getIpAddress(), dist);
            g2.setColor(isSel ? Theme.WARNING_AMBER : Theme.TEXT_MAIN);
            g2.drawString(label, px + 8, py + 4);
        }

        // Vòng viền ngoài cùng
        g2.setColor(Theme.BORDER);
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(cx - maxRadius, cy - maxRadius, maxRadius * 2, maxRadius * 2);

        g2.dispose();
    }
}
