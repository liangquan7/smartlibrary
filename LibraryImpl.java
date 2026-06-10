import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Stack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;

/**
 * Concrete implementation of the LibraryADT interface.
 *
 * Uses a Binary Search Tree (BST) to manage book inventory by ISBN
 * and a Stack to track borrowing history (LIFO order).
 *
 * v1.3 — Data Persistence additions:
 *   saveLibraryState()  writes both the BST (pre-order) and history stack
 *                       (bottom-to-top) to pipe-delimited text files so the
 *                       session can be fully restored on the next launch.
 *   preloadHistory()    reads a saved history file and reconstructs the
 *                       stack in its original LIFO order.
 *
 * Supports pre-loading initial book data from a pipe-delimited text file
 * via {@link #preloadData(String)}.
 *
 * @author Senior Java Engineer
 * @version 1.3
 */
public class LibraryImpl implements LibraryADT {

    // BST root — manages the collection of books
    private Book root;

    // Borrowing history stack (LIFO)
    private Stack<BorrowHistory> historyStack;

    // List of borrowers
    private ArrayList<Borrower> borrowers = new ArrayList<>();

    // Delimiter used in all data files
    private static final String FILE_DELIMITER = "\\|";

    // Expected number of fields per line in any data file
    private static final int EXPECTED_FIELDS = 4;

    // =========================================================================
    //  Constructor
    // =========================================================================

    /**
     * Initialises the library with an empty BST and an empty history stack.
     */
    public LibraryImpl() {
        this.root = null;
        this.historyStack = new Stack<>();
    }

    // =========================================================================
    //  LibraryADT — Core operations
    // =========================================================================

    @Override
    public void addBook(int isbn, String title, String author, int stock) {
        root = insertBST(root, isbn, title, author, stock);
    }

    @Override
    public void borrowBook(int isbn, String borrowerID) {
        Book book = searchBook(isbn);

        if (book == null) {
            System.out.println("Error: Book with ISBN " + isbn +
                    " not found in library!");
            return;
        }

        // Check stock
        if (book.getStock() <= 0) {
            System.out.println("Error: Book is currently out of stock!");
            return;
        }

        // Record borrowing in history stack BEFORE removing from BST
        BorrowHistory record = new BorrowHistory();
        record.setIsbn(isbn);
        record.setTitle(book.getTitle());
        record.setAuthor(book.getAuthor());
        record.setBID(borrowerID);
        historyStack.push(record);

        // Decrease book stock by 1; if no book left, remove from BST catalogue after borrowing
        if(book.borrowOut()){
            root = deleteBST(root, isbn);
        }
        

        System.out.println("Book borrowed: " + record.getTitle() +
                " by " + record.getAuthor());
    }

    @Override
    public void viewLatestHistory(String borrowerID) {
        if (historyStack.isEmpty()) {
            System.out.println("No borrowing history yet.");
            return;
        }

        System.out.println("\n=== Latest Borrowing History ===");
        int size  = historyStack.size();
        //cancel the limitation int limit = Math.min(10, size);

        // Iterate from top of stack (newest) downward to show latest 10
        for (int i = size - 1; i >= 0; i--) { //size - limit
            BorrowHistory record = historyStack.get(i);
            if(borrowerID != null && !borrowerID.equals(record.getBID())){
                continue;
            }
            System.out.println(String.format("[%d] %s by %s (ISBN: %d) - Borrower: %s",
                    (size - i), record.getTitle(), record.getAuthor(),
                    record.getIsbn(), record.getBID()));
        }
        System.out.println("==============================\n");
    }

    @Override
    public Book searchBook(int isbn) {
        return searchBST(root, isbn);
    }

