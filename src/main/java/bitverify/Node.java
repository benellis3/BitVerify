package bitverify;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Scanner;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

import bitverify.OttoExample.OttoEvent;
import bitverify.block.Block;
import bitverify.entries.Metadata;
import bitverify.mining.Miner;
import bitverify.mining.Miner.BlockFoundEvent;
import bitverify.network.ConnectionManager;


public class Node {
	private String[] mOptions = {"Start mining", "Add entry", "Search entries", "See statistics", "Exit"};
	private int mMiningOptionNum = 0;
	
	private Scanner mScanner;
	private Miner mMiner;
	
	private ConnectionManager mConnectionManager;
	
	private Bus mEventBus;
	
	public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	
	public Node(String[] args) {
		handleArgs(args);
	}
	
	private void handleArgs(String[] args) {
		// If no args, we will just show default prompt to user
		if (args.length == 0) {
			mScanner = new Scanner(System.in);
			mEventBus = new Bus(ThreadEnforcer.ANY);
			mEventBus.register(this);
			//setupNetwork();
			userCLISetup();
		}
		else {
			//TODO handle individual commands
		}
	}
	
	private void userCLISetup() {
		// Print out the command options for the user
		for (int i = 0; i < mOptions.length; i++) {
			System.out.printf("(%d)%s\n", i+1, mOptions[i]);
		}
		
		// Get the user input and run command if valid
		System.out.println("Enter number to run command:");
		String uInput = mScanner.nextLine();
		boolean shouldContinue = true;
		try {
			int inputNum = Integer.parseInt(uInput);
			String command = mOptions[inputNum - 1];
			shouldContinue = handleUserInput(command);
		} catch (NumberFormatException e) {
			System.out.printf("'%s' is not a valid command. Enter a number instead.\n", uInput);
		} catch (ArrayIndexOutOfBoundsException e2) {
			System.out.printf("'%s' is not a valid number. Enter a number between %d-%d.\n", uInput, 1, mOptions.length);
		}
		
		// Redisplay options to user after command has finished.
		if (shouldContinue)
			userCLISetup();
	}
	
	private boolean handleUserInput(String command) {
		command = command.toLowerCase();
		if (command.equals("start mining"))
			startMiner();
		else if (command.equals("stop mining"))
			stopMiner();
		else if (command.equals("add entry"))
			addEntry();
		else if (command.equals("search entries"))
			searchEntries();
		else if (command.equals("see statistics"))
			displayStatistics();
		else if (command.equals("exit")){
			exitProgram();
			return false;
		}
		return true;
			
	}
	
	private void startMiner() {
		while (mMiner != null) {
			stopMiner();
		}
		try {
			mMiner = new Miner(mEventBus);
		} catch (SQLException e) {
			// TODO Handle this
			e.printStackTrace();
		}
		
		Thread miningThread = new Thread(mMiner);
		miningThread.start();
		mOptions[mMiningOptionNum] = "Stop mining";
	}
	
	private void stopMiner() {
		if (mMiner != null) {
			mMiner.stopMining(); // best way to handle this I believe.
			mMiner = null;
		}
		mOptions[mMiningOptionNum] = "Start mining";
		
	}
	
	private void addEntry() {
		// Get input from user
		//TODO Input validation
		System.out.println("Enter file path:");
		String filePath = mScanner.nextLine();
		String hash = "12903910lasfa";
		
		System.out.println("Enter file download:");
		String fileDownload = mScanner.nextLine();
		
		System.out.println("Enter file name:");
		String fileName = mScanner.nextLine();
		
		System.out.println("Enter file description:");
		String fileDescription = mScanner.nextLine();
		
		System.out.println("Enter file geolocation:");
		String fileGeo = mScanner.nextLine();
		
		System.out.println("Enter tags seperated by commas:");
		String tagString = mScanner.nextLine();
		String [] tags = tagString.split(",");
		for (int i = 0; i < tags.length; i++) {
			tags[i] = tags[i].trim();
		}
		String fileTimeStamp = getCurrentDatetime();
		
		// Construct metadata object for file
		Metadata data = new Metadata(hash, fileDownload, fileName, fileDescription, fileGeo, fileTimeStamp, tags);
		// TODO broadcast to everyone
	}
	
	private void searchEntries() {
		
	}
	
	private void displayStatistics() {
		
	}
	
	private void exitProgram() {
		if (mMiner != null)
			mMiner.stopMining();
	}
	
	private void setupNetwork() {
		System.out.println("Setting up network...");
		try {
			mConnectionManager = new ConnectionManager(32903);
		} catch (IOException e) {
			System.out.println("Error setting up network. Will try again.");
			setupNetwork();
		}
	}
	
   
    @Subscribe
    public void onBlockFoundEvent(BlockFoundEvent e) {
    	Block block = e.getBlock();
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

}
