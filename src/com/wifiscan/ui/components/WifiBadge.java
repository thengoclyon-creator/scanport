package com.wifiscan.ui.components;

import com.wifiscan.model.WifiInfo;
import com.wifiscan.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Thanh huy hiệu hiển thị trạng thái kết nối Wi-Fi, SSID, RSSI và IP máy Scanner.
 */
public class WifiBadge extends JPanel {

    private final JLabel lblStatus = new JLabel("● ĐANG KIỂM TRA...");
    private final JLabel lblSsid = new JLabel("SSID: --");
    private final JLabel lblRssi = new JLabel("RSSI: -- dBm");
    private final JLabel lblLocalIp = new JLabel("IP Scanner: --");
    private final JLabel lblGateway = new JLabel("Gateway: --");
    private final JLabel lblBand = new JLabel("Tần số: --");

    public WifiBadge() {
        setLayout(new BorderLayout(15, 10));
        setBackground(new Color(24, 33, 47));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(10, 16, 10, 16)
        ));

        initUI();
    }

    private void initUI() {
        // Bên trái: Trạng thái và SSID
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftPanel.setOpaque(false);

        lblStatus.setFont(Theme.FONT_BOLD);
        lblStatus.setForeground(Theme.SUCCESS_GREEN);

        lblSsid.setFont(Theme.FONT_BOLD);
        lblSsid.setForeground(Theme.TEXT_MAIN);

        lblRssi.setFont(Theme.FONT_REGULAR);
        lblRssi.setForeground(Theme.ACCENT_CYAN);

        leftPanel.add(lblStatus);
        leftPanel.add(lblSsid);
        leftPanel.add(lblRssi);

        // Bên phải: Thông số mạng IP và Gateway
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightPanel.setOpaque(false);

        lblLocalIp.setFont(Theme.FONT_MONO_BOLD);
        lblLocalIp.setForeground(new Color(167, 139, 250));

        lblGateway.setFont(Theme.FONT_MONO);
        lblGateway.setForeground(Theme.TEXT_MUTED);

        lblBand.setFont(Theme.FONT_REGULAR);
        lblBand.setForeground(Theme.TEXT_MUTED);

        rightPanel.add(lblLocalIp);
        rightPanel.add(lblGateway);
        rightPanel.add(lblBand);

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);
    }

    public void updateWifi(WifiInfo info) {
        if (info.isConnected()) {
            lblStatus.setText("● WI-FI ĐANG KẾT NỐI");
            lblStatus.setForeground(Theme.SUCCESS_GREEN);
            lblSsid.setText("SSID: " + info.getSsid());
            lblRssi.setText(String.format("RSSI: %d dBm (%d%%)", info.getRssiDbm(), info.getSignalPercent()));
            lblLocalIp.setText("Scanner IP: " + info.getLocalIp());
            lblGateway.setText("Gateway: " + info.getGatewayIp());
            lblBand.setText(info.getBand() + " / Kênh " + info.getChannel());
        } else {
            lblStatus.setText("● CHƯA KẾT NỐI WI-FI");
            lblStatus.setForeground(Theme.DANGER_RED);
            lblSsid.setText("SSID: Không");
            lblRssi.setText("RSSI: N/A");
            lblLocalIp.setText("IP: " + info.getLocalIp());
            lblGateway.setText("Gateway: " + info.getGatewayIp());
            lblBand.setText("-");
        }
    }
}
