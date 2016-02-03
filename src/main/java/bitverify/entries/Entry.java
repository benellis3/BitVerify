package bitverify.entries;

import java.io.IOException;

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

	@DatabaseField(id = true)
	private long entryID;

	@DatabaseField
	private long entryTimeStamp;
	private transient String entryHashSigned = "";

	@DatabaseField
	private String uploaderID;
	@DatabaseField
	private String receiverID = ""; //optional

	// TODO: database storage - a byte[] of metadata would be convenient but we will discuss this
	private transient Metadata metadata = null;
	private Object encMetadata;
	private String encryptedSymmetricKey = null;
	
	private void _constructEntryCore(AsymmetricCipherKeyPair uploaderKeyPair, Metadata metadata) throws IOException{
		entryID = 0; //TODO
		entryTimeStamp = java.lang.System.currentTimeMillis();
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
	
	public Entry(AsymmetricCipherKeyPair uploaderKeyPair, Metadata metadata, String receiverID) throws IOException, StringKeyDecodingException{
		_constructEntryCore(uploaderKeyPair, metadata);
		
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
	
	private String hashEntry(){
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
