package com.drbeef.dvr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.vrtoolkit.cardboard.Eye;

import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import doom.util.DoomTools;
import doom.util.Natives;

/**
 * Created by Simon on 04/03/2016.
 */
public class WADChooser {

	OpenGL openGL = null;

	List<String> wads = new ArrayList<String>();
	private int selectedWAD = 0;

	WADChooser(OpenGL openGL) {
		this.openGL = openGL;

		File[] files = new File(DoomTools.GetDVRFolder()).listFiles();

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

	public String GetChosenWAD() {
		return wads.get(selectedWAD);
	}

	public void MoveNext()
	{
		selectedWAD++;
		if (selectedWAD == wads.size())
			selectedWAD = 0;
	}

	public void MovePrev()
	{
		selectedWAD--;
		if (selectedWAD < 0)
			selectedWAD = wads.size()-1;
	}

	void DrawWADName()
	{
// Create an empty, mutable bitmap
		Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_4444);
// get a canvas to paint over the bitmap
		Canvas canvas = new Canvas(bitmap);
		bitmap.eraseColor(0);

// get a background image from resources
// note the image format must match the bitmap format
//		Drawable background = context.getResources().getDrawable(R.drawable.background);
//		background.setBounds(0, 0, 256, 256);
//		background.draw(canvas); // draw the background to our bitmap

// Draw the text
		Paint textPaint = new Paint();
		textPaint.setTextSize(20);

		textPaint.setAntiAlias(true);
		textPaint.setARGB(0xff, 0xff, 0x20, 0x00);
// draw the text centered
		canvas.drawText("Choose WAD:", 16, 60, textPaint);
		canvas.drawText("<-  " + GetChosenWAD() + "  ->", 16, 112, textPaint);

		openGL.CopyBitmapToTexture(bitmap, openGL.fbo.ColorTexture[0]);
	}

	public void onDrawEye(Eye eye) {

		DrawWADName();

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
		Matrix.translateM(openGL.modelScreen, 0, 0, 0, -openGL.screenDistance);
		Matrix.scaleM(openGL.modelScreen, 0, openGL.screenScale, openGL.screenScale, 1.0f);
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

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, openGL.fbo.ColorTexture[0]);

		// Set the sampler texture unit to our fbo's color texture
		GLES20.glUniform1i(openGL.samplerParam, 0);

		// Draw the triangles
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, openGL.listBuffer);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeTex0.get(0));
	}
}
