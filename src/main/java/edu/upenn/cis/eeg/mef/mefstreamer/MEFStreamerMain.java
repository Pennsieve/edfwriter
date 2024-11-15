package edu.upenn.cis.eeg.mef.mefstreamer;

//import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
//import java.io.FileWriter;
import java.io.IOException;
//import java.io.RandomAccessFile;
//import java.util.ArrayList;
import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.StringTokenizer;

//import edu.upenn.cis.edfdatawriter.EDFDataWriter;
//import edfheaderwriter.EDFHeaderWriter;
//import edu.upenn.cis.db.mefview.services.TimeSeriesPage;

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
            System.out.println("Usage: java -jar <jarfile> <Pathdir>");
            return;
        }
        
        String directoryPath = args[0];
        File directory = new File(directoryPath);
        String subjectid = directory.getName(); //
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
        
            

                            	
                            
                           //     if (edfFile != null) {
                                    // Close the current EDF file
                              //      edfFile.close();
                               // }
                                
                                // Update running max and min based on the current block's values
                      //        for (double value : page.values) {
                        //          runningMax = Math.max(runningMax, value);
                          //        runningMin = Math.min(runningMin, value);
                            //   }

                             // Initialize a counter for the numbering
                              //  int counter = 1; 

                                // Generate the new file path with the counter
                                //newEdfFilePath = directoryPath + File.separator + subjectid + "_" + counter + ".edf";

                                // Increment the counter for the next file
                               // counter++;
                                
                                //edfFile = new RandomAccessFile(newEdfFilePath, "rw");
                                
                             //   arguments = new HashMap<>();
                               // arguments.put("Physicalmax", runningMax);
                                //arguments.put("Physicalmin", runningMin);
                                //arguments.put("SubjID", subjectid);
                                //arguments.put("Signalnum", numsignals);
                                //arguments.put("StartDate", startdate);
                                //arguments.put("StartTime", starttime);
                                //arguments.put("Duration", duration);
                                //arguments.put("Recordsnum", pagesum);
                                //arguments.put("ChannelNames", channelnames);
                      


                                // Write header for the new file
                                //EDFHeaderWriter writer = new EDFHeaderWriter(newEdfFilePath, arguments);
//                                writer.write();

                                //System.out.println("New EDF file created: " + newEdfFilePath);
                                //pagesum = 0;
                               // mintimevalue = false;
                            //}
                            


                            // Write the data record for the current block to the EDF file
                           // if (edfFile != null) {
                            //	EDFDataWriter writer = new EDFDataWriter(newEdfFilePath, arguments);
//                            	writer.write();
                            	//edfFile, page.values);
                            //}

                            // Log shift times
                            //if (timeDifference > timeIncrement && timeDifference < 2 * timeIncrement) {
                              //  long timeshift = (long) (timeDifference - timeIncrement);
                                //shiftWriter.write(String.format("%d,%d,%d\n", lastEntryTime, nextBlockStartTime, timeshift));
                            //}


                        //}

                        // Update the previous page to the current page for the next iteration
                        //previousPage = page;
                    //}

                  //  System.out.println("Shift file created: " + shiftFileName);
                    //System.out.println("Running Max: " + runningMax);
                    //System.out.println("Running Min: " + runningMin);
                //}
           // } catch (IOException e) {
               // System.err.println("Error processing file: " + fileName);
               // e.printStackTrace();
          //  }
//        }

//        System.out.println("All files processed!");
    }

}