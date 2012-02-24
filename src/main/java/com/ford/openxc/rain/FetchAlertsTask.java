package com.ford.openxc.rain;

import java.util.TimerTask;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.openxc.measurements.Latitude;
import com.openxc.measurements.Longitude;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;

import com.openxc.remote.NoValueException;

import com.openxc.VehicleService;

import android.os.Handler;

import android.util.Log;

import android.widget.TextView;

public class FetchAlertsTask extends TimerTask {
    private final String TAG = "FetchAlertsTask";
    private final String API_URL =
        "http://api.wunderground.com/api/dcffc57e05a81ad8/alerts/q/";

    private VehicleService mVehicleService;
    private Handler mHandler;
    private TextView mAlertStatusView;

    public FetchAlertsTask(VehicleService vehicleService, Handler handler,
            TextView alertStatusView) {
        mVehicleService = vehicleService;
        mHandler = handler;
        mAlertStatusView = alertStatusView;
    }

    public void run() {
        double latitudeValue;
        double longitudeValue;

        try {
            Latitude latitude = (Latitude)
                    mVehicleService.get(Latitude.class);
            Longitude longitude = (Longitude)
                    mVehicleService.get(Longitude.class);

            latitudeValue = latitude.getValue().doubleValue();
            longitudeValue = longitude.getValue().doubleValue();
        } catch(UnrecognizedMeasurementTypeException e) {
            return;
        } catch (NoValueException e) {
            Log.d(TAG, "No vehicle data available, using fake lat/long");
            latitudeValue = 28;
            longitudeValue = 131;
        }

        Log.d(TAG, "Querying for weather alerts near " + latitudeValue +
                ", " + longitudeValue);
        StringBuilder urlBuilder = new StringBuilder(API_URL);
        urlBuilder.append(latitudeValue + ",");
        urlBuilder.append(longitudeValue + ".json");

        final URL wunderground;
        try {
            wunderground = new URL(urlBuilder.toString());
        } catch (MalformedURLException e) {
            Log.w(TAG, "URL we created isn't valid", e);
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                fetchAlerts(wunderground);
            }
        }).start();
    }

    private void fetchAlerts(URL url) {
        StringBuilder builder = new StringBuilder();
        try {
            HttpURLConnection wundergroundConnection =
                (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(
                    wundergroundConnection.getInputStream());

            InputStreamReader is = new InputStreamReader(in);
            BufferedReader br = new BufferedReader(is);
            String line;
            while((line = br.readLine()) != null) {
                builder.append(line);
            }
        } catch (IOException e) {
            Log.w(TAG, "Unable to fetch alert information", e);
            return;
        }

        final boolean hasAlerts;
        try {
            JSONObject result = new JSONObject(builder.toString());
            if(!result.has("alerts")) {
                hasAlerts = false;
                Log.d(TAG, "Wunderground didn't recognize the lat/long");
            } else {
                JSONArray alerts = result.getJSONArray("alerts");
                hasAlerts = alerts.length() > 0;
                Log.d(TAG, "Wunderground reports an alert: " + hasAlerts);
            }
        } catch(JSONException e) {
            Log.w(TAG, "Received invalid JSON", e);
            return;
        }

        mHandler.post(new Runnable() {
            public void run() {
                mAlertStatusView.setText("" + hasAlerts);
            }
        });
    }
}
