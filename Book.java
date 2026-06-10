/**
 * Represents a book in the library's Binary Search Tree (BST).
 * Each node stores book details (ISBN, title, author) and maintains
 * BST structure with left and right child pointers for efficient searching.
 * 
 * @author Senior Java Engineer
 * @version 1.0
 */
public class Book {
    // Book identifier - unique in the library
    private int isbn;
    
    // Book metadata
    private String title;
    private String author;
    private int stock;
    
    // BST structure pointers
    private Book left;
    private Book right;
    
    // Constructs a new Book node with specified details
    /**
     * Creates a new Book node with the given ISBN, title, and author.
     * 
     * @param isbn The unique book identifier
     * @param title The book's title
     * @param author The book's author
     */
    public Book(int isbn, String title, String author) {
        // Initialize book properties
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        stock = 1;
        
        // Initialize BST pointers to null (no children yet)
        this.left = null;
        this.right = null;
    }

    /**
     * Creates a new book node with stock specified
     * @param isbn The unique book identifier
     * @param title The book's title
     * @param author The book's author
     * @param stock The stock of the book
     */
    public Book(int isbn, String title, String author, int stock) {
        // Initialize book properties
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.stock = stock;
        
        // Initialize BST pointers to null (no children yet)
        this.left = null;
        this.right = null;
    }
    
    // --- Getter methods ---
    
    /**
     * Retrieves the book's ISBN.
     * 
     * @return The book's ISBN
     */
    public int getIsbn() {
        return isbn;
    }
    
    /**
     * Retrieves the book's title.
     * 
     * @return The book's title
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Retrieves the book's author.
     * 
     * @return The book's author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Retrieves the book's stock.
     * 
     * @return The book's stock
     */
    public int getStock() {
        return stock;
    }
    
    // --- Stock Management ---

    public void setStock(int newStock) {
        if(newStock < 0 ){return;}
        this.stock = newStock;
    }

    public boolean borrowOut(){
        stock--;
        return stock<=0;
    }

    public void returnIn(){ stock++; }

    
    // --- BST Pointer Accessors ---
    
    /**
     * Retrieves the left child pointer (for smaller ISBN values).
     * 
     * @return The left child Book node, or null if no left child exists
     */
    public Book getLeft() {
        return left;
    }
    
    /**
     * Retrieves the right child pointer (for larger ISBN values).
     * 
     * @return The right child Book node, or null if no right child exists
     */
    public Book getRight() {
        return right;
    }
    
    // --- BST Pointer Mutators ---
    
    /**
     * Sets the left child pointer.
     * 
     * @param left The new left child Book node, or null to remove it
     */
    public void setLeft(Book left) {
        this.left = left;
    }
    
    /**
     * Sets the right child pointer.
     * 
     * @param right The new right child Book node, or null to remove it
     */
    public void setRight(Book right) {
        this.right = right;
    }
    
    // --- String Representation ---
    
    /**
     * Returns a string representation of the book node for debugging.
     * Includes ISBN, title, author, and BST structure info.
     * 
     * @return String representation of the Book node
     */
    @Override
    public String toString() {
        String leftInfo = left != null ? "L:ISBN " + left.isbn + 
            " [" + left.title + "]" : "L: NULL";
        String rightInfo = right != null ? "R:ISBN " + right.isbn + 
            " [" + right.title + "]" : "R: NULL";
        
        return "Book [isbn=" + isbn + ", title=" + title + 
            ", author=" + author + ", left=" + leftInfo + 
            ", right=" + rightInfo + "]";
    }

    //Kits help to pick out redundancy
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return isbn == book.isbn;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(isbn);
    }
}
