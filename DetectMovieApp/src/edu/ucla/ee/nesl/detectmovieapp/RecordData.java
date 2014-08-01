package edu.ucla.ee.nesl.detectmovieapp;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Environment;
import android.os.IBinder;
import android.text.format.Time;
import android.util.Log;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;

public class RecordData extends Service {
	public static final String TAG = "RecordData";
	public static boolean isServiceInitialized = false;
	private SensorManager sManager;
	private mSensorEventListener eventListener;
	private int samplingPeriod;
	private int numSamples;
	private int refreshPeriod;
	long curr_time, last_time;
	double currAccMag = 0.0, currLight = 0.0;
	private double[] lightBuf;
	private double[] accBuf;
	boolean gotEnoughData = false;
	int windowStart = 0;
	int currIndex = 0;
	
	public static final int NUM_SENSOR_VALUES = 5;
	
	volatile float[] lightSensorBuf = new float[NUM_SENSOR_VALUES];
	
	boolean getLightFromCamera = true;
	
	boolean writeToFile = true;
	SoundPool soundPool;
	
	int sound;
	int lastActionType = -1;
	
	// records all new sensor data to a buffer specified at instatiation
	private class SensorRecorder implements SensorEventListener {
		private float[] valueBuf;
		
		public SensorRecorder (float[] targetValueBuf) {
			valueBuf = targetValueBuf;
		}
		
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		public void onSensorChanged(SensorEvent event) {
			for (int i = 0; i < event.values.length; i++) {
				valueBuf[i] = event.values[i];
			}
		}
	}
	
	DataOutputStream dataStream;
	
	boolean isFileOpen = false;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	
	public void onCreate() {
		Log.d(TAG, "onCreate() - Creating Service");
	}
	
	public void instantiateBuffer() {
		currIndex = 0;
		lightBuf = new double[numSamples*2];
		accBuf = new double[numSamples*2];
	}
	
	public int onStartCommand(Intent intent, int flags, int startId) {
		double light = intent.getDoubleExtra("light", -1);
		if(light != -1) {
			writeData(light);
			return 0;
		}
		
		SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		Sensor lightSensor = sensorManager.getSensorList(Sensor.TYPE_LIGHT).get(0);
		
		return 0;
	}
	
	public void pollLightValue() {
		Intent intent = new Intent("CAMERAPOLL");
		sendBroadcast(intent);
	}
	
	public void setSamplingPeriod(int sPeriod) {
		samplingPeriod = sPeriod;
	}
	
	public int getSamplingPeriod() {
		return samplingPeriod;
	}
	
	public void setNumSamples(int numSamples) {
		this.numSamples = numSamples;
	}
	
	public int getNumSamples() {
		return numSamples;
	}
	
	public void setRefreshPeriod(int refreshPeriod) {
		this.refreshPeriod = refreshPeriod;
	}
	
	public int getRefreshPeriod() {
		return refreshPeriod;
	}
	
	public DataOutputStream openBufferedWriter(String fileName) {
		int bufferSize = 8000;
		File file = new File (Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fileName);
		if (!file.exists()) {
			try {
				file.createNewFile();
				Log.d(TAG, "Created file " + fileName + " for writing" );
			}
			catch (IOException e) {
				Log.e(TAG, "Failed to create " + file.toString());
			}
		}
		else {
			Log.e(TAG, "File exists. Trying to write to existing file");
		}	
		
		DataOutputStream dataStream = null;
		try	{
		    dataStream = new DataOutputStream (new BufferedOutputStream  (new FileOutputStream (file, true), bufferSize));   
		}
		catch(Exception e) {
			Log.e(TAG, "Unable to open file for writing");
		}
		return dataStream;
	}
	
	public void writeData(double cameraLight) {
		try {		
			String timeStr = Long.toString(System.currentTimeMillis());
			String cameraStr = Double.toString(cameraLight);
			String dataStr = timeStr + "," + cameraStr					
				+ "," + Float.toString(lightSensorBuf[0]) + "\n";
			
			dataStream.writeUTF(dataStr);
			Log.d("sensor-data", dataStr);
			dataStream.flush();
		}
		catch(Exception ex) {
			Log.e(TAG, "Unable to write data to file");
		}
	}
	
	public void closeBufferedWriter(DataOutputStream opStream) {
		try {
			opStream.close();
			isFileOpen = false;
		}
		catch (Exception ex) {
			Log.e(TAG, "Trying to close file which is not open");
		}
	}
	
	public void openFileForLogging() {
		if(!isFileOpen) {
			final Date currTime = new Date();
			final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT-8"));
			String timeStr = sdf.format(currTime);
			String dataFileName = "data" + timeStr + ".txt";
			dataStream = openBufferedWriter(dataFileName);
			isFileOpen = true;
		}
	}
	
	private class mSensorEventListener implements SensorEventListener {
		@Override
		public void onAccuracyChanged(Sensor arg0, int arg1) {
		}

		public double getAccMagnitude(float a, float b, float c) {
			double grav = SensorManager.GRAVITY_EARTH;
			double totalForce = 0.0;            
			totalForce += Math.pow(a/grav, 2.0); 
            totalForce += Math.pow(b/grav, 2.0); 
            totalForce += Math.pow(c/grav, 2.0); 
            totalForce = Math.sqrt(totalForce);
            totalForce *= 310;
			//return Math.sqrt((double)(a*a + b*b + c*c));
            return totalForce;
		}
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			float[] values = event.values;
			if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				currAccMag = getAccMagnitude(values[0], values[1], values[2]);
			}
			if(event.sensor.getType() == Sensor.TYPE_LIGHT) {
				currLight = (double)values[0];
			}
			curr_time = System.currentTimeMillis();
			pollLightValue();
		}
	}
}
