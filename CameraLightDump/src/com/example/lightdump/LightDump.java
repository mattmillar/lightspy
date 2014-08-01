package com.example.lightdump;

import android.os.Bundle;
import android.util.Log;
import android.hardware.Camera;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.ImageFormat;
import android.graphics.BitmapFactory;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.Color;


public class LightDump extends Activity {
	public static final int PIXELS_TO_SAMPLE_PER_ROW    = 10;
	public static final int ROWS_TO_SAMPLE_PER_FRAME    = 10;
	public static final double FRAMES_TO_SAMPLE_PER_SECOND = 1;

		
	private Camera camera;
	
	private int samplesPerFrame = PIXELS_TO_SAMPLE_PER_ROW * ROWS_TO_SAMPLE_PER_FRAME;
	
	// return light level given a byte image
	private double computeLightIntensity(byte[] frameData, Camera camera) {
		int width = camera.getParameters().getPictureSize().width;
		int height = camera.getParameters().getPictureSize().height;
		
		int dx = width / PIXELS_TO_SAMPLE_PER_ROW;
		int dy = height / ROWS_TO_SAMPLE_PER_FRAME;
		
        //Rect rect = new Rect(0, 0, width, height); 
        YuvImage yuv = new YuvImage(frameData, ImageFormat.NV21, width, height, null);
		
		Bitmap img = BitmapFactory.decodeByteArray(yuv.getYuvData(), 0, yuv.getYuvData().length);
		
		
		double runningTotal = 0;
		for (int i_y = 0; i_y < height; i_y++) {
			for (int i_x = 0; i_x < width; i_x++) {
				if (i_x % dx == 0 && i_y % dy == 0) {
					int red = Color.red(img.getPixel(i_x, i_y));
					int blue = Color.blue(img.getPixel(i_x, i_y));
					int green = Color.green(img.getPixel(i_x, i_y));
					
					double pixelIntensity = ((double) (red + blue + green)) / 3.0;
					
					runningTotal += pixelIntensity;
				}
			}
		}
		
		double frameIntensity = runningTotal / samplesPerFrame;
		return frameIntensity;
	}
	
	private Camera.PictureCallback picCB = new Camera.PictureCallback() {
		@Override
		public void onPictureTaken(byte[] frameData, Camera camera) {
			double lightLevel = computeLightIntensity(frameData, camera);
			
			Log.d("sensor-data", Double.toString(System.currentTimeMillis())
			             + "," + Double.toString(lightLevel) + "\n");
		}
	};
	
	private void takePicture() {
	    try {
	    	//SurfaceHolder sh = (SurfaceHolder) findViewById(R.id.activity_light_dump_camerathumbnailview);
	    	//sh.addCallback(new SurfaceHolder.Callback() {
	    	//	public void surfaceChanged (SurfaceHolder holder, int format, int width, int height) {
	    	//	};
	    	//	
	    	//	public void surfaceCreated (SurfaceHolder holder) {
	    	//	};
	    	//	
	    	//	public void surfaceDestroyed (SurfaceHolder holder) {
	    	//	};
	    	//});
		    //camera.setPreviewDisplay((SurfaceHolder) findViewById(R.id.activity_light_dump_camerathumbnailview));
		    //camera.startPreview();
		    camera.takePicture(null, picCB, null, null);
		    
	    } catch (Exception e) {
	        ((TextView) findViewById(R.id.activity_light_dump_description)).setText("Could not take picture:\n\n" + e.getMessage());
	    }
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_light_dump);
		
		try {
			// set up the camera
			camera = Camera.open();
			
			Camera.Parameters camParams = camera.getParameters();
			camParams.setPictureSize(PIXELS_TO_SAMPLE_PER_ROW, ROWS_TO_SAMPLE_PER_FRAME);
			
			camera.setParameters(camParams);
		} catch (Exception e) {
	        ((TextView) findViewById(R.id.activity_light_dump_description)).setText("Could not take picture:\n\n" + e.getMessage());

        }
		
		while (true) {
			//takePicture();
			Log.d("sensor-data", "HI")
			
		    SystemClock.sleep((int) (((double) 1000)/((double) FRAMES_TO_SAMPLE_PER_SECOND)));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.light_dump, menu);
		return true;
	}

}
