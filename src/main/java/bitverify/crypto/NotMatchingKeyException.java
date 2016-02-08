package bitverify.crypto;

public class NotMatchingKeyException extends Exception {
	
	private static final long serialVersionUID = 2901867119861975498L;

	public NotMatchingKeyException(String message) {
        super(message);
    }
	
	public NotMatchingKeyException() {
        super();
    }
	
}
