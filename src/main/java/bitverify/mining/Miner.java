package bitverify.mining;

import java.io.IOException;
import java.lang.String;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.block.Block;
import bitverify.network.NewBlockEvent;
import bitverify.network.NewEntryEvent;
import bitverify.network.NewMiningProofEvent;
import bitverify.persistence.DataStore;

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
	public static class BlockFoundEvent {
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
	private volatile boolean mining;
	
	//Limits to the mining target, the unpacked target cannot go outside this range
	private static final BigInteger minTarget = new BigInteger("1",16);
	private static final BigInteger maxTarget = new BigInteger("f",16).shiftLeft(251);
	
	//Constant used in unpacking targets (it is subtracted from the exponent)
	private static final int byteOffset = 3;	
	
	//The maximum factor that the target may grow or shrink by on each target recalculation
	private static int growthFactorLimit = 4;
	//We recalculate the mining difficulty adjustTargetFrequency + 1 blocks (so it is the time to mine adjustTargetFrequency blocks)
	private static int adjustTargetFrequency = 3;	//For actual system use rather than demoing it might be 1008
	//The amount of time, in milliseconds, we want adjustTargetFrequency + 2 blocks to take to mine
	//(+2 due to removing gaps between periods, we use +1 for the first period to avoid using the genesis timestamp)
	private static long idealMiningTime = 30000;	//For actual system use rather than demoing it might be 604800000
	//this means
	//we want 3 blocks to be mined every 30 seconds/a block every 10 seconds
	
	//Proof of mining (we multiply the success target by the scale)
	private static int miningProofDifficultyScale = 0x2;	//For actual system use rather than demoing it might be 0x800;
	private int currentMiningProofTarget;
	
	//The block we are currently mining
	private Block blockMining;
	
	//Constant making calculations easier to read
	private static final int bitsInByte = 8;
	
	//Whether to print the current target (when we first start the miner, except when the target is immediately changed)
	private static boolean printTarget = true;
	
	/**
     * Constructor for creating a miner, used for creating new blocks
     *
     * @param eventBus		instance of the event bus for sending successful blocks/proof of mining 
     * @param dataStore		instance of the database for the miner
     * @throws SQLException
     * @throws IOException
     */
	public Miner(Bus eventBus, DataStore dataStore) throws SQLException, IOException{
		this.dataStore = dataStore;
		
		//Set up the event bus
		this.eventBus = eventBus;
		eventBus.register(this);
		
		//Add unconfirmed entries from the database to the block for mining
		newMiningBlock();
	}
	
	/**
     * Constructor for creating a miner for testing (setting constants)
     *
     * @param eventBus		instance of the event bus for sending successful blocks/proof of mining 
     * @param dataStore		instance of the database for the miner
     * @param adjustTargetFrequency			how many blocks to wait before recalculating the mining difficulty
     * @param idealMiningTime				the number of milliseconds that 'adjustTargetFrequency' blocks should take to mine
     * @param miningProofDifficultyScale	amount to multiply the target by to get the proof of mining target
     * @throws SQLException
     * @throws IOException
     */
	public Miner(Bus eventBus, DataStore dataStore, int adjustTargetFrequency, int idealMiningTime, int miningProofDifficultyScale) throws SQLException, IOException{
		this.dataStore = dataStore;
		
		//Set up the event bus
		this.eventBus = eventBus;
		eventBus.register(this);

		//Add unconfirmed entries from the database to the block for mining
		newMiningBlock();

		Miner.adjustTargetFrequency = adjustTargetFrequency;
		Miner.idealMiningTime = idealMiningTime;
		Miner.miningProofDifficultyScale = miningProofDifficultyScale;
	}
	
	/**
     * Determine whether the block's hash meets its target difficulty
     *
     * @param block		the block that we are testing
     * @return whether the block's hash meets its target
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
     * @return whether the block's hash meets its proof of mining target
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
     * @param eventBus		instance of the eventbus so the static method can print info to the GUI
     * @return whether the block's target is the difficulty we require
     * @throws SQLException
     */
	public static boolean checkBlockDifficulty(DataStore dataStore, Block block, Block parent, Bus eventBus) throws SQLException{
		//Pass new event bus so output is not displayed in Miner output, since we are verifying not calculating a new target
		int targetShouldBe = calculatePackedTarget(dataStore, parent, new Bus(ThreadEnforcer.ANY));
		
		return targetCorrect(targetShouldBe,block.getTarget());
	}
	
	/**
     * Check that the target field of proof of mining block is the difficulty we expect (or more difficult).
     * We will reject proof blocks with targets that are easier.
     * 
     * @param dataStore		instance of the database to use in this static method
     * @param block			the block who's target we are testing
     * @param parent		the parent of the block that we are testing (used to calculate the required proof difficulty for block)
     * @param eventBus		instance of the eventbus so the static method can print info to the GUI
     * @return whether the proof of mining block's target is the difficulty we require
     * @throws SQLException
     */
	public static boolean checkMiningProofDifficulty(DataStore dataStore, Block block, Block parent, Bus eventBus) throws SQLException{
		//Pass new event bus so output is not displayed in Miner output, since we are verifying not calculating a new target
		int proofTargetShouldBe = calculateMiningProofTarget(calculatePackedTarget(dataStore, parent, new Bus(ThreadEnforcer.ANY)));
		
		return targetCorrect(proofTargetShouldBe,block.getTarget());
	}
	
	
	/**
     * Determine whether a hash meets the target's difficulty (it is numerically less than the target)
     * 
     * @param hash		the 256 bit hash we compare against the target
     * @param packedT	the packed representation of the target
     * @return whether the unpacked 256 bit hash is less than the unpacked 256 bit target
     */
	public static boolean mineSuccess(String hash, int packedT){
		String target = unpackTarget(packedT);
		
		//BigInteger.compareTo returns -1 if this BigInteger is less than the argument BigInteger
		boolean lessThan = ((new BigInteger(hash,16)).compareTo(new BigInteger(target,16)) == -1);
		
		return lessThan;
	}
	
	/**
     * Determine whether the target of a block is less than or equal to the target it should have
     * 
     * @param targetShouldBe	the packed target that it should be
     * @param targetActual		the packed target that is in the block
     * @return whether the actual target is less than or equal to what it should be
     */
	public static boolean targetCorrect(int targetShouldBe, int targetActual){
		String target = unpackTarget(targetShouldBe);
		String actual = unpackTarget(targetActual);
		
		//BigInteger.compareTo returns -1 if this BigInteger is less than the argument BigInteger
		int comparison = (new BigInteger(actual,16)).compareTo(new BigInteger(target,16));
		
		//The target must be less than or equal to required target
		if ((comparison == -1) || (comparison == 0)){
			return true;
		}
		else{
			return false;
		}
	}
	
	/**
     * Perform mining in a separate thread, we repeatedly incrementing the block's nonce and check its hash
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
					Block successfulBlock = blockMining;
					eventBus.post(new LogEvent("Successful block mine",LogEventSource.MINING,Level.INFO));
					eventBus.post(new LogEvent("Block Hash:		"+result,LogEventSource.MINING,Level.INFO));
					eventBus.post(new LogEvent("Block id:	  	"+ Base64.getEncoder().encodeToString(successfulBlock.getBlockID()),LogEventSource.MINING,Level.INFO));
					//Add the successful block to the blockchain (the database will ensure the entries in it are no longer unconfirmed)
					dataStore.insertBlock(successfulBlock);
					//Pass successful block to application logic for broadcasting to the network
					eventBus.post(new BlockFoundEvent(successfulBlock));
	
					newMiningBlock();
				}
				//Proof of mining
				else if (mineSuccess(result, currentMiningProofTarget)){
					//Application logic must broadcast to peers
					//Must maintain a list of peers in database that have received proof from
					//Reject incoming entries from public IPs not from the list
					eventBus.post(new NewMiningProofEvent(blockMining));
					//eventBus.post(new LogEvent("Successful proof of mining",LogEventSource.MINING,Level.INFO));
					//eventBus.post(new LogEvent("Proof Block Hash:	"+result,LogEventSource.MINING,Level.INFO));
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
     * We calculate its target by looking at the blockchain and associate it with the block at the end of the chain.
     * The timestamp is also assigned.
     * 
     * @throws SQLException
     * @throws IOException
     */
	public void newMiningBlock() throws SQLException, IOException{
		Block mostRecentBlock = dataStore.getMostRecentBlock();
		//Create the next block to mine, passing the most recently mined block
		int target = calculatePackedTarget(dataStore, mostRecentBlock, eventBus);
		
		//Keep track of the current proof of mining target (instead of storing it in the block)
		this.currentMiningProofTarget = Miner.calculateMiningProofTarget(target);

		blockMining = new Block(mostRecentBlock, System.currentTimeMillis(),target, 0, dataStore.getUnconfirmedEntries());
	}
	
	/**
     * Stop mining the current block. This gets called when a new block has been successfully mined elsewhere. 
     */
	public void stopMining(){
		mining = false;
	}
	
	/**
     * Subscribe to new entry events on bus. We create a new block to mine with the new entry.
     * 
     *  @param e	the event that is created when a entry has been received
     *  @throws SQLException
     * 	@throws IOException
     */
    @Subscribe
    public void onNewEntryEvent(NewEntryEvent e) throws IOException, SQLException {
    	//Add entry from pool to block we are mining (by creating a new block)
    	eventBus.post(new LogEvent("About to mine new entry",LogEventSource.MINING,Level.INFO));
    	newMiningBlock();
    }
    
    /**
     * Subscribe to block found events on bus. We restart the Miner when a new block has been found elsewhere.
     * 
     *  @param e	the event that is created when a new block has been found from the network
     *  @throws SQLException
     * 	@throws IOException
     */
    @Subscribe
    public void onNewBlockEvent(NewBlockEvent e) throws IOException, SQLException {
    	//A new block as been found elsewhere, abort our current block
    	
    	newMiningBlock();
    }
	
    /**
     * Calculate the packed representation of the target from the string.
     * 
     *  @param s	the target hex string (64 digits or less) to pack for storage
     *  @return the packed form of the 256 bit target (64 digit hex string)
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
     *  @return the unpacked form of the target
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
     *  @param eventBus		instance of the eventbus so the static method can print info to the GUI
     *  @return the target we should assign to the next block
     *  @throws SQLException
     */
	public static int calculatePackedTarget(DataStore ds, Block block, Bus eventBus) throws SQLException{
		//Every adjustTargetFrequency + 1 blocks (the time to time adjustTargetFrequency blocks) we calculate the new mining difficulty
		long blocksCount = block.getHeight();
		
		// We adjust the target every adjustTargetFrequency + 1 blocks
		// i.e.
		// adjustTargetFrequency = 2
		// idealMiningTime = 100
		// b0 is the genesis block
		// b0 - b1 - b2 - b3 - b4 - b5 - b6 - b7 - b8 - b9 - b10
		// recalculate on b4 based on the time from b1 to b3 (we want b1 to b3 to take 100 milliseconds) due to avoiding genesis block timestamp
		// recalculate on b7 based on time from b3 to b6 (we want b3 to b6 to take 100 milliseconds)
		// recalculate on b10 based on time from b6 to b9 (we want b6 to b9 to take 100 milliseconds)
		//
		// for attack mitigation purposes, there must be no unaccounted time (no gap between calculations)
		// e.g. we don't want to calculate times b1 to b3, then b4 to b6, as the b3 to b4 time is a security risk
		// (since the block chain no longer ensures subsequent blocks have later time stamps)
		if (((blocksCount + 1) % (adjustTargetFrequency + 1) == 1) && (blocksCount > 1)) {
			List<Block> nMostRecent = ds.getNMostRecentBlocks(adjustTargetFrequency + 2, block);
			
			//We require that the timestamp for n blocks ago is earlier than the most recent
			long mostRecentTime = nMostRecent.get(0).getTimeStamp();
			long nAgoTime;
			int excludingDueToGenesis;
			if (blocksCount+1 == adjustTargetFrequency+1+1){
				//avoid using genesis timestamp for first recalculation
				nAgoTime = nMostRecent.get(adjustTargetFrequency).getTimeStamp();
				excludingDueToGenesis = 1;
			} else {
				nAgoTime = nMostRecent.get(adjustTargetFrequency+1).getTimeStamp();
				excludingDueToGenesis = 0;
			}
			long difference = mostRecentTime - nAgoTime;
			
			eventBus.post(new LogEvent("Calculating new target",LogEventSource.MINING,Level.INFO));
			eventBus.post(new LogEvent("Previous "+(adjustTargetFrequency+(1-excludingDueToGenesis))+" blocks took "+difference+" milliseconds to mine",LogEventSource.MINING,Level.INFO));
			eventBus.post(new LogEvent("We want it to take "+idealMiningTime+" milliseconds",LogEventSource.MINING,Level.INFO));
		
			if (printTarget) printTarget = false;
			
			//Limit exponential growth
			if (difference < idealMiningTime/growthFactorLimit) difference = idealMiningTime/growthFactorLimit;
			if (difference > idealMiningTime*growthFactorLimit) difference = idealMiningTime*growthFactorLimit;
			
			//Adjust the target by multiplying the previous target by actual time frame/expected time frame
			BigInteger newTarget = ((BigInteger.valueOf(difference)).multiply(new BigInteger(unpackTarget(block.getTarget()),16))).divide(BigInteger.valueOf(idealMiningTime));
			
			//Check if the target goes out of the specified range
			if (newTarget.compareTo(minTarget) == -1) newTarget = minTarget;
			if (newTarget.compareTo(maxTarget) == 1) newTarget = maxTarget;
			
			int result = packTarget(newTarget.toString(16));
			
			eventBus.post(new LogEvent("New Target:		"+stringFormat(unpackTarget(result)),LogEventSource.MINING,Level.INFO));
			//eventBus.post(new LogEvent("Proof Target:	"+stringFormat(unpackTarget(Miner.calculateMiningProofTarget(result))),LogEventSource.MINING,Level.INFO));
			
			return result;
		}
		else{
			//Otherwise use the same target as the most recent block
			
			int target = block.getTarget();
			
			//Initially print the target
			if (printTarget){
				eventBus.post(new LogEvent("Success Target:	"+stringFormat(unpackTarget(target)),LogEventSource.MINING,Level.INFO));
				printTarget = false;
			}
			
			return target;
		}
	}
	
	/**
     * Calculate the target for proof of mining given the mining success target.
     * We multiply the success target by miningProofDifficultyScale to make it easier to meet this target.
     * 
     *  @param successPackedTarget	the packed target for successfully mining a block
     *  @return the 256 proof of mining target based on the mining success target
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
     *  @return 64 digit hex string string on valid input
     */
	public static String stringFormat(String s){
		while (s.length() < 64){
			s = "0"+s;
		}
		return s;
	}
	
}
