package bitverify;

import bitverify.gui.GUI;

public class ArgumentHandler {
	
	public static void HandleArgs(String [] args) {
		if (args.length == 0) {
			Node n = new Node(Node.StartType.CLI);
		} else if (args[0].equalsIgnoreCase("--gui")) {
			GUI.StartGUI();
		}
	
	}

}
