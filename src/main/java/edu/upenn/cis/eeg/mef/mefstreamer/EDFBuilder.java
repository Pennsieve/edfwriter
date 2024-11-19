package edu.upenn.cis.eeg.mef.mefstreamer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import edfheaderwriter.EDFHeaderWriter;
import edu.upenn.cis.db.mefview.services.TimeSeriesPage;
import edu.upenn.cis.edfdatawriter.EDFDataWriter;

public class EDFBuilder{
	
	File[] files;
	String directoryPath;
	String outputEDF;
	String subjectid;
	int numsignals;
	HashMap<String,Object> arguments; 
	
	
	public EDFBuilder(File[] mefFiles, String directoryPath, String subjectid, int numsignals) {
		this.files = mefFiles;
		this.directoryPath = directoryPath;
		this.subjectid = subjectid;
		this.numsignals = numsignals;
		this.arguments = null; 
		System.out.println(this.files);
	
	}
	
	public void build() throws FileNotFoundException, IOException {
		
		// Initialize variable for channelnames
		ArrayList<String> channelnames = new ArrayList<>();
		
		
		// Get channel names
		for (File inputFile : this.files) {
            String fileName = inputFile.getName();
            String baseName;
            
            // Check for file extension
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = fileName.substring(0, dotIndex);
            } else {
                baseName = fileName; // Use the whole filename if no extension
            }
            
            channelnames.add(baseName);
            
		}
		
		// Creates csv for timing shifts
       String shiftFileName = directoryPath + File.separator + subjectid + "_shifted_times.csv";

		
		// Initialize variables for runningMax/runningMin
		double runningMax = Double.NEGATIVE_INFINITY;
        double runningMin = Double.POSITIVE_INFINITY;
        
        // Initialize variables for counter/startdate/starttime
		int counter = 1;
		String startdate = "";
		String starttime = "";
		File[] filesminusfirst = new File[this.files.length - 1];
		
		
		// Create variable that excludes the first mef file
		System.arraycopy(this.files, 1, filesminusfirst, 0, this.files.length - 1);
		
        // Read first MEF file and first block to build header
		// Pulls the first mef file 
        String path = this.files[0].getAbsolutePath();
        
        // Opens the first Mef file 
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
        	MEFStreamer streamer = new MEFStreamer(file);
        	MefHeader2 header = streamer.getMEFHeader();
                
