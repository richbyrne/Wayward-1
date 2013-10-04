package com.wayward.wifiscanner;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

//This service will push scanned logs to a server at timed intervals.

public class UploadService extends Service
{
	// Used to name the file
	String _deviceID = "";
	// ServerURI
	String _serverURI = "http://horizab1.miniserver.com/~richard/uploadToServer.php";
	// "http://horizab1.miniserver.com/~richard/uploadToServer.php";

	// File path to grab the logfile from
	final String logPath = Environment.getExternalStorageDirectory().getAbsolutePath();

	// TODO: logfile name - this needs to change to something more meaningful,
	// maybe
	// DevId_Log_date
	private String _logFileName = "/wifi_Log.txt/";

	private WifiManager manager;
	private WifiLock _wifi_lock;

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		super.onStartCommand(intent, flags, startId);

		// TODO: Need to pass in the AP Name here so we know what to connect
		// to/what credentials

		Bundle myExtras = intent.getExtras();
		_logFileName = myExtras.getString("LOG_NAME");
		_deviceID = myExtras.getString("DEVICE_ID");

		// connect to access point and upload if successful
		if (connectToAP())
			uploadToServer();

		// /Make sure the network is forgotten
		closeDownWifi();

		return START_NOT_STICKY;
	}

	private boolean connectToAP()
	{
		// Attempt to connect to access point and wait until we are connted
		// (infinite while loop? - while disconnected)
		manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		// If Wifi is on then turn it off
		if (!manager.isWifiEnabled())
		{
			manager.setWifiEnabled(true);
		}

		_wifi_lock = manager.createWifiLock("upload_lock");
		_wifi_lock.acquire();
		// connect to the Access point we want to connect to
		// String SSID = "Wayward";
		String SSID = "UoN-guest";
		// TODO: There may also be a password string too String password="";

		WifiConfiguration wifiConf = new WifiConfiguration();
		// wifiConf.BSSID = "\"" + BSSID + "\"";
		wifiConf.SSID = "\"" + SSID + "\""; // this needs to be in quotation
											// marks
		// wep network:
		// wifiConf.wepKeys[0] = "\"" + networkPass + "\"";
		// wifiConf.wepTxKeyIndex = 0;
		// wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
		// wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
		//

		// conf.preSharedKey = "\""+ networkPass +"\""; // WPA NETWORK

		// Open Network
		// wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

		// Add the configuration to the manager
		manager.addNetwork(wifiConf);

		// Connect to the correct network
		// manager.setWifiEnabled(true);
		// Get list of configs and connect to the one that we want
		List<WifiConfiguration> list = manager.getConfiguredNetworks();
		for (WifiConfiguration i : list)
		{
			if (i.SSID != null && i.SSID.equals("\"" + SSID + "\""))
			{
				manager.disconnect();
				manager.enableNetwork(i.networkId, true);
				manager.reconnect();

				// Give time to connect
				try
				{
					Thread.sleep(5000);

				}
				catch (InterruptedException ie)
				{
					// Handle exception
				}
				// TODO:Return once connected

				return true;
			}

		}

		// False if unable to connect
		return false;

	}

	// Connect to the server and upload etc.
	public void uploadToServer()
	{
		// Create and destroy all objects in here, keep it as self contained as
		// it can be, no need to have trailing things

		HttpURLConnection connection = null;

		int bytesRead, bytesAvailable, bufferSize;
		byte[] buffer;
		int maxBufferSize = 1024;// 1 * 1024 * 1024;
		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary = "*****";

		String filePath = logPath + "/" + _logFileName;
		File fileToUpload = new File(filePath);

		// Check the file actually exists
		if (!fileToUpload.isFile())
		{
			// TODO: Break here, and upload an error instead, NO LOGFILE FOR
			// DEVICE (and device ID);
		}
		else
		{

			try
			{

				FileInputStream fileInputStream = new FileInputStream(fileToUpload);

				URL url = new URL(_serverURI);
				connection = (HttpURLConnection) url.openConnection();

				// Allow Inputs & Outputs
				connection.setDoInput(true);
				connection.setDoOutput(true);
				connection.setUseCaches(false);

				// Enable POST method
				connection.setRequestMethod("POST");

				connection.setRequestProperty("Connection", "Keep-Alive");
				connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
				DataOutputStream outputStream = null;

				outputStream = new DataOutputStream(connection.getOutputStream());

				// outputStream = new DataOutputStream(
				// connection.getOutputStream());
				outputStream.writeBytes(twoHyphens + boundary + lineEnd);
				Locale.getDefault();
				SimpleDateFormat s = new SimpleDateFormat("dd-MM-yy_HH:mm:ss", Locale.getDefault());
				String currentDateTime = s.format(new Date());
				String serverFileName = fileToUpload + "_" + currentDateTime + ".txt";
				outputStream.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + serverFileName + "\"" + lineEnd);
				outputStream.writeBytes(lineEnd);

				bytesAvailable = fileInputStream.available();
				bufferSize = Math.min(bytesAvailable, maxBufferSize);
				buffer = new byte[bufferSize];

				// Read file
				bytesRead = fileInputStream.read(buffer, 0, bufferSize);

				while (bytesRead > 0)
				{
					outputStream.write(buffer, 0, bufferSize);
					bytesAvailable = fileInputStream.available();
					bufferSize = Math.min(bytesAvailable, maxBufferSize);
					bytesRead = fileInputStream.read(buffer, 0, bufferSize);
				}

				outputStream.writeBytes(lineEnd);
				outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

				// Responses from the server (code and message)
				int serverResponseCode = connection.getResponseCode();
				// String serverResponseMessage =
				// connection.getResponseMessage();

				fileInputStream.close();
				outputStream.flush();
				outputStream.close();

				// If response is okay
				if (serverResponseCode == 200)
				{
					// Put into the shared preferences that we have uploaded
					// successfully, and don't need to for 24 hours
					resetLogFile();
				}

			}
			catch (Exception ex)
			{
				// Exception handling
				Log.d("UPLOAD_ERROR", "Upload error: " + ex.toString());

				SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

				SharedPreferences.Editor editor = sharedPrefs.edit();
				editor.putBoolean("WAIT_FOR_RETRY", true);
				editor.commit();

			}

			finally
			{
				// always disconnect to stop wasting battery on perminant wifi
				// connection
				// manager.disconnect();
				if (connection != null)
				{
					connection.disconnect();
				}

				if (_wifi_lock != null)
					;
				{
					_wifi_lock.release();
				}
			}

		}
	}

	private void resetLogFile()
	{
		// Logfile has uploaded and can be deleted, as well as letting other
		// service know that we are waiting another 24 hours

		File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), _logFileName);
		// delete the file and make a clean fresh logFile
		if (file.delete())
		{
			String logFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + _logFileName;
			File logFile = new File(logFilePath);
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
				catch (Exception ex)
				{

				}
			}
		}

		// update shared preferences with new time
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		long currentTime = new Date().getTime();
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putLong("LAST_UPLOAD_TIME", currentTime);
		editor.putBoolean("WAIT_FOR_RETRY", false);
		editor.commit();

	}

	// Disconnect from the wifi network, and forget the config to avoid issues
	// with the other service
	private void closeDownWifi()
	{

	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}
}
