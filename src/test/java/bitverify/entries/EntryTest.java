package bitverify.entries;

import static org.junit.Assert.*;

import java.io.IOException;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import bitverify.crypto.Asymmetric;
import bitverify.crypto.AsymmetricTest;

public class EntryTest {
		
	public static Entry generateEntry1() {
		try {
			AsymmetricCipherKeyPair uploaderKeyPair = 
					Asymmetric.getKeyPairFromStringKeys(AsymmetricTest.myPubKey2, AsymmetricTest.myPrivKey2);
			Metadata metadata = MetadataTest.generateMetadata1();
			Entry entry = new Entry(uploaderKeyPair, metadata);
			return entry;
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}
	
	public static Entry generateEntry2() {
		try {
			AsymmetricCipherKeyPair uploaderKeyPair = 
					Asymmetric.getKeyPairFromStringKeys(AsymmetricTest.myPubKey2, AsymmetricTest.myPrivKey2);
			Metadata metadata = MetadataTest.generateMetadata1();
			byte[] receiverID = Asymmetric.stringKeyToByteKey(AsymmetricTest.myPubKey);
			Entry entry = new Entry(uploaderKeyPair, metadata, receiverID);
			return entry;
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}

	@Test
	public void testSerialize_public() {
		Entry entry1;
		try {
			entry1 = generateEntry1();
		} catch (Exception e) {
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
		assertTrue( MetadataTest.compareMetadata(meta1, meta2) );
	}
	
	@Test
	public void testSerialize_private() {
		Entry entry2;
		try {
			entry2 = generateEntry2();
		} catch (Exception e) {
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
			entry2B.decrypt(receiverKeyPair);
		} catch (Exception e) {
			fail();
			return;
		}
		//compare metadata
		Metadata meta1 = entry2.getMetadata();
		Metadata meta2 = entry2B.getMetadata();
		assertTrue( MetadataTest.compareMetadata(meta1, meta2) );
	}

	@Test
	public void testTestEntryHashSignature() {
		Entry entry1, entry2;
		try {
			entry1 = generateEntry1();
			entry2 = generateEntry2();
		} catch (Exception e) {
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
