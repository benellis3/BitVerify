//This code is not yet in working order but is 'proof of work'

package bitverify.mining;

import bitverify.crypto;
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
			result = hashBytes(block);
			
			if (result.startsWith(goal)){
				//Successful mine
			}
		}
	}
	
	public startMining(){
		mining = true;
		mineEntries();
	}
	
	public stopMining(){
		mining = false;
	}
	
	public updateGoal(int zeros){
		goalZeros = zeros;
		
		goal = "";
		
		for (int x = 0; x < goalZeros; x++){
			goal = goal + "0";
		}
	}
	
}
