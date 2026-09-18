import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WishListModule
 * ----------------
 * Member 5's whole area in a single class: "Create, Update, Delete my Wish
 * List" and "View my Friends Wish List" (spec points 4 and 6), plus the
 * small pieces of shared plumbing (Item, Request/Response, NetworkClient)
 * needed to talk to the server, all as nested classes so the whole feature
 * lives in one file.
 *
 * Expected server-side commands (to agree with member 2 - Connections,
 * and member 3 - Business Logic):
 *  - "GET_CATALOG"          -> data: List<Item>
 *  - "GET_MY_WISHLIST"      -> data: List<WishListEntry>
 *  - "ADD_WISHLIST_ITEM"    params: itemId, note   -> data: WishListEntry
 *  - "UPDATE_WISHLIST_ITEM" params: entryId, note  -> data: WishListEntry
 *  - "DELETE_WISHLIST_ITEM" params: entryId        -> data: null
 *  - "GET_FRIEND_WISHLIST"  params: friendUsername -> data: List<WishListEntry>
 */
public class WishListModule {

    // ---------------------------------------------------------------
    // Demo entry point: opens a window with "My Wish List" and
    // "Friend's Wish List" tabs. Replace host/port with the real
    // server address once member 2's server is up.
    // ---------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        NetworkClient client = NetworkClient.connect("localhost", 5000);
        WishListService service = new WishListService(client);

        JFrame frame = new JFrame("i-Wish - My Wish List");
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("My Wish List", new MyWishListPanel(service));

        FriendWishListPanel friendPanel = new FriendWishListPanel(service);
        tabs.addTab("Friend's Wish List", friendPanel);
        // Example: friendPanel.showWishListOf("some_friend_username");

