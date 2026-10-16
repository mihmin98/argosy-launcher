package android.content.res;

/**
 * Desktop stand-in for {@code android.content.res.Resources}, carrying only the members upstream
 * code calls. The desktop implementation subclasses it.
 */
public abstract class Resources {

    public abstract String getString(int id);

    public abstract String getString(int id, Object... formatArgs);

    public abstract String getQuantityString(int id, int quantity);

    public abstract String getQuantityString(int id, int quantity, Object... formatArgs);
}
