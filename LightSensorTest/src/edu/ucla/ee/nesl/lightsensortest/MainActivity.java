package edu.ucla.ee.nesl.lightsensortest;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.view.Menu;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import android.view.View;
import android.widget.TextView;

import android.os.SystemClock;

//import java.util.List;

public class MainActivity extends Activity {
	protected SensorManager sensorManager;
	protected Sensor lightSensor;
	
	protected TextView updateText;
	protected TextView valueText;
	protected TextView freqText;
	protected TextView accText;
	
	protected int updateCount = 0;
	protected long startTime;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		updateText = (TextView) findViewById(R.id.update_status);
		valueText = (TextView) findViewById(R.id.value_status);
		freqText = (TextView) findViewById(R.id.frequency_desc);
		accText = (TextView) findViewById(R.id.accuracy_desc);
		
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		lightSensor = sensorManager.getSensorList(Sensor.TYPE_LIGHT).get(0);
		
		startTime = System.currentTimeMillis();
		
		sensorManager.registerListener(new SensorEventListener () {
			public void onAccuracyChanged (Sensor sensor, int accuracy) {
				accText.setText("Accuracy is " + Integer.toString(accuracy) + "\n\n");
			}
			
			public void onSensorChanged (SensorEvent event) {
				updateCount++;
				updateText.setText("Updated "
				  + Integer.toString(updateCount) + " times");
				
				valueText.setText("Current value: "
				  + Float.toString(event.values[0]) + "\n");
			}
		}, lightSensor, SensorManager.SENSOR_DELAY_FASTEST);
		
	}
	
	public void checkFreq (View view) {
		double duration = ((double) (System.currentTimeMillis() - startTime)) / 1000.0;
		
		double freq = ((double) updateCount) / duration;
		
		freqText.setText(String.format("%.3f", freq).replaceAll("(\\.\\d+?)0*$", "$1"));
		
		startTime = System.currentTimeMillis();
		updateCount = 0;

		updateText.setText("Updated "
		  + Integer.toString(updateCount) + " times");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
