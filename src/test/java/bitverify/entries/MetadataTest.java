package bitverify.entries;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import bitverify.crypto.Hash;

public class MetadataTest {
	
	public static Metadata generateMetadata1(){
		String docHash = Hash.hashStringToString("imitation of some random file");
		String linkToDownloadFile = "http://mywebsite.com/file01.txt";
		String docName = "The Fall of Humanity";
		String docDescription = "2+2 is sometimes 4";
		String docGeoLocation = "some random coords near Cambridge";
		String docTimeStamp = "10000 BC";
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
		assertEquals(metadata.getDocHash(), metadata2.getDocHash());
		assertEquals(metadata.getLinkToDownloadFile(), metadata2.getLinkToDownloadFile());
		assertEquals(metadata.getDocName(), metadata2.getDocName());
		assertEquals(metadata.getDocDescription(), metadata2.getDocDescription());
		assertEquals(metadata.getDocGeoLocation(), metadata2.getDocGeoLocation());
		assertEquals(metadata.getDocTimeStamp(), metadata2.getDocTimeStamp());
		
		int numTags = metadata.getTags().length;
		assertEquals(metadata.getTags().length, metadata2.getTags().length);
		for (int i=0; i<numTags; i++){
			assertEquals(metadata.getTags()[i], metadata2.getTags()[i]);
		}
		
	}

}
