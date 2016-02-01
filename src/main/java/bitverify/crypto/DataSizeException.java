package bitverify.crypto;

public class DataSizeException extends Exception {

	private static final long serialVersionUID = 5073074868656945143L;
	
	public DataSizeException(String message) {
        super(message);
    }
	
	public DataSizeException() {
        super();
    }
	
}
