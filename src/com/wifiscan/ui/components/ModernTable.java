package com.wifiscan.ui.components;

import com.wifiscan.model.NetworkDevice;
import com.wifiscan.model.PortState;
import com.wifiscan.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Bảng dữ liệu phong cách hiện đại với bo viền và định dạng nhãn Badge nhiều màu.
 */
public class ModernTable extends JTable {

    public ModernTable(DefaultTableModel model) {
        super(model);
        setRowHeight(34);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 1));
        setFont(Theme.FONT_REGULAR);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader header = getTableHeader();
        header.setPreferredSize(new Dimension(100, 38));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setBackground(new Color(24, 34, 53));
                label.setForeground(Theme.TEXT_MAIN);
                label.setFont(Theme.FONT_BOLD);
                label.setBorder(new EmptyBorder(0, 12, 0, 12));
                return label;
            }
        });

        setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setFont(Theme.FONT_REGULAR);
                label.setBorder(new EmptyBorder(0, 12, 0, 12));

                if (isSelected) {
                    label.setBackground(new Color(79, 70, 229));
                    label.setForeground(Color.WHITE);
                    return label;
                }

                // Xen kẽ màu dòng
                if (row % 2 == 0) {
                    label.setBackground(Theme.BG_CARD);
                } else {
                    label.setBackground(new Color(24, 33, 47));
                }
                label.setForeground(Theme.TEXT_MAIN);

                if (value instanceof PortState) {
                    PortState state = (PortState) value;
                    if (state == PortState.OPEN) {
                        label.setForeground(Theme.SUCCESS_GREEN);
                        label.setFont(Theme.FONT_BOLD);
                    } else if (state == PortState.CLOSED) {
                        label.setForeground(Theme.DANGER_RED);
                    } else {
                        label.setForeground(Theme.WARNING_AMBER);
                    }
                } else if (value instanceof NetworkDevice.DeviceType) {
                    NetworkDevice.DeviceType type = (NetworkDevice.DeviceType) value;
                    if (type == NetworkDevice.DeviceType.GATEWAY) {
                        label.setForeground(Theme.ACCENT_CYAN);
                        label.setFont(Theme.FONT_BOLD);
                    } else if (type == NetworkDevice.DeviceType.LOCAL_SCANNER) {
                        label.setForeground(new Color(167, 139, 250));
                        label.setFont(Theme.FONT_BOLD);
                    } else {
                        label.setForeground(Theme.TEXT_MAIN);
                    }
                    label.setText(type.getLabel());
                }

                return label;
            }
        });
    }
}
