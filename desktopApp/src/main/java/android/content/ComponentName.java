package android.content;

/**
 * Desktop stand-in for {@code android.content.ComponentName}, carrying only the members upstream
 * code calls. No instance exists on desktop.
 */
public final class ComponentName {

    private ComponentName() {}

    public String getShortClassName() {
        throw new UnsupportedOperationException("desktop: ComponentName.getShortClassName");
    }
}
