package bitverify;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Level;

import bitverify.crypto.*;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.gui.GUI;
import bitverify.mining.Miner;
import bitverify.mining.Miner.BlockFoundEvent;
import bitverify.network.ConnectionManager;
import bitverify.network.NewEntryEvent;
import bitverify.persistence.DataStore;
import bitverify.persistence.DatabaseIterator;
import bitverify.persistence.DatabaseStore;
import org.bouncycastle.crypto.InvalidCipherTextException;

public class Node {
	private String[] mOptions = {
			"Start mining",
			"Add entry",
			"List confirmed entries",
			"List unconfirmed entries",
			"List connected peers",
			"List blocks on primary chain",
			"List all blocks",
			"Quick add predef. entry",
			"Print out my public ID",
			"Exit",
			}; // see mapping in handleUserInput
	private boolean isMining = false;
	private static final int mMiningOptionNum = 0;
	
	private Scanner mScanner;
	
	private Miner mMiner;
	private ConnectionManager mConnectionManager;
	private DataStore mDatabase;
	
	private Identity mIdentity;
	
	private Bus mEventBus;
	
	public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	
	public enum StartType {CLI, GUI};
	
	private GUI mGUI;

	public Node(StartType startType) {
		handleType(startType);
	}
	
	public Node(GUI gui) {
		mGUI = gui;
		handleType(StartType.GUI);
	}
	
	private void handleType(StartType startType) {
		if (mEventBus == null) {
			mEventBus = new Bus(ThreadEnforcer.ANY);
			mEventBus.register(this);
		}
		
		switch (startType) {
			case CLI:
				mScanner = new Scanner(System.in);
				setUpModules();
				userCLISetup();
				break;
			default:
				setUpModules();
				
				if (mGUI != null) {
					mGUI.onNodeSetupComplete();
				}
		}
	}
	
	private void setUpModules(){
		setupDatabase();
		setupUser();
		setupNetwork();
		setupMiner();
	}
	
	private void userCLISetup() {
		boolean shouldContinue = true;
		while (shouldContinue) {
			// Print out the command options for the user
			System.out.println("----------");
			System.out.println("CLI MENU");
			for (int i = 0; i < mOptions.length; i++) {
				System.out.printf("(%d)%s\n", i, mOptions[i]);
			}
			System.out.println("----------");

			// Get the user input and run command if valid
			System.out.println("Enter number to run command:");
			String uInput = mScanner.nextLine();

			try {
				int inputNum = Integer.parseInt(uInput);
				shouldContinue = handleUserInput(inputNum);
			} catch (NumberFormatException e) {
				System.out.printf("'%s' is not a valid command. Enter a number instead.\n", uInput);
			} catch (ArrayIndexOutOfBoundsException e2) {
				System.out.printf("'%s' is not a valid number. Enter a number between %d-%d.\n", uInput, 1, mOptions.length);
			}

		}
	}
	
	private boolean handleUserInput(int commandNum) {
		switch (commandNum){
			case 0:
				if (isMining){
					stopMiner();
				} else {
					startMiner();
				}
				break;
			case 1:
				addEntry();
				break;
			case 2:
				listConfirmedEntries();
				break;
			case 3:
				listUnconfirmedEntries();
				break;
			case 4:
				listConnectedPeers();
				break;
			case 5:
				listPrimaryBlocks();
				break;
			case 6:
				listAllBlocks();
				break;
			case 7:
				quickAddPredefinedEntry();
				break;
			case 8:
				printPublicID();
				break;
			case 9:
				exitProgram();
				return false;
		}
		return true;
	}
	
	public void startMiner() {
		Thread miningThread = new Thread(mMiner);
		miningThread.start();
		mOptions[mMiningOptionNum] = "Stop mining";
		isMining = true;
	}
	
	public void stopMiner() {
		if (mMiner != null) {
			mMiner.stopMining(); // best way to handle this I believe.
			//mMiner = null;
		}
		mOptions[mMiningOptionNum] = "Start mining";
		isMining = false;
	}
	
