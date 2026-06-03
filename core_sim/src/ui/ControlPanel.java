package ui;

import core.MemoryManager;
import core.ProcessManager;
import core.Scheduler;
import model.PCB;
import model.ProcessState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ControlPanel extends JFrame {

    private final ProcessManager manager = new ProcessManager();
    private final Scheduler scheduler = new Scheduler(manager);
    private final MemoryManager memoryManager = new MemoryManager(4);
    private final ProcessTableModel tableModel = new ProcessTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextArea logArea = new JTextArea(8, 40);
    private final JLabel runningLabel = new JLabel("(none)");
    private final JLabel readyLabel = new JLabel("[]");
    private final JLabel waitingLabel = new JLabel("[]");

    // Dedicated PCB cells for the currently running process (Module 1 center).
    private final JLabel rPidLbl       = new JLabel("—");
    private final JLabel rStateLbl     = new JLabel("—");
    private final JLabel rPriorityLbl  = new JLabel("—");
    private final JLabel rBurstLbl     = new JLabel("—");
    private final JLabel rRemainingLbl = new JLabel("—");
    private final JLabel rMemoryLbl    = new JLabel("—");
    private final JLabel rParentLbl    = new JLabel("—");
    private final JLabel rChildrenLbl  = new JLabel("—");
    private final JLabel rIoLbl        = new JLabel("—");

    // Same PCB fields rendered vertically inside the sidebar so the running
    // process is always visible while the user clicks operation buttons.
    private final JLabel vPidLbl       = new JLabel("—");
    private final JLabel vStateLbl     = new JLabel("—");
    private final JLabel vPriorityLbl  = new JLabel("—");
    private final JLabel vBurstLbl     = new JLabel("—");
    private final JLabel vRemainingLbl = new JLabel("—");
    private final JLabel vMemoryLbl    = new JLabel("—");
    private final JLabel vParentLbl    = new JLabel("—");
    private final JLabel vChildrenLbl  = new JLabel("—");
    private final JLabel vIoLbl        = new JLabel("—");
    private final JTabbedPane tabs = new JTabbedPane();
    private SchedulingPanel schedulingPanel;
    private MemoryPanel memoryPanel;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ControlPanel() {
        super("Core Sim  •  OS Kernel Simulator");
        manager.setLogger(this::appendLog);
        scheduler.setLogger(this::appendLog);
        // Memory events go to the central log AND the Module 3 tab's own log
        // so the user sees them no matter which tab is active.
        memoryManager.setLogger(msg -> {
            appendLog(msg);
            if (memoryPanel != null) memoryPanel.appendLog(msg);
        });
        // Sync: every scheduler tick references one page of the executing
        // process, so Module 3 visualisation animates live while Module 2
        // runs FCFS / Round Robin.
        scheduler.setOnTick(this::onSchedulerTickedPcb);
        Theme.apply();
        buildUi();
        setJMenuBar(buildMenuBar());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
    }

    /**
     * Hook fired by {@link Scheduler#tick()} with the PCB that executed during
     * the just-completed time unit. Cycles through the process's pages so
     * Module 3 shows live hits / faults / evictions as Module 2 schedules.
     */
    private void onSchedulerTickedPcb(PCB pcb) {
        if (pcb == null) return;
        int pages = MemoryManager.pagesNeededFor(pcb.getMemory());
        if (pages <= 0) return;
        int pageIdx = (scheduler.getClock() - 1 + pages) % pages;
        memoryManager.accessPage(pcb.getPid(), pageIdx);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(Theme.BG_DEEP);
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);

        tabs.setFont(Theme.FONT_HEAD);
        tabs.setBackground(Theme.BG_PANEL);
        tabs.setForeground(Theme.TEXT);
        tabs.addTab("Module 1  •  PCB & Process Management", buildModule1Tab());

        schedulingPanel = new SchedulingPanel(manager, scheduler, this::refreshSiblingsOfScheduler);
        tabs.addTab("Module 2  •  CPU Scheduling", schedulingPanel);

        memoryPanel = new MemoryPanel(manager, memoryManager);
        tabs.addTab("Module 3  •  Memory Management (Paging + LRU)", memoryPanel);

        root.add(tabs, BorderLayout.CENTER);

        refresh();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setBackground(Theme.BG_PANEL);
        mb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));

        JMenu modules = new JMenu("Modules");
        modules.setForeground(Theme.TEXT);
        modules.setFont(Theme.FONT_HEAD);
        JMenuItem m1 = new JMenuItem("Module 1: PCB & Process Management");
        JMenuItem m2 = new JMenuItem("Module 2: CPU Scheduling");
        JMenuItem m3 = new JMenuItem("Module 3: Memory Management (Paging + LRU)");
        m1.addActionListener(e -> tabs.setSelectedIndex(0));
        m2.addActionListener(e -> tabs.setSelectedIndex(1));
        m3.addActionListener(e -> tabs.setSelectedIndex(2));
        modules.add(m1);
        modules.add(m2);
        modules.add(m3);
        mb.add(modules);

        JMenu topic = new JMenu("Assigned Topic");
        topic.setForeground(Theme.TEXT);
        topic.setFont(Theme.FONT_HEAD);
        JMenuItem placeholder = new JMenuItem("(to be assigned — Module 5)");
        placeholder.setEnabled(false);
        topic.add(placeholder);
        mb.add(topic);

        JMenu help = new JMenu("Help");
        help.setForeground(Theme.TEXT);
        help.setFont(Theme.FONT_HEAD);
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Core Sim — OS Kernel Simulator\nUmar Ahad Usmani (60199), Noreen Tariq (61291)",
                "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        mb.add(help);
        return mb;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.BG_PANEL);
        header.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel title = new JLabel("◈  CORE SIM");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Theme.ACCENT);

        JLabel sub = new JLabel("OS Kernel Simulator");
        sub.setFont(Theme.FONT_BODY);
        sub.setForeground(Theme.TEXT_DIM);
        sub.setBorder(new EmptyBorder(0, 14, 0, 0));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(title);
        left.add(sub);

        JLabel team = new JLabel("Umar Ahad Usmani (60199)  •  Noreen Tariq (61291)");
        team.setFont(Theme.FONT_BODY);
        team.setForeground(Theme.TEXT_DIM);

        header.add(left, BorderLayout.WEST);
        header.add(team, BorderLayout.EAST);
        return wrapRounded(header);
    }

    private JComponent buildModule1Tab() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(10, 0, 0, 0));
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildCenter(), BorderLayout.CENTER);
        return root;
    }

    private JComponent buildSidebar() {
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.setOpaque(false);

        buttons.add(actionButton("＋  Create Process",         Theme.SUCCESS, e -> onCreate()));
        buttons.add(actionButton("▶  Dispatch (Ready→Run)",    Theme.ACCENT,  e -> { manager.dispatch(); refresh(); }));
        buttons.add(actionButton("✕  Destroy Process",         Theme.DANGER,  e -> onPidAction("Destroy", pid -> {
            memoryManager.freeProcess(pid);
            return manager.destroyProcess(pid);
        })));
        buttons.add(actionButton("❚❚  Suspend Process",        Theme.WARN,    e -> onPidAction("Suspend", manager::suspendProcess)));
        buttons.add(actionButton("↻  Resume Process",          Theme.SUCCESS, e -> onPidAction("Resume",  manager::resumeProcess)));
        buttons.add(actionButton("■  Block Process",           Theme.WARN,    e -> onPidAction("Block",   manager::blockProcess)));
        buttons.add(actionButton("☼  Wakeup Process",          Theme.INFO,    e -> onPidAction("Wakeup",  manager::wakeupProcess)));
        buttons.add(actionButton("⇅  Change Priority",         Theme.ACCENT_2,e -> onChangePriority()));
        buttons.add(Box.createVerticalStrut(10));
        buttons.add(actionButton("🗑  Clear Log",              Theme.TEXT_DIM,e -> logArea.setText("")));
        buttons.add(Box.createVerticalGlue());

        JPanel opsInner = new JPanel(new BorderLayout());
        opsInner.setOpaque(false);
        opsInner.setBorder(Theme.titled("Operations"));
        opsInner.setBackground(Theme.BG_PANEL);
        opsInner.add(buttons, BorderLayout.NORTH);
        JComponent opsCard = wrapRounded(opsInner);

        JComponent pcbCard = buildVerticalRunningPcb();

        // Stack: Operations on top (natural height), Running PCB fills the
        // rest of the sidebar height below it.
        JPanel sidebar = new JPanel(new BorderLayout(0, 12));
        sidebar.setOpaque(false);
        sidebar.add(opsCard, BorderLayout.NORTH);
        sidebar.add(pcbCard, BorderLayout.CENTER);
        sidebar.setPreferredSize(new Dimension(290, 0));
        return sidebar;
    }

    /**
     * Vertical mirror of {@link #buildRunningPcb()} for the sidebar.
     * Stacks one row per PCB field so the running process is always visible
     * while the user is working with the operation buttons just above it.
     */
    private JComponent buildVerticalRunningPcb() {
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(new EmptyBorder(4, 4, 8, 4));

        rows.add(pcbVerticalRow("PID",       vPidLbl,       Theme.SUCCESS));
        rows.add(pcbVerticalRow("State",     vStateLbl,     Theme.SUCCESS));
        rows.add(pcbVerticalRow("Priority",  vPriorityLbl,  Theme.ACCENT));
        rows.add(pcbVerticalRow("Burst",     vBurstLbl,     Theme.ACCENT));
        rows.add(pcbVerticalRow("Remaining", vRemainingLbl, Theme.ACCENT));
        rows.add(pcbVerticalRow("Memory MB", vMemoryLbl,    Theme.INFO));
        rows.add(pcbVerticalRow("Parent",    vParentLbl,    Theme.TEXT_DIM));
        rows.add(pcbVerticalRow("Children",  vChildrenLbl,  Theme.TEXT_DIM));
        rows.add(pcbVerticalRow("I/O",       vIoLbl,        Theme.WARN));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Running Process PCB"));
        wrap.add(rows, BorderLayout.NORTH);
        return wrapRounded(wrap);
    }

    private JComponent pcbVerticalRow(String name, JLabel value, Color accent) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(5, 8, 5, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        nameLbl.setForeground(accent);

        value.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        value.setForeground(Theme.TEXT);
        value.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(nameLbl, BorderLayout.WEST);
        row.add(value,   BorderLayout.CENTER);
        return row;
    }

    private JComponent buildCenter() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        topStack.add(boxedFullWidth(buildQueues()));
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(boxedFullWidth(buildRunningPcb()));

        center.add(topStack, BorderLayout.NORTH);
        center.add(buildTable(), BorderLayout.CENTER);
        center.add(buildLog(), BorderLayout.SOUTH);
        return center;
    }

    /** Wrap a fixed-height component so BoxLayout doesn't stretch it vertically. */
    private JComponent boxedFullWidth(JComponent inner) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(inner, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, inner.getPreferredSize().height));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JComponent buildQueues() {
        JPanel queues = new JPanel(new GridLayout(1, 3, 12, 0));
        queues.setOpaque(false);
        queues.add(statCard("CPU - Running", runningLabel, Theme.SUCCESS));
        queues.add(statCard("Ready Queue",   readyLabel,   Theme.ACCENT));
        queues.add(statCard("Waiting Queue", waitingLabel, Theme.WARN));
        return queues;
    }

    private JComponent statCard(String title, JLabel valueLabel, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Theme.BG_PANEL);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setForeground(accent);

        valueLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        valueLabel.setForeground(Theme.TEXT);

        card.add(t, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return wrapRounded(card);
    }

    /**
     * Dedicated horizontal PCB table for the currently dispatched (RUNNING)
     * process. Each PCB field (PID, State, Priority, Burst, Remaining, Memory,
     * Parent, Children, I/O) renders as its own cell with a header + value,
     * so the user can read every field of the running PCB at a glance without
     * scanning the full process table.
     */
    private JComponent buildRunningPcb() {
        JPanel row = new JPanel(new GridLayout(1, 9, 8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(6, 10, 10, 10));

        row.add(pcbFieldCell("PID",       rPidLbl,       Theme.SUCCESS));
        row.add(pcbFieldCell("State",     rStateLbl,     Theme.SUCCESS));
        row.add(pcbFieldCell("Priority",  rPriorityLbl,  Theme.ACCENT));
        row.add(pcbFieldCell("Burst",     rBurstLbl,     Theme.ACCENT));
        row.add(pcbFieldCell("Remaining", rRemainingLbl, Theme.ACCENT));
        row.add(pcbFieldCell("Memory MB", rMemoryLbl,    Theme.INFO));
        row.add(pcbFieldCell("Parent",    rParentLbl,    Theme.TEXT_DIM));
        row.add(pcbFieldCell("Children",  rChildrenLbl,  Theme.TEXT_DIM));
        row.add(pcbFieldCell("I/O",       rIoLbl,        Theme.WARN));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Running Process PCB"));
        wrap.add(row, BorderLayout.CENTER);
        return wrapRounded(wrap);
    }

    private JComponent pcbFieldCell(String title, JLabel valueLabel, Color accent) {
        JPanel cell = new JPanel(new BorderLayout(0, 4));
        cell.setBackground(Theme.BG_RAISED);
        cell.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.setForeground(accent);

        valueLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        valueLabel.setForeground(Theme.TEXT);
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

        cell.add(t, BorderLayout.NORTH);
        cell.add(valueLabel, BorderLayout.CENTER);

        JPanel rounded = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_RAISED);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(accent);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), 3, 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rounded.setOpaque(false);
        cell.setOpaque(false);
        rounded.add(cell, BorderLayout.CENTER);
        return rounded;
    }

    private JComponent buildTable() {
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(Theme.BG_PANEL);
        table.setForeground(Theme.TEXT);
        table.setSelectionBackground(Theme.BG_RAISED);
        table.setSelectionForeground(Theme.ACCENT);
        table.setFont(Theme.FONT_BODY);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(Theme.BG_RAISED);
        table.getTableHeader().setForeground(Theme.ACCENT);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        table.setDefaultRenderer(Object.class, new ZebraRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG_PANEL);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Process Table (PCB List)"));
        wrap.add(scroll, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(0, 220));
        return wrapRounded(wrap);
    }

    private JComponent buildLog() {
        logArea.setEditable(false);
        logArea.setFont(Theme.FONT_MONO);
        logArea.setBackground(Theme.BG_DEEP);
        logArea.setForeground(Theme.TEXT);
        logArea.setCaretColor(Theme.ACCENT);
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        logScroll.getViewport().setBackground(Theme.BG_DEEP);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG_PANEL);
        wrap.setBorder(Theme.titled("Event Log"));
        wrap.add(logScroll, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(0, 200));
        return wrapRounded(wrap);
    }

    private JComponent wrapRounded(JComponent inner) {
        RoundedContainer rc = new RoundedContainer();
        rc.setLayout(new BorderLayout());
        rc.add(inner, BorderLayout.CENTER);
        return rc;
    }

    private JButton actionButton(String text, Color accent, java.awt.event.ActionListener listener) {
        AccentPillButton b = new AccentPillButton(text, accent);
        b.addActionListener(listener);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return b;
    }

    private void onCreate() {
        JTextField priorityField = new JTextField("1");
        JTextField memoryField = new JTextField("128");
        JTextField burstField = new JTextField("5");
        JLabel hint = new JLabel(
                "Memory is auto-paged: " + MemoryManager.PAGE_SIZE_MB
              + " MB / page -> Module 3 will allocate the matching number of pages.");
        hint.setForeground(Theme.TEXT_DIM);
        hint.setFont(Theme.FONT_BODY);
        Object[] msg = {
                "Priority (1-10):", priorityField,
                "Memory (MB):", memoryField,
                "Burst Time (units, for scheduling):", burstField,
                hint
        };
        int res = JOptionPane.showConfirmDialog(this, msg, "Create Process", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;
        try {
            int p = Integer.parseInt(priorityField.getText().trim());
            int m = Integer.parseInt(memoryField.getText().trim());
            int b = Integer.parseInt(burstField.getText().trim());
            if (b < 0) throw new NumberFormatException("burst must be >= 0");
            PCB created = manager.createProcess(p, m, b);
            // Sync: auto-allocate pages for the new PCB so Module 3 shows it
            // alongside Module 1 / Module 2 without the user having to type
            // anything in the Memory tab.
            memoryManager.allocatePages(created.getPid(), MemoryManager.pagesNeededFor(m));
            refresh();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Priority, Memory, and Burst must be non-negative integers.",
                    "Invalid input", JOptionPane.ERROR_MESSAGE);
        }
    }

    private interface PidOp { boolean apply(int pid); }

    private void onPidAction(String label, PidOp op) {
        Integer pid = pickPid(label + " - select PID");
        if (pid == null) return;
        op.apply(pid);
        refresh();
    }

    private void onChangePriority() {
        Integer pid = pickPid("Change Priority - select PID");
        if (pid == null) return;
        String input = JOptionPane.showInputDialog(this, "New priority for P" + pid + ":", "Change Priority", JOptionPane.QUESTION_MESSAGE);
        if (input == null) return;
        try {
            manager.changePriority(pid, Integer.parseInt(input.trim()));
            refresh();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Priority must be an integer.", "Invalid input", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Integer pickPid(String title) {
        if (manager.getProcesses().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No processes available.", title, JOptionPane.WARNING_MESSAGE);
            return null;
        }
        Integer[] options = manager.getProcesses().stream().map(PCB::getPid).toArray(Integer[]::new);
        int selectedRow = table.getSelectedRow();
        Integer initial = selectedRow >= 0 ? (Integer) tableModel.getValueAt(selectedRow, 0) : options[0];
        Object choice = JOptionPane.showInputDialog(this, "Select PID:", title,
                JOptionPane.QUESTION_MESSAGE, null, options, initial);
        return choice == null ? null : (Integer) choice;
    }

    private void refresh() {
        refreshModule1View();
        if (schedulingPanel != null) schedulingPanel.refresh();
        if (memoryPanel != null) memoryPanel.refresh();
    }

    /**
     * Sibling refresh used as the SchedulingPanel state-change callback.
     * Each scheduler tick should propagate to Module 1 (running PCB / process
     * table) AND Module 3 (memory frames + page tables), so all three tabs
     * stay synchronised while Module 2 is animating.
     */
    private void refreshSiblingsOfScheduler() {
        refreshModule1View();
        if (memoryPanel != null) memoryPanel.refresh();
    }

    private void refreshModule1View() {
        tableModel.fireTableDataChanged();
        PCB run = manager.getRunningProcess();
        runningLabel.setText(run == null ? "(none)" : "P" + run.getPid());
        readyLabel.setText(manager.getReadyQueue().toString());
        waitingLabel.setText(manager.getWaitingQueue().toString());
        refreshRunningPcb(run);
    }

    private void refreshRunningPcb(PCB run) {
        if (run == null) {
            setPcbCells("—", "—", "—", "—", "—", "—", "—", "[]", "—", Theme.TEXT);
            return;
        }
        setPcbCells(
                "P" + run.getPid(),
                run.getState().toString(),
                Integer.toString(run.getPriority()),
                Integer.toString(run.getBurstTime()),
                Integer.toString(run.getRemainingTime()),
                Integer.toString(run.getMemory()),
                Integer.toString(run.getParentId()),
                run.getChildren().toString(),
                run.getIoStatus(),
                Theme.SUCCESS);
    }

    /** Push the same nine values into both the horizontal and vertical PCB cards. */
    private void setPcbCells(String pid, String state, String priority,
                             String burst, String remaining, String memory,
                             String parent, String children, String io,
                             Color stateColor) {
        rPidLbl.setText(pid);          vPidLbl.setText(pid);
        rStateLbl.setText(state);      vStateLbl.setText(state);
        rStateLbl.setForeground(stateColor);
        vStateLbl.setForeground(stateColor);
        rPriorityLbl.setText(priority);   vPriorityLbl.setText(priority);
        rBurstLbl.setText(burst);         vBurstLbl.setText(burst);
        rRemainingLbl.setText(remaining); vRemainingLbl.setText(remaining);
        rMemoryLbl.setText(memory);       vMemoryLbl.setText(memory);
        rParentLbl.setText(parent);       vParentLbl.setText(parent);
        rChildrenLbl.setText(children);   vChildrenLbl.setText(children);
        rIoLbl.setText(io);               vIoLbl.setText(io);
    }

    private void appendLog(String msg) {
        logArea.append("[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private class ProcessTableModel extends AbstractTableModel {
        private final String[] cols = {"PID", "State", "Priority", "Burst", "Remaining", "Memory (MB)", "Parent", "Children", "I/O"};

        public int getRowCount() { return manager.getProcesses().size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int row, int col) {
            PCB p = manager.getProcesses().get(row);
            return switch (col) {
                case 0 -> p.getPid();
                case 1 -> p.getState();
                case 2 -> p.getPriority();
                case 3 -> p.getBurstTime();
                case 4 -> p.getRemainingTime();
                case 5 -> p.getMemory();
                case 6 -> p.getParentId();
                case 7 -> p.getChildren().toString();
                case 8 -> p.getIoStatus();
                default -> "";
            };
        }
    }

    private static class ZebraRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            if (!sel) {
                c.setBackground(row % 2 == 0 ? Theme.BG_PANEL : Theme.BG_RAISED);
                c.setForeground(Theme.TEXT);
            }
            if (col == 1 && v instanceof ProcessState ps) {
                c.setForeground(stateColor(ps));
            }
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return c;
        }

        private Color stateColor(ProcessState s) {
            return switch (s) {
                case RUNNING   -> Theme.SUCCESS;
                case READY     -> Theme.ACCENT;
                case WAITING   -> Theme.WARN;
                case TERMINATED-> Theme.DANGER;
                default        -> Theme.INFO;
            };
        }
    }

    private static class RoundedContainer extends JPanel {
        RoundedContainer() {
            setOpaque(false);
            setBackground(Theme.BG_PANEL);
        }
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
    }

    private static class AccentPillButton extends JButton {
        private final Color accent;
        AccentPillButton(String text, Color accent) {
            super(text);
            this.accent = accent;
            setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
            setForeground(Theme.TEXT);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(new EmptyBorder(8, 14, 8, 14));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();
            Color bg = pressed ? accent.darker() : (hover ? blend(accent, Theme.BG_RAISED, 0.55f) : Theme.BG_RAISED);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            g2.setColor(accent);
            g2.fill(new RoundRectangle2D.Float(0, 0, 4, getHeight(), 12, 12));
            g2.dispose();
            super.paintComponent(g);
        }
        private static Color blend(Color a, Color b, float t) {
            int r = (int)(a.getRed()*t   + b.getRed()*(1-t));
            int g = (int)(a.getGreen()*t + b.getGreen()*(1-t));
            int bl= (int)(a.getBlue()*t  + b.getBlue()*(1-t));
            return new Color(Math.min(255,r), Math.min(255,g), Math.min(255,bl));
        }
    }
}
