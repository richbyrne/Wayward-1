package com.wayward.wifiscanner;

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
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
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

	public static boolean mScansRecorded = false;

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

	private Logger mLogger;

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	// public void onStart(Intent intent, int startId)

	{
		_sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mLogger = new Logger(this);

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
		// _sharedPrefs =
		// PreferenceManager.getDefaultSharedPreferences(this);
		// SharedPreferences.Editor editor = _sharedPrefs.edit();
		// long currentTime = new Date().getTime();
		// editor.putLong("LAST_UPLOAD_TIME", currentTime);
		// editor.putBoolean("WAIT_FOR_RETRY", false);
		// editor.commit();

		_wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		// Set up the thread to do the scanning operation
		beginScanThread();
		//
		// Intent i = registerReceiver(null, new
		// IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		// Bundle b = i.getExtras();

		return START_NOT_STICKY;
	}

	public float getBatteryLevel()
	{
		Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

		return ((float) level / (float) scale) * 100.0f;
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

		// Unregister all receivers
		// this.unregisterReceiver(batteryInfoReceiver);
		this.unregisterReceiver(wifiScansAvailable);

	}

	// @Override
	private void beginScanThread()
	{

		// float battLevel = getBatteryLevel();
		// mLogger.WriteLog(null, "Battery Level: " + String.valueOf(battLevel),
		// null);
		// if (battLevel > 10.0)
		// {
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

		if (!mScansRecorded)
		{
			// WriteLog(null,
			// "--------------------unable to scan access points on previous scan------------");
			Log.d("Unable_to_scan", new Date().toGMTString() + ": --------------------unable to scan access points on previous scan------------");
		}

		// WriteLog(null, new Date().toGMTString() +
		// ", Starting Scan...");
		Log.d("Starting_scan", new Date().toGMTString() + ": Starting SCAN");
		// _totalWriteTime = 0;
		mScansRecorded = false;
		boolean success = _wifiManager.startScan();

		if (!success)
			mLogger.WriteLog(null, new Date().toGMTString() + ", Unsucessful starting wifimanager scan!", null);
		// }
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
					mLogger.WriteLog(null, dateTime + ", No access points in scan.", null);
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
					mLogger.WriteLog(scannedResults, null, _deviceID);
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
				mLogger.WriteLog(null, dateTime + " Unable to find access points.", null);
			}
		}

		catch (Exception ex)
		{
			mLogger.WriteLog(null, "Error Scanning for access points: " + ex.toString(), null);
		}

	}

}
