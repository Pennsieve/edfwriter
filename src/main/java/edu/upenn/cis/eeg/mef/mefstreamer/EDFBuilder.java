package edu.upenn.cis.eeg.mef.mefstreamer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
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
		double runningMax = 1;
        double runningMin = -1;
        
        double localMin = -1;
        double localMax = 1;
        
        double physicalMax = 32767;
        double physicalMin = -32767;
        
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
        	int currentOffset = 0;
                
                // Write values to CSV
                try (BufferedWriter shiftWriter = new BufferedWriter(new FileWriter(shiftFileName))) {
                    shiftWriter.write("Start_Discont,End_Discont,Difference_minus_Increment\n");
                 
                    long abs_starttime = header.getRecordingStartTime();
                    System.out.println("Absolute Start Time: " + abs_starttime);
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
                    // Don't know if we need this
                    arguments = new HashMap<>();
                    //arguments.put("Physicalmax", runningMax);
                    //arguments.put("Physicalmin", runningMin);
                    //arguments.put("SubjID", subjectid);
                    //arguments.put("Signalnum", numsignals);
                    //arguments.put("StartDate", startdate);
                    //arguments.put("StartTime", starttime);
                    //arguments.put("Duration", 1l); // Needs to be updated
                    //arguments.put("Recordsnum", -1); // Needs to be updated
                    //arguments.put("ChannelNames", channelnames);
                
                // Loop over each block in the first mef file
                for (TimeSeriesPage page : streamer.getNextBlocks((int) numBlocks)) {
                	MefHeader2 mefHeader = streamer.getMEFHeader();
                	
                	// Get min and max from MEF header

            		localMin = Arrays.stream(page.values).min().orElseThrow();
                    localMax = Arrays.stream(page.values).max().orElseThrow();
                    if (localMax > runningMax) {
                    	runningMax = localMax;
                    	arguments.put("Physicalmax", localMax);
                    }
                    if (localMin < runningMin) {
                    	runningMin = localMin;
                    	arguments.put("Physicalmin", localMin);
                    }
                    
                    
                    // Get number of entries on page and add to variable
                    // Adds the number of entries for each block over time
                    pagesum += page.values.length;
                   // System.out.println("Page Sum: " + pagesum);
                   // long numentries = ((page.timeEnd - page.timeStart) * (1/512));
                    //System.out.println("Num Entries Calculated: " + numentries);
                   // System.out.println("Page Sum: " + pagesum);

                    
                	if (previousPage == null) {
                		absStartTime = page.timeStart;
                		System.out.println("Start Time: " + page.timeStart);
                		String date = new java.text.SimpleDateFormat("dd.MM.yy HH.mm.ss").format(new java.util.Date (page.timeStart / 1000 )); 
                		// can be improved 
                		StringTokenizer tokenizer = new StringTokenizer(date, " ");
                		startdate = tokenizer.nextToken();
                		starttime = tokenizer.nextToken();
                        double totaltimeinblock = (double)(page.timeEnd - page.timeStart);
                        double numentries = totaltimeinblock * Math.pow(10, 6) * (1/samplingfreq);
                        System.out.println("Num Entries Calculated: " + numentries);
                        System.out.println("Total Time in Block: " + Math.subtractExact(page.timeEnd, page.timeStart)); 
                       // System.out.println("Num Entires Calculated: " + Math.multiplyExact((1/512), Math.subtractExact(page.timeEnd, page.timeStart))); 
             
                	}
            		else {
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
            			// Write out discontinuity loop here
            			//System.out.println("Page Sum: " + pagesum);
            			//System.out.println("page time start: " + page.timeStart);
            			double calculated_sampfreq = pagesum/((page.timeStart - abs_starttime)*10e-6);
            			//System.out.println("Calculated Samp Freq: " + calculated_sampfreq);
            			if (calculated_sampfreq > 2 * samplingfreq);{
                			System.out.println("Page Sum: " + pagesum);
                			System.out.println("page time start: " + page.timeStart);
                			System.out.println("Calculated Samp Freq: " + calculated_sampfreq);
                			System.out.println("Real Samp Freq: " + samplingfreq);
            				//Write out an interpolated value 
            			}
                        long timeDifference = nextBlockStartTime - lastEntryTime;
                        endrange++;
                        
                        // Write the first mef file data here 
                        // Where should this go?

                		outputEDF = this.directoryPath + File.separator + subjectid + "_" + counter + ".edf";
                		//EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);
                		// How to scale all the values? maybe not just writing them out here 
                		// Probably need to not write data until  we have the range of all the data we are writing?
                		//dataWriter.write(currentOffset,page.values);
                		

                		
                		// Write out to csv
                		if (timeDifference > timeIncrement && timeDifference < 2 * timeIncrement) {
                            long timeshift = (long) (timeDifference - timeIncrement);
                            shiftWriter.write(String.format("%d,%d,%d\n", lastEntryTime, nextBlockStartTime, timeshift));
                         //   if (page.timestart/512 >)
                		}
                		
                		// Add if loop here to write out continuously , actually no put this as an elseif maybe for the
                		//discontinuity counter
                        
                        // Check if the time difference requires a new file (or new channel)
                        if (timeDifference > 2 * timeIncrement) {
                        	System.out.println("Discontinuity Found: " + page.timeStart);
                        	long endblocktime = page.timeEnd;
                        	long duration = (endblocktime - absStartTime)/1000000;
                        	// Loops through the rest of the mef files 
                        	for (File inputFile : filesminusfirst) {
                        		// Loops through the blocks that the first mef file got through
                        		int subcounter = 0;
                        		for (TimeSeriesPage subpage : streamer.getNextBlocks((int) numBlocks)) {
                        			// Start range begins at 0 and helps to define the range of values for each 
                        			// new edf file, the code will iterate through each value and update the start range 
                        			// until 
                        			if (subcounter < startrange || subcounter > endrange) {
                        				subcounter++;
                        				continue;
                        			}

                                    // Update running max and min based on the current block's values
                                    for (double value : subpage.values) {
                                		localMin = Arrays.stream(subpage.values).min().orElseThrow();
                                        localMax = Arrays.stream(subpage.values).max().orElseThrow();
                                        if (localMax > runningMax) {
                                        	runningMax = localMax;
                                        	arguments.put("Physicalmax", localMax);
                                        }
                                        if (localMin < runningMin) {
                                        	runningMin = localMin;
                                        	arguments.put("Physicalmin", localMin);
                                        }
        
                                     } 
                        		}
                                    
                        			// Write data to edf here too
                        		for (File currentFile: this.files); {
                        			int subtwocounter = 0;
                        			for (TimeSeriesPage subpage: streamer.getNextBlocks((int) numBlocks)) {
                        				if (subtwocounter < startrange || subtwocounter > endrange) {
                            				subtwocounter++;
                            				continue;
                        				}
                        				
                                	//delete existing files before rerunning!
                                	File delFile = new File(outputEDF); //opens file
                                	if (delFile.exists()) {
                                		delFile.delete();
                                	}
                        			EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);
                        			double scalingfactor = (runningMax - runningMin)/(physicalMax  - physicalMin);
                                    for (int i =0; i < page.values.length; i++) {
                                    	page.values[i] = (int) (scalingfactor * page.values[i]);
                                    }
  
                        			dataWriter.write(currentOffset,page.values);
                        			currentOffset += dataWriter.calculateRecordSize();
                        		
                                    }
                                    
                            		
                                    subcounter++;
                                    subtwocounter++;
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
                            arguments.put("Duration", duration);						
                            arguments.put("Recordsnum", pagesum);
                            arguments.put("ChannelNames", channelnames);
                            
                            // Write header for the new file
                            EDFHeaderWriter headerWriter = new EDFHeaderWriter(outputEDF, arguments);
                            headerWriter.write(outputEDF);

                        	counter++;
                        
                        	startrange = endrange;
                        	}
                		}
                	previousPage = page;
                	
                	
                }
                pagesum = 0;
                
                // Need to move this to be in the right place which is not below previous page
                runningMax = 1; 
            	runningMin = -1; 
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}
                	