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
	private final int byteOffset = 3;	//A constant used in unpacking the target
	
	//The number of blocks before we recalculate the difficulty
	private int adjustTargetFrequency = 1008;
	//The amount of time we want adjustTargetFrequency blocks to take to mine
	//private long idealMiningTime = ;
	
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
		
		//for (Entry e: pool){
		//	blockMining.addEntry(e);
		//}

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
		//blockMining.setTarget(target);
		//setPackedTarget(target);
		
		Block lastBlockInChain = dataStore.getMostRecentBlock(); //from datastore method
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
        //blockMining.addEntry(e.getEntry());
    }
	
	public void setPackedTarget(int p){
		packedTarget = p;
	}
	
	//Calculate the packed representation of the target from the string
	public int packTarget(String s){
		//To pack mantissa = shift right string length - 4, store number of shifts
		//newPackedTarget is noShifts * 2^(2*bitsInByte) + mantissa
		
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
	
	//To be called every X blocks
	//Let X be 1008 blocks
	//Let Y be 1 week
	//Reject new blocks that don't adhere to this target
	public int calculatePackedTarget(){
		//if ((noBlocks % 1008 == 0) && (noBlocks > 0)) {
			//	Timestamp a = mostRecentBlock.timeStamp();
			//	Timestamp b = XBlocksBeforeMRB.timeStamp();
			//	TimeToMine c = a - b;
		
			//if (c < Y/4) c = Y/4;
			//if (c > Y*4) c = Y*4;
		
			//newTarget = (c/Y) * unpackTarget(packedTarget);
		
			//if (newTarget > maxPossibleTarget) newTarget = maxTarget;
			//if (newTarget < minPossibleTarget) newTarget = minTarget;
		
			//return pack(newTarget);
		//else if(noBlocks == 0){
		//	return 0x1;
		//}
		//else{
		//	return mostRecentBlock.packedTarget();
		//}
			
		//Remove this:
		return 1;
	}
	
	//For quick tests
	public static void main(String[] args){
		//Miner m = new Miner(new Bus(ThreadEnforcer.ANY));
		
	}
}
