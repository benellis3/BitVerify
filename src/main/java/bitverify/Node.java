package bitverify;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.util.encoders.Hex;

import bitverify.crypto.Asymmetric;
import bitverify.crypto.Hash;
import bitverify.crypto.Identity;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;
import com.sun.org.apache.xml.internal.security.utils.Base64;

import bitverify.block.Block;
import bitverify.crypto.KeyDecodingException;
import bitverify.entries.Entry;
import bitverify.gui.GUI;
import bitverify.mining.Miner;
import bitverify.mining.Miner.BlockFoundEvent;
import bitverify.network.ConnectionManager;
import bitverify.network.NewEntryEvent;
import bitverify.persistence.DataStore;
import bitverify.persistence.DatabaseIterator;
import bitverify.persistence.DatabaseStore;

public class Node {
	private String[] mOptions = {
			"Start mining",
			"Add entry",
			"List confirmed entries",
			"List unconfirmed entries",
			"List connected peers",
			"List blocks on primary chain",
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
		mEventBus = new Bus(ThreadEnforcer.ANY);
		mEventBus.register(this);
		
		switch (startType) {
			case CLI:
				mScanner = new Scanner(System.in);
				setupDatabase();
				setupUser();
				setupNetwork();
				userCLISetup();
				break;
			default:
				setupDatabase();
				setupUser();
				setupNetwork();
				if (mGUI != null) {
					mGUI.onNodeSetupComplete();
				}
		}
	}
	
	
	private void userCLISetup() {
		// Print out the command options for the user
		System.out.println("----------");
		System.out.println("CLI MENU");
		for (int i = 0; i < mOptions.length; i++) {
			System.out.printf("(%d)%s\n", i, mOptions[i]);
		}
		
		// Get the user input and run command if valid
		System.out.println("Enter number to run command:");
		String uInput = mScanner.nextLine();
		boolean shouldContinue = true;
		try {
			int inputNum = Integer.parseInt(uInput);
			shouldContinue = handleUserInput(inputNum);
		} catch (NumberFormatException e) {
			System.out.printf("'%s' is not a valid command. Enter a number instead.\n", uInput);
		} catch (ArrayIndexOutOfBoundsException e2) {
			System.out.printf("'%s' is not a valid number. Enter a number between %d-%d.\n", uInput, 1, mOptions.length);
		}
		
		// Redisplay options to user after command has finished.
		if (shouldContinue)
			userCLISetup();
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
				listBlocks();
				break;
			case 6:
				exitProgram();
				return false;
		}
		return true;
	}
	
	public void startMiner() {
		while (mMiner != null) {
			stopMiner();
		}
		try {
			mMiner = new Miner(mEventBus, mDatabase);
		} catch (SQLException e) {
			// TODO Handle this
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Handle this as well
			e.printStackTrace();
		}
		
		Thread miningThread = new Thread(mMiner);
		miningThread.start();
		mOptions[mMiningOptionNum] = "Stop mining";
		isMining = true;
	}
	
	public void stopMiner() {
		if (mMiner != null) {
			mMiner.stopMining(); // best way to handle this I believe.
			mMiner = null;
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
		
		System.out.println("Enter file download:");
		String fileDownload = mScanner.nextLine();
		
		System.out.println("Enter file name:");
		String fileName = mScanner.nextLine();
		
		System.out.println("Enter file description:");
		String fileDescription = mScanner.nextLine();
		
		System.out.println("Enter recieved id (leave blank if none):");
		String recieverID = mScanner.nextLine();
		
		System.out.println("Enter file geolocation:");
		String fileGeo = mScanner.nextLine();
		
		System.out.println("Enter tags seperated by commas:");
		String tagString = mScanner.nextLine();
		String [] tags = tagString.split(",");
		for (int i = 0; i < tags.length; i++) {
			tags[i] = tags[i].trim();
		}
		
		// Construct metadata and entry objects for file
		Entry entry;
		try {
			entry = new Entry(mIdentity.getKeyPair(), hash, fileDownload, fileName, fileDescription, fileGeo, System.currentTimeMillis(), tags);
			// Notify the relevant authorities of this important incident
			NewEntryEvent event = new NewEntryEvent(entry);
			mEventBus.post(event);
			mDatabase.insertEntry(entry);
			mConnectionManager.broadcastEntry(entry);
			
		} catch (KeyDecodingException | IOException | SQLException e) {
			System.out.println("Error generating entry. Try again...");
			return;
		} 
	}
	
	private void listConfirmedEntries() {
		try (DatabaseIterator<Entry> di = mDatabase.getConfirmedEntries()) {
			int entryCount = 0;
			System.out.println("######################################");
			System.out.println("Confirmed entries:");
		    while (di.moveNext()) {
		    	entryCount++;
		        Entry entry = di.current();
		        System.out.printf("entryID: %s, UploaderID: %s\n",
						entry.getEntryID().toString(), Base64.encode(entry.getUploaderID()) );
		    }
		    System.out.println("There are "+entryCount+" confirmed entries.");
		    System.out.println("######################################");
		} catch (SQLException e) {
		    e.printStackTrace();
		}
	}
	
	private void listUnconfirmedEntries() {
		try {
			List<Entry> entries = mDatabase.getUnconfirmedEntries();
			System.out.println("######################################");
			System.out.println("Unconfirmed entries:");
			for (int i=0; i<entries.size(); i++){
		        System.out.printf("entryID: %s, UploaderID: %s\n",
		        		entries.get(i).getEntryID().toString(),
		        		Base64.encode(entries.get(i).getUploaderID()) );
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
	
	private void listBlocks() {
		try {
			List<Block> blocks = mDatabase.getNMostRecentBlocks(2000); //TODO cheating here for now...
			System.out.println("######################################");
			System.out.println("Blocks on the primary chain:");
			for (int i=0; i<blocks.size(); i++){
				System.out.printf("height: %d, blockID: %s, entriesHash: %s\n",
						blocks.get(i).getHeight(), Base64.encode(blocks.get(i).getBlockID()),
						Base64.encode(blocks.get(i).getEntriesHash()) );
			}
			System.out.println("There are "+blocks.size()+" blocks on the primary chain.");
			System.out.println("######################################");
		} catch (SQLException e) {
			e.printStackTrace();
			return;
		}
	}
	
	private void exitProgram() {
		if (mMiner != null)
			mMiner.stopMining();
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
		try {
			mConnectionManager = new ConnectionManager(32903, mDatabase, mEventBus);
		} catch (IOException e) {
			System.out.println("Error setting up network. Will try again.");
			setupNetwork();
		}
	}
	
	
	private void setupDatabase() {
		informUserOfProgress("Setting up database...");
		// create a connection source to an in-memory database
		try {
			mDatabase = new DatabaseStore("jdbc:h2:mem:bitverify");
		} catch (SQLException e) {
			System.out.println("Error setting up database...");
			exitProgram();
		}
	}
	
   
    @Subscribe
    public void onBlockFoundEvent(BlockFoundEvent e) {
    	Block block = e.getBlock();
    	System.out.println(block);
    	//send to database
    	//send to network
    }

    @Subscribe
    public void onAnyEvent(Object o) {
        // do stuff
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

}
