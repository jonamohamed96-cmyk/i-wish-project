
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

public class IWishMemberGUI {

    public static final Color BG_COLOR = new Color(244, 247, 246);
    public static final Color SIDEBAR_COLOR = new Color(44, 62, 80);
    public static final Color PRIMARY_BTN = new Color(52, 152, 219);
    public static final Color SUCCESS_BTN = new Color(46, 204, 113);
    public static final Color TEXT_COLOR = new Color(52, 73, 94);
    public static final Color CARD_BG = Color.WHITE;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            UIManager.put("Button.focus", new Color(0,0,0,0));
        } catch (Exception e) { e.printStackTrace(); }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    static class MainFrame extends JFrame {
        private JPanel contentArea;
        private CardLayout cardLayout;

        public MainFrame() {
            setTitle("I-Wish | Make Your Friends Happy");
            setSize(1000, 650);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLayout(new BorderLayout());
            getContentPane().setBackground(BG_COLOR);

            add(new Sidebar(this), BorderLayout.WEST);

            cardLayout = new CardLayout();
            contentArea = new JPanel(cardLayout);
            contentArea.setBackground(BG_COLOR);
            contentArea.setBorder(new EmptyBorder(20, 20, 20, 20));

            contentArea.add(new ContributionPanel(), "Contribution");
            contentArea.add(new NotificationsPanel(), "Notifications");

            add(contentArea, BorderLayout.CENTER);
            cardLayout.show(contentArea, "Contribution");
        }

        public void showScreen(String screenName) {
            cardLayout.show(contentArea, screenName);
        }
    }

    static class Sidebar extends JPanel {
        public MainFrame parentFrame;

        public Sidebar(MainFrame parent) {
            this.parentFrame = parent;
            setPreferredSize(new Dimension(220, 0));
            setBackground(SIDEBAR_COLOR);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(20, 0, 20, 0));

            JLabel logo = new JLabel("I-Wish", SwingConstants.CENTER);
            logo.setFont(new Font("Segoe UI", Font.BOLD, 28));
            logo.setForeground(Color.WHITE);
            logo.setBorder(new EmptyBorder(0, 0, 30, 0));
            add(logo, BorderLayout.NORTH);

            JPanel menuPanel = new JPanel();
            menuPanel.setLayout(new GridLayout(0, 1, 0, 15));
            menuPanel.setOpaque(false);
            menuPanel.setBorder(new EmptyBorder(20, 20, 0, 20));

            JButton btnContribute = createMenuButton("ð  Contribute");
            JButton btnNotifications = createMenuButton("ð  Notifications");

            btnContribute.addActionListener(e -> parent.showScreen("Contribution"));
            btnNotifications.addActionListener(e -> parent.showScreen("Notifications"));

            menuPanel.add(btnContribute);
            menuPanel.add(btnNotifications);
            add(menuPanel, BorderLayout.CENTER);
        }

        private JButton createMenuButton(String text) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            btn.setForeground(Color.WHITE);
            btn.setBackground(SIDEBAR_COLOR);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setHorizontalAlignment(SwingConstants.LEFT);

            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(52, 73, 94)); }
                public void mouseExited(MouseEvent e) { btn.setBackground(SIDEBAR_COLOR); }
            });
            return btn;
        }
    }

    static class ContributionPanel extends JPanel {
        private JTextField amountField;
        private JProgressBar progressBar;

        public ContributionPanel() {
            setLayout(new BorderLayout(20, 20));
            setOpaque(false);

            JLabel title = new JLabel("Contribute to Friend's Wish");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(TEXT_COLOR);
            add(title, BorderLayout.NORTH);

            JPanel itemCard = new JPanel(new BorderLayout(15, 15));
            itemCard.setBackground(CARD_BG);
            itemCard.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(25, 25, 25, 25)
            ));

            JPanel itemDetails = new JPanel(new GridLayout(0, 1, 5, 5));
            itemDetails.setOpaque(false);

            JLabel itemName = new JLabel("Item: Sony WH-1000XM4 Headphones");
            itemName.setFont(new Font("Segoe UI", Font.BOLD, 18));
            itemName.setForeground(TEXT_COLOR);

            JLabel friendName = new JLabel("For: Ahmed Mohamed");
            friendName.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            friendName.setForeground(new Color(127, 140, 141));

            JLabel priceLabel = new JLabel("Total Price: 3000 EGP");
            priceLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            priceLabel.setForeground(PRIMARY_BTN);

            itemDetails.add(itemName);
            itemDetails.add(friendName);
            itemDetails.add(priceLabel);

            JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
            progressPanel.setOpaque(false);
            progressBar = new JProgressBar(0, 100);
            progressBar.setValue(40);
            progressBar.setStringPainted(true);
            progressBar.setString("1200 EGP collected (40%)");
            progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
            progressBar.setForeground(SUCCESS_BTN);
            progressPanel.add(progressBar, BorderLayout.CENTER);

            JLabel progressTitle = new JLabel("Current Progress:");
            progressTitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            progressPanel.add(progressTitle, BorderLayout.NORTH);

            itemDetails.add(new JLabel(" "));
            itemDetails.add(progressPanel);

            itemCard.add(itemDetails, BorderLayout.CENTER);

            JLabel imgPlaceholder = new JLabel("ð§", SwingConstants.CENTER);
            imgPlaceholder.setFont(new Font("Arial", Font.PLAIN, 80));
            imgPlaceholder.setPreferredSize(new Dimension(150, 150));
            itemCard.add(imgPlaceholder, BorderLayout.EAST);

            add(itemCard, BorderLayout.CENTER);

            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
            actionPanel.setOpaque(false);

            JLabel amountLabel = new JLabel("Your Contribution (EGP):");
            amountLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            amountLabel.setForeground(TEXT_COLOR);

            amountField = new JTextField(10);
            amountField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            amountField.setPreferredSize(new Dimension(150, 40));

            RoundedButton btnContribute = new RoundedButton("Contribute Now", SUCCESS_BTN, Color.WHITE);
            btnContribute.setFont(new Font("Segoe UI", Font.BOLD, 16));
            btnContribute.setPreferredSize(new Dimension(180, 45));

            btnContribute.addActionListener(e -> handleContribution());

            actionPanel.add(amountLabel);
            actionPanel.add(amountField);
            actionPanel.add(btnContribute);

            add(actionPanel, BorderLayout.SOUTH);
        }

        private void handleContribution() {
            String amountStr = amountField.getText().trim();
            if (amountStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter an amount!", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JOptionPane.showMessageDialog(this, "Thank you! Your contribution of " + amountStr + " EGP was sent successfully.\nYour friend will be notified.", "Success", JOptionPane.INFORMATION_MESSAGE);
            amountField.setText("");
        }
    }

    static class NotificationsPanel extends JPanel {
        public NotificationsPanel() {
            setLayout(new BorderLayout(20, 20));
            setOpaque(false);

            JLabel title = new JLabel("Notifications");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(TEXT_COLOR);
            add(title, BorderLayout.NORTH);

            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setOpaque(false);

            listPanel.add(createNotificationCard("Receiver", "Ahmed contributed 500 EGP to your 'PlayStation 5' wish!", "2 mins ago", new Color(235, 245, 251)));
            listPanel.add(createNotificationCard("Buyer", "Yay! The 'Coffee Maker' you contributed to is now fully funded!", "1 hour ago", new Color(235, 251, 240)));
            listPanel.add(createNotificationCard("System", "Sara Ahmed accepted your friend request.", "Yesterday", new Color(245, 245, 245)));

            JScrollPane scrollPane = new JScrollPane(listPanel);
            scrollPane.setBorder(null);
            scrollPane.setBackground(BG_COLOR);
            scrollPane.getViewport().setOpaque(false);
            add(scrollPane, BorderLayout.CENTER);
        }

        private JPanel createNotificationCard(String type, String message, String time, Color bgColor) {
            String icon = "ð"; // Ø£ÙÙÙÙØ© Ø§ÙØªØ±Ø§Ø¶ÙØ©
            if (type.equals("Receiver"));
            else if (type.equals("Buyer")) ;
            else if (type.equals("System")) ;
            JPanel card = new JPanel(new BorderLayout(15, 0));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            card.setBackground(bgColor);
            card.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220, 220, 220), 1, true),
                    new EmptyBorder(15, 20, 15, 20)
            ));

            JLabel iconLabel = new JLabel(icon, SwingConstants.CENTER);
            iconLabel.setFont(new Font("Arial", Font.PLAIN, 30));
            iconLabel.setPreferredSize(new Dimension(50, 50));
            card.add(iconLabel, BorderLayout.WEST);

            JPanel textPanel = new JPanel(new GridLayout(0, 1, 2, 2));
            textPanel.setOpaque(false);

            JLabel typeLabel = new JLabel(type + " Notification");
            typeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            typeLabel.setForeground(PRIMARY_BTN);

            JLabel msgLabel = new JLabel(message);
            msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            msgLabel.setForeground(TEXT_COLOR);

            textPanel.add(typeLabel);
            textPanel.add(msgLabel);
            card.add(textPanel, BorderLayout.CENTER);

            JLabel timeLabel = new JLabel(time, SwingConstants.RIGHT);
            timeLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            timeLabel.setForeground(new Color(150, 150, 150));
            card.add(timeLabel, BorderLayout.EAST);

            return card;
        }
    }

    static class RoundedButton extends JButton {
        private Color backgroundColor;
        private Color textColor;
        private int borderRadius = 20;

        public RoundedButton(String text, Color bg, Color fg) {
            super(text);
            this.backgroundColor = bg;
            this.textColor = fg;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setForeground(textColor);
            setFont(new Font("Segoe UI", Font.BOLD, 14));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { backgroundColor = bg.brighter(); repaint(); }
                public void mouseExited(MouseEvent e) { backgroundColor = bg; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(backgroundColor);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), borderRadius, borderRadius));
            super.paintComponent(g);
            g2.dispose();
        }
    }
}