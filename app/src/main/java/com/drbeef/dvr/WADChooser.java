package com.drbeef.dvr;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.vrtoolkit.cardboard.Eye;

import java.io.File;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import doom.util.DoomTools;

/**
 * Created by Simon on 04/03/2016.
 */
public class WADChooser {

	OpenGL openGL = null;
	List<String> wads = new ArrayList<String>();
	Map<String, String> wadThumbnails = new HashMap<String, String>();
	Typeface type;

	enum Transition
	{
		ready,
		move_left,
		moving_left,
		move_right,
		moving_right
	};

	Transition mCurrentTransition = Transition.ready;
	long mTransitionStart = -1;

	private int selectedWAD = 0;

	WADChooser(OpenGL openGL) {
		this.openGL = openGL;
	}

	public void Initialise(Context context, AssetManager assets)
	{
		wadThumbnails.put(new String("doom.wad"), new String("d1.png"));
		wadThumbnails.put(new String("doom2.wad"), new String("d2.png"));
		wadThumbnails.put(new String("freedoom.wad"), new String("fd.png"));
		wadThumbnails.put(new String("freedoom1.wad"), new String("fd1.png"));
		wadThumbnails.put(new String("freedoom2.wad"), new String("fd2.png"));

		type = Typeface.createFromAsset(assets, "fonts/DooM.ttf");

		File[] files = new File(DoomTools.GetDVRFolder(context)).listFiles();

		for (File file : files) {
			if (file.isFile() &&
					file.getName().toUpperCase().compareTo("PRBOOM.WAD") != 0 &&
					file.getName().toUpperCase().endsWith("WAD"))
				wads.add(file.getName());
		}

		//No point choosing a wad if there's only one!
		if (wads.size() == 1) mChoosingWAD = false;
	}

	private boolean mChoosingWAD = true;

	public boolean choosingWAD() {
		return mChoosingWAD;
	}

	public void SelectWAD()
	{
		mChoosingWAD = false;
	}

	public String GetSelectedWADName() {
		return wads.get(selectedWAD);
	}

	public void MoveNext()
	{
		if (mCurrentTransition == Transition.ready) {
			mCurrentTransition = Transition.move_right;
			mTransitionStart = System.currentTimeMillis();
		}
	}

	public void MovePrev()
	{
		if (mCurrentTransition == Transition.ready) {
			mCurrentTransition = Transition.move_left;
			mTransitionStart = System.currentTimeMillis();
		}
	}

	void DrawWADName(Context ctx)
	{
// Create an empty, mutable bitmap
		Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_4444);

// get a canvas to paint over the bitmap
		Canvas canvas = new Canvas(bitmap);
		bitmap.eraseColor(0);

		Paint paint = new Paint();
		paint.setTextSize(20);
		paint.setTypeface(type);
		paint.setAntiAlias(true);
		paint.setARGB(0xff, 0xff, 0x20, 0x00);

		if (wadThumbnails.containsKey(GetSelectedWADName().toLowerCase())) {

			try {
				AssetManager assets = ctx.getAssets();
				InputStream in = assets.open("thumbnails/" + wadThumbnails.get(GetSelectedWADName().toLowerCase()));
				Bitmap thumbnail = BitmapFactory.decodeStream(in);
				in.close();

				canvas.drawBitmap(thumbnail, null, new Rect(36, 36, 218, 214), paint);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		else
		{
			canvas.drawText("no thumbnail", 42, 114, paint);
		}

// Draw the text
		if (mCurrentTransition == Transition.ready) {
			canvas.drawText("Choose Wad", 32, 24, paint);
			canvas.drawText(GetSelectedWADName(), 32, 256, paint);

			//Draw arrows
			paint.setTextSize(36);
			paint.setARGB(0xff, 0x20, 0x20, 0xff);
			canvas.drawText("<", 0, 116, paint);
			canvas.drawText(">", 228, 116, paint);
		}

		openGL.CopyBitmapToTexture(bitmap, openGL.fbo[0].ColorTexture[0]);
	}

	public void onDrawEye(Eye eye, Context ctx) {

		if (System.currentTimeMillis() - mTransitionStart > 250) {
			if (mCurrentTransition == Transition.move_right) {
				selectedWAD++;
				if (selectedWAD == wads.size())
					selectedWAD = 0;
				mCurrentTransition = Transition.moving_right;
			}
			if (mCurrentTransition == Transition.move_left) {
				selectedWAD--;
				if (selectedWAD < 0)
					selectedWAD = wads.size() - 1;
				mCurrentTransition = Transition.moving_left;
			}
		}

		if (System.currentTimeMillis() - mTransitionStart > 500)
		{
			mTransitionStart = -1;
			mCurrentTransition = Transition.ready;
		}


		DrawWADName(ctx);

		GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
		GLES20.glScissor(eye.getViewport().x, eye.getViewport().y,
				eye.getViewport().width, eye.getViewport().height);
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

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

		GLES20.glUseProgram(openGL.sp_Image);

		// Apply the eye transformation to the camera.
		Matrix.multiplyMM(openGL.view, 0, eye.getEyeView(), 0, openGL.camera, 0);

		// Build the ModelView and ModelViewProjection matrices
		// for calculating screen position.
		float[] perspective = eye.getPerspective(0.1f, 100.0f);

		// Object first appears directly in front of user.
		Matrix.setIdentityM(openGL.modelScreen, 0);
		Matrix.translateM(openGL.modelScreen, 0, 0, 0, openGL.screenDistance);
		Matrix.scaleM(openGL.modelScreen, 0, openGL.screenScale * 1.3f, openGL.screenScale, 1.0f);

		if (mTransitionStart != -1) {
			long transVal = System.currentTimeMillis() - mTransitionStart;
			if (mCurrentTransition == Transition.moving_left ||
					mCurrentTransition == Transition.move_left)
				transVal = 500 - transVal;
			float mAngle = 180.0f * (float) (((float)transVal) / 500.0f);
			if (mAngle > 90.0f)
				mAngle += 180.0f;
			Matrix.rotateM(openGL.modelScreen, 0, mAngle, 0.0f, 1.0f, 0.0f);
		}

		Matrix.multiplyMM(openGL.modelView, 0, openGL.view, 0, openGL.modelScreen, 0);
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

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, openGL.fbo[0].ColorTexture[0]);

		// Set the sampler texture unit to our fbo's color texture
		GLES20.glUniform1i(openGL.samplerParam, 0);

		// Draw the triangles
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, openGL.listBuffer);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeTex0.get(0));
	}
}
