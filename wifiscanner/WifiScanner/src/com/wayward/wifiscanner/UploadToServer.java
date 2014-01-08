package com.wayward.wifiscanner;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

//This service will push scanned logs to a server at timed intervals.

public class UploadToServer
{
	// Used to name the file
	String _deviceID = "";
	Context mContext;

	String _serverURI = "http://192.168.42.1/uploadToServer.php";

	// File path to grab the logfile from
	final String logPath = Environment.getExternalStorageDirectory().getAbsolutePath();

	// TODO: logfile name - this needs to change to something more meaningful,
	// maybe
	// DevId_Log_date
	private String mLogFileName = "/wifi_Log.txt/";

	private WifiManager manager;
	private WifiLock _wifi_lock;

	// @Override
	// public int onStartCommand(Intent intent, int flags, int startId)
	public UploadToServer(String fileName, Context context)
	{
		mContext = context;
		mLogFileName = fileName;

	}

	public boolean startUpload()
	{
		boolean success = false;
		try
		{
			String fullExternalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
			File dir = new File(fullExternalPath + "/WaitingForUpload/");

			if (connectToAP())
			{
				// for all the sub files in the folder
				for (File child : dir.listFiles())
				{
					if (uploadToServer(child))
						success = true;
				}
			}
		}

		catch (Exception ex)
		{
			Log.d("ERROR", "NO FILES TO UPLOAD");
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
			SharedPreferences.Editor editor = sharedPrefs.edit();

			Log.d("UPDATING_UPLOAD_TIME", "No files in folder, so updating timestamp.");
			long currentTime = new Date().getTime();
			editor.putLong("LAST_UPLOAD_TIME", currentTime);
			editor.commit();
		}
		finally
		{
			// /Make sure the network is forgotten
			closeDownWifi();
		}

		return success;
	}

	private boolean connectToAP()
	{
		// Attempt to connect to access point and wait until we are connted
		// (infinite while loop? - while disconnected)
		manager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
		// If Wifi is on then turn it off
		if (!manager.isWifiEnabled())
		{
			manager.setWifiEnabled(true);
		}

		_wifi_lock = manager.createWifiLock("upload_lock");
		_wifi_lock.acquire();
		// connect to the Access point we want to connect to
		// String SSID = "Wayward";
		String SSID = "WAYWARD";

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

		String networkPass = "wayw4rd1";
		wifiConf.preSharedKey = "\"" + networkPass + "\""; // WPA NETWORK

		// Add the configuration to the manager
		manager.addNetwork(wifiConf);

		// Connect to the correct network
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
	private boolean uploadToServer(File fileToUpload)
	{
		boolean success = false;
		// Create and destroy all objects in here, keep it as self contained as
		// it can be, no need to have trailing things

		HttpURLConnection connection = null;

		int bytesRead, bytesAvailable, bufferSize;
		byte[] buffer;
		int maxBufferSize = 1024;// 1 * 1024 * 1024;
		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary = "*****";

		// String filePath = logPath + "/" + mLogFileName;
		// File fileToUpload = new File(filePath);

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
				// SimpleDateFormat s = new
				// SimpleDateFormat("dd-MM-yy_HH:mm:ss", Locale.getDefault());
				// String currentDateTime = s.format(new Date());
				String serverFileName = fileToUpload.getName();// + "_" +
																// currentDateTime
				// + ".txt";
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
					// resetLogFile();

					// The file has been successfully uploaded so move it and
					// reset it
					Logger logger = new Logger(mContext);
					logger.MoveAndReset(true);
					success = true;
				}

			}
			catch (Exception ex)
			{
				// Exception handling
				Log.e("UPLOAD_ERROR", "Upload error: " + ex.toString());

				SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
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
				{
					_wifi_lock.release();
				}
			}

		}
		return success;
	}

	// Disconnect from the wifi network, and forget the config to avoid issues
	// with the other service
	private void closeDownWifi()
	{

		ConnectivityManager connManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		if (mWifi.isConnected())
		{
			int networkID = manager.getConnectionInfo().getNetworkId();
			manager.disconnect();
			boolean success = manager.removeNetwork(networkID);
			manager.saveConfiguration();
		}
	}
}
