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
 * This class is responsible for performing the mining to find new blocks for the blockchain using unconfirmed entries.
 * It also adjusts the difficulty for mining based on the recent blockchain history.
 * @author Alex Day
 */
public class Miner implements Runnable{
	//Event bus for notifying mining successes and getting new entries
	private Bus eventBus;
    
	//Create an event when a block is successfully mined
	public class BlockFoundEvent {
        private Block successBlock;

        public BlockFoundEvent(Block b) {
            successBlock = b;
        }

        public Block getBlock() {
            return successBlock;
        }
    }
	
	//Database storing the blocks and entries
	private DataStore dataStore;
	
	//Flag to specify whether we are currently mining
	private boolean mining;
	
	//Limits to the mining target, the unpacked target cannot go outside this range
	private static final BigInteger minTarget = new BigInteger("1",16);
	private static final BigInteger maxTarget = new BigInteger("f",16).shiftLeft(251);
	
	//Constant used in unpacking targets (it is subtracted from the exponent)
	private static final int byteOffset = 3;	
	
	//We recalculate the mining difficulty every adjustTargetFrequency blocks
	private static int adjustTargetFrequency = 2;//1008;
	//The amount of time, in milliseconds, we want adjustTargetFrequency blocks to take to mine
	//(we want 1008 blocks to be mined every week/a block every 10 minutes)
	private static long idealMiningTime = 8000;//604800000;
	
	//Proof of mining (we multiply the success target by the scale)
	private static int miningProofDifficultyScale = 0x2;
	private int currentMiningProofTarget;
	
	//The block we are currently mining
	private Block blockMining;
	
	//Constant making calculations easier to read
	private static final int bitsInByte = 8;
	
	/**
     * Constructor for creating a miner, used for creating new blocks
     *
     * @param eventBus		instance of the event bus for sending successful blocks/proof of mining 
     * @param dataStore		instance of the database for the miner
     */
	public Miner(Bus eventBus, DataStore dataStore) throws SQLException, IOException{
		this.dataStore = dataStore;
		
		//Set up the event bus
		this.eventBus = eventBus;
		eventBus.register(this);
		
		newMiningBlock(new ArrayList<Entry>());
		
		//Add unconfirmed entries from the database to the block for mining
		List<Entry> pool = dataStore.getUnconfirmedEntries();
		
		blockMining.setEntriesList(pool);

	}
	
	/**
     * Constructor for creating a miner for testing (setting constants)
     *
     * @param eventBus		instance of the event bus for sending successful blocks/proof of mining 
     * @param dataStore		instance of the database for the miner
     * @param adjustTargetFrequency			how many blocks to wait before recalculating the mining difficulty
     * @param idealMiningTime				the number of milliseconds that 'adjustTargetFrequency' blocks should take to mine
     * @param miningProofDifficultyScale	amount to multiply the target by to get the proof of mining target
     */
	public Miner(Bus eventBus, DataStore dataStore, int adjustTargetFrequency, int idealMiningTime, int miningProofDifficultyScale) throws SQLException, IOException{
		this.dataStore = dataStore;
		
		//Set up the event bus
		this.eventBus = eventBus;
		eventBus.register(this);
		
		newMiningBlock(new ArrayList<Entry>());
		
		//Add unconfirmed entries from the database to the block for mining
		List<Entry> pool = dataStore.getUnconfirmedEntries();
		
		blockMining.setEntriesList(pool);

		Miner.adjustTargetFrequency = adjustTargetFrequency;
		Miner.idealMiningTime = idealMiningTime;
		Miner.miningProofDifficultyScale = miningProofDifficultyScale;
	}
	
	/**
     * Determine whether the block's hash meets it's target difficulty
     *
     * @param block		the block that we are testing
     */
	public static boolean blockHashMeetDifficulty(Block block){
		return mineSuccess(Hex.toHexString(block.hashHeader()),block.getTarget());
	}
	
	/**
     * Determine whether the block's hash meets the proof of mining difficulty.
     * We prove that we are mining by sending block headers that satisfy an easier target to our peers.
     * We don't need to send the entries, just the block headers for mining proof.
     * We will enforce that you need to prove you are mining in order to send entries to peers.
     * 
     * @param block		the block that we are testing
     */
	public static boolean miningProofMeetDifficulty(Block block){
		return mineSuccess(Hex.toHexString(block.hashHeader()),calculateMiningProofTarget(block.getTarget()));
	}
	
	/**
     * Check that the target field of a block is the difficulty we expect (or more difficult).
     * We will reject blocks with targets that are easier.
     * 
     * @param dataStore		instance of the database to use in this static method
     * @param block			the block who's target we are testing
     * @param parent		the parent of the block that we are testing (used to calculate the required difficulty for block)
     */
	public static boolean checkBlockDifficulty(DataStore dataStore, Block block, Block parent) throws SQLException{
		int targetShouldBe = calculatePackedTarget(dataStore, parent);
		
		return mineSuccess(unpackTarget(targetShouldBe),block.getTarget());
	}
	
