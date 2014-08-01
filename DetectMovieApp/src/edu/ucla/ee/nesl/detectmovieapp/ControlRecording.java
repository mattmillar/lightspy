package edu.ucla.ee.nesl.detectmovieapp;


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.hardware.Camera.PictureCallback;

class Preview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "Preview";

    TextView virtualLightNormText;
    TextView virtualLightRawText;
    TextView exposureValueText;
    SurfaceHolder mHolder;
    public Camera camera;
   
    final String filename = Environment.getExternalStorageDirectory().getPath() + "/.tmp_lightapp_exposure_photo.jpg";
    
    int w,h;
    int previewFormat;

    double currLight = 0;
    
    int samplesPerDim = 10;
    
    boolean waitingForValue = false;
    boolean isCameraOpen = false;
    boolean isExposureSet = false;
    
    double badExposure = 10;
    
    int timeSinceAutoExposure = 0;
    
    double exposureAndISOFactor = 1;
        
    Preview(Context context) {
        super(context);
        mHolder = getHolder();
        mHolder.addCallback(this);
    }
    
    PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.d(TAG, "JPEG Callback called");
			FileOutputStream outStream = null;
			try {
				outStream = new FileOutputStream(filename);
				outStream.write(data);
				outStream.close();
				ExifInterface exif;
				exif = new ExifInterface(filename);
				exposureAndISOFactor = 1 / (Double.parseDouble(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME))); // * Double.parseDouble(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)));
				exposureValueText.setText(String.valueOf(1 / exposureAndISOFactor));
				Log.d(TAG, "ISO " + exif.getAttribute(ExifInterface.TAG_ISO)
						+ "; AP " + exif.getAttribute(ExifInterface.TAG_APERTURE)
						+ "; ET " + exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
				);
			} catch (FileNotFoundException e) {
				Log.e(TAG, e.getLocalizedMessage());
			} catch (IOException e) {
				Log.e(TAG, e.getLocalizedMessage());
			}
			Log.d(TAG, "onPictureTaken - jpeg");
	        if (isCameraOpen) {
	        	camera.startPreview();
	        }
		}
	};
	
    void getExposure() {
    	camera.takePicture(null, null, jpegCallback);
    }
    
    void lockExposure() {
    	Camera.Parameters parameters = camera.getParameters();
        parameters.setAutoExposureLock(true);
        parameters.setAutoWhiteBalanceLock(true);
		//parameters.setSceneMode(Camera.Parameters.SCENE_MODE_SPORTS);
        Log.d(TAG, "Exposure locked");
        camera.setParameters(parameters);
		badExposure = 0;
        isExposureSet = true;
        getExposure();
    }
    
    void unlockExposure() {
    	Camera.Parameters parameters = camera.getParameters();
        parameters.setAutoExposureLock(false);
        exposureValueText.setText("---");
        Log.d(TAG, "Exposure unlocked");
        camera.setParameters(parameters);
		badExposure = 10;
		timeSinceAutoExposure = 0;
        isExposureSet = false;
    }
    
    double[] convertYUV420_NV21toLight(byte [] data) {
        int size = w*h;
        double[] lightArray = new double[size];
        for(int i=0; i < size; i+=2) {
            lightArray[i  ] =   ((double) (data[  i  ]&0xff)) /256;
            lightArray[i+1] =   ((double) (data[  i+1]&0xff)) /256;
            lightArray[w+i  ] = ((double) (data[w+i  ]&0xff)) /256;
            lightArray[w+i+1] = ((double) (data[w+i+1]&0xff)) /256;
     
            if (i!=0 && (i+2)%w==0)
                i+=w;
        }
        return lightArray;
    }
    
    public void processLightArray(double[] lightArray) {
    	double result = 0;
    	int x,y;
    	double minLight = Double.MAX_VALUE;
    	double maxLight = 0;
    	double pixelLight;
    	for(int i = 0; i < samplesPerDim; i++) {
    		for (int j = 0; j < samplesPerDim; j++) {
    			x = (w * i) / samplesPerDim;
    			y = (h * j) / samplesPerDim;
    			pixelLight = lightArray[y*w+x];
    			if (pixelLight < minLight) minLight = pixelLight;
    			if (pixelLight > maxLight) maxLight = pixelLight;
    			result += pixelLight;
    		}
    	}
    	result *= 1000 / (samplesPerDim * samplesPerDim);
    	
    	badExposure *= 0.8;
    	if(minLight >= 0.9 || (maxLight <= 0.2 && exposureAndISOFactor > 20) || maxLight <= 0.05) {
    		badExposure += 1;
    	}
		if(isExposureSet) {
			if(badExposure > 3) {
				unlockExposure();
			}
		} else {
			timeSinceAutoExposure++;
			if(badExposure < 1.8 || timeSinceAutoExposure > 15) {
				lockExposure();
			}
		}

		virtualLightRawText.setText(String.valueOf(result));

    	if(isExposureSet) {
    		result *= Math.sqrt(100 * exposureAndISOFactor); // Seems to work well on Galaxy Nexus so that the gap between the light
    		                                                 // values from roughly the same scene but at different exposures is small
    		virtualLightNormText.setText(String.valueOf(result));
    		this.currLight = result;
    	} else {
    		virtualLightNormText.setText("---");
    	}
    	waitingForValue = false;
    }


    public void surfaceCreated(SurfaceHolder holder) {
        camera = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int camId = 0; camId < Camera.getNumberOfCameras(); camId++) {
            Camera.getCameraInfo(camId, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    camera = Camera.open(camId);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
                }
            }
        }
        Camera.Parameters parameters = camera.getParameters();
        // Log.v(TAG, parameters.flatten());
        Size smallestSize = null;
        int smallestArea = Integer.MAX_VALUE;
        for (Size size : parameters.getSupportedPreviewSizes()) {
        	int area = size.height * size.width;
        	if (area < smallestArea) {
        		smallestArea = area;
        		smallestSize = size;
        	}
        }
        h = smallestSize.height;
        w = smallestSize.width;
        Log.d(TAG, "Preview size set to " + w + "x" + h);
        previewFormat = 0;
        for (int format : parameters.getSupportedPreviewFormats()) {
            if (previewFormat == 0 && format == ImageFormat.NV21) {
                previewFormat = ImageFormat.NV21;
                Log.d(TAG, "Format NV21.");
            } else if ((format == ImageFormat.JPEG || format == ImageFormat.RGB_565)) {
                Log.d(TAG, "Format JPEG or RGB_565.");
                previewFormat = format;
            }
        }
        if (previewFormat != 0) {
            parameters.setPreviewFormat(previewFormat);
        } else {
            Log.e(TAG, "No supported preview format...");
        }
        camera.setParameters(parameters);
        try {
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(new PreviewCallback() {
                public void onPreviewFrame(byte[] data, Camera arg1) {
                    if(waitingForValue) {
                    	if (previewFormat == ImageFormat.NV21) {
                    		double[] lightArray = convertYUV420_NV21toLight(data);
                    		processLightArray(lightArray);
                    	} else {
                    		Log.e(TAG, "NV21 not supported");
//                    		if (previewFormat == ImageFormat.JPEG || previewFormat == ImageFormat.RGB_565) {
//                    			BitmapFactory.Options opts = new BitmapFactory.Options();
//                    			opts.inDither = true;
//                    			opts.inPreferredConfig = Bitmap.Config.RGB_565;
//                    			bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
//                        		processBitmap(bitmap);
//                    		}
                    	}
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        isCameraOpen = true;
    }
    
    public double getLight() {
    	waitingForValue = true;
    	return currLight;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (isCameraOpen) {
        	camera.stopPreview();
        	camera.setPreviewCallback(null);
        	camera.release();
        	camera = null;
        	isCameraOpen = false;
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (isCameraOpen) {
        	camera.startPreview();
        }
    }
    
    public void setTextViews(TextView virtualLightNormText, TextView virtualLightRawText, TextView exposureValueText) {
        this.virtualLightNormText = virtualLightNormText;
        this.virtualLightRawText  = virtualLightRawText;
        this.exposureValueText    = exposureValueText;
    }
}

public class ControlRecording extends Activity{

	public static final String TAG = "ControlRecording";
	Context mContext;
	TextView status, virtualLightFiltered, lightUtility;
	Preview preview;
	
	Button buttonStart, buttonStop;
	
    String[] utilityNames = {"VERY DARK", "DARK", "MEDIUM", "BRIGHT", "VERY BRIGHT"};
    int utilityAlphabetSize = utilityNames.length;
    double veryverydark   = 100;
    double veryverybright = 100000;
    double utilityScaleRate = Math.pow(veryverybright / veryverydark , 1 / ((double) utilityAlphabetSize));
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this.getApplicationContext();
		setContentView(R.layout.activity_main);
		
		buttonStart = (Button)findViewById(R.id.buttonStart);        
		buttonStart.setOnClickListener(startListener); 

		buttonStop = (Button)findViewById(R.id.buttonStop);      
		buttonStop.setOnClickListener(stopListener);
		
		status = (TextView)findViewById(R.id.status);
		status.setTypeface(Typeface.MONOSPACE);
		
		virtualLightFiltered = (TextView)findViewById(R.id.virtualLightFilteredContent);
		lightUtility =         (TextView)findViewById(R.id.lightUtility);
		
		mContext.registerReceiver(featureStatusUpdate, new IntentFilter("FEATURESTATUS"));
		mContext.registerReceiver(filteredLightUpdate, new IntentFilter("FILTEREDLIGHT"));
		mContext.registerReceiver(cameraPoll, new IntentFilter("CAMERAPOLL"));
		
		preview = new Preview(this);
        ((FrameLayout)findViewById(R.id.preview)).addView(preview);
        preview.setTextViews(
        		(TextView)findViewById(R.id.virtualLightNormContent),
        		(TextView)findViewById(R.id.virtualLightRawContent),
        		(TextView)findViewById(R.id.exposureValueContent)
        );
	}
	
	private BroadcastReceiver featureStatusUpdate = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        status.setText(intent.getExtras().getString("featurevalues"));
	    }
	};
	private BroadcastReceiver filteredLightUpdate = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	    	double filteredLight = intent.getDoubleExtra("filteredLight",0);
	    	virtualLightFiltered.setText(String.valueOf(filteredLight));
    		int X = (int) Math.floor(Math.log(filteredLight / veryverydark) / Math.log(utilityScaleRate));
    		X = (X <0) ? 0 : X;
    		X = (X>=utilityAlphabetSize) ? utilityAlphabetSize-1 : X;
    		lightUtility.setText(utilityNames[X]);
    		int XColor = (int) (50 + 180 * ((double) X/ (double) (utilityAlphabetSize-1)));
    		lightUtility.setBackgroundColor(Color.rgb(XColor, XColor, XColor));

	    }
	};	
	private BroadcastReceiver cameraPoll = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intentFromService) {
	    	Log.d(TAG, "receivedPoll");
	    	double light = preview.getLight();
	    	Intent intentToService = new Intent(ControlRecording.this, RecordData.class);
	        intentToService.putExtra("light", light);
	        startService(intentToService);
	    }
	};


	//Create an anonymous implementation of OnClickListener
	private OnClickListener startListener = new OnClickListener() {
		public void onClick(View v) {
			Log.d(TAG,"onClick() called - start button");
			Intent intent = new Intent(ControlRecording.this, RecordData.class);
			intent.putExtra("sPeriod", Integer.parseInt(mContext.getString(R.string.DEFAULTPERIOD)));
			intent.putExtra("numSamples", Integer.parseInt(mContext.getString(R.string.DEFAULTNUMSAMPLES)));
			intent.putExtra("refreshPeriod", Integer.parseInt(mContext.getString(R.string.DEFAULTREFRESHPERIOD)));
			startService(intent);
			buttonStart.setEnabled(false);
			buttonStop.setEnabled(true);
		}
	};

	// Create an anonymous implementation of OnClickListener
	private OnClickListener stopListener = new OnClickListener() {
		public void onClick(View v) {
			buttonStart.setEnabled(true);
			buttonStop.setEnabled(false);
			Log.d(TAG,"onClick() called - stop button");
			Intent intent = new Intent(ControlRecording.this, RecordData.class);
			stopService(intent);
		} 
	};

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		try{
			mContext.unregisterReceiver(filteredLightUpdate);
			mContext.unregisterReceiver(featureStatusUpdate);
			mContext.unregisterReceiver(cameraPoll);
		}
		catch(Exception e) {
			Log.d(TAG, "Unable to destroy receiver");
		}
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		try{
			mContext.unregisterReceiver(filteredLightUpdate);
			mContext.unregisterReceiver(featureStatusUpdate);
			mContext.unregisterReceiver(cameraPoll);
		}
		catch(Exception e) {
			Log.d(TAG, "Unable to destroy receiver");
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		mContext.registerReceiver(filteredLightUpdate, new IntentFilter("FILTEREDLIGHT"));
		mContext.registerReceiver(featureStatusUpdate, new IntentFilter("FEATURESTATUS"));
		mContext.registerReceiver(cameraPoll, new IntentFilter("CAMERAPOLL"));
		super.onResume();
	}
}
