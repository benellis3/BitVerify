package bitverify.entries;

import static org.junit.Assert.*;

import java.io.IOException;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import bitverify.crypto.Asymmetric;
import bitverify.crypto.AsymmetricTest;
import bitverify.crypto.KeyDecodingException;

public class EntryTest {
		
	public static Entry generateEntry1() throws KeyDecodingException, IOException{
		AsymmetricCipherKeyPair uploaderKeyPair = 
				Asymmetric.getKeyPairFromStringKeys(AsymmetricTest.myPubKey2, AsymmetricTest.myPrivKey2);
		Metadata metadata = MetadataTest.generateMetadata1();
		Entry entry = new Entry(uploaderKeyPair, metadata);
		return entry;
	}
	
	public static Entry generateEntry2() throws KeyDecodingException, IOException{
		AsymmetricCipherKeyPair uploaderKeyPair = 
				Asymmetric.getKeyPairFromStringKeys(AsymmetricTest.myPubKey2, AsymmetricTest.myPrivKey2);
		Metadata metadata = MetadataTest.generateMetadata1();
		byte[] receiverID = Asymmetric.stringKeyToByteKey(AsymmetricTest.myPubKey);
		Entry entry = new Entry(uploaderKeyPair, metadata, receiverID);
		return entry;
	}

	@Test
	public void testSerialize_public() {
		Entry entry1;
		try {
			entry1 = generateEntry1();
		} catch (KeyDecodingException | IOException e) {
			fail();
			return;
		}		
		//serialize
		byte[] entry1Bytes;
		try {
			entry1Bytes = entry1.serialize();
		} catch (IOException e) {
			fail();
			return;
		}
		//deserialize
		Entry entry1B;
		try {
			entry1B = Entry.deserialize(entry1Bytes);
		} catch (IOException e) {
			fail();
			return;
		}
		//compare entry-level fields
		assertEquals(entry1.getEntryID(), entry1B.getEntryID());
		assertEquals(entry1.getEntryTimeStamp(), entry1B.getEntryTimeStamp());
		assertEquals(Hex.toHexString(entry1.getUploaderID()), Hex.toHexString(entry1B.getUploaderID()));
		assertEquals(Hex.toHexString(entry1.getReceiverID()), Hex.toHexString(entry1B.getReceiverID()));
		//compare metadata
		Metadata meta1 = entry1.getMetadata();
		Metadata meta2 = entry1B.getMetadata();
		assertEquals(meta1.getDocHash(), meta2.getDocHash());
		assertEquals(meta1.getLinkToDownloadFile(), meta2.getLinkToDownloadFile());
		assertEquals(meta1.getDocName(), meta2.getDocName());
		assertEquals(meta1.getDocDescription(), meta2.getDocDescription());
		assertEquals(meta1.getDocGeoLocation(), meta2.getDocGeoLocation());
		assertEquals(meta1.getDocTimeStamp(), meta2.getDocTimeStamp());
		
		int numTags = meta1.getTags().length;
		assertEquals(meta1.getTags().length, meta2.getTags().length);
		for (int i=0; i<numTags; i++){
			assertEquals(meta1.getTags()[i], meta2.getTags()[i]);
		}
	}
	
	@Test
	public void testSerialize_private() {
		Entry entry2;
		try {
			entry2 = generateEntry2();
		} catch (KeyDecodingException | IOException e) {
			fail();
			return;
		}		
		//serialize
		byte[] entry2Bytes;
		try {
			entry2Bytes = entry2.serialize();
		} catch (IOException e) {
			fail();
			return;
		}
		//deserialize
		Entry entry2B;
		try {
			entry2B = Entry.deserialize(entry2Bytes);
		} catch (IOException e) {
			fail();
			return;
		}
		//compare entry-level fields
		assertEquals(entry2.getEntryID(), entry2B.getEntryID());
		assertEquals(entry2.getEntryTimeStamp(), entry2B.getEntryTimeStamp());
		assertEquals(Hex.toHexString(entry2.getUploaderID()), Hex.toHexString(entry2B.getUploaderID()));
		assertEquals(Hex.toHexString(entry2.getReceiverID()), Hex.toHexString(entry2B.getReceiverID()));
		//decrypt
		AsymmetricCipherKeyPair receiverKeyPair;
		try {
			receiverKeyPair = Asymmetric.getKeyPairFromStringKeys(AsymmetricTest.myPubKey, AsymmetricTest.myPrivKey);
		} catch (KeyDecodingException e) {
			fail();
			return;
		}
		try {
			entry2B.decrypt(receiverKeyPair);
		} catch (Exception e) {
			fail();
			return;
		}
		//compare metadata
		Metadata meta1 = entry2.getMetadata();
		Metadata meta2 = entry2B.getMetadata();
		assertEquals(meta1.getDocHash(), meta2.getDocHash());
		assertEquals(meta1.getLinkToDownloadFile(), meta2.getLinkToDownloadFile());
		assertEquals(meta1.getDocName(), meta2.getDocName());
		assertEquals(meta1.getDocDescription(), meta2.getDocDescription());
		assertEquals(meta1.getDocGeoLocation(), meta2.getDocGeoLocation());
		assertEquals(meta1.getDocTimeStamp(), meta2.getDocTimeStamp());
		
		int numTags = meta1.getTags().length;
		assertEquals(meta1.getTags().length, meta2.getTags().length);
		for (int i=0; i<numTags; i++){
			assertEquals(meta1.getTags()[i], meta2.getTags()[i]);
		}
	}

	@Test
	public void testTestEntryHashSignature() {
		Entry entry1, entry2;
		try {
			entry1 = generateEntry1();
			entry2 = generateEntry2();
		} catch (KeyDecodingException | IOException e) {
			fail();
			return;
		}
		try {
			assertTrue( entry1.testEntryHashSignature() );
			assertTrue( entry2.testEntryHashSignature() );
		} catch (IOException e) {
			fail();
			return;
		}
	}

}
