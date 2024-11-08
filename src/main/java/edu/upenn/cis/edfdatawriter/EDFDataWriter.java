package edu.upenn.cis.edfdatawriter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

public class EDFDataWriter {
	
    private int numSignals;
    private int numSamplesPerSignal;
    private int numRecords;
    private int signalDataSize;
    private String File;
    
    public EDFDataWriter(String Input, HashMap<String, Object> arguments) {
    	this.File = Input;
        // Need to change this
        this.numSignals = (int)arguments.get("numSignals"); // Number of signals in the EDF file
        this.numSamplesPerSignal = 1000; // Number of samples per signal (per data record)
        this.numRecords = (int)arguments.get("Recordsnum");  // Number of records to write
        this.signalDataSize = 2; // 2 bytes for each 16-bit signed integer value
    	
    }

    public void write() {

        
        // Add actual data from main
        short[][] data = new short[numSignals][numSamplesPerSignal];
        for (int i = 0; i < numSignals; i++) {
            for (int j = 0; j < numSamplesPerSignal; j++) {
                data[i][j] = (short) (Math.random() * Short.MAX_VALUE); // Fill with dummy data
            }
        }

        // Open EDF file in append mode
        try (RandomAccessFile raf = new RandomAccessFile(this.File, "rw")) {
            // Seek to the point after the 256-byte header (typically starting at byte 256)
            // do not hard code this
        	raf.seek(512);

            // Write data records
            for (int record = 0; record < numRecords; record++) {
                writeDataRecord(raf, data, numSignals, numSamplesPerSignal, signalDataSize);
            }
            raf.close();

            System.out.println("Data records written successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    } 

    /**
     * Writes a single data record to the EDF file.
     */
    private static void writeDataRecord(RandomAccessFile raf, short[][] data, int numSignals, int numSamplesPerSignal, int signalDataSize) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(numSignals * numSamplesPerSignal * signalDataSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // Ensure data is written in little-endian byte order
        
        // Write the data for each signal in the record
        for (int i = 0; i < numSignals; i++) {
            for (int j = 0; j < numSamplesPerSignal; j++) {
                buffer.putShort(data[i][j]); // Write 16-bit signed integer data
            }
        }

        // Write the record to the file
        raf.write(buffer.array());
    }
}
