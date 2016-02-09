package bitverify.mining;

import java.lang.String;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.persistence.DataStore;

/**
 * This class is responsible for performing the mining on a new block using unconfirmed entries.
 * It also computes the difficulty for mining based on the recent blockchain history.
 * @author Alex Day
 */
public class Miner implements Runnable{
	//We create an event when a block is successfully mined and subscribe to new entry for mining events
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
	
	//Database storing the blockchain and unconfirmed entries
	private DataStore dataStore;
	
	//Whether we are currently mining
	private boolean mining;
	
	//The mining target (the hash of the block must be less than the unpacked target)
	private int packedTarget;
	private final int initialTarget = 0x08800000;
	//Ensure the target does not go outside the specified range
	private final BigInteger minTarget = new BigInteger("1",16);
	private final BigInteger maxTarget = new BigInteger("f",16).shiftLeft(255);
	
	//Minimum of 1
	//Maximum of f << 255
	
	private final int byteOffset = 3;	//A constant used in unpacking the target
	
	//The number of blocks before we recalculate the difficulty
	private int adjustTargetFrequency = 1008;
	//The amount of time we want adjustTargetFrequency blocks to take to mine, in milliseconds
	//(one week)
	private long idealMiningTime = 604800000;
	
	//The block we are currently mining
	private Block blockMining;
	
	//Simple constant for making calculations easier to read
	private final int bitsInByte = 8;
	
	//Temporary Constructor for testing purposes
	public Miner(Bus eventBus) throws SQLException{
		//Temporary block creation until we can pass the most recent block
		blockMining = new Block();
		
		//newMiningBlock();
	    
		//Set up the event bus
		this.eventBus = eventBus;
		eventBus.register(this);
		
		//this.dataStore = dataStore;
		
		//Add unconfirmed entries from the database to the block for mining
		//List<Entry> pool = dataStore.getUnconfirmedEntries();
		
		//for (Entry e: pool){
		//	blockMining.addEntry(e);
		//}

	}
	
	public Miner(Bus eventBus, DataStore dataStore) throws SQLException{
		//Temporary block creation until we can pass the most recent block
		blockMining = new Block();
		
		newMiningBlock();
	    
		//Set up the event bus
		this.eventBus = eventBus;
		eventBus.register(this);
		
		this.dataStore = dataStore;
		
		//Add unconfirmed entries from the database to the block for mining
		List<Entry> pool = dataStore.getUnconfirmedEntries();
		
		for (Entry e: pool){
			blockMining.addEntry(e);
		}

	}
	
	//Determine whether the block's hash meets the target's requirements
	public boolean mineSuccess(String hash){
		String target = unpackTarget(packedTarget);
		
		//BigInteger.compareTo returns -1 if this BigInteger is less than the argument BigInteger
		boolean lessThan = ((new BigInteger(hash,16)).compareTo(new BigInteger(target,16)) == -1);
		
		if (lessThan){
			return true;
		}
		else{
			return false;
		}
	}
	
	//Perform mining in a separate thread
	@Override
	public void run(){
		String result;
		
		mining = true;

		while (mining){
			//Currently fails because block serialisation returns null
			result = blockMining.hashBlock();
			
			if (mineSuccess(result)){
				try{
					//Add the successful block to the blockchain (it will ensure the entries are no longer unconfirmed)
					dataStore.createBlock(blockMining);
					//Pass successful block to application logic for broadcasting to the network
					eventBus.post(new BlockFoundEvent(blockMining));

					newMiningBlock();
				}
				catch (SQLException e){
					e.printStackTrace();
				}
			}
			
			//Increment the header's nonce to generate a new hash
			blockMining.header.incrementNonce();
		}
	}
	
	public void newMiningBlock() throws SQLException{
		//Create the next block to mine, passing the most recently mined block (it's hash is required for the header)
		
		int target = calculatePackedTarget();
		//////// WAITING ON BLOCK
		//blockMining.setTarget(target);
		//setPackedTarget(target);
		
		Block lastBlockInChain = dataStore.getMostRecentBlock(); //from datastore method
		//////// WAITING ON BLOCK
		//blockMining = new Block(lastBlockInChain, target);
	}
	
	//This gets called when a new block has been successfully mined elsewhere
	public void stopMining(){
		mining = false;
		//Application logic should ensure uncomfirmed entries in database that were in the new block are no longer uncomfirmed
	}
	
	//Subscribe to new entry events on bus
    @Subscribe
    public void onNewEntryEvent(NewEntryEvent e) {
    	//Add entry from pool to block we are mining
        blockMining.addEntry(e.getEntry());
    }
	
	public void setPackedTarget(int p){
		packedTarget = p;
	}
	
	//Calculate the packed representation of the target from the string
	public int packTarget(String s){
		//Require that the string is the correct format and is represents an integer
		
		int sizeMantissa = 6;
		
		if (s.length() < 6) sizeMantissa = s.length();
		
		String mantissa = s.substring(0, sizeMantissa);	//Get the first 6 characters of the string
		int exponent = ((s.length()-(sizeMantissa)) / 2) + byteOffset;	//We divide by two since each character is half a byte
		
		int result = Integer.valueOf(mantissa,16) + (exponent << (3 * bitsInByte));
		
		return result;
	}
	
	//Calculate the hexstring representation of the target from its packed form
	public String unpackTarget(int p){
		//packedTarget stored as
		//	0xeemmmm
		//represents m * 2 ^ (bitsInByte * (e - byteOffset))
		
		BigInteger mantissa = BigInteger.valueOf(p & 0xffffff);
		int exponent = p >> (3 * bitsInByte);		//Extract the exponent
		
		BigInteger result = mantissa.shiftLeft((bitsInByte * (exponent - byteOffset)));
		
		return result.toString(16);
	}
	
	//Reject new blocks that don't adhere to this target
	public int calculatePackedTarget() throws SQLException{
		//Every adjustTargetFrequency blocks we calculate the new mining difficulty
		if ((dataStore.getNumberBlocks() % adjustTargetFrequency == 0) && (dataStore.getNumberBlocks() > 0)) {
			long mostRecentTime = dataStore.getMostRecentBlock().header.getTimeStamp();
			long nAgoTime = dataStore.getNthMostRecentBlock(adjustTargetFrequency).header.getTimeStamp();
			long difference = mostRecentTime - nAgoTime;
		
			//Limit exponential growth
			if (difference < idealMiningTime/4) difference = idealMiningTime/4;
			if (difference > idealMiningTime*4) difference = idealMiningTime*4;
		
			BigInteger newTarget = ((BigInteger.valueOf(difference)).multiply(new BigInteger(unpackTarget(packedTarget),16))).divide(BigInteger.valueOf(idealMiningTime));
			
			if (newTarget.compareTo(minTarget) == -1) newTarget = minTarget;
			if (newTarget.compareTo(maxTarget) == 1) newTarget = maxTarget;
		
			return packTarget(newTarget.toString(16));
		}
		else if(dataStore.getNumberBlocks() == 0){
			//Start with initial target
			return initialTarget;
		}
		else{
			//If not every adjustTargetFrequency blocks then we use the same target as the most recent block
			return dataStore.getMostRecentBlock().header.getTarget();
		}
	}
	
	//For quick tests
	public static void main(String[] args){
		//Miner m = new Miner(new Bus(ThreadEnforcer.ANY));
		
	}
}
