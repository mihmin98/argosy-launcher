package android.content;

import android.content.res.Resources;
import java.io.File;

/**
 * Desktop stand-in for {@code android.content.Context}, carrying only the members upstream
 * code calls. The desktop implementation subclasses it.
 */
public abstract class Context {

    public abstract Context getApplicationContext();

    public abstract File getFilesDir();

    public abstract Resources getResources();

    public abstract <T> T getSystemService(Class<T> serviceClass);

    public final String getString(int resId) {
        return getResources().getString(resId);
    }

    public final String getString(int resId, Object... formatArgs) {
        return getResources().getString(resId, formatArgs);
    }
}
