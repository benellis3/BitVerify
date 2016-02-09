package bitverify.entries;

import java.io.*;
import java.util.UUID;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.util.encoders.Hex;

import bitverify.crypto.Asymmetric;
import bitverify.crypto.DataSizeException;
import bitverify.crypto.Hash;
import bitverify.crypto.KeyDecodingException;
import bitverify.crypto.NotMatchingKeyException;
import bitverify.crypto.Symmetric;

@DatabaseTable
public class Entry {

	@DatabaseField
	private UUID entryID = UUID.randomUUID();

	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] blockID;

	// we should store hashes and keys as byte arrays.
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] entryHashSigned;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] uploaderID;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] receiverID = new byte[0];

	@DatabaseField
	private long entryTimeStamp;

	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] metadataBytes = new byte[0];

	private Metadata metadataObject = null;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] encryptedSymmetricKey = new byte[0];
	
	private void _constructEntryCore(AsymmetricCipherKeyPair uploaderKeyPair, Metadata metadata) throws KeyDecodingException{
		entryTimeStamp = System.currentTimeMillis();
		this.uploaderID = Asymmetric.keyToByteKey( uploaderKeyPair.getPublic() );
		this.metadataObject = metadata;
	}

	// no-argument constructor required for database framework
	Entry() { }

	public Entry(AsymmetricCipherKeyPair uploaderKeyPair, Metadata metadata) throws KeyDecodingException, IOException{
		_constructEntryCore(uploaderKeyPair, metadata);
		
		//and finally:
		finalise(uploaderKeyPair);
	}
	
	public Entry(AsymmetricCipherKeyPair uploaderKeyPair, Metadata metadata, byte[] receiverID) throws KeyDecodingException, IOException{
		_constructEntryCore(uploaderKeyPair, metadata);

		if (!Asymmetric.isValidKey(receiverID)){
			throw new KeyDecodingException();
		}
		this.receiverID = receiverID;
		
		//and finally:
		finalise(uploaderKeyPair);
	}
	
	private Entry(UUID entryID, byte[] entryHashSigned, byte[] uploaderID, byte[] receiverID, long entryTimeStamp,
			byte[] metadataBytes, byte[] encryptedSymmetricKey) throws IOException{
		this.entryID = entryID;
		this.entryHashSigned = entryHashSigned;
		this.uploaderID = uploaderID;
		this.receiverID = receiverID;
		this.entryTimeStamp = entryTimeStamp;
		this.metadataBytes = metadataBytes;
		this.encryptedSymmetricKey = encryptedSymmetricKey;
		
		if (!isPrivatelyShared()){
			ByteArrayInputStream in = new ByteArrayInputStream(metadataBytes);
			metadataObject = Metadata.deserialize(in);
		}
	}
	
	private void finalise(AsymmetricCipherKeyPair uploaderKeyPair)
			throws IOException, DataLengthException, KeyDecodingException{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		metadataObject.serialize(out);
		metadataBytes = out.toByteArray();
		
		if (isPrivatelyShared()){ //encrypt
			try {
					byte[] symKey = Symmetric.generateKey();
					metadataBytes = Symmetric.encryptBytes(metadataBytes, symKey);
					AsymmetricKeyParameter receiverKey = Asymmetric.byteKeyToKey(receiverID);
					encryptedSymmetricKey = Asymmetric.encryptBytes(symKey, receiverKey);
				
			} catch (DataSizeException | InvalidCipherTextException e) { //should never happen
				e.printStackTrace();
				throw new RuntimeException();
			}
		}
		
		hashAndSignEntry( uploaderKeyPair.getPrivate() );
	}
	
	public void decrypt(AsymmetricCipherKeyPair receiverKeyPair)
			throws NotMatchingKeyException, KeyDecodingException, InvalidCipherTextException, IOException{
		if (!isPrivatelyShared()) return;
		
		String providedPublicKey = Asymmetric.keyToStringKey(receiverKeyPair.getPublic());
		String expectedPublicKey = Asymmetric.byteKeyToStringKey(receiverID);
		if (!providedPublicKey.equals(expectedPublicKey)){
			throw new NotMatchingKeyException();
		}
		
		byte[] symKey = Asymmetric.decryptBytes(encryptedSymmetricKey, receiverKeyPair.getPrivate());
		byte[] metadataBytes_decrypted = Symmetric.decryptBytes(metadataBytes, symKey);
		
		ByteArrayInputStream in = new ByteArrayInputStream(metadataBytes_decrypted);
		metadataObject = Metadata.deserialize(in);
	}

	public static Entry deserialize(InputStream in) throws IOException {
		// DataInputStream allows us to read in primitives in binary form.
		try (DataInputStream d = new DataInputStream(in)) {
			long entryIDMostSigBits = d.readLong();
			long entryIDLeastSigBits = d.readLong();
			UUID entryID = new UUID(entryIDMostSigBits, entryIDLeastSigBits);
			
			int entryHashSignedLength = d.readInt();
			byte[] entryHashSigned = new byte[entryHashSignedLength];
			d.readFully(entryHashSigned);

			int uploaderIDLength = d.readInt();
			byte[] uploaderID = new byte[uploaderIDLength];
			d.readFully(uploaderID);

			int receiverIDLength = d.readInt();
			byte[] receiverID = new byte[receiverIDLength];
			d.readFully(receiverID);

			long entryTimeStamp = d.readLong();

			int metadataLength = d.readInt();
			byte[] metadataBytes = new byte[metadataLength];
			d.readFully(metadataBytes);
			
			int encryptedSymmetricKeyLength = d.readInt();
			byte[] encryptedSymmetricKey = new byte[encryptedSymmetricKeyLength];
			d.readFully(encryptedSymmetricKey);

			return new Entry(entryID, entryHashSigned, uploaderID, receiverID, entryTimeStamp, metadataBytes, encryptedSymmetricKey);
		}
	}
	
	public static Entry deserialize(byte[] data) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		return Entry.deserialize(in);
	}
	
	private void _serialize(OutputStream out, boolean includeHash) throws IOException {
		// DataOutputStream allows us to write primitives in binary form.
		try (DataOutputStream d = new DataOutputStream(out)) {
			// write out each field in binary form, in declaration order.
			d.writeLong(entryID.getMostSignificantBits());
			d.writeLong(entryID.getLeastSignificantBits());
			
			if (includeHash){
				d.writeInt(entryHashSigned.length);
				d.write(entryHashSigned);
			}
			
			d.writeInt(uploaderID.length);
			d.write(uploaderID);
			
			d.writeInt(receiverID.length);
			d.write(receiverID);
			
			d.writeLong(entryTimeStamp);
			
			d.writeInt(metadataBytes.length);
			d.write(metadataBytes);
			
			d.writeInt(encryptedSymmetricKey.length);
			d.write(encryptedSymmetricKey);
			
			d.flush();
		}
	}
	
	public void serialize(OutputStream out) throws IOException {
		_serialize(out, true);
	}
	
	public byte[] serialize() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		serialize(out);
		return out.toByteArray();
	}
	
	private void serializeForHashing(OutputStream out) throws IOException {
		_serialize(out, false);
	}

	private byte[] hashEntry() throws IOException{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		serializeForHashing(out);
		byte[] serialisedEntry = out.toByteArray();
		return Hash.hashBytes(serialisedEntry);
	}
	
	private void hashAndSignEntry(AsymmetricKeyParameter privKey) throws IOException{
		byte[] entryHash = hashEntry();
		try {
			entryHashSigned = Asymmetric.encryptBytes(entryHash, privKey);
		} catch (InvalidCipherTextException | DataSizeException e) {
			//this should never happen
			e.printStackTrace();
			throw new RuntimeException();
		}
	}
	
	public boolean testEntryHashSignature() throws IOException{
		//calculate hash
		byte[] calculatedEntryHash = hashEntry();
		//decode stored hash
		AsymmetricKeyParameter uploaderPubKey;
		try {
			uploaderPubKey = Asymmetric.byteKeyToKey(uploaderID);
		} catch (KeyDecodingException e) {
			e.printStackTrace();
			System.err.println("Cannot decode public key stored in entry.");
			return false;
		}
		byte[] decodedEntryHash;
		try {
			decodedEntryHash = Asymmetric.decryptBytes(entryHashSigned, uploaderPubKey);
		} catch (InvalidCipherTextException e) {
			return false;
		}
		//compare the two
		String calcHash = Hex.toHexString(calculatedEntryHash);
		String decodedHash = Hex.toHexString(decodedEntryHash);
		return calcHash.equals(decodedHash);
	}
	
	public boolean isPrivatelyShared(){
		return receiverID.length != 0;
	}
	
	public UUID getEntryID(){
		return entryID;
	}
	
	public long getEntryTimeStamp(){
		return entryTimeStamp;
	}
	
	public byte[] getUploaderID(){
		return uploaderID;
	}
	
	public byte[] getReceiverID(){
		return receiverID;
	}
	
	public Metadata getMetadata(){
		return metadataObject;
	}
	
}
