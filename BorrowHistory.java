/**
 * Helper class to represent a borrowing history entry.
 * Used by the Stack to track borrowing operations in LIFO order.
 * 
 * @author Senior Java Engineer
 * @version 1.0
 */
public class BorrowHistory {
    // Booking record properties
    private int isbn;
    private String title;
    private String author;
    private String borrowerID;
    
    // Constructs a new borrowing history record
    public BorrowHistory() {
        this.isbn = -1;
        this.title = "";
        this.author = "";
    }
    
    // --- Getter methods ---
    
    public int getIsbn() {
        return isbn;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getAuthor() {
        return author;
    }

    public String getBID() {
        return borrowerID;
    }
    
    // --- Setter methods ---
    
    public void setIsbn(int isbn) {
        this.isbn = isbn;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public void setBID(String bid) {
        this.borrowerID = bid;
    }
    // --- String Representation ---
    
    @Override
    public String toString() {
        return "BorrowHistory [isbn=" + isbn + 
            ", title=" + title + 
            ", author=" + author + 
            ", borrowerID=" + borrowerID + "]";
    }
}
