package bitverify;

import bitverify.gui.GUI;

public class ArgumentHandler {
	
	public static void HandleArgs(String [] args) {
		if (args.length == 0) {
			GUI.StartGUI();
		} else if (args[0].equalsIgnoreCase("--gui")) {
			GUI.StartGUI();
		} else if (args[0].equalsIgnoreCase("--cli")) {
			Node n = new Node(Node.StartType.CLI);
		}
	
	}

}
