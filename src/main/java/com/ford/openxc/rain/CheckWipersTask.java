package com.ford.openxc.rain;

import java.util.TimerTask;

import java.io.IOException;

import org.apache.http.client.HttpClient;

import org.apache.http.client.methods.HttpGet;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import org.apache.http.impl.client.DefaultHttpClient;

import com.openxc.measurements.Latitude;
import com.openxc.measurements.Longitude;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.WindshieldWiperStatus;

import com.openxc.NoValueException;

import com.openxc.VehicleManager;

import android.os.Handler;

import android.util.Log;

import android.widget.TextView;

public class CheckWipersTask extends TimerTask {
    private final String TAG = "CheckWipersTask";
    private final String WUNDERGROUND_URL =
        "http://www.wunderground.com/weatherstation/VehicleWeatherUpdate.php";

    private VehicleManager mVehicle;
    private Handler mHandler;
    private TextView mWiperStatusView;

    public CheckWipersTask(VehicleManager vehicle, Handler handler,
            TextView wiperStatusView) {
        mVehicle = vehicle;
        mHandler = handler;
        mWiperStatusView = wiperStatusView;
    }

    public void run() {
        final Latitude latitude;
        final Longitude longitude;
        final WindshieldWiperStatus wiperStatus;
        try {
            latitude = (Latitude) mVehicle.get(Latitude.class);
            longitude = (Longitude) mVehicle.get(Longitude.class);
            wiperStatus = (WindshieldWiperStatus) mVehicle.get(
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
    }

    private void uploadWiperStatus(Latitude latitude, Longitude longitude,
            final WindshieldWiperStatus wiperStatus) {
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
        final String finalUri = uriBuilder.toString();

        new Thread(new Runnable() {
            public void run() {
                final HttpClient client = new DefaultHttpClient();
                HttpGet request = new HttpGet(finalUri);
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
        }).start();
    }
}
