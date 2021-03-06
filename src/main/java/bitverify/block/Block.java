package bitverify.block;

import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.entries.*;
import bitverify.mining.Miner;
import bitverify.crypto.Hash;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Level;

import bitverify.network.BlockID;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.squareup.otto.Bus;


/**
 * This class is responsible for creating a block in our chain. Given another block, it can create its own hashes based on
 * these prior dependencies. There are also static methods to check the chain and create a block from a byte array.
 *
 * @author Dominiquo Santistevan
 */
public class Block {
	
	public static final int TIME_INVAR_1_MEDIAN_OF_THIS_MANY_PREV_BLOCKS = 11;
	private static final long TIME_INVAR_2_TIME_BUFFER_INTO_THE_FUTURE = 10 * 60 * 1000; //we allow blocks to have a timestamp couple minutes into the future
	public static final long GENESIS_TIMESTAMP = 1455745984018l;
	
    // Block header
    @DatabaseField(dataType = DataType.BYTE_ARRAY, columnDefinition = "VARBINARY(32)")
    private byte[] prevBlockHash;
    @DatabaseField(dataType = DataType.BYTE_ARRAY, columnDefinition = "VARBINARY(32)")
    private byte[] entriesHash;
    @DatabaseField
    private long timeStamp;
    @DatabaseField
    private int bitsTarget;
    @DatabaseField
    private int nonce = 0;

    // for benefit of database
    @DatabaseField(dataType = DataType.BYTE_ARRAY, columnDefinition = "VARBINARY(32)", uniqueIndex = true)
    private byte[] blockID;
    @DatabaseField
    private long height;
    @DatabaseField
    private boolean active;

    private List<Entry> entries;
    private boolean verifiedEntries;

    /**
     * The empty package-visible constructor is required for the database.
     */
    Block() {
    }

    /**
     * @param prevBlock   A block object that is the last known mined block in the chain to which this block will be attached.
     * @param target      The integer value that mining calculates should be the number of zeros required to 'mine' this block.
     * @param nonce       The changing value that is the free parameter input value to our hash that must reach the target zeros.
     * @param entriesList List of Entry types that this block be the container for.
     */
    public Block(Block prevBlock, int target, int nonce, List<Entry> entriesList) {
        this(prevBlock,System.currentTimeMillis(),target,nonce,entriesList);
    }

    /**
     * 
     *  Special constructor so that mining can assign the timestamp manually for conccurrent mining reasons.
     *
     */
    public Block(Block prevBlock,long timestamp,int target,int nonce, List<Entry> entriesList){
        this.prevBlockHash = prevBlock.hashHeader();
        this.bitsTarget = target;
        this.timeStamp = timestamp;
        this.nonce = nonce;

        Collections.sort(entriesList);
        this.entries = entriesList;
        this.entriesHash = hashEntries();
        this.verifiedEntries = true;

        this.blockID = this.hashHeader();
    }
    

    /**
     * This constructor will only be used by the deserialize method. That is why the timestamp is manually entered as well as
     * the hashes for the block and entries. When using this method, the block is not verified until it is given a list of
     * entries that agrees with the initial hash that was deserialized.
     *
     * @param prevHash    byte array storing the hash of the previous block to the block being deserialized.
     * @param entriesHash byte array storing the hash of the entries list corresponding to this block.
     * @param timeStamp   time since epoch that was originally created by the miner.
     * @param bitsTarget  The integer value that mining calculates should be the number of zeros required to 'mine' this block.
     * @param nonce       The changing value that is the free parameter input value to our hash that must reach the target zeros.
     */
    private Block(byte[] prevHash, byte[] entriesHash, long timeStamp, int bitsTarget, int nonce) {
        this.prevBlockHash = prevHash;
        this.bitsTarget = bitsTarget;
        this.timeStamp = timeStamp;
        this.nonce = nonce;
        
        this.entriesHash = entriesHash;
        this.verifiedEntries = false;
        
        this.blockID = this.hashHeader();
    }

    /**
     * @return Static block that has no previous block. This will be the genesis block.
     */
    public static Block getGenesisBlock() {
        String mythology = "ARNOLD";
        String itsComing = "You hear that, Mr. Anderson. That is the sound inevitability.";
        byte[] prevHash = Hash.hashString(mythology);
        byte[] entryHash = Hash.hashString(itsComing);
        long timeStamp = GENESIS_TIMESTAMP;
        int target = Miner.packTarget("00000fa1e3800000000000000000000000000000000000000000000000000000");
        int nonce = 10136621;
        Block resultBlock = new Block(prevHash,entryHash,timeStamp,target,nonce);
        resultBlock.setEntriesList(Collections.emptyList());
        resultBlock.verifiedEntries = true;
        return resultBlock;
    }

