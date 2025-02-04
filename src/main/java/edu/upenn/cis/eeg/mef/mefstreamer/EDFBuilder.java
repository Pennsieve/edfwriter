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

import org.apache.commons.compress.utils.IOUtils;

//import org.openjdk.jol.info.ClassLayout;

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
	
    ArrayList<Double> physicalMax;
    ArrayList<Double> physicalMin;
    
	double runningMax;
    double runningMin;
    
    double localMin;
    double localMax;
    
    double digitalMax;
    double digitalMin;
    
    // Initialize variables for counter/startdate/starttime
	int counter;
	
    int currentOffset;
    boolean mintimevalue;
    
    int startrange;
    int endrange;
    
    double duration;
    
	
	
	public EDFBuilder(File[] mefFiles, String directoryPath, String subjectid, int numsignals) {
		this.files = mefFiles;
		this.directoryPath = directoryPath;
		this.subjectid = subjectid;
		this.numsignals = numsignals;
		this.arguments = null; 
		
        this.physicalMax = new ArrayList<>();
        this.physicalMin = new ArrayList<>();
        
		this.runningMax = 0;
		this.runningMin = 0;
		
		this.localMin = 0;
		this.localMax = 0;
		
		this.digitalMax = 32767;
		this.digitalMin = -32767;
        
        // Initialize variables for counter/startdate/starttime
		this.counter = 0;
		
		this.currentOffset = 0;
		this.mintimevalue = false;
		
        this.startrange = 0;
        this.endrange = 0;
        
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

		String startdate = "";
		String starttime = "";
		
        // Read first MEF file and first block to build header
		// Pulls the first mef file 
        String path = this.files[0].getAbsolutePath();
        
        // Opens the first Mef file from our list of files
       try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
    	   	
    	   // Get some file info from MEFStreamer
        	MEFStreamer streamer = new MEFStreamer(file);
        	MefHeader2 header = streamer.getMEFHeader();
        	
                
                 
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
             
                
            int pagesum = 0;
            
            long absStartTime = 0;
            
           // double newendrange = 0;
            
            // Initialize the arguments to be inputed into the edf header writer 
            arguments = new HashMap<>();
            arguments.put("SubjID", subjectid);
            arguments.put("Signalnum", numsignals);
            arguments.put("DigitalMin", digitalMin);
            arguments.put("DigitalMax", digitalMax);
            arguments.put("ChannelNames", channelnames);
                
            // Loop over each block in the first mef file
            for (TimeSeriesPage page : streamer.getNextBlocks((int) numBlocks)) {
            	
                
                // Get number of entries on page and add to variable
                // Adds the number of entries for each block over time
                pagesum += (page.values.length);
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
                    double numentries = (totaltimeinblock * Math.pow(10, 6)) * (1/samplingfreq); 
                    
        			long lastEntryTime = page.timeStart + (page.values.length - 1) * timeIncrement;
        			System.out.println("Number of Entries in First Block: " + page.values.length);
        			RandomAccessFile openFile = new RandomAccessFile(path,"r");
        			MEFStreamer nextpagestreamer = new MEFStreamer(openFile);
        			List<TimeSeriesPage> nextPages = nextpagestreamer.getNextBlocks((int) 2);
        			long nextBlockStartTime = nextPages.get(1).timeStart;
        			nextpagestreamer.close();
        			
        			

        			pagesum = readAndWriteBlocks(startdate, starttime, abs_starttime, numBlocks, samplingfreq, timeIncrement,
							pagesum, absStartTime, page, lastEntryTime, nextBlockStartTime, numsignals);
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
        			
        			

        			pagesum = readAndWriteBlocks(startdate, starttime, abs_starttime, numBlocks, samplingfreq, timeIncrement,
							pagesum, absStartTime, page, lastEntryTime, nextBlockStartTime, numsignals);


            		}
            	previousPage = page;
                runningMax = 0; 
            	runningMin = 0; 
            	
              
            	
            	
            } // This is the loop for each block within a mef file
                

            
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

	private int readAndWriteBlocks(String startdate, String starttime, long abs_starttime, long numBlocks,
			double samplingfreq, long timeIncrement, int pagesum, long absStartTime, TimeSeriesPage page,
			long lastEntryTime, long nextBlockStartTime, int numsignals) throws FileNotFoundException, IOException {

		long timeDifference = nextBlockStartTime - lastEntryTime;
		endrange++;



		outputEDF = this.directoryPath + File.separator + subjectid + "_" + counter + ".edf";
		//delete existing files before rerunning!
		File delFile = new File(outputEDF); //opens file
		if (delFile.exists()) {
			delFile.delete();
		}
		
		
		// Check if the time difference requires a new file (or new channel)
		if (timeDifference > 2 * timeIncrement && page.timeStart != absStartTime || timeDifference > 2 * timeIncrement && page.timeStart == abs_starttime) {
			System.out.println("Page After cut: " + pagesum);
			System.out.println("Discontinuity Found: " + page.timeStart);

			fileiterateandwrite(page, absStartTime, startdate, starttime, pagesum, samplingfreq);

			counter++;

			// Specific to within file
			startrange = endrange;
			mintimevalue = false;
			physicalMax = new ArrayList<>();
			physicalMin = new ArrayList<>();
			duration = 0;
			pagesum = 0;

		}
		else if (endrange == numBlocks) {

			System.out.println("Final EDF Writing: " + page.timeStart);

			fileiterateandwrite(page, absStartTime, startdate, starttime, pagesum, samplingfreq);

			System.out.println("Final Count (End Range): " + endrange);


		}
		return pagesum;
	}

	private void writeDatatoEDF() throws IOException { //MEFStreamer subtwostreamer) 
		for (File currentthreeFile : this.files) {
			int subtwocounter = 0;
			String currentpathtwo = currentthreeFile.getAbsolutePath();
			RandomAccessFile currentfourFile = new RandomAccessFile(currentpathtwo,"r");
			MEFStreamer subtwostreamer = new MEFStreamer(currentfourFile);
			MefHeader2 headervoltage = subtwostreamer.getMEFHeader();
			
            double conversion_factortwo = headervoltage.getVoltageConversionFactor();
			if ((subtwocounter == 0) && (startrange == 0) && (endrange == 1)) {
				List<TimeSeriesPage> subtwopage = subtwostreamer.getNextBlocks((int) 1);
				EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);

				dataWriter.write(currentOffset,subtwopage.get(0).values);
				currentOffset += dataWriter.calculateRecordSize();

			}
			else {
				List<TimeSeriesPage> subtwopage = subtwostreamer.getNextBlocks((int) endrange);
				for (int i = startrange; i < endrange; i++) {

					subtwocounter++;


					double scalingfactor = (runningMax - runningMin)/(digitalMax  - digitalMin);
					for (int j =0; j < subtwopage.get(i).values.length; j++) {
						subtwopage.get(i).values[j] = (int) ((scalingfactor *subtwopage.get(i).values[j]) * conversion_factortwo); 
					}

					EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);

					dataWriter.write(currentOffset,subtwopage.get(i).values);
					currentOffset += dataWriter.calculateRecordSize();
				}

			}
			subtwostreamer.close();
		}
	}
	
	private void fileiterateandwrite(TimeSeriesPage page, long absStartTime, String startdate, String starttime, int pagesum, double samplingfreq) throws IOException {

		double actual_duration = pagesum/samplingfreq;
		System.out.println("Actual Duration: " + actual_duration);
		arguments.put("Duration", actual_duration);

		// Opens up all files, finds if they are within the range and pulls min and max
		for (File currentFile : this.files) {
			int subcounter = 0;
			String currentpath = currentFile.getAbsolutePath();
			RandomAccessFile currenttwoFile = new RandomAccessFile(currentpath,"r");
			MEFStreamer substreamer = new MEFStreamer(currenttwoFile);
			MefHeader2 headervoltage = substreamer.getMEFHeader();

			double conversion_factor = headervoltage.getVoltageConversionFactor();

			subcounter = buildLocalMinMax(conversion_factor, subcounter, substreamer);


			subcounter++;
			substreamer.close();
		}

		for (int i = 0; i < numsignals; i++) {
			physicalMin.add(runningMin);
			physicalMax.add(runningMax);
		}



		writeDatatoEDF();




		// Write out data into the edf
		System.out.println("PhysicalMax: " + physicalMax);
		System.out.println("PhysicalMin: " + physicalMin);
		System.out.println("Start Date: "  + startdate);
		System.out.println("Start Time: "  + starttime);
		arguments.put("Physicalmax", physicalMax);
		arguments.put("Physicalmin", physicalMin);
		arguments.put("SubjID", subjectid);
		arguments.put("Signalnum", numsignals);
		arguments.put("Duration", actual_duration);						
		arguments.put("Recordsnum", 1);
		arguments.put("NumSamples",pagesum);


		// Write header for the new file
		EDFHeaderWriter headerWriter = new EDFHeaderWriter(outputEDF, arguments);
		headerWriter.write(outputEDF);
	}
	

	private int buildLocalMinMax(double conversion_factor, int subcounter, MEFStreamer substreamer) throws IOException {
		if ((subcounter == 0) && (startrange == 0) && (endrange == 1)) {
			List<TimeSeriesPage> subpage = substreamer.getNextBlocks((int) 1);
			// Get min and max from values by streaming them in
			localMin = Arrays.stream(subpage.get(0).values).min().orElseThrow();
			localMax = Arrays.stream(subpage.get(0).values).max().orElseThrow();
			if (localMax > runningMax) {
				runningMax = localMax;

			}
			if (localMin < runningMin) {
				runningMin = localMin;
			}


		}
		else {
			List<TimeSeriesPage> subpage = substreamer.getNextBlocks((int) endrange);
			for (int i = startrange; i < endrange; i++) {

				subcounter++;

				if (conversion_factor < 0) {
					// Get min and max from values by streaming them in
					localMax = (Arrays.stream(subpage.get(i).values).min().orElseThrow()) * conversion_factor;
					localMin = (Arrays.stream(subpage.get(i).values).max().orElseThrow()) * conversion_factor;
					if (localMax > runningMax) {
						runningMax = localMax;

					}
					if (localMin < runningMin) {
						runningMin = localMin;
					}

				}

				else {

					// Get min and max from values by streaming them in
					localMin = (Arrays.stream(subpage.get(i).values).min().orElseThrow()) * conversion_factor;
					localMax = (Arrays.stream(subpage.get(i).values).max().orElseThrow()) * conversion_factor;
					if (localMax > runningMax) {
						runningMax = localMax;

					}
					if (localMin < runningMin) {
						runningMin = localMin;
					}
				}

			}
		}
		return subcounter;
	}

	private void elseif(int i) {
		// TODO Auto-generated method stub
		
	}
}
                	

