package ui;

import core.MemoryManager;
import core.MemoryManager.Frame;
import core.ProcessManager;
import model.PCB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Module 3 — Memory Management (Paging + LRU) tab.
 *
 * <p>Layout mirrors {@link SchedulingPanel}: a rounded toolbar on top, a
 * status row, the visual memory blocks in the centre, the consolidated page
 * table on the bottom, and a dedicated event log so page faults / hits /
 * replacements are visible from this tab without having to jump to Module 1.
 */
public class MemoryPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ProcessManager processManager;
    private final MemoryManager memory;

    private final JSpinner frameSpinner   = new JSpinner(new SpinnerNumberModel(4,  1, 32, 1));
    private final JSpinner accessPidField = new JSpinner(new SpinnerNumberModel(1,  1, 999, 1));
    private final JSpinner accessPageField= new JSpinner(new SpinnerNumberModel(0,  0, 999, 1));
    private final JSpinner allocPagesField= new JSpinner(new SpinnerNumberModel(3,  1, 32, 1));
    private final JTextField refStringField = new JTextField("1, 2, 3, 1, 4", 18);
    private final JSpinner defaultPidField = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JSpinner freePidField   = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));

    private final MemoryBlocksPanel blocksPanel;
    private final PageTableModel pageTableModel = new PageTableModel();
    private final JTable pageTable = new JTable(pageTableModel);

    private final JLabel accessesLabel = new JLabel("0");
    private final JLabel hitsLabel     = new JLabel("0");
    private final JLabel faultsLabel   = new JLabel("0");
    private final JLabel hitRateLabel  = new JLabel("0.0%");

    private final JTextArea logArea = new JTextArea(6, 40);

    public MemoryPanel(ProcessManager processManager, MemoryManager memory) {
        this.processManager = processManager;
        this.memory = memory;
        this.blocksPanel = new MemoryBlocksPanel(memory);

        setOpaque(false);
        setLayout(new BorderLayout(12, 12));
        add(buildToolbar(),    BorderLayout.NORTH);
        add(buildCenter(),     BorderLayout.CENTER);

        refresh();
    }

    // ------------------------------------------------------------------
    // Toolbar
    // ------------------------------------------------------------------
    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.setOpaque(false);

        bar.add(dimLabel("Frames:"));
        frameSpinner.setValue(memory.getFrameCount());
        bar.add(frameSpinner);
        bar.add(controlButton("Apply", Theme.INFO, e -> onApplyFrames()));

        bar.add(separator());

        bar.add(dimLabel("PID:"));
        bar.add(accessPidField);
        bar.add(dimLabel("Page:"));
        bar.add(accessPageField);
        bar.add(controlButton("Access", Theme.ACCENT, e -> onAccess()));

        bar.add(separator());

        bar.add(dimLabel("Allocate Pages:"));
        bar.add(allocPagesField);
        bar.add(controlButton("Allocate", Theme.SUCCESS, e -> onAllocate()));

        bar.add(separator());

        bar.add(dimLabel("Reference String:"));
        refStringField.setColumns(16);
        refStringField.setToolTipText(
                "Comma-separated pages. Plain numbers use the default PID; "
              + "use \"pid:page\" for cross-process refs. e.g. \"1,2,3,1,4\" or \"1:0, 2:1, 1:0\"");
        bar.add(refStringField);
        bar.add(dimLabel("default PID:"));
        bar.add(defaultPidField);
        bar.add(controlButton("Run Refs", Theme.ACCENT_2, e -> onRunReferenceString()));

        bar.add(separator());

        bar.add(dimLabel("Free PID:"));
        bar.add(freePidField);
        bar.add(controlButton("Free", Theme.WARN, e -> onFreeProcess()));

        bar.add(controlButton("Reset", Theme.DANGER, e -> onReset()));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Memory Controls"));
        wrap.add(bar, BorderLayout.CENTER);
        return wrapRounded(wrap);
    }

    private Component separator() {
        JPanel sep = new JPanel();
        sep.setOpaque(false);
        sep.setPreferredSize(new Dimension(14, 1));
        return sep;
    }

    // ------------------------------------------------------------------
    // Center area
    // ------------------------------------------------------------------
    private JComponent buildCenter() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);

        center.add(buildStatusRow(), BorderLayout.NORTH);

        JPanel mid = new JPanel(new BorderLayout(12, 12));
        mid.setOpaque(false);
        mid.add(buildBlocks(), BorderLayout.CENTER);

        JPanel southStack = new JPanel(new BorderLayout(12, 12));
        southStack.setOpaque(false);
        southStack.add(buildPageTable(), BorderLayout.CENTER);
        southStack.add(buildLog(),       BorderLayout.SOUTH);
        mid.add(southStack, BorderLayout.SOUTH);

        center.add(mid, BorderLayout.CENTER);
        return center;
    }

    private JComponent buildStatusRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.add(statCard("Accesses",  accessesLabel, Theme.INFO));
        row.add(statCard("Hits",      hitsLabel,     Theme.SUCCESS));
        row.add(statCard("Faults",    faultsLabel,   Theme.DANGER));
        row.add(statCard("Hit Rate",  hitRateLabel,  Theme.ACCENT_2));
        return row;
    }

    private JComponent statCard(String title, JLabel valueLabel, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Theme.BG_PANEL);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setForeground(accent);

        valueLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        valueLabel.setForeground(Theme.TEXT);

        card.add(t, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return wrapRounded(card);
    }

    private JComponent buildBlocks() {
        JScrollPane scroll = new JScrollPane(blocksPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG_DEEP);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Physical Memory (Frames)"));
        wrap.add(scroll, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(0, 200));
        return wrapRounded(wrap);
    }

    private JComponent buildPageTable() {
        pageTable.setRowHeight(24);
        pageTable.setShowGrid(false);
        pageTable.setIntercellSpacing(new Dimension(0, 0));
        pageTable.setBackground(Theme.BG_PANEL);
        pageTable.setForeground(Theme.TEXT);
        pageTable.setSelectionBackground(Theme.BG_RAISED);
        pageTable.setSelectionForeground(Theme.ACCENT);
        pageTable.setFont(Theme.FONT_MONO);
        pageTable.getTableHeader().setBackground(Theme.BG_RAISED);
        pageTable.getTableHeader().setForeground(Theme.ACCENT);
        pageTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        pageTable.setDefaultRenderer(Object.class, new PageTableRenderer());

        JScrollPane scroll = new JScrollPane(pageTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG_PANEL);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Page Tables (per-process page → frame mapping)"));
        wrap.add(scroll, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(0, 180));
        return wrapRounded(wrap);
    }

    private JComponent buildLog() {
        logArea.setEditable(false);
        logArea.setFont(Theme.FONT_MONO);
        logArea.setBackground(Theme.BG_DEEP);
        logArea.setForeground(Theme.TEXT);
        logArea.setCaretColor(Theme.ACCENT);
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG_DEEP);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Memory Event Log"));
        wrap.add(scroll, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(0, 140));
        return wrapRounded(wrap);
    }

    // ------------------------------------------------------------------
    // Button handlers
    // ------------------------------------------------------------------
    private void onApplyFrames() {
        int n = (int) frameSpinner.getValue();
        memory.resize(n);
        refresh();
    }

    private void onAccess() {
        int pid = (int) accessPidField.getValue();
        int page = (int) accessPageField.getValue();
        memory.accessPage(pid, page);
        refresh();
    }

    private void onAllocate() {
        int pid = (int) accessPidField.getValue();
        int pages = (int) allocPagesField.getValue();
        memory.allocatePages(pid, pages);
        refresh();
    }

    private void onRunReferenceString() {
        String raw = refStringField.getText();
        int defaultPid = (int) defaultPidField.getValue();
        List<int[]> refs = parseReferenceString(raw, defaultPid);
        if (refs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Could not parse any references from \"" + raw + "\".\n"
                  + "Use comma-separated numbers (e.g. 1, 2, 3, 1, 4) or pid:page pairs.",
                    "Reference String", JOptionPane.WARNING_MESSAGE);
            return;
        }
        appendLog("Running reference string [" + refs.size() + " refs] with default PID = " + defaultPid);
        for (int[] ref : refs) memory.accessPage(ref[0], ref[1]);
        refresh();
    }

    private void onFreeProcess() {
        int pid = (int) freePidField.getValue();
        memory.freeProcess(pid);
        refresh();
    }

    private void onReset() {
        memory.reset();
        refresh();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    /** Parses "1, 2, 3:0, 1:2" -> list of {pid, page}. Bare numbers use defaultPid. */
    public static List<int[]> parseReferenceString(String raw, int defaultPid) {
        List<int[]> out = new ArrayList<>();
        if (raw == null) return out;
        String[] tokens = raw.split("[,\\s]+");
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            try {
                if (t.contains(":")) {
                    String[] parts = t.split(":", 2);
                    int pid = Integer.parseInt(parts[0].trim());
                    int page = Integer.parseInt(parts[1].trim());
                    out.add(new int[]{pid, page});
                } else {
                    out.add(new int[]{defaultPid, Integer.parseInt(t.trim())});
                }
            } catch (NumberFormatException ignored) {
                // skip malformed token, keep going
            }
        }
        return out;
    }

    /** Called externally so the kernel can pipe its log into this panel. */
    public void appendLog(String msg) {
        logArea.append("[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /** Refresh every visible widget from current memory state. */
    public void refresh() {
        accessesLabel.setText(Integer.toString(memory.getAccesses()));
        hitsLabel.setText(Integer.toString(memory.getPageHits()));
        faultsLabel.setText(Integer.toString(memory.getPageFaults()));
        hitRateLabel.setText(String.format("%.1f%%", memory.getHitRate()));
        blocksPanel.repaint();
        pageTableModel.fireTableDataChanged();
        seedPidSpinnersFromKernel();
    }

    /**
     * If the user just created PCBs in Module 1 we nudge the access / free
     * spinners to a valid PID so they don't have to type one in manually.
     */
    private void seedPidSpinnersFromKernel() {
        List<PCB> processes = processManager.getProcesses();
        if (processes.isEmpty()) return;
        int firstPid = processes.get(0).getPid();
        if (!isValidPid((int) accessPidField.getValue(), processes)) accessPidField.setValue(firstPid);
        if (!isValidPid((int) defaultPidField.getValue(), processes)) defaultPidField.setValue(firstPid);
        if (!isValidPid((int) freePidField.getValue(), processes))   freePidField.setValue(firstPid);
    }

    private static boolean isValidPid(int pid, List<PCB> processes) {
        for (PCB p : processes) if (p.getPid() == pid) return true;
        return false;
    }

    private JLabel dimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_DIM);
        l.setFont(Theme.FONT_BODY);
        return l;
    }

    private JButton controlButton(String text, Color accent, java.awt.event.ActionListener listener) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = getModel().isRollover();
                boolean pressed = getModel().isPressed();
                Color bg = pressed ? accent.darker() : (hover ? Theme.BG_RAISED.brighter() : Theme.BG_RAISED);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(accent);
                g2.fill(new RoundRectangle2D.Float(0, 0, 3, getHeight(), 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 12));
        b.setForeground(Theme.TEXT);
        b.setBorder(new EmptyBorder(6, 14, 6, 14));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(listener);
        return b;
    }

    private JComponent wrapRounded(JComponent inner) {
        JPanel rc = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_PANEL);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rc.setOpaque(false);
        rc.add(inner, BorderLayout.CENTER);
        return rc;
    }

    // ------------------------------------------------------------------
    // Memory blocks visualization
    // ------------------------------------------------------------------
    private static class MemoryBlocksPanel extends JPanel {
        private static final int CELL_W = 110;
        private static final int CELL_H = 110;
        private static final int GAP    = 14;
        private static final int PAD    = 16;

        private final MemoryManager memory;

        MemoryBlocksPanel(MemoryManager memory) {
            this.memory = memory;
            setBackground(Theme.BG_DEEP);
        }

        @Override
        public Dimension getPreferredSize() {
            int n = memory.getFrameCount();
            int perRow = Math.max(1, Math.min(n, 8));
            int rows = (int) Math.ceil(n / (double) perRow);
            int width  = PAD * 2 + perRow * CELL_W + (perRow - 1) * GAP;
            int height = PAD * 2 + rows   * CELL_H + (rows   - 1) * GAP;
            return new Dimension(width, height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Frame[] frames = memory.getFrames();
            int n = frames.length;
            int perRow = Math.max(1, Math.min(n, Math.max(1, (getWidth() - 2 * PAD + GAP) / (CELL_W + GAP))));

            for (int i = 0; i < n; i++) {
                int row = i / perRow;
                int col = i % perRow;
                int x = PAD + col * (CELL_W + GAP);
                int y = PAD + row * (CELL_H + GAP);
                drawFrame(g2, frames[i], i, x, y);
            }

            g2.dispose();
        }

        private void drawFrame(Graphics2D g2, Frame f, int idx, int x, int y) {
            Color fill, stroke;
            if (f.isFree()) {
                fill = Theme.BG_PANEL;
                stroke = Theme.BORDER;
            } else {
                Color base = GanttChartPanel.colorFor(f.pid);
                fill = base;
                stroke = base.darker();
            }

            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(x, y, CELL_W, CELL_H, 14, 14));

            float strokeWidth = 1.4f;
            if (f.justReplaced) { stroke = Theme.DANGER; strokeWidth = 3f; }
            else if (f.justAccessed) { stroke = Theme.SUCCESS; strokeWidth = 2.5f; }
            g2.setStroke(new BasicStroke(strokeWidth));
            g2.setColor(stroke);
            g2.draw(new RoundRectangle2D.Float(x + 0.7f, y + 0.7f, CELL_W - 1.4f, CELL_H - 1.4f, 14, 14));

            g2.setFont(Theme.FONT_MONO);
            g2.setColor(f.isFree() ? Theme.TEXT_DIM : Theme.BG_DEEP);
            g2.drawString("F" + idx, x + 8, y + 16);

            if (f.isFree()) {
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                g2.setColor(Theme.TEXT_DIM);
                String s = "Free";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(s, x + (CELL_W - fm.stringWidth(s)) / 2, y + CELL_H / 2 + 6);
            } else {
                g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
                g2.setColor(Theme.BG_DEEP);
                String pidStr = "P" + f.pid;
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(pidStr, x + (CELL_W - fm.stringWidth(pidStr)) / 2, y + CELL_H / 2 + 2);

                g2.setFont(Theme.FONT_MONO);
                g2.setColor(Theme.BG_DEEP);
                String pageStr = "page " + f.page;
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(pageStr, x + (CELL_W - fm2.stringWidth(pageStr)) / 2, y + CELL_H / 2 + 24);

                g2.setColor(Theme.BG_DEEP);
                g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
                String lru = "lastUsed=" + f.lastUsed;
                g2.drawString(lru, x + 8, y + CELL_H - 8);
            }
        }
    }

    // ------------------------------------------------------------------
    // Page table model
    // ------------------------------------------------------------------
    private class PageTableModel extends AbstractTableModel {
        private final String[] cols = {"PID", "Page", "Frame", "Last Used", "Loaded At"};
        private final List<Object[]> rows = new ArrayList<>();

        @Override public int getRowCount() {
            rebuild();
            return rows.size();
        }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) { return rows.get(r)[c]; }

        private void rebuild() {
            rows.clear();
            for (Map.Entry<Integer, Map<Integer, Integer>> e : memory.getPageTables().entrySet()) {
                int pid = e.getKey();
                for (Map.Entry<Integer, Integer> pe : e.getValue().entrySet()) {
                    int page = pe.getKey();
                    int frameIdx = pe.getValue();
                    Frame f = memory.getFrames()[frameIdx];
                    rows.add(new Object[]{
                            pid,
                            page,
                            "F" + frameIdx,
                            f.lastUsed,
                            f.loadedAt
                    });
                }
            }
        }
    }

    private static class PageTableRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            if (!sel) {
                c.setBackground(row % 2 == 0 ? Theme.BG_PANEL : Theme.BG_RAISED);
                c.setForeground(Theme.TEXT);
            }
            if (col == 0 && v instanceof Integer pid) {
                c.setForeground(GanttChartPanel.colorFor(pid));
            }
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return c;
        }
    }
}
