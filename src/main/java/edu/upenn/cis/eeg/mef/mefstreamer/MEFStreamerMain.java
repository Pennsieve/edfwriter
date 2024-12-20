package edu.upenn.cis.eeg.mef.mefstreamer;

//import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.HashMap;


/**
 * Main class to run the MEFStreamer application.
 */
public class MEFStreamerMain {
	
	static String newEdfFilePath ;
	static HashMap<String,Object> arguments; 
	static EDFBuilder mefBuilder;
	
    public static void main(String[] args) {
        // Check for file path argument
        if (args.length < 1) {
            System.out.println("Usage: java <SubjPathdir>");
            return;
        }
        
        String directoryPath = args[0];
        File directory = new File(directoryPath);
        String subjectid = directory.getName(); // Subject ID corresponds to the name of the directory mef files are within
        // count number of mef files 
        int numsignals;
        String startdate = null;
        String starttime = null;


        // Check if the provided path is a directory
        if (!directory.isDirectory()) {
            System.out.println("The provided path is not a valid directory.");
            return;
        }

        // List all .mef files in the directory
        File[] mefFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".mef"));
       
        // Number of channels (aka the number of mef files within directory) 
        numsignals = mefFiles.length;
 
        if (mefFiles == null || mefFiles.length == 0) {
            System.out.println("No .mef files found in the specified directory.");
            return;
        }
        

        // Init MEFBuilder
        mefBuilder = new EDFBuilder(mefFiles, directoryPath, subjectid,numsignals);
        
        try {
			mefBuilder.build();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        System.out.println("REAL EDF MADE");
    }

}