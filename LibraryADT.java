import java.util.Stack;

/**
 * Library Abstract Data Type (ADT) Interface.
 *
 * Defines the complete contract for the Smart Library system.
 * All clients — both the CLI (SmartLibrary) and the GUI (SmartLibraryGUI)
 * — communicate exclusively through this interface, enforcing strict
 * Information Hiding: no concrete implementation details are ever exposed.
 *
 * Data structures used by the implementation:
 *   - Binary Search Tree (BST)  : book catalogue, indexed by ISBN
 *   - Stack (LIFO)              : borrowing history
 *
 * Persistence contract (v1.3 additions):
 *   On exit  → saveLibraryState() writes both the BST and the history stack
 *              to two pipe-delimited text files.
 *   On start → preloadData()    restores the catalogue (falling back to the
 *              read-only books.txt on first run).
 *            → preloadHistory() restores the history stack.
 *   The caller (main method) decides which files to pass; the interface
 *   methods are file-agnostic.
 *
 * @author Senior Java Engineer
 * @version 1.3
 */
public interface LibraryADT {

    // =========================================================================
    //  Core catalogue & borrowing operations
    // =========================================================================

    /**
     * Adds a new book to the library's BST collection.
     * Duplicate ISBNs are silently ignored.
     *
     * @param isbn   The unique book identifier
     * @param title  The book's title
     * @param author The book's author
     */
    void addBook(int isbn, String title, String author, int stock);

    /**
     * Processes a book borrowing request.
     * Removes the book from available inventory and records the borrowing
     * in the history stack.
     *
     * @param isbn The ISBN of the book to borrow
     */
    void borrowBook(int isbn, String borrowerID);

    /**
     * Displays the latest borrowing history from the stack.
     * Shows the most recent borrowing operations in LIFO order (up to 10).
     */
    void viewLatestHistory(String borrowerID);

    /**
     * Searches for a book by ISBN using a recursive BST traversal — O(log n).
     *
     * @param isbn The ISBN to search for
     * @return The Book object if found, or {@code null} if not present
     */
    Book searchBook(int isbn);

    /**
     * LIFO Undo-Style Return: pops the most recently borrowed book from the
     * history stack and re-inserts it into the BST catalogue via addBook().
     * Prints a descriptive message if the stack is empty.
     */
    void returnBook(int isbn, String borrowerID);

    // =========================================================================
    //  Startup — data loading
    // =========================================================================

    /**
     * Pre-loads books from a pipe-delimited text file into the BST.
     *
     * Expected file format (one book per line):
     *   ISBN|Title|Author
     *
     * Robustness:
     *   - Comment lines (starting with '#') and blank lines are skipped.
     *   - Malformed lines are skipped with a [WARN] message.
     *   - FileNotFoundException is handled gracefully (library starts empty).
     *
     * The caller is responsible for choosing the correct file path — typically:
     *   "current_catalogue.txt" if it exists (saved session), else "books.txt"
     *   (read-only fallback for first-run).
     *
     * @param filePath Path to the catalogue file (e.g. "books.txt")
     */
    void preloadData(String filePath);

    /**
     * Restores the borrowing history stack from a previously saved file.
     *
     * Lines are read and pushed sequentially, so the last line in the file
     * becomes the top of the stack — exactly recreating the original LIFO
     * order when the file was written bottom-to-top by saveLibraryState().
     *
     * If the file does not exist the method returns silently; the history
     * stack remains empty (graceful first-run behaviour).
     *
     * @param historyPath Path to the history file (e.g. "current_history.txt")
     */
    void preloadHistory(String historyPath);

    // =========================================================================
    //  Shutdown — data persistence
    // =========================================================================

    /**
     * Persists the current library state to two pipe-delimited text files.
     *
     * Catalogue serialisation strategy — Pre-order BST traversal:
     *   Writing root → left → right ensures that re-inserting the records in
     *   the same order on the next startup reconstructs the identical BST
     *   topology and preserves O(log n) search performance. An in-order
     *   (sorted) dump would produce a degenerate right-skewed linked list.
     *
     * History serialisation strategy — Bottom-to-top (index 0 → top):
     *   Writing the oldest entry first means sequential pushes on the next
     *   startup recreate the exact same LIFO order; the last line written
     *   ends up on top of the stack.
     *
     * Both files use the standard ISBN|Title|Author pipe-delimited format
     * and include a comment header that is skipped on reload.
     *
     * @param cataloguePath Destination path for the BST catalogue snapshot
     *                      (e.g. "current_catalogue.txt")
     * @param historyPath   Destination path for the history stack snapshot
     *                      (e.g. "current_history.txt")
     */
    void saveLibraryState(String cataloguePath, String historyPath);

    // =========================================================================
    //  State accessors
    // =========================================================================

    /**
     * Returns the live borrowing history stack.
     *
     * Exposed so that external components (e.g. the GUI) can synchronise
     * their visual state after preloadHistory() is called at startup.
     * Callers must treat the returned reference as <b>read-only</b>.
     *
     * Stack layout: index 0 = oldest record, top (size-1) = most recent.
     *
     * @return The internal {@link Stack} of {@link BorrowHistory} records
     */
    Stack<BorrowHistory> getHistoryStack();

    /**
     * Returns the BST root node.
     * Provided for unit-testing convenience; not for general use.
     *
     * @return The root {@link Book} node, or {@code null} for an empty library
     */
    Book getRoot();

    // =========================================================================
    //  New Addition: RBAC and Delete   Date: 2026-6-10
    // =========================================================================

    /**
     * Preloads borrower accounts from a text file (e.g., users.txt).
     */
    void preloadBorrowers(String filePath);

    /**
     * Authenticates a borrower using their ID and plaintext key.
     */
    boolean authenticateBorrower(String id, String rawKey);

    /**
     * Deletes a book entirely from the BST catalog (Librarian only).
     */
    void deleteBook(int isbn);
}
