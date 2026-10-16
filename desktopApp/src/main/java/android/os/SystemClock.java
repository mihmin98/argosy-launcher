package android.os;

/**
 * Desktop stand-in for {@code android.os.SystemClock}, carrying only the members upstream code
 * calls. Times are milliseconds on the JVM's monotonic clock.
 */
public final class SystemClock {

    private SystemClock() {}

    public static long uptimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }
}
