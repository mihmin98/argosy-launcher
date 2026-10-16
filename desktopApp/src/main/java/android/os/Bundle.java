package android.os;

import java.util.Set;

/**
 * Desktop stand-in for {@code android.os.Bundle}, carrying only the members upstream code calls.
 * No instance exists on desktop.
 */
public final class Bundle {

    private Bundle() {}

    public boolean isEmpty() {
        throw new UnsupportedOperationException("desktop: Bundle.isEmpty");
    }

    public Set<String> keySet() {
        throw new UnsupportedOperationException("desktop: Bundle.keySet");
    }
}
