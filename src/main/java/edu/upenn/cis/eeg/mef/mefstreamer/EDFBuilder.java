package edu.upenn.cis.eeg.mef.mefstreamer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import edfheaderwriter.EDFHeaderWriter;
import edu.upenn.cis.db.mefview.services.TimeSeriesPage;
import edu.upenn.cis.edfdatawriter.EDFDataWriter;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleEdge;

import javax.swing.*;

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
        
		this.runningMax = 1;
		this.runningMin = -1;
		
		this.localMin = -1;
		this.localMax = 1;
		
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
		String startdatepre = "";
		
        // Read first MEF file and first block to build header
		// Pulls the first mef file 
        String path = this.files[0].getAbsolutePath();
        
        long daysBetweenInputAndBase = 0;
        
        // Opens the first Mef file from our list of files
       try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
    	   	
    	   // Get some file info from MEFStreamer
        	MEFStreamer streamer = new MEFStreamer(file);
        	MefHeader2 header = streamer.getMEFHeader();

                 
            long abs_starttime = header.getRecordingStartTime();
            //System.out.println("Absolute Start Time: " + abs_starttime);
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
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy");

                
            	if (previousPage == null) {
            		absStartTime = page.timeStart;
            		String date = new java.text.SimpleDateFormat("dd.MM.yy HH.mm.ss").format(new java.util.Date (page.timeStart / 1000 )); 
            		// can be improved 
            		StringTokenizer tokenizer = new StringTokenizer(date, " ");
            		startdatepre = tokenizer.nextToken();
                    LocalDate inputDate = LocalDate.parse(startdatepre, formatter);
                    LocalDate baseDate = LocalDate.of(2000, 1, 1);
                    daysBetweenInputAndBase = ChronoUnit.DAYS.between(inputDate, baseDate);
                    LocalDate shiftedDate = inputDate.plusDays(daysBetweenInputAndBase);
                    startdate = shiftedDate.format(formatter);
            		arguments.put("StartDate", startdate);
            		starttime = tokenizer.nextToken();
            		arguments.put("StartTime", starttime);
                    
        			long lastEntryTime = page.timeStart + (page.values.length - 1) * timeIncrement;
        			RandomAccessFile openFile = new RandomAccessFile(path,"r");
        			MEFStreamer nextpagestreamer = new MEFStreamer(openFile);
        			List<TimeSeriesPage> nextPages = nextpagestreamer.getNextBlocks((int) 2);
        			long nextBlockStartTime = nextPages.get(1).timeStart;
        			nextpagestreamer.close();
        			
        			

        			pagesum = readAndWriteBlocks(startdate, starttime, abs_starttime, numBlocks, samplingfreq, timeIncrement,
							pagesum, absStartTime, page, lastEntryTime, nextBlockStartTime);
            	}

            	
        		else {
        			long lastEntryTime = previousPage.timeStart + (previousPage.values.length - 1) * timeIncrement;
        			long nextBlockStartTime = page.timeStart;
        			if (mintimevalue == false) {
        				absStartTime = page.timeStart;
        				String date = new java.text.SimpleDateFormat("dd.MM.yy HH.mm.ss").format(new java.util.Date (page.timeStart / 1000 )); 
            			// can be improved 
        				StringTokenizer tokenizer = new StringTokenizer(date, " ");
        				startdatepre = tokenizer.nextToken();
        				LocalDate inputDate = LocalDate.parse(startdatepre, formatter);
                        LocalDate shiftedDate = inputDate.plusDays(daysBetweenInputAndBase);
                        startdate = shiftedDate.format(formatter);
                		arguments.put("StartDate", startdate);
        				starttime = tokenizer.nextToken();
        				arguments.put("StartTime", starttime);
        				mintimevalue = true;
        			}
        			
        			

        			pagesum = readAndWriteBlocks(startdate, starttime, abs_starttime, numBlocks, samplingfreq, timeIncrement,
							pagesum, absStartTime, page, lastEntryTime, nextBlockStartTime);


            		}
            	previousPage = page;
                runningMax = 0; 
            	runningMin = 0; 
            	streamer.close();            	
              
            	
            	
            } // This is the loop for each block within a mef file
                

            
        } catch (IOException e) {
            e.printStackTrace();
        }
	}


	private int readAndWriteBlocks(String startdate, String starttime, long abs_starttime, long numBlocks,
			double samplingfreq, long timeIncrement, int pagesum, long absStartTime, TimeSeriesPage page,
			long lastEntryTime, long nextBlockStartTime) throws FileNotFoundException, IOException {
		
		long timeDifference = nextBlockStartTime - lastEntryTime;
		endrange++;



		outputEDF = this.directoryPath + File.separator + subjectid + "_" + counter + ".edf";
		//delete existing files before rerunning!
		//File delFile = new File(outputEDF); //opens file
		//if (delFile.exists()) {
		//	delFile.delete();
		//}

		// Check if the time difference requires a new file (or new channel)
		if (timeDifference > 2 * timeIncrement && page.timeStart != absStartTime || timeDifference > 2 * timeIncrement && page.timeStart == abs_starttime) {
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

	private void writeDatatoEDF(double conversion_factor, int subtwocounter, MEFStreamer subtwostreamer) throws IOException {
		if ((subtwocounter == 0) && (startrange == 0) && (endrange == 1)) {
			List<TimeSeriesPage> subtwopage = subtwostreamer.getNextBlocks((int) 1);
			EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);

			dataWriter.write(currentOffset,subtwopage.get(0).values);
			currentOffset += dataWriter.calculateRecordSize();
			
		}
		else {
			List<TimeSeriesPage> subtwopage = subtwostreamer.getNextBlocks((int) endrange);
			for (int i = startrange; i < endrange; i++) {

				double scalingfactor = (runningMax - runningMin)/(digitalMax  - digitalMin);
				for (int j =0; j < subtwopage.get(i).values.length; j++) {
					subtwopage.get(i).values[j] = (int) ((scalingfactor *subtwopage.get(i).values[j]) * conversion_factor); 
					subtwocounter++;
				}

				EDFDataWriter dataWriter =  new EDFDataWriter(outputEDF, arguments);

				dataWriter.write(currentOffset,subtwopage.get(i).values);
				currentOffset += dataWriter.calculateRecordSize();

			}
		}
	}
	
	private void fileiterateandwrite(TimeSeriesPage page, long absStartTime, String startdate, String starttime, int pagesum, double samplingfreq) throws IOException {
		double actual_duration = pagesum/samplingfreq;
		arguments.put("Duration", actual_duration);
		
		// Opens up all files, finds if they are within the range, scales, and then 
		// writes to the edf file
		for (File currentFile : this.files) {
			int subcounter = 0;
			//int subtwocounter = 0;
			runningMax = 0;
			runningMin = 0;
			String currentpath = currentFile.getAbsolutePath();
			RandomAccessFile currenttwoFile = new RandomAccessFile(currentpath,"r");
			MEFStreamer substreamer = new MEFStreamer(currenttwoFile);
			MefHeader2 headervoltage = substreamer.getMEFHeader();
			
            double conversion_factor = headervoltage.getVoltageConversionFactor();
			
			subcounter = buildLocalMinMax(conversion_factor, subcounter, substreamer);
			
			RandomAccessFile currentthreeFile = new RandomAccessFile(currentpath,"r");
			MEFStreamer subtwostreamer = new MEFStreamer(currentthreeFile);
			
			//writeDatatoEDF(conversion_factor, subtwocounter, subtwostreamer);

            //subcounter++;
            substreamer.close();
            subtwostreamer.close();
            //  Add the physical max dimension here and index to be the first in the list
    		physicalMax.add(runningMax);
    		physicalMin.add(runningMin);
		}

		// Write out data into the edf
		System.out.println("Start Date: "  + startdate);
		System.out.println("Start Time: "  + starttime);
		Instant instant = Instant.now();
 		System.out.println("UTC Time: " + instant);
		arguments.put("Physicalmax", physicalMax);
		arguments.put("Physicalmin", physicalMin);
		arguments.put("SubjID", subjectid);
		arguments.put("Signalnum", numsignals);	
		
		//calculatedatarecords(samplingfreq, pagesum, numsignals, arguments);
		
		// Write header for the new file
		//EDFHeaderWriter headerWriter = new EDFHeaderWriter(outputEDF, arguments);
		//headerWriter.write(outputEDF);
	}
	
	private void calculatedatarecords(double samplingfreq, int pagesum, int numsignals, HashMap<String, Object> arguments) {

		//  Need to figure out if this is wrong 
		int totalSamples = pagesum;// * numsignals;

		// Upper limit to the number of bytes allowed per data record according to edf
		int maxSamplesPerRecord = 61440;  

		// Variables to store the results
		int numDataRecords = 0;
		int samplesPerRecord = 0;
		
		if (pagesum == 1) {
			arguments.put("Recordsnum", 1);
			arguments.put("NumSamples", 1);
			return;
		}
		else {
//			int start = 0;
//			if (0 <= totalSamples && totalSamples >= 999) {
//				start = totalSamples / 10; 
//			}
//			else if (1000 <= totalSamples && totalSamples >= 9999) {
//				start = totalSamples / 100; 
//			}
//			else if (10000 <= totalSamples && totalSamples >= 99999) {
//				start = totalSamples / 1000; 
//			}
//			else if (100000 <= totalSamples && totalSamples >= 999999) {
//				start = totalSamples / 10000; 
//			}
//			else if (1000000 <= totalSamples && totalSamples >= 9999999) {
//				start = totalSamples / 100000; 
//			}
 			// Need to update this so it actually works 
			// Iterate over values to determine value to divide by
			 int start = totalSamples / 10000;  
			 if (start == 0) {
				 if (0 <= totalSamples && totalSamples >= 999) {
					 start = totalSamples / 10; 
				 }
				 else if (1000 <= totalSamples && totalSamples >= 9999) {
					 start = totalSamples / 100; 
				 }
			 }


			for (samplesPerRecord = start; samplesPerRecord <= maxSamplesPerRecord; samplesPerRecord++) {
				// Check if the number of data records will be a whole number
				if (totalSamples % samplesPerRecord == 0) {
					// Calculate the number of data records
					numDataRecords = totalSamples / samplesPerRecord;
					arguments.put("Recordsnum", numDataRecords);
					arguments.put("NumSamples",samplesPerRecord);

					double newduration = (double) samplesPerRecord / samplingfreq;
					arguments.put("Duration", newduration);

					return;
				}
			}
			if (samplesPerRecord == (maxSamplesPerRecord + 1)) {
					System.out.println("No Whole Data Records Found");
					samplesPerRecord = 1;
					numDataRecords = totalSamples / samplesPerRecord;
					arguments.put("Recordsnum", numDataRecords);
					arguments.put("NumSamples",samplesPerRecord);

					double newduration = (double) samplesPerRecord / samplingfreq;
					arguments.put("Duration", newduration);
					
				}
			
		}

	}
	


	private int buildLocalMinMax(double conversion_factor, int subcounter, MEFStreamer substreamer) throws IOException {
		int[] values = null;
		List<Integer> appendedValues = new ArrayList<>();
		if ((startrange == 0) && (endrange == 1)) { //(subcounter == 0) && 
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

				//subcounter++;
		

	            // Collect values for the histogram
	            values = subpage.get(i).values;
	            
	            for (int value : values) {
	                appendedValues.add(value);
	            }

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
	     
	        HistogramDataset dataset = createHistogramDataset(appendedValues);
	        
	        double[] rawvalues = appendedValues.stream().mapToDouble(Integer::doubleValue).toArray();

	        // Calculate mean and standard deviation
	        double sum = 0.0;
	        for (double v : rawvalues) sum += v;
	        double mean = sum / rawvalues.length;

	        double squaredDiffs = 0.0;
	        for (double v : rawvalues) squaredDiffs += (v - mean) * (v - mean);
	        double stdDev = Math.sqrt(squaredDiffs / rawvalues.length);

	        // Count how many values fall outside of 3 std
	        int outlierCount = 0;
	        double lowerBound = mean - 3 * stdDev;
	        double upperBound = mean + 3 * stdDev;
	        for (double v : rawvalues) {
	        	if (v < lowerBound || v > upperBound) outlierCount++;
	        }
	        System.out.println("Number of values for edf " + (counter) + "outside 3 standard deviations: " + outlierCount);
	        System.out.println("Number of values for edf " + (counter) + ": " + rawvalues.length);
	        System.out.println("Percentage of values that would be removed with 3 stds " + (outlierCount/(rawvalues.length))*100);


	        // Create the chart
	        JFreeChart chart = createChart(dataset, counter);
	        XYPlot plot = (XYPlot) chart.getPlot();
	        
	     // Add vertical lines at 3 std
	        ValueMarker lowerMarker = new ValueMarker(lowerBound);
	        lowerMarker.setPaint(Color.RED);
	        lowerMarker.setStroke(new BasicStroke(2.0f));
	        lowerMarker.setLabel("-3σ");
	        lowerMarker.setLabelAnchor(RectangleAnchor.BOTTOM_RIGHT);         
	        //lowerMarker.setLabelAnchor(RectangleEdge.BOTTOM);
	        plot.addDomainMarker(lowerMarker);

	        ValueMarker upperMarker = new ValueMarker(upperBound);
	        upperMarker.setPaint(Color.RED);
	        upperMarker.setStroke(new BasicStroke(2.0f));
	        upperMarker.setLabel("+3σ");
	        upperMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT); 
	        //upperMarker.setLabelAnchor(RectangleEdge.BOTTOM);
	        plot.addDomainMarker(upperMarker);
	        
	        JFrame frame = new JFrame("Distribution Chart (Histogram)");
	        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        frame.getContentPane().add(new ChartPanel(chart));
	        frame.pack();
	        frame.setVisible(true);
		}
		return subcounter;
	}


	private static HistogramDataset createHistogramDataset(List<Integer> data) {
	  
	    double[] doubleData = new double[data.size()];
	    
	    for (int i = 0; i < data.size(); i++) {
	        doubleData[i] = data.get(i);  
	    }

	    // Create the histogram dataset
	    HistogramDataset dataset = new HistogramDataset();
	    dataset.addSeries("Data", doubleData, 30); 
	    return dataset;
	}

	

    private static JFreeChart createChart(HistogramDataset dataset, int counter) {
        // Create the histogram chart
        return ChartFactory.createHistogram(
                "Data Distribution"  + (counter),    // Title
                "Value",                // X-axis label
                "Frequency",            // Y-axis label
                dataset,                // Dataset
                org.jfree.chart.plot.PlotOrientation.VERTICAL, // Orientation (Vertical)
                true,                   // Include legend
                true,                   // Tooltips
                false                   // URLs
        );
    }

}

                	

