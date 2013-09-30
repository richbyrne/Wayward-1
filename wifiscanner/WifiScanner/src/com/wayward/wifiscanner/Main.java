package com.wayward.wifiscanner;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

public class Main extends Activity
{

	// Default interval
	private int _interval = 60;
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
	private StepReceiver mStepreceived;
	private AccessPointRec mAPRec;

	// Alarm manager variables;
	AlarmManager _alarm;
	PendingIntent _pintent;

	// Start off logging service, retrieve interval etc.
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_ui);

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

		// Set the checkbox on to make sure that as long as the apps running it
		// will start

		// logginEnabled.setChecked(true);

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
		// Get the inerval value if already set when logging is turned on
		try
		{
			_interval = Integer.parseInt(scanInterval.getText().toString());
		}
		catch (Exception e)
		{
			// default interval time
			_interval = 60;
		}

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, 10);
		Intent intent = new Intent(Main.this, WifiScannerService.class);
		_pintent = PendingIntent.getService(Main.this, 0, intent, 0);
		_alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		_alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), _interval * 1000, _pintent);

		//
		// // Start the service to log and scan for wifiPoints
		// Intent startIntent = new Intent(this, WifiScannerService.class);
		// startIntent.putExtra("INTERVAL_TIME", _interval);
		// startService(startIntent);
		// logRunning.setText("Logging...");

		// Start the Step Counting
		// startService(new Intent(this, StepCountService.class));
	}

	private void stopLogging()
	{
		// stop the service and clean up
		// stopService(new Intent(this, WifiScannerService.class));
		// stopService(new Intent(this, StepCountService.class));
		_alarm.cancel(_pintent);
		logRunning.setText("Logging Stopped.");

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
		}
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

}
