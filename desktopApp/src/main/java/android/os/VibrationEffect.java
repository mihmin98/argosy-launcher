package android.os;

/**
 * Desktop stand-in for {@code android.os.VibrationEffect}, carrying only the members upstream
 * code calls. Constants are the Android SDK's values; no effect exists on desktop.
 */
public final class VibrationEffect {

    public static final int DEFAULT_AMPLITUDE = -1;
    public static final int EFFECT_CLICK = 0;
    public static final int EFFECT_DOUBLE_CLICK = 1;
    public static final int EFFECT_TICK = 2;
    public static final int EFFECT_HEAVY_CLICK = 5;

    private VibrationEffect() {}

    public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
        throw new UnsupportedOperationException("desktop: VibrationEffect.createOneShot");
    }

    public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
        throw new UnsupportedOperationException("desktop: VibrationEffect.createWaveform");
    }

    public static VibrationEffect createPredefined(int effectId) {
        throw new UnsupportedOperationException("desktop: VibrationEffect.createPredefined");
    }
}
