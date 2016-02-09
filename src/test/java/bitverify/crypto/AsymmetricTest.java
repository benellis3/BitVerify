package bitverify.crypto;

import static org.junit.Assert.*;

import java.io.IOException;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.junit.Test;

public class AsymmetricTest {
	
	public static String myPrivKey =
			"-----BEGIN RSA PRIVATE KEY-----\n"+
			"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\n"+
			"EVTd9Zoj+jVVaYE5ENgugGMn+RM=\n"+
			"-----END RSA PRIVATE KEY-----";
	
	public static String myPubKey =
			"-----BEGIN RSA PUBLIC KEY-----\n"+
			"MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwuhVhtwFTe2KoC/Xdor1\n"+
			"Iy9SDjWY2KGKUrSAv8iPWUzzXB4qgUorpmX/6efWaA9igzjxzUg/9w97d1HxLr7+\n"+
			"Wr154I99w/nhfy+fRe6DIyocF0g5dyr0YJiLmFt75sP+EVKcwEFwLlgII7EH0ym+\n"+
			"JvBE6CrjLj+X3AHZ2MTm+WIIVqOLAxYA11DxphdYqb/shTLSr2oeh1t1S/6tOBOb\n"+
			"DeGI+0pYbTG9z2y3ZPtlDIPOA//UJ2srlRuHx74YUvSZQ3KpMRoiRqySRqai9P8n\n"+
			"d9EFC0hagRV2oyQMthuvoE8R/MSRyHz3Ogg7rhMWS8FSawrLolVD21YY/tSIei6b\n"+
			"5mhCBX3bzG0oerUdnXY7ZfoPwnuPZrtkVSW0Jwt0u5xaJcNYK3/SBrRXhSFznIzk\n"+
			"eLhY2lY4/14lKKEDyVLLxPnkiviF24iJT0rhTdgOWxjTWDo8qjuOBNA+3NRJw5Pl\n"+
			"yC+3WOeSM/LE2VgaeWEY/QCqhtd+7Ei1LGSf55sKhp8d7keBgPn360QNA+nmswWF\n"+
			"Bg9Z2YvGF3h1eCv1songXiFH+ge8YtU/KKlAH5j6Ptk8BNdsSHMTysegcN7PADT6\n"+
			"YKsc8x89IvT0/hxU71q/UtZThek87E9rHMo+NrKBZntff4QPnZlZUAC9MGngFywf\n"+
			"R7sAUuoLW2dVPBwKATW3agECAwEAAQ==\n"+
			"-----END RSA PUBLIC KEY-----";

	public static String myPrivKey2 =
			"-----BEGIN RSA PRIVATE KEY-----\n"+
			"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\n"+
			"jTNP2l23FSIdv34FDpNABoocRzz3rg==\n"+
			"-----END RSA PRIVATE KEY-----";
	
	public static String myPubKey2 =
			"-----BEGIN RSA PUBLIC KEY-----\n"+
			"MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAvaZyL9xm12L+a5MdX5U5\n"+
			"dfb1bVgpRpTAuiqFM01zjPRxnZPfBhLnKpxC89YB0tjqMyGVmaTCJODsvukbvcS6\n"+
			"t0vrpMhj5nMdGel+PIueE+S3VlyUFrBbV+ElVN5dqvZfHphmubNWhWCoVF0iRhCx\n"+
			"NP/bJ1eG1mtcIowV0YV4VqvRvYU+eA/Kz/AzxaI0hpJFLHQlJV6v1FR4PcUTx/oB\n"+
			"cFkNgaws7ObM0V6dZuCCiJHZewMacvgJO9PFSDVDJPx5zl4rUKQTYvlcSvnfaLan\n"+
			"kFMLtF84LsPoHWld22YMBHebUfZOD3mhp32d5uv/0DkPZPr6itR5/DBbtRVxk/rr\n"+
			"B+Y3Z1jllBSXhVN/xnG0H4GIWhWgxJm1YFlHBgLXC+X66rxYTzy9axbYoqd5YXsJ\n"+
			"PZxRao5Rjw7vB0cltPcNeAxzh/eU5TBVeTZ1aYJEw5GMjuQdVQWj0vEfDOUedv6O\n"+
			"5aQc4Sd+76i1xi1XIwe4az/4UfFQMgh6dT3GuRKoE/5T+ePdwXAzWzXTNKLKoeaP\n"+
			"Xh4e0N7FMxwXjUNAxrdJGhIhxLwnGH23X8Uw8ENHq5qIJqvAQWuvCPwzDZo3zpBu\n"+
			"h0grOGeH1MGSCRGXqlMpnGXVF1HVt1y8ckyXA/iuHuRkwN8Z5U8xi68uzyZKOU4o\n"+
			"6O4MlC/+iRrxspYuicE0n6ECAwEAAQ==\n"+
			"-----END RSA PUBLIC KEY-----";
	
