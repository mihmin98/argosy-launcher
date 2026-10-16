package android.os;

/**
 * Desktop stand-in for {@code android.os.VibratorManager}, carrying only the members upstream
 * code calls. The desktop implementation subclasses it.
 */
public abstract class VibratorManager {

    public abstract Vibrator getDefaultVibrator();
}
