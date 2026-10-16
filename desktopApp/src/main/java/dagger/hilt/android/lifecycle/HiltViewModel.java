package dagger.hilt.android.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Desktop stand-in for Hilt's {@code @HiltViewModel}. {@code scripts/desktop/gen-vm-module.py}
 * binds every compiled class carrying it into {@code DesktopViewModelModule}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface HiltViewModel {}