	private void addEntry() {
		// Get input from user
		//TODO Input validation
		FileInputStream inputStream = null;
		byte[] hash = null;
		while (hash == null) {
			System.out.println("Enter file path:");
			String filePath = mScanner.nextLine();
			File inputFile = new File(filePath);
			
			try {
				inputStream = new FileInputStream(inputFile);
				hash = Hash.hashStream(inputStream);
			} catch (IOException e1) {
				System.out.println(String.format("'%s' is not a valid file", filePath));
			}
		}
		
		System.out.println("Enter file name:");
		String fileName = mScanner.nextLine();
		
		System.out.println("Enter file download:");
		String fileDownload = mScanner.nextLine();
		
		System.out.println("Enter file description:");
		String fileDescription = mScanner.nextLine();
		
		System.out.println("Enter received id (leave blank if none):");
		String receiverID = mScanner.nextLine();
		
		System.out.println("Enter file geolocation:");
		String fileGeo = mScanner.nextLine();
				
		try {
			addEntry(hash, fileDownload, fileName, receiverID, fileDescription, fileGeo);
		} catch (KeyDecodingException | IOException | SQLException e) {
			System.out.println("Error generating entry. Try again...");
			return;
		} 
	}
	
	private void quickAddPredefinedEntry() {
		byte[] hash = Base64.getDecoder().decode("LwwOujrMsbB26le3uZ3aa/XN025Pp+Xd/jdFJVTSE3M=");
		String fileName = "cl-spring-370.jpg";
		String fileDownload = "https://www.cl.cam.ac.uk/images/cl-spring-370.jpg";
		String fileDescription = "pic of Cambridge Computer Lab";
		String receiverID = "";
		String fileGeo = "Cambridge, UK";
		
		try {
			addEntry(hash, fileDownload, fileName, receiverID, fileDescription, fileGeo);
		} catch (KeyDecodingException | IOException | SQLException e) {
			System.out.println("Oops. Error generating the predefined entry...");
			return;
		} 
	}
	
	public Identity getCurrentIdentity() {
		return mIdentity;
	}
	
	@Deprecated
	public void addEntry(byte [] hash, String fileDownload, String fileName, 
			String receiverID, String fileDescription, String fileGeo, 
			String tagString) throws KeyDecodingException, IOException, SQLException {
		addEntry(hash, fileDownload, fileName, receiverID, fileDescription, fileGeo);
	}
	
	public void addEntry(byte [] hash, String fileDownload, String fileName, 
			String receiverID, String fileDescription, String fileGeo 
			) throws KeyDecodingException, IOException, SQLException {
		// Construct entry object 
		Entry entry;
		
		// ReceiverID is optional 
		if (receiverID.length() > 0) {
			byte[] processedReceiverID;
			try {
				processedReceiverID = Base64.getDecoder().decode(receiverID);
			} catch (IllegalArgumentException e){
				throw new KeyDecodingException();
			}
			entry = new Entry(mIdentity.getKeyPair(), processedReceiverID, hash, fileDownload, fileName, 
					fileDescription, fileGeo, System.currentTimeMillis());
		} else {
			entry = new Entry(mIdentity.getKeyPair(), hash, fileDownload, fileName, 
					fileDescription, fileGeo, System.currentTimeMillis());
		}
		
		// Notify the relevant authorities of this important incident
		NewEntryEvent event = new NewEntryEvent(entry);
		mDatabase.insertEntry(entry);
		mEventBus.post(event);
		mConnectionManager.broadcastEntry(entry);
	}
	
	private void listConfirmedEntries() {
		try (DatabaseIterator<Entry> di = mDatabase.getConfirmedEntries()) {
			int entryCount = 0; //this is needed, as the number of entries could have changed since the prev call
			System.out.println("######################################");
			System.out.println("Confirmed entries:");
		    while (di.moveNext()) {
		    	entryCount++;
		        Entry entry = di.current();
		        System.out.printf("entryID: %s, UploaderID: %s\n",
						entry.getEntryID().toString(), Base64.getEncoder().encodeToString(entry.getUploaderID()) );
		    }
		    System.out.println("There are "+entryCount+" confirmed entries.");
		    System.out.println("######################################");
		} catch (SQLException e) {
		    e.printStackTrace();
		}
	}
	
	public long getBlockCount() {
		if (mDatabase != null) {
			try {
				return mDatabase.getBlocksCount();
			} catch (SQLException e) {
				return 0;
			}
		} else {
			return 0;
		}
	}
	
	public long getActiveBlockCount() {
		if (mDatabase != null) {
			try {
				return mDatabase.getActiveBlocksCount();
			} catch (SQLException e) {
				return 0;
			}
		} else {
			return 0;
		}
	}
	