	@Test
	public void testEncryptHexString() {
		try {
			String input[] = {
					"0000",
					"abcdef",
					"ff6f77206973207468652074696d6520666f7220616c6c20676f6f64206d656e",
					"bebc54453327ba42847dbd448e13d906b8b4ce3dca2b615388c3fb3e379820cbded7a3",
					"f2fabebc54453327ba42847dbd448e13d906b8b4ce3dca2b615388c3fb3e379820cbded7a3",
					"af261dd208c1b6c61cc38b3dd201487309a76dafee0f9f131de357e8041dc932efe0b63e9626257e4faaf40093ecb699db894c540b540a34c3c666325fc927982fbf7fb6d09e241952528841d5c24369c35ca60107a6a07b206f71d92ecb11addc777302d47e8ddccd2f463e0fc854b8775e2723a43133b9ac46bde49714be01b45e578d17bbc17ed403d6c3f71235974132ebacd974c1f53f442ea150b8abf163d233500f8194ce1a9ae07c08fee5dbe9663f0b6d12bae58d714f3cffcdd8bb086ff9c8d0d10c58f2522125be7f3723924430599ea15cd2a4bf1d77568f5dca0dbb162994ca54a04f4bf1c59bc9933f7f245dd510d21a34323c139f7bf7d60e",
					"1dd208c1b6c61cc38b3dd201487309a76dafee0f9f131de357e8041dc932efe0b63e9626257e4faaf40093ecb699db894c540b540a34c3c666325fc927982fbf7fb6d09e241952528841d5c24369c35ca60107a6a07b206f71d92ecb11addc777302d47e8ddccd2f463e0fc854b8775e2723a43133b9ac46bde49714be01b45e578d17bbc17ed403d6c3f71235974132ebacd974c1f53f442ea150b8abf163d233500f8194ce1a9ae07c08fee5dbe9663f0b6d12bae58d714f3cffcdd8bb086ff9c8d0d10c58f2522125be7f3723924430599ea15cd2a4bf1d77568f5dca0dbb162994ca54a04f4bf1c59bc9933f7f245dd510d21a34323c139f7bf7d60eaf261dd208c1b6c61cc38b3dd201487309a76dafee0f9f131de357e8041dc932efe0b63e9626257e4faaf40093ecb699db894c540b540a34c3c666325fc927982fbf7fb6d09e241952528841d5c24369c35ca60107a6a07b206f71d92ecb11addc777302d47e8ddccd2f463e0fc854b8775e2723a43133b9ac46bde49714be01b45e578d17bbc17ed403d6c3f71235974132ebacd974c1f53f442ea150b8abf163d233500f8194ce1a9ae07c08fee5dbe9663f0b6d12bae58d714f3cffcdd8bb086ff9c8d0d10c58f2522125be7f3723924430599ea15cd2",
			};
			AsymmetricKeyParameter privateKey = Asymmetric.stringKeyToKey(myPrivKey);
			AsymmetricKeyParameter publicKey = Asymmetric.stringKeyToKey(myPubKey);
			String encrypted, decrypted;
			for (int i=0; i<input.length; i++){
				encrypted = Asymmetric.encryptHexString(input[i], privateKey);
				decrypted = Asymmetric.decryptHexString(encrypted, publicKey);
				assertEquals(input[i], decrypted);
				
				encrypted = Asymmetric.encryptHexString(input[i], publicKey);
				decrypted = Asymmetric.decryptHexString(encrypted, privateKey);
				assertEquals(input[i], decrypted);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testEncryptHexString2() {
		//try encrypting too long data
		//expecting DataSizeException
		String input[] = {
				"000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
				"77686174206120776f6e64657266756c206c6966652057652075736520636f6f6b69657320746f20706572736f6e616c69736520636f6e74656e7420616e64206164732c20746f2070726f7669646520736f6369616c206d6564696120666561747572657320616e6420746f20616e616c797365206f757220747261666669632e20576520616c736f20736861726520696e666f726d6174696f6e2061626f757420796f757220757365206f66206f757220736974652077697468206f757220736f6369616c206d656469612c206164766572746973696e6720616e6420616e616c797469637320706172746e6572732e20427920636f6e74696e75696e6720746f20757365207468697320776562736974652c20796f7520636f6e73656e7420746f206f757220757365206f6620746865736520636f6f6b6965732e2041626f7574206f757220757365206f6620636f6f6b69657377686174206120776f6e64657266756c206c6966652057652075736520636f6f6b69657320746f20706572736f6e616c69736520636f6e74656e7420616e64206164732c20746f2070726f7669646520736f6369616c206d6564696120666561747572657320616e6420746f20616e616c797365206f757220747261666669632e20576520616c736f20736861726520696e666f726d6174696f6e2061626f757420796f757220757365",
		};
		AsymmetricKeyParameter publicKey;
		try {
			publicKey = Asymmetric.stringKeyToKey(myPubKey);
		} catch (KeyDecodingException e1) {
			e1.printStackTrace();
			fail();
			return;
		}
		int exceptionsCounted = 0;
		for (int i=0; i<input.length; i++){
			try {
				Asymmetric.encryptHexString(input[i], publicKey);
			} catch (InvalidCipherTextException e) {
				e.printStackTrace();
				fail();
			} catch (DataSizeException e) {
				exceptionsCounted++;
			}
		}
		assertEquals(input.length, exceptionsCounted);
	}
	
	@Test
	public void testDecryptHexString() {
		try {
			String input[] = {
					"5b2996acec4ad72cbdff87e92bdb7c3aa5069279ecc55df953eb94732ec343d82d363f3d667f33da3e8ca771897466607535115687dd12ff72768dc00a3bacfc6eb90c143bab92d35d1d01af4ff32e87d56f2baceeb9e5954c8603797dc145ce2df95df50296440154ab15f90ed91600c872d4cdf86cdc0fcbe27efb0ae110797f759c736c9f19457063cb06a8b0dffd3f2cb71349adc2df855825bdd974d6788ac7c8f09d08696455f748960dd79386ca4ebfdf90ee4629d6423f24afc1c1382d506922bb38a5304d65db4070cbd2ae054a4b1f00e817c7e02b511bad5cea7f4de768dcebf6a4df02eed68fd696d0ec90ff3da87029568df55de6db041d276c45944f7d5efc50f9277fcff533db5235ebcb544968e2e1455bdeb5a7c822ab17fcce4faa18c6a7d0548e2403e11da9b40f4e0759e39080aa7f9dd3e2be217a4e49a0e143ff2669d27b362af51307fc067aeca363149242c276975cfee2f4e00282be1aad619f947e715234df70ea4748a223e4b28d99f49186b1f066bbde16817ecf9a90be1ab72be0dc24cef96c114b77264a8166d76d191c62ac8890e062d083362b2be12bc101b7b113a428b37158aa38675e1333473376bdf030c86d8b85fc5e0ffc029f60db5a0c81165f607f0887f7930bc8c62132cf3d71306f6d1934df8c3ea8af41fe0a5f0d3c92f513b4b8e7181795923210889371b807787f40c5",
					"118254db38d7a2b06a06b2d02a727c0619c6657c91443436235ceae7f6db3b125109746c671aedb7fdba37240cc5b288ad0e2ad3dc0dda15e82271a1f1e27c2b52327a661661068d97ddcc192725eb5cc6eb193306df38ac9466c871a20e2aa8c241d1ac0c0682254052bb3353bfbc252f3e2b568a45fb350eab2feec3e3390f553f1bba08450fd730fd67691753303ac82f212ea4cfe7dc774caeece2c8297a0cd24758cdbc98eaa6e3907f6e600d5ec6fad9bd6c7009c0a7f05674d4d48dada32e10b896342e17a1ce1bd2ae39af130eecb3b70f5f13418c56cb46b29e62d5fb0a779cd54a3d45d1423ff18d5832027745a809ec8c5933297d10df16bc7c21fc57cf03d40c2e9e6c9631039851198990f82b8dd13e8048df91929e05d3d1306971c253d0bfe070554510cbcd050d9dadfb874364c2ca425c94a16262a481615ae2330667fb67b49228e85e201db532086b3a57e95fbfcad898f207b71cba5b314429fb0ae0ec5885b69a94c63835fb8e771b91b1e10c65827e16b2d38d5bb37c38dba2c22bccdf2584f257c333d5c107d646574a1bfb872782503b38f81b2c94dd4afa1cad46a6d1772f5f956f0c11c41a5c13dd91980eb9c9d3a5f23aa74c8ff5898b8c96e08d4d783b52996ffc60d334f0e0ac020ff3c75fd5d877a4e5cd8308923650c4c8bdff4dc890ce284963333a5ff558c34a849e2d725f2e040b6c",
					"5434d9c96f4a3376052a58afeb852f033eb419b5244cc1056e2fbc174a328cf64872dd723958b3d9934bd6a5e4b3f874d14d7ea71ffc500907976f9006e806491ebcf82bf0ce4c0d7c371fc5fc8d3b3d007f5645ed97565cf78729987cc2b5b7df3e199483d9a903cdfc360aea4e4feecf8a0217f51004f5921582a3ba1a4523f12ae6506d07b45fa21c33a715d768bed5daa71d135f270d67bc3ad68cd3fc7144e0cf8c46fdcce0c1bdbb4349010ad15ad2b52e5e3ea26e45fe87dbe6f8e2f3fac760cf9141d7594330977e56d2cf7526b5fa81cc63602cb5925b38a74484cd6810480c870385bbfc6e99340c3b0599e61387fcbcb47f57456fd45ebd9895afd355ad5b3f8fa5cd044e8535ad899691262a7a41f6e8dbd6a902d7a35e46cd54764f3e97cf637a1559eabee434fe1c7004b9bf1f4bdeba03ba2f1e7efb0cbbefc9cd3e608afc42aba34842b148deca22be5b39f0e80acaca2fb7b29e37c29b57f9316cc4751ecc4fcc3f3fb892d926678afcd6e12438c3893f1d11e6e81021930270b4f4ebff3fde67aa60e0295b401434a6bd3c77e43ac74d6ea727154d01777fe06c5dc434e8fae2be4c0cf37ac2ad1d2610057aaccdd5e843a12fd7c32f53ed745bcfdb36ba1c159ea9dc7e68623355b2a9f10eedb3dbafe1da67b2278bde70fa8b16d8e1cfec36e70f6a0d99cd7d81ac5abf15c42faaa70e6d5e16207a2a",
					"a06dc7636e6e8ab0d96cd8a173c414bf9471bbcf16ba28bc75f0afbe2e85dcd0da03e98775b5012c8b513e720a09ee3b1fc6245218fdc3a49c0b14277e894c0a6b7ccaf2dacb27f66c6c599b1ea9d4673b9a1b99b421a99e17f60b1668b90bd74f05357b2f6e1d524253b439e05abf344695df879d3252daf94121de95044c85277d633a8e19973141a73e83149c8ed70b2c4b52604d52d11396fef59338e5c14d2faf293a63e4a1eb27c46b6785ce559f50e7ffb944610cd0477e7b4ec4f85b2f7ffad673410056e62f1c6fa0658091e8529d10a9db01f506870780c5e16e2b2ac7a34daca5079c1f73b4ea9560e880415de3b232c2bbc7575527da9019012d5f77464ca5cfcac6285ab31a5af0f8449085129cbbd22322cbefffa575899a568acf0f1bea01e2005c58e9b72bd4d5f0f4988e166322f46ecd6357f5c37e4d1237cae1b568409ecdd5ce6ce5fba53ba896b5b0ec35581d74dd93200cb01600a4fb31e5dd9f7f43a202c27038a5c03fe66801678117c222baed8fe31445351776476cba416a8f97a3eaf11b353724c768eee73aa0827f381164d8d13811f04c7afacfb1ec919bf7310b3d8eedbcaeb6fbe182dce5c2e9521eaaa3ed318f18bc8ae57062e8daa05b4ee18be698134ec4af37e69adcc4f7d2958dc90cfe9261b12736829c325f3300ad7357d09e3ecb7eb396b3ed3cfbd42d27d4e1962dd8338e2f",
					"09e3dedea06e4a2bc386140c1756ccf4bf71d36810c84d52e5f59bfeb9e3616b213d54149ed0fc5ca6e16edc2729f922bdf29d9f81fcfb975335adb19293920fa229e1e657c8d79aac882f8de86eb2b0bf3762dc54db1f3200d0f89b4ab0671aabed8a7674a42e6a417adf3f8a4a9e06b402880a5041f24600e2c1e1fff9bfeaaff503b539f32c48dec6a73d7228156b8251e86321bb19f2483d71be88d03dd30d1c1816a6eea02b7c43cb248ff4a6b1b38bba91bfbd82c2213d945f4719093f832801e906a3db17605a83bd8b08c32d5658635271a8e2b69fb37f54ed3eafa6de76d0eabead588baac220e1ffe621b3e0097d2eef9bb586afe06cfd488d505f54512593c3afcfd12101a4002802f87f4e36b198b26eff9ba1a135b4d22f2c15c55091e4fd511cb193420ff0fc259f937321f7b1ab16917f23097e10a07fb050803253cee20b89d77068a8aec93d252b05efe9e6d82b9fe033b56aa54e4356a40dfc28cc505b85e16d1655233428845cf4c7d71a84872a8a253332e7c88952a6a90097648f6d34f93c5f69c607913da79fbacd6aeb1d55904408d75408b8f340e9ef80c5159806cc724c29bc39c2df16bb1a75b1f0a68fedae05bdd63abab6088f29dfc1578807a14198f9c012eb06668be5e933f8dd4e8469200423a8ac858fb28c5317a9a0006fb6f0d1e59c21f4aacd8741e1190882594565dc3e9b2642e5",
			};
			String output[] = {
					"6a75737420736f6d652072616e646f6d20776f7264732068657265",
					"4920646f6e2774206576656e206b6e6f77207768617420746f207361792e204c6f6c2e",
					"4e6f7420676f696e672077656c6c2c2069732069743f",
					"2d2053485554205550210a2d2057687920776f756c6420492c206e6f773f",
					"4e6f2c204920616d20796f75722066617468657221",
			};
			AsymmetricKeyParameter publicKey = Asymmetric.stringKeyToKey(myPubKey);
			String decrypted;
			for (int i=0; i<input.length; i++){
				decrypted = Asymmetric.decryptHexString(input[i], publicKey);
				assertEquals(output[i], decrypted);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testEncryptBase64String() {
		try {
			String input[] = {
					"V2h5IHdvdWxkIHlvdSBkbyB0aGF0IHRvIG1lPw==",
					"Tm9vb28sIHlvdSBtb3JvbiEhISBZb3UgcnVpbmVkIGl0Lg==",
					"SGF2ZSB0byBkZWFsIHdpdGggQmFzZTY0IGZvcm1hdD8gVGhlbiB0aGlzIHNpdGUgaXMgbWFkZSBmb3IgWW91ISBVc2UgdGhlIHN1cGVyIHNpbXBsZSBvbmxpbmUgZm9ybSBiZWxvdyB0byBkZWNvZGUgb3IgZW5jb2RlIFlvdXIgZGF0YS4=",
			};
			AsymmetricKeyParameter privateKey = Asymmetric.stringKeyToKey(myPrivKey);
			AsymmetricKeyParameter publicKey = Asymmetric.stringKeyToKey(myPubKey);
			String encrypted, decrypted;
			for (int i=0; i<input.length; i++){
				encrypted = Asymmetric.encryptBase64String(input[i], privateKey);
				decrypted = Asymmetric.decryptBase64String(encrypted, publicKey);
				assertEquals(input[i], decrypted);
				
				encrypted = Asymmetric.encryptBase64String(input[i], publicKey);
				decrypted = Asymmetric.decryptBase64String(encrypted, privateKey);
				assertEquals(input[i], decrypted);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testGenerateNewKeyPair() {
		try {
			AsymmetricCipherKeyPair keyPair = Asymmetric.generateNewKeyPair();
			String input = "dGVzdGluZyBteSBnbG9yaW91cyBtZXRob2Rz";
			String encrypted = Asymmetric.encryptBase64String(input, keyPair.getPrivate());
			String decrypted = Asymmetric.decryptBase64String(encrypted, keyPair.getPublic());
			assertEquals( input, decrypted );
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testGetKeyPairFromKeys() {
		try {
			AsymmetricCipherKeyPair keyPair =
					Asymmetric.getKeyPairFromKeys(Asymmetric.stringKeyToKey(myPubKey), Asymmetric.stringKeyToKey(myPrivKey));
			//keyPair constructed, now try encrypting something
			String input = "dGVzdGluZyBteSBnbG9yaW91cyBtZXRob2Rz";
			String encrypted = Asymmetric.encryptBase64String(input, keyPair.getPrivate());
			String decrypted = Asymmetric.decryptBase64String(encrypted, keyPair.getPublic());
			assertEquals( input, decrypted );
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testGetKeyPairFromStringKeys() {
		try {
			AsymmetricCipherKeyPair keyPair = Asymmetric.getKeyPairFromStringKeys(myPubKey, myPrivKey);
			//keyPair constructed, now try encrypting something
			String input = "dGVzdGluZyBteSBnbG9yaW91cyBtZXRob2Rz";
			String encrypted = Asymmetric.encryptBase64String(input, keyPair.getPrivate());
			String decrypted = Asymmetric.decryptBase64String(encrypted, keyPair.getPublic());
			assertEquals( input, decrypted );
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testStringKeyToKey() {
		try {
			Asymmetric.stringKeyToKey(myPrivKey);
			String myPrivKey2 = Asymmetric.keyToStringKey(Asymmetric.stringKeyToKey(myPrivKey));
			String myPubKey2 = Asymmetric.keyToStringKey(Asymmetric.stringKeyToKey(myPubKey));
			assertEquals( myPrivKey, myPrivKey2 );
			assertEquals( myPubKey, myPubKey2 );
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testIsValidKey() {
		//test hardcoded keys
		assertTrue( Asymmetric.isValidKey(myPrivKey) );
		assertTrue( Asymmetric.isValidKey(myPubKey) );
		assertTrue( Asymmetric.isValidKey(myPrivKey2) );
		assertTrue( Asymmetric.isValidKey(myPubKey2) );
		
		//test newly generated keys
		AsymmetricCipherKeyPair keyPair = Asymmetric.generateNewKeyPair();
		String newPrivKey, newPubKey;
		try {
			newPrivKey = Asymmetric.keyToStringKey(keyPair.getPrivate());
			newPubKey = Asymmetric.keyToStringKey(keyPair.getPublic());
		} catch (KeyDecodingException e) {
			e.printStackTrace();
			fail();
			return;
		}
		assertTrue( Asymmetric.isValidKey(newPrivKey) );
		assertTrue( Asymmetric.isValidKey(newPubKey) );
		
		//test random gibberish
		String input[] = {
				"V2h5IHdvdWxkIHlvdSBkbyB0aGF0IHRvIG1lPw==",
				"-----lollolasdasd",
				"-----BEGIN RSA PRIVATE KEY-----\nasdasdjustkidding\n-----END RSA PRIVATE KEY-----",
				"-----BEGIN RSA PUBLIC KEY-----\n nahforgetit then",
		};
		for (int i=0; i<input.length; i++){
			assertFalse( Asymmetric.isValidKey(input[i]) );
		}
		
	}
	
}
