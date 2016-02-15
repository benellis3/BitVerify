package bitverify.crypto;

import static org.junit.Assert.*;

import java.io.IOException;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class IdentityTest {

	public static final String passwordExample = "$3cr3t,n0b0dy!will EveR cr4ckkk th1s.__asdasd";
	
	public static Identity generateIdentity_unsecure(){
		try {
			String name = "Arnold";
			byte[] publicKey = Asymmetric.stringKeyToByteKey(AsymmetricTest.myPubKey);
			byte[] privateKey = Asymmetric.stringKeyToByteKey(AsymmetricTest.myPubKey);
			return new Identity(name, publicKey, privateKey);
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}
	
	public static Identity generateIdentity_secure(){
		try {
			String name = "Name is Bond, Arnold Bond";
			byte[] publicKey = Asymmetric.stringKeyToByteKey(AsymmetricTest.myPubKey);
			byte[] privateKey = Asymmetric.stringKeyToByteKey(AsymmetricTest.myPubKey);
			String masterPw = passwordExample;
			return new Identity(name, publicKey, privateKey, masterPw);
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}

	@Test
	public void testSerialize_unsecure() {
		Identity myID1;
		try {
			myID1 = generateIdentity_unsecure();
		} catch (Exception e) {
			fail();
			return;
		}
		//serialize
		byte[] myID1Bytes;
		try {
			myID1Bytes = myID1.serialize();
		} catch (IOException e) {
			fail();
			return;
		}
		//deserialize
		Identity myID2;
		try {
			myID2 = Identity.deserialize(myID1Bytes);
		} catch (IOException e) {
			fail();
			return;
		}
		//check fields
		assertEquals(myID1.getDescription(), myID2.getDescription());
		assertEquals(Hex.toHexString(myID1.getPublicKey()), Hex.toHexString(myID2.getPublicKey()));
		assertEquals(Hex.toHexString(myID1.getPrivateKey()), Hex.toHexString(myID2.getPrivateKey()));
		assertFalse(myID1.getNeedsEncryption());
		assertFalse(myID2.getNeedsEncryption());
	}
	
	@Test
	public void testSerialize_secure() {
		Identity myID1;
		try {
			myID1 = generateIdentity_secure();
		} catch (Exception e) {
			fail();
			return;
		}
		//serialize
		byte[] myID1Bytes;
		try {
			myID1Bytes = myID1.serialize();
		} catch (IOException e) {
			fail();
			return;
		}
		//deserialize
		Identity myID2;
		try {
			myID2 = Identity.deserialize(myID1Bytes);
		} catch (IOException e) {
			fail();
			return;
		}
		//check fields BEFORE decryption
		assertEquals(myID1.getDescription(), myID2.getDescription());
		assertEquals(Hex.toHexString(myID1.getPublicKey()), Hex.toHexString(myID2.getPublicKey()));
		assertEquals(null, myID2.getPrivateKey());
		assertTrue(myID1.getNeedsEncryption());
		assertTrue(myID2.getNeedsEncryption());
		//decrypt
		try {
			myID2.decrypt(passwordExample);
		} catch (NotMatchingKeyException e) {
			fail();
			return;
		}
		//check fields AFTER decryption
		assertEquals(myID1.getDescription(), myID2.getDescription());
		assertEquals(Hex.toHexString(myID1.getPublicKey()), Hex.toHexString(myID2.getPublicKey()));
		assertEquals(Hex.toHexString(myID1.getPrivateKey()), Hex.toHexString(myID2.getPrivateKey()));
		assertTrue(myID1.getNeedsEncryption());
		assertTrue(myID2.getNeedsEncryption());
	}
	
	@Test
	public void testSerialize_secure_wrongpassword() {
		Identity myID1;
		try {
			myID1 = generateIdentity_secure();
		} catch (Exception e) {
			fail();
			return;
		}
		//serialize
		byte[] myID1Bytes;
		try {
			myID1Bytes = myID1.serialize();
		} catch (IOException e) {
			fail();
			return;
		}
		//deserialize
		Identity myID2;
		try {
			myID2 = Identity.deserialize(myID1Bytes);
		} catch (IOException e) {
			fail();
			return;
		}
		//check fields BEFORE decryption
		assertEquals(myID1.getDescription(), myID2.getDescription());
		assertEquals(Hex.toHexString(myID1.getPublicKey()), Hex.toHexString(myID2.getPublicKey()));
		assertEquals(null, myID2.getPrivateKey());
		assertTrue(myID1.getNeedsEncryption());
		assertTrue(myID2.getNeedsEncryption());
		//decrypt
		boolean exceptionThrown = false;
		try {
			myID2.decrypt("wrong password here");
		} catch (NotMatchingKeyException e) {
			//note: the underlying library prints a stack trace here unfortunately
			//so currently seeing a stack trace but having no errors in the tests is
			//the expected behaviour
			exceptionThrown = true;
		}
		assertTrue(exceptionThrown);
		//check fields again
		assertEquals(myID1.getDescription(), myID2.getDescription());
		assertEquals(Hex.toHexString(myID1.getPublicKey()), Hex.toHexString(myID2.getPublicKey()));
		assertEquals(null, myID2.getPrivateKey());
		assertTrue(myID1.getNeedsEncryption());
		assertTrue(myID2.getNeedsEncryption());
	}

}
