package bitverify;

import com.j256.ormlite.logger.LocalLog;

public class Main {
    public static void main(String[] args) {
    	System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
    	ArgumentHandler.HandleArgs(args);
    }
}