	public long getEntryCount() {
		if (mDatabase != null) {
			try {
				return mDatabase.getEntriesCount();
			} catch (SQLException e) {
				return 0;
			}
		} else {
			return 0;
		}
	}
	
	private void printPublicID() {
		System.out.println("######################################");
		System.out.println("Your public identity:");
		System.out.println( Base64.getEncoder().encodeToString(mIdentity.getPublicKey()) );
		System.out.println("######################################");
	}
	
	private void searchEntries() {
		System.out.println("Enter search query");
		String searchQuery = mScanner.nextLine();
		
		// Specify how many entries we want to show to user
		int entriesAtOnce = 10;
		if (mDatabase != null) {
			// We use an iterator to avoid loading entire database in memory
			try (DatabaseIterator<Entry> di = mDatabase.searchEntries(searchQuery)) {
				outerLoop:
				while (true) {
					for (int i = 0; i < entriesAtOnce; i++) {
						if (di.moveNext())
							System.out.println(di.current().toString());
						else
							System.out.println("END OF SEARCH");
					}
					while (true) {
						System.out.println("Type 'n' for next page or 'exit' to exit search");
						String userDecision = mScanner.nextLine();
						if (userDecision.equalsIgnoreCase("n")) {
							break;
						} else if (userDecision.equalsIgnoreCase("exit")) {
							di.close();
							break outerLoop;
						} else {
							System.out.println(String.format("'%s' is not a valid command", userDecision));
						}
					}
			    }
			} catch (SQLException ex) {
				System.out.println("An issue came up with the database. Try to search again.");
			}
		} else {
			System.out.println("An issue came up with the database. Try to search again.");
		}
	}
	

	public DatabaseIterator<Entry> searchEntries(String searchQuery) throws SQLException {
		if (mDatabase != null) 
			return mDatabase.searchEntries(searchQuery);
		return null;
	}
	
	private void listUnconfirmedEntries() {
		try {
			List<Entry> entries = mDatabase.getUnconfirmedEntries();
			System.out.println("######################################");
			System.out.println("Unconfirmed entries:");
			for (int i=0; i<entries.size(); i++){
		        System.out.printf("entryID: %s, UploaderID: %s\n",
		        		entries.get(i).getEntryID().toString(),
		        		Base64.getEncoder().encodeToString(entries.get(i).getUploaderID()) );
		    }
			System.out.println("There are "+entries.size()+" unconfirmed entries.");
			System.out.println("######################################");
		} catch (SQLException e) {
		    e.printStackTrace();
		}
	}
	
	private void listConnectedPeers() {
		System.out.println("######################################");
		mConnectionManager.printPeers();
		System.out.println("######################################");
	}
	
	private void listPrimaryBlocks() {
		try {
			int numPrBlocks = (int)mDatabase.getActiveBlocksCount();
			List<Block> blocks = mDatabase.getNMostRecentBlocks(numPrBlocks);
			System.out.println("######################################");
			System.out.println("Blocks on the primary chain:");
			for (int i=0; i<blocks.size(); i++){
				System.out.printf("height: %d, blockID: %s, entriesHash: %s, nEntries: %d\n",
						blocks.get(i).getHeight(),
						Base64.getEncoder().encodeToString(blocks.get(i).getBlockID()),
						Base64.getEncoder().encodeToString(blocks.get(i).getEntriesHash()),
						blocks.get(i).getEntriesList().size() );
			}
			System.out.println("There are "+numPrBlocks+" blocks on the primary chain.");
			System.out.println("######################################");
		} catch (SQLException e) {
			e.printStackTrace();
			return;
		}
	}
	
	private void listAllBlocks() {
		try (DatabaseIterator<Block> di = mDatabase.getAllBlocks()) {
			int blockCount = 0; //this is needed, as the number of blocks could have changed since the prev call
			System.out.println("######################################");
			System.out.println("All blocks:");
		    while (di.moveNext()) {
		    	blockCount++;
		        Block block = di.current();
		        System.out.printf("i: %d, height: %d, blockID: %s, entriesHash: %s, nEntries: %d\n",
		        		blockCount,
		        		block.getHeight(),
		        		Base64.getEncoder().encodeToString(block.getBlockID()),
						Base64.getEncoder().encodeToString(block.getEntriesHash()),
						block.getEntriesList().size() );
		    }
		    System.out.println("There are "+blockCount+" blocks total.");
		    System.out.println("######################################");
		} catch (SQLException e) {
			e.printStackTrace();
			return;
		}
	}
	
