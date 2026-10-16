package android.content;

import android.net.Uri;
import android.os.Bundle;

/**
 * Desktop stand-in for {@code android.content.Intent}, carrying only the members upstream code
 * calls. No instance exists on desktop.
 */
public final class Intent {

    private Intent() {}

    public String getAction() {
        throw new UnsupportedOperationException("desktop: Intent.getAction");
    }

    public String getPackage() {
        throw new UnsupportedOperationException("desktop: Intent.getPackage");
    }

    public ComponentName getComponent() {
        throw new UnsupportedOperationException("desktop: Intent.getComponent");
    }

    public Uri getData() {
        throw new UnsupportedOperationException("desktop: Intent.getData");
    }

    public Bundle getExtras() {
        throw new UnsupportedOperationException("desktop: Intent.getExtras");
    }
}
