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

public class RecordDataBackup extends Service {
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
	//volatile float[] gravSensorBuf  = new float[NUM_SENSOR_VALUES];
	//volatile float[] accelSensorBuf = new float[NUM_SENSOR_VALUES];
	//volatile float[] gyroSensorBuf  = new float[NUM_SENSOR_VALUES];
	//volatile float[] tempSensorBuf  = new float[NUM_SENSOR_VALUES];
	
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
	
	Filter filter = new LowPassFilter(0.95);
	// Filter filter = new ConstantFilter(5000);
	
	boolean isFileOpen = false;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	public void onCreate() {
		Log.d(TAG, "onCreate() - Creating Service");
		soundPool = new SoundPool(1, AudioManager.STREAM_RING,0);
		sound = soundPool.load(this, R.raw.detectmovie_sound, 1);
	}
	
	public void instantiateBuffer() {
		currIndex = 0;
		lightBuf = new double[numSamples*2];
		accBuf = new double[numSamples*2];
	}
	
	public void onDestroy() {
		Log.d(TAG, "onDestroy() - Stopping Service");
		try {
			sManager.unregisterListener(eventListener, sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
			sManager.unregisterListener(eventListener, sManager.getDefaultSensor(Sensor.TYPE_LIGHT));
			if(isFileOpen) {
				closeBufferedWriter(dataStream);
				isFileOpen = false;
			}
			isServiceInitialized = false;
		}
		catch(Exception e) {
			Log.e(TAG, "Failed unregistering llisteners");
		}
	}
	
	public int onStartCommand(Intent intent, int flags, int startId) {
		double light = intent.getDoubleExtra("light", -1);
		if(light != -1) {
			Log.d(TAG, "onStartCommand - received virtual Light data: '" + light + "'");
			processLightSample(light);
			return 0;
		}
		Log.d(TAG, "onStartCommand - Starting Service");
		setSamplingPeriod(intent.getIntExtra("sPeriod", Integer.parseInt(this.getString(R.string.DEFAULTPERIOD))));
		setNumSamples(intent.getIntExtra("numSamples", Integer.parseInt(this.getString(R.string.DEFAULTNUMSAMPLES))));
		setRefreshPeriod(intent.getIntExtra("refreshPeriod", Integer.parseInt(this.getString(R.string.DEFAULTREFRESHPERIOD))));
		Log.d(TAG, "numSamples = " + numSamples + ": samplingPeriod = " + samplingPeriod);
		if(!isServiceInitialized) {
			instantiateBuffer();
			sManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
			Log.d(TAG, "Created SensorManager Object");
			eventListener = new mSensorEventListener();
			sManager.registerListener(eventListener, 
					sManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
			sManager.registerListener(eventListener, 
					sManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_FASTEST);
			Log.d(TAG, "Registered Sensor Listener");
			last_time = System.currentTimeMillis();
			isServiceInitialized = true;
		}
		
		SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		Sensor lightSensor = sensorManager.getSensorList(Sensor.TYPE_LIGHT).get(0);
		//Sensor gravSensor = sensorManager.getSensorList(Sensor.TYPE_GRAVITY).get(0);
		//Sensor accelSensor = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
		//Sensor gyroSensor = sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).get(0);
		//Sensor tempSensor = sensorManager.getSensorList(Sensor.TYPE_GRAVITY).get(0);
		
		//sensorManager.registerListener(new SensorRecorder(lightSensorBuf), lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
		//sensorManager.registerListener(new SensorRecorder(gravSensorBuf), gravSensor, SensorManager.SENSOR_DELAY_NORMAL);
		//sensorManager.registerListener(new SensorRecorder(accelSensorBuf), accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
		//sensorManager.registerListener(new SensorRecorder(gyroSensorBuf), gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
		//sensorManager.registerListener(new SensorRecorder(tempSensorBuf), tempSensor, SensorManager.SENSOR_DELAY_NORMAL);

		
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
	
	public void writeData() {
		try {
			int start = (currIndex + 2*numSamples - refreshPeriod) % (2*numSamples);
			for(int i = start ; i < start + refreshPeriod; i++) {
		
				String timeStr = Long.toString(System.currentTimeMillis());
				String cameraStr = Double.toString(lightBuf[i % (2*numSamples)]);

				String dataStr = timeStr + "," + cameraStr					
						+ "," + Float.toString(lightSensorBuf[0])
						//+ "," + Float.toString(lightSensorBuf[1])
						//+ "," + Float.toString(lightSensorBuf[2])
						
						//+ "," + Float.toString(gravSensorBuf[0])
						//+ "," + Float.toString(gravSensorBuf[1])
						//+ "," + Float.toString(gravSensorBuf[2])
						
						//+ "," + Float.toString(accelSensorBuf[0])
						//+ "," + Float.toString(accelSensorBuf[1])
						//+ "," + Float.toString(accelSensorBuf[2])
						
						//+ "," + Float.toString(gyroSensorBuf[0])
						//+ "," + Float.toString(gyroSensorBuf[1])
						//+ "," + Float.toString(gyroSensorBuf[2])
						
						//+ "," + Float.toString(tempSensorBuf[0])
						
						+ "\n";
				
				//dataStream.writeUTF(dataStr);
				Log.d("sensor-data", dataStr);

			}
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
	
	public void processBuffer() {
		String isMoving = "false";
		double normFactor = this.normFactor();
		int numCoeffs = 13;
		double lightCoeffs[] = new double[numCoeffs];

		String str = "";
		if(!gotEnoughData) {
			str += "NOT ENOUGH DATA YET!!\n\n";
		}
		for(int i=0; i<numCoeffs; i++) {
			lightCoeffs[i] = goertzel(lightBuf,i)/normFactor;
			str += String.format("a[%2d] = %10.3f\n",i,lightCoeffs[i]);
		}
		str += "\nUser is Currently Moving: %s";

		double accCoeff1 = goertzel(accBuf, 1);
		double accCoeff2 = goertzel(accBuf, 2);
		double accCoeff3 = goertzel(accBuf, 3);
		double accCoeff4 = goertzel(accBuf, 4);
		double accCoeff5 = goertzel(accBuf, 5);
				
		if(accCoeff1 + accCoeff2 + accCoeff3 + accCoeff4 + accCoeff5 > 10000) {
			isMoving = "true";
			if(isFileOpen)
				closeBufferedWriter(dataStream);
		} else {
			if(writeToFile) {
				openFileForLogging();
				writeData();
			}
			//if(checkIfMovie(lightCoeffs)) { 
			//	soundPool.play(sound, 1.0f, 1.0f, 0, 0, 1.0f);
			//}
		}
			 
		updateFeatures(String.format(str,isMoving));
	}
	
	boolean checkIfMovie(double[] a) {
		// boolean isMovie = false;
		double a1 = a[1];
		double a2 = a[2];
		double a3 = a[3];
		double a4 = a[4];
		double a5 = a[5];
		double a6 = a1 + a2 + a3 + a4 + a5;
		double a7 = a1/a2;
		double a8 = a1/a3;
		double a9 = a1/a4;
		double a10 = a1/a5;
		double a11 = a2/a3;
		double a12 = a2/a4;
		double a13 = a2/a5;
		double a14 = a3/a4;
		double a15 = a3/a5;
		double a16 = a4/a5;
		DecisionTree dt = new DecisionTree(a1, a2, a3, a4, a5,
				a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16);
		return dt.isMovie();
		
		/*Log.d(TAG, "Coeff Sum = " + a1 + a2 + a3 + a4 + a5);
		if(a1 + a2 + a3 + a4 + a5 > 0.5)
			isMovie = true;
		return isMovie;*/
	}
	public double goertzel(double [] data, int freqIndex) {
		double s_prev = 0;
        double s_prev2 = 0;
        double coeff = 2 * Math.cos( (2*Math.PI*freqIndex) / (double)numSamples);
        double s;
        for (int i = windowStart; i < windowStart + numSamples; i++) {
        	double sample = data[i % (2*numSamples)];
            s = sample + coeff*s_prev  - s_prev2;
            s_prev2 = s_prev;
            s_prev = s;
        }
        double power = s_prev2*s_prev2 + s_prev*s_prev - coeff*s_prev2*s_prev;
        return power;
	}

	public double normFactor() {
        double s = 0;
        double sample;
        for (int i = windowStart; i < windowStart + numSamples; i++) {
        	sample = lightBuf[i % (2*numSamples)];
            s += sample * sample;
        }
        return s / numSamples;
	}
	public void updateFeatures(String msg) {
		Intent intent = new Intent("FEATURESTATUS");
		intent.putExtra("featurevalues",msg);
		sendBroadcast(intent);
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
			if(curr_time - last_time >= samplingPeriod) {
				last_time = last_time + samplingPeriod;
				Log.d(TAG, "pollingLightValue");
				if(getLightFromCamera) {
					pollLightValue();
				} else {
					processLightSample(currLight);
				}
			}
		}
	}
	
	// The "Filter" interface and the various filters
	interface Filter {
		double filter(double light);
	}
	class LowPassFilter implements Filter {
		double lastValue = -1;
		double inertia;
		public LowPassFilter(double inertia) {
			this.inertia = inertia;
			if(inertia < 0 || inertia > 1) {
				Log.e(TAG, "Inertia has to be between 0 and 1, defaulting to 1/2");
				this.inertia = 1/2;
			}
		}
		public double filter(double light) {
			if(lastValue == -1) lastValue = light;
			double newLight = lastValue * inertia + light * (1-inertia);
			lastValue = newLight;
			return newLight;
		}
	}
	class SuppressFilter implements Filter {
		public SuppressFilter() {}
		public double filter(double light) {
			return -1;
		}
	}
	class NoFilter implements Filter {
		public NoFilter() {}
		public double filter(double light) {
			return light;
		}
	}
	class ConstantFilter implements Filter {
		double constant;
		public ConstantFilter(double constant) {
			this.constant = constant;
		}
		public double filter(double light) {
			return constant;
		}
	}
	double filter(double light) {
		return this.filter.filter(light);
	}
	
    public void processLightSample(double light) {
		Log.d(TAG, "processLightSample");
		double filteredLight = filter(light);
		lightBuf[currIndex] = filteredLight;
		if(filteredLight != -1) {
			Intent intent = new Intent("FILTEREDLIGHT");
			intent.putExtra("filteredLight", filteredLight);
			sendBroadcast(intent);
			accBuf[currIndex] = currAccMag;
			currIndex++;
			currIndex %= 2*numSamples;
			if (currIndex == numSamples) gotEnoughData = true;
			if( (currIndex + 2*numSamples - windowStart) % (2*numSamples) >= numSamples) {
				processBuffer();
				windowStart += refreshPeriod;
				windowStart %= 2*numSamples;
			}
		}
	}
}
