package android.content;

import java.io.File;

/**
 * Desktop stand-in for {@code android.content.Context}, carrying only the members upstream
 * code calls. The desktop implementation subclasses it.
 */
public abstract class Context {

    public abstract Context getApplicationContext();

    public abstract File getFilesDir();

    public abstract <T> T getSystemService(Class<T> serviceClass);
}
