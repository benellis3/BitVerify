package bitverify.mining;

import java.lang.String;

import bitverify.block.Block;
import bitverify.entries.Entry;

//This will run in it's own thread
public class Miner {
	//Whether we are currently mining
	private boolean mining;
	
	//The number of zeros at the start of the block hash
	private int goalZeros;
	private String goal;
	
	//The pool of entries
	private Pool pool;
	
	//The block we are currently mining
	private Block blockMining;
	
	public Miner(){
		pool = new Pool();
		
		blockMining = new Block();
		
		//Block lastBlockInChain = getLastBlockInChain();
		//blockMining = new Block(lastBlockInChain);
	}
	
	public boolean mineSuccess(String hash){
		if (hash.startsWith(goal)){
			return true;
		}
		else{
			return false;
		}
	}
	
	private void updateMiningBlock(){
		Entry e = pool.takeFromPool();

		while (e != null){	//And check block has room for more entries 
			//blockMining.addEntry(entry);
		}
	}
	
	private void mineEntries(){
		updateMiningBlock();

		String result;

		while (mining){
			result = blockMining.hashBlock();
			
			if (mineSuccess(result)){
				//Broadcast block
				
				//Block lastBlockInChain = getLastBLockInChain();
				//Block blockMining = new Block(lastBlockInChain);
				
				mining = false;
			}
			
			//Add new entries to block mining as they come in
			updateMiningBlock();
			
			blockMining.header.incrementNonce();
		}
	}
	
	public void startMining(){
		mining = true;
		System.out.println("test");
		mineEntries();
	}
	
	//This gets called when a new block has been successfully mined elsewhere
	public void stopMining(){
		mining = false;
		//Return entries to pool that aren't in the new block
	}
	
	public void updateGoal(int zeros){
		goalZeros = zeros;
		
		goal = "";
		
		for (int x = 0; x < goalZeros; x++){
			goal = goal + "0";
		}
	}
	
}