                // Write values to CSV
                try (BufferedWriter shiftWriter = new BufferedWriter(new FileWriter(shiftFileName))) {
                    shiftWriter.write("Start_Discont,End_Discont,Difference_minus_Increment\n");
                 
                    // Get number of blocks from MEF file
                    long numBlocks = streamer.getNumberOfBlocks();
                    System.out.println("Num Blocks: " + numBlocks + " blocks.");
                    
                	// Pull sampling frequency to be used in calculations
                	double samplingfreq = header.getSamplingFrequency();
                    // Define the time increment
                    long timeIncrement = (long) (1.0 / samplingfreq * Math.pow(10, 6)); // 1/sampling frequency * 10^6

                    TimeSeriesPage previousPage = null;

                    // Create new EDF files when time difference exceeds threshold
                    RandomAccessFile edfFile = null;
                    
                    int pagesum = 0;
                    boolean mintimevalue = false;
                    long absStartTime = 0;
                    int startrange = 0;
                    int endrange = 0;
                
                
                // Loop over each block in the first mef file
                for (TimeSeriesPage page : streamer.getNextBlocks((int) numBlocks)) {
                	if (previousPage == null) {
                		absStartTime = page.timeStart;
                		String date = new java.text.SimpleDateFormat("dd.MM.yy HH.mm.ss").format(new java.util.Date (page.timeStart / 1000 )); 
                		// can be improved 
                		StringTokenizer tokenizer = new StringTokenizer(date, " ");
                		startdate = tokenizer.nextToken();
                		starttime = tokenizer.nextToken();
             
                	}
                		
                		else {
                			pagesum += page.values.length;
                			long lastEntryTime = previousPage.timeStart + (previousPage.values.length - 1) * timeIncrement;
                			long nextBlockStartTime = page.timeStart;
                			if (mintimevalue == false) {
                				absStartTime = page.timeStart;
                				String date = new java.text.SimpleDateFormat("dd.MM.yy HH.mm.ss").format(new java.util.Date (page.timeStart / 1000 )); 
                    			// can be improved 
                				StringTokenizer tokenizer = new StringTokenizer(date, " ");
                				startdate = tokenizer.nextToken();
                				starttime = tokenizer.nextToken();
                				mintimevalue = true;
                        }
                        long timeDifference = nextBlockStartTime - lastEntryTime;
                        endrange++;
                        
                        // Write the first mef file data here 
                        // Where should this go?
                		outputEDF = this.directoryPath + File.separator + subjectid + "_" + counter + ".edf";
                		EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);
                		dataWriter.write();
                        
                        // Check if the time difference requires a new file (or new channel)
                        if (timeDifference > 2 * timeIncrement) {
                        	long endblocktime = page.timeEnd;
                        	long duration = (endblocktime - absStartTime)/1000000;
                        	
                        	// Loops through the rest of the mef files 
                        	for (File inputFile : filesminusfirst) {
                        		// Loops through the blocks that the first mef file got through
                        		int subcounter = 0;
                        		for (TimeSeriesPage subpage : streamer.getNextBlocks((int) numBlocks)) {

                        			if (subcounter < startrange || subcounter > endrange) {
                        				subcounter++;
                        				continue;
                        			}
                        			// Write data to edf here too
                        			EDFDataWriter subdataWriter =  new EDFDataWriter(outputEDF, arguments);
                                    // Update running max and min based on the current block's values
                                    for (double value : subpage.values) {
                                        runningMax = Math.max(runningMax, value);
                                        runningMin = Math.min(runningMin, value);
                                     } 
                                    subcounter++;
                        		}

                        		}
                    		// Write out data into the edf
                            arguments = new HashMap<>();
                            arguments.put("Physicalmax", runningMax);
                            arguments.put("Physicalmin", runningMin);
                            arguments.put("SubjID", subjectid);
                            arguments.put("Signalnum", numsignals);
                            arguments.put("StartDate", startdate);
                            arguments.put("StartTime", starttime);
                            arguments.put("Duration", duration); // Needs to be updated
                            arguments.put("Recordsnum", pagesum); // Needs to be updated
                            arguments.put("ChannelNames", channelnames);
                            
                            // Write header for the new file
                            EDFHeaderWriter headerWriter = new EDFHeaderWriter(outputEDF, arguments);
                            headerWriter.write(path);
                            int headerSize = 0;
                        	counter++;
                        
                        	startrange = endrange;
                        	}
                		}
                	previousPage = page;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
               
                        
                   //         if (edfFile != null) {
                                // Close the current EDF file
                     //           edfFile.close();
                    //        }
                           
        	
		
		// for page in filepath[0] done
		
			//check for discontinuity done
		
			// if discontinuity = true done
				
				// update size of data to read ()
		
				// for remaining mef files [1-end]
					
					// write data()
		
				// write header to start of file ()
		
			// write data to file () - only writes if no discontinuity 
		
		// write header to start of file ()
		

                
             //   System.out.println("Block Header Length: " + header.getBlockHeaderLength());
               // System.out.println("Block interval: " + header.getBlockInterval());
                //System.out.println("Discontinuity Offset: " + header.getDiscontinuityDataOffset());
             //   System.out.println("GMT Offset: " + header.getGmtOffset());
               // System.out.println("Header Length: " + header.getHeaderLength());
             //   System.out.println("Number of Discontinuities: " + header.getNumberOfDiscontinuityEntries());
               // System.out.println("No. Samples: " + header.getNumberOfSamples());
              //  System.out.println("Start time: " + header.getRecordingStartTime());
               // System.out.println("SEnd Time: " + header.getRecordingEndTime());
                

                //System.out.println("Num Blocks: " + numBlocks + " blocks.");
                

            	//System.out.println("Sampling frequency: " + samplingfreq);
            	
            	//System.out.println("Time increment: " + timeIncrement);
	}
}

                
     
                
                
              // IDK what some of this is  
             //   int currentOffset = headerSize;
          
                
              //  for (TimeSeriesPage page : streamer.getNextBlocks((int) numBlocks)) {
                	
                //	System.out.println("Page Len: "+ page.values.length);
                	
                //	if (page.values.length > 1) {
                   // 	pageSum+= page.values.length;
                    	//dataWriter.write(currentOffset, page.values);
                    	//currentOffset += dataWriter.calculateRecordSize();
                	