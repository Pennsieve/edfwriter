package edu.upenn.cis.eeg.mef.mefstreamer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import edu.upenn.cis.db.mefview.services.TimeSeriesPage;

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
	
	public void build() throws IOException {
		// Outputs: BIN files and JSON files

	    for (File inputFile : this.files) {
	        final String fileName = inputFile.getName();
	        final String baseName = (fileName.lastIndexOf('.') > 0)
	                ? fileName.substring(0, fileName.lastIndexOf('.'))
	                : fileName;

	        final Path outDir = inputFile.getParentFile().toPath(); 
	        final Path channelJsonPath = outDir.resolve(baseName + ".json");

	        try (RandomAccessFile raf = new RandomAccessFile(inputFile.getAbsolutePath(), "r")) {
	            // -- Open MEF and read header
	            MEFStreamer streamer = new MEFStreamer(raf);
	            MefHeader2 header = streamer.getMEFHeader();

	            // Header basics
	            final double rateHz = header.getSamplingFrequency();
	            final double periodUsExact = 1_000_000.0 / rateHz; 
	            final long tolUs = Math.max(2L, (long) Math.ceil(0.002 * periodUsExact)); // Reasonable gap

	            // Channel classification by name
	            String channelName = baseName;
	            String lower = channelName.toLowerCase(Locale.ROOT);
	            String typeStr, description;
	            if (lower.contains("grid")) { typeStr = "ECOG"; description = "Electrocorticography"; }
	            else if (lower.contains("seeg") || lower.contains("depth")) { typeStr = "SEEG"; description = "Stereoelectroencephalography"; }
	            else if (lower.contains("eeg")) { typeStr = "EEG"; description = "Electroencephalography"; }
	            else if (lower.contains("eog")) { typeStr = "EOG"; description = "Electrooculography"; }
	            else if (lower.contains("ecg") || lower.contains("ekg")) { typeStr = "ECG"; description = "Electrocardiography"; }
	            else if (lower.contains("emg")) { typeStr = "EMG"; description = "Electromyography"; }
	            else { typeStr = "Unknown"; description = "Unknown signal type"; }

	            // Filters (can be -1 if unset)
	            final double lowCut = header.getLowFrequencyFilterSetting();
	            final double highCut = header.getHighFrequencyFilterSetting();

	            // Totals (reported)
	            final long reportedStartUs = header.getRecordingStartTime();
	            final long reportedEndUs = header.getRecordingEndTime();
	            final long totalBlocks = streamer.getNumberOfBlocks();

	            System.out.println("Processing channel: " + channelName);
	            System.out.println("Rate: " + rateHz + " Hz, Blocks: " + totalBlocks);

	            // iterate pages, detect discontinuities, write one BIN per contiguous segment

	            // Segment state
	            List<SegmentMeta> segments = new ArrayList<>();
	            int segIndex = -1;
	            long segStartUs = 0L;                 // epoch µs of first sample in current segment
	            long segSamples = 0L;                 // samples written in current segment
	            OutputStream segOut = null;           // stream for current segment BIN
	            ByteBuffer le64 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN); // for little-endian float64 writes

	            // Absolute channel bounds we actually observed
	            Long observedStartUs = null;
	            long observedEndUs = Long.MIN_VALUE;  // last-sample timestamp across the channel

	            // Helper to open a new segment (on first page or after a gap)
	            final SegOpenCloser segIO = new SegOpenCloser(outDir, baseName);

	            // Running expected start = segStartUs + round(segSamples * periodUsExact)
	            // This avoids cumulative float rounding page-to-page.
	            for (TimeSeriesPage page : streamer.getNextBlocks((int) totalBlocks)) {
	                final long pageStartUs = page.timeStart;
	                final int n = page.values.length;

	                if (observedStartUs == null) {
	                    // First page in channel: set observed start
	                    observedStartUs = pageStartUs;
	                }

	                // If we have an open segment, check contiguity; otherwise open a new one.
	                if (segOut == null) {
	                    // Open first segment
	                    segIndex += 1;
	                    segStartUs = pageStartUs;
	                    segSamples = 0L;
	                    segOut = segIO.open(segIndex);
	                } else {
	                    final long expectedNextStartUs = segStartUs + Math.round(segSamples * periodUsExact);
	                    final long delta = Math.abs(pageStartUs - expectedNextStartUs);

	                    if (delta > tolUs) {
	                        // --- Discontinuity detected: close previous segment and start a new one ---
	                        final long segLastSampleUs = segStartUs + Math.round((segSamples - 1) * periodUsExact);
	                        segIO.close(); // close previous BIN
	                        segments.add(new SegmentMeta(segIndex, segStartUs, segLastSampleUs,
	                                                     segSamples, segIO.fileName(segIndex)));

	                        // Start new segment at this page
	                        segIndex += 1;
	                        segStartUs = pageStartUs;
	                        segSamples = 0L;
	                        segOut = segIO.open(segIndex);
	                    }
	                }

	                // --- Stream page values as LITTLE-ENDIAN float64 ---
	                // Base case: you believe values are µV already. If they are COUNTS, apply scaling here.
	                final OutputStream os = segOut;
	                for (int v : page.values) {
	                    le64.clear();
	                    le64.putDouble((double) v);
	                    os.write(le64.array());
	                }

	                // Update running segment sample count
	                segSamples += n;

	                // Update observed channel end (last-sample timestamp after this page)
	                final long pageLastSampleUs = pageStartUs + Math.round((long) (n - 1) * periodUsExact);
	                if (pageLastSampleUs > observedEndUs) {
	                    observedEndUs = pageLastSampleUs;
	                }
	            }

	            // Close the final open segment, if any
	            if (segOut != null) {
	                final long segLastSampleUs = segStartUs + Math.round((segSamples - 1) * periodUsExact);
	                segIO.close();
	                segments.add(new SegmentMeta(segIndex, segStartUs, segLastSampleUs, segSamples, segIO.fileName(segIndex)));
	            }

	            // Sanity: if no pages were present
	            if (observedStartUs == null) {
	                System.err.println("Warning: channel " + channelName + " has no pages/samples.");
	                continue;
	            }

	            // Optional consistency check vs header (don’t fail; log if wildly off)
	            if (Math.abs(observedStartUs - reportedStartUs) > 10_000) { // >10 ms difference
	                System.out.println("Note: observed start (" + observedStartUs + ") != header start (" + reportedStartUs + ")");
	            }
	            if (reportedEndUs > 0 && Math.abs(observedEndUs - reportedEndUs) > 10_000) {
	                System.out.println("Note: observed end (" + observedEndUs + ") != header end (" + reportedEndUs + ")");
	            }

	            // ---- Write per-channel JSON metadata ----
	            writeChannelJson(channelJsonPath, channelName, typeStr, description,
	                             rateHz, lowCut, highCut, observedStartUs, observedEndUs, segments);

	            System.out.println("Wrote " + segments.size() + " segment(s) and JSON for " + channelName);
	        }
	    }
	}

	/** Holds one segment’s bookkeeping for JSON. */
	static final class SegmentMeta {
	    final int index;
	    final long startUs;
	    final long endUs;       // last-sample timestamp
	    final long nSamples;
	    final String dataPath;  // filename only; relative to channel directory

	    SegmentMeta(int index, long startUs, long endUs, long nSamples, String dataPath) {
	        this.index = index;
	        this.startUs = startUs;
	        this.endUs = endUs;
	        this.nSamples = nSamples;
	        this.dataPath = dataPath;
	    }
	}

	/** Small helper to manage opening/closing segment BIN files with stable names. */
	static final class SegOpenCloser {
	    private final Path outDir;
	    private final String baseName;
	    private OutputStream current;

	    SegOpenCloser(Path outDir, String baseName) {
	        this.outDir = outDir;
	        this.baseName = baseName;
	    }

	    OutputStream open(int segIndex) throws IOException {
	        close();
	        Path p = outDir.resolve(fileName(segIndex));
	        return (current = new BufferedOutputStream(Files.newOutputStream(p)));
	    }

	    void close() throws IOException {
	        if (current != null) {
	            current.flush();
	            current.close();
	            current = null;
	        }
	    }

	    String fileName(int segIndex) {
	        return String.format("%s_seg%03d.bin", baseName, segIndex);
	    }
	}

	/** Writes the per-channel JSON. No external deps; simple, readable JSON. */
	static void writeChannelJson(Path out, String name, String type, String desc,
	                             double rateHz, double lowCut, double highCut,
	                             long absStartUs, long absEndUs, List<SegmentMeta> segments) throws IOException {
	    StringBuilder sb = new StringBuilder(1024);
	    sb.append("{\n");
	    sb.append("  \"name\": ").append(json(name)).append(",\n");
	    sb.append("  \"type\": ").append(json(type)).append(",\n");
	    sb.append("  \"description\": ").append(json(desc)).append(",\n");
	    sb.append("  \"unit\": ").append(json("uV (assumed)")).append(",\n"); // TODO: set to "counts" if not scaled
	    sb.append("  \"low_cut_hz\": ").append(lowCut).append(",\n");
	    sb.append("  \"high_cut_hz\": ").append(highCut).append(",\n");
	    sb.append("  \"rate_hz\": ").append(rateHz).append(",\n");
	    sb.append("  \"absolute_start_us\": ").append(absStartUs).append(",\n");
	    sb.append("  \"absolute_end_us\": ").append(absEndUs).append(",\n");
	    sb.append("  \"segments\": [\n");
	    for (int i = 0; i < segments.size(); i++) {
	        SegmentMeta s = segments.get(i);
	        sb.append("    {")
	          .append("\"index\": ").append(s.index).append(", ")
	          .append("\"start_us\": ").append(s.startUs).append(", ")
	          .append("\"end_us\": ").append(s.endUs).append(", ")
	          .append("\"n_samples\": ").append(s.nSamples).append(", ")
	          .append("\"data_path\": ").append(json(s.dataPath))
	          .append("}");
	        if (i + 1 < segments.size()) sb.append(",");
	        sb.append("\n");
	    }
	    sb.append("  ]\n");
	    sb.append("}\n");

	    Files.write(out, sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/** Minimal JSON string escaper & quoter. */
	static String json(String s) {
	    StringBuilder sb = new StringBuilder(s.length() + 2);
	    sb.append('"');
	    for (int i = 0; i < s.length(); i++) {
	        char c = s.charAt(i);
	        switch (c) {
	            case '"':  sb.append("\\\""); break;
	            case '\\': sb.append("\\\\"); break;
	            case '\b': sb.append("\\b");  break;
	            case '\f': sb.append("\\f");  break;
	            case '\n': sb.append("\\n");  break;
	            case '\r': sb.append("\\r");  break;
	            case '\t': sb.append("\\t");  break;
	            default:
	                if (c < 0x20) sb.append(String.format("\\u%04x", (int)c));
	                else sb.append(c);
	        }
	    }
	    sb.append('"');
	    return sb.toString();
	}

}
                	

