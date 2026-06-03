package ui;

import core.Scheduler.GanttSegment;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public class GanttChartPanel extends JPanel {

    private static final int UNIT_WIDTH = 38;
    private static final int BAR_HEIGHT = 56;
    private static final int TOP_PAD = 14;
    private static final int BOTTOM_PAD = 30;
    private static final int LEFT_PAD = 14;

    private List<GanttSegment> segments = List.of();

    private static final Color[] PALETTE = new Color[]{
            new Color(0x7A, 0xA2, 0xF7),
            new Color(0xBB, 0x9A, 0xF7),
            new Color(0x9E, 0xCE, 0x6A),
            new Color(0xE0, 0xAF, 0x68),
            new Color(0x7D, 0xCF, 0xFF),
            new Color(0xF7, 0x76, 0x8E),
            new Color(0xFF, 0x9E, 0x64),
            new Color(0x73, 0xDA, 0xCA),
    };

    public GanttChartPanel() {
        setBackground(Theme.BG_DEEP);
        setBorder(new EmptyBorder(8, 12, 8, 12));
    }

    public void setSegments(List<GanttSegment> segments) {
        this.segments = segments == null ? List.of() : segments;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int totalTime = segments.isEmpty() ? 10 : segments.get(segments.size() - 1).end;
        int width = LEFT_PAD * 2 + Math.max(totalTime, 8) * UNIT_WIDTH;
        int height = TOP_PAD + BAR_HEIGHT + BOTTOM_PAD;
        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (segments.isEmpty()) {
            g2.setColor(Theme.TEXT_DIM);
            g2.setFont(Theme.FONT_BODY);
            String msg = "No execution yet. Press Run or Step to schedule processes.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, LEFT_PAD, TOP_PAD + BAR_HEIGHT / 2 + fm.getAscent() / 2);
            g2.dispose();
            return;
        }

        int barY = TOP_PAD;
        for (GanttSegment seg : segments) {
            int x = LEFT_PAD + seg.start * UNIT_WIDTH;
            int w = seg.length() * UNIT_WIDTH;
            Color base = colorFor(seg.pid);
            g2.setColor(base);
            g2.fill(new RoundRectangle2D.Float(x + 1, barY, w - 2, BAR_HEIGHT, 10, 10));
            g2.setColor(base.darker());
            g2.draw(new RoundRectangle2D.Float(x + 1, barY, w - 2, BAR_HEIGHT, 10, 10));

            g2.setColor(Theme.BG_DEEP);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            String label = "P" + seg.pid;
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (w - fm.stringWidth(label)) / 2;
            int ty = barY + (BAR_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(label, tx, ty);
        }

        int totalTime = segments.get(segments.size() - 1).end;
        int tickY = TOP_PAD + BAR_HEIGHT + 4;
        g2.setColor(Theme.TEXT_DIM);
        g2.setFont(Theme.FONT_MONO);
        FontMetrics fm = g2.getFontMetrics();
        for (int t = 0; t <= totalTime; t++) {
            int x = LEFT_PAD + t * UNIT_WIDTH;
            g2.drawLine(x, tickY, x, tickY + 4);
            String label = Integer.toString(t);
            g2.drawString(label, x - fm.stringWidth(label) / 2, tickY + 4 + fm.getAscent() + 2);
        }
        g2.dispose();
    }

    public static Color colorFor(int pid) {
        return PALETTE[Math.floorMod(pid - 1, PALETTE.length)];
    }
}
