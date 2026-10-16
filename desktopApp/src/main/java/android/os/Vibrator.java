package android.os;

/**
 * Desktop stand-in for {@code android.os.Vibrator}, carrying only the members upstream code calls.
 * The desktop implementation subclasses it.
 */
public abstract class Vibrator {

    public abstract boolean hasVibrator();

    public abstract boolean hasAmplitudeControl();

    public abstract void vibrate(VibrationEffect vibe);

    public abstract void vibrate(VibrationEffect vibe, VibrationAttributes attributes);
}
