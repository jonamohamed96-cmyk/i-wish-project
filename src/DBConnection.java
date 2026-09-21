package iwish;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DBConnection {

    private static final String URL = "jdbc:mysql://localhost:3306/iwish?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void initializeDatabase() {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS users ("
                        + "user_id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "username VARCHAR(50) NOT NULL, "
                        + "email VARCHAR(100) NOT NULL UNIQUE, "
                        + "password VARCHAR(64) NOT NULL, "
                        + "birth_date DATE, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",

                "CREATE TABLE IF NOT EXISTS friends ("
                        + "friendship_id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "requester_id INT NOT NULL, "
                        + "receiver_id INT NOT NULL, "
                        + "status VARCHAR(10) NOT NULL DEFAULT 'PENDING', "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "UNIQUE (requester_id, receiver_id), "
                        + "FOREIGN KEY (requester_id) REFERENCES users(user_id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE)",

                "CREATE TABLE IF NOT EXISTS wishlists ("
                        + "wishlist_id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "user_id INT NOT NULL, "
                        + "title VARCHAR(100) NOT NULL, "
                        + "event_date DATE, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)",

                "CREATE TABLE IF NOT EXISTS items ("
                        + "item_id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "wishlist_id INT NOT NULL, "
                        + "name VARCHAR(100) NOT NULL, "
                        + "price DECIMAL(10,2) NOT NULL, "
                        + "description VARCHAR(255), "
                        + "status VARCHAR(10) NOT NULL DEFAULT 'OPEN', "
                        + "FOREIGN KEY (wishlist_id) REFERENCES wishlists(wishlist_id) ON DELETE CASCADE)",

                "CREATE TABLE IF NOT EXISTS contributions ("
                        + "contribution_id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "item_id INT NOT NULL, "
                        + "contributor_id INT NOT NULL, "
                        + "amount DECIMAL(10,2) NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (contributor_id) REFERENCES users(user_id) ON DELETE CASCADE)",

                "CREATE TABLE IF NOT EXISTS notifications ("
                        + "notification_id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "user_id INT NOT NULL, "
                        + "message VARCHAR(255) NOT NULL, "
                        + "is_read BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)"
        };
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static List<Map<String, Object>> queryList(String sql, Object... params) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= cols; c++) {
                        row.put(md.getColumnLabel(c), rs.getObject(c));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    private static Map<String, Object> queryOne(String sql, Object... params) {
        List<Map<String, Object>> rows = queryList(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static int update(String sql, Object... params) {
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static int insert(String sql, Object... params) {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int addUser(String username, String email, String password, Date birthDate) {
        return insert("INSERT INTO users (username, email, password, birth_date) VALUES (?, ?, ?, ?)",
                username, email, hashPassword(password), birthDate);
    }

    public static Map<String, Object> login(String email, String password) {
        return queryOne("SELECT user_id, username, email, birth_date FROM users WHERE email = ? AND password = ?",
                email, hashPassword(password));
    }

    public static boolean emailExists(String email) {
        return queryOne("SELECT user_id FROM users WHERE email = ?", email) != null;
    }

    public static Map<String, Object> getUserById(int userId) {
        return queryOne("SELECT user_id, username, email, birth_date FROM users WHERE user_id = ?", userId);
    }

    public static Map<String, Object> getUserByEmail(String email) {
        return queryOne("SELECT user_id, username, email, birth_date FROM users WHERE email = ?", email);
    }

    public static boolean updateUser(int userId, String username, String email, Date birthDate) {
        return update("UPDATE users SET username = ?, email = ?, birth_date = ? WHERE user_id = ?",
                username, email, birthDate, userId) > 0;
    }

    public static boolean updatePassword(int userId, String newPassword) {
        return update("UPDATE users SET password = ? WHERE user_id = ?", hashPassword(newPassword), userId) > 0;
    }

    public static boolean deleteUser(int userId) {
        return update("DELETE FROM users WHERE user_id = ?", userId) > 0;
    }

    public static boolean relationshipExists(int userA, int userB) {
        return queryOne("SELECT friendship_id FROM friends WHERE (requester_id = ? AND receiver_id = ?) OR (requester_id = ? AND receiver_id = ?)",
                userA, userB, userB, userA) != null;
    }

    public static boolean sendFriendRequest(int requesterId, int receiverId) {
        if (requesterId == receiverId || relationshipExists(requesterId, receiverId)) {
            return false;
        }
        return insert("INSERT INTO friends (requester_id, receiver_id, status) VALUES (?, ?, 'PENDING')",
                requesterId, receiverId) > 0;
    }

    public static boolean acceptFriendRequest(int requesterId, int receiverId) {
        return update("UPDATE friends SET status = 'ACCEPTED' WHERE requester_id = ? AND receiver_id = ? AND status = 'PENDING'",
                requesterId, receiverId) > 0;
    }

    public static boolean declineFriendRequest(int requesterId, int receiverId) {
        return update("DELETE FROM friends WHERE requester_id = ? AND receiver_id = ? AND status = 'PENDING'",
                requesterId, receiverId) > 0;
    }

    public static boolean removeFriend(int userId, int friendId) {
        return update("DELETE FROM friends WHERE status = 'ACCEPTED' AND ((requester_id = ? AND receiver_id = ?) OR (requester_id = ? AND receiver_id = ?))",
                userId, friendId, friendId, userId) > 0;
    }

    public static List<Map<String, Object>> getFriends(int userId) {
        return queryList("SELECT u.user_id, u.username, u.email, u.birth_date FROM users u "
                + "JOIN friends f ON (f.requester_id = u.user_id AND f.receiver_id = ?) "
                + "OR (f.receiver_id = u.user_id AND f.requester_id = ?) "
                + "WHERE f.status = 'ACCEPTED'", userId, userId);
    }

    public static List<Map<String, Object>> getPendingRequests(int userId) {
        return queryList("SELECT u.user_id, u.username, u.email, f.created_at FROM friends f "
                + "JOIN users u ON u.user_id = f.requester_id "
                + "WHERE f.receiver_id = ? AND f.status = 'PENDING'", userId);
    }

    public static int createWishList(int userId, String title, Date eventDate) {
        return insert("INSERT INTO wishlists (user_id, title, event_date) VALUES (?, ?, ?)", userId, title, eventDate);
    }

    public static List<Map<String, Object>> getWishListsByUser(int userId) {
        return queryList("SELECT wishlist_id, user_id, title, event_date, created_at FROM wishlists WHERE user_id = ?", userId);
    }

    public static Map<String, Object> getWishListById(int wishListId) {
        return queryOne("SELECT wishlist_id, user_id, title, event_date, created_at FROM wishlists WHERE wishlist_id = ?", wishListId);
    }

    public static boolean updateWishList(int wishListId, String title, Date eventDate) {
        return update("UPDATE wishlists SET title = ?, event_date = ? WHERE wishlist_id = ?", title, eventDate, wishListId) > 0;
    }

    public static boolean deleteWishList(int wishListId) {
        return update("DELETE FROM wishlists WHERE wishlist_id = ?", wishListId) > 0;
    }

    public static int addItem(int wishListId, String name, double price, String description) {
        return insert("INSERT INTO items (wishlist_id, name, price, description) VALUES (?, ?, ?, ?)",
                wishListId, name, price, description);
    }

    public static List<Map<String, Object>> getItemsByWishList(int wishListId) {
        return queryList("SELECT item_id, wishlist_id, name, price, description, status FROM items WHERE wishlist_id = ?", wishListId);
    }

    public static Map<String, Object> getItemById(int itemId) {
        return queryOne("SELECT item_id, wishlist_id, name, price, description, status FROM items WHERE item_id = ?", itemId);
    }

    public static boolean updateItem(int itemId, String name, double price, String description) {
        return update("UPDATE items SET name = ?, price = ?, description = ? WHERE item_id = ?",
                name, price, description, itemId) > 0;
    }

    public static boolean setItemStatus(int itemId, String status) {
        return update("UPDATE items SET status = ? WHERE item_id = ?", status, itemId) > 0;
    }

    public static boolean deleteItem(int itemId) {
        return update("DELETE FROM items WHERE item_id = ?", itemId) > 0;
    }

    public static int addContribution(int itemId, int contributorId, double amount) {
        return insert("INSERT INTO contributions (item_id, contributor_id, amount) VALUES (?, ?, ?)",
                itemId, contributorId, amount);
    }

    public static List<Map<String, Object>> getContributionsByItem(int itemId) {
        return queryList("SELECT c.contribution_id, c.item_id, c.contributor_id, u.username, c.amount, c.created_at "
                + "FROM contributions c JOIN users u ON u.user_id = c.contributor_id WHERE c.item_id = ?", itemId);
    }

    public static double getTotalContributed(int itemId) {
        Map<String, Object> row = queryOne("SELECT COALESCE(SUM(amount), 0) AS total FROM contributions WHERE item_id = ?", itemId);
        return row == null ? 0 : ((Number) row.get("total")).doubleValue();
    }

    public static int addNotification(int userId, String message) {
        return insert("INSERT INTO notifications (user_id, message) VALUES (?, ?)", userId, message);
    }

    public static List<Map<String, Object>> getNotifications(int userId) {
        return queryList("SELECT notification_id, user_id, message, is_read, created_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC", userId);
    }

    public static List<Map<String, Object>> getUnreadNotifications(int userId) {
        return queryList("SELECT notification_id, user_id, message, is_read, created_at FROM notifications WHERE user_id = ? AND is_read = FALSE ORDER BY created_at DESC", userId);
    }

    public static boolean markNotificationAsRead(int notificationId) {
        return update("UPDATE notifications SET is_read = TRUE WHERE notification_id = ?", notificationId) > 0;
    }

    public static boolean markAllNotificationsAsRead(int userId) {
        return update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", userId) > 0;
    }

    public static boolean deleteNotification(int notificationId) {
        return update("DELETE FROM notifications WHERE notification_id = ?", notificationId) > 0;
    }

    public static void main(String[] args) {
        initializeDatabase();
        System.out.println("Database ready");
    }
}
