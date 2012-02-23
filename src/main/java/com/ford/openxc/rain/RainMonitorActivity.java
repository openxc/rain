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
import com.ford.openxc.rain.R;

public class RainMonitorActivity extends Activity {

    private static String TAG = "RainMonitorActivity";

    private VehicleService mVehicleService;
    private boolean mIsBound;
    private TextView mWiperStatusView;
    private TextView mAlertStatusView;
    private final Handler mHandler = new Handler();

    private Runnable mFetchAlertsTask;
    private Runnable mCheckWipersTask;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            Log.i(TAG, "Bound to VehicleService");
            mVehicleService = ((VehicleService.VehicleServiceBinder)service
                    ).getService();
            mIsBound = true;

            mFetchAlertsTask = new FetchAlertsTask(mVehicleService, mHandler,
                    mAlertStatusView);
            mCheckWipersTask = new CheckWipersTask(mVehicleService, mHandler,
                    mWiperStatusView);
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
        mAlertStatusView = (TextView) findViewById(R.id.alert_status);
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
