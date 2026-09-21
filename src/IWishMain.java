package iwish;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Date;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class IWishMain {

    public static void main(String[] args) {
        DBConnection.initializeDatabase();
        seedDemoData();

        ServerMain server = new ServerMain(new FullRequestHandler());
        server.showGui();
        try {
            server.start(ServerMain.DEFAULT_PORT);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not start server: " + e.getMessage(), "Server Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                WishListModule.NetworkClient client = WishListModule.NetworkClient.connect("localhost", ServerMain.DEFAULT_PORT);

                ServerMain.Request loginReq = new ServerMain.Request(ServerMain.RequestType.LOGIN);
                loginReq.put("userId", 1).put("username", "Ahmed");
                client.send(loginReq);

                WishListModule.WishListService service = new WishListModule.WishListService(client);

                JFrame wishFrame = new JFrame("i-Wish — Wish Lists");
                JTabbedPane tabs = new JTabbedPane();
                tabs.addTab("My Wish List", new WishListModule.MyWishListPanel(service));
                WishListModule.FriendWishListPanel friendPanel = new WishListModule.FriendWishListPanel(service);
                tabs.addTab("Friend's Wish List", friendPanel);
                wishFrame.add(tabs);
                wishFrame.setSize(700, 500);
                wishFrame.setLocation(100, 100);
                wishFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                wishFrame.setVisible(true);

                new FriendRequestsFrame(1).setVisible(true);
                new IWishMemberGUI.MainFrame().setVisible(true);

            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Could not connect to server: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void seedDemoData() {
        try {
            if (!DBConnection.emailExists("ahmed@iwish.com")) {
                DBConnection.addUser("Ahmed", "ahmed@iwish.com", "123", Date.valueOf("2000-01-01"));
            }
            if (!DBConnection.emailExists("sara@iwish.com")) {
                DBConnection.addUser("Sara", "sara@iwish.com", "123", Date.valueOf("2001-02-02"));
            }
            if (!DBConnection.emailExists("mohamed@iwish.com")) {
                DBConnection.addUser("Mohamed", "mohamed@iwish.com", "123", Date.valueOf("1999-03-03"));
            }

            List<Map<String, Object>> lists = DBConnection.getWishListsByUser(2);
            if (lists.isEmpty()) {
                int wl = DBConnection.createWishList(2, "Birthday", Date.valueOf("2026-12-20"));
                DBConnection.addItem(wl, "Sony WH-1000XM4 Headphones", 3000, "Noise cancelling");
                DBConnection.addItem(wl, "PlayStation 5", 25000, "Digital edition");
                DBConnection.addItem(wl, "Coffee Maker", 1500, "Espresso machine");
            }
        } catch (Exception e) {
            System.err.println("Seed error: " + e.getMessage());
        }
    }

    public static class FullRequestHandler implements ServerMain.RequestHandler {

        @Override
        public ServerMain.Response handle(ServerMain.Request req, ServerMain.ClientHandler client) {
            try {
                switch (req.getType()) {
                    case LOGIN: {
                        Integer uid = req.get("userId");
                        String uname = req.get("username");
                        if (uid != null && uname != null) client.login(uid, uname);
                        return ServerMain.Response.ok(req.getId(), "Logged in as " + uname);
                    }
                    case LOGOUT: {
                        client.logout();
                        return ServerMain.Response.ok(req.getId(), "Logged out");
                    }
                    case SEND_FRIEND_REQUEST: {
                        int from = client.getUserId();
                        int to = req.get("receiverId");
                        boolean ok = DBConnection.sendFriendRequest(from, to);
                        if (!ok) return ServerMain.Response.error(req.getId(), "Cannot send request");
                        DBConnection.addNotification(to, client.getUsername() + " sent you a friend request.");
                        return ServerMain.Response.ok(req.getId(), "Request sent");
                    }
                    case ACCEPT_FRIEND_REQUEST: {
                        int me = client.getUserId();
                        int from = req.get("requesterId");
                        boolean ok = DBConnection.acceptFriendRequest(from, me);
                        if (!ok) return ServerMain.Response.error(req.getId(), "No pending request");
                        DBConnection.addNotification(from, client.getUsername() + " accepted your friend request.");
                        return ServerMain.Response.ok(req.getId(), "Accepted");
                    }
                    case DECLINE_FRIEND_REQUEST: {
                        int me = client.getUserId();
                        int from = req.get("requesterId");
                        DBConnection.declineFriendRequest(from, me);
                        return ServerMain.Response.ok(req.getId(), "Declined");
                    }
                    case REMOVE_FRIEND: {
                        int me = client.getUserId();
                        int friend = req.get("friendId");
                        DBConnection.removeFriend(me, friend);
                        return ServerMain.Response.ok(req.getId(), "Removed");
                    }
                    case GET_FRIENDS: {
                        List<Map<String, Object>> friends = DBConnection.getFriends(client.getUserId());
                        return ServerMain.Response.ok(req.getId()).put("data", (Serializable) friends);
                    }
                    case GET_FRIEND_REQUESTS: {
                        List<Map<String, Object>> reqs = DBConnection.getPendingRequests(client.getUserId());
                        return ServerMain.Response.ok(req.getId()).put("data", (Serializable) reqs);
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
                        return ServerMain.Response.ok(req.getId()).put("data", (Serializable) items);
                    }
                    case GET_MY_WISHLIST: {
                        List<WishListModule.WishListEntry> entries = new ArrayList<>();
                        for (Map<String, Object> wl : DBConnection.getWishListsByUser(client.getUserId())) {
                            int wlId = ((Number) wl.get("wishlist_id")).intValue();
                            for (Map<String, Object> it : DBConnection.getItemsByWishList(wlId)) {
                                entries.add(toEntry(it));
                            }
                        }
                        return ServerMain.Response.ok(req.getId()).put("data", (Serializable) entries);
                    }
                    case ADD_WISH_ITEM: {
                        int itemId = req.get("itemId");
                        String note = req.get("note");
                        Map<String, Object> it = DBConnection.getItemById(itemId);
                        if (it == null) return ServerMain.Response.error(req.getId(), "Item not found");

                        List<Map<String, Object>> myLists = DBConnection.getWishListsByUser(client.getUserId());
                        int wlId;
                        if (myLists.isEmpty()) {
                            wlId = DBConnection.createWishList(client.getUserId(), "My Wishlist", null);
                        } else {
                            wlId = ((Number) myLists.get(0).get("wishlist_id")).intValue();
                        }
                        int newId = DBConnection.addItem(wlId, (String) it.get("name"), ((Number) it.get("price")).doubleValue(), note != null ? note : (String) it.get("description"));
                        Map<String, Object> newIt = DBConnection.getItemById(newId);
                        return ServerMain.Response.ok(req.getId()).put("data", toEntry(newIt));
                    }
                    case UPDATE_WISH_ITEM: {
                        int entryId = req.get("entryId");
                        String note = req.get("note");
                        DBConnection.updateItem(entryId, null, 0, note);
                        Map<String, Object> it = DBConnection.getItemById(entryId);
                        return ServerMain.Response.ok(req.getId()).put("data", toEntry(it));
                    }
                    case DELETE_WISH_ITEM: {
                        int entryId = req.get("entryId");
                        DBConnection.deleteItem(entryId);
                        return ServerMain.Response.ok(req.getId());
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
                        if (friend == null) return ServerMain.Response.error(req.getId(), "Friend not found");

                        int friendId = ((Number) friend.get("user_id")).intValue();
                        List<WishListModule.WishListEntry> entries = new ArrayList<>();
                        for (Map<String, Object> wl : DBConnection.getWishListsByUser(friendId)) {
                            int wlId = ((Number) wl.get("wishlist_id")).intValue();
                            for (Map<String, Object> it : DBConnection.getItemsByWishList(wlId)) {
                                entries.add(toEntry(it));
                            }
                        }
                        return ServerMain.Response.ok(req.getId()).put("data", (Serializable) entries);
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
                        if (total >= price) msg += " — Fully funded!";
                        DBConnection.addNotification(ownerId, msg);

                        return ServerMain.Response.ok(req.getId(), "Contribution recorded");
                    }
                    case GET_NOTIFICATIONS: {
                        List<Map<String, Object>> notifs = DBConnection.getNotifications(client.getUserId());
                        return ServerMain.Response.ok(req.getId()).put("data", (Serializable) notifs);
                    }
                    case MARK_NOTIFICATION_READ: {
                        int nid = req.get("notificationId");
                        DBConnection.markNotificationAsRead(nid);
                        return ServerMain.Response.ok(req.getId());
                    }
                    default:
                        return ServerMain.Response.error(req.getId(), req.getType() + " not implemented");
                }
            } catch (Exception e) {
                e.printStackTrace();
                return ServerMain.Response.error(req.getId(), "Server error: " + e.getMessage());
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

    public static class FriendRequestsFrame extends JFrame {
        private final JTextField receiverIdField;
        private final JPanel requestsPanel = new JPanel();

        public FriendRequestsFrame(int defaultUserId) {
            setTitle("i-Wish — Friend Requests");
            setSize(650, 450);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            receiverIdField = new JTextField(String.valueOf(defaultUserId), 10);
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

        private void loadRequests() {
            requestsPanel.removeAll();
            try {
                int receiverId = Integer.parseInt(receiverIdField.getText().trim());
                List<Map<String, Object>> reqs = DBConnection.getPendingRequests(receiverId);
                if (reqs.isEmpty()) {
                    requestsPanel.add(new JLabel("No pending friend requests."));
                } else {
                    for (Map<String, Object> r : reqs) {
                        addRequestRow(r);
                    }
                }
                requestsPanel.revalidate();
                requestsPanel.repaint();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Please enter a valid User ID.", "Input Error", JOptionPane.WARNING_MESSAGE);
            }
        }

        private void addRequestRow(Map<String, Object> r) {
            int requesterId = ((Number) r.get("user_id")).intValue();
            String senderName = (String) r.get("username");

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(new JLabel(senderName + " sent you a friend request."));
            JButton accept = new JButton("Accept");
            JButton decline = new JButton("Decline");

            accept.addActionListener(e -> {
                boolean ok = DBConnection.acceptFriendRequest(requesterId, Integer.parseInt(receiverIdField.getText().trim()));
                JOptionPane.showMessageDialog(this, ok ? "Friend request accepted!" : "Could not accept request.");
                loadRequests();
            });
            decline.addActionListener(e -> {
                DBConnection.declineFriendRequest(requesterId, Integer.parseInt(receiverIdField.getText().trim()));
                JOptionPane.showMessageDialog(this, "Friend request declined.");
                loadRequests();
            });

            row.add(accept);
            row.add(decline);
            requestsPanel.add(row);
        }
    }
}