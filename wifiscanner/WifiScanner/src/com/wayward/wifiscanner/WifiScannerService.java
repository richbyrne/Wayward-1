package com.wayward.wifiscanner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class WifiScannerService extends Service
{

	/*
	 * Service will log the wifi status of the application, even when the
	 * application is not in the foreground.
	 */

	// The BSSID of the Access Point to Upload the LogFiles to
	// private static String BSSID = "54:78:1a:5f:2b:a0";

	private ReentrantLock _lock = new ReentrantLock();
	// private Condition _condition = _lock.newCondition();

	private PowerManager.WakeLock _wakeLock;
	private PowerManager _pm;

	private boolean _scansRecorded = false;

	private static String TAG = "Wifi Logging Service";
	// private boolean _connected = false;
	// private float _batteryLevel = 0.0f;
	private int _interval = 60;
	private boolean _running = false;
	// private boolean _retry = false;
	// private boolean _pauseScanning = false;
	private String _deviceID = "0";
	private String _logFileName = "wifiLog.txt";
	// private boolean _waitForRetry = false;

	// Make thread object - easier for global referencing, e.g. start and
	// stopping from within the program
	private Thread _scanThread;
	private Thread _retryThread;

	// Shared preferences to store the last upload for checking
	SharedPreferences _sharedPrefs;

	// Wifi Manager
	WifiManager _wifiManager;

	public static final String AP_NUM_IS = "AP_NUM";

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	// public void onStart(Intent intent, int startId)

	{

		_logFileName = "wifiLog.txt";
		_pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		_wakeLock = _pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiService");

		// Make sure the Wifi Never Sleeps
		Context context = getApplicationContext();
		ContentResolver cr = context.getContentResolver();
		int set = android.provider.Settings.System.WIFI_SLEEP_POLICY_NEVER;

		android.provider.Settings.System.putInt(cr, android.provider.Settings.System.WIFI_SLEEP_POLICY, set);

		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		// get IMEI
		_deviceID = tm.getDeviceId();
		_logFileName = _deviceID + "_" + _logFileName;
		super.onStart(intent, startId);// flags, startId);
		Log.d(TAG, "Service Started");

		// Set up receivers:

		// Any Battery Changes
		// this.registerReceiver(this.batteryInfoReceiver, new
		// IntentFilter(Intent.ACTION_BATTERY_CHANGED));

		// When wifiScans are ready
		this.registerReceiver(this.wifiScansAvailable, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

		Locale.getDefault();

		Bundle myExtras = intent.getExtras();
		_interval = myExtras.getInt("INTERVAL_TIME");
		_running = true;

		// default pref information
		// _sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		// SharedPreferences.Editor editor = _sharedPrefs.edit();
		// long currentTime = new Date().getTime();
		// editor.putLong("LAST_UPLOAD_TIME", currentTime);
		// editor.putBoolean("WAIT_FOR_RETRY", false);
		// editor.commit();

		_wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		// Set up the thread to do the scanning operation
		beginScanThread();

		return START_NOT_STICKY;
	}

	// Battery info changed
	// private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver()
	// {
	// @Override
	// public void onReceive(Context context, Intent intent)
	// {
	// // Get battery information
	// int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
	// int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
	//
	// int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
	// boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
	// status == BatteryManager.BATTERY_STATUS_FULL;
	//
	// float batteryPct = level / (float) scale;
	// // Get the percentage
	// batteryPct = batteryPct * 100;
	//
	// // Only want to print on a significant change, i.e. when dropping a
	// // %
	// if (_batteryLevel != batteryPct)
	// {
	// _batteryLevel = batteryPct;
	//
	// String batteryLog = "Battery Level: " + Float.toString(batteryPct) + "%";
	//
	// String dateTime = new Date().toGMTString();
	// WriteLog(null, dateTime + ": " + batteryLog);
	// }
	//
	// // If the battery is too low want to stop scanning until it is
	// // plugged in again
	// if (!_pauseScanning && _batteryLevel < 10 && !isCharging)
	// {
	// // Stop scanning and stuff
	// _pauseScanning = true;
	// }
	// // If we are now charging and scanning was paused
	// else if (_pauseScanning && isCharging || _batteryLevel > 10)
	// {
	// _pauseScanning = false;
	// }
	// // All other conditions keep scanning
	// else if (_batteryLevel > 10)
	// {
	// _pauseScanning = false;
	// }
	//
	// }
	// };

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if (_wakeLock.isHeld())
			_wakeLock.release();

		// _running = false;
		// _retry = false;

		// if (_scanThread != null)
		// {
		// try
		// {
		// Kill asap
		// _scanThread.join(5000);
		// _scanThread = null;
		// }
		// catch (InterruptedException iEx)
		// {

		// }
		// }
		// if (_retryThread != null)
		// {
		// try
		// {
		// Kill asap
		// _retryThread.join(5000);
		// _retryThread = null;
		// }
		// catch (InterruptedException iEx)
		// {
		// }
		// }

		// Unregister all receivers
		// this.unregisterReceiver(batteryInfoReceiver);
		this.unregisterReceiver(wifiScansAvailable);

	}

	// @Override
	private void beginScanThread()
	{
		// Define what the thread will do in this code
		// Runnable scannerThread = new Runnable()
		// {
		// public void run()
		// {

		// _interval = _interval * 1000;

		// while (_running)
		// {

		// only scan if enough battery, pauseScanning will be true
		// only if battery is < 10%
		// if (!_pauseScanning)
		// {
		// scanAccessPoints();

		if (!_wakeLock.isHeld() || _wakeLock == null)
		{

			// Get the wakelock - ensure at least the CPU
			// remains running
			_wakeLock.acquire();
		}
		if (!_wifiManager.isWifiEnabled())
		{
			_wifiManager.setWifiEnabled(true);
		}

		// Log out each time the scan starts

		if (!_scansRecorded)
		{
			// WriteLog(null,
			// "--------------------unable to scan access points on previous scan------------");
			Log.d("Unable_to_scan", new Date().toGMTString() + ": --------------------unable to scan access points on previous scan------------");
		}

		// WriteLog(null, new Date().toGMTString() +
		// ", Starting Scan...");
		Log.d("Starting_scan", new Date().toGMTString() + ": Starting SCAN");
		// _totalWriteTime = 0;
		_scansRecorded = false;
		boolean success = _wifiManager.startScan();

		if (!success)
			WriteLog(null, new Date().toGMTString() + ", Unsucessful starting wifimanager scan!");
		// }

		// Wait interval
		try
		{

			// long startTime = System.currentTimeMillis();
			// Block here until all scan results have been printed
			// out, exit loop incase no results were returned.
			// while (_finishedWriting == false)
			// {
			// long currentTime = System.currentTimeMillis();
			// // Wait for _interval + 60 seconds as a fail safe
			// if (currentTime - startTime > _interval + 60000)
			// {
			// // Log and break out of the loop
			// // WriteLog(null,
			// //
			// "--------------Timer Expired-NO APS found in scan-------------");
			// Log.d("Time Expired", new Date().toGMTString() +
			// ": Failsafe timer has expired, breaking out of loop");
			// break;
			// }
			// }

			// long waitTime = _interval - _totalWriteTime;
			// WriteLog(null, new Date().toGMTString() +
			// ", Waiting for: " + Long.toString(waitTime));//
			// Integer.toString(_interval));
			// Log.d("WWaiting", new Date().toGMTString() +
			// ": Waiting for: " + Long.toString(waitTime));

			// SystemClock.sleep(waitTime);// _interval -
			// _totalWriteTime);

			// WriteLog(null, new Date().toGMTString() +
			// ", Interval over");
			// Log.d("Interval Over", new Date().toGMTString() +
			// ": Interval OVER");

		}
		catch (Exception ex)
		{
			Log.d("SCAN_THREAD_ERROR", "Scan AP Thread interrupted.");
		}
		// }
		// }

		// };
		// Actually start the Thread that has been defined above
		// _scanThread = new Thread(scannerThread);
		// _scanThread.start();
	}

	// scans available
	private BroadcastReceiver wifiScansAvailable = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			// WriteLog(null, "");
			// WriteLog(null, "--------- Wifi Scans Available-------------");
			Log.d("WIFI_SCANS_AVAILABLE", new Date().toGMTString() + ": Wifi Scans Available");
			List<ScanResult> scannedResults = _wifiManager.getScanResults();
			processScans(scannedResults);

		}
	};

	public void processScans(List<ScanResult> scannedResults)
	{
		try
		{

			String dateTime = new Date().toGMTString();
			if (scannedResults != null)
			{
				if (scannedResults.isEmpty())
				{
					WriteLog(null, dateTime + ", No access points in scan.");
					Intent intent = new Intent(AP_NUM_IS);
					intent.putExtra("THE_NUMBER_OF_AP", 0);
					sendBroadcast(intent);
				}

				else
				{
					Intent intent = new Intent(AP_NUM_IS);
					intent.putExtra("THE_NUMBER_OF_AP", scannedResults.size());
					sendBroadcast(intent);

					// Write out the scans
					WriteLog(scannedResults, null);
					// close down the list.
					scannedResults.clear();
				}

			}
			else
			{
				Intent intent = new Intent(AP_NUM_IS);
				intent.putExtra("THE_NUMBER_OF_AP", 0);
				sendBroadcast(intent);
				// Log that we couldn't find any access points
				WriteLog(null, dateTime + " Unable to find access points.");
			}
		}

		catch (Exception ex)
		{
			WriteLog(null, "Error Scanning for access points: " + ex.toString());
		}

	}

	// Write to log file
	// private void WriteLog(String logText)
	private void WriteLog(List<ScanResult> scannedResults, String logText)
	{

		// Check that we have access to the SDCARD

		boolean mExternalStorageAvailable = false;
		boolean mExternalStorageWriteable = false;
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state))
		{
			// We can read and write the media
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		}
		else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
		{
			// We can only read the media
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		}
		else
		{
			// Something else is wrong. It may be one of many other states, but
			// all we need
			// to know is we can neither read nor write
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}

		// Only write if the storage is available
		if (mExternalStorageAvailable && mExternalStorageWriteable)
		{
			// boolean uploadToServer = false;
			String logFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + _logFileName;
			File logFile = new File(logFilePath);

			// If the logfile doesn't already exists
			if (!logFile.exists())
			{
				try
				{
					// Create a new log file
					logFile.createNewFile();
					BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
					buf.append("DeviceID: " + _deviceID);
					buf.newLine();
					buf.close();

				}
				catch (IOException e)
				{
					Log.d("ERROR MAKING FILE", e.toString());
				}
			}
			// else
			// {
			// Otherwise just append to it
			try
			{

				// Lock the threading until all writing is completed
				_lock.lock();

				BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));

				// do itteration here
				if (scannedResults != null)
				{

					if (!scannedResults.isEmpty())
					{
						// start stopwatch
						long startTime = System.currentTimeMillis();
						for (ScanResult scanResult : scannedResults)
						{

							// if (scanResult.BSSID.equals(BSSID))
							// uploadToServer = true;

							// Write the ScanResults
							buf.append(new Date().toGMTString() + "," + scanResult.SSID + "," + scanResult.BSSID + "," + scanResult.frequency + "," + scanResult.level + ",");
							buf.newLine();

							if (scanResult.BSSID.equals(""))
							{
								Main.mUploadToServer = true;
								Main.mDeviceID = _deviceID;
								Main.mLogFileName = _logFileName;
							}
						}
						// _totalWriteTime = System.currentTimeMillis() -
						// startTime;
						_scansRecorded = true;

						scannedResults.clear();

						// Trigger the upload if the correct BSSID has been
						// found

					}
				}
				// Just print out the normal log text, and not the AP Text
				else
				{
					if (logText != null)
					{
						buf.append(logText);
						buf.newLine();
					}
				}

				// Always close the buffer
				if (buf != null)
					buf.close();

				// Check if we need to upload the logfile because the BSSID AP
				// is in range
				// if (uploadToServer)
				// {
				// uploadToServer();
				// }

			}
			catch (IOException e)
			{
				Log.d("ERROR WRITING TO LOG FILE", e.toString());
			}
			finally
			{
				_lock.unlock();
				// _finishedWriting = true;
			}
		}
	}

	// }

	// private void uploadToServer()
	// {
	// _waitForRetry = _sharedPrefs.getBoolean("WAIT_FOR_RETRY", false);
	//
	// // If we need to wait for a retry, start the retry
	// // waiting thread.
	// if (_waitForRetry && !_retry)
	// {
	// _retry = true;
	// startRetryThread();
	// }
	//
	// else if (timeDifference() && !_waitForRetry)
	// {
	//
	// // upload to server if the BSSID is found
	// Intent startIntent = new Intent(this, UploadService.class);
	// startIntent.putExtra("LOG_NAME", _logFileName);
	// startIntent.putExtra("DEVICE_ID", _deviceID);
	// startService(startIntent);
	// }
	// }

	// private void startRetryThread()
	// {
	// // Define what the thread will do in this code
	// Runnable retries = new Runnable()
	// {
	// public void run()
	// {
	//
	// while (_retry)
	// {
	// // Wait interval
	// try
	// {
	// // Wait half an hour (miliseconds)
	// Thread.sleep(1800000);
	// // Thread.sleep(60000);
	//
	// // break out of the loop
	// _retry = false;
	// _waitForRetry = false;
	// }
	// catch (InterruptedException ex)
	// {
	// Log.d("RETRY_THREAD_ERROR", "Retry thread died: " + ex.toString());
	// }
	// }
	// }
	//
	// };
	// // Actually start the Thread that has been defined above
	// _retryThread = new Thread(retries);
	// _retryThread.start();
	// }

	// Calculate the time difference between last log time and current time
	// private boolean timeDifference()
	// {
	// long storedTime = 0;
	// storedTime = _sharedPrefs.getLong("LAST_UPLOAD_TIME", 0);
	//
	// long currentTime = new Date().getTime();
	//
	// long difference = currentTime - storedTime;
	// int days = (int) (difference / (1000 * 60 * 60 * 24));
	// int hours = (int) ((difference - (1000 * 60 * 60 * 24 * days)) / (1000 *
	// 60 * 60));
	// int min = (int) (difference - (1000 * 60 * 60 * 24 * days) - (1000 * 60 *
	// 60 * hours)) / (1000 * 60);
	//
	// // if last upload was over 24 hours ago
	// // if (hours >= 24)
	// if (min >= 5)
	// {
	// // Upload to server
	// return true;
	// }
	//
	// else
	// {
	// // Don't upload
	// return false;
	// }
	// }

}
