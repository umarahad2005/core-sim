import ui.ControlPanel;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ControlPanel().setVisible(true));
    }
}
