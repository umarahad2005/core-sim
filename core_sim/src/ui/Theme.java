package ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public final class Theme {

    public static final Color BG_DEEP    = new Color(0x12, 0x16, 0x22);
    public static final Color BG_PANEL   = new Color(0x1B, 0x20, 0x32);
    public static final Color BG_RAISED  = new Color(0x25, 0x2C, 0x44);
    public static final Color BORDER     = new Color(0x39, 0x44, 0x66);

    public static final Color ACCENT     = new Color(0x7A, 0xA2, 0xF7);
    public static final Color ACCENT_2   = new Color(0xBB, 0x9A, 0xF7);
    public static final Color SUCCESS    = new Color(0x9E, 0xCE, 0x6A);
    public static final Color WARN       = new Color(0xE0, 0xAF, 0x68);
    public static final Color DANGER     = new Color(0xF7, 0x76, 0x8E);
    public static final Color INFO       = new Color(0x7D, 0xCF, 0xFF);

    public static final Color TEXT       = new Color(0xC0, 0xCA, 0xF5);
    public static final Color TEXT_DIM   = new Color(0x9A, 0xA5, 0xCE);

    public static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_HEAD   = new Font("Segoe UI Semibold", Font.PLAIN, 14);
    public static final Font FONT_BODY   = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_MONO   = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private Theme() {}

    public static void apply() {
        UIManager.put("Panel.background", BG_PANEL);
        UIManager.put("OptionPane.background", BG_PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", FONT_BODY);
        UIManager.put("TextField.background", BG_RAISED);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.border", new EmptyBorder(6, 8, 6, 8));
        UIManager.put("TextArea.background", BG_DEEP);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", ACCENT);
        UIManager.put("ScrollPane.background", BG_PANEL);
        UIManager.put("Viewport.background", BG_PANEL);
        UIManager.put("Table.background", BG_PANEL);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.selectionBackground", ACCENT);
        UIManager.put("Table.selectionForeground", BG_DEEP);
        UIManager.put("TableHeader.background", BG_RAISED);
        UIManager.put("TableHeader.foreground", ACCENT);
        UIManager.put("TableHeader.font", FONT_HEAD);
        UIManager.put("Button.background", BG_RAISED);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.font", FONT_BODY);
        UIManager.put("Button.focusPainted", Boolean.FALSE);
        UIManager.put("TitledBorder.titleColor", ACCENT);
        UIManager.put("TitledBorder.font", FONT_HEAD);
    }

    public static Border titled(String title) {
        TitledBorder t = BorderFactory.createTitledBorder(
                new LineBorder(BORDER, 1, true), "  " + title + "  ");
        t.setTitleColor(ACCENT);
        t.setTitleFont(FONT_HEAD);
        return BorderFactory.createCompoundBorder(t, new EmptyBorder(8, 10, 10, 10));
    }
}
