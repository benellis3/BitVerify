package bitverify.entries;

import java.io.*;
import java.security.Key;
import java.security.KeyPair;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;

import bitverify.crypto.Asymmetric;
import bitverify.crypto.DataSizeException;
import bitverify.crypto.Hash;
import bitverify.crypto.StringKeyDecodingException;

@DatabaseTable
public class Entry {

	private transient String entryHashSigned = "";

	// we should store hashes and keys as byte arrays.
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] fileHash;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] uploaderID;
	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] receiverID; //optional

	@DatabaseField
	private long entryTimeStamp;

	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	private byte[] metadata;

	// TODO: database storage - a byte[] of metadata would be convenient but we will discuss this
	// private transient Metadata metadata = null;
	private Object encMetadata;
	private String encryptedSymmetricKey = null;
	
	private void _constructEntryCore(AsymmetricCipherKeyPair uploaderKeyPair, Metadata metadata) throws IOException{

		entryTimeStamp = System.currentTimeMillis();
		// TODO: fix for byte array field.
		this.uploaderID = Asymmetric.keyToStringKey( uploaderKeyPair.getPublic() );
		this.metadata = metadata;
	}

	// no-argument constructor required for database framework
	Entry() { }

	public Entry(AsymmetricCipherKeyPair uploaderKeyPair, Metadata metadata) throws IOException{
		_constructEntryCore(uploaderKeyPair, metadata);
		
		//and finally:
		finalise(uploaderKeyPair);
	}
	
	public Entry(AsymmetricCipherKeyPair uploaderKeyPair, Metadata metadata, byte[] receiverID) throws IOException, StringKeyDecodingException{
		_constructEntryCore(uploaderKeyPair, metadata);

		// TODO: fix for byte array field.
		if (!Asymmetric.isValidStringKey(receiverID)){
			throw new StringKeyDecodingException();
		}
		this.receiverID = receiverID;
		
		//and finally:
		finalise(uploaderKeyPair);
	}
	
	private void finalise(AsymmetricCipherKeyPair uploaderKeyPair){
		//TODO generate symmetricKey
		//TODO encMetadata = AES_encrypt(metadata, symmetricKey);
		//TODO encryptedSymmetricKey = RSA_encrypt(symmetricKey, receiverID);
		//     note: if shared publicly, use uploader's private key to encrypt (not receiverID - public key)
		metadata = null; //delete clear text metadata
		hashAndSignEntry( uploaderKeyPair.getPrivate() );
	}


	public static Entry deserialize(InputStream in) throws IOException {
		// DataInputStream allows us to read in primitives in binary form.
		try (DataInputStream d = new DataInputStream(in)) {
			byte[] fileHash = new byte[Hash.HASH_LENGTH];
			d.readFully(fileHash);

			// TODO: ascertain key length
			int KEY_LENGTH = 0;
			byte[] uploaderID = new byte[KEY_LENGTH];
			d.readFully(uploaderID);
			byte[] receiverID = new byte[KEY_LENGTH];
			d.readFully(receiverID);

			long timeStamp = d.readLong();

			int metadataLength = d.readInt();

			byte[] metadata = new byte[metadataLength];
			d.readFully(metadata);

			// TODO: provide suitable constructor.
			return new Entry(/* arguments from above */);
		}
	}

	public void serialize(OutputStream out) throws IOException {
		// DataOutputStream allows us to write primitives in binary form.
		try (DataOutputStream d = new DataOutputStream(out)) {
			// write out each field in binary form, in declaration order.
			d.write(fileHash);
			d.write(uploaderID);
			d.write(receiverID);
			d.writeLong(entryTimeStamp);
			// write the length of the metadata, so we know where it ends.
			d.writeInt(metadata.length);
			d.write(metadata);
		}
	}


	// TODO: I believe we will only ever hash a series of entries in a block, not an individual one.

	private String hash(){


		byte[] serialisedEntry;
		//TODO implement serialisation for Entry
		//Serialisation MUST NOT include entryHashSigned (or alternatively, treat it as "")
		//see fields marked as transient
		serialisedEntry = new byte[0];

		return Hash.hashBytes(serialisedEntry);
	}
	
	private void hashAndSignEntry(AsymmetricKeyParameter privKey){
		String entryHash = hashEntry();
		try {
			entryHashSigned = Asymmetric.encryptHexString(entryHash, privKey);
		} catch (InvalidCipherTextException | DataSizeException e) {
			//this should never happen
			e.printStackTrace();
		}
	}
	
	public boolean testEntryHashSignature(){
		String calculatedEntryHash = hashEntry();
		AsymmetricKeyParameter uploaderPubKey;
		try {
			uploaderPubKey = Asymmetric.stringKeyToKey(uploaderID);
		} catch (StringKeyDecodingException e) {
			e.printStackTrace();
			System.err.println("Cannot decode public key stored in entry.");
			return false;
		}
		String decodedEntryHash;
		try {
			decodedEntryHash = Asymmetric.decryptHexString(entryHashSigned, uploaderPubKey);
		} catch (InvalidCipherTextException e) {
			return false;
		}
		return calculatedEntryHash.equals(decodedEntryHash);
	}
	
	public boolean isPrivatelyShared(){
		return receiverID.equals("");
	}
	
	public long getEntryID(){
		return entryID;
	}
	
	public long getEntryTimeStamp(){
		return entryTimeStamp;
	}
	
	public String getUploaderID(){
		return uploaderID;
	}
	
	public String getReceiverID(){
		return receiverID;
	}
	
	public Metadata getMetadata(){
		return metadata;
	}
	
}
