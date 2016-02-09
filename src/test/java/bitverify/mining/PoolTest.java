package bitverify.mining;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import bitverify.crypto.KeyDecodingException;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.junit.Test;

import bitverify.crypto.KeyDecodingException;
import bitverify.entries.Entry;
import bitverify.entries.Metadata;

public class PoolTest {

	@Test
	public void testMineSuccess() {
		//Unable to verify this test case due to ormlite package in entries
		Pool p = new Pool();
		
		p.setMaxEntries(2);
		try {
			Entry e1 = new Entry(new AsymmetricCipherKeyPair(new AsymmetricKeyParameter(false),new AsymmetricKeyParameter(true)), new Metadata("","","","","","",null));
			Entry e2 = new Entry(new AsymmetricCipherKeyPair(new AsymmetricKeyParameter(false),new AsymmetricKeyParameter(true)), new Metadata("example","example_data","example","example","example_data","example",null)); 
			
			p.addToPool(e1);
			
			p.addToPool(e2);
			
			p.addToPool(e2);
			
			Entry output[] = {
					e1,
					e2,
					null
			};
			for (int i=0; i<output.length; i++){
				assertEquals( output[i], p.takeFromPool() );	
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} 
		catch (KeyDecodingException e) {
			e.printStackTrace();
		} 
	}
	
}
