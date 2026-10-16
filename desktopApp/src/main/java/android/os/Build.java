package android.os;

/**
 * Desktop stand-in for {@code android.os.Build}, carrying only the members upstream code calls.
 * {@link VERSION#SDK_INT} is 0: every upstream API-level gate takes its oldest path.
 */
public final class Build {

    private Build() {}

    public static final class VERSION {

        public static final int SDK_INT = 0;

        private VERSION() {}
    }

    public static final class VERSION_CODES {

        public static final int Q = 29;
        public static final int S = 31;
        public static final int TIRAMISU = 33;

        private VERSION_CODES() {}
    }
}
