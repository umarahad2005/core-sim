package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class IntroScreen extends JFrame {

    public IntroScreen(Runnable onContinue) {
        super("Core Sim - Welcome");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(820, 560);
        setLocationRelativeTo(null);
        setContentPane(buildContent(onContinue));
    }

    private JComponent buildContent(Runnable onContinue) {
        GradientPanel root = new GradientPanel();
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(40, 50, 30, 50));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(onContinue), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("◈  CORE SIM");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 44));
        logo.setForeground(Theme.ACCENT);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel tagline = new JLabel("An OS Kernel Simulator  •  PCB · CPU Scheduling · Memory Management");
        tagline.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        tagline.setForeground(Theme.TEXT_DIM);
        tagline.setAlignmentX(Component.CENTER_ALIGNMENT);
        tagline.setBorder(new EmptyBorder(8, 0, 0, 0));

        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.BORDER);
        sep.setBackground(Theme.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setBorder(new EmptyBorder(20, 0, 0, 0));

        header.add(logo);
        header.add(tagline);
        header.add(sep);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(28, 0, 20, 0));

        JLabel teamTitle = new JLabel("PROJECT TEAM");
        teamTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        teamTitle.setForeground(Theme.ACCENT_2);

        JPanel cards = new JPanel(new GridLayout(1, 2, 24, 0));
        cards.setOpaque(false);
        cards.add(memberCard("Umar Ahad Usmani", "60199", Theme.ACCENT));
        cards.add(memberCard("Noreen Tariq",     "61291", Theme.ACCENT_2));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 4, 12, 0);
        body.add(teamTitle, gbc);

        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        body.add(cards, gbc);

        return body;
    }

    private JComponent memberCard(String name, String sap, Color accent) {
        RoundedPanel card = new RoundedPanel(18, Theme.BG_RAISED, accent);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 22, 22, 22));

        JLabel avatar = new JLabel(initials(name));
        avatar.setFont(new Font("Segoe UI", Font.BOLD, 22));
        avatar.setForeground(accent);
        avatar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel n = new JLabel(name);
        n.setFont(new Font("Segoe UI", Font.BOLD, 18));
        n.setForeground(Theme.TEXT);
        n.setAlignmentX(Component.LEFT_ALIGNMENT);
        n.setBorder(new EmptyBorder(10, 0, 4, 0));

        JLabel s = new JLabel("SAP ID  •  " + sap);
        s.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        s.setForeground(Theme.TEXT_DIM);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(avatar);
        card.add(n);
        card.add(s);
        return card;
    }

    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() >= 2) break;
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
        }
        return sb.toString();
    }

    private JComponent buildFooter(Runnable onContinue) {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);

        JLabel course = new JLabel("Operating Systems Lab  •  BSCS-5A");
        course.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        course.setForeground(Theme.TEXT_DIM);

        AccentButton launch = new AccentButton("Launch Simulator  →");
        launch.addActionListener(e -> {
            dispose();
            onContinue.run();
        });

        footer.add(course, BorderLayout.WEST);
        footer.add(launch, BorderLayout.EAST);
        return footer;
    }

    private static class GradientPanel extends JPanel {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            GradientPaint gp = new GradientPaint(0, 0, Theme.BG_DEEP, w, h, Theme.BG_PANEL);
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(), 28));
            g2.fillOval(-120, -120, 360, 360);
            g2.setColor(new Color(Theme.ACCENT_2.getRed(), Theme.ACCENT_2.getGreen(), Theme.ACCENT_2.getBlue(), 22));
            g2.fillOval(w - 240, h - 240, 360, 360);
            g2.dispose();
        }
    }

    private static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fill;
        private final Color stroke;

        RoundedPanel(int radius, Color fill, Color stroke) {
            this.radius = radius;
            this.fill = fill;
            this.stroke = stroke;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(new RoundRectangle2D.Float(0.7f, 0.7f, getWidth() - 1.4f, getHeight() - 1.4f, radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class AccentButton extends JButton {
        AccentButton(String text) {
            super(text);
            setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
            setForeground(Theme.BG_DEEP);
            setBackground(Theme.ACCENT);
            setFocusPainted(false);
            setBorder(new EmptyBorder(10, 22, 10, 22));
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = getModel().isRollover() ? Theme.ACCENT_2 : Theme.ACCENT;
            g2.setColor(base);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
