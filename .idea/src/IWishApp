import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IWishApp {

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equalsIgnoreCase("server")) {
            new MockServer().start();
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                ServerConnection.getInstance().disconnect()));

        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }

    static final class Protocol {

        static final String REGISTER = "REGISTER";
        static final String LOGIN = "LOGIN";
        static final String ADD_FRIEND = "ADD_FRIEND";
        static final String REMOVE_FRIEND = "REMOVE_FRIEND";
        static final String ACCEPT_FRIEND = "ACCEPT_FRIEND";
        static final String DECLINE_FRIEND = "DECLINE_FRIEND";
        static final String GET_FRIENDS = "GET_FRIENDS";
        static final String GET_REQUESTS = "GET_REQUESTS";

        static final String OK = "OK";
        static final String ERR = "ERR";

        private static final String SEP = "|";

        private Protocol() {
        }

        static String build(String command, String... args) {
            StringBuilder sb = new StringBuilder(command);
            for (String arg : args) {
                sb.append(SEP).append(arg);
            }
            return sb.toString();
        }

        static boolean isOk(String response) {
            return response != null && response.startsWith(OK);
        }

        static String message(String response, String fallback) {
            if (response == null) return fallback;
            String[] parts = response.split("\\|", 2);
            return parts.length > 1 ? parts[1] : fallback;
        }
    }

    static class ServerConnection {

        private static final String HOST = "localhost";
        private static final int PORT = 5000;

        private static ServerConnection instance;

        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        private ServerConnection() {
        }

        static synchronized ServerConnection getInstance() {
            if (instance == null) {
                instance = new ServerConnection();
            }
            return instance;
        }

        void connect() throws IOException {
            if (socket != null && !socket.isClosed()) {
                return;
            }
            socket = new Socket(HOST, PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }

        synchronized String sendRequest(String request) throws IOException {
            if (socket == null || socket.isClosed()) {
                connect();
            }
            out.println(request);
            String response = in.readLine();
            if (response == null) {
                throw new IOException("No response from server");
            }
            return response;
        }

        void disconnect() {
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    static class LoginFrame extends JFrame {

        private final JTextField loginUsernameField = new JTextField(15);
        private final JPasswordField loginPasswordField = new JPasswordField(15);
        private final JButton loginButton = new JButton("Login");

        private final JTextField regUsernameField = new JTextField(15);
        private final JPasswordField regPasswordField = new JPasswordField(15);
        private final JPasswordField regConfirmPasswordField = new JPasswordField(15);
        private final JButton registerButton = new JButton("Register");

        LoginFrame() {
            super("i-Wish - Login");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(400, 320);
            setLocationRelativeTo(null);
            setResizable(false);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Login", buildLoginPanel());
            tabs.addTab("Register", buildRegisterPanel());

            add(tabs);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    ServerConnection.getInstance().disconnect();
                }
            });
        }

        private JPanel buildLoginPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            c.gridx = 0; c.gridy = 0;
            panel.add(new JLabel("Username:"), c);
            c.gridx = 1;
            panel.add(loginUsernameField, c);

            c.gridx = 0; c.gridy = 1;
            panel.add(new JLabel("Password:"), c);
            c.gridx = 1;
            panel.add(loginPasswordField, c);

            c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
            panel.add(loginButton, c);

            loginButton.addActionListener(e -> doLogin());
            loginPasswordField.addActionListener(e -> doLogin());

            return panel;
        }

        private JPanel buildRegisterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            c.gridx = 0; c.gridy = 0;
            panel.add(new JLabel("Username:"), c);
            c.gridx = 1;
            panel.add(regUsernameField, c);

            c.gridx = 0; c.gridy = 1;
            panel.add(new JLabel("Password:"), c);
            c.gridx = 1;
            panel.add(regPasswordField, c);

            c.gridx = 0; c.gridy = 2;
            panel.add(new JLabel("Confirm Password:"), c);
            c.gridx = 1;
            panel.add(regConfirmPasswordField, c);

            c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
            panel.add(registerButton, c);

            registerButton.addActionListener(e -> doRegister());

            return panel;
        }

        private void doLogin() {
            String username = loginUsernameField.getText().trim();
            String password = new String(loginPasswordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                showError("Please enter your username and password");
                return;
            }

            loginButton.setEnabled(false);

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws IOException {
                    return ServerConnection.getInstance()
                            .sendRequest(Protocol.build(Protocol.LOGIN, username, password));
                }

                @Override
                protected void done() {
                    loginButton.setEnabled(true);
                    try {
                        String response = get();
                        if (Protocol.isOk(response)) {
                            FriendsFrame friendsFrame = new FriendsFrame(username);
                            friendsFrame.setVisible(true);
                            dispose();
                        } else {
                            showError(Protocol.message(response, "Invalid username or password"));
                        }
                    } catch (Exception ex) {
                        showError("Cannot connect to the server. Make sure the server is running.");
                    }
                }
            };
            worker.execute();
        }

        private void doRegister() {
            String username = regUsernameField.getText().trim();
            String password = new String(regPasswordField.getPassword());
            String confirm = new String(regConfirmPasswordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                showError("Please enter your username and password");
                return;
            }
            if (!password.equals(confirm)) {
                showError("Passwords do not match");
                return;
            }

            registerButton.setEnabled(false);

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws IOException {
                    return ServerConnection.getInstance()
                            .sendRequest(Protocol.build(Protocol.REGISTER, username, password));
                }

                @Override
                protected void done() {
                    registerButton.setEnabled(true);
                    try {
                        String response = get();
                        if (Protocol.isOk(response)) {
                            JOptionPane.showMessageDialog(LoginFrame.this,
                                    "Account created successfully. You can log in now.",
                                    "Success", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            showError(Protocol.message(response, "There was a problem creating the account"));
                        }
                    } catch (Exception ex) {
                        showError("Cannot connect to the server. Make sure the server is running.");
                    }
                }
            };
            worker.execute();
        }

        private void showError(String msg) {
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static class FriendsFrame extends JFrame {

        private final String currentUsername;

        private final DefaultListModel<String> friendsModel = new DefaultListModel<>();
        private final DefaultListModel<String> requestsModel = new DefaultListModel<>();

        private final JList<String> friendsList = new JList<>(friendsModel);
        private final JList<String> requestsList = new JList<>(requestsModel);

        FriendsFrame(String username) {
            super("i-Wish - Welcome " + username);
            this.currentUsername = username;

            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(560, 420);
            setLocationRelativeTo(null);

            setLayout(new BorderLayout(10, 10));
            ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

            add(buildTopBar(), BorderLayout.NORTH);
            add(buildCenterPanel(), BorderLayout.CENTER);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    ServerConnection.getInstance().disconnect();
                }
            });

            refreshFriends();
            refreshRequests();
        }

        private JPanel buildTopBar() {
            JPanel top = new JPanel(new BorderLayout());
            JLabel welcome = new JLabel("Welcome, " + currentUsername);
            welcome.setFont(welcome.getFont().deriveFont(Font.BOLD, 16f));

            JButton logoutButton = new JButton("Logout");
            logoutButton.addActionListener(e -> logout());

            top.add(welcome, BorderLayout.WEST);
            top.add(logoutButton, BorderLayout.EAST);
            return top;
        }

        private JPanel buildCenterPanel() {
            JPanel center = new JPanel(new GridLayout(1, 2, 15, 0));
            center.add(buildFriendsPanel());
            center.add(buildRequestsPanel());
            return center;
        }

        private JPanel buildFriendsPanel() {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBorder(BorderFactory.createTitledBorder("My Friends"));

            panel.add(new JScrollPane(friendsList), BorderLayout.CENTER);

            JPanel buttons = new JPanel(new GridLayout(1, 3, 5, 0));
            JButton addButton = new JButton("Add Friend");
            JButton removeButton = new JButton("Remove Friend");
            JButton refreshButton = new JButton("Refresh");

            addButton.addActionListener(e -> addFriend());
            removeButton.addActionListener(e -> removeFriend());
            refreshButton.addActionListener(e -> refreshFriends());

            buttons.add(addButton);
            buttons.add(removeButton);
            buttons.add(refreshButton);

            panel.add(buttons, BorderLayout.SOUTH);
            return panel;
        }

        private JPanel buildRequestsPanel() {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBorder(BorderFactory.createTitledBorder("Incoming Friend Requests"));

            panel.add(new JScrollPane(requestsList), BorderLayout.CENTER);

            JPanel buttons = new JPanel(new GridLayout(1, 3, 5, 0));
            JButton acceptButton = new JButton("Accept");
            JButton declineButton = new JButton("Decline");
            JButton refreshButton = new JButton("Refresh");

            acceptButton.addActionListener(e -> respondToRequest(true));
            declineButton.addActionListener(e -> respondToRequest(false));
            refreshButton.addActionListener(e -> refreshRequests());

            buttons.add(acceptButton);
            buttons.add(declineButton);
            buttons.add(refreshButton);

            panel.add(buttons, BorderLayout.SOUTH);
            return panel;
        }

        private void addFriend() {
            String friendUsername = JOptionPane.showInputDialog(this,
                    "Enter the username you want to add:", "Add Friend",
                    JOptionPane.PLAIN_MESSAGE);

            if (friendUsername == null || friendUsername.trim().isEmpty()) {
                return;
            }
            String target = friendUsername.trim();

            if (target.equalsIgnoreCase(currentUsername)) {
                showError("You can't add yourself as a friend :)");
                return;
            }

            runRequest(
                    Protocol.build(Protocol.ADD_FRIEND, currentUsername, target),
                    response -> {
                        if (Protocol.isOk(response)) {
                            showInfo("Friend request sent to " + target);
                        } else {
                            showError(Protocol.message(response, "Could not add this friend"));
                        }
                    }
            );
        }

        private void removeFriend() {
            String selected = friendsList.getSelectedValue();
            if (selected == null) {
                showError("Please select a friend from the list first");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to remove " + selected + " from your friends?",
                    "Confirm Removal", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            runRequest(
                    Protocol.build(Protocol.REMOVE_FRIEND, currentUsername, selected),
                    response -> {
                        if (Protocol.isOk(response)) {
                            refreshFriends();
                        } else {
                            showError(Protocol.message(response, "Could not remove this friend"));
                        }
                    }
            );
        }

        private void respondToRequest(boolean accept) {
            String selected = requestsList.getSelectedValue();
            if (selected == null) {
                showError("Please select a request from the list first");
                return;
            }

            String command = accept ? Protocol.ACCEPT_FRIEND : Protocol.DECLINE_FRIEND;
            runRequest(
                    Protocol.build(command, currentUsername, selected),
                    response -> {
                        if (Protocol.isOk(response)) {
                            refreshRequests();
                            if (accept) refreshFriends();
                        } else {
                            showError(Protocol.message(response, "Something went wrong"));
                        }
                    }
            );
        }

        private void logout() {
            new LoginFrame().setVisible(true);
            dispose();
        }

        private void refreshFriends() {
            runRequest(
                    Protocol.build(Protocol.GET_FRIENDS, currentUsername),
                    response -> {
                        friendsModel.clear();
                        if (Protocol.isOk(response)) {
                            String data = Protocol.message(response, "");
                            if (!data.isEmpty()) {
                                for (String friend : data.split(",")) {
                                    friendsModel.addElement(friend);
                                }
                            }
                        }
                    }
            );
        }

        private void refreshRequests() {
            runRequest(
                    Protocol.build(Protocol.GET_REQUESTS, currentUsername),
                    response -> {
                        requestsModel.clear();
                        if (Protocol.isOk(response)) {
                            String data = Protocol.message(response, "");
                            if (!data.isEmpty()) {
                                for (String req : data.split(",")) {
                                    requestsModel.addElement(req);
                                }
                            }
                        }
                    }
            );
        }

        private interface ResponseHandler {
            void handle(String response);
        }

        private void runRequest(String request, ResponseHandler handler) {
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws IOException {
                    return ServerConnection.getInstance().sendRequest(request);
                }

                @Override
                protected void done() {
                    try {
                        handler.handle(get());
                    } catch (Exception ex) {
                        showError("Cannot connect to the server");
                    }
                }
            };
            worker.execute();
        }

        private void showError(String msg) {
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        }

        private void showInfo(String msg) {
            JOptionPane.showMessageDialog(this, msg, "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    static class MockServer {

        private static final int PORT = 5000;

        private final Map<String, String> users = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> friends = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> pendingRequests = new ConcurrentHashMap<>();

        void start() throws IOException {
            users.put("ahmed", "1234");
            users.put("sara", "1234");
            users.put("mona", "1234");

            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("MockServer running on port " + PORT);
            System.out.println("Test accounts ready: ahmed/1234 , sara/1234 , mona/1234");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
            }));

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        }

        private void handleClient(Socket socket) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String line;
                while ((line = in.readLine()) != null) {
                    out.println(handleCommand(line));
                }
            } catch (IOException e) {
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private synchronized String handleCommand(String line) {
            String[] parts = line.split("\\|");
            if (parts.length == 0) return Protocol.ERR + "|Unrecognized command";

            String command = parts[0];
            try {
                switch (command) {
                    case Protocol.REGISTER:
                        return register(parts[1], parts[2]);
                    case Protocol.LOGIN:
                        return login(parts[1], parts[2]);
                    case Protocol.ADD_FRIEND:
                        return addFriend(parts[1], parts[2]);
                    case Protocol.REMOVE_FRIEND:
                        return removeFriend(parts[1], parts[2]);
                    case Protocol.ACCEPT_FRIEND:
                        return acceptFriend(parts[1], parts[2]);
                    case Protocol.DECLINE_FRIEND:
                        return declineFriend(parts[1], parts[2]);
                    case Protocol.GET_FRIENDS:
                        return getFriends(parts[1]);
                    case Protocol.GET_REQUESTS:
                        return getRequests(parts[1]);
                    default:
                        return Protocol.ERR + "|Unknown command: " + command;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                return Protocol.ERR + "|Missing data in request";
            } catch (Exception e) {
                return Protocol.ERR + "|Unexpected server error";
            }
        }

        private String register(String username, String password) {
            if (users.containsKey(username)) {
                return Protocol.ERR + "|This username is already taken";
            }
            users.put(username, password);
            friends.put(username, ConcurrentHashMap.newKeySet());
            pendingRequests.put(username, ConcurrentHashMap.newKeySet());
            return Protocol.OK + "|Account created";
        }

        private String login(String username, String password) {
            if (!users.containsKey(username)) {
                return Protocol.ERR + "|This user does not exist";
            }
            if (!users.get(username).equals(password)) {
                return Protocol.ERR + "|Incorrect password";
            }
            return Protocol.OK + "|Login successful";
        }

        private String addFriend(String username, String targetUsername) {
            if (!users.containsKey(targetUsername)) {
                return Protocol.ERR + "|This user does not exist";
            }
            if (friends.getOrDefault(username, Collections.emptySet()).contains(targetUsername)) {
                return Protocol.ERR + "|You are already friends";
            }
            pendingRequests.computeIfAbsent(targetUsername, k -> ConcurrentHashMap.newKeySet())
                    .add(username);
            return Protocol.OK + "|Friend request sent";
        }

        private String removeFriend(String username, String targetUsername) {
            friends.getOrDefault(username, Collections.emptySet()).remove(targetUsername);
            friends.getOrDefault(targetUsername, Collections.emptySet()).remove(username);
            return Protocol.OK + "|Removed";
        }

        private String acceptFriend(String username, String requester) {
            Set<String> requests = pendingRequests.getOrDefault(username, Collections.emptySet());
            if (!requests.contains(requester)) {
                return Protocol.ERR + "|No request from this user";
            }
            requests.remove(requester);
            friends.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(requester);
            friends.computeIfAbsent(requester, k -> ConcurrentHashMap.newKeySet()).add(username);
            return Protocol.OK + "|Accepted";
        }

        private String declineFriend(String username, String requester) {
            pendingRequests.getOrDefault(username, Collections.emptySet()).remove(requester);
            return Protocol.OK + "|Declined";
        }

        private String getFriends(String username) {
            Set<String> userFriends = friends.getOrDefault(username, Collections.emptySet());
            return Protocol.OK + "|" + String.join(",", userFriends);
        }

        private String getRequests(String username) {
            Set<String> requests = pendingRequests.getOrDefault(username, Collections.emptySet());
            return Protocol.OK + "|" + String.join(",", requests);
        }
    }
}
