import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Stack;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/**
 * SmartLibraryGUI — Java Swing GUI for the Smart Library System.
 *
 * ┌─ Design principles enforced ──────────────────────────────────────┐
 * │  Information Hiding : ALL library calls go through LibraryADT.   │
 * │                        LibraryImpl is NEVER referenced after      │
 * │                        construction (not even via instanceof).    │
 * │  EDT compliance     : All Swing mutations happen on the Event     │
 * │                        Dispatch Thread via invokeLater().         │
 * │  Separation of      : This class handles display only; all data   │
 * │  concerns             logic stays inside LibraryImpl.             │
 * └───────────────────────────────────────────────────────────────────┘
 *
 * v1.3 — Data Persistence additions:
 *   Shutdown : setDefaultCloseOperation changed to DO_NOTHING_ON_CLOSE.
 *              A WindowListener intercepts the close event, calls
 *              library.saveLibraryState() to persist the BST and history
 *              stack, then terminates cleanly via dispose() + System.exit(0).
 *   Startup  : main() checks for "current_catalogue.txt" and loads it when
 *              present; falls back to "books.txt" on first run.
 *              After preloadHistory(), syncHistoryTableFromStack() rebuilds
 *              the GUI history table to match the restored stack exactly.
 *
 * Layout overview:
 *   NORTH  — dark header bar (title + subtitle)
 *   CENTER — JSplitPane
 *              LEFT  : scrollable stack of operation cards
 *                        [Add Book | Search Book | Borrow Book]
 *              RIGHT : GridLayout 2 × 1
 *                        [System Console (terminal-style)]
 *                        [Borrowing History table (LIFO)
 *                           + Return Latest Book button]
 *   SOUTH  — status bar
 *
 * @author Senior Java Engineer
 * @version 2.2
 */
public class SmartLibraryGUI extends JFrame {

    // RBAC flag
    private boolean isLibrarian = false;
    // UserID
    private String currentUserID = null;

    // ── Backend — interface-typed for Information Hiding ──────────────────────
    private final LibraryADT library;

    // ── Persistence file paths ─────────────────────────────────────────────────
    private static final String SAVED_CATALOGUE = "current_catalogue.txt";
    private static final String SAVED_HISTORY   = "current_history.txt";
    private static final String DEFAULT_CATALOGUE = "books.txt";

    // ── Colour palette ─────────────────────────────────────────────────────────
    private static final Color C_HEADER  = new Color( 28,  40,  51);
    private static final Color C_GREEN   = new Color( 39, 174,  96);
    private static final Color C_BLUE    = new Color( 41, 128, 185);
    private static final Color C_RED     = new Color(192,  57,  43);
    private static final Color C_ORANGE  = new Color(175,  76,   0);
    private static final Color C_BG      = new Color(245, 246, 250);
    private static final Color C_CARD    = Color.WHITE;
    private static final Color C_TEXT    = new Color( 44,  62,  80);
    private static final Color C_MUTED   = new Color(127, 140, 141);
    private static final Color C_BORDER  = new Color(220, 221, 225);
    private static final Color C_CON_BG  = new Color( 18,  26,  34);
    private static final Color C_CON_FG  = new Color( 46, 204, 113);
    private static final Color C_ROW_ALT = new Color(248, 249, 251);

    // ── Add Book widgets ───────────────────────────────────────────────────────
    private JTextField fAddIsbn;
    private JTextField fAddTitle;
    private JTextField fAddAuthor;
    private JTextField fDeleteIsbn;

    // ── Search Book widgets ────────────────────────────────────────────────────
    private JTextField fSearchIsbn;
    private JPanel     pSearchResult;
    private JLabel     lSearchTitle;
    private JLabel     lSearchAuthor;

    // ── Borrow Book widgets ────────────────────────────────────────────────────
    private JTextField fBorrowIsbn;

    // ── Borrowing History table ────────────────────────────────────────────────
    private DefaultTableModel historyModel;
    private int               borrowSeq = 0;

    // ── Console output (System.out sink) ──────────────────────────────────────
    private JTextArea consoleArea;

    // ── Status bar ─────────────────────────────────────────────────────────────
    private JLabel statusLabel;

