package bitverify.mining;

import java.io.IOException;
import java.lang.String;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.j256.ormlite.logger.LocalLog;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.network.NewEntryEvent;
import bitverify.network.NewMiningProofEvent;
import bitverify.persistence.DataStore;
import bitverify.persistence.DatabaseStore;

import org.bouncycastle.util.encoders.Hex;

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
	
	//Database storing the blockchain and unconfirmed entries
	private DataStore dataStore;
	
	//Whether we are currently mining
	private boolean mining;
	
	//Ensure the target does not go outside the specified range
	private static final BigInteger minTarget = new BigInteger("1",16);				//0x03000001 is min possible
	private static final BigInteger maxTarget = new BigInteger("f",16).shiftLeft(255); //0x20ffffff is max possible
	
	//Minimum of 1
	//Maximum of f << 255
	
	private static final int byteOffset = 3;	//A constant used in unpacking the target
	
	//The number of blocks before we recalculate the difficulty
	//private static final int adjustTargetFrequency = 1008;
	//The amount of time we want adjustTargetFrequency blocks to take to mine, in milliseconds
	//(one week)
	//private static final long idealMiningTime = 604800000;
	
	//Test times
	private static final int adjustTargetFrequency = 2;
	private static final long idealMiningTime = 15000;
	
	//Proof of mining targets
	private static final int miningProofDifficultyScale = 0x2;
	private int currentMiningProofTarget;
	
	//The block we are currently mining
	private Block blockMining;
	
	//Simple constant for making calculations easier to read
	private static final int bitsInByte = 8;
	
	public Miner(Bus eventBus, DataStore dataStore) throws SQLException, IOException{
		this.dataStore = dataStore;
		//System.out.println(dataStore.getBlocksCount());
		
		//Set up the event bus
		this.eventBus = eventBus;
		eventBus.register(this);
		
		newMiningBlock(new ArrayList<Entry>());
		
		//Add unconfirmed entries from the database to the block for mining
		List<Entry> pool = dataStore.getUnconfirmedEntries();
		
		blockMining.setEntriesList(pool);

	}
	
	//Determine whether the block's hash meets it's target requirements
	public static boolean blockHashMeetDifficulty(Block b){
		return mineSuccess(Hex.toHexString(b.hashHeader()),b.getTarget());
	}
	
	public static boolean miningProofMeetDifficulty(Block b){
		return mineSuccess(Hex.toHexString(b.hashHeader()),calculateMiningProofTarget(b.getTarget()));
	}
	
	//Check target is that given by target calculation (or is more difficult)
	public static boolean checkBlockDifficulty(DataStore ds, Block b, Block parent) throws SQLException{
		int targetShouldBe = calculatePackedTarget(ds, parent);
		
		return mineSuccess(unpackTarget(targetShouldBe),b.getTarget());
	}
	
	public static boolean checkMiningProofDifficulty(DataStore ds, Block b, Block parent) throws SQLException{
		int proofTargetShouldBe = calculateMiningProofTarget(calculatePackedTarget(ds, parent));
		
		return mineSuccess(unpackTarget(proofTargetShouldBe),b.getTarget());
	}
	
	
	//Determine whether a hash meets the target's difficulty
	public static boolean mineSuccess(String hash, int packedT){
		String target = unpackTarget(packedT);
		
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

		//System.out.println("Target is"+unpackTarget(packedTarget));
		
		while (mining){
			try{
				result = Hex.toHexString(blockMining.hashHeader());
				if (mineSuccess(result, blockMining.getTarget())){
					System.out.println("Success");
					System.out.println("Block Hash: "+result);
					//System.out.println(blockMining.getNonce());
					
					//Add the successful block to the blockchain (it will ensure the entries are no longer unconfirmed)
					dataStore.insertBlock(blockMining);
					//Pass successful block to application logic for broadcasting to the network
					eventBus.post(new BlockFoundEvent(blockMining));
	
					newMiningBlock(new ArrayList<Entry>());
					
				}
				else if (mineSuccess(result, currentMiningProofTarget)){
				//	//Application logic must broadcast to peers
				//	//Must maintain a list of peers in database that have received proof from
				//	//Reject incoming entries from public IPs not from the list
					eventBus.post(new NewMiningProofEvent(blockMining));
					System.out.println("Proof Success");
					System.out.println("Block Hash: "+result);
				}
				
				//Increment the header's nonce to generate a new hash
				blockMining.incrementNonce();
				}
			catch (SQLException e){
				e.printStackTrace();
			}
			catch (IOException e){
				e.printStackTrace();
			}
		}
	}
	
	public void newMiningBlock(List<Entry> entries) throws SQLException, IOException{
		//Create the next block to mine, passing the most recently mined block (it's hash is required for the header)
		
		int target = calculatePackedTarget(dataStore, dataStore.getMostRecentBlock());
		
		this.currentMiningProofTarget = Miner.calculateMiningProofTarget(target);
		
		System.out.println("New Proof Target: "+unpackTarget(currentMiningProofTarget));
		
		Block lastBlockInChain = dataStore.getMostRecentBlock();
		
		//System.out.println("Previous block timestamp: "+lastBlockInChain.getTimeStamp());
		//System.out.println("Current block timestamp: "+System.currentTimeMillis());
		
		blockMining = new Block(lastBlockInChain, System.currentTimeMillis(),target, 0, entries);
	}
	
	//This gets called when a new block has been successfully mined elsewhere
	public void stopMining(){
		mining = false;
		//Application logic should ensure uncomfirmed entries in database that were in the new block are no longer uncomfirmed
		//When we start mining again, it will get the new unconfirmed entries from the database
	}
	
	//Subscribe to new entry events on bus
    @Subscribe
    public void onNewEntryEvent(NewEntryEvent e) throws IOException, SQLException {
    	//Add entry from pool to block we are mining (by creating a new block)
    	List<Entry> entries = blockMining.getEntriesList();
    	
    	entries.add(e.getNewEntry());
    	
    	newMiningBlock(entries);
    }
	
	//Calculate the packed representation of the target from the string
	public static int packTarget(String s){
		//Require that the string is the correct format
		
		//Remove leading zeros
		String target = s.replaceFirst("^0+", "");

		int sizeMantissa = 6;
		
		//There must be a byte number of places to the right of the 6 mantissa bits
		//If there is not we include a leading zero
		if (!((target.length() - sizeMantissa) % 2 == 0)){
			target = "0"+target;
		}
		
		//If the target is zero
		if (target.equals("")) return 0x03000000;
		
		if (target.length() < sizeMantissa){
			//We do not need to shift
			return Integer.valueOf(target,16) + 0x03000000;
		}
		
		String mantissa = target.substring(0, sizeMantissa);	//Get the first 6 characters of the string
		int exponent = ((target.length()-(sizeMantissa)) / 2) + byteOffset;	//We divide by two since each character is half a byte
		
		int result = Integer.valueOf(mantissa,16) + (exponent << (3 * bitsInByte));	//Shift exponent three hex digits to the left for packed storage

		return result;
	}
	
	//Calculate the hexstring representation of the target from its packed form
	public static String unpackTarget(int p){
		//packedTarget stored as
		//	0xeemmmmmm
		//represents m * 2 ^ (bitsInByte * (e - byteOffset))
		
		//Use maximum representable target if greater than this
		if (p > 0x20ffffff) p = 0x20ffffff;
		
		BigInteger mantissa = BigInteger.valueOf(p & 0xffffff);
		int exponent = p >> (3 * bitsInByte);		//Extract the exponent
		
		BigInteger result = mantissa.shiftLeft((bitsInByte * (exponent - byteOffset)));
		
		return result.toString(16);
	}
	
	public static String stringFormat(String s){
		while (s.length() < 64){
			s = "0"+s;
		}
		return s;
	}
	
	//Reject new blocks that don't adhere to this target
	public static int calculatePackedTarget(DataStore ds, Block block) throws SQLException{
		
		//Every adjustTargetFrequency blocks we calculate the new mining difficulty
		long blocksCount = block.getHeight();
		
		// When we can get adjustTargetFrequency before the current
		if ((blocksCount % adjustTargetFrequency == 0) && (blocksCount > 1)) {
			List<Block> nMostRecent = ds.getNMostRecentBlocks(adjustTargetFrequency + 1, block);
			
			//These datastore retrievals seems to return incorrect blocks
			long mostRecentTime = nMostRecent.get(0).getTimeStamp();
			long nAgoTime = nMostRecent.get(adjustTargetFrequency).getTimeStamp();
			long difference = mostRecentTime - nAgoTime;
			
			//System.out.println("Most Recent: "+mostRecentTime);
			//System.out.println("No ago: "+nAgoTime);
			System.out.println("Time Difference: "+difference);
		
			//Limit exponential growth
			if (difference < idealMiningTime/4) difference = idealMiningTime/4;
			if (difference > idealMiningTime*4) difference = idealMiningTime*4;
			
			BigInteger newTarget = ((BigInteger.valueOf(difference)).multiply(new BigInteger(unpackTarget(block.getTarget()),16))).divide(BigInteger.valueOf(idealMiningTime));
			
			if (newTarget.compareTo(minTarget) == -1) newTarget = minTarget;
			if (newTarget.compareTo(maxTarget) == 1) newTarget = maxTarget;
			
			System.out.println("New Target: "+stringFormat(newTarget.toString(16)));
		
			return packTarget(newTarget.toString(16));
		}
		//else if(blocksCount == 0){
			//Start with initial target
		//	return initialTarget;
		//}
		else{
			//If not every adjustTargetFrequency blocks then we use the same target as the most recent block
			return block.getTarget();
		}
	}
	
	public static int calculateMiningProofTarget(int successTarget){
		BigInteger proofTarget = new BigInteger(unpackTarget(successTarget),16);
		
		BigInteger proofTargetScaled = proofTarget.multiply(BigInteger.valueOf(miningProofDifficultyScale));
		proofTarget.add(BigInteger.valueOf(0x70));
		
		return packTarget(proofTargetScaled.toString(16));
	}
	
	//For quick tests
	public static void main(String[] args) throws SQLException, IOException{
		System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
		
		DataStore d = new DatabaseStore("jdbc:h2:mem:bitverify");
		
		Miner m = new Miner(new Bus(ThreadEnforcer.ANY),d);
		
		Thread miningThread = new Thread(m);
		miningThread.start();
	}
}
