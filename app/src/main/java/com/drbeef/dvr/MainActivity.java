package com.drbeef.dvr;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.Window;
import android.view.WindowManager;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;

import doom.audio.AudioManager;
import doom.util.DoomTools;
import doom.util.Natives;


public class MainActivity
        extends CardboardActivity
        implements CardboardView.StereoRenderer, Natives.EventListener
{
    private static final String TAG = "DVR";

    private static final int GL_RGBA8 = 0x8058;

    private int[] currentFBO = new int[1];

    //Head orientation
    private float[] eulerAngles = new float[3];

    // Audio Cache Manager
    private AudioManager mAudioMgr;

    private int MONO = 0;
    private int STEREO = 1;

    private int mStereoMode = STEREO;
    private int eyeID = 0;

    static private Bitmap mDoomBitmap;

    // width of mBitmap
    private int mDoomWidth;
    // height of mBitmap
    private int mDoomHeight;

    //-1 means start button isn't pressed
    private long startButtonDownCounter = -1;
    //Don't allow the trigger to fire more than once per 200ms
    private long triggerTimeout = 0;

    private Vibrator vibrator;
    private float M_PI = 3.14159265358979323846f;

    //Read these from a file and pass through
    String commandLineParams = new String("");

    private CardboardView cardboardView;

    private FloatBuffer screenVertices;

    private int positionParam;
    private int texCoordParam;
    private int samplerParam;
    private int modelViewProjectionParam;

    private float[] modelScreen;
    private float[] camera;
    private float[] view;
    private float[] modelViewProjection;
    private float[] modelView;

    private float screenDistance = 8f;
    private float screenScale = 4f;

    public static final String vs_Image =
            "uniform mat4 u_MVPMatrix;" +
            "attribute vec4 a_Position;" +
            "attribute vec2 a_texCoord;" +
            "varying vec2 v_texCoord;" +
            "void main() {" +
            "  gl_Position = u_MVPMatrix * a_Position;" +
            "  v_texCoord = a_texCoord;" +
            "}";


    public static final String fs_Image =
            "precision mediump float;" +
            "varying vec2 v_texCoord;" +
            "uniform sampler2D s_texture;" +
            "void main() {" +
            "  gl_FragColor = texture2D( s_texture, v_texCoord );" +
            "}";

    public static int loadShader(int type, String shaderCode){
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    //FBO render eye buffer
    private DVRFBO fbo;


    static boolean CreateFBO( DVRFBO fbo, int width, int height)
    {
        Log.d(TAG, "CreateFBO");
        // Create the color buffer texture.
        GLES20.glGenTextures(1, fbo.ColorTexture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbo.ColorTexture[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        // Create depth buffer.
        GLES20.glGenRenderbuffers(1, fbo.DepthBuffer, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, fbo.DepthBuffer[0]);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES11Ext.GL_DEPTH_COMPONENT24_OES, width, height);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);

        // Create the frame buffer.
        GLES20.glGenFramebuffers(1, fbo.FrameBuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.FrameBuffer[0]);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, fbo.DepthBuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fbo.ColorTexture[0], 0);
        int renderFramebufferStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if ( renderFramebufferStatus != GLES20.GL_FRAMEBUFFER_COMPLETE )
        {
            Log.d(TAG, "Incomplete frame buffer object!!");
            return false;
        }

        fbo.width = width;
        fbo.height = height;

        return true;
    }

    static void DestroyFBO( DVRFBO fbo )
    {
        GLES20.glDeleteFramebuffers( 1, fbo.FrameBuffer, 0 );
        fbo.FrameBuffer[0] = 0;
        GLES20.glDeleteRenderbuffers( 1, fbo.DepthBuffer, 0 );
        fbo.DepthBuffer[0] = 0;
        GLES20.glDeleteTextures( 1, fbo.ColorTexture, 0 );
        fbo.ColorTexture[0] = 0;
        fbo.width = 0;
        fbo.height = 0;
    }

    // Geometric variables
    public static float vertices[];
    public static final short[] indices = new short[] {0, 1, 2, 0, 2, 3};
    public static final float uvs[] =  new float[] {
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
    };

    public static final float[] SCREEN_COORDS = new float[] {
            -1.3f, -1.0f, 1.0f,
            -1.3f, 1.0f, 1.0f,
            1.3f, 1.0f, 1.0f,
            1.3f, -1.0f, 1.0f
    };

    public FloatBuffer vertexBuffer;
    public ShortBuffer listBuffer;
    public FloatBuffer uvBuffer;

    //Shader Program
    public static int sp_Image;

    public static boolean mDVRInitialised = false;
    //Can't rebuild eye buffers until surface changed flag recorded
    public static boolean mSurfaceChanged = false;

    private boolean mShowingSpashScreen = true;
    private int[] splashTexture = new int[1];
    private MediaPlayer mPlayer;


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


    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setEGLConfigChooser(5, 6, 5, 0, 16, 0);
        cardboardView.setLowLatencyModeEnabled(true);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        modelScreen = new float[16];
        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (!DoomTools.wadsExist()) {
            MessageBox("Read this carefully",
                    "You must install a game file. Tap \"Install WADs\" for auto-install. "
                            + "Tap \"Help Me!\" for instructions on manual installation. "
                            + "A fast WIFI network and SDCARD are required."
                            + "If you experience game problems, try the Cleanup option.");
        }


        //Create the FBOs
        fbo = new DVRFBO();

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

    void CopyBitmapToTexture(Bitmap bmp, int textureUnit)
    {
        // Bind texture to texturename
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureUnit);

        // Set filtering
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // Set wrapping mode
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // Load the bitmap into the bound texture.
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);

        //unbind
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void onSurfaceCreated(EGLConfig config) {
         Log.i(TAG, "onSurfaceCreated");

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(SCREEN_COORDS.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        screenVertices = bbVertices.asFloatBuffer();
        screenVertices.put(SCREEN_COORDS);
        screenVertices.position(0);

        // initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        listBuffer = dlb.asShortBuffer();
        listBuffer.put(indices);
        listBuffer.position(0);

         // Create the shaders, images
         int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vs_Image);
         int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fs_Image);

         sp_Image = GLES20.glCreateProgram();             // create empty OpenGL ES Program
         GLES20.glAttachShader(sp_Image, vertexShader);   // add the vertex shader to program
         GLES20.glAttachShader(sp_Image, fragmentShader); // add the fragment shader to program
         GLES20.glLinkProgram(sp_Image);                  // creates OpenGL ES program executable

        positionParam = GLES20.glGetAttribLocation(sp_Image, "a_Position");
        texCoordParam = GLES20.glGetAttribLocation(sp_Image, "a_texCoord");
        modelViewProjectionParam = GLES20.glGetUniformLocation(sp_Image, "u_MVPMatrix");
        samplerParam = GLES20.glGetUniformLocation(sp_Image, "s_texture");


        GLES20.glEnableVertexAttribArray(positionParam);
        GLES20.glEnableVertexAttribArray(texCoordParam);

        // Build the camera matrix
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, 0.01f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

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
        CopyBitmapToTexture(bmp, splashTexture[0]);
    }

    public void SwitchStereoMode(int stereo_mode)
    {
        mStereoMode = stereo_mode;
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {

        if (!mDVRInitialised) {
            if (!mSurfaceChanged)
                return;

            SetupUVCoords();

            //Reset our orientation
            cardboardView.resetHeadTracker();

            if (!mShowingSpashScreen) {
                final String[] argv;
                String args = new String();
                args = "doom -width 640 -height 400 -iwad doom.wad";
                argv = args.split(" ");
                Natives.DoomInit(argv);
                mDVRInitialised = true;
            }
        }

        if (mDVRInitialised) {
            headTransform.getEulerAngles(eulerAngles, 0);

            float yaw = eulerAngles[1] / (M_PI / 180.0f);
            float pitch = -eulerAngles[0] / (M_PI / 180.0f);
            float roll = -eulerAngles[2] / (M_PI / 180.0f);
            if (Natives.gameState() == 0)
                Natives.DoomStartFrame(pitch, yaw, roll);
            else
                Natives.DoomStartFrame(0, 0, 0);
        }
    }

    @Override
    public void onDrawEye(Eye eye) {
        if (mDVRInitialised || mShowingSpashScreen) {

            if (mShowingSpashScreen) {
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
                GLES20.glScissor(eye.getViewport().x, eye.getViewport().y,
                        eye.getViewport().width, eye.getViewport().height);
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            } else  {
                Natives.DoomDrawEye(eye.getType() - 1);

                //Now we'll have a populated bitmap, copy to the fbo colour buffer
                CopyBitmapToTexture(mDoomBitmap, fbo.ColorTexture[0]);
            }

            GLES20.glViewport(eye.getViewport().x,
                    eye.getViewport().y,
                    eye.getViewport().width,
                    eye.getViewport().height);

            //Clear the viewport
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glScissor(eye.getViewport().x,
                    eye.getViewport().y,
                    eye.getViewport().width,
                    eye.getViewport().height);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            GLES20.glUseProgram(sp_Image);

            if (Natives.gameState() != 0) {
                // Apply the eye transformation to the camera.
                Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

                // Build the ModelView and ModelViewProjection matrices
                // for calculating screen position.
                float[] perspective = eye.getPerspective(0.1f, 100.0f);

                float scale = screenScale;
                if (mShowingSpashScreen)
                    scale /= 2;

                // Object first appears directly in front of user.
                Matrix.setIdentityM(modelScreen, 0);
                Matrix.translateM(modelScreen, 0, 0, 0, -screenDistance);
                Matrix.scaleM(modelScreen, 0, scale, scale, 1.0f);
                Matrix.multiplyMM(modelView, 0, view, 0, modelScreen, 0);
                Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
                GLES20.glVertexAttribPointer(positionParam, 3, GLES20.GL_FLOAT, false, 0, screenVertices);

            } else {

                // Create the triangles for orthographic projection (if required)
                int w = (int) (eye.getViewport().width * 0.82f);
                int h = (int) (eye.getViewport().height * 0.7f);
                int x = (int) ((eye.getType() == Eye.Type.LEFT) ? 0 : (eye.getViewport().width - w));
                int y = (int) (eye.getViewport().height * 0.15f);

                int pitchOffset = (int)(-(eulerAngles[0]/M_PI)*(eye.getViewport().height));

                SetupTriangle(x, y, w, h);

                // Calculate the projection and view transformation
                Matrix.orthoM(view, 0, 0, eye.getViewport().width, 0, eye.getViewport().height, 0, 50);
                //Translate so origin is centre of image
                if (eye.getType() == Eye.Type.LEFT)
                    Matrix.translateM(view, 0, w / 2, eye.getViewport().height / 2, 0);
                else
                    Matrix.translateM(view, 0, eye.getViewport().width - w / 2, eye.getViewport().height / 2, 0);
                //rotate for head roll
                Matrix.rotateM(view, 0, (int) (-(eulerAngles[2] / M_PI) * 180.f), 0, 0, 1);
                //translate back to where it was before
                if (eye.getType() == Eye.Type.LEFT)
                    Matrix.translateM(view, 0, -w / 2, -eye.getViewport().height / 2, 0);
                else
                    Matrix.translateM(view, 0, w / 2 - eye.getViewport().width, -eye.getViewport().height / 2, 0);
                //Now apply head pitch transformation
                Matrix.translateM(view, 0, 0, pitchOffset, 0);
                Matrix.multiplyMM(modelViewProjection, 0, view, 0, camera, 0);

                // Prepare the triangle coordinate data
                GLES20.glVertexAttribPointer(positionParam, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            }

            // Prepare the texturecoordinates
            GLES20.glVertexAttribPointer(texCoordParam, 2, GLES20.GL_FLOAT, false, 0, uvBuffer);

            // Apply the projection and view transformation
            GLES20.glUniformMatrix4fv(modelViewProjectionParam, 1, false, modelViewProjection, 0);

            // Bind texture to fbo's color texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            IntBuffer activeTex0 = IntBuffer.allocate(2);
            GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, activeTex0);

            if (mShowingSpashScreen) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, splashTexture[0]);
            }
            else  {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbo.ColorTexture[0]);
            }

            // Set the sampler texture unit to our fbo's color texture
            GLES20.glUniform1i(samplerParam, 0);

            // Draw the triangles
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, listBuffer);
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

        if (System.currentTimeMillis() - triggerTimeout > 200) {

            if (mDVRInitialised) {
                Natives.keyEvent(Natives.EV_KEYDOWN, DoomTools.KEY_RCTRL);
                Natives.keyEvent(Natives.EV_KEYDOWN, DoomTools.KEY_ENTER);
            }

            cardboardView.resetHeadTracker();

            dismissSplashScreen();

            triggerTimeout = System.currentTimeMillis();
        }
    }


    public int getCharacter(int keyCode, KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_DEL) return '\b';
        return event.getUnicodeChar();
    }

    private void dismissSplashScreen()
    {
        if (mShowingSpashScreen) {
            mPlayer.stop();
            mPlayer.release();
            mShowingSpashScreen = false;
        }
    }

    @Override public boolean dispatchKeyEvent( KeyEvent event )
    {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

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

        //Following buttons must not be handled here
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL
                )
            return false;

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

    boolean wDown = false;
    boolean aDown = false;
    boolean sDown = false;
    boolean dDown = false;

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        int action = event.getAction();
        if ((source==InputDevice.SOURCE_JOYSTICK)||(event.getSource()==InputDevice.SOURCE_GAMEPAD))
        {
            if (event.getAction() == MotionEvent.ACTION_MOVE)
            {
                float x = getCenteredAxis(event, MotionEvent.AXIS_X);
                float y = -getCenteredAxis(event, MotionEvent.AXIS_Y);

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
            }
        }
        return false;
    }

    private float max(float axisValue, float axisValue2) {
        return (axisValue > axisValue2) ? axisValue : axisValue2;
    }

    public void SetupUVCoords()
    {
        // The texture buffer
        ByteBuffer bb = ByteBuffer.allocateDirect(uvs.length * 4);
        bb.order(ByteOrder.nativeOrder());
        uvBuffer = bb.asFloatBuffer();
        uvBuffer.put(uvs);
        uvBuffer.position(0);
    }

    public void SetupTriangle(int x, int y, int width, int height)
    {
        // We have to create the vertices of our triangle.
        vertices = new float[]
                {
                        x, y, 0.0f,
                        x, y + height, 0.0f,
                        x + width, y + height, 0.0f,
                        x + width, y, 0.0f,
                };

        // The vertex buffer.
        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);
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
            Thread.sleep(1000);
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
        mDoomBitmap.setPixels(pixels, 0, mDoomWidth, 0, 0, mDoomWidth,
                mDoomHeight);
    }

    /**
     * Fires on LIB message
     */
    @Override
    public void OnMessage(String text) {
        Log.d(TAG, "**Doom Message:  " + text);
    }

    @Override
    public void OnInfoMessage(final String msg, final int type) {
        Log.i(TAG, "**Doom Message:  " + msg);
    }

    @Override
    public void OnInitGraphics(int w, int h) {
        Log.d(TAG, "OnInitGraphics creating Bitmap of " + w + " by " + h);
        mDoomWidth = w;
        mDoomHeight = h;
        mDoomBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);

        CreateFBO(fbo, mDoomWidth, mDoomHeight);
    }

    @Override
    public void OnFatalError(final String text) {
        MessageBox("Fatal Error", "Doom has terminated. " + "Reason: "
                        + text);

        try {
            Thread.sleep(10000);
        }
        catch (InterruptedException ie){
        }

        // Must quit here or the LIB will crash
        DoomTools.hardExit(-1);
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

    /**
     * Send a key event to the native layer
     *
     * @param type
     * @param sym
     */
    private void sendNativeKeyEvent(int type, int sym) {
        try {
            Natives.keyEvent(type, sym);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, e.toString());
        }
    }
}