    // ==========================================================================
    //  CONSTRUCTOR
    // ==========================================================================

    /**
     * Creates and displays the GUI.
     *
     * Lifecycle note: preloading and history sync are performed by the caller
     * (main()) AFTER construction so that the System.out redirect installed by
     * redirectSystemOut() is already active when library data is loaded —
     * ensuring all load messages appear in the GUI console, not the terminal.
     *
     * @param library A LibraryADT implementation. The concrete type is never
     *                inspected here — full information hiding is maintained.
     */
    public SmartLibraryGUI(LibraryADT library) {
        this.library = library;

        // Login-based Authorization
        if (!authenticate()) {
            System.exit(0);
        }

        redirectSystemOut();   // must run before buildFrame() and before preloading
        buildFrame();

        String roleStr = isLibrarian? "Librarian Mode" : "Borrower Mode";
        log("▶  Smart Library System started (" + roleStr + ") — restoring session...");
    }

    // ==========================================================================
    //  SYSTEM.OUT → GUI CONSOLE REDIRECT
    // ==========================================================================

    /**
     * Replaces System.out with a custom PrintStream that appends every byte
     * written by LibraryImpl into the console JTextArea instead of the terminal.
     * Called before buildFrame() so the JTextArea exists when the first
     * character arrives.
     */
    private void redirectSystemOut() {
        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consoleArea.setBackground(C_CON_BG);
        consoleArea.setForeground(C_CON_FG);
        consoleArea.setCaretColor(C_CON_FG);
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);

        OutputStream sink = new OutputStream() {
            @Override public void write(int b) {
                push(String.valueOf((char) b));
            }
            @Override public void write(byte[] b, int off, int len) {
                push(new String(b, off, len));
            }
            private void push(String s) {
                SwingUtilities.invokeLater(() -> {
                    consoleArea.append(s);
                    consoleArea.setCaretPosition(
                        consoleArea.getDocument().getLength());
                });
            }
        };

