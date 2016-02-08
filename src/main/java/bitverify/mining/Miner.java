package bitverify.mining;

import java.lang.String;
import java.math.BigInteger;

import bitverify.block.Block;
import bitverify.entries.Entry;

//Functionality required:
//Ability to query most recent block from chain/database (to get its timestamp)
//Ability to jump back X blocks to get its timestamp
//Ability to read pool entries from database to add to pool class
//Ability to add entries to block and determine if block is full

//This will run in it's own thread
public class Miner implements Runnable{
	//Whether we are currently mining
	private boolean mining;
	
	private int packedTarget;
	private final int byteOffset = 3;
	
	//The pool of entries
	private Pool pool;
	
	//The block we are currently mining
	private Block blockMining;
	
	private final int bitsInByte = 8;
	
	public Miner(){
		pool = new Pool();
		
		blockMining = new Block();
		
		//Block lastBlockInChain = getLastBlockInChain();
		//blockMining = new Block(lastBlockInChain);
	}
	
	public void setPackedTarget(int p){
		packedTarget = p;
	}
	
	//To be called every X blocks
	//Let X be 2016
	//Let Y be 2 weeks
	//Reject new blocks that don't adhere to this target
	public int calculateNewPackedTarget(){
		//Timestamp a = mostRecentBlock.timeStamp();
		//Timestamp b = XBlocksBeforeMRB.timeStamp();
		//TimeToMine c = a - b;
		
		//if (c < Y/4) c = Y/4;
		//if (c > Y*4) c = Y*4;
		
		// newPackedTarget = (c/Y) * unpackTarget(packedTarget)
		
		// if (newPackedTarget > maxPossibleTarget) newPackedTarget = maxPossibleTarget
		// if (newPackedTarget < minPossibleTarget) newPackedTarget = minPossibleTarget
		
		return 1;
	}
	
	public String unpackTarget(int p){
		//packedTarget stored as
		//	0xeemmmm
		//represents m * 2 ^ (bitsInByte * (e - byteOffset))
		
		BigInteger mantissa = BigInteger.valueOf(p & 0xffffff);
		int exponent = p >> (3 * bitsInByte);
		
		BigInteger result = mantissa.shiftLeft((bitsInByte * (exponent - byteOffset)));
		
		return result.toString(16);
	}
	
	public boolean mineSuccess(String hash){
		String target = unpackTarget(packedTarget);
		
		//BigInteger.compareTo returns -1 if this BigInteger is less than the argument
		boolean lessThan = ((new BigInteger(hash,16)).compareTo(new BigInteger(target,16)) == -1);
		
		if (lessThan){
			return true;
		}
		else{
			return false;
		}
	}
	
	private void updateMiningBlock(){
		Entry e = pool.takeFromPool();

		while (e != null){
			//blockMining.addEntry(entry);
		}
	}
	
	@Override
	public void run(){
		updateMiningBlock();
			
		//String result;

		while (mining){
			//Currently fails because block serialisation returns null
			//result = blockMining.doubleHashBlock();
			
			//if (mineSuccess(result)){
				//Broadcast block
				
				//Block lastBlockInChain = getLastBLockInChain();
				//Block blockMining = new Block(lastBlockInChain);
				
				//System.out.println("Success");
				
				//mining = false;
			//}
			
			//Add new entries to block mining as they come in
			//if (not blockMining.isFull()){
				updateMiningBlock();
			//}
			
			blockMining.header.incrementNonce();
		}
	}
	
	public void startMining(){
		//Calculate new target (use most recent's one if not every Xth block)
		mining = true;
		run();
	}
	
	//This gets called when a new block has been successfully mined elsewhere
	public void stopMining(){
		mining = false;
		//Return entries to pool that aren't in the new block
	}
	
	//public static void main(String[] args){
	//	Miner m = new Miner();
		//m.startMining();
		
	//	System.out.println(m.unpackTarget(0x2a3b20fa));
	//}
	
}
