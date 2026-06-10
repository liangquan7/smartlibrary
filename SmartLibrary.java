import java.io.File;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * SmartLibrary — Command-Line Interface for the Smart Library System.
 *
 * v1.3 — Data Persistence additions:
 *   Startup : Checks for "current_catalogue.txt" first (saved session).
 *             Falls back to "books.txt" (read-only default) on first run.
 *             Restores the borrowing history stack from "current_history.txt"
 *             if it exists.
 *   Exit    : Saves both the BST catalogue and the history stack to
 *             "current_catalogue.txt" and "current_history.txt" before
 *             terminating, so the session is fully restored next launch.
 *
 * All library operations go through the LibraryADT interface only —
 * LibraryImpl is never referenced after construction (Information Hiding).
 *
 * @author Senior Java Engineer
 * @version 1.3
 */
public class SmartLibrary {

    // ── File paths ────────────────────────────────────────────────────────────
    /** Read-only fallback catalogue for first-run initialisation. */
    private static final String DEFAULT_CATALOGUE = "books.txt";

    /** Auto-saved catalogue written on exit and read back on the next launch. */
    private static final String SAVED_CATALOGUE = "current_catalogue.txt";

    /** Auto-saved history stack written on exit and read back on the next launch. */
    private static final String SAVED_HISTORY = "current_history.txt";

    /** Users' info.*/
    private static final String USERS_FILE = "users.txt";

    // =========================================================================
    //  Entry point
    // =========================================================================

    public static void main(String[] args) {

        // Declare as LibraryADT — concrete type is hidden from this point on
        LibraryADT library = new LibraryImpl();
        Scanner    scanner  = new Scanner(System.in);

        // ── Data Initialisation ───────────────────────────────────────────────
        File savedCatalogue = new File(SAVED_CATALOGUE);

        if (savedCatalogue.exists()) {
            // Restore a previously saved session
            System.out.println("Saved session detected. Loading catalogue from \"" +
                    SAVED_CATALOGUE + "\"...");
            library.preloadData(SAVED_CATALOGUE);
        } else {
            // First-run: seed from the read-only default catalogue
            System.out.println("No saved session found. Loading default catalogue from \"" +
                    DEFAULT_CATALOGUE + "\"...");
            library.preloadData(DEFAULT_CATALOGUE);
        }

        // Restore borrowing history (graceful no-op if file is absent)
        System.out.println("Restoring borrowing history from \"" + SAVED_HISTORY + "\"...");
        library.preloadHistory(SAVED_HISTORY);

        // Read in Users' info
        library.preloadBorrowers(USERS_FILE);

        // ── Login Loop ──────────────────────────────────
        while (true) {
            System.out.println("\n=== Smart Library Login ===");
            System.out.println("1. Login as Librarian");
            System.out.println("2. Login as Borrower");
            System.out.println("3. Exit System");
            System.out.print("Select role: ");

            int roleChoice = readInt(scanner, "");
            if (roleChoice == 3) {
                System.out.println("\nSaving library state...");
                library.saveLibraryState(SAVED_CATALOGUE, SAVED_HISTORY);
                System.out.println("Goodbye!");
                scanner.close();
                return;
            }

            if (roleChoice == 1) {
                System.out.print("Enter Librarian Password: ");
                String pass = scanner.nextLine();
                if ("42".equals(pass)) {
                    librarianMenu(library, scanner);
                } else {
                    System.out.println("Access Denied!");
                }
            } else if (roleChoice == 2) {
                System.out.print("Enter Borrower ID: ");
                String id = scanner.nextLine();
                System.out.print("Enter Key: ");
                String key = scanner.nextLine();
                
                if (library.authenticateBorrower(id, key)) {
                    borrowerMenu(library, scanner, id);
                } else {
                    System.out.println("Invalid ID or Key!");
                }
            }
        }
    }