    /**
     * This method is used after a block is received and deserialized but the corresponding entries have yet to be recieved.
     * When a node receives, what is believed to be the correct entries, this method will set their values and also check
     * to make sure they haven't been tampered with throughout the sending process.
     *
     * @param entryList List of entries that should correspond to the current block
     * @return a boolean to indicate if the entered entries are the same as the ones indicated in the hash of the deserialized block.
     */
    public boolean setEntriesList(List<Entry> entryList) {
        Collections.sort(entryList);
        entries = entryList;
        byte[] newEntriesHash = hashEntries();
        if (Arrays.equals(this.entriesHash, newEntriesHash)) {
            this.verifiedEntries = true;
            return true;
        }
        return false;
    }

    public byte[] hashEntries() {
        byte[] entriesSerial = serializeEntries();
        byte[] firstHash = Hash.hashBytes(entriesSerial);
        byte[] secondHash = Hash.hashBytes(firstHash);
        return secondHash;
    }

    /**
     * adds one to the nonce value for the miner.
     */
    public void incrementNonce() {
    	//If we have mined all nonce values without a successful mine, update the timestamp and try again
        if (nonce == -1) this.timeStamp = System.currentTimeMillis();
        nonce += 1;
    }

    /**
     * @param blockList List of blocks that represents a subchain in the total blockchain therefore order should be preserved
     * @return boolean to indicate whether the given subchain is valid or not
     * @throws Exception Method is expecting a list of Entries to verify, so the list should have size > 0.
     */
    public static boolean verifyChain(List<Block> blockList, Bus bus){
        if (bus!=null) bus.post(new LogEvent("verifying chain of headers", LogEventSource.BLOCK, Level.FINER));
        int FIRST = 0;
        int listLen = blockList.size();
        if (blockList.isEmpty()) {
            throw new IllegalArgumentException();
        } else if (listLen == 1) {
        	if (bus!=null) bus.post(new LogEvent("chain only had one block, verifying hash meets difficulty", LogEventSource.BLOCK, Level.FINER));
            Block onlyBlock = blockList.get(FIRST);
            return Miner.blockHashMeetDifficulty(onlyBlock);
        } else {
        	if (bus!=null) bus.post(new LogEvent("chain has " + blockList.size() + " blocks", LogEventSource.BLOCK, Level.FINER));
            Block prevBlock = blockList.get(0);
            Block currentBlock;
            long currentBlockTime;
            byte[] prevBlockHash = prevBlock.hashHeader();
            byte[] currentBlockPrevHash;
            boolean matchingHash;
            boolean validNonce;
            
            boolean timeInvar0;
            
            long[] prevBlockTimes = new long[TIME_INVAR_1_MEDIAN_OF_THIS_MANY_PREV_BLOCKS];
            long medianTime = 0;
            boolean timeInvar1;
            
            boolean timeInvar2;
            long currentSysTime = System.currentTimeMillis();

            for (int i = 1; i < listLen; i++) {
                currentBlock = blockList.get(i);
                currentBlockTime = currentBlock.getTimeStamp();
                currentBlockPrevHash = currentBlock.getPrevBlockHash();
                matchingHash = Arrays.equals(prevBlockHash, currentBlockPrevHash);
                validNonce = Miner.blockHashMeetDifficulty(currentBlock);
                
                //time invariant 0: not older than genesis block
                timeInvar0 = (currentBlockTime >= GENESIS_TIMESTAMP);
                
                //time invariant 1: currentBlockTime > median of prev TIME_INVAR_1_MEDIAN_OF_THIS_MANY_PREV_BLOCKS block times
                if (i>=11){ //only check if there are enough previous blocks
                	for (int jBlocksBack=1; jBlocksBack<=TIME_INVAR_1_MEDIAN_OF_THIS_MANY_PREV_BLOCKS; jBlocksBack++){
                		prevBlockTimes[jBlocksBack-1] = blockList.get(i-jBlocksBack).getTimeStamp();
                    }
                	Arrays.sort(prevBlockTimes);
                	medianTime = prevBlockTimes[TIME_INVAR_1_MEDIAN_OF_THIS_MANY_PREV_BLOCKS/2];
                    timeInvar1 = (currentBlockTime > medianTime);
                } else {
                	//invariant automatically holds if not enough blocks yet
                	timeInvar1 = true;
                }
                
                //time invariant 2: future times are not allowed
                timeInvar2 = (currentBlockTime < currentSysTime + TIME_INVAR_2_TIME_BUFFER_INTO_THE_FUTURE);
                if (bus!=null) {
	                if (!matchingHash)
	                    bus.post(new LogEvent("chain validation failed: child-parent hashes didn't match", LogEventSource.BLOCK, Level.FINER));
	                if (!validNonce)
	                    bus.post(new LogEvent("chain validation failed: block hash did not meet its difficulty", LogEventSource.BLOCK, Level.FINER));
	                if (!timeInvar0) {
	                    bus.post(new LogEvent("chain validation failed: time invariant 0 test failed", LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("block timestamp was " + new Date(currentBlockTime), LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("genesis block timestamp is " + new Date(GENESIS_TIMESTAMP), LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("time invariant should be positive: " + (currentBlockTime - medianTime), LogEventSource.BLOCK, Level.FINER));
	                }
	                if (!timeInvar1) {
	                    bus.post(new LogEvent("chain validation failed: time invariant 1 test failed", LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("median of prev "+TIME_INVAR_1_MEDIAN_OF_THIS_MANY_PREV_BLOCKS+" timestamps was "
	                    		+ new Date(medianTime), LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("next block timestamp was " + new Date(currentBlockTime), LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("time invariant should be positive: " + (currentBlockTime - medianTime), LogEventSource.BLOCK, Level.FINER));
	                }
	                if (!timeInvar2) {
	                    bus.post(new LogEvent("chain validation failed: time invariant 2 test failed", LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("block timestamp was " + new Date(currentBlockTime), LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("current system time plus allowance buffer was" +
	                    		new Date(currentSysTime + TIME_INVAR_2_TIME_BUFFER_INTO_THE_FUTURE), LogEventSource.BLOCK, Level.FINER));
	                    bus.post(new LogEvent("time invariant should be positive: " + (currentBlockTime - medianTime), LogEventSource.BLOCK, Level.FINER));
	                }
                }
                if (!matchingHash || !validNonce || !timeInvar0 || !timeInvar1 || !timeInvar2) {
                    return false;
                }

                prevBlock = currentBlock;
                prevBlockHash = prevBlock.hashHeader();
            }
        }
        return true;
    }

    /**
     * Calucaltes the hash of this block's header. Also updates the blockID to match the calculated hash as they should always be consistent.
     * @return the header hash as a byte array
     */
    public byte[] hashHeader() {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        try {
            serializeHeader(header);
        } catch (IOException e) {
            // we are in control of creating the stream here so this will never happen, but just in case...
            e.printStackTrace();
        }
        byte[] firstHash = Hash.hashBytes(header.toByteArray());
        byte[] secondHash = Hash.hashBytes(firstHash);
        blockID = secondHash;
        return blockID;
    }

    /**
     * @param data the byte array that should contain the data for a valid block.
     * @return the unverified block corresponding to the given byte array
     * @throws IOException if there is some data in the byte array that does not fit the format of the block serialization
     */
    public static Block deserialize(byte[] data) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        return Block.deserialize(in);
    }


    private static Block deserialize(InputStream in) throws IOException {
        // DataInputStream allows us to read in primitives in binary form.
        try (DataInputStream d = new DataInputStream(in)) {
            // establish a pair of 32-byte buffers to read in our hashes as byte arrays
            byte[] previousBlockHash = new byte[Hash.HASH_LENGTH];
            d.readFully(previousBlockHash);
            byte[] entriesHash = new byte[Hash.HASH_LENGTH];
            d.readFully(entriesHash);
            // instantiate while reading in the remaining fields - timestamp, bitsTarget , nonce
            return new Block(previousBlockHash, entriesHash, d.readLong(), d.readInt(), d.readInt());
        }
    }

    private byte[] serializeEntries() {
        if (entries == null)
            return null;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(out)) {
            for (Entry e : entries) {
                d.write(e.serialize());
            }
        } catch (IOException e) {
            // this will never happen with a byte array stream
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    /**
     * @return byte array to be sent over the network and later unpacked
     * @throws IOException
     */
    public byte[] serializeHeader() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            serializeHeader(out);
        } catch (IOException e) {
            // this will never happen with a byte array stream
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private void serializeHeader(OutputStream out) throws IOException {
        try (DataOutputStream d = new DataOutputStream(out)) {
            d.write(prevBlockHash);
            d.write(entriesHash);
            d.writeLong(timeStamp);
            d.writeInt(bitsTarget);
            d.writeInt(nonce);
        }
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }
    
    @Override
    public boolean equals(Object thatObject){
        if (!(thatObject instanceof Block)) return false;
        Block thatBlock = (Block) thatObject;
        return Arrays.equals(this.hashHeader(), thatBlock.hashHeader());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(blockID);
    }
    
    
//  GETTER METHODS

    public byte[] getBlockID() {
        return blockID;
    }

    public byte[] getPrevBlockHash() {
        return prevBlockHash;
    }

    public byte[] getEntriesHash() {
        return entriesHash;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public long getHeight() {
        return height;
    }

    public int getTarget() {
        return bitsTarget;
    }

    public int getNonce() {
        return nonce;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Gets a read-only list containing this block's entries.
     * May be null if the entries have not yet been set.
     */
    public List<Entry> getEntriesList() {
        return entries == null ? null : Collections.unmodifiableList(entries);
    }

    /**
     * Determines whether the block's entries have been set yet. Otherwise it only contains the block header information.
     */
    public boolean areEntriesSet() {
        return entries != null;
    }

    public boolean isVerified(){
        return this.verifiedEntries;
    }

}
