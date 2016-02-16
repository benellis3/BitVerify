package bitverify.mining;

import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Test;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

import bitverify.mining.Miner.BlockFoundEvent;
import bitverify.persistence.DataStore;
import bitverify.persistence.DatabaseStore;

public class MinerTest {

	//Test whether hashes are successful when they should be
	@Test
	public void testMineSuccess() throws SQLException, IOException{
		DataStore d = new DatabaseStore("jdbc:h2:mem:bitverify");
		Miner m = new Miner(new Bus(ThreadEnforcer.ANY),d);
		
		m.setPackedTarget(0x1f3b20fa);
		//We have an unpacked target of 003b20fa00000000000000000000000000000000000000000000000000000000
		
		String input[] = {
				"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
				"cf80cd8aed482d5d1527d7dc72fceff84e6326592848447d2dc0b0e87dfc9a90",
				"a5fdf69452bc32ff2ef109f4b501d84928ea04e0d6ebf2eac42cf35a9d926ba9",
				"00ffffffe6fcdc36f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
				"0000000000000000f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
				"0000001000000000f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
		};
		boolean output[] = {
				false,
				false,
				false,
				false,
				true,
				true,
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], m.mineSuccess(input[i]) );	
		}
	}
	
	//Test whether mining targets are successfully unpacked from the integer representation
	@Test
	public void testUnPack() throws SQLException, IOException{
		DataStore d = new DatabaseStore("jdbc:h2:mem:bitverify");
		Miner m = new Miner(new Bus(ThreadEnforcer.ANY),d);
		
		int input[] = {
				0x04111111,
				0x083B12AB,
				0x020000ff,
		};
		String output[] = {
				"11111100",
				"3b12ab0000000000",
				"0",	//Reject negative targets
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], m.unpackTarget(input[i]) );	
		}
	}
	
	//Test whether mining targets are successfully packed into the integer representation
	@Test
	public void testPack() throws SQLException, IOException{
		DataStore d = new DatabaseStore("jdbc:h2:mem:bitverify");
		Miner m = new Miner(new Bus(ThreadEnforcer.ANY),d);
		
		int output[] = {
				0x04111111,
				0x083B12AB,
				0x03000000,		//Accept this for 0 strings
		};
		String input[] = {
				"11111100",
				"3b12ab0000000000",
				"0",
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], m.packTarget(input[i]) );	
		}
	}
	
	//@Test
	@Subscribe
    public void onBlockFoundEvent(BlockFoundEvent e) {
    	//try {
    		////Block block = e.getBlock();
			////String hash = Hex.toHexString(block.hashHeader());
			//int target = block.getTarget();
			//String targetUnPacked = m.unpackTarget(target);
			//boolean result = targetUnPacked < hash;
			//assertEquals( true, result );	
		//} catch (IOException e1) {
		//	e1.printStackTrace();
		//}
    	
    }

}
