import iwish.DBConnection;

import javax.swing.*;
import java.util.*;

import java.awt.*;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ServerMain {
    public static final int DEFAULT_PORT = 5005;

    // ======================= RequestType =======================
    public enum RequestType {
        REGISTER, LOGIN, LOGOUT,
        SEND_FRIEND_REQUEST, ACCEPT_FRIEND_REQUEST, DECLINE_FRIEND_REQUEST,
        REMOVE_FRIEND, GET_FRIENDS, GET_FRIEND_REQUESTS,
        GET_ALL_ITEMS, GET_MY_WISHLIST, ADD_WISH_ITEM, UPDATE_WISH_ITEM,
        DELETE_WISH_ITEM, GET_FRIEND_WISHLIST,
        CONTRIBUTE, GET_NOTIFICATIONS, MARK_NOTIFICATION_READ
    }

    // ========================= Request =========================
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

    // ========================= Response ========================
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

        public static Response ok(long requestId)                    { return new Response(Kind.REPLY, requestId, true, "OK", null); }
        public static Response ok(long requestId, String message)    { return new Response(Kind.REPLY, requestId, true, message, null); }
        public static Response error(long requestId, String message) { return new Response(Kind.REPLY, requestId, false, message, null); }
        public static Response push(String event, String message)    { return new Response(Kind.PUSH, 0, true, message, event); }

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

    // ======================= RequestHandler ====================
    public interface RequestHandler {
        Response handle(Request request, ClientHandler client) throws Exception;
    }

    // ======================= Server fields =====================
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

    // ======================= ClientHandler =====================
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

    // =================== DefaultRequestHandler =================
    public static class DefaultRequestHandler implements RequestHandler {
        @Override
        public Response handle(Request request, ClientHandler client) {
            switch (request.getType()) {
                case LOGIN:
                    String u = request.get("username");
                    client.login(Math.abs(String.valueOf(u).hashCode()), String.valueOf(u));
                    return Response.ok(request.getId(), "Demo login as " + u);
                case LOGOUT:
                    client.logout();
                    return Response.ok(request.getId(), "Logged out");
                case GET_MY_WISHLIST: {
                    if (!client.isLoggedIn()) {
                        return Response.error(request.getId(), "Not logged in");
                    }
                    int userId = client.getUserId();
                    List<Map<String, Object>> myWishlists = DBConnection.getWishListsByUser(userId);
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Map<String, Object> wl : myWishlists) {
                        int wishlistId = (int) wl.get("wishlist_id");
                        List<Map<String, Object>> items = DBConnection.getItemsByWishList(wishlistId);
                        Map<String, Object> wlWithItems = new LinkedHashMap<>(wl);
                        wlWithItems.put("items", items);
                        result.add(wlWithItems);
                    }
                    return Response.ok(request.getId())
                            .put("wishlists", (java.io.Serializable) result);
                }


                default:
                    return Response.error(request.getId(), request.getType() + " not implemented yet");
            }
        }
    }

    // ============================ GUI ==========================
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
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerMain(new DefaultRequestHandler()).showGui());
    }
}
