package bitverify.mining;

import java.lang.String;
import java.math.BigInteger;

import bitverify.block.Block;
import bitverify.entries.Entry;

//This will run in it's own thread
public class Miner implements Runnable{
	//Whether we are currently mining
	private boolean mining;
	
	//REMOVE THIS
	//The number of zeros at the start of the block hash
	private int goalZeros;
	private String goal;
	//Will use 256 target, test if hash is less than this
	//REMOVE THIS
	
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
	
	public String unpackTarget(int p){
		//packedTarget stored as
		//	0xeemmmm
		//represents m * 2 ^ (bitsInByte * (e - byteOffset))
		
		BigInteger mantissa = BigInteger.valueOf(p & 0xffffff);
		int exponent = p >> (3 * bitsInByte);
		
		BigInteger result = mantissa.shiftLeft((bitsInByte * (exponent - byteOffset)));
		
		System.out.println(mantissa);
		System.out.println(Long.toHexString(exponent));
		System.out.println(result);
		
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

		while (e != null){	//And check block has room for more entries 
			//blockMining.addEntry(entry);
		}
	}
	
	@Override
	public void run(){
		updateMiningBlock();
			
		String result;

		while (mining){
			//Currently fails because block serialisation returns null
			//WE SHOULD PERFORM A DOUBLE HASH HERE 
			result = blockMining.hashBlock();
			
			if (mineSuccess(result)){
				//Broadcast block
				
				//Block lastBlockInChain = getLastBLockInChain();
				//Block blockMining = new Block(lastBlockInChain);
				
				System.out.println("Success");
				
				mining = false;
			}
			
			//Add new entries to block mining as they come in
			updateMiningBlock();
			
			blockMining.header.incrementNonce();
		}
	}
	
	public void startMining(){
		mining = true;
		run();
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
	
	public static void main(String[] args){
		Miner m = new Miner();
		//m.startMining();
		
		System.out.println(m.unpackTarget(0x1b0404cb));
	}
	
}
