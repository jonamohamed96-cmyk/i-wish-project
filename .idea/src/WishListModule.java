import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class WishListModule {
    public static void main(String[] args) throws IOException {
        NetworkClient client = NetworkClient.connect("localhost", ServerMain.DEFAULT_PORT);
        WishListService service = new WishListService(client);

        JFrame frame = new JFrame("i-Wish - My Wish List");
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("My Wish List", new MyWishListPanel(service));

        FriendWishListPanel friendPanel = new FriendWishListPanel(service);
        tabs.addTab("Friend's Wish List", friendPanel);

        frame.add(tabs);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 420);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

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

        public synchronized ServerMain.Response send(ServerMain.Request request) {
            try {
                out.writeObject(request);
                out.flush();
                out.reset();
                while (true) {
                    Object obj = in.readObject();
                    if (!(obj instanceof ServerMain.Response)) {
                        return ServerMain.Response.error(request.getId(), "Malformed response from server.");
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
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static class WishListService {
        private final NetworkClient client;

        public WishListService(NetworkClient client) {
            this.client = client;
        }

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
                    .put("itemId", item.getId())
                    .put("note", note);
            return call(request).get("data");
        }

        public WishListEntry updateEntry(int entryId, String newNote) throws WishListException {
            ServerMain.Request request = new ServerMain.Request(ServerMain.RequestType.UPDATE_WISH_ITEM)
                    .put("entryId", entryId)
                    .put("note", newNote);
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