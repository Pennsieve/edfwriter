package edfheaderwriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import edu.upenn.cis.eeg.mef.mefstreamer.FieldDetails;

//import edu.upenn.cis.eeg.mef.mefstreamer.MefHeader2;

public class EDFHeaderWriter {
	
    private String version;
    private String patientID;
    private String recordingID;
    private String startDate;
    private String startTime;
    private int numRecords;
    private int NumSamples;
    private long duration;
    private int numSignals;
    private int numBytes;
    private double Digital_Min; // Hardcoded. Min size of possible data using 2 bytes (Short)
    private double Digital_Max; // Hardcoded. Max size of possible data using 2 bytes (Short)
    
    
    private String[] signalLabels;
    private String[] transducerType;
    private String[] signalPhysicalDimensions;
    private double[] signalPhysicalMin;
    private double[] signalPhysicalMax;
    private double[] signalDigitalMin;
    private double[] signalDigitalMax;
    private  String[] prefiltering;
    private String[] numSamples;
    private String filePath;
    private final HashMap<String, FieldDetails> headerData;
    
	public EDFHeaderWriter(String filePath, HashMap<String, Object> arguments) {
        this.filePath = filePath;
		// Need to update this with values from main
        this.version = "0";          // 8 bytes
        this.patientID = (String)arguments.get("SubjID"); // 80 bytes
        this.recordingID = "Recording001"; // 80 bytes, figure out what this is
        this.startDate = (String)arguments.get("StartDate");    // 8 bytes
        this.startTime = (String)arguments.get("StartTime");        // 8 bytes
        this.numRecords = (int)arguments.get("Recordsnum");      // 8 bytes
        this.NumSamples = (int)arguments.get("NumSamples");
        this.duration = (long)arguments.get("Duration");         // 8 bytes
        this.numSignals = (int)arguments.get("Signalnum");        // 4 byte
        this.numBytes = (numSignals*256) + 256;
        
        this.signalLabels = new String[this.numSignals]; // Signal labels
        this.signalPhysicalDimensions = new String[this.numSignals]; // Signal dimensions
        this.transducerType = new String[this.numSignals]; // Transducer type
        this.signalPhysicalMin = new double[this.numSignals]; // Physical min values
        this.signalPhysicalMax = new double[this.numSignals]; // Physical max values
        this.signalDigitalMin = new double[this.numSignals]; // Digital min values
        this.signalDigitalMax = new double[this.numSignals]; // Digital max values
        this.prefiltering = new String[this.numSignals]; // Prefiltering
        this.numSamples = new String[this.numSignals]; // Samples per signal

        ArrayList<String> labels = ((ArrayList<String>)arguments.get("ChannelNames"));
        ArrayList<Double> physicalmin = (ArrayList<Double>) arguments.get("Physicalmin");
        ArrayList<Double> physicalmax = (ArrayList<Double>) arguments.get("Physicalmax");

        // Need to update this too
        for (int i = 0; i < numSignals; i++) {
            signalLabels[i] = labels.get(i); // 16 bytes each
            signalPhysicalDimensions[i] = "uV"; // 8 bytes each
            signalPhysicalMin[i] = physicalmin.get(i); // min physical value
            signalPhysicalMax[i] = physicalmax.get(i); // max physical value
            signalDigitalMin[i] = (double)arguments.get("DigitalMin"); 
            signalDigitalMax[i] = (double)arguments.get("DigitalMax");
            transducerType[i] = "Unknown"; // No prefiltering
            prefiltering[i] = ""; // No prefiltering
            numSamples[i] = String.valueOf(NumSamples); // Number of samples in each record (4 bytes) 8 ascii spaces x num of samples
        }
        
        
        headerData = new HashMap<>();

        headerData.put("version", new FieldDetails(0, 8, String.class)); // 8 bytes
        headerData.put("patientID", new FieldDetails(8, 80, String.class)); // 80 bytes
        headerData.put("recordingID", new FieldDetails(88, 80, String.class)); // 80 bytes
        headerData.put("startDate", new FieldDetails(168, 8, String.class)); // 8 bytes
        headerData.put("startTime", new FieldDetails(176, 8, String.class)); // 8 bytes
        headerData.put("numRecords", new FieldDetails(184, 8, Integer.class)); // 8 bytes
        headerData.put("duration", new FieldDetails(192, 8, Long.class)); // 8 bytes
        headerData.put("numSignals", new FieldDetails(200, 4, Integer.class)); // 4 bytes
        headerData.put("numBytes", new FieldDetails(204, 8, Integer.class)); // 8 bytes
	}
	

