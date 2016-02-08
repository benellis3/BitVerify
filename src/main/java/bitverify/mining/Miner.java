package bitverify.mining;

import java.lang.String;
import java.math.BigInteger;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import bitverify.OttoExample.OttoEvent;
import bitverify.block.Block;
import bitverify.entries.Entry;

//Functionality required:
//Ability to query most recent block from chain/database (to get its timestamp)
//Ability to jump back X blocks to get its timestamp
//Ability to read pool entries from database to add to pool class
//Ability to add entries to block and determine if block is full

/**
 * This class is responsible for performing the mining on a new block using unconfirmed entries.
 * It also computes the difficulty for mining based on the recent blockchain history.
 * @author Alex Day
 */
public class Miner implements Runnable{
	private Bus eventBus;
    
	public class BlockFoundEvent {
        private Block successBlock;

        public BlockFoundEvent(Block b) {
            successBlock = b;
        }

        public Block getBlock() {
            return successBlock;
        }
    }
	
	public class NewEntryEvent {
        private Entry entry;

        public NewEntryEvent(Entry e) {
            entry = e;
        }

        public Entry getEntry() {
            return entry;
        }
    }
	
	//Whether we are currently mining
	private boolean mining;
	
	private int packedTarget;
	private final int byteOffset = 3;
	
	//The pool of entries
	private Pool pool;
	
	//The block we are currently mining
	private Block blockMining;
	
	private final int bitsInByte = 8;
	
	public Miner(Bus eventBus){
		pool = new Pool();
		
		blockMining = new Block();
		
		//Block lastBlockInChain = getLastBlockInChain();
		//blockMining = new Block(lastBlockInChain);
	       
		this.eventBus = eventBus;
		eventBus.register(this);

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
	//Subscribe to new entry events on bus
    @Subscribe
    public void onNewEntryEvent(NewEntryEvent e) {
    	//Add entry from pool to block we are mining
        //blockMining.addEntry(e.getEntry());
    }
	
	@Override
	public void run(){
		String result;

		while (mining){
			//Currently fails because block serialisation returns null
			result = blockMining.hashBlock();
			
			if (mineSuccess(result)){
				//Pass successful block to application logic for broadcasting to the network
				eventBus.post(new BlockFoundEvent(blockMining));
				
				//Block lastBlockInChain = getLastBLockInChain();
				//Block blockMining = new Block(lastBlockInChain);
			}
			
			//Add new entries to block mining as they come in
			//Will use a busEvent to handle this
			
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
	
	public static void main(String[] args){
		Miner m = new Miner(new Bus());
		m.startMining();
		
	}
	
}
