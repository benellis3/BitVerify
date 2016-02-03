package bitverify.mining;

import java.lang.String;

public class Miner {
	private boolean mining;
	
	//The number of zeros at the start of the block hash
	private int goalZeros;
	private String goal;
	
	private void mineEntries(){
		//Put entries in pool into block (here or elsewhere)

		//byte[] block;
		
		String result;
		
		while (mining){
			//result = hashString(hashBytes(block));
			
			//Increment nonce of header
			
			
			//if (result.startsWith(goal)){
				//Successful mine
			//}
		}
	}
	
	public void startMining(){
		mining = true;
		mineEntries();
	}
	
	public void stopMining(){
		mining = false;
	}
	
	public void updateGoal(int zeros){
		goalZeros = zeros;
		
		goal = "";
		
		for (int x = 0; x < goalZeros; x++){
			goal = goal + "0";
		}
	}
	
}
