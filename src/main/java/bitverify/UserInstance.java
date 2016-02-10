package bitverify;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

import bitverify.crypto.Asymmetric;

public class UserInstance implements Serializable {
	private static UserInstance INSTANCE;
	// Maybe store some keys here?
	
	// Would like some way to serialize this please
	private AsymmetricCipherKeyPair mAsymmetricKeyPair;
	
	private UserInstance() {
		// How we define a user.
		// TODO: Figure out how we actually define a user.
		System.out.println("Generating a secure key pair.");
		mAsymmetricKeyPair = Asymmetric.generateNewKeyPair();
	}
	
	public AsymmetricCipherKeyPair getAsymmetricKeyPair() {
		return mAsymmetricKeyPair;
	}
	
	public static UserInstance getInstance() {
		// Solve some concurrency issues
		if (INSTANCE == null) {
			synchronized(UserInstance.class) {
				if (INSTANCE == null) {
					try{
						ObjectInputStream input = new ObjectInputStream(new FileInputStream("user_info.ser"));
						UserInstance instance = (UserInstance)input.readObject();
					} catch(Exception e) {
						INSTANCE = new UserInstance();
						try {
							ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("user_info.ser"));
						    oos.writeObject(INSTANCE);  
						    oos.close();  
						} catch (IOException e1) {
							// Life really sucks if we get to this point
							e1.printStackTrace();
						}  
					}
					
				}
				INSTANCE = new UserInstance();	
			}
		}
		return INSTANCE;
	}
	
	private Object readResolve() throws ObjectStreamException {
		return INSTANCE;
	}
	
	
}
