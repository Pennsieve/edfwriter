package edu.upenn.cis.eeg.mef.mefstreamer;

import java.io.File;
import java.io.FileNotFoundException;
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
		
	}
	
	public void build() throws FileNotFoundException, IOException {
		
		ArrayList<String> channelnames = new ArrayList<>();
		int counter = 1;
		String startdate;
		String starttime;
		
		// Write the header first with some blank data
		//newEdfFilePath
		outputEDF = this.directoryPath + File.separator + subjectid + "_" + counter + ".edf";
        
		double runningMax = Double.NEGATIVE_INFINITY;
        double runningMin = Double.POSITIVE_INFINITY;
        
        // Read first MEF file and first block to build header
        
        String path = this.files[0].getAbsolutePath();
        
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
        	MEFStreamer streamer = new MEFStreamer(file);
        	MefHeader2 header = streamer.getMEFHeader();
        	
        	runningMax = header.getMaximumDataValue();
        	runningMin = header.getMinimumDataValue();
        	
        	// Read in one block to get basic header info
        	List<TimeSeriesPage> page = streamer.getNextBlocks(1);
        	String date = new java.text.SimpleDateFormat("dd.MM.yy HH.mm.ss").format(new java.util.Date (page.get(0).timeStart / 1000 ));
    		StringTokenizer tokenizer = new StringTokenizer(date, " ");
    		startdate = tokenizer.nextToken();
    		starttime = tokenizer.nextToken();
    		
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
        	
        }
        
        arguments = new HashMap<>();
        arguments.put("Physicalmax", runningMax);
        arguments.put("Physicalmin", runningMin);
        arguments.put("SubjID", subjectid);
        arguments.put("Signalnum", numsignals);
        arguments.put("StartDate", startdate);
        arguments.put("StartTime", starttime);
        arguments.put("Duration", 1l); // Needs to be updated
        arguments.put("Recordsnum", -1); // Needs to be updated
        arguments.put("ChannelNames", channelnames);



        // Write header for the new file
        EDFHeaderWriter headerWriter = new EDFHeaderWriter(outputEDF, arguments);
        int headerSize = 0;
//      headerWriter.write();
		
		for (File inputFile : this.files) {
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
            
            String shiftFileName = directoryPath + File.separator + baseName + "_shifted_times.csv";
            
            try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
                MEFStreamer streamer = new MEFStreamer(file);
                MefHeader2 header = streamer.getMEFHeader();
                
                System.out.println("Block Header Length: " + header.getBlockHeaderLength());
                System.out.println("Block interval: " + header.getBlockInterval());
                System.out.println("Discontinuity Offset: " + header.getDiscontinuityDataOffset());
                System.out.println("GMT Offset: " + header.getGmtOffset());
                System.out.println("Header Length: " + header.getHeaderLength());
                System.out.println("Number of Discontinuities: " + header.getNumberOfDiscontinuityEntries());
                System.out.println("No. Samples: " + header.getNumberOfSamples());
                System.out.println("Start time: " + header.getRecordingStartTime());
                System.out.println("SEnd Time: " + header.getRecordingEndTime());
                
                // Get number of blocks from MEF file
                long numBlocks = streamer.getNumberOfBlocks();
                System.out.println("Num Blocks: " + numBlocks + " blocks.");
                
                // Pull sampling frequency to be used in calculations
            	double samplingfreq = header.getSamplingFrequency();
            	System.out.println("Sampling frequency: " + samplingfreq);
            	
            	// Define the time increment
                long timeIncrement = (long) (1.0 / samplingfreq * Math.pow(10, 6)); // 1/sampling frequency * 10^6
                System.out.println("Time increment: " + timeIncrement);
                
                TimeSeriesPage previousPage = null;
                
//                // Initialize running max and min
//                double runningMax = Double.NEGATIVE_INFINITY;
//                double runningMin = Double.POSITIVE_INFINITY;
                
                EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);
                
                int currentOffset = headerSize;
                int pageSum = 0;
                
                for (TimeSeriesPage page : streamer.getNextBlocks((int) numBlocks)) {
                	
                	System.out.println("Page Len: "+ page.values.length);
                	
                	if (page.values.length > 1) {
                    	pageSum+= page.values.length;
                    	dataWriter.write(currentOffset, page.values);
                    	currentOffset += dataWriter.calculateRecordSize();
                	}
                	
                	
                }
            }
            
		}
	}
}