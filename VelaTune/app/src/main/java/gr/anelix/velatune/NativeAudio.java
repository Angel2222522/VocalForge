package gr.anelix.velatune;
public final class NativeAudio {
    static {
        System.loadLibrary("velatune");
    }
    private NativeAudio() {
    }
    public static native int start();
    public static native void stop();
    public static native void release();
    public static native void configure(float[] p,boolean monitor,boolean record);
    public static native float[] stats();
    public static native int drain(float[] samples);
    public static native long createProcessor(int rate,float[] p);
    public static native int processorDelay(long handle);
    public static native void process(long handle,float[] samples,int count);
    public static native void destroyProcessor(long handle);
}
