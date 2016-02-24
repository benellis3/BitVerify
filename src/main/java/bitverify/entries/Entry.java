package bitverify.entries;

import java.io.*;
import java.util.Date;
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

	@DatabaseField(id = true)
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

	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] encryptedSymmetricKey = new byte[0];

	// Used by database to remember whether an entry is part of the active blockchain.
	@DatabaseField
	private boolean confirmed;
	
	// --> metadata
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] docHash = null;

	private static final int DOC_LINK_LENGTH = 2048;
	@DatabaseField(width = DOC_LINK_LENGTH)
	private String docLink = null; //e.g. magnet link

	private static final int DOC_NAME_LENGTH = 255;
	@DatabaseField(columnDefinition = "VARCHAR_IGNORECASE("+DOC_NAME_LENGTH+")")
	private String docName = null;

	private static final int DOC_DESCRIPTION_LENGTH = 2048;
	@DatabaseField(columnDefinition = "VARCHAR_IGNORECASE("+DOC_DESCRIPTION_LENGTH+")")
	private String docDescription = null;

	private static final int DOC_LOCATION_LENGTH = 255;
	@DatabaseField(width = DOC_LOCATION_LENGTH)	
	private String docGeoLocation = null;
	
	@DatabaseField
	private long docTimeStamp = 0;
	
	private String[] docTags = null;
	// <-- metadata
	
	private void _constructEntryCore(AsymmetricCipherKeyPair uploaderKeyPair,
			byte[] docHash, String linkToDownloadFile, String docName, String docDescription,
			String docGeoLocation, long docTimeStamp, String[] tags) throws KeyDecodingException{
		entryTimeStamp = System.currentTimeMillis();
		this.uploaderID = Asymmetric.keyToByteKey( uploaderKeyPair.getPublic() );
		setMetadataFields(docHash, linkToDownloadFile, docName, docDescription,
				docGeoLocation, docTimeStamp, tags);
	}

	// no-argument constructor required for database framework
	Entry() { }

	public Entry(AsymmetricCipherKeyPair uploaderKeyPair, byte[] docHash, String docLink,
				 String docName, String docDescription,
				 String docGeoLocation, long docTimeStamp, String[] docTags) throws KeyDecodingException, IOException{
		_constructEntryCore(uploaderKeyPair, docHash, docLink,
				docName, docDescription, docGeoLocation, docTimeStamp, docTags);
		
		//and finally:
		finalise(uploaderKeyPair);
	}
	
	public Entry(AsymmetricCipherKeyPair uploaderKeyPair, byte[] receiverID,
				 byte[] docHash, String docLink, String docName, String docDescription,
				 String docGeoLocation, long docTimeStamp, String[] docTags) throws KeyDecodingException, IOException{
		_constructEntryCore(uploaderKeyPair, docHash, docLink,
				docName, docDescription, docGeoLocation, docTimeStamp, docTags);

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
			deserializeMetadata(metadataBytes);
		}
	}
	
	private void finalise(AsymmetricCipherKeyPair uploaderKeyPair)
			throws IOException, DataLengthException, KeyDecodingException{
		metadataBytes = serializeMetadata();
		
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
		deserializeMetadata(in);
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
	
	public byte[] serialize() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			serialize(out);
		} catch (IOException e) {
			// we are in control of creating the stream here so this will never happen, but just in case...
			e.printStackTrace();
		}
		return out.toByteArray();
	}
	
	private void serializeForHashing(OutputStream out) throws IOException {
		_serialize(out, false);
	}

	private byte[] hashEntry() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			serializeForHashing(out);
		} catch (IOException e) {
			// we are in control of creating the stream here so this will never happen, but just in case...
			e.printStackTrace();
		}
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
	
	public boolean testEntryHashSignature() {
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
	
	public String getEntryTimeStampString() {
		return new Date(getEntryTimeStamp()).toString();
	}
	
	public byte[] getUploaderID(){
		return uploaderID;
	}
	
	public byte[] getReceiverID(){
		return receiverID;
	}

	public void setBlockID(byte[] blockID) {
		this.blockID = blockID;
	}

	public void setConfirmed(boolean confirmed) {
		this.confirmed = confirmed;
	}
	
	
	// ------------------------------------> metadata methods
	
	private void setMetadataFields(byte[] docHash, String docLink, String docName, String docDescription,
			String docGeoLocation, long docTimeStamp, String[] docTags){
		this.docHash = docHash;
		if (docLink.length() > DOC_LINK_LENGTH)
			throw new IllegalArgumentException("Link must be at most " + DOC_LINK_LENGTH + " characters long");
		this.docLink = docLink;

		if (docName.length() > DOC_NAME_LENGTH)
			throw new IllegalArgumentException("Name must be at most " + DOC_NAME_LENGTH + " characters long");
		this.docName = docName;

		if (docDescription.length() > DOC_DESCRIPTION_LENGTH)
			throw new IllegalArgumentException("Description must be at most " + DOC_DESCRIPTION_LENGTH + " characters long");
		this.docDescription = docDescription;

		if (docGeoLocation.length() > DOC_LOCATION_LENGTH)
			throw new IllegalArgumentException("GeoLocation must be at most " + DOC_LOCATION_LENGTH + " characters long");
		this.docGeoLocation = docGeoLocation;

		this.docTimeStamp = docTimeStamp;
		this.docTags = docTags;
	}
	
	private void deserializeMetadata(InputStream in) throws IOException {
		// DataInputStream allows us to read in primitives in binary form.
		try (DataInputStream d = new DataInputStream(in)) {
			int docHashLength = d.readInt();
			byte[] docHash = new byte[docHashLength];
			d.readFully(docHash);
			
			String linkToDownloadFile = d.readUTF();
			String docName = d.readUTF();
			String docDescription = d.readUTF();
			String docGeoLocation = d.readUTF();
			long docTimeStamp = d.readLong();
			
			int numTags = d.readInt();
			String[] tags = new String[numTags];
			for (int i=0; i<numTags; i++){
				tags[i] = d.readUTF();
			}

			setMetadataFields(docHash, linkToDownloadFile, docName, docDescription,
					docGeoLocation, docTimeStamp, tags);
		}
	}
	
	private void deserializeMetadata(byte[] data) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		deserializeMetadata(in);
	}
	
	private void serializeMetadata(OutputStream out) throws IOException {
		// DataOutputStream allows us to write primitives in binary form.
		try (DataOutputStream d = new DataOutputStream(out)) {
			// write out each field in binary form, in declaration order.
			d.writeInt(docHash.length); //not strictly needed, but just to make sure
			d.write(docHash);
			
			d.writeUTF(docLink);
			d.writeUTF(docName);
			d.writeUTF(docDescription);
			d.writeUTF(docGeoLocation);
			d.writeLong(docTimeStamp);
			
			d.writeInt(docTags.length);
			for (String tag : docTags) {
				d.writeUTF(tag);
			}

			d.flush();
		}
	}
	
	public byte[] serializeMetadata() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		serializeMetadata(out);
		return out.toByteArray();
	}
	
	public byte[] getDocHash(){
		return docHash;
	}
	
	public String getDocLink(){
		return docLink;
	}
	
	public String getDocName(){
		return docName;
	}
	
	public String getDocDescription(){
		return docDescription;
	}
	
	public String getDocGeoLocation(){
		return docGeoLocation;
	}
	
	public long getDocTimeStamp(){
		return docTimeStamp;
	}

	/**
	 * Gets the document tags. May be null.
     */
	public String[] getDocTags(){
		return docTags;
	}
	
	// <------------------------------------ metadata methods
}