    // ── Sub-Menu -- Librarian Menu ─────────────────────────────────────────
    private static void librarianMenu(LibraryADT library, Scanner scanner) {
        while (true) {
            System.out.println("\n--- Librarian Panel ---");
            System.out.println("1. Add Book / Add Stock"); // Same method adds stock if ISBN exists
            System.out.println("2. Delete Book");
            System.out.println("3. Search Book");
            System.out.println("4. View All History");
            System.out.println("5. Return Book (any user)");
            System.out.println("6. Logout");
            int choice = readInt(scanner, "Enter choice: ");

            switch (choice) {
                case 1: handleAddBook(library, scanner); break;
                case 2: 
                    int isbn = readInt(scanner, "Enter ISBN to delete: ");
                    if (isbn != -1) library.deleteBook(isbn);
                    break;
                case 3: handleSearchBook(library, scanner); break;
                case 4: library.viewLatestHistory(null); break;
                case 5: handleReturnBook(library, scanner, null); break; // 新增：管理员还书
                case 6: return;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    // ── Sub-Menu -- Borrower Menu ─────────────────────────────────────────
    private static void borrowerMenu(LibraryADT library, Scanner scanner, String borrowerID) {
        while (true) {
            System.out.println("\n--- Borrower Panel ---");
            System.out.println("1. Search Book");
            System.out.println("2. Borrow Book");
            System.out.println("3. Return Book");
            System.out.println("4. View My History");
            System.out.println("5. Logout");
            int choice = readInt(scanner, "Enter choice: ");

            switch (choice) {
                case 1: handleSearchBook(library, scanner); break;
                case 2: handleBorrowBook(library, scanner, borrowerID); break;
                case 3: handleReturnBook(library, scanner, borrowerID); break;
                case 4: library.viewLatestHistory(borrowerID); break; // Can be upgraded later to filter by user
                case 5: return; // Logout
                default: System.out.println("Invalid choice.");
            }
        }
    }

    // =========================================================================
    //  Menu action handlers
    // =========================================================================

    private static void handleAddBook(LibraryADT library, Scanner scanner) {
        int isbn = readInt(scanner, "Enter ISBN: ");
        if (isbn == -1) return;

        System.out.print("Enter Title: ");
        String title = scanner.nextLine();

        System.out.print("Enter Author: ");
        String author = scanner.nextLine();

        library.addBook(isbn, title, author, 1);
        System.out.println("Book added successfully!");
    }

    private static void handleSearchBook(LibraryADT library, Scanner scanner) {
        int isbn = readInt(scanner, "Enter ISBN to search: ");
        if (isbn == -1) return;

        Book book = library.searchBook(isbn);
        if (book != null) {
            System.out.println("Book found: " + book.getTitle() +
                    " by " + book.getAuthor());
        } else {
            System.out.println("Book not found!");
        }
    }

    private static void handleBorrowBook(LibraryADT library, Scanner scanner, String borrowerID) {
        int isbn = readInt(scanner, "Enter ISBN to borrow: ");
        if (isbn == -1) return;

        library.borrowBook(isbn, borrowerID);
    }


    private static void handleReturnBook(LibraryADT library, Scanner scanner, String borrowerID) {
        int isbn = readInt(scanner, "Enter ISBN of the book to return: ");
        if (isbn == -1) return;
        
        library.returnBook(isbn, borrowerID);
    }
    // =========================================================================
    //  Input utility
    // =========================================================================

    /**
     * Reads a single integer from stdin with a prompt.
     *
     * @param scanner Scanner instance to read from
     * @param prompt  Text to print before reading
     * @return The integer entered, or {@code -1} on invalid input
     */
    private static int readInt(Scanner scanner, String prompt) {
        System.out.print(prompt);
        try {
            int value = scanner.nextInt();
            scanner.nextLine();
            return value;
        } catch (InputMismatchException e) {
            System.out.println("Invalid input. Please enter numbers only.");
            scanner.nextLine();
            return -1;
        }
    }
}
