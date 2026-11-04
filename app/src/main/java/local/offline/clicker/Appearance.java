package local.offline.clicker;

/** Stored mode is independent of the system's current night setting. */
public final class Appearance {
    public static final int LIGHT=0,DARK=1,SYSTEM=2;
    public static boolean isDark(int mode,boolean systemDark){
        return mode==DARK||(mode==SYSTEM&&systemDark);
    }
}
