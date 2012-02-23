package com.ford.openxc.rain;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.client.methods.HttpGet;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import com.openxc.VehicleService;
import com.openxc.remote.NoValueException;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.ford.openxc.rain.R;
import com.openxc.measurements.WindshieldWiperStatus;
import com.openxc.measurements.Latitude;
import com.openxc.measurements.Longitude;

public class RainMonitorActivity extends Activity {

    private static String TAG = "RainMonitorActivity";
    private final String WUNDERGROUND_URL =
        "http://www.wunderground.com/weatherstation/VehicleWeatherUpdate.php";
    private final String API_URL =
        "http://api.wunderground.com/api/dcffc57e05a81ad8/alerts/q/";

    private VehicleService mVehicleService;
    private boolean mIsBound;
    private TextView mWiperStatusView;
    private TextView mAlertsStatusView;
    private final Handler mHandler = new Handler();

    private String result = null;

    /*
     * THis is my main runnable function to get Alerts
     */
    private Runnable mFetchAlertsTask = new Runnable() {
		public void run() {
			final String latitudeTest;
			final String longitudeTest;

        /*try {
            latitude = (Latitude) mVehicleService.get(Latitude.class);
            longitude = (Longitude) mVehicleService.get(Longitude.class);
            wiperStatus = (WindshieldWiperStatus) mVehicleService.get(
                    WindshieldWiperStatus.class);


        } catch(UnrecognizedMeasurementTypeException e) {
            return null;
        } catch (NoValueException e) {
        	return null;
        }*/

			latitudeTest = "28";
			longitudeTest = "131";
			StringBuilder urlBuilder = new StringBuilder(API_URL);
			urlBuilder.append(latitudeTest + ",");
			urlBuilder.append(longitudeTest + ".json");
			URL wunderground;
			try {
				String testURL = urlBuilder.toString();
				wunderground = new URL(API_URL + "37,-122.json");
			} catch (MalformedURLException e) {
				return;
			}

			try {
				HttpURLConnection wundergroundConnection =
                    (HttpURLConnection) wunderground.openConnection();
				InputStream in = new BufferedInputStream(
                        wundergroundConnection.getInputStream());
				InputStreamReader is = new InputStreamReader(in);
				BufferedReader br = new BufferedReader(is);
				String line = br.readLine();
				while (line != null) {
					result += line.trim();
					System.out.println(line);
					line = br.readLine();
				}
				//System.out.println("----------");
				//System.out.println(result);
				//JSONObject json = (JSONObject) new JSONValue().parse(result);
				//System.out.println(json.get("alerts"));
			} catch (IOException e) {
				return;
			}

			mHandler.post(new Runnable() {
                public void run() {
                	String test;
                    if(result != null) {
                        test = "true";
                    } else {
                        test = "false";
                    }
                    mAlertsStatusView.setText(test);
                }
            });

            mHandler.postDelayed(this, 300000);
		}
    };

    private Runnable mCheckWipersTask = new Runnable() {
        public void run() {
            final Latitude latitude;
            final Longitude longitude;
            final WindshieldWiperStatus wiperStatus;
            try {
                latitude = (Latitude) mVehicleService.get(Latitude.class);
                longitude = (Longitude) mVehicleService.get(Longitude.class);
                wiperStatus = (WindshieldWiperStatus) mVehicleService.get(
                        WindshieldWiperStatus.class);
            } catch(UnrecognizedMeasurementTypeException e) {
                return;
            } catch(NoValueException e) {
                Log.w(TAG, "One or more of the required measurements" +
                        " didn't have a value", e);
                return;
            }

            final boolean wiperStatusValue;
            wiperStatusValue = wiperStatus.getValue().booleanValue();

            mHandler.post(new Runnable() {
                public void run() {
                    String wiperText;
                    if(wiperStatusValue) {
                        wiperText = "on";
                    } else {
                        wiperText = "off";
                    }
                    mWiperStatusView.setText(wiperText);
                }
            });
            uploadWiperStatus(latitude, longitude, wiperStatus);
            // Repeat every 5 minutes or 300,000ms
            mHandler.postDelayed(this, 300000);
        }

        private void uploadWiperStatus(Latitude latitude, Longitude longitude,
                WindshieldWiperStatus wiperStatus) {
            double latitudeValue;
            double longitudeValue;
            boolean wiperStatusValue;
            latitudeValue = latitude.getValue().doubleValue();
            longitudeValue = longitude.getValue().doubleValue();
            wiperStatusValue = wiperStatus.getValue().booleanValue();

            if(!wiperStatusValue) {
                Log.d(TAG, "Wipers are off -- not uploading");
                return;
            }

            StringBuilder uriBuilder = new StringBuilder(WUNDERGROUND_URL);
            // TODO need to update API to accept boolean instead of speed
            int wiperSpeed = 0;
            if(wiperStatusValue) {
                wiperSpeed = 1;
            }
            uriBuilder.append("?wiperspd=" + wiperSpeed);
            uriBuilder.append("&lat=" + latitudeValue);
            uriBuilder.append("&lon=" + longitudeValue);

            final HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(uriBuilder.toString());
            try {
                HttpResponse response = client.execute(request);
                final int statusCode = response.getStatusLine().getStatusCode();
                if(statusCode != HttpStatus.SC_OK) {
                    Log.w(TAG, "Error " + statusCode +
                        " while uploading wiper status");
                } else {
                    Log.d(TAG, "Wiper status (" + wiperStatus + ") uploaded " +
                        "successfully");
                }
            } catch(IOException e) {
                Log.w(TAG, "Error while uploading wiper status", e);
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            Log.i(TAG, "Bound to VehicleService");
            mVehicleService = ((VehicleService.VehicleServiceBinder)service
                    ).getService();
            mIsBound = true;

            mHandler.postDelayed(mCheckWipersTask, 100);
            mHandler.postDelayed(mFetchAlertsTask, 100);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.w(TAG, "RemoteVehicleService disconnected unexpectedly");
            mVehicleService = null;
            mIsBound = false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.i(TAG, "Rain monitor created");
        mWiperStatusView = (TextView) findViewById(R.id.wiper_status);
    }

    @Override
    public void onResume() {
        super.onResume();
        bindService(new Intent(this, VehicleService.class),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mIsBound) {
            Log.i(TAG, "Unbinding from vehicle service");
            unbindService(mConnection);
            mIsBound = false;
        }
    }
}
