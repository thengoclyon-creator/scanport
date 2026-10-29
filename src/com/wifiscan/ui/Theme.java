package com.wifiscan.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Bảng màu và quy chuẩn giao diện hiện đại (Modern Dark Theme).
 */
public class Theme {
    public static final Color BG_DARK = new Color(15, 23, 42);         // Slate 900
    public static final Color BG_PANEL = new Color(30, 41, 59);        // Slate 800
    public static final Color BG_CARD = new Color(30, 41, 59);         // Slate 800
    public static final Color BG_INPUT = new Color(15, 23, 42);        // Slate 900
    public static final Color BORDER = new Color(51, 65, 85);          // Slate 700
    public static final Color BORDER_FOCUS = new Color(99, 102, 241);  // Indigo 500

    public static final Color TEXT_MAIN = new Color(248, 250, 252);    // Slate 50
    public static final Color TEXT_MUTED = new Color(148, 163, 184);   // Slate 400
    public static final Color TEXT_DIM = new Color(100, 116, 139);     // Slate 500

    public static final Color ACCENT_INDIGO = new Color(99, 102, 241); // Indigo 500
    public static final Color ACCENT_CYAN = new Color(6, 182, 212);    // Cyan 500
    public static final Color SUCCESS_GREEN = new Color(16, 185, 129); // Emerald 500
    public static final Color DANGER_RED = new Color(244, 63, 94);     // Rose 500
    public static final Color WARNING_AMBER = new Color(245, 158, 11); // Amber 500

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);
    public static final Font FONT_MONO_BOLD = new Font("Consolas", Font.BOLD, 13);
    public static final Font FONT_HERO = new Font("Segoe UI", Font.BOLD, 28);

    public static void setupGlobalStyles() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("Label.foreground", TEXT_MAIN);
        UIManager.put("Label.font", FONT_REGULAR);
        UIManager.put("Button.font", FONT_BOLD);
        UIManager.put("Table.background", BG_PANEL);
        UIManager.put("Table.foreground", TEXT_MAIN);
        UIManager.put("Table.selectionBackground", ACCENT_INDIGO);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("TableHeader.background", new Color(20, 29, 47));
        UIManager.put("TableHeader.foreground", TEXT_MAIN);
        UIManager.put("TableHeader.font", FONT_BOLD);
        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("ScrollBar.thumb", BORDER);
    }
}
