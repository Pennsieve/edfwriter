package edu.upenn.cis.eeg.mef.mefstreamer;


public class FieldDetails {
    public int offset;
    public int length;
    public Class<?> type;

    public FieldDetails(int offset, int length, Class<?> type) {
        this.offset = offset;
        this.length = length;
        this.type = type;
    }
}