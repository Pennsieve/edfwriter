package edfheaderwriter;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class EDFHeaderWriter {

    public static void main(String[] args) {
        // Example information for the EDF header
        String version = "0       ";          // 8 bytes
        String patientID = "Patient001       "; // 80 bytes
        String recordingID = "Recording001     "; // 80 bytes
        String startDate = "2024-11-05";      // 8 bytes
        String startTime = "12:34:56";        // 8 bytes
        String numRecords = "10000   ";       // 8 bytes
        String duration = "1       ";         // 8 bytes
        String numSignals = "32    ";         // 4 bytes
        
        String[] signalLabels = new String[32]; // Signal labels
        String[] signalPhysicalDimensions = new String[32]; // Signal dimensions
        double[] signalPhysicalMin = new double[32]; // Physical min values
        double[] signalPhysicalMax = new double[32]; // Physical max values
        double[] signalDigitalMin = new double[32]; // Digital min values
        double[] signalDigitalMax = new double[32]; // Digital max values
        String[] prefiltering = new String[32]; // Prefiltering
        String[] numSamples = new String[32]; // Samples per signal

        // Fill example data for signals (for illustration purposes)
        for (int i = 0; i < 32; i++) {
            signalLabels[i] = String.format("Signal%d    ", i+1); // 16 bytes each
            signalPhysicalDimensions[i] = "uV      "; // 8 bytes each
            signalPhysicalMin[i] = -100.0; // min physical value
            signalPhysicalMax[i] = 100.0; // max physical value
            signalDigitalMin[i] = -32768.0; // min digital value
            signalDigitalMax[i] = 32767.0; // max digital value
            prefiltering[i] = ""; // No prefiltering
            numSamples[i] = "1000    "; // Number of samples in each record (4 bytes)
        }

        // Prepare the header byte array
        byte[] header = new byte[256];
        int offset = 0;

        // Fill in the header fields
        offset = writeStringToHeader(version, header, offset, 8);
        offset = writeStringToHeader(patientID, header, offset, 80);
        offset = writeStringToHeader(recordingID, header, offset, 80);
        offset = writeStringToHeader(startDate, header, offset, 8);
        offset = writeStringToHeader(startTime, header, offset, 8);
        offset = writeStringToHeader(numRecords, header, offset, 8);
        offset = writeStringToHeader(duration, header, offset, 8);
        offset = writeStringToHeader(numSignals, header, offset, 4);

        // Signal data: each signal has a specific number of bytes to be written
        for (int i = 0; i < 32; i++) {
            offset = writeStringToHeader(signalLabels[i], header, offset, 16);
            offset = writeStringToHeader(signalPhysicalDimensions[i], header, offset, 8);
            offset = writeStringToHeader(String.format("%f", signalPhysicalMin[i]), header, offset, 8);
            offset = writeStringToHeader(String.format("%f", signalPhysicalMax[i]), header, offset, 8);
            offset = writeStringToHeader(String.format("%f", signalDigitalMin[i]), header, offset, 8);
            offset = writeStringToHeader(String.format("%f", signalDigitalMax[i]), header, offset, 8);
            offset = writeStringToHeader(prefiltering[i], header, offset, 80);
            offset = writeStringToHeader(numSamples[i], header, offset, 4);
        }

        // Reserved: fill the last section with spaces
        offset = writeStringToHeader(" ", header, offset, 32);

        // Write header to file
        try (FileOutputStream out = new FileOutputStream("output.edf")) {
            out.write(header);
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