    @Override
    public void returnBook(int isbn, String borrowerID) {
        if (historyStack.isEmpty()) {
            System.out.println("No borrowing history found. Cannot return book.");
            return;
        }

        // 从栈顶向下查找第一个匹配 ISBN 的记录（即最近一次借阅未归还的） search
        int index = -1;
        for (int i = historyStack.size() - 1; i >= 0; i--) {
            BorrowHistory record = historyStack.get(i);
            if (historyStack.get(i).getIsbn() == isbn) {
                if(borrowerID == null || borrowerID.equals(record.getBID())){
                    index = i;
                    break;
                }
            }
        }

        if (index == -1) {
            System.out.println("No borrowing record found for ISBN " + isbn + ". Cannot return.");
            return;
        }

        // 取出记录并删除 change record
        BorrowHistory record = historyStack.remove(index);

        /**
         * Search the book in the BST, if found, increase stock by 1;
         * if not found, re-add the book to the BST with stock 1
         */
        Book book = searchBook(isbn);
        if (book != null) {
            book.returnIn(); // Just increment stock
        } else {
            addBook(isbn, record.getTitle(), record.getAuthor(), 1); // Re-insert if deleted
        }

        String title = record.getTitle();
        System.out.println("Successfully returned: " + title + " (ISBN: " + isbn + ")");
    }

    @Override
    public void deleteBook(int isbn) {
        Book book = searchBook(isbn);
        if (book != null) {
            root = deleteBST(root, isbn);
            System.out.println("Book with ISBN " + isbn + " deleted from catalog.");
        } else {
            System.out.println("Book not found.");
        }
    }

    // =========================================================================
    //  LibraryADT — Startup: data loading
    // =========================================================================

    /**
     * Pre-loads books from a pipe-delimited text file into the BST.
     *
     * Expected file format (one book per line):
     *   ISBN|Title|Author
     *
     * Robustness rules applied during parsing:
     *   - Lines starting with '#' are treated as comments and skipped.
     *   - Blank / whitespace-only lines are silently skipped.
     *   - Lines with a non-integer ISBN log a warning and are skipped.
     *   - Lines with the wrong number of fields log a warning and are skipped.
     *   - Empty title or author fields log a warning and are skipped.
     *   - If the file is not found, a clear message is printed and the
     *     library starts empty (no crash).
     *   - Any other I/O error is reported and loading stops gracefully.
     *
     * The caller decides which file to pass:
     *   Pass "current_catalogue.txt" when a saved session exists, otherwise
     *   fall back to "books.txt" for a first-run default load.
     *
     * @param filePath Path to the catalogue data file
     */
    @Override
    public void preloadData(String filePath) {
        int loaded    = 0;
        int skipped   = 0;
        int lineNumber = 0;

        ArrayList<Book> bookList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {

            String line;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();

                // Skip blank lines and comment lines
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] fields = trimmed.split(FILE_DELIMITER);

                // Validate field count
                if (fields.length != EXPECTED_FIELDS) {
                    System.out.println("  [WARN] Line " + lineNumber +
                            ": Expected " + EXPECTED_FIELDS +
                            " fields but found " + fields.length +
                            " — skipping: \"" + trimmed + "\"");
                    skipped++;
                    continue;
                }

                String isbnRaw = fields[0].trim();
                String title   = fields[1].trim();
                String author  = fields[2].trim();
                String stockRaw= fields[3].trim();

                // Validate ISBN is a valid integer
                int isbn;
                try {
                    isbn = Integer.parseInt(isbnRaw);
                } catch (NumberFormatException e) {
                    System.out.println("  [WARN] Line " + lineNumber +
                            ": Invalid ISBN \"" + isbnRaw +
                            "\" (must be an integer) — skipping.");
                    skipped++;
                    continue;
                }

                // Validate title and author are not empty after trimming
                if (title.isEmpty() || author.isEmpty()) {
                    System.out.println("  [WARN] Line " + lineNumber +
                            ": Title or Author is empty — skipping: \"" + trimmed + "\"");
                    skipped++;
                    continue;
                }

                // Validate stock is a valid integer
                int stock;
                try {
                    stock = Integer.parseInt(stockRaw);
                } catch (NumberFormatException e) {
                    System.out.println("  [WARN] Line " + lineNumber +
                            ": Invalid stock \"" + stockRaw +
                            "\" (must be an integer) — skipping.");
                    skipped++;
                    continue;
                }

                // All checks passed — add to the list, pending sort and tree-building
                bookList.add(new Book(isbn, title, author, stock));
            }

