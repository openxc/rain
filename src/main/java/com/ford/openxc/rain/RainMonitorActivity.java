package com.ford.openxc.rain;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

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
import com.openxc.measurements.VehicleMeasurement;
import com.openxc.measurements.NoValueException;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.ford.openxc.rain.R;
import com.openxc.measurements.WindshieldWiperStatus;
import com.openxc.measurements.Latitude;
import com.openxc.measurements.Longitude;
import com.openxc.remote.RemoteVehicleServiceException;

public class RainMonitorActivity extends Activity {

    private static String TAG = "RainMonitorActivity";
    private final String WUNDERGROUND_URL =
        "http://www.wunderground.com/weatherstation/VehicleWeatherUpdate.php";

    private VehicleService mVehicleService;
    private boolean mIsBound;
    private TextView mWiperStatusView;;
    private final Handler mHandler = new Handler();

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
            }

            if(!latitude.isNone() && !longitude.isNone() &&
                    !wiperStatus.isNone()) {
                final boolean wiperStatusValue;
                try {
                    wiperStatusValue =
                        wiperStatus.getValue().booleanValue();
                } catch(NoValueException e) {
                    Log.w(TAG, "Expected wiper status to have a value " +
                            "but it did not", e);
                    return;
                }

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
        }

        private void uploadWiperStatus(Latitude latitude, Longitude longitude,
                WindshieldWiperStatus wiperStatus) {
            double latitudeValue;
            double longitudeValue;
            boolean wiperStatusValue;
            try {
                latitudeValue = latitude.getValue().doubleValue();
                longitudeValue = longitude.getValue().doubleValue();
                wiperStatusValue = wiperStatus.getValue().booleanValue();
            } catch(NoValueException e) {
                Log.w(TAG, "Expected measurements to have a value, " +
                        "but at least one didn't -- skipping upload", e);
                return;
            }

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
