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
import java.util.Map;
import java.util.StringTokenizer;

import edu.upenn.cis.edfdatawriter.EDFDataWriter;
import edfheaderwriter.EDFHeaderWriter;
import edu.upenn.cis.db.mefview.services.TimeSeriesPage;

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
        
        ArrayList<String> channelnames = new ArrayList<>();

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
        
        // Process each .mef file
        for (File inputFile : mefFiles) {
            String filePath = inputFile.getAbsolutePath();
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
            

            // Construct output file names including the directory
            String headerFileName = directoryPath + File.separator + baseName + "_header_info.csv";
            String shiftFileName = directoryPath + File.separator + baseName + "_shifted_times.csv";

            try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
                MEFStreamer streamer = new MEFStreamer(file);
                MefHeader2 header = streamer.getMEFHeader();

         // Write header information to CSV 53
                try (BufferedWriter headerWriter = new BufferedWriter(new FileWriter(headerFileName))) {
                	headerWriter.write("Institution,Unencrypted Text Field,Encryption Algorithm,Subject Encryption Used,Session Encryption Used,Data Encryption Used,Byte Order Code,Header Version Major,Header Version Minor,Header Length,Session Unique ID,Subject First Name,Subject Middle Name,Subject Last Name,Subject ID,Session Password,Number of Index Entries,Channel Name,Recording Start Time,Recording End Time,Sampling Frequency,Low Frequency Filter Setting,High Frequency Filter Setting,Notch Filter Frequency,Voltage Conversion Factor,Acquisition System,Channel Comments,Study Comments,Physical Channel Number,Compression Algorithm,Maximum Compressed Block Size,Maximum Block Length,Block Interval,Maximum Data Value,Minimum Data Value,Block Header Length,GMT Offset,Discontinuity Data Offset,Number of Discontinuity Entries,File Unique ID,Anonymized Subject Name,Header CRC,8 Random Bytes,Bytes Read,Class,File Name,Index Data Offset,Index Data Offset End,Session Validation Field,Subject Validation Field,Voltage Offset,CRC Tab 32\n");
                	headerWriter.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                			header.getInstitution(), // string
                			header.getUnencryptedTextField(), //string
                			header.getEncryptionAlgorithm(), //string
                			String.valueOf(header.getSubjectEncryptionUsed()), //int
                			String.valueOf(header.getSessionEncryptionUsed()), //int
                			String.valueOf(header.getDataEncryptionUsed()), //int
                			String.valueOf(header.getByteOrderCode()), //int
                			String.valueOf(header.getHeaderVersionMajor()), //int
                			String.valueOf(header.getHeaderVersionMinor()), //int
                			String.valueOf(header.getHeaderLength()), //short
                			String.valueOf(header.getSessionUniqueID()), //byte
                			header.getSubjectFirstName(), //string
                			header.getSubjectSecondName(), //string 
                			header.getSubjectThirdName(), //string
                			header.getSubjectId(), //string
                			header.getSessionPassword(), //string
                			// missing subject password validation field
                			// missing protected region
                			String.valueOf(header.getNumberOfIndexEntries()), //long
                			header.getChannelName(), //string
                			header.getRecordingStartTime(), //string
                			header.getRecordingEndTime(), //string
                			String.valueOf(header.getSamplingFrequency()),
                			String.valueOf(header.getLowFrequencyFilterSetting()), // double
                			String.valueOf(header.getHighFrequencyFilterSetting()), //double
                			String.valueOf(header.getNotchFilterFrequency()), //double
                			String.valueOf(header.getVoltageConversionFactor()), //double
                			header.getAcquisitionSystem(), //string
                			header.getChannelComments(), //string
                			header.getStudyComments(), //string
                			String.valueOf(header.getPhysicalChannelNumber()), //int
                			header.getCompressionAlgorithm(), //string
                			String.valueOf(header.getMaximumCompressedBlockSize()), //long
                			String.valueOf(header.getMaximumBlockLength()), //long
                			String.valueOf(header.getBlockInterval()), //long
                			String.valueOf(header.getMaximumDataValue()), //long
                			String.valueOf(header.getMinimumDataValue()), //long
                			// missing Offset to Block Indices Data
                			// missing Number of Block Index Entries
                			String.valueOf(header.getBlockHeaderLength()), //short
                			// missing unused
                			String.valueOf(header.getGmtOffset()), //float
                			String.valueOf(header.getDiscontinuityDataOffset()), //long
                			String.valueOf(header.getNumberOfDiscontinuityEntries()), //long
                			// missing unused
                			String.valueOf(header.getFileUniqueID()), //byte
                			header.getAnonymizedSubjectName(), //string
                			String.valueOf(header.getHeaderCrc()),
                			String.valueOf(header.get8RandomBytes()),
                			String.valueOf(header.getBytesRead()),
                			String.valueOf(header.getClass()),
                			header.getFilename(),
                			String.valueOf(header.getIndexDataOffset()),
                			String.valueOf(header.getIndexDataOffsetEnd()),
                			String.valueOf(header.getNumberOfSamples()),
                			header.getSessionValidationField(),
                			header.getSubjectValidationField(),
                			String.valueOf(header.getVoltageOffset()),
                			String.valueOf(header.getCrcTab32()))); //byte
                        	// missing EEG Data Start
                		
                }
                
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
                    // Initialize running max and min
                    double runningMax = Double.NEGATIVE_INFINITY;
                    double runningMin = Double.POSITIVE_INFINITY;
                    
                    // Create new EDF files when time difference exceeds threshold
                    RandomAccessFile edfFile = null;
                    
                    int pagesum = 0;
                    boolean mintimevalue = false;
                    long absStartTime = 0;
                    
                   

                    // Loop over each block
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
                    			//System.out.println("Page Values: " + page.values.length);
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

                            // Check if the time difference requires a new file (or new channel)
                            if (timeDifference > 2 * timeIncrement) {
                            	long endblocktime = page.timeEnd;
                            	long duration = (endblocktime - absStartTime)/1000000;
                                if (edfFile != null) {
                                    // Close the current EDF file
                                    edfFile.close();
                                }
                                
                                // Update running max and min based on the current block's values
                              for (double value : page.values) {
                                  runningMax = Math.max(runningMax, value);
                                  runningMin = Math.min(runningMin, value);
                               }

                             // Initialize a counter for the numbering
                                int counter = 1; 

                                // Generate the new file path with the counter
                                newEdfFilePath = directoryPath + File.separator + subjectid + "_" + counter + ".edf";

                                // Increment the counter for the next file
                                counter++;
                                
                                edfFile = new RandomAccessFile(newEdfFilePath, "rw");
                                
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
                                EDFHeaderWriter writer = new EDFHeaderWriter(newEdfFilePath, arguments);
                                writer.write();

                                System.out.println("New EDF file created: " + newEdfFilePath);
                                pagesum = 0;
                                mintimevalue = false;
                            }
                            


                            // Write the data record for the current block to the EDF file
                            if (edfFile != null) {
                            	EDFDataWriter writer = new EDFDataWriter(newEdfFilePath, arguments);
//                            	writer.write();
                            	//edfFile, page.values);
                            }

                            // Log shift times
                            if (timeDifference > timeIncrement && timeDifference < 2 * timeIncrement) {
                                long timeshift = (long) (timeDifference - timeIncrement);
                                shiftWriter.write(String.format("%d,%d,%d\n", lastEntryTime, nextBlockStartTime, timeshift));
                            }


                        }

                        // Update the previous page to the current page for the next iteration
                        previousPage = page;
                    }

                    System.out.println("Header file created: " + headerFileName);
                    System.out.println("Shift file created: " + shiftFileName);
                    System.out.println("Running Max: " + runningMax);
                    System.out.println("Running Min: " + runningMin);
                }
            } catch (IOException e) {
                System.err.println("Error processing file: " + fileName);
                e.printStackTrace();
            }
        }

        System.out.println("All files processed!");
    }

}