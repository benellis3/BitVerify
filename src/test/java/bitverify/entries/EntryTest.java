package bitverify.entries;

import static org.junit.Assert.*;

import java.io.IOException;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import bitverify.crypto.Asymmetric;
import bitverify.crypto.AsymmetricTest;
import bitverify.crypto.Hash;

public class EntryTest {
		
	public static Entry generateEntry1() {
		try {
			AsymmetricCipherKeyPair uploaderKeyPair = 
					Asymmetric.getKeyPairFromStringKeys(AsymmetricTest.myPubKey2, AsymmetricTest.myPrivKey2);
			// --> metadata
			byte[] docHash = Hash.hashString("imitation of some random file");
			String docLink = "http://mywebsite.com/file01.txt";
			String docName = "The Fall of Humanity";
			String docDescription = "2+2 is sometimes 4";
			String docGeoLocation = "some random coords near Cambridge";
			long docTimeStamp = 1455524447;
			String[] docTags = {"cool", "terminator", "random"};
			// <-- metadata
			Entry entry = new Entry(uploaderKeyPair, docHash, docLink, docName, docDescription,
					docGeoLocation, docTimeStamp, docTags);
			return entry;
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}
	
	public static Entry generateEntry2() {
		try {
			AsymmetricCipherKeyPair uploaderKeyPair = 
					Asymmetric.getKeyPairFromStringKeys(AsymmetricTest.myPubKey2, AsymmetricTest.myPrivKey2);
			// --> metadata
			byte[] docHash = Hash.hashString("imitation of some random file");
			String docLink = "http://mywebsite.com/file01.txt";
			String docName = "The Fall of Humanity";
			String docDescription = "2+2 is sometimes 4";
			String docGeoLocation = "some random coords near Cambridge";
			long docTimeStamp = 1455524447;
			String[] docTags = {"cool", "terminator", "random"};
			// <-- metadata
			byte[] receiverID = Asymmetric.stringKeyToByteKey(AsymmetricTest.myPubKey);
			Entry entry = new Entry(uploaderKeyPair, receiverID, docHash, docLink, docName, docDescription,
					docGeoLocation, docTimeStamp, docTags);
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
		entry1Bytes = entry1.serialize();
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
		assertTrue( compareMetadata(entry1, entry1B) );
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
		entry2Bytes = entry2.serialize();
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
		assertTrue( compareMetadata(entry2, entry2B) );
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
		assertTrue( entry1.testEntryHashSignature() );
		assertTrue( entry2.testEntryHashSignature() );
	}
	
	/**
	 * note: do NOT use this in production code; testing only
	 * 
	 * @return true iff metadata fields in e1 and e2 are equal
	 */
	public static boolean compareMetadata(Entry e1, Entry e2){
		try {
			assertEquals(Hex.toHexString(e1.getDocHash()), Hex.toHexString(e2.getDocHash()));
			assertEquals(e1.getDocLink(), e2.getDocLink());
			assertEquals(e1.getDocName(), e2.getDocName());
			assertEquals(e1.getDocDescription(), e2.getDocDescription());
			assertEquals(e1.getDocGeoLocation(), e2.getDocGeoLocation());
			assertEquals(e1.getDocTimeStamp(), e2.getDocTimeStamp());

			if (e1.getDocTags() != null) {
				int numTags = e1.getDocTags().length;
				assertEquals(e1.getDocTags().length, e2.getDocTags().length);
				for (int i = 0; i < numTags; i++) {
					assertEquals(e1.getDocTags()[i], e2.getDocTags()[i]);
				}
			} else {
				//e1.getDocTags() == null
				assertTrue(null == e2.getDocTags());
			}
		} catch (Exception e){
			return false;
		}
		return true;
	}

}
