package edu.ucla.ee.nesl.lightdump;

// imports {{{

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

import android.os.Environment;
import android.os.Handler;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import android.widget.Button;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.EditText;

import android.graphics.Color;
import android.graphics.PorterDuff.Mode;

// }}}


public class LightDump extends Activity {

	private class StateFragment extends Fragment { // {{{

		public SensorEventListener dumpToLog;

		public Handler uiHandler;

		// status
		public boolean sensorListenerRegistered = false;
		public boolean currentlyRecording = false;
		public boolean currentlyListening = false;
		public int originalBrightnessMode;
		public int originalBrightnessLevel;
	
		// for file logging
		public File logFile = null;
		public PrintWriter logFileOut = null;
		
		// for network logging
		public Thread socketThread = null;
		public PrintWriter netOut;
		public Socket netSocket;
		public boolean currentlyConnected = false;
		public String test = "hi";
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
				super.onCreate(savedInstanceState);
				
				// retain this fragment
				setRetainInstance(true);
		}
	} // }}}
	
	private StateFragment stateFragment = null;

	// Context vars {{{

	// system
	private SensorManager sensorManager;
	private Sensor lightSensor;

	// gui
	private TextView networkStatusText;
	private CheckBox fileEnable;
	private CheckBox networkEnable;
	private EditText networkAddr;
	private EditText networkPort;
	private CheckBox logcatEnable;
	private Button startStopRec;

	// }}}

	@Override
	protected void onCreate(Bundle savedInstanceState) { // {{{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_light_dump);

		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

		networkStatusText = (TextView) findViewById(R.id.activity_light_dump_network_status);
		fileEnable = (CheckBox) findViewById(R.id.activity_light_dump_file_enable);
		logcatEnable = (CheckBox) findViewById(R.id.activity_light_dump_logcat_enable);
		networkEnable = (CheckBox) findViewById(R.id.activity_light_dump_network_enable);
		networkAddr = (EditText) findViewById(R.id.activity_light_dump_network_addr);
		networkPort = (EditText) findViewById(R.id.activity_light_dump_network_port);
		startStopRec = (Button) findViewById(R.id.activity_light_dump_start_stop_rec);

		// find the retained fragment on activity restarts
		FragmentManager fm = getFragmentManager();
		stateFragment = (StateFragment) fm.findFragmentByTag("state");

		// create the fragment and data the first time
		if (stateFragment == null) {
			// add the fragment
			stateFragment = new StateFragment();
			fm.beginTransaction().add(stateFragment, "state").commit();

			stateFragment.dumpToLog = new SensorEventListener() {
				public void onAccuracyChanged (Sensor sensor, int accuracy) {
				}

				public void onSensorChanged (SensorEvent event) {
					String dataStr = Long.toString(System.currentTimeMillis()) + "," + Float.toString(event.values[0]);
					
					if (stateFragment.currentlyListening) {
						if (fileEnable.isChecked()) {
							//Log.d("state fragment", stateFragment.test);
							stateFragment.logFileOut.println(dataStr);
						}

						if (networkEnable.isChecked()) {
							printlnToNetwork(dataStr);
						}

						if (logcatEnable.isChecked()) {
							Log.d("light-dump", dataStr);
						}
					}
				}
			};

			stateFragment.uiHandler = new Handler();
		}

		if (! stateFragment.sensorListenerRegistered) {
			sensorManager.registerListener(stateFragment.dumpToLog, lightSensor, SensorManager.SENSOR_DELAY_FASTEST);

			stateFragment.sensorListenerRegistered = true;
		}
	} // }}}

	@Override
	protected void onResume() { // {{{
		super.onResume();

		if (stateFragment.currentlyRecording) {
			// uh-oh, we're supposed to be recording, better re-register the listener
			startListening();
		}
	} // }}}

	@Override
	protected void onPause () { // {{{
		super.onPause();

		if (stateFragment.currentlyRecording) {
		  stopListening();
		}
	} // }}}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) { // {{{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.light_dump, menu);
		return true;
	} // }}}

	@Override
	protected void onSaveInstanceState(Bundle outState) { // {{{
	    super.onSaveInstanceState(outState);
	    
	    // TODO save the REST of the state (currentlyRecording... thread pointer, etc?)
	    
	    outState.putBoolean("file_enable_checkbox_enabled", fileEnable.isEnabled());
	    outState.putBoolean("network_enable_checkbox_enabled", networkEnable.isEnabled());
	    outState.putBoolean("logcat_enable_checkbox_enabled", logcatEnable.isEnabled());
	    outState.putBoolean("network_addr_textview_enabled", networkAddr.isEnabled());
	    outState.putBoolean("network_port_textview_enabled", networkPort.isEnabled());
			outState.putString("toggle_button_text", startStopRec.getText().toString());
			outState.putString("network_status_text", networkStatusText.getText().toString());
	} // }}}
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) { // {{{
	    super.onRestoreInstanceState(savedInstanceState);

	    fileEnable.setEnabled(savedInstanceState.getBoolean("file_enable_checkbox_enabled"));
	    networkEnable.setEnabled(savedInstanceState.getBoolean("network_enable_checkbox_enabled"));
	    logcatEnable.setEnabled(savedInstanceState.getBoolean("logcat_enable_checkbox_enabled"));
	    networkAddr.setEnabled(savedInstanceState.getBoolean("network_addr_textview_enabled"));
	    networkPort.setEnabled(savedInstanceState.getBoolean("network_port_textview_enabled"));
			startStopRec.setText(savedInstanceState.getString("toggle_button_text"));
			networkStatusText.setText(savedInstanceState.getString("network_status_text"));
	} // }}}

	private class SocketThread implements Runnable { // {{{
		public static final String TAG = "SocketThread";
		@Override
		public void run() {
			try {
				InetAddress serverAddr = InetAddress.getByName(networkAddr.getText().toString());
				stateFragment.netSocket = new Socket(serverAddr, Integer.parseInt(networkPort.getText().toString()));
				stateFragment.netOut = new PrintWriter(new BufferedWriter (new OutputStreamWriter (stateFragment.netSocket.getOutputStream())), true);
				Log.i(TAG, "Opened Socket");

				stateFragment.currentlyConnected = true;
			} catch (Exception e) {
				Log.e(SocketThread.TAG, "Caught Exception");
				e.printStackTrace();

				handleNetworkError(e);
			}
		}
	} // }}}
	private void handleNetworkError (Exception e) { // {{{
		stateFragment.uiHandler.post(new Runnable() {
			@Override
			public void run() {
				networkStatusText.setText("Oops, network error.  Other recordings will continue.");
			}
		});
	} // }}}
	private void printlnToNetwork (String toWrite) { // {{{
		try {
			if (stateFragment.currentlyConnected) {
				stateFragment.netOut.print(toWrite + "\n");
				stateFragment.netOut.flush();
			}
		} catch (Exception e) {
			Log.e(SocketThread.TAG, "Caught Exception");
			e.printStackTrace();

			handleNetworkError(e);
		}
	} // }}}

	private void startListening () { // {{{
		//sensorManager.registerListener(stateFragment.dumpToLog, lightSensor, SensorManager.SENSOR_DELAY_FASTEST);
		stateFragment.currentlyListening = true;
		// FIXME
		// manually registering and unregistering the listener
		// seems to be simply ignored by some phones
		// so I switched to a method of keeping the listener on and 
		// then listening selectively, which wastes some resources
	} // }}}
	private void stopListening () { // {{{
		//sensorManager.unregisterListener(stateFragment.dumpToLog);
		stateFragment.currentlyListening = false;
	} // }}}

	public void toggleRecording (View view) { // {{{
	  if (stateFragment.currentlyRecording) {
	    stopRecording();
	  } else {
	    startRecording();
	  }
	 } // }}}
	protected void startRecording () { // {{{
		stateFragment.test="foobar started";
		// update the ui FIRST

		startStopRec.setText(R.string.button_stop);

		fileEnable.setEnabled(false);
		logcatEnable.setEnabled(false);
		networkEnable.setEnabled(false);
		networkAddr.setEnabled(false);
		networkPort.setEnabled(false);
		// the above disables the *gui widgets*, nothing else

		stateFragment.originalBrightnessMode = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, -1);
		Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

		stateFragment.originalBrightnessLevel = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
		Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 0);


		// set up resources SECOND

		if (networkEnable.isChecked()) {
			stateFragment.socketThread = new Thread(new SocketThread());
			stateFragment.socketThread.start();
		}

		if (fileEnable.isChecked()) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
			String timestamp = sdf.format(new Date(System.currentTimeMillis()));

			stateFragment.logFile = new File(Environment.getExternalStorageDirectory(), "light-dump-" + timestamp + ".txt");
			try {
				stateFragment.logFileOut = new PrintWriter(stateFragment.logFile);
			} catch (FileNotFoundException e) {
				Log.e("Error opening file", e.toString());
			}
		}

		// start listening LAST
		startListening();

		stateFragment.currentlyRecording = true;
	} // }}}
	protected void stopRecording () { // {{{
		stateFragment.test = "foobar stopped";

		// stop listening FIRST
		stopListening();

		// cleanup resources SECOND

		if (stateFragment.socketThread != null) {
			stateFragment.socketThread.interrupt();
			stateFragment.socketThread = null;
		}

		if (stateFragment.logFileOut != null) {
			stateFragment.logFileOut.flush();
			stateFragment.logFileOut.close();
			stateFragment.logFileOut = null;
		}

		// update ui LAST

		startStopRec.setText(R.string.button_start);
		networkStatusText.setText(R.string.network_status_empty);

		fileEnable.setEnabled(true);
		logcatEnable.setEnabled(true);
		networkEnable.setEnabled(true);
		networkAddr.setEnabled(true);
		networkPort.setEnabled(true);
		// the above enables the *gui widgets*, nothing else

		Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, stateFragment.originalBrightnessMode);

		if (stateFragment.originalBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
			Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, stateFragment.originalBrightnessLevel);
		}

		stateFragment.currentlyRecording = false;
	} // }}}
}
