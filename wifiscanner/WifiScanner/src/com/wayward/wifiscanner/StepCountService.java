package com.wayward.wifiscanner;

import java.util.Date;
import java.util.List;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class StepCountService extends Service
{

	// service variables
	private PowerManager.WakeLock _wakeLock;
	// private NotificationManager mNoteMang;

	// Accelerometer Variables
	private SensorManager myManager;
	private List<Sensor> sensors;
	private Sensor accSensor;

	// Step Count variables
	private int mStepCount = 0;
	private long _stepRate = 0;
	// for calories to be sent to activity
	// private String saveCalories;

	// magnitude array variables
	private Double magnitudeVector;
	private int ii; // this is used as array list position
	private double[] magValues = new double[50]; // array to hold magnitude
													// values

	// Hill detection and step count variables
	private double mPeakMean;
	private double mForwardSlope;
	private double mBackwardSlope;
	private double mPeakCount;
	private double mPeakAccumilate;

	Thread _stepRateThread;
	private boolean _running = false;

	// String for Broadcast intent (to send step count)
	public static final String STEP_COUNT_IS = "STEP_COUNT";

	private long _startTime = 0;
	private long _currentTime = 0;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		super.onStartCommand(intent, flags, startId);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		// really hold a partial wakelock but doesnt send accelerometer data to
		// the user. //removed partial wakelock for now
		_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StepCountService");
		// Get the wakelock - ensure at least the CPU remains running
		_wakeLock.acquire();

		// set sensor manager
		myManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensors = myManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (sensors.size() > 0)
		{
			accSensor = sensors.get(0);
		}
		// register the sensor listener
		myManager.registerListener(mySensorListener, accSensor, SensorManager.SENSOR_DELAY_NORMAL);

		_running = true;
		StartStepRateThread();

		this.registerReceiver(this.screenOffRec, new IntentFilter(Intent.ACTION_SCREEN_OFF));

		_startTime = new Date().getTime();

		// Always keep running in the background
		return START_STICKY;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if (_wakeLock != null)
			_wakeLock.release();

		if (_stepRateThread != null)
			if (_running = true)
			{
				try
				{
					_running = false;
					_stepRateThread.join(5000);
					_stepRateThread = null;
				}
				catch (InterruptedException iEx)
				{
					Log.d("ERROR_STOPPING_STEP_THREAD", "Error stopping step count thread: " + iEx.toString());
				}
			}

	}

	private BroadcastReceiver screenOffRec = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			// Unregester
			myManager.unregisterListener(mySensorListener, accSensor);

			// re-register
			myManager.registerListener(mySensorListener, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
		}
	};

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	private void StartStepRateThread()
	{
		// Define what the thread will do in this code
		Runnable stepRuns = new Runnable()
		{
			public void run()
			{

				while (_running)
				{
					calculateStepRate();
					// Wait interval
					try
					{
						Thread.sleep(30000);
					}
					catch (InterruptedException ex)
					{
						Log.d("CALCULATE_STEP_RATE", "Step Rate Thread interrupted.");
					}
				}
			}

		};
		// Actually start the Thread that has been defined above
		_stepRateThread = new Thread(stepRuns);
		_stepRateThread.start();
	}

	// Step count thread/services
	// sensor listener
	private final SensorEventListener mySensorListener = new SensorEventListener()
	{
		public void onSensorChanged(SensorEvent event)
		{

			// want x y z data of stuff
			// smooth the data
			// _wakeLock.acquire();
			if (ii < 50)
			{
				// call add magnitude and add to arrayList
				magnitudeVector = AddMagnitude(event.values[0], event.values[1], event.values[2]);
				magValues[ii] = magnitudeVector;
				// System.out.println("Mag: " + magValues[ii]);
				ii = ii + 1;// increment counter

			}
			else
			{
				// call hill detection
				HillDetection();

				// call step count
				CountSteps();

				// reset magnitude array and arrayList counter
				ii = 0;
			}

		}

		// Do not use but needed as part of SensorEventListener
		public void onAccuracyChanged(Sensor sensor, int accuracy)
		{

		}

	};

	// Add Magnitude
	public Double AddMagnitude(float x, float y, float z)
	{
		double magVector = 0.0;
		float xR, yR, zR;

		xR = x;
		yR = y;
		zR = z;
		// convert to doubles
		// xR = Double.parseDouble(Integer.toString(x));
		// yR = Double.parseDouble(Integer.toString(y));
		// zR = Double.parseDouble(Integer.toString(z));

		// square variables
		xR = xR * xR;
		yR = yR * yR;
		zR = zR * zR;

		// add values
		float mag = (xR + yR + zR);

		// calculate magnitude vector
		magVector = Math.sqrt(mag);

		// return magnitude
		return magVector;
	}

	/*-------------------------------------------
	 * Hill detection - Detect hills and peaks in
	 * the walking data.
	 ---------------------------------------------*/
	public void HillDetection()
	{
		mForwardSlope = 0;
		mBackwardSlope = 0;
		mPeakCount = 0;
		mPeakAccumilate = 0;
		mPeakMean = 0;

		for (int i = 1; i < 49; i++)
		{
			mForwardSlope = magValues[i + 1] - magValues[i];
			mBackwardSlope = magValues[i] - magValues[i - 1];

			if (mForwardSlope <= 0 && mBackwardSlope > 0)
			{
				mPeakCount = mPeakCount + 1;
				mPeakAccumilate = mPeakAccumilate + magValues[i];
			}// end if
		}// end for

		if (mPeakCount != 0)
		{
			mPeakMean = (mPeakAccumilate / mPeakCount);
		}
	}

	/*-------------------------------------------
	 * Count Steps - Re-run over hill detection and 
	 * count peaks above threshold that are likely 
	 * steps.
	 ---------------------------------------------*/
	// Count steps
	public void CountSteps()
	{
		// previously 0.9 (3/10/2010)
		double varVal = 0.7;
		double value = 0;

		for (int i = 1; i < 49; i++)
		{
			mForwardSlope = magValues[i + 1] - magValues[i];
			mBackwardSlope = magValues[i] - magValues[i - 1];
			value = (mPeakMean * varVal);

			// Detect Steps - threshold value previously 11.5 (3/08/2010)
			if (mForwardSlope <= 0 && mBackwardSlope > 0 && magValues[i] > value && magValues[i] > 10.8)
			{
				mStepCount = mStepCount + 1;
			}// end if
		}// end for

		SendSteps();
	}

	private void calculateStepRate()
	{
		_currentTime = new Date().getTime();
		long totalTime = _currentTime - _startTime;
		// TODO: Every 30 seconds

		// avoid divide by zero
		if (mStepCount > 0 && totalTime > 0)
		{
			// TODO: _stepRate = (mStepCount / totalTime);

		}
	}

	/*----------------------------------------------
	 * Send new step count to activity screen.
	 ------------------------------------------------*/
	public void SendSteps()
	{
		Intent intent = new Intent(STEP_COUNT_IS);

		//
		intent.putExtra("THE_STEP_COUNT", mStepCount);
		intent.putExtra("STEP_RATE", _stepRate);
		//
		sendBroadcast(intent);

	}

}
