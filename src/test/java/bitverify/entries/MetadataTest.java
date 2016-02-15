package bitverify.entries;

import static org.junit.Assert.*;

import java.io.IOException;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import bitverify.crypto.Hash;

public class MetadataTest {
	
	public static Metadata generateMetadata1(){
		byte[] docHash = Hash.hashString("imitation of some random file");
		String linkToDownloadFile = "http://mywebsite.com/file01.txt";
		String docName = "The Fall of Humanity";
		String docDescription = "2+2 is sometimes 4";
		String docGeoLocation = "some random coords near Cambridge";
		long docTimeStamp = 1455524447;
		String[] tags = {"cool", "terminator", "random"};
		Metadata metadata = new Metadata(docHash, linkToDownloadFile, docName,
				docDescription, docGeoLocation, docTimeStamp, tags);
		return metadata;
	}
	
	@Test
	public void testSerialize() {
		Metadata metadata = generateMetadata1();
		//serialize
		byte[] metadataBytes;
		try {
			metadataBytes = metadata.serialize();
		} catch (IOException e) {
			fail();
			return;
		}
		//deserialize
		Metadata metadata2;
		try {
			metadata2 = Metadata.deserialize(metadataBytes);
		} catch (IOException e) {
			fail();
			return;
		}
		//compare
		assertTrue( compareMetadata(metadata, metadata2) );
	}
	
	/**
	 * @return true iff m1 logically equals m2
	 * note: do NOT use this in production code; testing only
	 */
	public static boolean compareMetadata(Metadata m1, Metadata m2){
		try {
			assertEquals(Hex.toHexString(m1.getDocHash()), Hex.toHexString(m2.getDocHash()));
			assertEquals(m1.getLinkToDownloadFile(), m2.getLinkToDownloadFile());
			assertEquals(m1.getDocName(), m2.getDocName());
			assertEquals(m1.getDocDescription(), m2.getDocDescription());
			assertEquals(m1.getDocGeoLocation(), m2.getDocGeoLocation());
			assertEquals(m1.getDocTimeStamp(), m2.getDocTimeStamp());
			
			int numTags = m1.getTags().length;
			assertEquals(m1.getTags().length, m2.getTags().length);
			for (int i=0; i<numTags; i++){
				assertEquals(m1.getTags()[i], m2.getTags()[i]);
			}
		} catch (Exception e){
			return false;
		}
		return true;
	}

}
