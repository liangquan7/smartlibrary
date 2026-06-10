import java.util.Base64;

/**
 * Represents a borrower of the library.
 * Each element stores the ID of the user and the corresponding login-key.
 * The key is encrypted by XOR
 * 
 * @author Luo Yan Hao
 * @version 1.0
 */
public class Borrower {
    private String id;
    private String key;

    private static final byte KEY = 0x42;

    /**
     * Creates a new valid borrower
     * 
     * @param id the borrower's ID
     * @param key the borrower's login key
     */
    public Borrower(String id, String key){
        this.id = id;
        this.key = decrypt(key);
    }

    /**
     * Encrypts a Base64 string with XOR
     * 
     * @param plainText A {@code String} requires encryption or decryption with {@code KEY}
     * @return Encrypted/Decrypted string
     */
    private static String encrypt(String plainText) {
        byte[] bytes = plainText.getBytes();
        byte[] encrypted = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            encrypted[i] = (byte) (bytes[i] ^ KEY);
        }
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypts a Base64 string back to plaintext with XOR
     */
    private static String decrypt(String cipherText) {
        byte[] decodedBytes = Base64.getDecoder().decode(cipherText);
        byte[] decrypted = new byte[decodedBytes.length];
        
        for (int i = 0; i < decodedBytes.length; i++) {
            decrypted[i] = (byte) (decodedBytes[i] ^ KEY);
        }
        
        return new String(decrypted);
    }

    //Getters
    public String getID(){ return id;}

    /**
     * Matches a input key
     * 
     * @param inputKey
     * @return {@code true} for successful match 
     */
    public boolean verify(String inputKey){
        return inputKey.equals(key);
    }

    /**
     * Returns a string representation of the the borrower.
     * Includes ID, Encrypted Key, Num of Books Currently borrowed.
     * 
     * @return String representation of the borrower
     */
    @Override
    public String toString() {
        return id + "," + encrypt(key); 
    }
}