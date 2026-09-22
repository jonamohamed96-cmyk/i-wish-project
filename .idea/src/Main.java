import javax.swing.table.DefaultTableModel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    public static void main(String[] args) {
        DBConnection.initializeDatabase();
        seedDemoData();

        SwingUtilities.invokeLater(() -> {
            ServerMain server = new ServerMain(new ServerMain.FullRequestHandler());
            try {
                server.start(ServerMain.DEFAULT_PORT);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Could not start server: " + e.getMessage(),
                        "Server Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }

    private static void seedDemoData() {
        try {
            if (!DBConnection.emailExists("ahmed@iwish.com")) {
                DBConnection.addUser("Ahmed", "ahmed@iwish.com", "123", java.sql.Date.valueOf("2000-01-01"));
            }
            if (!DBConnection.emailExists("sara@iwish.com")) {
                DBConnection.addUser("Sara", "sara@iwish.com", "123", java.sql.Date.valueOf("2001-02-02"));
            }
            if (!DBConnection.emailExists("mohamed@iwish.com")) {
                DBConnection.addUser("Mohamed", "mohamed@iwish.com", "123", java.sql.Date.valueOf("1999-03-03"));
            }
            List<Map<String, Object>> lists = DBConnection.getWishListsByUser(2);
            if (lists.isEmpty()) {
                int wl = DBConnection.createWishList(2, "Birthday", java.sql.Date.valueOf("2026-12-20"));
                DBConnection.addItem(wl, "Sony WH-1000XM4 Headphones", 3000, "Noise cancelling");
                DBConnection.addItem(wl, "PlayStation 5", 25000, "Digital edition");
                DBConnection.addItem(wl, "Coffee Maker", 1500, "Espresso machine");
            }
        } catch (Exception e) {
            System.err.println("Seed error: " + e.getMessage());
        }
    }

    public static class DBConnection {
        private static final String URL = "jdbc:mysql://localhost:3306/iwish?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        private static final String USER = "root";
        private static final String PASSWORD = "123456";

        public static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        }

        public static void initializeDatabase() {
            String[] statements = {
                    "CREATE TABLE IF NOT EXISTS users (user_id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) NOT NULL, email VARCHAR(100) NOT NULL UNIQUE, password VARCHAR(64) NOT NULL, birth_date DATE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                    "CREATE TABLE IF NOT EXISTS friends (friendship_id INT AUTO_INCREMENT PRIMARY KEY, requester_id INT NOT NULL, receiver_id INT NOT NULL, status VARCHAR(10) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE (requester_id, receiver_id), FOREIGN KEY (requester_id) REFERENCES users(user_id) ON DELETE CASCADE, FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE)",
                    "CREATE TABLE IF NOT EXISTS wishlists (wishlist_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, title VARCHAR(100) NOT NULL, event_date DATE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)",
                    "CREATE TABLE IF NOT EXISTS items (item_id INT AUTO_INCREMENT PRIMARY KEY, wishlist_id INT NOT NULL, name VARCHAR(100) NOT NULL, price DECIMAL(10,2) NOT NULL, description VARCHAR(255), status VARCHAR(10) NOT NULL DEFAULT 'OPEN', FOREIGN KEY (wishlist_id) REFERENCES wishlists(wishlist_id) ON DELETE CASCADE)",
                    "CREATE TABLE IF NOT EXISTS contributions (contribution_id INT AUTO_INCREMENT PRIMARY KEY, item_id INT NOT NULL, contributor_id INT NOT NULL, amount DECIMAL(10,2) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE, FOREIGN KEY (contributor_id) REFERENCES users(user_id) ON DELETE CASCADE)",
                    "CREATE TABLE IF NOT EXISTS notifications (notification_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, message VARCHAR(255) NOT NULL, is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)"
            };
            try (Connection con = getConnection(); Statement st = con.createStatement()) {
                for (String sql : statements) {
                    st.execute(sql);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public static List<Map<String, Object>> queryList(String sql, Object... params) {
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

        public static Map<String, Object> queryOne(String sql, Object... params) {
            List<Map<String, Object>> rows = queryList(sql, params);
            return rows.isEmpty() ? null : rows.get(0);
        }

        public static int update(String sql, Object... params) {
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

        public static int insert(String sql, Object... params) {
            try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

        public static int addUser(String username, String email, String password, java.sql.Date birthDate) {
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

        public static boolean updateUser(int userId, String username, String email, java.sql.Date birthDate) {
            return update("UPDATE users SET username = ?, email = ?, birth_date = ? WHERE user_id = ?",
                    username, email, birthDate, userId) > 0;
        }

        public static boolean updatePassword(int userId, String newPassword) {
            return update("UPDATE users SET password = ? WHERE user_id = ?",
                    hashPassword(newPassword), userId) > 0;
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
            return queryList("SELECT u.user_id, u.username, u.email, u.birth_date FROM users u JOIN friends f ON (f.requester_id = u.user_id AND f.receiver_id = ?) OR (f.receiver_id = u.user_id AND f.requester_id = ?) WHERE f.status = 'ACCEPTED'",
                    userId, userId);
        }

        public static List<Map<String, Object>> getPendingRequests(int userId) {
            return queryList("SELECT u.user_id, u.username, u.email, f.created_at FROM friends f JOIN users u ON u.user_id = f.requester_id WHERE f.receiver_id = ? AND f.status = 'PENDING'",
                    userId);
        }

        public static int createWishList(int userId, String title, java.sql.Date eventDate) {
            return insert("INSERT INTO wishlists (user_id, title, event_date) VALUES (?, ?, ?)",
                    userId, title, eventDate);
        }

        public static List<Map<String, Object>> getWishListsByUser(int userId) {
            return queryList("SELECT wishlist_id, user_id, title, event_date, created_at FROM wishlists WHERE user_id = ?",
                    userId);
        }

        public static Map<String, Object> getWishListById(int wishListId) {
            return queryOne("SELECT wishlist_id, user_id, title, event_date, created_at FROM wishlists WHERE wishlist_id = ?",
                    wishListId);
        }

        public static boolean updateWishList(int wishListId, String title, java.sql.Date eventDate) {
            return update("UPDATE wishlists SET title = ?, event_date = ? WHERE wishlist_id = ?",
                    title, eventDate, wishListId) > 0;
        }

        public static boolean deleteWishList(int wishListId) {
            return update("DELETE FROM wishlists WHERE wishlist_id = ?", wishListId) > 0;
        }

        public static int addItem(int wishListId, String name, double price, String description) {
            return insert("INSERT INTO items (wishlist_id, name, price, description) VALUES (?, ?, ?, ?)",
                    wishListId, name, price, description);
        }

        public static List<Map<String, Object>> getItemsByWishList(int wishListId) {
            return queryList("SELECT item_id, wishlist_id, name, price, description, status FROM items WHERE wishlist_id = ?",
                    wishListId);
        }

        public static Map<String, Object> getItemById(int itemId) {
            return queryOne("SELECT item_id, wishlist_id, name, price, description, status FROM items WHERE item_id = ?",
                    itemId);
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
            return queryList("SELECT c.contribution_id, c.item_id, c.contributor_id, u.username, c.amount, c.created_at FROM contributions c JOIN users u ON u.user_id = c.contributor_id WHERE c.item_id = ?",
                    itemId);
        }

        public static double getTotalContributed(int itemId) {
            Map<String, Object> row = queryOne("SELECT COALESCE(SUM(amount), 0) AS total FROM contributions WHERE item_id = ?",
                    itemId);
            return row == null ? 0 : ((Number) row.get("total")).doubleValue();
        }

        public static int addNotification(int userId, String message) {
            return insert("INSERT INTO notifications (user_id, message) VALUES (?, ?)", userId, message);
        }

        public static List<Map<String, Object>> getNotifications(int userId) {
            return queryList("SELECT notification_id, user_id, message, is_read, created_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC",
                    userId);
        }

        public static List<Map<String, Object>> getUnreadNotifications(int userId) {
            return queryList("SELECT notification_id, user_id, message, is_read, created_at FROM notifications WHERE user_id = ? AND is_read = FALSE ORDER BY created_at DESC",
                    userId);
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
    }

    public static class ServerMain {
        public static final int DEFAULT_PORT = 5005;

        public enum RequestType {
            REGISTER, LOGIN, LOGOUT,
            SEND_FRIEND_REQUEST, ACCEPT_FRIEND_REQUEST, DECLINE_FRIEND_REQUEST,
            REMOVE_FRIEND, GET_FRIENDS, GET_FRIEND_REQUESTS,
            GET_ALL_ITEMS, GET_MY_WISHLIST, ADD_WISH_ITEM, UPDATE_WISH_ITEM,
            DELETE_WISH_ITEM, GET_FRIEND_WISHLIST,
            CONTRIBUTE, GET_NOTIFICATIONS, MARK_NOTIFICATION_READ
        }

        public static class Request implements Serializable {
            private static final long serialVersionUID = 1L;
            private static final AtomicLong COUNTER = new AtomicLong();
            private final long id = COUNTER.incrementAndGet();
            private final RequestType type;
            private final Map<String, Serializable> data = new HashMap<>();

            public Request(RequestType type) { this.type = type; }
            public long getId() { return id; }
            public RequestType getType() { return type; }
            public Request put(String key, Serializable value) { data.put(key, value); return this; }
            @SuppressWarnings("unchecked")
            public <T> T get(String key) { return (T) data.get(key); }
            @Override public String toString() {
                return "Request{" + id + ", " + type + ", keys=" + data.keySet() + "}";
            }
        }

        public static class Response implements Serializable {
            private static final long serialVersionUID = 1L;
            public enum Kind { REPLY, PUSH }
            private final Kind kind;
            private final long requestId;
            private final boolean success;
            private final String message;
            private final String event;
            private final Map<String, Serializable> data = new HashMap<>();

            private Response(Kind kind, long requestId, boolean success, String message, String event) {
                this.kind = kind; this.requestId = requestId; this.success = success;
                this.message = message; this.event = event;
            }

            public static Response ok(long requestId) { return new Response(Kind.REPLY, requestId, true, "OK", null); }
            public static Response ok(long requestId, String message) { return new Response(Kind.REPLY, requestId, true, message, null); }
            public static Response error(long requestId, String message) { return new Response(Kind.REPLY, requestId, false, message, null); }
            public static Response push(String event, String message) { return new Response(Kind.PUSH, 0, true, message, event); }
            public Response put(String key, Serializable value) { data.put(key, value); return this; }
            @SuppressWarnings("unchecked")
            public <T> T get(String key) { return (T) data.get(key); }
            public Kind getKind() { return kind; }
            public boolean isPush() { return kind == Kind.PUSH; }
            public long getRequestId() { return requestId; }
            public boolean isSuccess() { return success; }
            public String getMessage() { return message; }
            public String getEvent() { return event; }
            @Override public String toString() {
                return "Response{" + kind + ", req=" + requestId + ", ok=" + success + ", msg=" + message + ", event=" + event + "}";
            }
        }

        public interface RequestHandler {
            Response handle(Request request, ClientHandler client) throws Exception;
        }

        private final RequestHandler handler;
        private final Collection<ClientHandler> clients = ConcurrentHashMap.newKeySet();
        private final Map<Integer, ClientHandler> byUser = new ConcurrentHashMap<>();
        private ServerSocket serverSocket;
        private ExecutorService pool;
        private volatile boolean running;
        private JTextArea logArea;
        private JTextField portField;
        private JButton startBtn;
        private JButton stopBtn;
        private JLabel status;

        public ServerMain(RequestHandler handler) {
            this.handler = handler;
        }

        public boolean isRunning() { return running; }
        public int connectedCount() { return clients.size(); }
        public boolean isOnline(int userId) { return byUser.containsKey(userId); }

        public boolean sendPush(int userId, Response push) {
            ClientHandler c = byUser.get(userId);
            return c != null && c.send(push);
        }

        public synchronized void start(int port) throws IOException {
            if (running) return;
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));
            pool = Executors.newCachedThreadPool();
            running = true;
            new Thread(this::acceptLoop, "iwish-accept").start();
            log("Server started on port " + port);
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket s = serverSocket.accept();
                    pool.execute(new ClientHandler(s));
                } catch (SocketException e) {
                    if (running) log("Accept error: " + e.getMessage());
                } catch (IOException e) {
                    log("Accept error: " + e.getMessage());
                }
            }
        }

        public synchronized void stop() {
            if (!running) return;
            running = false;
            try { serverSocket.close(); } catch (IOException ignored) { }
            for (ClientHandler c : clients) c.close();
            clients.clear();
            byUser.clear();
            pool.shutdownNow();
            log("Server stopped");
        }

        private void log(String msg) {
            String t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String line = "[" + t + "] " + msg;
            if (logArea == null) {
                System.out.println(line);
            } else {
                SwingUtilities.invokeLater(() -> logArea.append(line + "\n"));
            }
        }

        public class ClientHandler implements Runnable {
            private final Socket socket;
            private ObjectOutputStream out;
            private ObjectInputStream in;
            private volatile Integer userId;
            private volatile String username;

            private ClientHandler(Socket socket) { this.socket = socket; }
            public Integer getUserId() { return userId; }
            public String getUsername() { return username; }
            public boolean isLoggedIn() { return userId != null; }

            public void login(int userId, String username) {
                this.userId = userId;
                this.username = username;
                byUser.put(userId, this);
                log("User logged in: " + username + " (id=" + userId + ")");
            }

            public void logout() {
                Integer id = this.userId;
                if (id != null) {
                    byUser.remove(id, this);
                    log("User logged out: " + username);
                }
                this.userId = null;
                this.username = null;
            }

            @Override
            public void run() {
                String remote = String.valueOf(socket.getRemoteSocketAddress());
                log("Client connected: " + remote);
                try {
                    out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    in = new ObjectInputStream(socket.getInputStream());
                    clients.add(this);
                    while (!socket.isClosed()) {
                        Object obj = in.readObject();
                        if (!(obj instanceof Request)) continue;
                        Request req = (Request) obj;
                        Response res;
                        try {
                            res = handler.handle(req, this);
                        } catch (Exception e) {
                            log("Error handling " + req + ": " + e);
                            res = Response.error(req.getId(), "Server error: " + e.getMessage());
                        }
                        if (res != null) send(res);
                    }
                } catch (EOFException | SocketException e) {
                } catch (IOException | ClassNotFoundException e) {
                    log("Connection error (" + remote + "): " + e);
                } finally {
                    logout();
                    clients.remove(this);
                    close();
                    log("Client disconnected: " + remote);
                }
            }

            public boolean send(Response r) {
                synchronized (this) {
                    try {
                        if (out == null) return false;
                        out.writeObject(r);
                        out.flush();
                        out.reset();
                        return true;
                    } catch (IOException e) {
                        return false;
                    }
                }
            }

            private void close() {
                try { socket.close(); } catch (IOException ignored) { }
            }
        }

        public static class FullRequestHandler implements RequestHandler {
            @Override
            public Response handle(Request req, ClientHandler client) {
                try {
                    switch (req.getType()) {
                        case LOGIN: {
                            Integer uid = req.get("userId");
                            String uname = req.get("username");
                            if (uid != null && uname != null) client.login(uid, uname);
                            return Response.ok(req.getId(), "Logged in as " + uname);
                        }
                        case LOGOUT: {
                            client.logout();
                            return Response.ok(req.getId(), "Logged out");
                        }
                        case SEND_FRIEND_REQUEST: {
                            int from = client.getUserId();
                            int to = req.get("receiverId");
                            boolean ok = DBConnection.sendFriendRequest(from, to);
                            if (!ok) return Response.error(req.getId(), "Cannot send request");
                            DBConnection.addNotification(to, client.getUsername() + " sent you a friend request.");
                            return Response.ok(req.getId(), "Request sent");
                        }
                        case ACCEPT_FRIEND_REQUEST: {
                            int me = client.getUserId();
                            int from = req.get("requesterId");
                            boolean ok = DBConnection.acceptFriendRequest(from, me);
                            if (!ok) return Response.error(req.getId(), "No pending request");
                            DBConnection.addNotification(from, client.getUsername() + " accepted your friend request.");
                            return Response.ok(req.getId(), "Accepted");
                        }
                        case DECLINE_FRIEND_REQUEST: {
                            int me = client.getUserId();
                            int from = req.get("requesterId");
                            DBConnection.declineFriendRequest(from, me);
                            return Response.ok(req.getId(), "Declined");
                        }
                        case REMOVE_FRIEND: {
                            int me = client.getUserId();
                            int friend = req.get("friendId");
                            DBConnection.removeFriend(me, friend);
                            return Response.ok(req.getId(), "Removed");
                        }
                        case GET_FRIENDS: {
                            List<Map<String, Object>> friends = DBConnection.getFriends(client.getUserId());
                            return Response.ok(req.getId()).put("data", (Serializable) friends);
                        }
                        case GET_FRIEND_REQUESTS: {
                            List<Map<String, Object>> reqs = DBConnection.getPendingRequests(client.getUserId());
                            return Response.ok(req.getId()).put("data", (Serializable) reqs);
                        }
                        case GET_ALL_ITEMS: {
                            List<WishListModule.Item> items = new ArrayList<>();
                            List<Map<String, Object>> allUsers = DBConnection.queryList("SELECT user_id FROM users");
                            for (Map<String, Object> u : allUsers) {
                                int uid = ((Number) u.get("user_id")).intValue();
                                for (Map<String, Object> wl : DBConnection.getWishListsByUser(uid)) {
                                    int wlId = ((Number) wl.get("wishlist_id")).intValue();
                                    for (Map<String, Object> it : DBConnection.getItemsByWishList(wlId)) {
                                        items.add(toItem(it));
                                    }
                                }
                            }
                            return Response.ok(req.getId()).put("data", (Serializable) items);
                        }
                        case GET_MY_WISHLIST: {
                            List<WishListModule.WishListEntry> entries = new ArrayList<>();
                            for (Map<String, Object> wl : DBConnection.getWishListsByUser(client.getUserId())) {
                                int wlId = ((Number) wl.get("wishlist_id")).intValue();
                                for (Map<String, Object> it : DBConnection.getItemsByWishList(wlId)) {
                                    entries.add(toEntry(it));
                                }
                            }
                            return Response.ok(req.getId()).put("data", (Serializable) entries);
                        }
                        case ADD_WISH_ITEM: {
                            int itemId = req.get("itemId");
                            String note = req.get("note");
                            Map<String, Object> it = DBConnection.getItemById(itemId);
                            if (it == null) return Response.error(req.getId(), "Item not found");
                            List<Map<String, Object>> myLists = DBConnection.getWishListsByUser(client.getUserId());
                            int wlId;
                            if (myLists.isEmpty()) {
                                wlId = DBConnection.createWishList(client.getUserId(), "My Wishlist", null);
                            } else {
                                wlId = ((Number) myLists.get(0).get("wishlist_id")).intValue();
                            }
                            int newId = DBConnection.addItem(wlId, (String) it.get("name"), ((Number) it.get("price")).doubleValue(), note != null ? note : (String) it.get("description"));
                            Map<String, Object> newIt = DBConnection.getItemById(newId);
                            return Response.ok(req.getId()).put("data", toEntry(newIt));
                        }
                        case UPDATE_WISH_ITEM: {
                            int entryId = req.get("entryId");
                            String note = req.get("note");
                            DBConnection.updateItem(entryId, null, 0, note);
                            Map<String, Object> it = DBConnection.getItemById(entryId);
                            return Response.ok(req.getId()).put("data", toEntry(it));
                        }
                        case DELETE_WISH_ITEM: {
                            int entryId = req.get("entryId");
                            DBConnection.deleteItem(entryId);
                            return Response.ok(req.getId());
                        }
                        case GET_FRIEND_WISHLIST: {
                            String friendUsername = req.get("friendUsername");
                            Map<String, Object> friend = DBConnection.getUserByEmail(friendUsername);
                            if (friend == null) {
                                List<Map<String, Object>> all = DBConnection.getFriends(client.getUserId());
                                friend = null;
                                for (Map<String, Object> f : all) {
                                    if (friendUsername.equals(f.get("username"))) {
                                        friend = f;
                                        break;
                                    }
                                }
                            }
                            if (friend == null) return Response.error(req.getId(), "Friend not found");
                            int friendId = ((Number) friend.get("user_id")).intValue();
                            List<WishListModule.WishListEntry> entries = new ArrayList<>();
                            for (Map<String, Object> wl : DBConnection.getWishListsByUser(friendId)) {
                                int wlId = ((Number) wl.get("wishlist_id")).intValue();
                                for (Map<String, Object> it : DBConnection.getItemsByWishList(wlId)) {
                                    entries.add(toEntry(it));
                                }
                            }
                            return Response.ok(req.getId()).put("data", (Serializable) entries);
                        }
                        case CONTRIBUTE: {
                            int itemId = req.get("itemId");
                            double amount = ((Number) req.get("amount")).doubleValue();
                            int contributorId = client.getUserId();
                            DBConnection.addContribution(itemId, contributorId, amount);
                            Map<String, Object> it = DBConnection.getItemById(itemId);
                            int wlId = ((Number) it.get("wishlist_id")).intValue();
                            Map<String, Object> wl = DBConnection.getWishListById(wlId);
                            int ownerId = ((Number) wl.get("user_id")).intValue();
                            double total = DBConnection.getTotalContributed(itemId);
                            double price = ((Number) it.get("price")).doubleValue();
                            String msg = client.getUsername() + " contributed " + String.format("%.0f", amount) + " to '" + it.get("name") + "'";
                            if (total >= price) msg += " - Fully funded!";
                            DBConnection.addNotification(ownerId, msg);
                            return Response.ok(req.getId(), "Contribution recorded");
                        }
                        case GET_NOTIFICATIONS: {
                            List<Map<String, Object>> notifs = DBConnection.getNotifications(client.getUserId());
                            return Response.ok(req.getId()).put("data", (Serializable) notifs);
                        }
                        case MARK_NOTIFICATION_READ: {
                            int nid = req.get("notificationId");
                            DBConnection.markNotificationAsRead(nid);
                            return Response.ok(req.getId());
                        }
                        default:
                            return Response.error(req.getId(), req.getType() + " not implemented");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return Response.error(req.getId(), "Server error: " + e.getMessage());
                }
            }

            private WishListModule.Item toItem(Map<String, Object> r) {
                return new WishListModule.Item(
                        ((Number) r.get("item_id")).intValue(),
                        (String) r.get("name"),
                        (String) r.get("description"),
                        ((Number) r.get("price")).doubleValue(),
                        null);
            }

            private WishListModule.WishListEntry toEntry(Map<String, Object> r) {
                WishListModule.Item item = toItem(r);
                double contributed = DBConnection.getTotalContributed(item.getId());
                return new WishListModule.WishListEntry(
                        item.getId(), item, (String) r.get("description"), contributed);
            }
        }

        public void showGui() {
            JFrame frame = new JFrame("i-Wish Server");
            portField = new JTextField(String.valueOf(DEFAULT_PORT), 6);
            startBtn = new JButton("Start");
            stopBtn = new JButton("Stop");
            status = new JLabel("Stopped");
            logArea = new JTextArea();
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(new JLabel("Port:"));
            top.add(portField);
            top.add(startBtn);
            top.add(stopBtn);
            top.add(status);
            stopBtn.setEnabled(false);
            status.setForeground(Color.RED);
            logArea.setEditable(false);
            frame.add(top, BorderLayout.NORTH);
            frame.add(new JScrollPane(logArea), BorderLayout.CENTER);
            frame.setSize(640, 400);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            startBtn.addActionListener(e -> {
                try {
                    start(Integer.parseInt(portField.getText().trim()));
                    setRunningUi(true);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Invalid port number");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Could not start: " + ex.getMessage());
                }
            });
            stopBtn.addActionListener(e -> {
                stop();
                setRunningUi(false);
            });
            frame.setVisible(true);
        }

        private void setRunningUi(boolean isRunning) {
            startBtn.setEnabled(!isRunning);
            stopBtn.setEnabled(isRunning);
            portField.setEnabled(!isRunning);
            status.setText(isRunning ? "Running" : "Stopped");
            status.setForeground(isRunning ? new Color(0, 130, 0) : Color.RED);
        }
    }

    public static class LoginFrame extends JFrame {
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
            getContentPane().setBackground(new Color(244, 247, 246));

            JPanel mainPanel = new JPanel(new GridBagLayout());
            mainPanel.setBackground(new Color(244, 247, 246));
            mainPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(10, 10, 10, 10);

            JLabel titleLabel = new JLabel("I-Wish", SwingConstants.CENTER);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
            titleLabel.setForeground(new Color(44, 62, 80));
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
            loginButton = createRoundedButton("Login", new Color(52, 152, 219), 150, 40);
            mainPanel.add(loginButton, gbc);

            gbc.gridy = 5;
            registerButton = createRoundedButton("Register", new Color(46, 204, 113), 150, 40);
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
                public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
                public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
            });
            return btn;
        }

        private void handleLogin() {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter username and password", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Map<String, Object> user = DBConnection.login(username + "@iwish.com", password);

            if (user != null) {
                try {
                    WishListModule.NetworkClient client = WishListModule.NetworkClient.connect("localhost", ServerMain.DEFAULT_PORT);
                    ServerMain.Request loginReq = new ServerMain.Request(ServerMain.RequestType.LOGIN);
                    loginReq.put("userId", ((Number) user.get("user_id")).intValue());
                    loginReq.put("username", (String) user.get("username"));
                    client.send(loginReq);

                    WishListModule.WishListService service = new WishListModule.WishListService(client);

                    int userId = ((Number) user.get("user_id")).intValue();
                    String userName = (String) user.get("username");
                    MainFrame mainFrame = new MainFrame(userId, userName, service);
                    mainFrame.setVisible(true);
                    dispose();
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Could not connect to server: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username or password", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void handleRegister() {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter username and password", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (DBConnection.emailExists(username + "@iwish.com")) {
                JOptionPane.showMessageDialog(this, "Username already exists", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int result = DBConnection.addUser(username, username + "@iwish.com", password, java.sql.Date.valueOf("2000-01-01"));

            if (result > 0) {
                JOptionPane.showMessageDialog(this, "Registration successful! Please login.", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Registration failed", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static class MainFrame extends JFrame {
        private JPanel contentArea;
        private CardLayout cardLayout;
        private int userId;
        private String username;
        private WishListModule.WishListService service;

        public MainFrame(int userId, String username, WishListModule.WishListService service) {
            this.userId = userId;
            this.username = username;
            this.service = service;
            setTitle("I-Wish | Make Your Friends Happy - Welcome " + username);
            setSize(1100, 700);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLayout(new BorderLayout());
            getContentPane().setBackground(new Color(244, 247, 246));

            add(new Sidebar(this), BorderLayout.WEST);

            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            topPanel.setBackground(new Color(244, 247, 246));
            JLabel welcomeLabel = new JLabel("Welcome, " + username + "!");
            welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
            welcomeLabel.setForeground(new Color(52, 73, 94));
            topPanel.add(welcomeLabel);
            add(topPanel, BorderLayout.NORTH);

            cardLayout = new CardLayout();
            contentArea = new JPanel(cardLayout);
            contentArea.setBackground(new Color(244, 247, 246));
            contentArea.setBorder(new EmptyBorder(20, 20, 20, 20));

            contentArea.add(new DashboardPanel(), "Dashboard");
            contentArea.add(new MyWishListPanel(service), "MyWishList");
            contentArea.add(new FriendsPanel(userId, service), "Friends");
            contentArea.add(new FriendWishListPanel(service), "FriendWishList");
            contentArea.add(new ContributionPanel(userId, service), "Contribution");
            contentArea.add(new NotificationsPanel(userId, service), "Notifications");

            add(contentArea, BorderLayout.CENTER);
            cardLayout.show(contentArea, "Dashboard");
        }

        public void showScreen(String screenName) {
            cardLayout.show(contentArea, screenName);
        }

        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public WishListModule.WishListService getService() { return service; }
    }

    public static class Sidebar extends JPanel {
        public MainFrame parentFrame;

        public Sidebar(MainFrame parent) {
            this.parentFrame = parent;
            setPreferredSize(new Dimension(220, 0));
            setBackground(new Color(44, 62, 80));
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
            btn.setBackground(new Color(44, 62, 80));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setBorder(new EmptyBorder(10, 15, 10, 15));
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(52, 73, 94)); }
                public void mouseExited(MouseEvent e) { btn.setBackground(new Color(44, 62, 80)); }
            });
            btn.addActionListener(e -> parentFrame.showScreen(screenName));
            return btn;
        }
    }

    public static class DashboardPanel extends JPanel {
        public DashboardPanel() {
            setLayout(new BorderLayout(20, 20));
            setOpaque(false);

            JPanel welcomeCard = new JPanel(new BorderLayout(20, 20));
            welcomeCard.setBackground(Color.WHITE);
            welcomeCard.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(30, 30, 30, 30)
            ));

            JLabel welcomeText = new JLabel("Welcome to I-Wish!");
            welcomeText.setFont(new Font("Segoe UI", Font.BOLD, 28));
            welcomeText.setForeground(new Color(52, 73, 94));
            welcomeCard.add(welcomeText, BorderLayout.NORTH);

            JLabel description = new JLabel("<html>Manage your wish lists, connect with friends, <br>and make each other happy by contributing to wishes!</html>");
            description.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            description.setForeground(new Color(127, 140, 141));
            welcomeCard.add(description, BorderLayout.CENTER);

            add(welcomeCard, BorderLayout.CENTER);
        }
    }

    public static class MyWishListPanel extends JPanel {
        private final WishListModule.WishListService service;
        private DefaultTableModel tableModel;
        private JTable table;

        public MyWishListPanel(WishListModule.WishListService service) {
            this.service = service;
            setLayout(new BorderLayout(15, 15));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("My Wish List");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(new Color(52, 73, 94));
            add(title, BorderLayout.NORTH);

            tableModel = new DefaultTableModel(new Object[]{"Item", "Price", "Description", "Funded", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
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

            JButton btnAdd = createButton("Add Item", new Color(52, 152, 219));
            JButton btnRefresh = createButton("Refresh", new Color(46, 204, 113));

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
                public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
                public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
            });
            return btn;
        }

        private void refreshList() {
            try {
                List<WishListModule.WishListEntry> entries = service.getMyWishList();
                tableModel.setRowCount(0);
                for (WishListModule.WishListEntry entry : entries) {
                    tableModel.addRow(new Object[]{
                            entry.getItem().getName(),
                            String.format("%.2f EGP", entry.getItem().getPrice()),
                            entry.getNote(),
                            String.format("%.2f EGP", entry.getAmountContributed()),
                            entry.isFullyFunded() ? "Fully Funded \u2713" : "In Progress"
                    });
                }
            } catch (WishListModule.WishListService.WishListException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void addItem() {
            try {
                List<WishListModule.Item> catalog = service.getCatalog();
                if (catalog.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No items available in catalog.", "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                String[] itemNames = new String[catalog.size()];
                for (int i = 0; i < catalog.size(); i++) {
                    itemNames[i] = catalog.get(i).getName() + " - " + String.format("%.2f EGP", catalog.get(i).getPrice());
                }

                String selected = (String) JOptionPane.showInputDialog(this, "Select item to add:", "Add Item", JOptionPane.PLAIN_MESSAGE, null, itemNames, itemNames[0]);

                if (selected != null) {
                    int selectedIndex = Arrays.asList(itemNames).indexOf(selected);
                    WishListModule.Item item = catalog.get(selectedIndex);
                    String note = JOptionPane.showInputDialog(this, "Enter note (optional):");
                    service.addItem(item, note != null ? note : "");
                    JOptionPane.showMessageDialog(this, "Item added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshList();
                }
            } catch (WishListModule.WishListService.WishListException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static class FriendsPanel extends JPanel {
        private final int userId;
        private final WishListModule.WishListService service;
        private DefaultListModel<String> listModel;
        private JList<String> friendsList;

        public FriendsPanel(int userId, WishListModule.WishListService service) {
            this.userId = userId;
            this.service = service;
            setLayout(new BorderLayout(15, 15));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("My Friends");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(new Color(52, 73, 94));
            add(title, BorderLayout.NORTH);

            listModel = new DefaultListModel<>();
            friendsList = new JList<>(listModel);
            friendsList.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            friendsList.setSelectionBackground(new Color(235, 245, 251));
            friendsList.setFixedCellHeight(40);

            JScrollPane scrollPane = new JScrollPane(friendsList);
            scrollPane.setBorder(new LineBorder(new Color(230, 230, 230), 1, true));
            add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            buttonPanel.setOpaque(false);

            JButton btnAdd = createButton("Add Friend", new Color(52, 152, 219));
            JButton btnRemove = createButton("Remove Friend", new Color(231, 76, 60));
            JButton btnRefresh = createButton("Refresh", new Color(46, 204, 113));

            btnAdd.addActionListener(e -> addFriend());
            btnRemove.addActionListener(e -> removeFriend());
            btnRefresh.addActionListener(e -> refreshList());

            buttonPanel.add(btnAdd);
            buttonPanel.add(btnRemove);
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
            return btn;
        }

        private void refreshList() {
            try {
                WishListModule.NetworkClient client = WishListModule.NetworkClient.getInstance();
                ServerMain.Request req = new ServerMain.Request(ServerMain.RequestType.GET_FRIENDS);
                ServerMain.Response res = client.send(req);

                if (res.isSuccess()) {
                    List<Map<String, Object>> friends = res.get("data");
                    listModel.clear();
                    for (Map<String, Object> f : friends) {
                        listModel.addElement((String) f.get("username"));
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void addFriend() {
            String username = JOptionPane.showInputDialog(this, "Enter friend's username:");
            if (username != null && !username.trim().isEmpty()) {
                try {
                    WishListModule.NetworkClient client = WishListModule.NetworkClient.getInstance();
                    ServerMain.Request req = new ServerMain.Request(ServerMain.RequestType.SEND_FRIEND_REQUEST);
                    Map<String, Object> friend = DBConnection.getUserByEmail(username + "@iwish.com");
                    if (friend == null) {
                        JOptionPane.showMessageDialog(this, "User not found.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    req.put("receiverId", ((Number) friend.get("user_id")).intValue());
                    ServerMain.Response res = client.send(req);
                    if (res.isSuccess()) {
                        JOptionPane.showMessageDialog(this, "Friend request sent!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, res.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void removeFriend() {
            String selected = friendsList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(this, "Remove " + selected + "?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        WishListModule.NetworkClient client = WishListModule.NetworkClient.getInstance();
                        ServerMain.Request req = new ServerMain.Request(ServerMain.RequestType.REMOVE_FRIEND);
                        Map<String, Object> friend = DBConnection.getUserByEmail(selected + "@iwish.com");
                        req.put("friendId", ((Number) friend.get("user_id")).intValue());
                        client.send(req);
                        refreshList();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }

    public static class FriendWishListPanel extends JPanel {
        private final WishListModule.WishListService service;
        private DefaultTableModel tableModel;
        private JComboBox<String> friendCombo;

        public FriendWishListPanel(WishListModule.WishListService service) {
            this.service = service;
            setLayout(new BorderLayout(15, 15));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("Friend's Wish List");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(new Color(52, 73, 94));
            add(title, BorderLayout.NORTH);

            JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            infoPanel.setOpaque(false);
            infoPanel.add(new JLabel("Select Friend:"));

            friendCombo = new JComboBox<>();
            infoPanel.add(friendCombo);

            JButton btnView = createButton("View List", new Color(52, 152, 219));
            JButton btnRefresh = createButton("Refresh Friends", new Color(46, 204, 113));
            infoPanel.add(btnView);
            infoPanel.add(btnRefresh);
            add(infoPanel, BorderLayout.NORTH);

            tableModel = new DefaultTableModel(new Object[]{"Item", "Price", "Description", "Needed"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };

            JTable table = new JTable(tableModel);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            table.setRowHeight(35);

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(new LineBorder(new Color(230, 230, 230), 1, true));
            add(scrollPane, BorderLayout.CENTER);

            btnView.addActionListener(e -> viewList());
            btnRefresh.addActionListener(e -> refreshFriends());

            refreshFriends();
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

        private void refreshFriends() {
            try {
                WishListModule.NetworkClient client = WishListModule.NetworkClient.getInstance();
                ServerMain.Request req = new ServerMain.Request(ServerMain.RequestType.GET_FRIENDS);
                ServerMain.Response res = client.send(req);

                if (res.isSuccess()) {
                    List<Map<String, Object>> friends = res.get("data");
                    friendCombo.removeAllItems();
                    for (Map<String, Object> f : friends) {
                        friendCombo.addItem((String) f.get("username"));
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void viewList() {
            String selected = (String) friendCombo.getSelectedItem();
            if (selected != null) {
                try {
                    List<WishListModule.WishListEntry> entries = service.getFriendWishList(selected);
                    tableModel.setRowCount(0);
                    for (WishListModule.WishListEntry entry : entries) {
                        String remaining = entry.isFullyFunded() ? "Fully Funded \u2713" : String.format("%.2f EGP", entry.getRemainingAmount());
                        tableModel.addRow(new Object[]{
                                entry.getItem().getName(),
                                String.format("%.2f EGP", entry.getItem().getPrice()),
                                entry.getNote(),
                                remaining
                        });
                    }
                } catch (WishListModule.WishListService.WishListException e) {
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    public static class ContributionPanel extends JPanel {
        private final int userId;
        private final WishListModule.WishListService service;
        private JTextField amountField;
        private JProgressBar progressBar;
        private JComboBox<String> friendCombo;
        private JComboBox<String> itemCombo;
        private JLabel itemNameLabel;
        private JLabel friendNameLabel;
        private JLabel priceLabel;
        private WishListModule.WishListEntry currentEntry;

        public ContributionPanel(int userId, WishListModule.WishListService service) {
            this.userId = userId;
            this.service = service;
            setLayout(new BorderLayout(20, 20));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("Contribute to Friend's Wish");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(new Color(52, 73, 94));
            add(title, BorderLayout.NORTH);

            JPanel selectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            selectionPanel.setOpaque(false);
            selectionPanel.add(new JLabel("Friend:"));
            friendCombo = new JComboBox<>();
            selectionPanel.add(friendCombo);
            selectionPanel.add(new JLabel("Item:"));
            itemCombo = new JComboBox<>();
            selectionPanel.add(itemCombo);
            JButton btnLoad = createButton("Load", new Color(52, 152, 219));
            selectionPanel.add(btnLoad);
            add(selectionPanel, BorderLayout.NORTH);

            JPanel itemCard = new JPanel(new BorderLayout(20, 20));
            itemCard.setBackground(Color.WHITE);
            itemCard.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(230, 230, 230), 1, true),
                    new EmptyBorder(30, 30, 30, 30)
            ));

            JPanel itemDetails = new JPanel(new GridLayout(0, 1, 10, 10));
            itemDetails.setOpaque(false);

            itemNameLabel = new JLabel("Item: -");
            itemNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
            itemNameLabel.setForeground(new Color(52, 73, 94));

            friendNameLabel = new JLabel("For: -");
            friendNameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            friendNameLabel.setForeground(new Color(127, 140, 141));

            priceLabel = new JLabel("Total Price: -");
            priceLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            priceLabel.setForeground(new Color(52, 152, 219));

            itemDetails.add(itemNameLabel);
            itemDetails.add(friendNameLabel);
            itemDetails.add(priceLabel);

            JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
            progressPanel.setOpaque(false);

            progressBar = new JProgressBar(0, 100);
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
            progressBar.setString("0 EGP collected (0%)");
            progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
            progressBar.setForeground(new Color(46, 204, 113));
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
            amountLabel.setForeground(new Color(52, 73, 94));

            amountField = new JTextField(10);
            amountField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            amountField.setPreferredSize(new Dimension(150, 40));

            RoundedButton btnContribute = new RoundedButton("Contribute Now", new Color(46, 204, 113), Color.WHITE);
            btnContribute.setFont(new Font("Segoe UI", Font.BOLD, 16));
            btnContribute.setPreferredSize(new Dimension(180, 45));
            btnContribute.addActionListener(e -> handleContribution());

            actionPanel.add(amountLabel);
            actionPanel.add(amountField);
            actionPanel.add(btnContribute);
            add(actionPanel, BorderLayout.SOUTH);

            btnLoad.addActionListener(e -> loadItem());
            refreshFriends();
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

        private void refreshFriends() {
            try {
                WishListModule.NetworkClient client = WishListModule.NetworkClient.getInstance();
                ServerMain.Request req = new ServerMain.Request(ServerMain.RequestType.GET_FRIENDS);
                ServerMain.Response res = client.send(req);

                if (res.isSuccess()) {
                    List<Map<String, Object>> friends = res.get("data");
                    friendCombo.removeAllItems();
                    for (Map<String, Object> f : friends) {
                        friendCombo.addItem((String) f.get("username"));
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void loadItem() {
            String friend = (String) friendCombo.getSelectedItem();
            if (friend != null) {
                try {
                    List<WishListModule.WishListEntry> entries = service.getFriendWishList(friend);
                    itemCombo.removeAllItems();
                    for (WishListModule.WishListEntry entry : entries) {
                        itemCombo.addItem(entry.getItem().getName());
                    }
                } catch (WishListModule.WishListService.WishListException e) {
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void handleContribution() {
            String friend = (String) friendCombo.getSelectedItem();
            String itemName = (String) itemCombo.getSelectedItem();
            String amountStr = amountField.getText().trim();

            if (friend == null || itemName == null || amountStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all fields!", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    JOptionPane.showMessageDialog(this, "Amount must be greater than 0!", "Warning", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                WishListModule.NetworkClient client = WishListModule.NetworkClient.getInstance();
                List<WishListModule.WishListEntry> entries = service.getFriendWishList(friend);
                WishListModule.WishListEntry selectedEntry = null;
                for (WishListModule.WishListEntry entry : entries) {
                    if (entry.getItem().getName().equals(itemName)) {
                        selectedEntry = entry;
                        break;
                    }
                }

                if (selectedEntry != null) {
                    ServerMain.Request req = new ServerMain.Request(ServerMain.RequestType.CONTRIBUTE);
                    req.put("itemId", selectedEntry.getItem().getId());
                    req.put("amount", amount);
                    ServerMain.Response res = client.send(req);

                    if (res.isSuccess()) {
                        JOptionPane.showMessageDialog(this, "Thank you! Your contribution of " + amountStr + " EGP was sent successfully.\nYour friend will be notified.", "Success", JOptionPane.INFORMATION_MESSAGE);
                        amountField.setText("");
                        loadItem();
                    } else {
                        JOptionPane.showMessageDialog(this, res.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Please enter a valid number!", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static class NotificationsPanel extends JPanel {
        private final int userId;
        private final WishListModule.WishListService service;
        private JPanel listPanel;

        public NotificationsPanel(int userId, WishListModule.WishListService service) {
            this.userId = userId;
            this.service = service;
            setLayout(new BorderLayout(20, 20));
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("Notifications");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(new Color(52, 73, 94));
            add(title, BorderLayout.NORTH);

            listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setOpaque(false);

            JScrollPane scrollPane = new JScrollPane(listPanel);
            scrollPane.setBorder(null);
            scrollPane.setBackground(new Color(244, 247, 246));
            scrollPane.getViewport().setOpaque(false);
            add(scrollPane, BorderLayout.CENTER);

            refreshNotifications();
        }

        private void refreshNotifications() {
            try {
                WishListModule.NetworkClient client = WishListModule.NetworkClient.getInstance();
                ServerMain.Request req = new ServerMain.Request(ServerMain.RequestType.GET_NOTIFICATIONS);
                ServerMain.Response res = client.send(req);

                if (res.isSuccess()) {
                    List<Map<String, Object>> notifs = res.get("data");
                    listPanel.removeAll();
                    for (Map<String, Object> n : notifs) {
                        String message = (String) n.get("message");
                        String time = n.get("created_at").toString();
                        boolean isRead = (Boolean) n.get("is_read");
                        Color bgColor = isRead ? new Color(245, 245, 245) : new Color(235, 245, 251);
                        listPanel.add(createNotificationCard("Notification", message, time, bgColor));
                        listPanel.add(Box.createRigidArea(new Dimension(0, 10)));
                    }
                    listPanel.revalidate();
                    listPanel.repaint();
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
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

            JLabel typeLabel = new JLabel(type);
            typeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            typeLabel.setForeground(new Color(52, 152, 219));

            JLabel msgLabel = new JLabel("<html>" + message + "</html>");
            msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            msgLabel.setForeground(new Color(52, 73, 94));

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

    public static class RoundedButton extends JButton {
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

    public static class WishListModule {
        public static class Item implements Serializable {
            private int id;
            private String name;
            private String description;
            private double price;
            private String imageUrl;

            public Item() {}
            public Item(int id, String name, String description, double price, String imageUrl) {
                this.id = id; this.name = name; this.description = description;
                this.price = price; this.imageUrl = imageUrl;
            }
            public int getId() { return id; }
            public void setId(int id) { this.id = id; }
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getDescription() { return description; }
            public void setDescription(String description) { this.description = description; }
            public double getPrice() { return price; }
            public void setPrice(double price) { this.price = price; }
            public String getImageUrl() { return imageUrl; }
            public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
            @Override
            public String toString() { return name + " - $" + String.format("%.2f", price); }
        }

        public static class WishListEntry implements Serializable {
            private int entryId;
            private Item item;
            private String note;
            private double amountContributed;
            private boolean fullyFunded;

            public WishListEntry() {}
            public WishListEntry(int entryId, Item item, String note, double amountContributed) {
                this.entryId = entryId; this.item = item; this.note = note;
                this.amountContributed = amountContributed;
                this.fullyFunded = item != null && amountContributed >= item.getPrice();
            }
            public int getEntryId() { return entryId; }
            public void setEntryId(int entryId) { this.entryId = entryId; }
            public Item getItem() { return item; }
            public void setItem(Item item) { this.item = item; }
            public String getNote() { return note; }
            public void setNote(String note) { this.note = note; }
            public double getAmountContributed() { return amountContributed; }
            public void setAmountContributed(double amountContributed) {
                this.amountContributed = amountContributed;
                this.fullyFunded = item != null && amountContributed >= item.getPrice();
            }
            public double getRemainingAmount() {
                if (item == null) return 0;
                return Math.max(item.getPrice() - amountContributed, 0);
            }
            public boolean isFullyFunded() { return fullyFunded; }
        }

        public static class NetworkClient {
            private static NetworkClient instance;
            private final Socket socket;
            private final ObjectOutputStream out;
            private final ObjectInputStream in;

            private NetworkClient(String host, int port) throws IOException {
                this.socket = new Socket(host, port);
                this.out = new ObjectOutputStream(socket.getOutputStream());
                this.out.flush();
                this.in = new ObjectInputStream(socket.getInputStream());
            }

            public static synchronized NetworkClient connect(String host, int port) throws IOException {
                if (instance == null) instance = new NetworkClient(host, port);
                return instance;
            }

            public static NetworkClient getInstance() {
                if (instance == null) throw new IllegalStateException("Not connected yet");
                return instance;
            }

            public synchronized ServerMain.Response send(ServerMain.Request request) {
                try {
                    out.writeObject(request);
                    out.flush();
                    out.reset();
                    while (true) {
                        Object obj = in.readObject();
                        if (!(obj instanceof ServerMain.Response)) {
                            return ServerMain.Response.error(request.getId(), "Malformed response");
                        }
                        ServerMain.Response response = (ServerMain.Response) obj;
                        if (response.isPush()) continue;
                        return response;
                    }
                } catch (IOException | ClassNotFoundException e) {
                    return ServerMain.Response.error(request.getId(), "Connection problem: " + e.getMessage());
                }
            }

            public void close() {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        public static class WishListService {
            private final NetworkClient client;

            public WishListService(NetworkClient client) { this.client = client; }

            private ServerMain.Response call(ServerMain.Request request) throws WishListException {
                ServerMain.Response response = client.send(request);
                if (!response.isSuccess()) throw new WishListException(response.getMessage());
                return response;
            }

            public List<Item> getCatalog() throws WishListException {
                ServerMain.Response response = call(new ServerMain.Request(ServerMain.RequestType.GET_ALL_ITEMS));
                List<Item> items = response.get("data");
                return items != null ? items : new ArrayList<>();
            }

            public List<WishListEntry> getMyWishList() throws WishListException {
                ServerMain.Response response = call(new ServerMain.Request(ServerMain.RequestType.GET_MY_WISHLIST));
                List<WishListEntry> entries = response.get("data");
                return entries != null ? entries : new ArrayList<>();
            }

            public WishListEntry addItem(Item item, String note) throws WishListException {
                ServerMain.Request request = new ServerMain.Request(ServerMain.RequestType.ADD_WISH_ITEM)
                        .put("itemId", item.getId()).put("note", note);
                return call(request).get("data");
            }

            public WishListEntry updateEntry(int entryId, String newNote) throws WishListException {
                ServerMain.Request request = new ServerMain.Request(ServerMain.RequestType.UPDATE_WISH_ITEM)
                        .put("entryId", entryId).put("note", newNote);
                return call(request).get("data");
            }

            public void deleteEntry(int entryId) throws WishListException {
                ServerMain.Request request = new ServerMain.Request(ServerMain.RequestType.DELETE_WISH_ITEM)
                        .put("entryId", entryId);
                call(request);
            }

            public List<WishListEntry> getFriendWishList(String friendUsername) throws WishListException {
                ServerMain.Request request = new ServerMain.Request(ServerMain.RequestType.GET_FRIEND_WISHLIST)
                        .put("friendUsername", friendUsername);
                List<WishListEntry> data = call(request).get("data");
                return data != null ? data : new ArrayList<>();
            }

            public static class WishListException extends Exception {
                public WishListException(String message) { super(message); }
            }
        }
    }
}
