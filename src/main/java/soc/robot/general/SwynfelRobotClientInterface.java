package soc.robot.general;
import soc.baseclient.ServerConnectInfo;
import soc.robot.SOCRobotClient;
import soc.robot.general.Format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class SwynfelRobotClientInterface extends SOCRobotClient {

	public final Format format;

	public SwynfelRobotClientInterface(final ServerConnectInfo sci, final String nn, final String pw)
			throws IllegalArgumentException
	{
		super(sci, nn, pw);
		loadProperties();
		format = Format.getFormat(this);
	}
    
    private static final String propertiesFilename = "bot.properties";

    private FileInputStream inputStream;
    
    public Properties properties;

    private void loadProperties() {
    	try {
    		final File inputFile = new File(propertiesFilename);
        	properties = new Properties();
        	inputStream = new FileInputStream(inputFile);
        	
        	if(inputStream != null) {
        		properties.load(inputStream);
        	} else {
        		System.out.println("No bot properties file called '"+propertiesFilename+"' found...");
        	}
    	} catch (Exception e) {
    		System.out.println("Exception: "+e);
    	} finally {
    		try {
    			if(inputStream != null) {
    				inputStream.close();
    			}
			} catch (IOException e) {
	    		System.out.println("Exception: "+e);
			}
    	}
    }

	public boolean getBoolProp(String key, boolean def) {
		String value = properties.getProperty(key);
		return value==null ? def : (value.contains("true") || value.contains("1"));
	}

	public int getIntProp(String key, int def) {
		String value = properties.getProperty(key);
		try{
			return value==null ? def : Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