	/**
     * Check that the target field of proof of mining block is the difficulty we expect (or more difficult).
     * We will reject proof blocks with targets that are easier.
     * 
     * @param dataStore		instance of the database to use in this static method
     * @param block			the block who's target we are testing
     * @param parent		the parent of the block that we are testing (used to calculate the required proof difficulty for block)
     */
	public static boolean checkMiningProofDifficulty(DataStore dataStore, Block block, Block parent) throws SQLException{
		int proofTargetShouldBe = calculateMiningProofTarget(calculatePackedTarget(dataStore, parent));
		
		return mineSuccess(unpackTarget(proofTargetShouldBe),block.getTarget());
	}
	
	
	/**
     * Determine whether a hash meets the target's difficulty (it is numerically less than the target)
     * 
     * @param hash		the 256 bit hash we compare against the target
     * @param packedT	the packed representation of the target
     */
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
	
	/**
     * Perform mining in a separate thread, we repeatedly incrementing the block's nonce and check it's hash
     */
	@Override
	public void run(){
		String result;
		
		mining = true;
		
		while (mining){
			try{
				result = Hex.toHexString(blockMining.hashHeader());
				//Successful mine
				if (mineSuccess(result, blockMining.getTarget())){
					//System.out.println("Success");
					//System.out.println("Block Hash: "+result);
					
					//Add the successful block to the blockchain (the database will ensure the entries in it are no longer unconfirmed)
					dataStore.insertBlock(blockMining);
					//Pass successful block to application logic for broadcasting to the network
					eventBus.post(new BlockFoundEvent(blockMining));
	
					newMiningBlock(new ArrayList<Entry>());
					
				}
				//Proof of mining
				else if (mineSuccess(result, currentMiningProofTarget)){
				//	//Application logic must broadcast to peers
				//	//Must maintain a list of peers in database that have received proof from
				//	//Reject incoming entries from public IPs not from the list
					eventBus.post(new NewMiningProofEvent(blockMining));
					//System.out.println("Proof Success");
					//System.out.println("Block Hash: "+result);
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
	
	/**
     * Create a new mining block with the specified entries.
     * We calculate it's target by looking at the blockchain and associate it with the block at the end of the chain.
     * The timestamp is also assigned.
     * 
     * @param entries	list of the entries for the block
     */
	public void newMiningBlock(List<Entry> entries) throws SQLException, IOException{
		//Create the next block to mine, passing the most recently mined block
		int target = calculatePackedTarget(dataStore, dataStore.getMostRecentBlock());
		
		//Keep track of the current proof of mining target (instead of storing it in the block)
		this.currentMiningProofTarget = Miner.calculateMiningProofTarget(target);
		
		//System.out.println("ProfTarget: "+stringFormat(unpackTarget(currentMiningProofTarget)));
		
		Block lastBlockInChain = dataStore.getMostRecentBlock();
		
		//System.out.println("Previous block timestamp: "+lastBlockInChain.getTimeStamp());
		//System.out.println("Current block timestamp: "+System.currentTimeMillis());
		
		blockMining = new Block(lastBlockInChain, System.currentTimeMillis(),target, 0, entries);
	}
	
	/**
     * Stop mining the current block. This gets called when a new block has been successfully mined elsewhere. 
     */
	public void stopMining(){
		mining = false;
		//Application logic should ensure uncomfirmed entries in database that were in the new block are no longer uncomfirmed
		//When we start mining again, it will get the new unconfirmed entries from the database
	}
	
	/**
     * Subscribe to new entry events on bus. We create a new block to mine with the new entry.
     * 
     *  @param e	the event that is created when a entry has been received
     */
    @Subscribe
    public void onNewEntryEvent(NewEntryEvent e) throws IOException, SQLException {
    	//Add entry from pool to block we are mining (by creating a new block)
    	List<Entry> entries = blockMining.getEntriesList();
    	
    	entries.add(e.getNewEntry());
    	
    	newMiningBlock(entries);
    }
	
    /**
     * Calculate the packed representation of the target from the string.
     * 
     *  @param s	the target hex string (64 digits or less) to pack for storage
     */
	public static int packTarget(String s){
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
		
		String mantissa = target.substring(0, sizeMantissa);				//Get the first 6 characters of the string
		int exponent = ((target.length()-(sizeMantissa)) / 2) + byteOffset;	//We divide by two since each character is half a byte
		
		int result = Integer.valueOf(mantissa,16) + (exponent << (3 * bitsInByte));	//Shift exponent three hex digits to the left for packed storage

		return result;
	}
	
	/**
     * Calculate the hexstring representation of the target from its packed form
     * 
     * packed targets are stored as:
     * 0xeemmmmmm
     * which represents m * 2 ^ (bitsInByte * (e - byteOffset))
     * 
     * 0x03000001 is min representable target
     * 0x20ffffff is max representable target
     * 
     *  @param p	the target to unpack into a 64 digit hex string
     */
	public static String unpackTarget(int p){
		//Use maximum representable target if the target is greater than this
		if (p > 0x20ffffff) p = 0x20ffffff;
		
		BigInteger mantissa = BigInteger.valueOf(p & 0xffffff);
		int exponent = p >> (3 * bitsInByte);		//Extract the exponent
		
		BigInteger result = mantissa.shiftLeft((bitsInByte * (exponent - byteOffset)));
		
		return result.toString(16);
	}
	
	/**
     * Calculate the target for the next block (i.e. the block we are mining, or a block we have received
     * from the network) by looking at the prior blocks. We look at the database and see how long the previous
     * 'adjustTargetFrequency' blocks took to mine, and adjust our target, every 'adjustTargetFrequency' blocks.
     * 
     *  @param ds		the database containing the blockchain
     *  @param block	parent of the block we are finding the target of
     */
	public static int calculatePackedTarget(DataStore ds, Block block) throws SQLException{
		//Every adjustTargetFrequency blocks we calculate the new mining difficulty
		long blocksCount = block.getHeight();
		
		// We adjust the target after every 'adjustTargetFrequency' blocks
		// i.e.
		// adjustTargetFrequency = 2
		// idealMiningTime = 100
		// b0 is the genesis block
		// b0 - b1 - b2 - b3 - b4 - b5 - b6
		// recalculate on b3 based on the time from b0 to b2 (we want b0 to b2 to take 100 milliseconds)
		// recalculate on b6 based on time from b3 to b5 (we want b3 to b5 to take 100 milliseconds)
		if (((blocksCount + 1) % (adjustTargetFrequency + 1) == 0) && (blocksCount > 0)) {
			List<Block> nMostRecent = ds.getNMostRecentBlocks(adjustTargetFrequency + 1, block);
			
			//We require that the timestamp for n blocks ago is earlier than the most recent
			long mostRecentTime = nMostRecent.get(0).getTimeStamp();
			long nAgoTime = nMostRecent.get(adjustTargetFrequency).getTimeStamp();
			long difference = mostRecentTime - nAgoTime;
			
			//System.out.println("Most Recent: "+mostRecentTime);
			//System.out.println("No ago: "+nAgoTime);
			//System.out.println("Time Difference: "+difference);
		
			//Limit exponential growth
			if (difference < idealMiningTime/4) difference = idealMiningTime/4;
			if (difference > idealMiningTime*4) difference = idealMiningTime*4;
			
			//Adjust the target by multiplying the previous target by actual time frame/expected time frame
			BigInteger newTarget = ((BigInteger.valueOf(difference)).multiply(new BigInteger(unpackTarget(block.getTarget()),16))).divide(BigInteger.valueOf(idealMiningTime));
			
			//Check if the target goes out of the specified range
			if (newTarget.compareTo(minTarget) == -1) newTarget = minTarget;
			if (newTarget.compareTo(maxTarget) == 1) newTarget = maxTarget;
			
			//System.out.println("New Target: "+stringFormat(unpackTarget(packTarget(newTarget.toString(16)))));
		
			return packTarget(newTarget.toString(16));
		}
		else{
			//System.out.println("Using targ: "+stringFormat(unpackTarget(block.getTarget())));
			
			//Otherwise use the same target as the most recent block
			return block.getTarget();
		}
	}
	
	/**
     * Calculate the target for proof of mining given the mining success target.
     * We multiply the success target by miningProofDifficultyScale to make it easier to meet this target.
     * 
     *  @param successPackedTarget	the packed target for successfully mining a block
     */
	public static int calculateMiningProofTarget(int successPackedTarget){
		BigInteger proofTarget = new BigInteger(unpackTarget(successPackedTarget),16);
		
		BigInteger proofTargetScaled = proofTarget.multiply(BigInteger.valueOf(miningProofDifficultyScale));
		proofTarget.add(BigInteger.valueOf(0x70));
		
		return packTarget(proofTargetScaled.toString(16));
	}
	
	/**
     * Make the hex string 64 digits long by adding leading zeros
     * 
     *  @param s	the string to format
     */
	public static String stringFormat(String s){
		while (s.length() < 64){
			s = "0"+s;
		}
		return s;
	}
	
	//Temporary method for quick tests
	public static void main(String[] args) throws SQLException, IOException{
		System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
		
		DataStore d = new DatabaseStore("jdbc:h2:mem:bitverify");
		
		Miner m = new Miner(new Bus(ThreadEnforcer.ANY),d);
		
		Thread miningThread = new Thread(m);
		miningThread.start();
	}
}
