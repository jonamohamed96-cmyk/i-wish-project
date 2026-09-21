package iwish;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FriendRequestsFrame extends JFrame {

    private static final String URL = "jdbc:mysql://localhost:3306/iwish";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";

    private final JTextField receiverIdField = new JTextField("1", 10);
    private final JPanel requestsPanel = new JPanel();

    public FriendRequestsFrame() {
        setTitle("i-Wish - Friend Requests");
        setSize(650, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Your User ID:"));
        topPanel.add(receiverIdField);

        JButton loadButton = new JButton("Load Requests");
        topPanel.add(loadButton);

        requestsPanel.setLayout(new BoxLayout(requestsPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(requestsPanel);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        loadButton.addActionListener(e -> loadRequests());

        loadRequests();
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private List<Object[]> getPendingRequests(int receiverId) throws SQLException {
        List<Object[]> requests = new ArrayList<>();

        String sql = "SELECT fr.request_id, u.name " +
                "FROM friend_requests fr " +
                "JOIN users u ON fr.sender_id = u.user_id " +
                "WHERE fr.receiver_id = ? AND fr.status = 'Pending'";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, receiverId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    requests.add(new Object[]{
                            rs.getInt("request_id"),
                            rs.getString("name")
                    });
                }
            }
        }

        return requests;
    }

    private void acceptRequest(int requestId) throws SQLException {
        String selectSql =
                "SELECT sender_id, receiver_id FROM friend_requests WHERE request_id = ?";

        String updateSql =
                "UPDATE friend_requests SET status = 'Accepted' WHERE request_id = ?";

        String insertFriendSql =
                "INSERT IGNORE INTO friends (user_id, friend_id) VALUES (?, ?)";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                int senderId;
                int receiverId;

                try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                    ps.setInt(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Friend request not found.");
                        }
                        senderId = rs.getInt("sender_id");
                        receiverId = rs.getInt("receiver_id");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setInt(1, requestId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(insertFriendSql)) {
                    ps.setInt(1, senderId);
                    ps.setInt(2, receiverId);
                    ps.executeUpdate();

                    ps.setInt(1, receiverId);
                    ps.setInt(2, senderId);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private void declineRequest(int requestId) throws SQLException {
        String sql =
                "UPDATE friend_requests SET status = 'Declined' WHERE request_id = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, requestId);
            ps.executeUpdate();
        }
    }

    private void loadRequests() {
        requestsPanel.removeAll();

        try {
            int receiverId = Integer.parseInt(receiverIdField.getText().trim());
            List<Object[]> requests = getPendingRequests(receiverId);

            if (requests.isEmpty()) {
                requestsPanel.add(new JLabel("No pending friend requests."));
            } else {
                for (Object[] request : requests) {
                    addRequestRow(request);
                }
            }

            requestsPanel.revalidate();
            requestsPanel.repaint();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid User ID.",
                    "Input Error",
                    JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Database error: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addRequestRow(Object[] request) {
        int requestId = (int) request[0];
        String senderName = (String) request[1];

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel label = new JLabel(senderName + " sent you a friend request.");

        JButton acceptButton = new JButton("Accept");
        JButton declineButton = new JButton("Decline");

        acceptButton.addActionListener(e -> {
            try {
                acceptRequest(requestId);

                JOptionPane.showMessageDialog(this,
                        "Friend request accepted successfully!");

                loadRequests();

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Could not accept request: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        declineButton.addActionListener(e -> {
            try {
                declineRequest(requestId);

                JOptionPane.showMessageDialog(this,
                        "Friend request declined.");

                loadRequests();

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Could not decline request: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        row.add(label);
        row.add(acceptButton);
        row.add(declineButton);

        requestsPanel.add(row);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FriendRequestsFrame frame = new FriendRequestsFrame();
            frame.setVisible(true);
        });
    }
}
