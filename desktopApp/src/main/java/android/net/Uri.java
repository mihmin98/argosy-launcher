package android.net;

/**
 * Desktop stand-in for {@code android.net.Uri}, carrying only the members upstream code calls.
 * No instance exists on desktop.
 */
public final class Uri {

    private Uri() {}

    public String getScheme() {
        throw new UnsupportedOperationException("desktop: Uri.getScheme");
    }

    public String getPath() {
        throw new UnsupportedOperationException("desktop: Uri.getPath");
    }
}
