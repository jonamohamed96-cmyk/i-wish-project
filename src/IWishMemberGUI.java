package iwish;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.Date;
import java.util.List;
import java.util.Map;

public class IWishMemberGUI {
    public static final Color BG_COLOR = new Color(244, 247, 246);
    public static final Color SIDEBAR_COLOR = new Color(44, 62, 80);
    public static final Color PRIMARY_BTN = new Color(52, 152, 219);
    public static final Color SUCCESS_BTN = new Color(46, 204, 113);
    public static final Color TEXT_COLOR = new Color(52, 73, 94);
    public static final Color CARD_BG = Color.WHITE;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Button.focus", new Color(0,0,0,0));
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }

    static class LoginFrame extends JFrame {
        private JTextField usernameField;
        private JPasswordField passwordField;
        private JButton loginButton;
        private JButton registerButton;

        public LoginFrame() {
            setTitle("I-Wish - Login");
            setSize(450, 400);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setResizable(false);
            getContentPane().setBackground(BG_COLOR);
            JPanel mainPanel = new JPanel(new GridBagLayout());
            mainPanel.setBackground(BG_COLOR);
            mainPanel.setBorder(new EmptyBorder(40, 40, 40, 40));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(10, 10, 10, 10);
            JLabel titleLabel = new JLabel("I-Wish", SwingConstants.CENTER);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
            titleLabel.setForeground(SIDEBAR_COLOR);
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
            mainPanel.add(titleLabel, gbc);
            JLabel subtitleLabel = new JLabel("Make Your Friends Happy");
            subtitleLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            subtitleLabel.setForeground(new Color(127, 140, 141));
            gbc.gridy = 1;
            mainPanel.add(subtitleLabel, gbc);
            gbc.gridy = 2; gbc.gridwidth = 1;
            mainPanel.add(new JLabel("Username:"), gbc);
            usernameField = new JTextField(20);
            usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            gbc.gridx = 1;
            mainPanel.add(usernameField, gbc);
            gbc.gridx = 0; gbc.gridy = 3;
            mainPanel.add(new JLabel("Password:"), gbc);
            passwordField = new JPasswordField(20);
            passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            gbc.gridx = 1;
            mainPanel.add(passwordField, gbc);
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
            loginButton = createRoundedButton("Login", PRIMARY_BTN, 150, 40);
            mainPanel.add(loginButton, gbc);
            gbc.gridy = 5;
            registerButton = createRoundedButton("Register", SUCCESS_BTN, 150, 40);
            mainPanel.add(registerButton, gbc);
            loginButton.addActionListener(e -> handleLogin());
            registerButton.addActionListener(e -> handleRegister());
            add(mainPanel);
        }

        private JButton createRoundedButton(String text, Color bg, int width, int height) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btn.setForeground(Color.WHITE);
            btn.setBackground(bg);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(width, height));
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(bg.brighter());
                }
                public void mouseExited(MouseEvent e) {
                    btn.setBackground(bg);
                }
            });
            return btn;
        }

        private void handleLogin() {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter username and password",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Map<String, Object> user = DBConnection.login(username + "@iwish.com", password);
            if (user != null) {
                int userId = ((Number) user.get("user_id")).intValue();
                String userName = (String) user.get("username");
                MainFrame mainFrame = new MainFrame(userId, userName);
                mainFrame.setVisible(true);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Invalid username or password",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void handleRegister() {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter username and password",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (DBConnection.emailExists(username + "@iwish.com")) {
                JOptionPane.showMessageDialog(this,
                        "Username already exists",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int result = DBConnection.addUser(username,
                    username + "@iwish.com",
                    password,
                    Date.valueOf("2000-01-01"));
            if (result > 0) {
                JOptionPane.showMessageDialog(this,
                        "Registration successful! Please login.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Registration failed",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    static class MainFrame extends JFrame {
        private JPanel contentArea;
        private CardLayout cardLayout;
        private int userId;
        private String username;
        private JLabel welcomeLabel;

        public MainFrame(int userId, String username) {
            this.userId = userId;
            this.username = username;
            setTitle("I-Wish | Make Your Friends Happy - Welcome " + username);
            setSize(1100, 700);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLayout(new BorderLayout());
            getContentPane().setBackground(BG_COLOR);
            add(new Sidebar(this), BorderLayout.WEST);
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            topPanel.setBackground(BG_COLOR);
            welcomeLabel = new JLabel("Welcome, " + username + "!");
            welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
            welcomeLabel.setForeground(TEXT_COLOR);
            topPanel.add(welcomeLabel);
            add(topPanel, BorderLayout.NORTH);
            cardLayout = new CardLayout();
            contentArea = new JPanel(cardLayout);
            contentArea.setBackground(BG_COLOR);
            contentArea.setBorder(new EmptyBorder(20, 20, 20, 20));
            contentArea.add(new DashboardPanel(), "Dashboard");
            contentArea.add(new MyWishListPanel(), "MyWishList");
            contentArea.add(new FriendsPanel(), "Friends");
            contentArea.add(new FriendWishListPanel(), "FriendWishList");
            contentArea.add(new ContributionPanel(), "Contribution");
            contentArea.add(new NotificationsPanel(), "Notifications");
            add(contentArea, BorderLayout.CENTER);
            cardLayout.show(contentArea, "Dashboard");
        }

        public void showScreen(String screenName) {
            cardLayout.show(contentArea, screenName);
        }

        public int getUserId() { return userId; }
        public String getUsername() { return username; }
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
            menuPanel.setLayout(new GridLayout(0, 1, 0, 10));
            menuPanel.setOpaque(false);
            menuPanel.setBorder(new EmptyBorder(20, 20, 0, 20));
            menuPanel.add(createMenuButton("\u2302  Dashboard", "Dashboard"));
            menuPanel.add(createMenuButton("\u2764  My Wish List", "MyWishList"));
            menuPanel.add(createMenuButton("\u2764  Friends", "Friends"));
            menuPanel.add(createMenuButton("\uD83C\uDF81  Friend's List", "FriendWishList"));
            menuPanel.add(createMenuButton("\uD83D\uDCB0  Contribute", "Contribution"));
            menuPanel.add(createMenuButton("\uD83D\uDD14  Notifications", "Notifications"));
            add(menuPanel, BorderLayout.CENTER);
        }

        private JButton createMenuButton(String text, String screenName) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            btn.setForeground(Color.WHITE);
            btn.setBackground(SIDEBAR_COLOR);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setBorder(new EmptyBorder(10, 15, 10, 15));
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(new Color(52, 73, 94));
                }
                public void mouseExited(MouseEvent e) {
                    btn.setBackground(SIDEBAR_COLOR);
                }
            });
            btn.addActionListener(e -> parentFrame.showScreen(screenName));
            return btn;
        }
    }

    static class DashboardPanel extends JPanel {
        public DashboardPanel() {
            setLayout(new BorderLayout(20, 20));
            setOpaque(false);
            JPanel welcomeCard = new JPanel(new BorderLayout(20, 20));
            welcomeCard.setBackground(CARD_BG);
            welcomeCard.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(30, 30, 30, 30)
            ));
            JLabel welcomeText = new JLabel("Welcome to I-Wish!");
            welcomeText.setFont(new Font("Segoe UI", Font.BOLD, 28));
            welcomeText.setForeground(TEXT_COLOR);
            welcomeCard.add(welcomeText, BorderLayout.NORTH);
            JLabel description = new JLabel("<html>Manage your wish lists, connect with friends, <br>" +
                    "and make each other happy by contributing to wishes!</html>");
            description.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            description.setForeground(new Color(127, 140, 141));
            welcomeCard.add(description, BorderLayout.CENTER);
            add(welcomeCard, BorderLayout.CENTER);
        }
    }

    static class MyWishListPanel extends JPanel {
        private DefaultTableModel tableModel;
        private JTable table;

        public MyWishListPanel() {
            setLayout(new BorderLayout(15, 15));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));
            JLabel title = new JLabel("My Wish List");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(TEXT_COLOR);
            add(title, BorderLayout.NORTH);
            tableModel = new DefaultTableModel(new Object[]{"Item", "Price", "Description", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            table = new JTable(tableModel);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            table.setRowHeight(35);
            table.setSelectionBackground(new Color(235, 245, 251));
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(new LineBorder(new Color(230, 230, 230), 1, true));
            add(scrollPane, BorderLayout.CENTER);
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            buttonPanel.setOpaque(false);
            JButton btnAdd = createButton("Add Item", PRIMARY_BTN);
            JButton btnRefresh = createButton("Refresh", SUCCESS_BTN);
            btnAdd.addActionListener(e -> addItem());
            btnRefresh.addActionListener(e -> refreshList());
            buttonPanel.add(btnAdd);
            buttonPanel.add(btnRefresh);
            add(buttonPanel, BorderLayout.SOUTH);
            refreshList();
        }

        private JButton createButton(String text, Color bg) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btn.setForeground(Color.WHITE);
            btn.setBackground(bg);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setBorder(new EmptyBorder(8, 20, 8, 20));
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(bg.brighter());
                }
                public void mouseExited(MouseEvent e) {
                    btn.setBackground(bg);
                }
            });
            return btn;
        }

        private void refreshList() {
            tableModel.setRowCount(0);
            tableModel.addRow(new Object[]{"Sony Headphones", "3000 EGP", "Noise cancelling", "In Progress"});
            tableModel.addRow(new Object[]{"PlayStation 5", "25000 EGP", "Gaming console", "Fully Funded \u2713"});
        }

        private void addItem() {
            String itemName = JOptionPane.showInputDialog(this, "Enter item name:");
            if (itemName != null && !itemName.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Item added successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                refreshList();
            }
        }
    }

    static class FriendsPanel extends JPanel {
        public FriendsPanel() {
            setLayout(new BorderLayout(15, 15));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));
            JLabel title = new JLabel("My Friends");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(TEXT_COLOR);
            add(title, BorderLayout.NORTH);
            DefaultListModel<String> listModel = new DefaultListModel<>();
            listModel.addElement("Ahmed Mohamed");
            listModel.addElement("Sara Ahmed");
            listModel.addElement("Mohamed Ali");
            JList<String> friendsList = new JList<>(listModel);
            friendsList.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            friendsList.setSelectionBackground(new Color(235, 245, 251));
            friendsList.setFixedCellHeight(40);
            JScrollPane scrollPane = new JScrollPane(friendsList);
            scrollPane.setBorder(new LineBorder(new Color(230, 230, 230), 1, true));
            add(scrollPane, BorderLayout.CENTER);
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            buttonPanel.setOpaque(false);
            JButton btnAdd = createButton("Add Friend", PRIMARY_BTN);
            JButton btnRemove = createButton("Remove Friend", new Color(231, 76, 60));
            buttonPanel.add(btnAdd);
            buttonPanel.add(btnRemove);
            add(buttonPanel, BorderLayout.SOUTH);
        }

        private JButton createButton(String text, Color bg) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btn.setForeground(Color.WHITE);
            btn.setBackground(bg);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setBorder(new EmptyBorder(8, 20, 8, 20));
            return btn;
        }
    }

    static class FriendWishListPanel extends JPanel {
        public FriendWishListPanel() {
            setLayout(new BorderLayout(15, 15));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));
            JLabel title = new JLabel("Friend's Wish List");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(TEXT_COLOR);
            add(title, BorderLayout.NORTH);
            JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            infoPanel.setOpaque(false);
            infoPanel.add(new JLabel("Select Friend:"));
            JComboBox<String> friendCombo = new JComboBox<>(new String[]{"Ahmed Mohamed", "Sara Ahmed"});
            infoPanel.add(friendCombo);
            JButton btnView = createButton("View List", PRIMARY_BTN);
            infoPanel.add(btnView);
            add(infoPanel, BorderLayout.NORTH);
            DefaultTableModel tableModel = new DefaultTableModel(
                    new Object[]{"Item", "Price", "Needed", "Action"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 3;
                }
            };
            JTable table = new JTable(tableModel);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            table.setRowHeight(35);
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(new LineBorder(new Color(230, 230, 230), 1, true));
            add(scrollPane, BorderLayout.CENTER);
        }

        private JButton createButton(String text, Color bg) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btn.setForeground(Color.WHITE);
            btn.setBackground(bg);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setBorder(new EmptyBorder(8, 20, 8, 20));
            return btn;
        }
    }

    static class ContributionPanel extends JPanel {
        private JTextField amountField;
        private JProgressBar progressBar;

        public ContributionPanel() {
            setLayout(new BorderLayout(20, 20));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));
            JLabel title = new JLabel("Contribute to Friend's Wish");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(TEXT_COLOR);
            add(title, BorderLayout.NORTH);
            JPanel itemCard = new JPanel(new BorderLayout(20, 20));
            itemCard.setBackground(CARD_BG);
            itemCard.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(30, 30, 30, 30)
            ));
            JPanel itemDetails = new JPanel(new GridLayout(0, 1, 10, 10));
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
            progressBar.setPreferredSize(new Dimension(400, 25));
            progressPanel.add(progressBar, BorderLayout.CENTER);
            JLabel progressTitle = new JLabel("Current Progress:");
            progressTitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            progressPanel.add(progressTitle, BorderLayout.NORTH);
            itemDetails.add(new JLabel(" "));
            itemDetails.add(progressPanel);
            itemCard.add(itemDetails, BorderLayout.CENTER);
            JLabel imgPlaceholder = new JLabel("\uD83C\uDFA7", SwingConstants.CENTER);
            imgPlaceholder.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 80));
            imgPlaceholder.setPreferredSize(new Dimension(150, 150));
            itemCard.add(imgPlaceholder, BorderLayout.EAST);
            add(itemCard, BorderLayout.CENTER);
            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
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
                JOptionPane.showMessageDialog(this,
                        "Please enter an amount!",
                        "Warning",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    JOptionPane.showMessageDialog(this,
                            "Amount must be greater than 0!",
                            "Warning",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                JOptionPane.showMessageDialog(this,
                        "Thank you! Your contribution of " + amountStr + " EGP was sent successfully.\n" +
                                "Your friend will be notified.",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                amountField.setText("");
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid number!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    static class NotificationsPanel extends JPanel {
        public NotificationsPanel() {
            setLayout(new BorderLayout(20, 20));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));
            JLabel title = new JLabel("Notifications");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(TEXT_COLOR);
            add(title, BorderLayout.NORTH);
            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setOpaque(false);
            listPanel.add(createNotificationCard(
                    "Receiver",
                    "Ahmed contributed 500 EGP to your 'PlayStation 5' wish!",
                    "2 mins ago",
                    new Color(235, 245, 251)));
            listPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            listPanel.add(createNotificationCard(
                    "Buyer",
                    "Yay! The 'Coffee Maker' you contributed to is now fully funded!",
                    "1 hour ago",
                    new Color(235, 251, 240)));
            listPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            listPanel.add(createNotificationCard(
                    "System",
                    "Sara Ahmed accepted your friend request.",
                    "Yesterday",
                    new Color(245, 245, 245)));
            JScrollPane scrollPane = new JScrollPane(listPanel);
            scrollPane.setBorder(null);
            scrollPane.setBackground(BG_COLOR);
            scrollPane.getViewport().setOpaque(false);
            add(scrollPane, BorderLayout.CENTER);
        }

        private JPanel createNotificationCard(String type, String message, String time, Color bgColor) {
            JPanel card = new JPanel(new BorderLayout(15, 0));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
            card.setBackground(bgColor);
            card.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220, 220, 220), 1, true),
                    new EmptyBorder(15, 20, 15, 20)
            ));
            String icon = "\uD83D\uDD14";
            JLabel iconLabel = new JLabel(icon, SwingConstants.CENTER);
            iconLabel.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 32));
            iconLabel.setPreferredSize(new Dimension(50, 50));
            card.add(iconLabel, BorderLayout.WEST);
            JPanel textPanel = new JPanel(new GridLayout(0, 1, 3, 3));
            textPanel.setOpaque(false);
            JLabel typeLabel = new JLabel(type + " Notification");
            typeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            typeLabel.setForeground(PRIMARY_BTN);
            JLabel msgLabel = new JLabel("<html>" + message + "</html>");
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
                public void mouseEntered(MouseEvent e) {
                    backgroundColor = bg.brighter();
                    repaint();
                }
                public void mouseExited(MouseEvent e) {
                    backgroundColor = bg;
                    repaint();
                }
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