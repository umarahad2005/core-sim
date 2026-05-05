package ui;

import core.ProcessManager;
import model.PCB;
import model.ProcessState;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ControlPanel extends JFrame {

    private final ProcessManager manager = new ProcessManager();
    private final ProcessTableModel tableModel = new ProcessTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextArea logArea = new JTextArea(8, 40);
    private final JLabel runningLabel = new JLabel("Running: (none)");
    private final JLabel readyLabel = new JLabel("Ready Queue: []");
    private final JLabel waitingLabel = new JLabel("Waiting Queue: []");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ControlPanel() {
        super("OS Kernel Simulator - Module 1: PCB & Process Management");
        manager.setLogger(this::appendLog);
        buildUi();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        buttons.setBorder(BorderFactory.createTitledBorder("Operations"));
        buttons.add(makeButton("Create Process", e -> onCreate()));
        buttons.add(makeButton("Dispatch (Ready -> Running)", e -> { manager.dispatch(); refresh(); }));
        buttons.add(makeButton("Destroy Process", e -> onPidAction("Destroy", manager::destroyProcess)));
        buttons.add(makeButton("Suspend Process", e -> onPidAction("Suspend", manager::suspendProcess)));
        buttons.add(makeButton("Resume Process",  e -> onPidAction("Resume",  manager::resumeProcess)));
        buttons.add(makeButton("Block Process",   e -> onPidAction("Block",   manager::blockProcess)));
        buttons.add(makeButton("Wakeup Process",  e -> onPidAction("Wakeup",  manager::wakeupProcess)));
        buttons.add(makeButton("Change Priority", e -> onChangePriority()));
        buttons.add(Box.createVerticalStrut(8));
        buttons.add(makeButton("Clear Log", e -> logArea.setText("")));

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.add(buttons, BorderLayout.NORTH);

        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Process Table (PCB List)"));

        JPanel queues = new JPanel(new GridLayout(3, 1));
        queues.setBorder(BorderFactory.createTitledBorder("CPU & Queues"));
        queues.add(runningLabel);
        queues.add(readyLabel);
        queues.add(waitingLabel);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Event Log"));

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(tableScroll, BorderLayout.CENTER);
        center.add(queues, BorderLayout.NORTH);
        center.add(logScroll, BorderLayout.SOUTH);

        add(sidebar, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);

        refresh();
    }

    private JButton makeButton(String text, java.awt.event.ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        return b;
    }

    private void onCreate() {
        JTextField priorityField = new JTextField("1");
        JTextField memoryField = new JTextField("128");
        Object[] msg = {"Priority (1-10):", priorityField, "Memory (MB):", memoryField};
        int res = JOptionPane.showConfirmDialog(this, msg, "Create Process", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;
        try {
            int p = Integer.parseInt(priorityField.getText().trim());
            int m = Integer.parseInt(memoryField.getText().trim());
            manager.createProcess(p, m);
            refresh();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Priority and Memory must be integers.", "Invalid input", JOptionPane.ERROR_MESSAGE);
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
        tableModel.fireTableDataChanged();
        PCB run = manager.getRunningProcess();
        runningLabel.setText("Running: " + (run == null ? "(none)" : "P" + run.getPid()));
        readyLabel.setText("Ready Queue: " + manager.getReadyQueue());
        waitingLabel.setText("Waiting Queue: " + manager.getWaitingQueue());
    }

    private void appendLog(String msg) {
        logArea.append("[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private class ProcessTableModel extends AbstractTableModel {
        private final String[] cols = {"PID", "State", "Priority", "Memory (MB)", "Parent", "Children", "I/O"};

        public int getRowCount() { return manager.getProcesses().size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int row, int col) {
            PCB p = manager.getProcesses().get(row);
            return switch (col) {
                case 0 -> p.getPid();
                case 1 -> p.getState();
                case 2 -> p.getPriority();
                case 3 -> p.getMemory();
                case 4 -> p.getParentId();
                case 5 -> p.getChildren().toString();
                case 6 -> p.getIoStatus();
                default -> "";
            };
        }
    }
}
