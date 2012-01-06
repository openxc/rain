package com.ford.openxc.rain;

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
import com.openxc.remote.RemoteVehicleServiceException;

public class RainMonitorActivity extends Activity {

    private static String TAG = "RainMonitorActivity";

    private VehicleService mVehicleService;
    private boolean mIsBound;
    private TextView mWiperStatusView;;
    private final Handler mHandler = new Handler();

    WindshieldWiperStatus.Listener mWiperListener =
            new WindshieldWiperStatus.Listener() {
        public void receive(VehicleMeasurement measurement) {
            final WindshieldWiperStatus wiperStatus =
                (WindshieldWiperStatus) measurement;
            if(!wiperStatus.isNone()) {
                mHandler.post(new Runnable() {
                    public void run() {
                        try {
                            mWiperStatusView.setText("" +
                                wiperStatus.getValue().booleanValue());
                        } catch(NoValueException e) { }
                    }
                });
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            Log.i(TAG, "Bound to VehicleService");
            mVehicleService = ((VehicleService.VehicleServiceBinder)service
                    ).getService();

            try {
                mVehicleService.addListener(WindshieldWiperStatus.class,
                        mWiperListener);
            } catch(RemoteVehicleServiceException e) {
                Log.w(TAG, "Couldn't add listeners for measurements", e);
            } catch(UnrecognizedMeasurementTypeException e) {
                Log.w(TAG, "Couldn't add listeners for measurements", e);
            }
            mIsBound = true;
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
