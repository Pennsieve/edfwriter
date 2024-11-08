package edfheaderwriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;



//import edu.upenn.cis.eeg.mef.mefstreamer.MefHeader2;

public class EDFHeaderWriter {
	
    private String version;
    private String patientID;
    private String recordingID;
    private String startDate;
    private String startTime;
    private int numRecords;
    private long duration;
    private int numSignals;
    private int numBytes;
    
    
    private String[] signalLabels;
    private String[] signalPhysicalDimensions;
    private double[] signalPhysicalMin;
    private double[] signalPhysicalMax;
    private double[] signalDigitalMin;
    private double[] signalDigitalMax;
    private  String[] prefiltering;
    private String[] numSamples;
    
	public EDFHeaderWriter(RandomAccessFile Input, HashMap<String, Object> arguments) {
        // Need to update this with values from main
        this.version = "0";          // 8 bytes
        this.patientID = (String)arguments.get("SubjID"); // 80 bytes
        this.recordingID = "Recording001"; // 80 bytes, figure out what this is
        this.startDate = (String)arguments.get("StartDate");    // 8 bytes
        this.startTime = (String)arguments.get("StartTime");        // 8 bytes
        this.numRecords = (int)arguments.get("Recordsnum");      // 8 bytes
        this.duration = (long)arguments.get("Duration");         // 8 bytes
        this.numSignals = (int)arguments.get("Signalnum");        // 4 bytes
        this.numBytes = (numSignals*256) + 256;
        
        
        this.signalLabels = new String[32]; // Signal labels
        this.signalPhysicalDimensions = new String[32]; // Signal dimensions
        this.signalPhysicalMin = new double[32]; // Physical min values
        this.signalPhysicalMax = new double[32]; // Physical max values
        this.signalDigitalMin = new double[32]; // Digital min values
        this.signalDigitalMax = new double[32]; // Digital max values
        this.prefiltering = new String[32]; // Prefiltering
        this.numSamples = new String[32]; // Samples per signal

        ArrayList<String> labels = ((ArrayList<String>)arguments.get("ChannelNames"));
        // Need to update this too
        for (int i = 0; i < numSignals; i++) {
            signalLabels[i] = labels.get(i); // 16 bytes each
            signalPhysicalDimensions[i] = "uV      "; // 8 bytes each
            signalPhysicalMin[i] = (double)arguments.get("Physicalmin"); // min physical value
            signalPhysicalMax[i] = (double)arguments.get("Physicalmax"); // max physical value
            signalDigitalMin[i] = -32768.0; // min digital value
            signalDigitalMax[i] = 32767.0; // max digital value
            prefiltering[i] = ""; // No prefiltering
            numSamples[i] = String.valueOf(numSignals); // Number of samples in each record (4 bytes) 8 ascii spaces x num of samples
        }
	
	}
	

    public void write() {

        // Prepare the header byte array
        byte[] header = new byte[numBytes];
        int offset = 0;
        
        
        // Fill in the header fields
        offset = writeStringToHeader(this.version, header, offset, 8);
        offset = writeStringToHeader(this.patientID, header, offset, 80);
        offset = writeStringToHeader(this.recordingID, header, offset, 80);
        offset = writeStringToHeader(this.startDate, header, offset, 8);
        offset = writeStringToHeader(this.startTime, header, offset, 8);
        offset = writeStringToHeader("",header,offset,44);
        offset = writeStringToHeader(String.valueOf(this.numBytes), header, offset, 8);
        offset = writeStringToHeader(String.valueOf(this.numRecords), header, offset, 8);
        offset = writeStringToHeader(String.valueOf(this.duration), header, offset, 8);
        offset = writeStringToHeader(String.valueOf(this.numSignals), header, offset, 4);
        

        // Signal data: each signal has a specific number of bytes to be written
        for (int i = 0; i < numSignals; i++) {
            offset = writeStringToHeader(signalLabels[i], header, offset, 16);
            offset = writeStringToHeader("", header, offset, 80); //figure out electrode type
            offset = writeStringToHeader(signalPhysicalDimensions[i], header, offset, 8);
            offset = writeStringToHeader(String.format("%f", signalPhysicalMin[i]), header, offset, 8);
            offset = writeStringToHeader(String.format("%f", signalPhysicalMax[i]), header, offset, 8);
            System.out.println("Error2: "+i);
            offset = writeStringToHeader(String.format("%f", signalDigitalMin[i]), header, offset, 8);
            offset = writeStringToHeader(String.format("%f", signalDigitalMax[i]), header, offset, 8);
            offset = writeStringToHeader(prefiltering[i], header, offset, 80);
            offset = writeStringToHeader(numSamples[i], header, offset, 8);
            offset = writeStringToHeader("", header, offset, 32);
            System.out.println("Error3: ");
        }

        // Reserved: fill the last section with spaces
        //offset = writeStringToHeader("", header, offset, this.numSignals);

        // Write header to file
        try (FileOutputStream out = new FileOutputStream("/Users/juliadengler/Documents/output.edf")) {
            out.write(header);
            System.out.println("fileoutputstream");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("EDF header written successfully.");
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
}
