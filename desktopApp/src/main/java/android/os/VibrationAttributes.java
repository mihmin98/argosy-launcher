package android.os;

/**
 * Desktop stand-in for {@code android.os.VibrationAttributes}, carrying only the members upstream
 * code calls. Constants are the Android SDK's values; no attributes exist on desktop.
 */
public final class VibrationAttributes {

    public static final int USAGE_TOUCH = 18;

    private VibrationAttributes() {}

    public static VibrationAttributes createForUsage(int usage) {
        throw new UnsupportedOperationException("desktop: VibrationAttributes.createForUsage");
    }
}