	public void exitProgram() {
		// Need to stop a few resources before exiting
		if (mMiner != null)
			mMiner.stopMining();
		if (mScanner != null)
			mScanner.close();
		
	}
	
	private void setupUser() {
		informUserOfProgress("Setting up user...");
		
		try {
			List<Identity> identities = mDatabase.getIdentities();
			if (identities.size() == 0) {
				System.out.println("Generating new key identity...");
				AsymmetricCipherKeyPair keyPair = Asymmetric.generateNewKeyPair();
				mIdentity = new Identity("default", keyPair);
				mDatabase.insertIdentity(mIdentity);
			}
			else {
				mIdentity = identities.get(0);
			}
		} catch (SQLException e) {
			// TODO deal with this
			e.printStackTrace();
		}
	}
	
	private void setupNetwork() {
		informUserOfProgress("Setting up network...");
		mConnectionManager = new ConnectionManager(32903, mDatabase, mEventBus);
	}
	
	private void setupMiner(){
		informUserOfProgress("Setting up miner...");
		try {
			mMiner = new Miner(mEventBus, mDatabase);
		} catch (SQLException e) {
			// TODO Handle this
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Handle this as well
			e.printStackTrace();
		}
	}
	
	private void setupDatabase() {
		informUserOfProgress("Setting up database...");
		try {
			mDatabase = new DatabaseStore("jdbc:h2:file:bitverify");
		} catch (SQLException e) {
			System.out.println("Error setting up database...");
			e.printStackTrace();
			exitProgram();
		}
	}
	
	public int getNumPeers() {
	    return mConnectionManager.peers().size();
	}
	
	public List<String> getPeerListAsStrings() {
		List<String> peers = new ArrayList<String>();
		if (mConnectionManager != null) {
			for (InetSocketAddress address : mConnectionManager.getPeerSet()) {
				peers.add(address.toString());
			}
		}
		return peers;
	}
	
	public DatabaseIterator<Block> getBlockList() {
		
		if (mDatabase != null){
			try {
				return mDatabase.getAllBlocks();
			} catch (SQLException e) {
				return null;
			}
		} else {
			return null;
		}
	}
	
   
    @Subscribe
    public void onBlockFoundEvent(BlockFoundEvent e) {
    	Block block = e.getBlock();
    	System.out.println(block);
    	//send to network
		mConnectionManager.broadcastBlock(e.getBlock());
    }

	@Subscribe
	public void onNewEntry(NewEntryEvent e) throws SQLException, KeyDecodingException, IOException, NotMatchingKeyException, InvalidCipherTextException {
		List<Identity> myIdentities = mDatabase.getIdentities();
		Entry entry = e.getNewEntry();
		for (Identity i : myIdentities) {
			if (entry.isThisEntryJustForMe(i)) {
				entry.decrypt(i.getKeyPair());
				mDatabase.updateEntry(entry);
				break;
			}
		}
	}

    @Subscribe
    public void onLogEvent(LogEvent o) {
		if (o.getLevel().intValue() >= Level.FINER.intValue()) {
			// we need to log to the console for debugging purposes.
			if (mGUI != null) {
				mGUI.addLogEvent(o);
			} else {
				System.out.println(o.getMessage());
			}
		}
    }

	@Subscribe
	public void onExceptionLogEvent(ExceptionLogEvent e) {
		if (e.getLevel().intValue() >= Level.WARNING.intValue())
			e.getCause().printStackTrace();
	}
    
    private String getCurrentDatetime() {
    	Calendar cal = Calendar.getInstance();
    	SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
    	return sdf.format(cal.getTime());
    }
    
    private void informUserOfProgress(String progress) {
    	if (mGUI == null) {
    		System.out.println(progress);
    	} else {
    		mGUI.changeLoadingText(progress);
    	}
    }
    
    public List<Entry> getEntrySearchByHash(byte [] hash) {
    	try {
			return mDatabase.getEntries(hash);
		} catch (SQLException e) {
			return new ArrayList<Entry>();
		}
    }
    

    public Bus getEventBus() {
    	return mEventBus;
    }

}
