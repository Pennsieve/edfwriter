package edu.upenn.cis.edfdatawriter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

public class EDFDataWriter {
	
    private int numSignals; // Number of channels (# Mef files)
    private int numSamplesPerSignal;
    private int numRecords;
    private int signalDataSize;
    private String File;
    
    public EDFDataWriter(String Input, HashMap<String, Object> arguments) {
    	this.File = Input;
        // Need to change this
        this.numSignals = (int)arguments.get("Signalnum"); // Number of signals in the EDF file
        this.numSamplesPerSignal = (int)arguments.get("Recordsnum"); // Number of samples per signal (per data record)
        this.numRecords = (int)arguments.get("Recordsnum");  // Number of records to write
        this.signalDataSize = 2; // 2 bytes for each 16-bit signed integer value
    	
    }

    public void write(int offset, int[] values) {
        // Create a single-dimensional array for signal data
        short[] data = new short[values.length];

        // Convert int values to short and store them in the data array
        for (int i = 0; i < values.length; i++) {
            data[i] = (short) values[i];  // Convert int to short (clipping may happen here if values are too large)
        }

        // Open EDF file in append mode
        try (RandomAccessFile raf = new RandomAccessFile(this.File, "rw")) {
            // Seek to the point after the 256-byte header (don't hard-code this)
            raf.seek(offset);

            // Write data records (we only need to write one record here because we are working with a flat data array)
            writeDataRecord(raf, data, values.length); // Pass the flat data array
            raf.close();

            //System.out.println("Data records written successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Writes a single data record to the EDF file using a single-dimensional array.
     */
    private static void writeDataRecord(RandomAccessFile raf, short[] data, int numSamples) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(numSamples * Short.BYTES); // numSamples * size of short
        buffer.order(ByteOrder.LITTLE_ENDIAN); // Ensure data is written in little-endian byte order

        // Write the data into the buffer (no nested loops needed for a single array)
        for (int i = 0; i < numSamples; i++) {
            buffer.putShort(data[i]); // Write 16-bit signed integer data (short)
        }

        // Write the record to the file
        raf.write(buffer.array());
    }

    
    public int calculateRecordSize() {
        return numSignals * numSamplesPerSignal * signalDataSize;
    }
}
