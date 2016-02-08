package bitverify.crypto;

public class KeyDecodingException extends Exception {
	
	private static final long serialVersionUID = 936738228240705550L;

	public KeyDecodingException(String message) {
        super(message);
    }
	
	public KeyDecodingException() {
        super();
    }
	
}