            // Remove the duplicates
            LinkedHashSet<Book> set = new LinkedHashSet<>(bookList);
            bookList.clear();
            bookList.addAll(set);
            loaded = bookList.size();

            //Sort: TimSort, which is stable and extremely efficient if the list is well-sorted 
            bookList.sort(Comparator.comparingInt(book->book.getIsbn()));

            //Build the tree
            root = buildBalancedBST(bookList, 0, bookList.size()-1);

            // Summary report
            System.out.println("--------------------------------------------------");
            System.out.println("Catalogue load complete: " + loaded + " book(s) loaded" +
                    (skipped > 0 ? ", " + skipped + " line(s) skipped." : "."));
            System.out.println("--------------------------------------------------");

        } catch (FileNotFoundException e) {
            System.out.println("[INFO] Catalogue file \"" + filePath +
                    "\" not found. Starting with an empty catalogue.");

        } catch (IOException e) {
            System.out.println("[ERROR] Failed to read \"" + filePath +
                    "\" at line " + lineNumber + ": " + e.getMessage());
            System.out.println("        " + loaded + " book(s) were loaded before the error.");
        }
    }

    // Help with Building a Tree From a Sorted List
    private Book buildBalancedBST(ArrayList<Book> sortedBooks, int start, int end) {
        if (start > end) return null;
        
        int mid = start + (end - start) / 2;
        Book root = sortedBooks.get(mid);
        
        root.setLeft(buildBalancedBST(sortedBooks, start, mid - 1));
        root.setRight(buildBalancedBST(sortedBooks, mid + 1, end));
        
        return root;
    }

    /**
     * Restores the borrowing history stack from a previously saved file.
     *
     * Records are pushed in the order they appear in the file (top-to-bottom).
     * Because saveLibraryState() writes the stack bottom-to-top, pushing in
     * file order recreates the exact original LIFO ordering — the last line
     * in the file was the top of the stack at save time, and it becomes the
     * top again after restore.
     *
     * If the file does not exist the method returns silently; the history
     * stack remains empty (graceful first-run behaviour).
     *
     * @param historyPath Path to the saved history file
     *                    (e.g. "current_history.txt")
     */
    @Override
    public void preloadHistory(String historyPath) {
        int loaded     = 0;
        int skipped    = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(historyPath))) {

            String line;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();

                // Skip blank lines and comment lines
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] fields = trimmed.split(FILE_DELIMITER);

                if (fields.length != EXPECTED_FIELDS) {
                    System.out.println("  [WARN] History line " + lineNumber +
                            ": Expected " + EXPECTED_FIELDS +
                            " fields but found " + fields.length + " — skipping.");
                    skipped++;
                    continue;
                }

                String isbnRaw = fields[0].trim();
                String title   = fields[1].trim();
                String author  = fields[2].trim();
                String bID     = fields[3].trim();

                int isbn;
                try {
                    isbn = Integer.parseInt(isbnRaw);
                } catch (NumberFormatException e) {
                    System.out.println("  [WARN] History line " + lineNumber +
                            ": Invalid ISBN \"" + isbnRaw + "\" — skipping.");
                    skipped++;
                    continue;
                }

                if (title.isEmpty() || author.isEmpty() || bID.isEmpty()) {
                    System.out.println("  [WARN] History line " + lineNumber +
                            ": Empty title, author, or borrower ID — skipping.");
                    skipped++;
                    continue;
                }

                // Reconstruct and push the history record
                BorrowHistory record = new BorrowHistory();
                record.setIsbn(isbn);
                record.setTitle(title);
                record.setAuthor(author);
                record.setBID(bID);
                historyStack.push(record);
                loaded++;
            }

            System.out.println("--------------------------------------------------");
            System.out.println("History restore: " + loaded + " record(s) loaded" +
                    (skipped > 0 ? ", " + skipped + " line(s) skipped." : "."));
            System.out.println("--------------------------------------------------");

        } catch (FileNotFoundException e) {
            System.out.println("[INFO] No history file found at \"" + historyPath +
                    "\". Starting with empty borrowing history.");

        } catch (IOException e) {
            System.out.println("[ERROR] Failed to read history from \"" + historyPath +
                    "\" at line " + lineNumber + ": " + e.getMessage());
        }
    }

    /**
     * Loads the borrowers login info from a file.
     *
     * @param filePath Path to the saved user's info
     *                    (e.g. "user.txt")
     */
    @Override
    public void preloadBorrowers(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split(","); 
                if (fields.length >= 2) {
                    String id = fields[0].trim();
                    String encryptedKey = fields[1].trim();
                    borrowers.add(new Borrower(id, encryptedKey));
                }
            }
            System.out.println("[INFO] Borrowers loaded successfully.");
        } catch (Exception e) {
            System.out.println("[INFO] Borrowers file not found at " + filePath + ". Starting empty.");
        }
    }

    // =========================================================================
    //  LibraryADT — Shutdown: data persistence
    // =========================================================================

    /**
     * Persists the current library state to two pipe-delimited text files.
     *
     * --- Catalogue (BST) ---
     * Serialises using a in-order traversal (left → root → right).
     * In-order is critical.
     * An in-order (sorted) dump would cause degenerate O(n) right-skewed reconstruction.
     * But if we restruct the tree with binary recusion, the new tree would be perfectly balanced.
     * Via special I/O processing, we maintain the performance of the tree without complicate algorithm (like AVL).
     * 
     *
     * --- History (Stack) ---
     * Iterates from index 0 (oldest / bottom) up to size-1 (newest / top).
     * Writing bottom-to-top means that sequential pushes in preloadHistory()
     * restore the original LIFO stack ordering exactly.
     *
     * Both files use the standard ISBN|Title|Author pipe-delimited format
     * with comment header lines that are skipped on reload.
     *
     * @param cataloguePath Destination path for the BST snapshot
     * @param historyPath   Destination path for the history stack snapshot
     */
    @Override
    public void saveLibraryState(String cataloguePath, String historyPath) {

        // ── 1. Save BST via In-order traversal ───────────────────────────────
        try (PrintWriter writer = new PrintWriter(new FileWriter(cataloguePath))) {

            writer.println("# Smart Library — Current Catalogue (auto-saved)");
            writer.println("# Format : ISBN|Title|Author");
            writer.println("# Traversal: In-order — DO NOT edit manually");

            int catCount = saveBSTInOrder(root, writer);

            System.out.println("[Save] Catalogue : " + catCount +
                    " book(s) written to \"" + cataloguePath + "\".");

        } catch (IOException e) {
            System.out.println("[ERROR] Failed to save catalogue to \"" +
                    cataloguePath + "\": " + e.getMessage());
        }

        // ── 2. Save history stack bottom-to-top ───────────────────────────────
        try (PrintWriter writer = new PrintWriter(new FileWriter(historyPath))) {

            writer.println("# Smart Library — Borrowing History (auto-saved)");
            writer.println("# Format  : ISBN|Title|Author");
            writer.println("# Order   : Bottom-to-top — DO NOT edit manually");

            int histCount = historyStack.size();
            for (int i = 0; i < histCount; i++) {
                BorrowHistory r = historyStack.get(i);
                writer.println(r.getIsbn() + "|" + r.getTitle() + "|" + r.getAuthor() + "|" + r.getBID());
            }

            System.out.println("[Save] History   : " + histCount +
                    " record(s) written to \"" + historyPath + "\".");

        } catch (IOException e) {
            System.out.println("[ERROR] Failed to save history to \"" +
                    historyPath + "\": " + e.getMessage());
        }

        System.out.println("[Save] Library state saved successfully.");
    }

    /**
     * Recursive IN-order BST serialisation helper.
     * Writes each node as "ISBN|Title|Author" and returns the total count.
     *
     * @param node   Current BST node (null = base case)
     * @param writer Destination writer (already open by the caller)
     * @return Number of nodes written in this subtree
     */
    private int saveBSTInOrder(Book node, PrintWriter writer) {
        if (node == null) return 0;

        //Recurse into left subtrees firstly (In-order)
        int left = saveBSTInOrder(node.getLeft(),  writer);

        // Write this node Secondly (In-order)
        writer.println(node.getIsbn() + "|" + node.getTitle() + "|" + node.getAuthor() + "|" + node.getStock());

        //Recurse into right subtrees at last (In-order)
        int right = saveBSTInOrder(node.getRight(), writer);

        //Accumulating the count
        return left + 1 + right;
    }

    // =========================================================================
    //  LibraryADT — State accessors
    // =========================================================================

    /**
     * Returns the live borrowing history stack.
     * Callers must treat this reference as read-only.
     *
     * @return The internal Stack of BorrowHistory records
     *         (index 0 = oldest, top = most recent)
     */
    @Override
    public Stack<BorrowHistory> getHistoryStack() {
        return historyStack;
    }

    /**
     * Returns the BST root node for testing/external access.
     *
     * @return BST root node, or {@code null} for an empty library
     */
    @Override
    public Book getRoot() {
        return root;
    }

    // =========================================================================
    //  BST private helpers
    // =========================================================================

    /**
     * Inserts a new book node into the BST.
     * Duplicate ISBNs are silently ignored (no update).
     *
     * @param node   Current BST node
     * @param isbn   ISBN of new book
     * @param title  Title of new book
     * @param author Author of new book
     * @return Updated subtree root
     */
    private Book insertBST(Book node, int isbn, String title, String author, int stock) {
        if (node == null) {
            return new Book(isbn, title, author, stock);
        }
        if (isbn < node.getIsbn()) {
            node.setLeft(insertBST(node.getLeft(), isbn, title, author, stock));
        } else if (isbn > node.getIsbn()) {
            node.setRight(insertBST(node.getRight(), isbn, title, author, stock));
        }
        // isbn == node.getIsbn() → stock increasing by 1
        else{
            node.setStock(node.getStock() + stock);
        }
        return node;
    }

    /**
     * Searches for a book in the BST recursively — O(log n).
     *
     * @param node Current BST node
     * @param isbn ISBN to search for
     * @return Book if found, null otherwise
     */
    private Book searchBST(Book node, int isbn) {
        if (node == null)              return null;
        if (isbn == node.getIsbn())    return node;
        if (isbn < node.getIsbn())     return searchBST(node.getLeft(), isbn);
        return searchBST(node.getRight(), isbn);
    }

    /**
     * Deletes a book node from the BST by ISBN.
     * Handles three standard cases:
     *   1. Leaf node      — simply remove it
     *   2. One child      — replace node with its child
     *   3. Two children   — replace with in-order successor (min of right subtree)
     *
     * @param node Current BST node
     * @param isbn ISBN of the book to delete
     * @return Updated subtree root after deletion
     */
    private Book deleteBST(Book node, int isbn) {
        if (node == null) return null;

        if (isbn < node.getIsbn()) {
            node.setLeft(deleteBST(node.getLeft(), isbn));
        } else if (isbn > node.getIsbn()) {
            node.setRight(deleteBST(node.getRight(), isbn));
        } else {
            // Found the target node

            // Cases 1 & 2: no left child or no right child
            if (node.getLeft()  == null) return node.getRight();
            if (node.getRight() == null) return node.getLeft();

            // Case 3: two children
            // Find in-order successor (smallest node in right subtree)
            Book successor = findMin(node.getRight());

            // Delete the successor from the right subtree first,
            // then promote its data to this position
            node.setRight(deleteBST(node.getRight(), successor.getIsbn()));

            Book replacement = new Book(
                    successor.getIsbn(), successor.getTitle(), successor.getAuthor());
            replacement.setLeft(node.getLeft());
            replacement.setRight(node.getRight());
            return replacement;
        }
        return node;
    }

    /**
     * Finds the minimum (leftmost) node in a BST subtree.
     * Used by deleteBST to locate the in-order successor.
     *
     * @param node Root of the subtree to search
     * @return The node with the smallest ISBN in the subtree
     */
    private Book findMin(Book node) {
        while (node.getLeft() != null) {
            node = node.getLeft();
        }
        return node;
    }

    // =========================================================================
    //  RBAC
    // =========================================================================

    @Override
    public boolean authenticateBorrower(String id, String rawKey) {
        for (Borrower b : borrowers) {
            if (b.getID().equals(id) && b.verify(rawKey)) {
                return true;
            }
        }
        return false;
    }
}
