package com.wayward.wifiscanner;

import java.util.Calendar;
import java.util.Date;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity
{

	SharedPreferences sharedPrefs;
	// Default interval
	private int _interval = 10;
	int _stepCount = 0;
	// private boolean running = false;

	private CheckBox logginEnabled;
	private EditText scanInterval;
	private TextView logRunning;
	private TextView stepRate;

	private TextView apNum;
	// Upload thread
	// private Thread _serverUploadThread;
	// private boolean _running = false;
	// Recievers
	// private StepReceiver mStepreceived;
	private AccessPointRec mAPRec;

	// Alarm manager variables;
	AlarmManager _alarm;
	AlarmManager _uploadAlarm;
	PendingIntent _pintent;
	PendingIntent _uploadPIntent;

	// For upload service
	public static boolean mUploadToServer = false;
	public static String mLogFileName = "";
	public static String mDeviceID = "";
	private boolean mUploadWithinTimeLimit = false; // used to only upload once
													// in 24 hours

	boolean mScanning = false;

	// Start off logging service, retrieve interval etc.
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_ui);

		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Link up checkbox and the scan interval number
		logginEnabled = (CheckBox) findViewById(R.id.logginEnabled);
		scanInterval = (EditText) findViewById(R.id.scanInterval);

		// Add logging text
		logRunning = (TextView) findViewById(R.id.logRunning);
		stepRate = (TextView) findViewById(R.id.stepRate);

		apNum = (TextView) findViewById(R.id.textView1);

		// Set checkbox listener
		logginEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
		{

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				if (isChecked)
					startLogging();
				else if (!isChecked)
					stopLogging();
			}
		});
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// Register the step counter for the UI - this can also go in one of the
		// other services

		// IntentFilter filter;
		// filter = new IntentFilter(StepCountService.STEP_COUNT_IS);
		// mStepreceived = new StepReceiver();
		// registerReceiver(mStepreceived, filter);

		IntentFilter filt;
		filt = new IntentFilter(WifiScannerService.AP_NUM_IS);
		mAPRec = new AccessPointRec();
		registerReceiver(mAPRec, filt);

	}

	// startLogging
	private void startLogging()
	{
		if (!mScanning)
		{
			// Get the inerval value if already set when logging is turned on
			try
			{
				_interval = Integer.parseInt(scanInterval.getText().toString());
			}
			catch (Exception e)
			{
				// default interval time
				_interval = 10;
			}

			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.SECOND, 10);
			Intent intent = new Intent(MainActivity.this, WifiScannerService.class);
			_pintent = PendingIntent.getService(MainActivity.this, 0, intent, 0);
			_alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			_alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), _interval * 1000, _pintent);
			mScanning = true;
			//

		}

		// Start the Step Counting
		// startService(new Intent(this, StepCountService.class));
	}

	private void stopLogging()
	{
		if (mScanning)
		{
			mScanning = false;
			// stop the service and clean up
			stopService(new Intent(this, WifiScannerService.class));
			// stopService(new Intent(this, StepCountService.class));
			_alarm.cancel(_pintent);
			logRunning.setText("Logging Stopped.");
		}

	}

	public class StepReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			// Get out the info
			Bundle extras = intent.getExtras();
			_stepCount = extras.getInt("THE_STEP_COUNT", 0);
			logRunning.setText("Total Steps:" + Integer.toString(_stepCount));
			stepRate.setText("Step Rate: ");
		}
	}

	public class AccessPointRec extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Bundle extras = intent.getExtras();
			int accessPointsInRange = extras.getInt("THE_NUMBER_OF_AP", 0);
			apNum.setText(Integer.toString(accessPointsInRange));

			// Check to see if we need to upload the logfile
			mUploadWithinTimeLimit = timeDifference();
			// if (mUploadToServer && mUploadWithinTimeLimit)
			// waitForUploadToComplete();
			// beginUploadProcess();
		}
	}

	// private void beginUploadProcess()
	private void waitForUploadToComplete()
	{
		stopLogging();
		// mUploadToServer = false;
		// mUploadWithinTimeLimit = false;

		// // TODO - get upload file
		// String logFile = mLogFileName;
		//
		// // Begin the file upload
		// UploadToServer upload = new UploadToServer(logFile, this);
		// boolean success = upload.startUpload();
		// // if success then save the current time of the last upload
		// if (success)
		// {
		// SharedPreferences.Editor editor = sharedPrefs.edit();
		// long currentTime = new Date().getTime();
		// editor.putLong("LAST_UPLOAD_TIME", currentTime);
		// editor.commit();
		// }

		// Resume logging

		// wait while uploading to the server
		while (mUploadToServer)
		{
			if (!mUploadToServer)
				break;
		}
		startLogging();
	}

	// Alarm Manager test - instead of using the wifiScanner Thread service
	public class AlarmManagerRec extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intint)
		{
			Intent startIntent = new Intent(context, WifiScannerService.class);
			startIntent.putExtra("INTERVAL_TIME", _interval);
			startService(startIntent);
		}
	}

	private boolean timeDifference()
	{
		long storedTime = 0;

		storedTime = sharedPrefs.getLong("LAST_UPLOAD_TIME", 0);

		long currentTime = new Date().getTime();

		long difference = currentTime - storedTime;
		int days = (int) (difference / (1000 * 60 * 60 * 24));
		int hours = (int) ((difference - (1000 * 60 * 60 * 24 * days)) / (1000 * 60 * 60));
		int min = (int) (difference - (1000 * 60 * 60 * 24 * days) - (1000 * 60 * 60 * hours)) / (1000 * 60);

		// if last upload was over 24 hours ago
		// if (hours >= 24)
		if (min >= 1)
		{
			// Upload to server
			return true;

		}

		return false;
	}

	@Override
	public void onPause()
	{
		super.onPause();

		unregisterReceiver(mAPRec);
	}

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();
		// beginUploadProcess();
	}
}