        frame.add(tabs);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 420);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // =================================================================
    // Model: a catalog item (added by admin / DB insertion - spec 12)
    // =================================================================
    public static class Item implements Serializable {
        private int id;
        private String name;
        private String description;
        private double price;
        private String imageUrl;

        public Item() {
        }

        public Item(int id, String name, String description, double price, String imageUrl) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.price = price;
            this.imageUrl = imageUrl;
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
        public String toString() {
            return name + " - $" + String.format("%.2f", price);
        }
    }

    // =================================================================
    // Model: one entry inside a wish list, with contribution progress
    // =================================================================
    public static class WishListEntry implements Serializable {
        private int entryId;
        private Item item;
        private String note;
        private double amountContributed;
        private boolean fullyFunded;

        public WishListEntry() {
        }

        public WishListEntry(int entryId, Item item, String note, double amountContributed) {
            this.entryId = entryId;
            this.item = item;
            this.note = note;
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

    // =================================================================
    // Shared plumbing: Request / Response / NetworkClient
    // =================================================================
    public static class Request implements Serializable {
        private final String command;
        private final Map<String, Object> params = new HashMap<>();

        public Request(String command) { this.command = command; }

        public Request set(String key, Object value) {
            params.put(key, value);
            return this;
        }

        public String getCommand() { return command; }
        public Object get(String key) { return params.get(key); }
    }

    public static class Response implements Serializable {
        private final boolean success;
        private final String message;
        private final Object data;

        public Response(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public static Response ok(Object data) { return new Response(true, "OK", data); }
        public static Response error(String message) { return new Response(false, message, null); }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
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
            if (instance == null) {
                instance = new NetworkClient(host, port);
            }
            return instance;
        }

        public static NetworkClient getInstance() {
            if (instance == null) {
                throw new IllegalStateException("Not connected yet - call NetworkClient.connect(host, port) first.");
            }
            return instance;
        }

        public synchronized Response send(Request request) {
            try {
                out.writeObject(request);
                out.flush();
                out.reset();
                Object response = in.readObject();
                if (response instanceof Response) {
                    return (Response) response;
                }
                return Response.error("Malformed response from server.");
            } catch (IOException | ClassNotFoundException e) {
                return Response.error("Connection problem: " + e.getMessage());
            }
        }

        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // =================================================================
    // Business logic: Create/Update/Delete my wish list, view a friend's
    // =================================================================
    public static class WishListService {
        private final NetworkClient client;

        public WishListService(NetworkClient client) {
            this.client = client;
        }

        @SuppressWarnings("unchecked")
        public List<Item> getCatalog() throws WishListException {
            Response response = client.send(new Request("GET_CATALOG"));
            if (!response.isSuccess()) throw new WishListException(response.getMessage());
            return (List<Item>) response.getData();
        }

        @SuppressWarnings("unchecked")
        public List<WishListEntry> getMyWishList() throws WishListException {
            Response response = client.send(new Request("GET_MY_WISHLIST"));
            if (!response.isSuccess()) throw new WishListException(response.getMessage());
            return (List<WishListEntry>) response.getData();
        }

        public WishListEntry addItem(Item item, String note) throws WishListException {
            Request request = new Request("ADD_WISHLIST_ITEM").set("itemId", item.getId()).set("note", note);
            Response response = client.send(request);
            if (!response.isSuccess()) throw new WishListException(response.getMessage());
            return (WishListEntry) response.getData();
        }

        public WishListEntry updateEntry(int entryId, String newNote) throws WishListException {
            Request request = new Request("UPDATE_WISHLIST_ITEM").set("entryId", entryId).set("note", newNote);
            Response response = client.send(request);
            if (!response.isSuccess()) throw new WishListException(response.getMessage());
            return (WishListEntry) response.getData();
        }

        public void deleteEntry(int entryId) throws WishListException {
            Request request = new Request("DELETE_WISHLIST_ITEM").set("entryId", entryId);
            Response response = client.send(request);
            if (!response.isSuccess()) throw new WishListException(response.getMessage());
        }

        @SuppressWarnings("unchecked")
        public List<WishListEntry> getFriendWishList(String friendUsername) throws WishListException {
            Request request = new Request("GET_FRIEND_WISHLIST").set("friendUsername", friendUsername);
            Response response = client.send(request);
            if (!response.isSuccess()) throw new WishListException(response.getMessage());
            List<WishListEntry> data = (List<WishListEntry>) response.getData();
            return data != null ? data : new ArrayList<>();
        }

        public static class WishListException extends Exception {
            public WishListException(String message) { super(message); }
        }
    }

    // =================================================================
    // GUI: dialog to add/edit a wish list entry
    // =================================================================
    public static class AddEditWishItemDialog extends JDialog {
        private JComboBox<Item> catalogCombo;
        private JTextArea noteArea;
        private boolean confirmed = false;
        private Item selectedItem;
        private String note;

        public AddEditWishItemDialog(Window owner, List<Item> catalog) {
            super(owner, "Add item to my wish list", ModalityType.APPLICATION_MODAL);
            buildUi(catalog, null);
        }

        public AddEditWishItemDialog(Window owner, WishListEntry existingEntry) {
            super(owner, "Edit wish list entry", ModalityType.APPLICATION_MODAL);
            buildUi(null, existingEntry);
        }

        private void buildUi(List<Item> catalog, WishListEntry existingEntry) {
            setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(8, 8, 8, 8);
            c.fill = GridBagConstraints.HORIZONTAL;

            c.gridx = 0; c.gridy = 0;
            add(new JLabel("Item:"), c);

            c.gridx = 1;
            if (catalog != null) {
                catalogCombo = new JComboBox<>(catalog.toArray(new Item[0]));
                add(catalogCombo, c);
            } else {
                JLabel fixedItem = new JLabel(existingEntry.getItem().toString());
                fixedItem.setFont(fixedItem.getFont().deriveFont(Font.BOLD));
                add(fixedItem, c);
                selectedItem = existingEntry.getItem();
            }

            c.gridx = 0; c.gridy = 1;
            add(new JLabel("Note:"), c);

            c.gridx = 1;
            noteArea = new JTextArea(existingEntry != null ? existingEntry.getNote() : "", 3, 20);
            add(new JScrollPane(noteArea), c);

            JButton saveButton = new JButton("Save");
            JButton cancelButton = new JButton("Cancel");
            saveButton.addActionListener(e -> onSave());
            cancelButton.addActionListener(e -> dispose());

            JPanel buttons = new JPanel();
            buttons.add(saveButton);
            buttons.add(cancelButton);

            c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
            add(buttons, c);

            pack();
            setLocationRelativeTo(getOwner());
        }

        private void onSave() {
            if (catalogCombo != null) {
                selectedItem = (Item) catalogCombo.getSelectedItem();
            }
            if (selectedItem == null) {
                JOptionPane.showMessageDialog(this, "Please choose an item.", "Missing item", JOptionPane.WARNING_MESSAGE);
                return;
            }
            note = noteArea.getText().trim();
            confirmed = true;
            dispose();
        }

        public boolean isConfirmed() { return confirmed; }
        public Item getSelectedItem() { return selectedItem; }
        public String getNote() { return note; }
    }

    // =================================================================
    // GUI: my own wish list panel (Create, Update, Delete)
    // =================================================================
    public static class MyWishListPanel extends JPanel {
        private final WishListService wishListService;
        private final DefaultTableModel tableModel;
        private final JTable table;

        public MyWishListPanel(WishListService wishListService) {
            this.wishListService = wishListService;
            setLayout(new BorderLayout(8, 8));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("My Wish List");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
            add(title, BorderLayout.NORTH);

            tableModel = new DefaultTableModel(new Object[]{"Item", "Price", "Note", "Funded", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            table = new JTable(tableModel);
            add(new JScrollPane(table), BorderLayout.CENTER);

            JButton addButton = new JButton("Add item");
            JButton editButton = new JButton("Edit note");
            JButton deleteButton = new JButton("Delete");
            JButton refreshButton = new JButton("Refresh");

            addButton.addActionListener(e -> onAdd());
            editButton.addActionListener(e -> onEdit());
            deleteButton.addActionListener(e -> onDelete());
            refreshButton.addActionListener(e -> refresh());

            JPanel buttons = new JPanel();
            buttons.add(addButton);
            buttons.add(editButton);
            buttons.add(deleteButton);
            buttons.add(refreshButton);
            add(buttons, BorderLayout.SOUTH);

            refresh();
        }

        public void refresh() {
            try {
                List<WishListEntry> entries = wishListService.getMyWishList();
                tableModel.setRowCount(0);
                for (WishListEntry entry : entries) {
                    tableModel.addRow(new Object[]{
                            entry.getItem().getName(),
                            String.format("$%.2f", entry.getItem().getPrice()),
                            entry.getNote(),
                            String.format("$%.2f", entry.getAmountContributed()),
                            entry.isFullyFunded() ? "Fully funded \u2714" : "In progress"
                    });
                    table.putClientProperty("entry_" + (tableModel.getRowCount() - 1), entry);
                }
            } catch (WishListService.WishListException e) {
                showError(e.getMessage());
            }
        }

        private void onAdd() {
            try {
                List<Item> catalog = wishListService.getCatalog();
                if (catalog.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "The catalog has no items yet.");
                    return;
                }
                AddEditWishItemDialog dialog = new AddEditWishItemDialog(SwingUtilities.getWindowAncestor(this), catalog);
                dialog.setVisible(true);
                if (dialog.isConfirmed()) {
                    wishListService.addItem(dialog.getSelectedItem(), dialog.getNote());
                    refresh();
                }
            } catch (WishListService.WishListException e) {
                showError(e.getMessage());
            }
        }

        private void onEdit() {
            WishListEntry selected = getSelectedEntry();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select an entry to edit first.");
                return;
            }
            AddEditWishItemDialog dialog = new AddEditWishItemDialog(SwingUtilities.getWindowAncestor(this), selected);
            dialog.setVisible(true);
            if (dialog.isConfirmed()) {
                try {
                    wishListService.updateEntry(selected.getEntryId(), dialog.getNote());
                    refresh();
                } catch (WishListService.WishListException e) {
                    showError(e.getMessage());
                }
            }
        }

        private void onDelete() {
            WishListEntry selected = getSelectedEntry();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select an entry to delete first.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove \"" + selected.getItem().getName() + "\" from your wish list?",
                    "Confirm delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    wishListService.deleteEntry(selected.getEntryId());
                    refresh();
                } catch (WishListService.WishListException e) {
                    showError(e.getMessage());
                }
            }
        }

        private WishListEntry getSelectedEntry() {
            int row = table.getSelectedRow();
            if (row < 0) return null;
            return (WishListEntry) table.getClientProperty("entry_" + row);
        }

        private void showError(String message) {
            JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =================================================================
    // GUI: read-only view of a friend's wish list
    // =================================================================
    public static class FriendWishListPanel extends JPanel {
        private final WishListService wishListService;
        private final DefaultTableModel tableModel;

        public FriendWishListPanel(WishListService wishListService) {
            this.wishListService = wishListService;
            setLayout(new BorderLayout(8, 8));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("Friend's Wish List");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
            add(title, BorderLayout.NORTH);

            tableModel = new DefaultTableModel(new Object[]{"Item", "Price", "Note", "Still needed"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            JTable table = new JTable(tableModel);
            add(new JScrollPane(table), BorderLayout.CENTER);
        }

        public void showWishListOf(String friendUsername) {
            try {
                List<WishListEntry> entries = wishListService.getFriendWishList(friendUsername);
                tableModel.setRowCount(0);
                for (WishListEntry entry : entries) {
                    String remaining = entry.isFullyFunded()
                            ? "Fully funded \u2714"
                            : String.format("$%.2f", entry.getRemainingAmount());
                    tableModel.addRow(new Object[]{
                            entry.getItem().getName(),
                            String.format("$%.2f", entry.getItem().getPrice()),
                            entry.getNote(),
                            remaining
                    });
                }
                if (entries.isEmpty()) {
                    JOptionPane.showMessageDialog(this, friendUsername + " has no items on their wish list yet.");
                }
            } catch (WishListService.WishListException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}