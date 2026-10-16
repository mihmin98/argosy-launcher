package android.view;

/**
 * Desktop stand-in for {@code android.view.KeyEvent}, carrying only the members upstream code
 * calls. Key codes are the Android SDK's values.
 */
public final class KeyEvent {

    public static final int KEYCODE_UNKNOWN = 0;
    public static final int KEYCODE_BUTTON_C = 98;
    public static final int KEYCODE_BUTTON_Z = 101;

    private KeyEvent() {}
}
