import ui.ControlPanel;
import ui.IntroScreen;
import ui.Theme;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.apply();
            IntroScreen intro = new IntroScreen(
                    () -> new ControlPanel().setVisible(true)
            );
            intro.setVisible(true);
        });
    }
}
