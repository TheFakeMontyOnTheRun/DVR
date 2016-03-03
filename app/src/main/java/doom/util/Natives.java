package doom.util;

/**
 * Doom for android JNI natives
 * @author vsilva
 *
 */
public class Natives 
{
	private static EventListener listener;
	
	public static final int EV_KEYDOWN = 0;
	public static final int EV_KEYUP = 1;
	public static final int EV_MOUSE = 2;
	
	public static interface EventListener
	{
		void OnMessage(String text);
		void OnInitGraphics(int w, int h);
		void OnImageUpdate (int[] pixels, int eye);
		void OnFatalError(String text);
		void OnQuit (int code);
		void OnStartSound(String name, int vol);
		void OnStartMusic (String name, int loop);
		void OnStopMusic (String name);
		void OnSetMusicVolume (int volume);
		void OnInfoMessage(String msg, int displayType);
	}


	public static void setListener (EventListener l) {
		listener = l;
	}
	
	/**
	 * Native Main Doom Initialisation
	 * @param argv
	 * @return
	 */
	public static native int DoomInit(String[] argv);

	/**
	 * Start frame rendering
	 */
	public static native int DoomStartFrame(float pitch, float yaw, float roll);

	/**
	 * Draw Eye: 0 = left, 1 = right
	 */
	public static native int DoomDrawEye(int eye);

	/**
	 * Finish frame rendering
	 */
	public static native int DoomEndFrame();

	/**
	 * Key Event JNI func
	 * @param type event type: UP/DOWN
	 * @param key ASCII symbol
	 */
	public static native int keyEvent(int type, int key);
	
	/**
	 * Motion Event
	 * @param b Mouse button 1,2,4
	 * @param x X coord
	 * @param y Y coord
	 * @return
	 */
	public static native int motionEvent(int b, int x, int y);

	//Returns whether menu or map is showing
	public static native int gameState();

	/***********************************************************
	 * C - Callbacks
	 ***********************************************************/
	
	/**
	 * This fires on messages from the C layer
	 * @param text
	 */
	@SuppressWarnings("unused")
	private static void OnMessage(String text) {
		if ( listener != null)
			listener.OnMessage(text);
	}
	
	@SuppressWarnings("unused")
	private static void OnInfoMessage(String msg, int displayType) {
		if ( listener != null )
			listener.OnInfoMessage(msg, displayType);
	}
	
	@SuppressWarnings("unused")
	private static void OnInitGraphics(int w, int h) {
		if ( listener != null)
			listener.OnInitGraphics(w, h);
	}
	
	@SuppressWarnings("unused")
	private static void OnImageUpdate(int[] pixels, int eye) {
		if ( listener != null)
			listener.OnImageUpdate(pixels, eye);

	}
	
	/**
	 * Fires when the C lib calls exit()
	 * @param message
	 */
	@SuppressWarnings("unused")
	private static void OnFatalError(String message) {
		if ( listener != null)
			listener.OnFatalError(message);
	}

	/**
	 * Fires when Doom Quits
	 * TODO: No yet implemented in the JNI lib
	 * @param code
	 */
	@SuppressWarnings("unused")
	private static void OnQuit(int code) {
		if ( listener != null)
			listener.OnQuit(code);
	}
	
	/**
	 * Fires when a sound is played in the C layer.
	 * @param name Sound name (e.g pistol)
	 * @param volume (1-100)
	 */
	@SuppressWarnings("unused")
	private static void OnStartSound(byte[] name, int vol) {
		if ( listener != null)
			listener.OnStartSound(new String( name), vol);
	}
	
	/**
	 * Start background music callback
	 * @param name
	 * @param loop
	 */
	@SuppressWarnings("unused")
	private static void OnStartMusic(String name, int loop) {
		if ( listener != null)
			listener.OnStartMusic(name, loop);
	}

	/**
	 * Stop bg music
	 * @param name
	 */
	@SuppressWarnings("unused")
	private static void OnStopMusic(String name) {
		if ( listener != null)
			listener.OnStopMusic(name);
	}
	
	
	/**
	 * Set bg music volume
	 * @param volume Range: (0-15)
	 */
	@SuppressWarnings("unused")
	private static void OnSetMusicVolume(int volume) {
		if ( listener != null)
			listener.OnSetMusicVolume((int)(volume * 100.0/15.0));
	}
	
}
