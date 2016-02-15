package bitverify.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;

public class Identity {
	
	private static final long NUM_OF_ENCRYPTION_ROUNDS = 1000;
	private static final long NUM_OF_MASTERPW_HASH_ROUNDS = 500000;
	
	private String name;
	private byte[] publicKey;
	private byte[] privateKey = null; //not null iff decrypted
	
	private boolean needsEncryption = false; //whether it needs encryption with master pw
	private boolean isEncrypted = false;
	private byte[] encryptedPrivateKey = null;
	
	Identity(){ }
	
	private Identity(String name, byte[] publicKey){
		this.name = name;
		this.publicKey = publicKey;
	}
	
	private Identity(String name, byte[] publicKey, byte[] privateKey, boolean isEncrypted, boolean needsEncryption){
		this(name, publicKey);
		this.needsEncryption = needsEncryption;
		this.isEncrypted = isEncrypted;
		if (isEncrypted){
			this.encryptedPrivateKey = privateKey;
		} else {
			this.privateKey = privateKey;
		}
	}
	
	public Identity(String name, byte[] publicKey, byte[] privateKey, String masterPassword){
		this(name, publicKey);
		this.needsEncryption = true;
		
		this.isEncrypted = false;
		this.privateKey = privateKey;
		encrypt(masterPassword);
	}
	
	public Identity(String name, AsymmetricCipherKeyPair keyPair, String masterPassword){
		this(name,
				Asymmetric.keyToByteKey(keyPair.getPublic()),
				Asymmetric.keyToByteKey(keyPair.getPrivate()),
				masterPassword);
	}
	
	public Identity(String name, byte[] publicKey, byte[] privateKey){
		this(name, publicKey);
		this.needsEncryption = false;
		
		this.privateKey = privateKey;
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

			boolean isEncrypted = d.readBoolean();
			
			if (isEncrypted){
				int encPrivateKeyLength = d.readInt();
				byte[] encPrivateKey = new byte[encPrivateKeyLength];
				d.readFully(encPrivateKey);
				return new Identity(name, publicKey, encPrivateKey, true, true);
			} else {
				int privateKeyLength = d.readInt();
				byte[] privateKey = new byte[privateKeyLength];
				d.readFully(privateKey);
				return new Identity(name, publicKey, privateKey, false, false);
			}
		}
	}
	
	public static Identity deserialize(byte[] data) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		return deserialize(in);
	}
	
	private void _serialize(OutputStream out) throws IOException {
		// DataOutputStream allows us to write primitives in binary form.
		try (DataOutputStream d = new DataOutputStream(out)) {
			d.writeUTF(name);
			
			d.writeInt(publicKey.length);
			d.write(publicKey);
			
			d.writeBoolean(isEncrypted);
			
			if (isEncrypted){
				d.writeInt(encryptedPrivateKey.length);
				d.write(encryptedPrivateKey);
			} else {
				d.writeInt(privateKey.length);
				d.write(privateKey);
			}
						
			d.flush();
		}
	}
	
	public void serialize(OutputStream out) throws IOException {
		if (needsEncryption == isEncrypted){
			_serialize(out);
		} else {
			throw new RuntimeException();
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
		if (isEncrypted) return;
		try {
			byte[] symKey = getSymmetricKey(masterPassword);
			if (symKey.length != Symmetric.KEY_LENGTH_IN_BYTES){
				//warning, using the fact here that we are using SHA256
				throw new RuntimeException("wrong symmetric key length");
			}
			byte[] tmp = privateKey;
			for (long round=0; round<NUM_OF_ENCRYPTION_ROUNDS; round++){
				tmp = Symmetric.encryptBytes(tmp, symKey);
			}
			encryptedPrivateKey = tmp;
		} catch (InvalidCipherTextException e) { //should never happen
			e.printStackTrace();
			throw new RuntimeException();
		}
		isEncrypted = true;
	}
	
	public void decrypt(String masterPassword) throws NotMatchingKeyException{
		if (!isEncrypted) return;
		try {
			byte[] symKey = getSymmetricKey(masterPassword);
			if (symKey.length != Symmetric.KEY_LENGTH_IN_BYTES){
				//warning, using the fact here that we are using SHA256
				throw new RuntimeException("wrong symmetric key length");
			}
			byte[] tmp = encryptedPrivateKey;
			for (long round=0; round<NUM_OF_ENCRYPTION_ROUNDS; round++){
				tmp = Symmetric.decryptBytes(tmp, symKey);
			}
			privateKey = tmp;
		} catch (InvalidCipherTextException e) {
			e.printStackTrace();
			throw new NotMatchingKeyException("wrong password");
		}
		isEncrypted = false;
	}
	
	public String getName(){
		return name;
	}
	
	public byte[] getPublicKey(){
		return publicKey;
	}
	
	public byte[] getPrivateKey(){
		return privateKey;
	}
	
	public boolean isEncrypted(){
		return isEncrypted;
	}
	
	public boolean getNeedsEncryption(){
		return needsEncryption;
	}
	
	public AsymmetricCipherKeyPair getKeyPair() throws KeyDecodingException{
		if (isEncrypted) return null;
		return Asymmetric.getKeyPairFromByteKeys(publicKey, privateKey);
	}
	
}
