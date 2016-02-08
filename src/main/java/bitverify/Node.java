package bitverify;

import java.util.Scanner;

import bitverify.mining.Miner;
import bitverify.network.ConnectionManager;

public class Node {
	private String[] mOptions = {"Start mining", "Add entry", "Search entries", "See statistics", "Exit"};
	private int mMiningOptionNum = 0;
	
	private Scanner mScanner;
	private Miner mMiner;
	
	private ConnectionManager mConnectionManager;
	
	public Node(String[] args) {
		handleArgs(args);
	}
	
	private void handleArgs(String[] args) {
		// If no args, we will just show default prompt to user
		if (args.length == 0) {
			mScanner = new Scanner(System.in);
			setupNetwork();
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
		mMiner = new Miner();
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
		
	}
	
	private void searchEntries() {
		
	}
	
	private void displayStatistics() {
		
	}
	
	private void exitProgram() {
		mMiner.stopMining();
	}
	
	private void setupNetwork() {
		System.out.println("Setting up network...");
		mConnectionManager = new ConnectionManager();
	}

}
