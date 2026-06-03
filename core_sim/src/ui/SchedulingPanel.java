package ui;

import core.ProcessManager;
import core.Scheduler;
import core.Scheduler.Algorithm;
import model.PCB;
import model.ProcessState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class SchedulingPanel extends JPanel {

    private final ProcessManager manager;
    private final Scheduler scheduler;
    private final Runnable onStateChange;

    private final JComboBox<Algorithm> algoCombo = new JComboBox<>(Algorithm.values());
    private final JSpinner quantumSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 99, 1));
    private final JSpinner speedSpinner = new JSpinner(new SpinnerNumberModel(400, 50, 2000, 50));
    private final GanttChartPanel ganttPanel = new GanttChartPanel();
    private final MetricsTableModel metricsModel = new MetricsTableModel();
    private final JTable metricsTable = new JTable(metricsModel);
    private final JLabel runningLabel = new JLabel("(idle)");
    private final JLabel readyLabel = new JLabel("[]");
    private final JLabel clockLabel = new JLabel("0");
    private final JLabel avgWtLabel = new JLabel("—");
    private final JLabel avgTatLabel = new JLabel("—");

    private final JButton runBtn;
    private final JButton pauseBtn;
    private final JButton stepBtn;
    private final JButton resetBtn;
    private final Timer timer;

    public SchedulingPanel(ProcessManager manager, Scheduler scheduler, Runnable onStateChange) {
        this.manager = manager;
        this.scheduler = scheduler;
        this.onStateChange = onStateChange != null ? onStateChange : () -> {};

        runBtn   = controlButton("▶  Run",   Theme.SUCCESS, e -> onRun());
        pauseBtn = controlButton("❚❚  Pause", Theme.WARN,   e -> onPause());
        stepBtn  = controlButton("⏵  Step",  Theme.ACCENT,  e -> onStep());
        resetBtn = controlButton("↺  Reset", Theme.DANGER,  e -> onReset());

        timer = new Timer(currentSpeed(), e -> onTick());
        timer.setRepeats(true);

        setOpaque(false);
        setLayout(new BorderLayout(12, 12));
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        pauseBtn.setEnabled(false);
        refresh();
    }

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bar.setOpaque(false);

        bar.add(label("Algorithm:"));
        algoCombo.setSelectedItem(scheduler.getAlgorithm());
        algoCombo.addActionListener(e -> {
            scheduler.setAlgorithm((Algorithm) algoCombo.getSelectedItem());
            quantumSpinner.setEnabled(scheduler.getAlgorithm() == Algorithm.ROUND_ROBIN);
        });
        bar.add(algoCombo);

        bar.add(Box.createHorizontalStrut(6));
        bar.add(label("Time Quantum:"));
        quantumSpinner.setValue(scheduler.getTimeQuantum());
        quantumSpinner.addChangeListener(e -> scheduler.setTimeQuantum((int) quantumSpinner.getValue()));
        quantumSpinner.setEnabled(scheduler.getAlgorithm() == Algorithm.ROUND_ROBIN);
        bar.add(quantumSpinner);

        bar.add(Box.createHorizontalStrut(6));
        bar.add(label("Tick delay (ms):"));
        speedSpinner.addChangeListener(e -> {
            if (timer.isRunning()) timer.setDelay(currentSpeed());
            else timer.setInitialDelay(currentSpeed());
        });
        bar.add(speedSpinner);

        bar.add(Box.createHorizontalStrut(14));
        bar.add(runBtn);
        bar.add(pauseBtn);
        bar.add(stepBtn);
        bar.add(resetBtn);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Scheduler Controls"));
        wrap.add(bar, BorderLayout.CENTER);
        return wrapRounded(wrap);
    }

    private JComponent buildCenter() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);

        center.add(buildStatusRow(), BorderLayout.NORTH);
        center.add(buildGantt(), BorderLayout.CENTER);
        center.add(buildMetrics(), BorderLayout.SOUTH);
        return center;
    }

    private JComponent buildStatusRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.add(statCard("CPU - Running", runningLabel, Theme.SUCCESS));
        row.add(statCard("Ready Queue",   readyLabel,   Theme.ACCENT));
        row.add(statCard("Clock (t)",     clockLabel,   Theme.INFO));
        row.add(statCard("Avg WT / TAT",  combinedAvgLabel(), Theme.ACCENT_2));
        return row;
    }

    private JComponent combinedAvgLabel() {
        JPanel p = new JPanel(new GridLayout(2, 1));
        p.setOpaque(false);
        JLabel wt = new JLabel();
        JLabel tat = new JLabel();
        wt.setForeground(Theme.TEXT);
        tat.setForeground(Theme.TEXT);
        wt.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        tat.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        avgWtLabel.setFont(wt.getFont()); avgWtLabel.setForeground(Theme.TEXT);
        avgTatLabel.setFont(tat.getFont()); avgTatLabel.setForeground(Theme.TEXT);
        JPanel l1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        l1.setOpaque(false);
        JLabel k1 = new JLabel("WT:");  k1.setForeground(Theme.TEXT_DIM);
        JLabel k2 = new JLabel("TAT:"); k2.setForeground(Theme.TEXT_DIM);
        l1.add(k1); l1.add(avgWtLabel);
        JPanel l2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        l2.setOpaque(false);
        l2.add(k2); l2.add(avgTatLabel);
        p.add(l1); p.add(l2);
        return p;
    }

    private JComponent statCard(String title, JComponent value, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Theme.BG_PANEL);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setForeground(accent);

        if (value instanceof JLabel jl) {
            jl.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
            jl.setForeground(Theme.TEXT);
        }
        card.add(t, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return wrapRounded(card);
    }

    private JComponent buildGantt() {
        JScrollPane scroll = new JScrollPane(ganttPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG_DEEP);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Gantt Chart"));
        wrap.add(scroll, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(0, 150));
        return wrapRounded(wrap);
    }

    private JComponent buildMetrics() {
        metricsTable.setRowHeight(24);
        metricsTable.setShowGrid(false);
        metricsTable.setIntercellSpacing(new Dimension(0, 0));
        metricsTable.setBackground(Theme.BG_PANEL);
        metricsTable.setForeground(Theme.TEXT);
        metricsTable.setSelectionBackground(Theme.BG_RAISED);
        metricsTable.setSelectionForeground(Theme.ACCENT);
        metricsTable.setFont(Theme.FONT_MONO);
        metricsTable.getTableHeader().setBackground(Theme.BG_RAISED);
        metricsTable.getTableHeader().setForeground(Theme.ACCENT);
        metricsTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        metricsTable.setDefaultRenderer(Object.class, new MetricsRenderer());

        JScrollPane scroll = new JScrollPane(metricsTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG_PANEL);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Per-Process Metrics"));
        wrap.add(scroll, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(0, 180));
        return wrapRounded(wrap);
    }

    private JLabel label(String text) {
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
        b.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
        b.setForeground(Theme.TEXT);
        b.setBorder(new EmptyBorder(7, 16, 7, 16));
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

    private int currentSpeed() {
        return (int) speedSpinner.getValue();
    }

    private boolean validateRunReadiness() {
        if (manager.getProcesses().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No processes to schedule. Create some processes first.",
                    "Scheduler", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        boolean anyBurst = manager.getProcesses().stream().anyMatch(p -> p.getBurstTime() > 0);
        if (!anyBurst) {
            JOptionPane.showMessageDialog(this,
                    "All processes have burst time 0. Set a burst time when creating processes.",
                    "Scheduler", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void onRun() {
        if (!validateRunReadiness()) return;
        if (scheduler.isComplete()) {
            JOptionPane.showMessageDialog(this,
                    "Scheduling already complete. Press Reset to run again.",
                    "Scheduler", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        timer.setDelay(currentSpeed());
        timer.start();
        runBtn.setEnabled(false);
        pauseBtn.setEnabled(true);
        stepBtn.setEnabled(false);
    }

    private void onPause() {
        timer.stop();
        runBtn.setEnabled(true);
        pauseBtn.setEnabled(false);
        stepBtn.setEnabled(true);
    }

    private void onStep() {
        if (!validateRunReadiness()) return;
        boolean advanced = scheduler.tick();
        refresh();
        if (!advanced) {
            JOptionPane.showMessageDialog(this,
                    "All processes completed. Press Reset to run again.",
                    "Scheduler", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onReset() {
        timer.stop();
        scheduler.reset();
        runBtn.setEnabled(true);
        pauseBtn.setEnabled(false);
        stepBtn.setEnabled(true);
        refresh();
    }

    private void onTick() {
        if (!scheduler.tick()) {
            timer.stop();
            runBtn.setEnabled(true);
            pauseBtn.setEnabled(false);
            stepBtn.setEnabled(true);
        }
        refresh();
    }

    /** Refresh from current ProcessManager + Scheduler state. */
    public void refresh() {
        PCB run = manager.getRunningProcess();
        runningLabel.setText(run == null ? "(idle)" : "P" + run.getPid() + "  rem=" + run.getRemainingTime());
        readyLabel.setText(formatReady());
        clockLabel.setText(Integer.toString(scheduler.getClock()));
        avgWtLabel.setText(String.format("%.2f", scheduler.getAverageWaitingTime()));
        avgTatLabel.setText(String.format("%.2f", scheduler.getAverageTurnaroundTime()));
        ganttPanel.setSegments(scheduler.getGantt());
        metricsModel.fireTableDataChanged();
        onStateChange.run();
    }

    private String formatReady() {
        if (manager.getReadyQueue().isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (PCB p : manager.getReadyQueue()) {
            if (!first) sb.append(" → ");
            sb.append("P").append(p.getPid()).append("(").append(p.getRemainingTime()).append(")");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    /** Sync UI when scheduler state was changed externally (e.g. from Module 1). */
    public void syncAlgorithmControls() {
        algoCombo.setSelectedItem(scheduler.getAlgorithm());
        quantumSpinner.setValue(scheduler.getTimeQuantum());
    }

    private class MetricsTableModel extends AbstractTableModel {
        private final String[] cols = {"PID", "Burst", "Remaining", "Start", "Completion", "Waiting", "Turnaround", "State"};

        public int getRowCount() { return manager.getProcesses().size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int row, int col) {
            PCB p = manager.getProcesses().get(row);
            return switch (col) {
                case 0 -> p.getPid();
                case 1 -> p.getBurstTime();
                case 2 -> p.getRemainingTime();
                case 3 -> p.getStartTime() < 0 ? "—" : p.getStartTime();
                case 4 -> p.getCompletionTime() < 0 ? "—" : p.getCompletionTime();
                case 5 -> p.getState() == ProcessState.TERMINATED ? p.getWaitingTime() : "—";
                case 6 -> p.getState() == ProcessState.TERMINATED ? p.getTurnaroundTime() : "—";
                case 7 -> p.getState();
                default -> "";
            };
        }
    }

    private static class MetricsRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            if (!sel) {
                c.setBackground(row % 2 == 0 ? Theme.BG_PANEL : Theme.BG_RAISED);
                c.setForeground(Theme.TEXT);
            }
            if (col == 7 && v instanceof ProcessState ps) {
                c.setForeground(switch (ps) {
                    case RUNNING    -> Theme.SUCCESS;
                    case READY      -> Theme.ACCENT;
                    case WAITING    -> Theme.WARN;
                    case TERMINATED -> Theme.DANGER;
                    default         -> Theme.INFO;
                });
            }
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return c;
        }
    }
}
