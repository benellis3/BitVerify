package bitverify.mining;

import static org.junit.Assert.*;

import org.junit.Test;

public class MinerTest {

	@Test
	public void testMineSuccess() {
		Miner m = new Miner();
		
		m.updateGoal(8);
		
		String input[] = {
				"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
				"cf80cd8aed482d5d1527d7dc72fceff84e6326592848447d2dc0b0e87dfc9a90",
				"a5fdf69452bc32ff2ef109f4b501d84928ea04e0d6ebf2eac42cf35a9d926ba9",
				"00000000e6fcdc36f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
				"0000000000000000f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
				"0000001000000000f7db2c2d9a8cd6ddf31763c0ada5fcf27904d445f6dc00e5",
		};
		boolean output[] = {
				false,
				false,
				false,
				true,
				true,
				false,
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], m.mineSuccess(input[i]) );	
		}
	}

}
