package bitverify.crypto;

import static org.junit.Assert.*;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class SymmetricTest {

	@Test
	public void testEncryptBytes() {
		try {
			String input[] = {
					"00",
					"48657861646563696d616c206e6f746174696f6e2069732075736564",
					"4a6176612c204153502e4e45542c20432b2b2c20466f727472616e206574632068617665206275696c742d696e2066756e6374696f6e73207468617420636f6e7665727420746f20616e642066726f6d2068657820666f726d61742e",
					"576174675b3965776d6a673920505748672850455748474e7738476520667770696e68676665504f57514846456f7769205b4567455257472b39457277673234206577616720776572676f77696647203745515739452071206c6b6577754647206c6967667742",
					"537472696e67204d616e6970756c6174696f6e20466f722050726f6772616d6d6572730d0a466f72206120636f6d70617269736f6e",
			};
			String output[] = {
					"7fb62af3c06daba5cc562a03e8923d4f",
					"7bcfaa53bc26442c325f78e316b4ab2fed6cb07819fc73973c3ac32cdf30ccf6",
					"cd20004c345feb5c15e82abe757586a076f7923ee9bfa1c7dfaee85f84943c754447e06257db7b9d39723de07529538ea71215e41efd5fb38f49ed3f94db834da3d34fb44e432273754eb6336f56bde04c5f42dc0749cbd484f663eec0d33b2a",
					"acd2d84683a5f2b8cb23d04da164fe7fa8dc4ece2889ba2cf202d5db7b287a093a825524d9b0aef63e0f476b6af920dbb0b4c1b3c4b9a3f96b0e24d058eb4a7d6ecb4fb6ee3a41049e23344e543bfe9ef64e4d17ef83a05b5c0178d1eb9f1c917639f8a15a8d990fe213d0539757dc3f",
					"6fd9b2f446c64a56ef264c426da9c86cb8a4de96b4dc5aeb8b099385776dc6ada780aab3133781e55bbb371a4d3afd184b60a441384466d3869d69f484bebdc4",
			};
			String hexKey[] = {
					"80000000000000000000000000000000",
					"5057464a49333257474e505756414453",
					"76657279207365637265742070617373",
					"395b4933484749287b6866652877617b4746656e706f6946",
					"41574641534546455750535548476577386874676f7169616867347569626768",
			};
			byte[] dataIn, dataOut;
			for (int i=0; i<input.length; i++){
				byte[] key = Hex.decode(hexKey[i]);
				//encrypt in --> out
				dataIn = Hex.decode(input[i]);
				dataOut = Symmetric.encryptBytes(dataIn, key);
				assertEquals( output[i], Hex.toHexString(dataOut) );
				//decrypt out --> in
				dataIn = Hex.decode(output[i]);
				dataOut = Symmetric.decryptBytes(dataIn, key);
				assertEquals( input[i], Hex.toHexString(dataOut) );
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testGenerateHexKey() {
		byte[] key1 = Symmetric.generateKey();
		byte[] key2 = Symmetric.generateKey();
		//test lengths
		assertEquals( Symmetric.KEY_LENGTH_IN_BYTES, key1.length );
		assertEquals( Symmetric.KEY_LENGTH_IN_BYTES, key2.length );
		//test randomness (well ok, they might end up being the same)
		String hexKey1 = Hex.toHexString(key1);
		String hexKey2 = Hex.toHexString(key2);
		assertFalse( hexKey1.equals(hexKey2) );
		//test: encrypt then decrypt and get back original
		String input[] = {
				"00",
				"48657861646563696d616c206e6f746174696f6e2069732075736564",
				"4a6176612c204153502e4e45542c20432b2b2c20466f727472616e206574632068617665206275696c742d696e2066756e6374696f6e73207468617420636f6e7665727420746f20616e642066726f6d2068657820666f726d61742e",
				"576174675b3965776d6a673920505748672850455748474e7738476520667770696e68676665504f57514846456f7769205b4567455257472b39457277673234206577616720776572676f77696647203745515739452071206c6b6577754647206c6967667742",
				"537472696e67204d616e6970756c6174696f6e20466f722050726f6772616d6d6572730d0a466f72206120636f6d70617269736f6e",
		};
		try {
			byte[] dataIn, dataOut;
			for (int i=0; i<input.length; i++){
				dataIn = Hex.decode(input[i]);
				dataOut = Symmetric.encryptBytes(dataIn, key1);
				dataOut = Symmetric.decryptBytes(dataOut, key1);
				assertEquals( input[i], Hex.toHexString(dataOut) );
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}
}