        System.setOut(new PrintStream(sink, /*autoFlush=*/ true));
    }

    // ==========================================================================
    //  FRAME CONSTRUCTION
    // ==========================================================================

    private void buildFrame() {
        setTitle("Smart Library System");

        // ── DO_NOTHING_ON_CLOSE: we handle the close event ourselves so we
        //    can save library state before the JVM exits.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // ── Window close hook: save state, then terminate cleanly ─────────────
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log("[System] Saving library state before exit...");
                library.saveLibraryState(SAVED_CATALOGUE, SAVED_HISTORY);
                log("[System] State saved. Goodbye!");
                // Give the EDT a moment to flush the final log messages,
                // then dispose and exit.
                SwingUtilities.invokeLater(() -> {
                    dispose();
                    System.exit(0);
                });
            }
        });

        setLayout(new BorderLayout());
        getContentPane().setBackground(C_BG);

        add(buildHeader(),    BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setSize(1150, 740);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ─── Header ───────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_HEADER);
        p.setBorder(new EmptyBorder(15, 24, 15, 24));

        JLabel titleLbl = new JLabel("📚  Smart Library System");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 22));
        titleLbl.setForeground(Color.WHITE);

        JLabel subLbl = new JLabel(
            "BST Book Catalogue  •  Stack Borrowing History  •  ADT Interface  •  Session Persistence");
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLbl.setForeground(new Color(189, 195, 199));

        JPanel textStack = new JPanel();
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.setOpaque(false);
        textStack.add(titleLbl);
        textStack.add(vGap(3));
        textStack.add(subLbl);

        p.add(textStack, BorderLayout.WEST);
        return p;
    }

    // ─── Center split pane ────────────────────────────────────────────────────
    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildLeftPanel(),
            buildRightPanel()
        );
        split.setDividerLocation(410);
        split.setDividerSize(5);
        split.setBorder(null);
        return split;
    }

    // ─── Left: scrollable stack of operation cards ────────────────────────────
    private JScrollPane buildLeftPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(C_BG);
        container.setBorder(new EmptyBorder(14, 14, 14, 7));

        if(isLibrarian){
            //Librarians' Panels
            container.add(buildAddCard());
            container.add(vGap(12));
            container.add(buildDeleteCard());
            container.add(vGap(12));
            container.add(buildSearchCard());

        }else{
            //Borrowers' Panels
            container.add(buildSearchCard());
            container.add(vGap(12));
            container.add(buildBorrowCard());
        }
        
        container.add(Box.createGlue());

        JScrollPane scroll = new JScrollPane(container,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ─── Right: console + history stacked ─────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 12));
        p.setBackground(C_BG);
        p.setBorder(new EmptyBorder(14, 7, 14, 14));
        p.add(buildConsoleCard());
        p.add(buildHistoryCard());
        return p;
    }

    // ==========================================================================
    //  OPERATION CARDS  (left side)
    // ==========================================================================

    // ─── Add Book ─────────────────────────────────────────────────────────────
    private JPanel buildAddCard() {
        JPanel card = makeCard(C_GREEN);

        fAddIsbn   = makeField("e.g.  1001");
        fAddTitle  = makeField("e.g.  The Great Gatsby");
        fAddAuthor = makeField("e.g.  F. Scott Fitzgerald");

        JButton btn = makeButton("＋  Add Book / Stock", C_GREEN);
        btn.addActionListener(e -> handleAddBook());

        card.add(makeSectionTitle("Add Book", C_GREEN));
        card.add(vGap(10));
        card.add(makeFieldRow("ISBN",   fAddIsbn));
        card.add(vGap(7));
        card.add(makeFieldRow("Title",  fAddTitle));
        card.add(vGap(7));
        card.add(makeFieldRow("Author", fAddAuthor));
        card.add(vGap(12));
        card.add(btn);

        return card;
    }

    // ─── Search Book ──────────────────────────────────────────────────────────
    private JPanel buildSearchCard() {
        JPanel card = makeCard(C_BLUE);

        fSearchIsbn = makeField("Enter ISBN to look up");

        JButton btn = makeButton("🔍  Search", C_BLUE);
        btn.addActionListener(e -> handleSearch());

        // Result box — hidden until the first search
        pSearchResult = new JPanel();
        pSearchResult.setLayout(new BoxLayout(pSearchResult, BoxLayout.Y_AXIS));
        pSearchResult.setAlignmentX(LEFT_ALIGNMENT);
        pSearchResult.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        pSearchResult.setBorder(new EmptyBorder(8, 10, 8, 10));
        pSearchResult.setVisible(false);

        lSearchTitle  = new JLabel(" ");
        lSearchAuthor = new JLabel(" ");
        lSearchTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        lSearchAuthor.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lSearchTitle.setAlignmentX(LEFT_ALIGNMENT);
        lSearchAuthor.setAlignmentX(LEFT_ALIGNMENT);

        pSearchResult.add(lSearchTitle);
        pSearchResult.add(vGap(4));
        pSearchResult.add(lSearchAuthor);

        card.add(makeSectionTitle("Search Book", C_BLUE));
        card.add(vGap(10));
        card.add(makeFieldRow("ISBN", fSearchIsbn));
        card.add(vGap(10));
        card.add(btn);
        card.add(vGap(10));
        card.add(pSearchResult);

        return card;
    }

    // ─── Borrow Book ──────────────────────────────────────────────────────────
    private JPanel buildBorrowCard() {
        JPanel card = makeCard(C_RED);

        fBorrowIsbn = makeField("Enter ISBN to borrow");

        JButton btn = makeButton("📖  Borrow Book", C_RED);
        btn.addActionListener(e -> handleBorrow());

        card.add(makeSectionTitle("Borrow Book", C_RED));
        card.add(vGap(10));
        card.add(makeFieldRow("ISBN", fBorrowIsbn));
        card.add(vGap(12));
        card.add(btn);

        return card;
    }

    // ─── Delete Book (For Librarians) ─────────────────────────────────────────
    private JPanel buildDeleteCard() {
        // 借用橙色作为删除警示色
        JPanel card = makeCard(C_ORANGE);

        fDeleteIsbn = makeField("Enter ISBN to delete");

        JButton btn = makeButton("🗑  Delete Book", C_ORANGE);
        btn.addActionListener(e -> handleDeleteBook());

        card.add(makeSectionTitle("Delete Book", C_ORANGE));
        card.add(vGap(10));
        card.add(makeFieldRow("ISBN", fDeleteIsbn));
        card.add(vGap(12));
        card.add(btn);

        return card;
    }

    // ==========================================================================
    //  OUTPUT CARDS  (right side)
    // ==========================================================================

    // ─── System Console ───────────────────────────────────────────────────────
    private JPanel buildConsoleCard() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(C_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(C_BORDER),
            new EmptyBorder(12, 14, 12, 14)));

        JLabel title = new JLabel("System Console");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(C_TEXT);

        JButton clearBtn = makeLinkButton("Clear");
        clearBtn.addActionListener(e -> consoleArea.setText(""));

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.add(title,    BorderLayout.WEST);
        hdr.add(clearBtn, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(consoleArea);
        scroll.setBorder(BorderFactory.createLineBorder(C_BORDER));

        card.add(hdr,    BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    // ─── Borrowing History Table ───────────────────────────────────────────────
    private JPanel buildHistoryCard() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(C_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(C_BORDER),
            new EmptyBorder(12, 14, 12, 14)));

        // ── Row 1: title label + "Print to Console" link ──────────────────────
        JLabel titleLbl = new JLabel("Borrowing History  (LIFO — newest first)");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLbl.setForeground(C_TEXT);

        JButton printBtn = makeLinkButton("⟳ Print to Console");
        printBtn.addActionListener(e -> {
            library.viewLatestHistory(isLibrarian ? null : currentUserID); // Print all history for librarians, filter by user for borrowers
            setStatus("History printed to console.");
        });

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.add(titleLbl, BorderLayout.WEST);
        hdr.add(printBtn, BorderLayout.EAST);

        // ── Row 2: Return by ISBN area ─────────────────────────────────────
        JPanel returnPanel = new JPanel(new BorderLayout(5, 0));
        returnPanel.setOpaque(false);

        JLabel returnLabel = new JLabel("Return Book by ISBN:");
        returnLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JTextField returnIsbnField = new JTextField();
        returnIsbnField.setColumns(10);

        JButton returnBtn = new JButton("Return");
        returnBtn.addActionListener(e -> handleReturnByIsbn(returnIsbnField));

        returnPanel.add(returnLabel, BorderLayout.WEST);
        returnPanel.add(returnIsbnField, BorderLayout.CENTER);
        returnPanel.add(returnBtn, BorderLayout.EAST);

        JPanel northPanel = new JPanel(new BorderLayout(0, 8));
        northPanel.setOpaque(false);
        northPanel.add(hdr,         BorderLayout.NORTH);
        if (!isLibrarian) {
            northPanel.add(returnPanel, BorderLayout.CENTER);
        }

        // ── Non-editable table model ───────────────────────────────────────────
        historyModel = new DefaultTableModel(
                new String[]{"#", "Title", "Author", "ISBN", "Borrower ID"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        JTable table = new JTable(historyModel);
        styleHistoryTable(table);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(C_BORDER));

        card.add(northPanel, BorderLayout.NORTH);
        card.add(scroll,     BorderLayout.CENTER);
        return card;
    }

    /** Applies all visual styling to the history JTable. */
    private void styleHistoryTable(JTable table) {
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setRowHeight(27);
        table.setShowGrid(true);
        table.setGridColor(new Color(235, 237, 239));
        table.setBackground(C_CARD);
        table.setForeground(C_TEXT);
        table.setSelectionBackground(new Color(207, 226, 255));
        table.setSelectionForeground(C_TEXT);
        table.setIntercellSpacing(new Dimension(8, 2));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setPreferredSize(new Dimension(0, 30));
        header.setReorderingAllowed(false);
        header.setResizingAllowed(true);

        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setBackground(C_HEADER);
                setForeground(Color.WHITE);
                setFont(new Font("SansSerif", Font.BOLD, 12));
                setHorizontalAlignment(JLabel.LEFT);
                setBorder(BorderFactory.createMatteBorder(
                    0, 0, 1, 1, new Color(52, 73, 94)));
                return this;
            }
        });

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(32);
        table.getColumnModel().getColumn(0).setMaxWidth(42);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setMaxWidth(72);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);

        // Alternating row colours + left-padded cell text
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                if (!sel) {
                    setBackground(row % 2 == 0 ? C_CARD : C_ROW_ALT);
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return this;
            }
        });
    }

    // ─── Status bar ───────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(C_HEADER);
        bar.setBorder(new EmptyBorder(5, 16, 5, 16));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(189, 195, 199));

        JLabel hint = new JLabel(
            "All operations via LibraryADT interface only  •  Information Hiding ✔  •  Auto-save on exit ✔");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 11));
        hint.setForeground(C_MUTED);

        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(hint,        BorderLayout.EAST);
        return bar;
    }

    // ==========================================================================
    //  STARTUP SYNC — restore GUI table from loaded history stack
    // ==========================================================================

    /**
     * Synchronises the GUI history table with the items currently sitting in
     * the library's history stack. Must be called from main() after both
     * preloadData() and preloadHistory() have completed.
     *
     * Iteration strategy — bottom to top (index 0 → size-1), inserting each
     * record at table row 0:
     *   After the loop, the last item inserted is the stack top (newest borrow)
     *   which correctly lands at row 0, matching the LIFO display contract.
     *
     * Example with stack [A(bottom), B, C(top)]:
     *   Insert A at row 0  → table: [A]
     *   Insert B at row 0  → table: [B, A]
     *   Insert C at row 0  → table: [C, B, A]   ← C (newest) visible at top ✓
     *
     * borrowSeq is reset and rebuilt here so the "#" column stays consistent
     * with any borrows / returns performed after startup.
     */
    void syncHistoryTableFromStack() {
    Stack<BorrowHistory> stack = library.getHistoryStack();
    // 无论栈是否为空，都要先清空表格 clear whether it is blank or not
    historyModel.setRowCount(0);
    borrowSeq = 0;
    
    if (stack == null || stack.isEmpty()) {
        // 栈为空，表格已经是空的，直接返回 if it's blank just return
        log("[Sync] History stack is empty, table cleared.");
        return;
    }
    
    // 从栈底到栈顶插入到表格第0行，保持 LIFO 顺序 from the lower to the top, keep LIFO sequence
    for (int i = 0; i < stack.size(); i++) {
        BorrowHistory record = stack.get(i);

        // RBAC: Borrowers only see their own history; librarians see all history 
        if(!isLibrarian && !currentUserID.equals(record.getBID())){
            continue;
        }
        borrowSeq++;
        historyModel.insertRow(0, new Object[]{
            borrowSeq,
            record.getTitle(),
            record.getAuthor(),
            record.getIsbn(),
            record.getBID(),
        });
    }
    log("[Sync] Restored " + stack.size() + " history record(s) to the table.");
}

    // ==========================================================================
    //  ACTION HANDLERS
    //  Every library operation calls a method on LibraryADT ONLY.
    //  LibraryImpl is NEVER mentioned here.
    // ==========================================================================

    /**
     * Handles the "Add to Library" button click.
     * Validates ISBN, title, and author; then delegates to library.addBook().
     */
    private void handleAddBook() {
        String isbnRaw = fieldValue(fAddIsbn);
        String title   = fieldValue(fAddTitle);
        String author  = fieldValue(fAddAuthor);

        // ── Validation 1: empty fields ─────────────────────────────────────
        if (isbnRaw.isEmpty() || title.isEmpty() || author.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "All three fields — ISBN, Title, and Author — must be filled in.",
                "Missing Input",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── Validation 2: ISBN must be a valid integer ─────────────────────
        int isbn;
        try {
            isbn = Integer.parseInt(isbnRaw);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "ISBN must be a whole number (e.g. 1001).\n"
                + "\"" + isbnRaw + "\" is not a valid integer.",
                "Invalid ISBN",
                JOptionPane.ERROR_MESSAGE);
            fAddIsbn.selectAll();
            fAddIsbn.requestFocus();
            return;
        }

        library.addBook(isbn, title, author, 1);     // ← LibraryADT only

        log("[Add]    ISBN " + isbn + " → " + title + " by " + author);
        setStatus("Added: " + title + "  (ISBN " + isbn + ")");

        clearField(fAddIsbn);
        clearField(fAddTitle);
        clearField(fAddAuthor);
        fAddIsbn.requestFocus();
    }

    /**
     * Handles the "Search" button click.
     * Validates ISBN; calls library.searchBook() and shows the result inline.
     */
    private void handleSearch() {
        String isbnRaw = fieldValue(fSearchIsbn);

        if (isbnRaw.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter an ISBN number to search for.",
                "Missing Input",
                JOptionPane.WARNING_MESSAGE);
            fSearchIsbn.requestFocus();
            return;
        }

        int isbn;
        try {
            isbn = Integer.parseInt(isbnRaw);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "ISBN must be a whole number.\n"
                + "\"" + isbnRaw + "\" is not a valid integer.",
                "Invalid ISBN",
                JOptionPane.ERROR_MESSAGE);
            fSearchIsbn.selectAll();
            fSearchIsbn.requestFocus();
            return;
        }

        Book found = library.searchBook(isbn);    // ← LibraryADT only

        if (found != null) {
            lSearchTitle.setText("📗  " + found.getTitle());
            lSearchAuthor.setText("✍   " + found.getAuthor()
                                  + "  (ISBN: " + isbn + ")");
            lSearchTitle.setForeground(new Color(27, 94, 32));
            lSearchAuthor.setForeground(C_MUTED);
            pSearchResult.setBackground(new Color(232, 245, 233));
            pSearchResult.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(165, 214, 167)),
                new EmptyBorder(8, 10, 8, 10)));

            log("[Search] Found ISBN " + isbn + ": "
                + found.getTitle() + " by " + found.getAuthor());
            setStatus("Found: " + found.getTitle());
        } else {
            lSearchTitle.setText("✖  No book found for ISBN: " + isbn);
            lSearchAuthor.setText("It may have been borrowed or was never added.");
            lSearchTitle.setForeground(C_RED);
            lSearchAuthor.setForeground(C_MUTED);
            pSearchResult.setBackground(new Color(253, 237, 236));
            pSearchResult.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 152, 144)),
                new EmptyBorder(8, 10, 8, 10)));

            log("[Search] ISBN " + isbn + " not found in catalogue.");
            setStatus("Not found: ISBN " + isbn);
        }

        pSearchResult.setVisible(true);
        pSearchResult.revalidate();
        pSearchResult.repaint();
    }

    /**
     * Handles the "Borrow Book" button click.
     *
     * Strategy:
     *   1. Validate ISBN input.
     *   2. Call library.searchBook() to verify existence AND capture book
     *      details BEFORE the BST node is removed on borrow.
     *   3. Call library.borrowBook() to update BST + push onto internal stack.
     *   4. Insert a new row at position 0 of the history table (LIFO order).
     */
    private void handleBorrow() {
        String isbnRaw = fieldValue(fBorrowIsbn);

        if (isbnRaw.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter an ISBN number to borrow.",
                "Missing Input",
                JOptionPane.WARNING_MESSAGE);
            fBorrowIsbn.requestFocus();
            return;
        }

        int isbn;
        try {
            isbn = Integer.parseInt(isbnRaw);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "ISBN must be a whole number.\n"
                + "\"" + isbnRaw + "\" is not a valid integer.",
                "Invalid ISBN",
                JOptionPane.ERROR_MESSAGE);
            fBorrowIsbn.selectAll();
            fBorrowIsbn.requestFocus();
            return;
        }

        // Pre-check: capture title/author BEFORE borrowBook() removes the node
        Book book = library.searchBook(isbn);     // ← LibraryADT only
        if (book == null) {
            JOptionPane.showMessageDialog(this,
                "No book with ISBN " + isbn + " is available.\n"
                + "It may already have been borrowed or was never added.",
                "Book Not Available",
                JOptionPane.ERROR_MESSAGE);
            log("[Borrow] FAILED — ISBN " + isbn + " not in catalogue.");
            setStatus("Borrow failed: ISBN " + isbn + " not found.");
            return;
        }

        String title  = book.getTitle();
        String author = book.getAuthor();

        library.borrowBook(isbn, currentUserID);                 // ← LibraryADT only

        // Insert at row 0 → newest record always appears at the top (LIFO)
        borrowSeq++;
        historyModel.insertRow(0, new Object[]{borrowSeq, title, author, isbn});

        log("[Borrow] ISBN " + isbn + ": " + title + " by " + author);
        setStatus("Borrowed: " + title + "  (ISBN " + isbn + ")");

        clearField(fBorrowIsbn);
        fBorrowIsbn.requestFocus();
    }

    /**
     * Handles the "↩  Undo Last Borrow / Return Book" button click.
     *
     * Strategy:
     *   1. Guard against empty history using the GUI table row count (always
     *      in sync with the internal stack depth).
     *   2. Capture title and ISBN from row 0 BEFORE removal.
     *   3. Delegate to library.returnLatestBook() — pops the stack and
     *      re-inserts the book into the BST via addBook(), all via LibraryADT.
     *   4. Remove row 0 from the table to mirror the LIFO pop.
     */

    //2.0 return book way
    
    private void handleReturnByIsbn(JTextField isbnField) {
        String raw = isbnField.getText().trim();
        if (raw.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter an ISBN to return.",
                "Missing Input",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        int isbn;
        try {
            isbn = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "ISBN must be a whole number.",
                "Invalid ISBN",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

    // 调用 LibraryADT 中的 returnBook 方法（注意接口中已经改为 returnBook(int isbn)）
            library.returnBook(isbn, currentUserID);

    // 刷新历史表格（重新从栈中加载所有记录）
            syncHistoryTableFromStack();
            isbnField.setText("");
            setStatus("Returned book with ISBN " + isbn);
    }

    /**
     * Handles the "Delete Book" button click (Librarian only).
     */
    private void handleDeleteBook() {
        String isbnRaw = fieldValue(fDeleteIsbn);
        if (isbnRaw.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an ISBN to delete.", "Missing Input", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int isbn;
        try {
            isbn = Integer.parseInt(isbnRaw);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "ISBN must be a whole number.", "Invalid ISBN", JOptionPane.ERROR_MESSAGE);
            return;
        }

        library.deleteBook(isbn);     // ← Calls the newly added LibraryADT method
        setStatus("Attempted deletion for ISBN: " + isbn);
        
        clearField(fDeleteIsbn);
        fDeleteIsbn.requestFocus();
    }

    // ==========================================================================
    //  UI FACTORY HELPERS
    // ==========================================================================

    private JPanel makeCard(Color accentColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(C_CARD);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 4, 0, 0, accentColor),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER),
                new EmptyBorder(14, 16, 16, 14))));
        return card;
    }

    private JLabel makeSectionTitle(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        lbl.setForeground(color);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }

    private JTextField makeField(String placeholder) {
        JTextField f = new JTextField();
        f.setFont(new Font("SansSerif", Font.PLAIN, 13));
        f.setForeground(C_MUTED);
        f.setPreferredSize(new Dimension(180, 30));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(C_BORDER),
            new EmptyBorder(3, 8, 3, 8)));

        f.putClientProperty("placeholder", placeholder);
        f.setText(placeholder);

        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (isShowingPlaceholder(f)) {
                    f.setText("");
                    f.setForeground(C_TEXT);
                }
            }
            @Override
            public void focusLost(FocusEvent e) {
                if (f.getText().trim().isEmpty()) {
                    f.setText(placeholder);
                    f.setForeground(C_MUTED);
                }
            }
        });
        return f;
    }

    private boolean isShowingPlaceholder(JTextField f) {
        String ph = (String) f.getClientProperty("placeholder");
        return ph != null && ph.equals(f.getText());
    }

    private String fieldValue(JTextField f) {
        return isShowingPlaceholder(f) ? "" : f.getText().trim();
    }

    private void clearField(JTextField f) {
        String ph = (String) f.getClientProperty("placeholder");
        f.setText(ph != null ? ph : "");
        f.setForeground(C_MUTED);
    }

    private JPanel makeFieldRow(String labelText, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(C_TEXT);
        lbl.setPreferredSize(new Dimension(46, 28));

        row.add(lbl,   BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JButton makeButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 37));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(bg.darker()); }
            @Override public void mouseExited (MouseEvent e) { btn.setBackground(bg); }
        });
        return btn;
    }

    private JButton makeLinkButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btn.setForeground(C_MUTED);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private Component vGap(int pixels) {
        return Box.createRigidArea(new Dimension(0, pixels));
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    // ==========================================================================
    //  ENTRY POINT
    // ==========================================================================

    /**
     * Application entry point.
     *
     * Startup sequence (order is critical):
     *   1. Build the GUI — this installs the System.out redirect so all
     *      subsequent library output appears in the GUI console, not the terminal.
     *   2. Decide which catalogue file to load:
     *        "current_catalogue.txt" if a saved session exists, else "books.txt".
     *   3. Restore the history stack from "current_history.txt" (no-op if absent).
     *   4. Sync the GUI history table from the restored stack.
     *
     * Shutdown sequence (triggered by window close):
     *   WindowListener.windowClosing() → saveLibraryState() → dispose() → exit(0)
     *
     * Compile & run:
     *   javac *.java
     *   java SmartLibraryGUI
     *
     * books.txt must be in the same directory as the .class files for first run.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            // Attempt to use the native OS look-and-feel for better aesthetics
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to cross-platform Metal L&F — always available
            }

            // Declared as LibraryADT — concrete type is hidden from this point on
            LibraryADT library = new LibraryImpl();

            // ── Step 0: Load Borrowers FIRST so login verification works ──
            library.preloadBorrowers("users.txt");
            
            // ── Step 1: Build GUI first ────────────────────────────────────────
            // MUST come before preloadData() so the System.out redirect is active
            // and all load messages appear in the GUI console, not the terminal.
            SmartLibraryGUI gui = new SmartLibraryGUI(library);

            // ── Step 2: Load catalogue (saved session or default) ─────────────
            File savedCatalogue = new File(SAVED_CATALOGUE);
            if (savedCatalogue.exists()) {
                System.out.println("[Startup] Saved catalogue detected — restoring previous session...");
                library.preloadData(SAVED_CATALOGUE);
            } else {
                System.out.println("[Startup] No saved session found — loading default catalogue...");
                library.preloadData(DEFAULT_CATALOGUE);
            }

            // ── Step 3: Restore borrowing history ─────────────────────────────
            library.preloadHistory(SAVED_HISTORY);

            // ── Step 4: Sync GUI history table from the restored stack ─────────
            gui.syncHistoryTableFromStack();
        });
    }

    // ==========================================================================
    //  AUTHENTICATION (Login Dialog)
    // ==========================================================================
    /**
     * Blocks execution and prompts the user to log in before the main frame loads.
     * @return true if successfully authenticated, false if user exits.
     */
    private boolean authenticate() {
        while (true) {
            String[] options = {"Librarian", "Borrower", "Exit"};
            int choice = JOptionPane.showOptionDialog(null, 
                    "Select Login Role", "Smart Library Login",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, 
                    null, options, options[0]);

            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                return false; // User closed the dialog or clicked Exit
            }

            if (choice == 0) { // Librarian
                JPasswordField pf = new JPasswordField();
                int okCxl = JOptionPane.showConfirmDialog(null, pf, 
                        "Enter Librarian Password (42):", 
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (okCxl == JOptionPane.OK_OPTION) {
                    if ("42".equals(new String(pf.getPassword()))) {
                        isLibrarian = true;
                        return true;
                    } else {
                        JOptionPane.showMessageDialog(null, "Access Denied!", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else if (choice == 1) { // Borrower
                JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
                JTextField idField = new JTextField();
                JPasswordField passField = new JPasswordField();
                panel.add(new JLabel("Borrower ID:"));
                panel.add(idField);
                panel.add(new JLabel("Key:"));
                panel.add(passField);

                int res = JOptionPane.showConfirmDialog(null, panel, 
                        "Borrower Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (res == JOptionPane.OK_OPTION) {
                    if (library.authenticateBorrower(idField.getText(), new String(passField.getPassword()))) {
                        isLibrarian = false;
                        currentUserID = idField.getText();
                        return true;
                    } else {
                        JOptionPane.showMessageDialog(null, "Invalid ID or Key!", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }
}
