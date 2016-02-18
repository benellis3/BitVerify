package bitverify.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import org.bouncycastle.asn1.dvcs.Data;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;

public class Identity {
	
	private static final long NUM_OF_ENCRYPTION_ROUNDS = 1000;
	private static final long NUM_OF_MASTERPW_HASH_ROUNDS = 500000;

	@DatabaseField(generatedId = true)
	private int id;

	private static final int DESCRIPTION_LENGTH = 255;
	@DatabaseField(width = DESCRIPTION_LENGTH)
	private String description;

	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] publicKey;

	private byte[] decryptedPrivateKey = null; //do not persist to DB

	@DatabaseField
	private boolean needsEncryption = false; //whether it needs encryption with master pw

	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] privateKey = null; //in encrypted form IF needsEcryption is true
		//same as decryptedPrivateKey otherwise
	
	Identity(){ }
	
	private Identity(String description, byte[] publicKey){
		this.setDescription(description);
		this.publicKey = publicKey;
	}
	
	private Identity(String description, byte[] publicKey, byte[] privateKey, boolean needsEncryption){
		this(description, publicKey);
		this.needsEncryption = needsEncryption;
		if (needsEncryption){
			this.privateKey = privateKey;
		} else {
			this.privateKey = privateKey;
			this.decryptedPrivateKey = privateKey;
		}
		
	}
	
	public Identity(String description, byte[] publicKey, byte[] decryptedPrivateKey, String masterPassword){
		this(description, publicKey);
		this.needsEncryption = true;
		
		this.decryptedPrivateKey = decryptedPrivateKey;
		encrypt(masterPassword);
	}
	
	public Identity(String description, AsymmetricCipherKeyPair keyPair, String masterPassword){
		this(description,
				Asymmetric.keyToByteKey(keyPair.getPublic()),
				Asymmetric.keyToByteKey(keyPair.getPrivate()),
				masterPassword);
	}
	
	public Identity(String description, byte[] publicKey, byte[] decryptedPrivateKey){
		this(description, publicKey);
		this.needsEncryption = false;
		
		this.privateKey = decryptedPrivateKey;
		this.decryptedPrivateKey = decryptedPrivateKey;
	}
	
	public Identity(String name, AsymmetricCipherKeyPair keyPair){
		this(name,
				Asymmetric.keyToByteKey(keyPair.getPublic()),
				Asymmetric.keyToByteKey(keyPair.getPrivate()));
	}
	
	public static Identity deserialize(InputStream in) throws IOException {
		// DataInputStream allows us to read in primitives in binary form.
		try (DataInputStream d = new DataInputStream(in)) {
			String name = d.readUTF();
			
			int publicKeyLength = d.readInt();
			byte[] publicKey = new byte[publicKeyLength];
			d.readFully(publicKey);

			boolean needsEncryption = d.readBoolean();
			
			int privateKeyLength = d.readInt();
			byte[] privateKey = new byte[privateKeyLength];
			d.readFully(privateKey);
			
			return new Identity(name, publicKey, privateKey, needsEncryption);
		}
	}
	
	public static Identity deserialize(byte[] data) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		return deserialize(in);
	}
	
	public void serialize(OutputStream out) throws IOException {
		// DataOutputStream allows us to write primitives in binary form.
		try (DataOutputStream d = new DataOutputStream(out)) {
			d.writeUTF(description);
			
			d.writeInt(publicKey.length);
			d.write(publicKey);
			
			d.writeBoolean(needsEncryption);
						
			d.writeInt(privateKey.length);
			d.write(privateKey);
						
			d.flush();
		}
	}
	
	public byte[] serialize() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		serialize(out);
		return out.toByteArray();
	}
	
	private byte[] getSymmetricKey(String masterPassword){
		byte[] ret = masterPassword.getBytes(StandardCharsets.UTF_8);
		for (long round=0; round<NUM_OF_MASTERPW_HASH_ROUNDS; round++){
			ret = Hash.hashBytes(ret);
		}
		return ret;
	}
	
	private void encrypt(String masterPassword){
		try {
			byte[] symKey = getSymmetricKey(masterPassword);
			if (symKey.length != Symmetric.KEY_LENGTH_IN_BYTES){
				//warning, using the fact here that we are using SHA256
				throw new RuntimeException("wrong symmetric key length");
			}
			byte[] tmp = decryptedPrivateKey;
			for (long round=0; round<NUM_OF_ENCRYPTION_ROUNDS; round++){
				tmp = Symmetric.encryptBytes(tmp, symKey);
			}
			privateKey = tmp;
		} catch (InvalidCipherTextException e) { //should never happen
			e.printStackTrace();
			throw new RuntimeException();
		}
	}
	
	public void decrypt(String masterPassword) throws NotMatchingKeyException{
		try {
			byte[] symKey = getSymmetricKey(masterPassword);
			if (symKey.length != Symmetric.KEY_LENGTH_IN_BYTES){
				//warning, using the fact here that we are using SHA256
				throw new RuntimeException("wrong symmetric key length");
			}
			byte[] tmp = privateKey;
			for (long round=0; round<NUM_OF_ENCRYPTION_ROUNDS; round++){
				tmp = Symmetric.decryptBytes(tmp, symKey);
			}
			decryptedPrivateKey = tmp;
		} catch (InvalidCipherTextException e) {
			e.printStackTrace();
			throw new NotMatchingKeyException("wrong password");
		}
	}
	
	public String getDescription(){
		return description;
	}
	
	public void setDescription(String description){
		if (description.length() > DESCRIPTION_LENGTH)
			throw new IllegalArgumentException("Description must be at most " + DESCRIPTION_LENGTH + " characters long.");
		this.description = description;
	}
	
	public byte[] getPublicKey(){
		return publicKey;
	}
	
	/**
	 * @return private key in byteKey format (see Asymmetric.java).
	 * 	Returns null if private key is not decrypted. In that case, you need to call
	 * 	decrypt() first.
	 */
	public byte[] getPrivateKey(){
		return decryptedPrivateKey;
	}
	
	public boolean getNeedsEncryption(){
		return needsEncryption;
	}
	
	public AsymmetricCipherKeyPair getKeyPair() throws KeyDecodingException{
		if (decryptedPrivateKey == null) return null;
		return Asymmetric.getKeyPairFromByteKeys(publicKey, decryptedPrivateKey);
	}
	
}
