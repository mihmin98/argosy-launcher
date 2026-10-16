package android.util;

import java.io.PrintStream;

/**
 * Desktop stand-in for {@code android.util.Log}, carrying only the members upstream code calls.
 * Lines go to standard error in logcat's {@code L/tag: message} form, the desktop counterpart of
 * the system log.
 */
public final class Log {

    private Log() {}

    public static int d(String tag, String msg) {
        return println('D', tag, msg, null);
    }

    public static int d(String tag, String msg, Throwable tr) {
        return println('D', tag, msg, tr);
    }

    public static int i(String tag, String msg) {
        return println('I', tag, msg, null);
    }

    public static int i(String tag, String msg, Throwable tr) {
        return println('I', tag, msg, tr);
    }

    public static int w(String tag, String msg) {
        return println('W', tag, msg, null);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return println('W', tag, msg, tr);
    }

    public static int e(String tag, String msg) {
        return println('E', tag, msg, null);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return println('E', tag, msg, tr);
    }

    private static int println(char level, String tag, String msg, Throwable tr) {
        PrintStream err = System.err;
        String line = level + "/" + tag + ": " + msg;
        synchronized (err) {
            err.println(line);
            if (tr != null) tr.printStackTrace(err);
        }
        return line.length();
    }
}
