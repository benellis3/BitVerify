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
			assertEquals(output[i], Miner.mineSuccess(input[i],0x1f3b20fa));	
		}
	}
	
	//Test whether mining targets are successfully unpacked from the integer representation
	@Test
	public void testUnpack() throws SQLException, IOException{
		
		int input[] = {
				0x04111111,
				0x083B12AB,
				0x03000000,
				0x20ffffff,
				0x16abcdef,
				0x0300a000,
				0x040a0000,
				0x040abcde,
				0x020000ff,
				0x030000ff,
				0x0030000ff, //Test if it ignores initial digits (before 8 digit packed arget)
				0x01ffffff,
				0x02ffffff,
				0x03ffffff,
				0x04ffffff,
				0x21ffffff,	//Test uses max 256 bit hex value (doesn't go over)
				//0x21ffffff,
				
				//0x7fffffff	//This is the maximum value before negative due to highest bit set (two's complement) - out or range of 256 hash anyway
		};
		String output[] = {
				"11111100",
				"3b12ab0000000000",
				"0",
				"ffffff0000000000000000000000000000000000000000000000000000000000",
				"abcdef00000000000000000000000000000000000000",
				"a000",
				"a000000",
				"abcde00",
				"0",	//Reject negative targets
				"ff",
				"ff",
				"ff",
				"ffff",
				"ffffff",
				"ffffff00",
				"ffffff0000000000000000000000000000000000000000000000000000000000",
				
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], Miner.unpackTarget(input[i]) );	
		}
	}
	
	//Test whether mining targets are successfully packed into the integer representation
	@Test
	public void testPack() throws SQLException, IOException{
		
		int output[] = {
				0x04111111,
				0x083B12AB,
				0x03000000,		//Accept this for zero strings
				0x03000000,
				0x20FFFFFF,
				0x20FFFFFF,
				0x16ABCDEF,
				0x16ABCDEF,
				0x16ABCDEF,
				0x20FFFFFF,
				0x0300A000,
				0x040A0000,
				0x040ABCDE,
		};
		String input[] = {
				"11111100",
				"3b12ab0000000000",
				"0",
				"",
				"ffffff0000000000000000000000000000000000000000000000000000000000",
				"ffffff00000000000000abcdef00000000000000000000000000000000000000",
				"00000000000000000000abcdef00000000000000000000000000000000000000",
				"000000abcdef00000000000000000000000000000000000000",
				"abcdef00000000000000000000000000000000000000",
				"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
				"a000",
				"a000000",
				"abcdef1",
				
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], Miner.packTarget(input[i]) );	
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
