package com.wifiscan.ui.components;

import com.wifiscan.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Nút bấm phong cách hiện đại với hiệu ứng Hover và bo góc mềm mại.
 */
public class ModernButton extends JButton {

    private final Color baseColor;
    private final Color hoverColor;
    private boolean isHovered = false;
    private int cornerRadius = 8;

    public ModernButton(String text, Color baseColor, Color hoverColor) {
        super(text);
        this.baseColor = baseColor;
        this.hoverColor = hoverColor;
        init();
    }

    public ModernButton(String text, Color baseColor) {
        this(text, baseColor, baseColor.brighter());
    }

    public ModernButton(String text) {
        this(text, Theme.ACCENT_INDIGO, new Color(129, 140, 248));
    }

    private void init() {
        setFont(Theme.FONT_BOLD);
        setForeground(Color.WHITE);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(8, 16, 8, 16));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }
        });
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (!isEnabled()) {
            g2.setColor(Theme.BORDER);
        } else if (isHovered) {
            g2.setColor(hoverColor);
        } else {
            g2.setColor(baseColor);
        }

        g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);
        g2.dispose();

        super.paintComponent(g);
    }
}
