package doom.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;

import java.io.File;


public class DoomTools {

    public static final boolean FORCE_USE_WAD_FROM_ASSETS = true;

    static public String GetSDCARD(Context context) {
        return context.getFilesDir().getPath();
    }

    static public String GetWorkingFolder() {
        return "DVR";
    }

    static public String GetDVRFolder(Context context) {
        return GetSDCARD(context) + File.separator + GetWorkingFolder();
    }

    // Game files we can handle
    public static final String[] DOOM_WADS =
            {"freedoom.wad", "freedoom1.wad", "freedoom2.wad", "freedm.wad", "doom.wad", "doom2.wad"};

    // These are required for the game to run
    public static final String REQUIRED_DOOM_WAD = "prboom.wad";

    /*
     * ASCII key symbols
     */
    public static final int KEY_RIGHTARROW = 0xae;
    public static final int KEY_LEFTARROW = 0xac;
    public static final int KEY_UPARROW = 0xad;
    public static final int KEY_DOWNARROW = 0xaf;
    public static final int KEY_ESCAPE = 27;
    public static final int KEY_ENTER = 13;
    public static final int KEY_TAB = 9;

    public static final int KEY_BACKSPACE = 127;
    public static final int KEY_PAUSE = 0xff;

    public static final int KEY_EQUALS = 0x3d;
    public static final int KEY_MINUS = 0x2d;

    public static final int KEY_RSHIFT = (0x80 + 0x36);
    public static final int KEY_RCTRL = (0x80 + 0x1d);
    public static final int KEY_RALT = (0x80 + 0x38);

    public static final int KEY_LALT = KEY_RALT;
    public static final int KEY_SPACE = 32;
    public static final int KEY_COMMA = 44;
    public static final int KEY_FULLSTOP = 46;

    public static final int KEY_W = 119;
    public static final int KEY_A = 97;
    public static final int KEY_S = 115;
    public static final int KEY_D = 100;

    public static final int KEY_0 = 48;
    public static final int KEY_1 = 49;
    public static final int KEY_2 = 50;
    public static final int KEY_3 = 51;
    public static final int KEY_4 = 52;
    public static final int KEY_5 = 53;
    public static final int KEY_6 = 54;
    public static final int KEY_7 = 55;
    public static final int KEY_8 = 56;
    public static final int KEY_9 = 57;

    public static final int KEY_Y = 121;
    public static final int KEY_N = 110;
    public static final int KEY_Z = 122;

    static boolean result;
    static ProgressDialog mProgressDialog;


    /**
     * Convert an android key to a Doom ASCII
     *
     */
    static public int keyCodeToKeySym(int key) {
        switch (key) {

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (Natives.isMapShowing() == 0 || Natives.isMenuShowing() != 0)
                    return KEY_A;
                else
                    return 0xac;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (Natives.isMapShowing() == 0 || Natives.isMenuShowing() != 0)
                    return KEY_D;
                else
                    return 0xae;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (Natives.isMapShowing() == 0 || Natives.isMenuShowing() != 0)
                    return KEY_W;
                else
                    return 0xad;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (Natives.isMapShowing() == 0 || Natives.isMenuShowing() != 0)
                    return KEY_S;
                else
                    return 0xaf;

            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return 0x69;

            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                return 0x6f;

            // Left
            case 84: // SYM
                return KEY_LEFTARROW;

            // Right
            case KeyEvent.KEYCODE_AT:
                return KEY_RIGHTARROW;

            // Up
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                return KEY_UPARROW;

            // Down
            case KeyEvent.KEYCODE_ALT_LEFT:
                return KEY_DOWNARROW;

            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return KEY_ENTER;

            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_B:
                return KEY_SPACE;

            case 4:    // ESC
                return KEY_ESCAPE;

            case KeyEvent.KEYCODE_BUTTON_Y:
                //Weapon Toggle
                return KEY_0;

            // Doom Map
            case KeyEvent.KEYCODE_ALT_RIGHT:
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_BUTTON_X:
                return KEY_TAB;

            // Strafe left
            case KeyEvent.KEYCODE_COMMA:
                return KEY_COMMA;

            // Strafe right
            case KeyEvent.KEYCODE_PERIOD:
                return KEY_FULLSTOP;

            case KeyEvent.KEYCODE_DEL:
                return KEY_BACKSPACE;

            case KeyEvent.KEYCODE_BUTTON_R1:
                return KEY_RCTRL;

            case KeyEvent.KEYCODE_BUTTON_L1:
                return KEY_RSHIFT;

            default:
                // A..Z
                if (key >= 29 && key <= 54) {
                    key += 68;
                }
                // 0..9
                else if (key >= 7 && key <= 16) {
                    key += 41;
                }
                break;
        }
        return key;
    }

    static public boolean wadsExist(Context context) {
        for (String doomWad : DOOM_WADS) {
            File f = new File(GetDVRFolder(context) + File.separator + doomWad);
            if (f.exists())
                return true;
        }
        return false;
    }

    /**
     * Get the sound folder name for a game file
     */
    public static File GetSoundFolder(Context context) {
        return new File(GetDVRFolder(context) + File.separator + "sound");
    }

    static final String TAG = "DoomTools";

    /**
     * Clean sounds
     */
    public static void deleteSounds(Context context) //int wadIdx)
    {
        File folder = GetSoundFolder(context);

        if (!folder.exists()) {
            Log.e(TAG, "Error: Sound folder " + folder + " not found.");
            return;
        }

        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {

                if (file.exists())
                    file.delete();
            }
        }
    }
}
