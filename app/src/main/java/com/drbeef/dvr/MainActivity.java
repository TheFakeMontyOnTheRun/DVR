package com.drbeef.dvr;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.Window;
import android.view.WindowManager;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardDeviceParams;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.ScreenParams;
import com.google.vrtoolkit.cardboard.Viewport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;

import doom.audio.AudioManager;
import doom.util.DoomTools;
import doom.util.Natives;


public class MainActivity
        extends CardboardActivity
        implements CardboardView.StereoRenderer, Natives.EventListener
{
    private static final String TAG = "DVR";

    OpenGL openGL = null;

    // Audio Cache Manager
    private AudioManager mAudioMgr;
    private Bitmap mDoomBitmap;

    // width of mBitmap
    private int mDoomWidth;
    // height of mBitmap
    private int mDoomHeight;

    //Head orientation
    private float[] eulerAngles = new float[3];
    private float hmdYaw;
    private float hmdPitch;
    private float hmdRoll;

    //-1 means start button isn't pressed
    private long startButtonDownCounter = -1;
    //Don't allow the trigger to fire more than once per 200ms
    private long triggerTimeout = 0;

    private Vibrator vibrator;
    private float M_PI = 3.14159265358979323846f;

    //Read these from a file and pass through
    String commandLineParams = new String("");

    private CardboardView cardboardView;

    private DownloadTask mDownloadTask = null;
    private WADChooser  mWADChooser = null;

    public static boolean mDVRInitialised = false;

    //Can't rebuild eye buffers until surface changed flag recorded
    public static boolean mSurfaceChanged = false;

    float lensCentreOffset = -1.0f;

    private boolean mShowingSpashScreen = true;
    private int[] splashTexture = new int[1];
    private MediaPlayer mPlayer;
    private int mPlayerVolume = 100;


    static {
        try {
            Log.i("JNI", "Trying to load libdvr.so");
            System.loadLibrary("dvr");
        } catch (UnsatisfiedLinkError ule) {
            Log.e("JNI", "WARNING: Could not load libdvr.so");
        }
    }

    public void copy_asset(String name, String folder) {
        File f = new File(folder + name);
        if (!f.exists()) {
            //Ensure we have an appropriate folder
            new File(folder).mkdirs();
            _copy_asset(name, folder + name);
        }
    }

    public void _copy_asset(String name_in, String name_out) {
        AssetManager assets = this.getAssets();

        try {
            InputStream in = assets.open(name_in);
            OutputStream out = new FileOutputStream(name_out);

            copy_stream(in, out);

            out.close();
            in.close();

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public static void copy_stream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buf = new byte[512];
        while (true) {
            int count = in.read(buf);
            if (count <= 0)
                break;
            out.write(buf, 0, count);
        }
    }

    public void startDownload()
    {
        mDownloadTask = new DownloadTask();
        mDownloadTask.set_context(MainActivity.this);
        mDownloadTask.execute();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setEGLConfigChooser(5, 6, 5, 0, 16, 0);
        cardboardView.setLowLatencyModeEnabled(true);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        openGL = new OpenGL();
        openGL.onCreate();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        //At the very least ensure we have a directory containing a config file
        copy_asset("DVR.cfg", DoomTools.GetDVRFolder() + File.separator);
        copy_asset("prboom.wad", DoomTools.GetDVRFolder() + File.separator);
        copy_asset("extraparams.txt", DoomTools.GetDVRFolder() + File.separator);

        File folder = new File(DoomTools.GetDVRFolder() + File.separator + "sound" + File.separator);
        if(!folder.exists())
            folder.mkdirs();

        //Clean up sound folder
        DoomTools.deleteSounds();

        //See if user is trying to use command line params
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(DoomTools.GetDVRFolder() + "extraparams.txt"));
            String s;
            StringBuilder sb=new StringBuilder(0);
            while ((s=br.readLine())!=null)
                sb.append(s + " ");
            br.close();

            commandLineParams = new String(sb.toString());
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (!DoomTools.wadsExist()) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    this);

            // set title
            alertDialogBuilder.setTitle("No WAD files found");

            // set dialog message
            alertDialogBuilder
                    .setMessage("Would you like to download the free Freedoom WADs (17.5MB)?\n\nIf you own or purchase the full game of Doom/Doom2 (or any other wad)you can click \'Cancel\' and copy the WAD file to the folder:\n\n{phonememory}/DVR")
                    .setCancelable(false)
                    .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            MainActivity.this.startDownload();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }

        mWADChooser = new WADChooser(openGL);

        // Audio?
        mAudioMgr = AudioManager.getInstance(this);

        // Listen for Doom events
        Natives.setListener(this);
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height)
    {
        Log.d(TAG, "onSurfaceChanged width = " + width + "  height = " + height);
        mSurfaceChanged = true;
    }


    @Override
    public void onSurfaceCreated(EGLConfig config) {
         Log.i(TAG, "onSurfaceCreated");

        openGL.onSurfaceCreated(config);
        openGL.SetupUVCoords();

        //Start intro music
        mPlayer = MediaPlayer.create(this, R.raw.m010912339);
        mPlayer.start();

        //Load bitmap for splash screen
        splashTexture[0] = 0;
        GLES20.glGenTextures(1, splashTexture, 0);

        Bitmap bmp = null;
        try {
            AssetManager assets = this.getAssets();
            InputStream in = assets.open("splash.jpg");
            bmp = BitmapFactory.decodeStream(in);
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Bind texture to texturename
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, splashTexture[0]);
        openGL.CopyBitmapToTexture(bmp, splashTexture[0]);

        bmp.recycle();
    }

    long prevState = 0;
    @Override
    public void onNewFrame(HeadTransform headTransform) {

        headTransform.getEulerAngles(eulerAngles, 0);
        hmdYaw = eulerAngles[1] / (M_PI / 180.0f);
        hmdPitch = -eulerAngles[0] / (M_PI / 180.0f);
        hmdRoll = -eulerAngles[2] / (M_PI / 180.0f);

        //Store head view
        headTransform.getHeadView(openGL.headView, 0);

        if (!mShowingSpashScreen && mWADChooser.choosingWAD())
        {
            return;
        }

        if (!mDVRInitialised) {
            if (!mSurfaceChanged)
                return;

            if (!mShowingSpashScreen) {
                final String[] argv;
                String args = new String();
                args = "doom -iwad " + mWADChooser.GetSelectedWADName() + " " + commandLineParams;
                argv = args.split(" ");
                String dvr= DoomTools.GetDVRFolder();
                Natives.DoomInit(argv, dvr);

                mDVRInitialised = true;

            }
        }

        if (mDVRInitialised) {
            //Fade out intro music
            if (mPlayer != null) {
                mPlayerVolume--;
                if (mPlayerVolume == 0) {
                    mPlayer.stop();
                    mPlayer.release();
                    mPlayer = null;
                }
                else
                {
                    float log1 = AudioManager.getLogVolume(mPlayerVolume);
                    mPlayer.setVolume(log1, log1);
                }
            }


            long newState = Natives.gameState();
            if (newState == 0)
                Natives.DoomStartFrame(hmdPitch, hmdYaw, hmdRoll);
            else
                Natives.DoomStartFrame(0, 0, 0);

            if (newState != prevState)
            {
                prevState = newState;

                //Reset head tracker in big screen mode
                if (newState != 0)
                    cardboardView.resetHeadTracker();
            }
        }

        if (mDVRInitialised) {
            //Get all the DOOM drawing done here, minimise time between eye calls
            Natives.DoomDrawEye(0);
            openGL.CopyBitmapToTexture(mDoomBitmap, openGL.fbo[0].ColorTexture[0]);
            Natives.DoomDrawEye(1);
            openGL.CopyBitmapToTexture(mDoomBitmap, openGL.fbo[1].ColorTexture[0]);
        }
    }

    @Override
    public void onDrawEye(Eye eye) {

        if (!mShowingSpashScreen && mWADChooser.choosingWAD())
        {
            mWADChooser.onDrawEye(eye, this);
        }
        else if (mDVRInitialised || mShowingSpashScreen) {

            GLES20.glViewport(eye.getViewport().x, eye.getViewport().y,
                    eye.getViewport().width, eye.getViewport().height);

            //Clear the viewport
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glScissor(eye.getViewport().x, eye.getViewport().y,
                    eye.getViewport().width, eye.getViewport().height);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            GLES20.glUseProgram(openGL.sp_Image);

            float modelScreen[] = new float[16];
            Matrix.setIdentityM(modelScreen, 0);

            // Set the position of the screen
            if (mShowingSpashScreen)
            {
                Matrix.translateM(modelScreen, 0, 0, 0, openGL.splashScreenDistance);
                Matrix.scaleM(modelScreen, 0, openGL.screenScale, openGL.screenScale, 1.0f);

                float mAngle = 180.0f * (float)((System.currentTimeMillis() % 2000) / 2000.0f);
                if (mAngle > 90.0f) mAngle += 180.0f;
                Matrix.rotateM(modelScreen, 0, mAngle, 0.0f, 1.0f, 0.0f);
            }
            else if (Natives.gameState() != 0)
            {
                //Drawing Virtual Screen
                Matrix.translateM(modelScreen, 0, 0, 0, openGL.screenDistance);
                //Make virtual screen wider than high
                Matrix.scaleM(modelScreen, 0, openGL.screenScale*1.3f, openGL.screenScale, 1.0f);
            }
            else
            {
                float screenDist = openGL.gameScreenDistance;
                float f = (hmdPitch / 90.0f);
                if (f > 0.125f)
                    screenDist *= (1.0f + (f - 0.125f) * 2.0f);

                //In Game - ensure screen is always "in-front" of us, whatever direction we are facing
                Matrix.translateM(modelScreen, 0, (float)(Math.sin(M_PI * (hmdYaw / 180f))) * screenDist, 0,
                        (float)(Math.cos(M_PI * (hmdYaw / 180f))) * screenDist);
                Matrix.rotateM(modelScreen, 0, hmdYaw, 0.0f, 1.0f, 0.0f);
                Matrix.scaleM(modelScreen, 0, openGL.screenScale, openGL.screenScale, openGL.screenScale);
            }

            // Build the ModelView and ModelViewProjection matrices
            // for calculating screen position.
            float[] perspective = eye.getPerspective(0.1f, 100.0f);

            if (Natives.gameState() != 0 || mShowingSpashScreen) {
                Matrix.multiplyMM(openGL.view, 0, eye.getEyeView(), 0, openGL.camera, 0);
            }
            else {
                //centre eye view - no stereo depth required
                Matrix.multiplyMM(openGL.view, 0, openGL.headView, 0, openGL.camera, 0);
            }

            Matrix.multiplyMM(openGL.modelView, 0, openGL.view, 0, modelScreen, 0);
            Matrix.multiplyMM(openGL.modelViewProjection, 0, perspective, 0, openGL.modelView, 0);
            GLES20.glVertexAttribPointer(openGL.positionParam, 3, GLES20.GL_FLOAT, false, 0, openGL.screenVertices);

            // Prepare the texturecoordinates
            GLES20.glVertexAttribPointer(openGL.texCoordParam, 2, GLES20.GL_FLOAT, false, 0, openGL.uvBuffer);

            // Apply the projection and view transformation
            GLES20.glUniformMatrix4fv(openGL.modelViewProjectionParam, 1, false, openGL.modelViewProjection, 0);

            // Bind texture to fbo's color texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            IntBuffer activeTex0 = IntBuffer.allocate(2);
            GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, activeTex0);

            if (mShowingSpashScreen) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, splashTexture[0]);
            }
            else  {
                //Actually Draw Doom
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, openGL.fbo[eye.getType()-1].ColorTexture[0]);
            }

            // Set the sampler texture unit to our fbo's color texture
            GLES20.glUniform1i(openGL.samplerParam, 0);

            // Draw the triangles
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, openGL.listBuffer);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeTex0.get(0));
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
        if (mDVRInitialised) {
             Natives.DoomEndFrame();
        }
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    //@Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

        if (!mShowingSpashScreen && mWADChooser.choosingWAD())
        {
            if (hmdYaw > 15.0f)
                mWADChooser.MoveNext();
            else if (hmdYaw < -15.0f)
                mWADChooser.MovePrev();
            else
                mWADChooser.SelectWAD();

            return;
        }

        if (System.currentTimeMillis() - triggerTimeout > 200) {

            if (mDVRInitialised) {
                Natives.keyEvent(Natives.EV_KEYDOWN, DoomTools.KEY_RCTRL);
                Natives.keyEvent(Natives.EV_KEYDOWN, DoomTools.KEY_ENTER);
            }

            dismissSplashScreen();

            triggerTimeout = System.currentTimeMillis();
        }
    }

    private void dismissSplashScreen()
    {
        if (mShowingSpashScreen) {
            mShowingSpashScreen = false;
            mWADChooser.Initialise(this.getAssets());
        }
    }

    @Override public boolean dispatchKeyEvent( KeyEvent event )
    {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        //Following buttons must not be handled here
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                )
            return false;

        if (!mShowingSpashScreen &&
                mWADChooser.choosingWAD())
        {
            if (action == KeyEvent.ACTION_UP &&
                    keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                if (hmdYaw > 15.0f)
                    mWADChooser.MoveNext();
                else if (hmdYaw < -15.0f)
                    mWADChooser.MovePrev();
                else
                    mWADChooser.SelectWAD();
            }
            else if (action == KeyEvent.ACTION_UP &&
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                mWADChooser.MovePrev();
            }
            else if (action == KeyEvent.ACTION_UP &&
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mWADChooser.MoveNext();
            }

            return true;
        }

        if ( action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP )
        {
            return super.dispatchKeyEvent( event );
        }
        if ( action == KeyEvent.ACTION_UP )
        {
            dismissSplashScreen();
        }

        //Allow user to switch vr mode by holding the start button down
        if (keyCode == KeyEvent.KEYCODE_BUTTON_START)
        {
            if (action == KeyEvent.ACTION_DOWN &&
                    startButtonDownCounter == -1)
            {
                startButtonDownCounter = System.currentTimeMillis();

            }
            else if (action == KeyEvent.ACTION_UP)
            {
                startButtonDownCounter = -1;
                cardboardView.resetHeadTracker();
                gameMenu();
            }
        }

        if (startButtonDownCounter != -1)
        {
            if ((System.currentTimeMillis() - startButtonDownCounter) > 2000)
            {
                startButtonDownCounter = -1;
                //Now make sure dvr is aware!

            }
        }

        if (mDVRInitialised) {
            if (action == KeyEvent.ACTION_DOWN) {
                Natives.keyEvent(Natives.EV_KEYDOWN,
                        DoomTools.keyCodeToKeySym(keyCode));
            }
            else
            {
                Natives.keyEvent(Natives.EV_KEYUP,
                        DoomTools.keyCodeToKeySym(keyCode));
            }
        }

        return true;
    }

    private static float getCenteredAxis(MotionEvent event,
                                         int axis) {
        final InputDevice.MotionRange range = event.getDevice().getMotionRange(axis, event.getSource());
        if (range != null) {
            final float flat = range.getFlat();
            final float value = event.getAxisValue(axis);
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }


    //Save the game pad type once known:
    // 1 - Generic BT gamepad
    // 2 - Samsung gamepad that uses different axes for right stick
    int gamepadType = 0;
    int lTrigAction = KeyEvent.ACTION_UP;
    int rTrigAction = KeyEvent.ACTION_UP;

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        int action = event.getAction();
        if ((source==InputDevice.SOURCE_JOYSTICK)||(event.getSource()==InputDevice.SOURCE_GAMEPAD))
        {
            if (event.getAction() == MotionEvent.ACTION_MOVE)
            {
                float z = getCenteredAxis(event, MotionEvent.AXIS_Z);
                float rz = -getCenteredAxis(event, MotionEvent.AXIS_RZ);
                //For the samsung game pad (uses different axes for the second stick)
                float rx = getCenteredAxis(event, MotionEvent.AXIS_RX);
                float ry = -getCenteredAxis(event, MotionEvent.AXIS_RY);

                //let's figure it out
                if (gamepadType == 0)
                {
                    if (z != 0.0f || rz != 0.0f)
                        gamepadType = 1;
                    else if (rx != 0.0f || ry != 0.0f)
                        gamepadType = 2;
                }

                switch (gamepadType)
                {
                    case 0:
                        break;
                    case 1:
                        Natives.motionEvent(0, (int)(z * 30), 0);
                        break;
                    case 2:
                        Natives.motionEvent(0, (int)(rx * 30), 0);
                        break;
                }

                //Fire weapon using shoulder trigger
                float axisRTrigger = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                        event.getAxisValue(MotionEvent.AXIS_GAS));
                int newRTrig = axisRTrigger > 0.6 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                if (rTrigAction != newRTrig)
                {
                    Natives.keyEvent(newRTrig, DoomTools.KEY_RCTRL);
                    rTrigAction = newRTrig;
                }

                //Run using L shoulder
                float axisLTrigger = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                        event.getAxisValue(MotionEvent.AXIS_BRAKE));
                int newLTrig = axisLTrigger > 0.6 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                if (lTrigAction != newLTrig)
                {
                    Natives.keyEvent(newLTrig, DoomTools.KEY_RSHIFT);
                    lTrigAction = newLTrig;
                }
            }
        }
        return false;
    }

    private float max(float axisValue, float axisValue2) {
        return (axisValue > axisValue2) ? axisValue : axisValue2;
    }

    /**
     * Show the menu
     */
    private void gameMenu() {
        Natives.keyEvent(Natives.EV_KEYDOWN, DoomTools.KEY_ESCAPE);
        Natives.keyEvent(Natives.EV_KEYUP, DoomTools.KEY_ESCAPE);
    }

    @Override
    public void OnQuit(int code) {
        try {
            Thread.sleep(500);
        }
        catch (InterruptedException ie){
        }

        System.exit(0);
    }

    public static  void MessageBox (Context ctx, String title, String text) {
        AlertDialog d = createAlertDialog(ctx
                , title
                , text);

        d.setButton("Dismiss", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                /* User clicked OK so do some stuff */
            }
        });
        d.show();
    }

    /**
     * Create an alert dialog
     * @param ctx App context
     * @param message Message
     * @return
     */
    static public AlertDialog createAlertDialog (Context ctx, String title, String message) {
        return new AlertDialog.Builder(ctx)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(title)
                .setMessage(message)
                .create();
    }

    void MessageBox(String title, String text) {
        AlertDialog d = createAlertDialog(this
                , title
                , text);

        d.setButton("Dismiss", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                /* User clicked OK so do some stuff */
            }
        });
        d.show();
    }

    /**
     * Fires when there an image update from Doom lib
     */
    @Override
    public void OnImageUpdate(int[] pixels, int eye) {
        mDoomBitmap.setPixels(pixels, 0, mDoomWidth, 0, 0, mDoomWidth, mDoomHeight);
    }

    /**
     * Fires on LIB message
     */
    @Override
    public void OnMessage(String text) {
        Log.d(TAG, "**Doom Message:  " + text);
    }

    @Override
    public void OnInfoMessage(String msg, final int type) {
        Log.i(TAG, "**Doom Message:  " + msg);
    }

    @Override
    public void OnInitGraphics(int w, int h) {
        Log.d(TAG, "OnInitGraphics creating Bitmap of " + w + " by " + h);
        mDoomWidth = w;
        mDoomHeight = h;
        mDoomBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);

        openGL.SetBitmap(mDoomBitmap);
        openGL.CreateFBO(openGL.fbo[0], mDoomWidth, mDoomHeight);
        openGL.CreateFBO(openGL.fbo[1], mDoomWidth, mDoomHeight);
    }

    @Override
    public void OnFatalError(final String text) {
        Log.e(TAG, "ERROR: " + text);
    }

    @Override
    public void OnStartSound(String name, int vol) {
        if (mAudioMgr == null) {
            Log.e(TAG, "Bug: Audio Mgr is NULL but sound is enabled!");
            return;
        }

        try {
            if (mAudioMgr != null)
                mAudioMgr.startSound(name, vol);

        } catch (Exception e) {
            Log.e(TAG, "OnStartSound: " + e.toString());
        }
    }

    /**
     * Fires on background music
     */
    @Override
    public void OnStartMusic(String name, int loop) {
        if (mAudioMgr != null)
            mAudioMgr.startMusic(MainActivity.this, name, loop);
    }

    /**
     * Stop bg music
     */
    @Override
    public void OnStopMusic(String name) {
        if (mAudioMgr != null)
            mAudioMgr.stopMusic(name);
    }

    @Override
    public void OnSetMusicVolume(int volume) {
        if (mAudioMgr != null)
            mAudioMgr.setMusicVolume(volume);
    }
}
