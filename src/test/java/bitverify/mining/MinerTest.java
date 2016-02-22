package bitverify.mining;

import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import org.junit.Test;

import com.j256.ormlite.logger.LocalLog;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.persistence.DataStore;
import bitverify.persistence.DatabaseStore;

public class MinerTest {

	//Test whether hashes are successful
	@Test
	public void testMineSuccess() throws SQLException, IOException{
		//We have an unpacked target of 003b20fa00000000000000000000000000000000000000000000000000000000
		
		String input[] = {
				"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
				"cf80cd8aed482d5d1527d7dc72fceff84e6326592848447d2dc0b0e87dfc9a90",
				"a5fdf69452bc32ff2ef109f4b501d84928ea04e0d6ebf2eac42cf35a9d926ba9",
				"00ffffffe6fcdc36f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
				"003b20fae6fcdc36f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
				"003b20f9e6fcdc36f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
				"0000000000000000f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
				"0000001000000000f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
		};
		boolean output[] = {
				false,
				false,
				false,
				false,
				false,
				true,
				true,
				true,
		};
		for (int i=0; i<input.length; i++){
			assertEquals(output[i], Miner.mineSuccess(input[i],0x1f3b20fa));	
		}
	}
	
	//Test whether mining targets are successfully unpacked from their integer representation
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
				0x0030000ff, //Test if it ignores initial digits (before 8 digit packed target)
				0x01ffffff,
				0x02ffffff,
				0x03ffffff,
				0x04ffffff,
				0x21ffffff,	//Test uses max 256 bit hex value (doesn't go over)
				-10, //Check it negative values unpack to zero (invalid case)
				
				//0x7fffffff	//This is the maximum value before negative due to highest bit set (two's complement) - out of range of 256 hash anyway
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
				"0",
				
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], Miner.unpackTarget(input[i]) );	
		}
	}
	
	//Test whether mining targets are successfully packed into their integer representation
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
	
	//Test the mining target calculation
	@Test
	public void testTargetCalculation() throws IOException, SQLException{
		//Turn off database output for testing purposes
		System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
		
		DataStore d = new DatabaseStore("jdbc:h2:mem:bitverify");
		
		//We store a packed target representing 2
		
		//We expect the blocks calculated targets to be
		// Target calculated for b2 - the same as b1 	4
		// Target calculated for b3 - based on b0-b2 	1	(due to genesis block being after the other times, reach lower bound of 0)
		// Target calculated for b4 - the same as b3 	4	(same as b3 as added to the database - 2)
		// Target calculated for b5 - the same as b4 	4
		// Target calculated for b6 - based on b3-b5 	8	(it took 400 milliseconds vs the intended 200)
		// Target calculated for b7 - the same as b6 	4	(same as b3 as added to the database - 2)
		// Target calculated for b8 - the same as b7	4
		// Target calculated for b9 - based on b6-b8 	12	(it took 600 milliseconds vs the intended 200)	
		// Target calculated for b10 - the same as b9 	5
		// Target calculated for b11 - the same as b10 	5
		// Target calculated for b12 - based on b9-b11 	2	(it took 100 milliseconds vs the intended 200, integer part of 5/2 = 2.5)
		
		Block b1 = new Block(Block.getGenesisBlock(),100,0x03000004,0,new ArrayList<Entry>());
		Block b2 = new Block(b1,200,0x03000004,0,new ArrayList<Entry>());
		Block b3 = new Block(b2,300,0x03000004,0,new ArrayList<Entry>());
		Block b4 = new Block(b3,400,0x03000004,0,new ArrayList<Entry>());
		Block b5 = new Block(b4,700,0x03000004,0,new ArrayList<Entry>());
		Block b6 = new Block(b5,800,0x03000004,0,new ArrayList<Entry>());
		Block b7 = new Block(b6,900,0x03000004,0,new ArrayList<Entry>());
		Block b8 = new Block(b7,1400,0x03000004,0,new ArrayList<Entry>());
		Block b9 = new Block(b8,1500,0x03000005,0,new ArrayList<Entry>());
		Block b10 = new Block(b9,1550,0x03000005,0,new ArrayList<Entry>());
		Block b11 = new Block(b10,1600,0x03000005,0,new ArrayList<Entry>());
		
		d.insertBlock(b1);
		d.insertBlock(b2);
		d.insertBlock(b3);
		d.insertBlock(b4);
		d.insertBlock(b5);
		d.insertBlock(b6);
		d.insertBlock(b7);
		d.insertBlock(b8);
		d.insertBlock(b9);
		d.insertBlock(b10);
		d.insertBlock(b11);
		
		Bus eventBus = new Bus(ThreadEnforcer.ANY);

		Miner m = new Miner(eventBus,d,2,200,10);
		
		m.stopMining();	//to prevent warning
		
		int input[] = {
				Miner.calculatePackedTarget(d, b1, eventBus),
				Miner.calculatePackedTarget(d, b2, eventBus),
				Miner.calculatePackedTarget(d, b3, eventBus),
				Miner.calculatePackedTarget(d, b4, eventBus),
				Miner.calculatePackedTarget(d, b5, eventBus),
				Miner.calculatePackedTarget(d, b6, eventBus),
				Miner.calculatePackedTarget(d, b7, eventBus),
				Miner.calculatePackedTarget(d, b8, eventBus),
				Miner.calculatePackedTarget(d, b9, eventBus),
				Miner.calculatePackedTarget(d, b10, eventBus),
				Miner.calculatePackedTarget(d, b11, eventBus),
		};
		
		int output[] = {
				4,
				1,
				4,
				4,
				8,
				4,
				4,
				12,
				5,
				5,
				2	//rounds down
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], Integer.parseInt(Miner.unpackTarget(input[i]),16));	
		}
	}
	
	//Test the calculating the proof of mining targets
	@Test
	public void testProofTarget() throws IOException,SQLException{
		System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
		
		DataStore d = new DatabaseStore("jdbc:h2:mem:bitverify");

		Miner m = new Miner(new Bus(ThreadEnforcer.ANY),d,2,200,2);
		m.stopMining();
		
		int input[] = {
				Miner.calculateMiningProofTarget(0x03000002),
				Miner.calculateMiningProofTarget(0x03000003),
		};
		
		int output[] = {
				4,
				6,
		};
		
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], Integer.parseInt(Miner.unpackTarget(input[i]),16));	
		}
		
		Miner m2 = new Miner(new Bus(ThreadEnforcer.ANY),d,2,200,0x10);
		m2.stopMining();
		
		int input2[] = {
				Miner.calculateMiningProofTarget(0x04000002),
				Miner.calculateMiningProofTarget(0x03000003),
				Miner.calculateMiningProofTarget(0x20ffffff),	//should stay the same since it is the maximum representable
				Miner.calculateMiningProofTarget(0x02000001),	//should stay the same since it is the minimum representable
		};
		
		int output2[] = {
				0x2000,
				0x30,
				0x20ffffff,
				0x1,
		};
		
		for (int i=0; i<input.length; i++){
			assertEquals( output2[i], Integer.parseInt(Miner.unpackTarget(input2[i]),16));	
		}
		
	}

	//Test the genesis block is valid
	@Test
	public void testBlockValid(){
		assertEquals(Miner.blockHashMeetDifficulty(Block.getGenesisBlock()),true);
		//If a block meets success difficulty it will also meet the mining proof difficulty
		assertEquals(Miner.miningProofMeetDifficulty(Block.getGenesisBlock()),true);
	}

}
