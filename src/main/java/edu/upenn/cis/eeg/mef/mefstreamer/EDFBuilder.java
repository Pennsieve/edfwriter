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

		// Initialize variables for runningMax/runningMin
		double runningMax = 1;
        double runningMin = -1;
        
        ArrayList<Double> physicalMax = new ArrayList<>();
        ArrayList<Double> physicalMin = new ArrayList<>();
        
        double localMin = -1;
        double localMax = 1;
        
        double digitalMax = 32767;
        double digitalMin = -32767;
        
        // Initialize variables for counter/startdate/starttime
		int counter = 0;
		String startdate = "";
		String starttime = "";
		
        // Read first MEF file and first block to build header
		// Pulls the first mef file 
        String path = this.files[0].getAbsolutePath();
        
        // Opens the first Mef file 
        // change to a for loop
       try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
        	MEFStreamer streamer = new MEFStreamer(file);
        	MefHeader2 header = streamer.getMEFHeader();
        	int currentOffset = 0;
                
                 
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
                
                // Initialize the arguments to be inputted into the edf header writer 
                arguments = new HashMap<>();
                arguments.put("SubjID", subjectid);
                arguments.put("Signalnum", numsignals);
                arguments.put("DigitalMin", digitalMin);
                arguments.put("DigitalMax", digitalMax);
                arguments.put("ChannelNames", channelnames);
                
            // Loop over each block in the first mef file
            for (TimeSeriesPage page : streamer.getNextBlocks((int) numBlocks)) {
            	
            	// Get min and max from values by streaming them in
        		localMin = Arrays.stream(page.values).min().orElseThrow();
                localMax = Arrays.stream(page.values).max().orElseThrow();
                if (localMax > runningMax) {
                	runningMax = localMax;
                }
                if (localMin < runningMin) {
                	runningMin = localMin;
                }
                
                
                // Get number of entries on page and add to variable
                // Adds the number of entries for each block over time
                pagesum += page.values.length;
                int recordsnum = pagesum * numsignals;
                arguments.put("Recordsnum", recordsnum);

                
            	if (previousPage == null) {
            		absStartTime = page.timeStart;
            		System.out.println("Start Time: " + page.timeStart);
            		String date = new java.text.SimpleDateFormat("dd.MM.yy HH.mm.ss").format(new java.util.Date (page.timeStart / 1000 )); 
            		// can be improved 
            		StringTokenizer tokenizer = new StringTokenizer(date, " ");
            		startdate = tokenizer.nextToken();
            		arguments.put("StartDate", startdate);
            		starttime = tokenizer.nextToken();
            		arguments.put("StartTime", starttime);
                    double totaltimeinblock = (double)(page.timeEnd - page.timeStart);
                    double numentries = totaltimeinblock * Math.pow(10, 6) * (1/samplingfreq);
                    System.out.println("Num Entries Calculated: " + numentries);
                    System.out.println("Total Time in Block: " + Math.subtractExact(page.timeEnd, page.timeStart)); 
                   // ** HERE INSERT FUNCTION THAT DOES THE DISCONTINUITY THING
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
        				arguments.put("StartDate", startdate);
        				starttime = tokenizer.nextToken();
        				arguments.put("StartTime", starttime);
        				mintimevalue = true;
        			}
        			
        			
        			// Write out discontinuity loop here
        			double calculated_sampfreq = pagesum/((page.timeStart - abs_starttime)*10e-6);
        			if (calculated_sampfreq > (2 * samplingfreq)){
            			System.out.println("Page Sum: " + pagesum);
            			System.out.println("page time start: " + page.timeStart);
            			System.out.println("Calculated Samp Freq: " + calculated_sampfreq);
            			System.out.println("Real Samp Freq: " + samplingfreq);
        				//Write out an interpolated value 
        			}
                    long timeDifference = nextBlockStartTime - lastEntryTime;
                    endrange++;
                    


            		outputEDF = this.directoryPath + File.separator + subjectid + "_" + counter + ".edf";
                  	//delete existing files before rerunning!
                	File delFile = new File(outputEDF); //opens file
                	if (delFile.exists()) {
                		delFile.delete();
                	}
                    
                    // Check if the time difference requires a new file (or new channel)
                    if (timeDifference > 2 * timeIncrement) {
                    	System.out.println("Discontinuity Found: " + page.timeStart);
                    	long endblocktime = page.timeEnd;
                    	long duration = (endblocktime - absStartTime)/1000000;
                    	arguments.put("Duration", duration);
                    	
                		// Opens up all files, finds if they are within the range, scales, and then 
                    	// writes to the edf file
                		for (File currentFile : this.files) {
                			int subcounter = 0;
                			int subtwocounter = 0;
                			runningMax = 0;
                			runningMin = 0;
                    		String currentpath = currentFile.getAbsolutePath();
                    		RandomAccessFile currenttwoFile = new RandomAccessFile(currentpath,"r");
                			MEFStreamer substreamer = new MEFStreamer(currenttwoFile);
                			for (TimeSeriesPage subpage: substreamer.getNextBlocks((int) numBlocks)) {
                				if (subcounter < startrange || subcounter > endrange) {
                    				subcounter++;
                    				continue;
                				}
                				
                				
                            	// Get min and max from values by streaming them in
                        		localMin = Arrays.stream(subpage.values).min().orElseThrow();
                                localMax = Arrays.stream(subpage.values).max().orElseThrow();
                                if (localMax > runningMax) {
                                	runningMax = localMax;
             
                                }
                                if (localMin < runningMin) {
                                	runningMin = localMin;
                                }
                                
                			}
                			RandomAccessFile currentthreeFile = new RandomAccessFile(currentpath,"r");
                			MEFStreamer subtwostreamer = new MEFStreamer(currentthreeFile);
                			for (TimeSeriesPage subtwopage: subtwostreamer.getNextBlocks((int) numBlocks)) {
                				if (subtwocounter < startrange || subtwocounter > endrange) {
                					subtwocounter++;
                					continue;
                				}
                				// ** THIS IS WHAT I THINK NEEDS TO BE CHANGED
                				//for (double value : page.values) {
                				double scalingfactor = (runningMax - runningMin)/(digitalMax  - digitalMin);
                				for (int i =0; i < subtwopage.values.length; i++) {
                					subtwopage.values[i] = (int) (scalingfactor * subtwopage.values[i]);
                				}
                				
                				
                            	EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);

                            	dataWriter.write(currentOffset,subtwopage.values);
                            	currentOffset += dataWriter.calculateRecordSize();
                		
                            }

                            subcounter++;
                            substreamer.close();
                            subtwostreamer.close();
                            //  Add the physical max dimension here and index to be the first in the list
                    		physicalMax.add(runningMax);
                    		physicalMin.add(runningMin);
                			}
                		
                			
                		
                		// Write out data into the edf
                        arguments = new HashMap<>();
                        arguments.put("Physicalmax", physicalMax);
                        arguments.put("Physicalmin", physicalMin);
                        //arguments.put("SubjID", subjectid);
                        //arguments.put("Signalnum", numsignals);
                        //arguments.put("StartDate", startdate);
                        //arguments.put("StartTime", starttime);
                        arguments.put("Duration", duration);						
                        arguments.put("Recordsnum", (pagesum * numsignals));
                        //arguments.put("ChannelNames", channelnames);
                        //arguments.put("DigitalMin", digitalMin);
                        //arguments.put("DigitalMax", digitalMax);
                        arguments.put("NumSamples",pagesum);
                        
                        
                        // Write header for the new file
                        EDFHeaderWriter headerWriter = new EDFHeaderWriter(outputEDF, arguments);
                        headerWriter.write(outputEDF);

                        counter++;

                    	// Specific to within file
                    	startrange = endrange;

                    	}
            		}
            	previousPage = page;
                // On the channel (file level)
                runningMax = 1; 
            	runningMin = -1; 
                // On the channel (file level) 
            	//counter++;
            	
            	
            } // This is the loop for each block within a mef file
                pagesum = 0;
                

            
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}
                	