	public int write(String EDFPath) {
	    // Prepare the header byte array
	    byte[] header = new byte[numBytes];
	    int offset = 0;

	    // Fill in the header fields
	    offset = writeStringToHeader(this.version, header, offset, 8);
	    offset = writeStringToHeader(this.patientID, header, offset, 80);
	    offset = writeStringToHeader(this.recordingID, header, offset, 80);
	    offset = writeStringToHeader(this.startDate, header, offset, 8);
	    offset = writeStringToHeader(this.startTime, header, offset, 8);
	    offset = writeStringToHeader(String.valueOf(this.numBytes), header, offset, 8);
	    offset = writeStringToHeader("", header, offset, 44);
	    offset = writeStringToHeader(String.valueOf(this.numRecords), header, offset, 8);
	    offset = writeStringToHeader(String.valueOf(this.duration), header, offset, 8);
	    offset = writeStringToHeader(String.valueOf(this.numSignals), header, offset, 4);

	    // Signal data fields
	    for (String label : signalLabels) offset = writeStringToHeader(label, header, offset, 16);
	    for (String type : transducerType) offset = writeStringToHeader(type, header, offset, 80);
	    for (String dim : signalPhysicalDimensions) offset = writeStringToHeader(dim, header, offset, 8);
	    for (double min : signalPhysicalMin) offset = writeStringToHeader(String.format("%.2f", min), header, offset, 8);
	    for (double max : signalPhysicalMax) offset = writeStringToHeader(String.format("%.2f", max), header, offset, 8);
	    for (double min : signalDigitalMin) offset = writeStringToHeader(String.format("%.0f", min), header, offset, 8);
	    for (double max : signalDigitalMax) offset = writeStringToHeader(String.format("%.0f", max), header, offset, 8);
	    for (String pre : prefiltering) offset = writeStringToHeader(pre, header, offset, 80);
	    for (String samples : numSamples) offset = writeStringToHeader(samples, header, offset, 8);
	    for (int i = 0; i < numSignals; i++) offset = writeStringToHeader("", header, offset, 32);  // Reserved

	    byte[] existingContent;
	    try (FileInputStream fis = new FileInputStream(EDFPath)) {
	        existingContent = fis.readAllBytes();
	    } catch (IOException e) {
	        System.err.println("Error reading existing file content: " + e.getMessage());
	        e.printStackTrace();
	        return -1;
	    }

	    // Combine header and existing content
	    byte[] combinedContent = new byte[header.length + existingContent.length];
	    System.arraycopy(header, 0, combinedContent, 0, header.length);
	    System.arraycopy(existingContent, 0, combinedContent, header.length, existingContent.length);

	    // Write combined content back to file
	    try (FileOutputStream out = new FileOutputStream(EDFPath)) {
	        out.write(combinedContent);
	        out.close();
	    } catch (IOException e) {
	        System.err.println("Error writing combined content to file: " + e.getMessage());
	        e.printStackTrace();
	        return -1;
	    }

	    System.out.println("EDF header written successfully. " + offset + " bytes written.");
	    return offset;  // Return total bytes written to header
	}

    

    // Helper method to write a string to the header byte array
    private static int writeStringToHeader(String str, byte[] header, int offset, int length) {
        byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
        int strLength = Math.min(strBytes.length, length);
        System.arraycopy(strBytes, 0, header, offset, strLength);
        // Fill the remaining space with spaces (if the string is shorter than the specified length)
        for (int i = strLength; i < length; i++) {
            header[offset + i] = (byte) ' ';
        }
        return offset + length;
    }
    
    // Experimental ...maybe use this for regular write. Not fully tested do not depend on
    public void updateHeader(String fieldName, Object newValue) throws IOException {
        FieldDetails fieldDetails = headerData.get(fieldName);

        if (fieldDetails == null) {
            throw new IllegalArgumentException("Field not found: " + fieldName);
        }

        // Prepare the header byte array
        byte[] header = new byte[numBytes];
        try (RandomAccessFile raf = new RandomAccessFile(this.filePath, "rw")) {
            raf.seek(0); // Seek to the start of the header
            raf.read(header); // Read the current header into the array

            // Update field
            String strValue = convertToString(newValue, fieldDetails.type);
            writeStringToHeader(strValue, header, fieldDetails.offset, fieldDetails.length);

            // Need to update header for these 2
            if (fieldName.equals("numSignals") || fieldName.equals("numRecords")) {
                // Update the total number of bytes in the header
                numBytes = header.length;
                writeStringToHeader(String.valueOf(numBytes), header, headerData.get("numBytes").offset, 8);
            }

            // re-write the header
            raf.seek(0); 
            raf.write(header); 
        }
    }

    // Convert types to strings
    private String convertToString(Object value, Class<?> type) {
        if (type == String.class) {
            return (String) value; 
        } else if (type == Integer.class) {
            return String.format("%d", (Integer) value); 
        } else if (type == Long.class) {
            return String.format("%d", (Long) value); 
        } else if (type == Double.class) {
            return String.format("%.2f", (Double) value);
        } else {
            throw new IllegalArgumentException("Unsupported field type: " + type);
        }
    }
    

}
