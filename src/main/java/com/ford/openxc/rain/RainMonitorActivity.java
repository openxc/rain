package com.ford.openxc.rain;

import java.util.Timer;
import java.util.TimerTask;

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

import com.openxc.VehicleManager;
import com.ford.openxc.rain.R;

public class RainMonitorActivity extends Activity {

    private static String TAG = "RainMonitorActivity";

    private VehicleManager mVehicle;
    private boolean mIsBound;
    private TextView mWiperStatusView;
    private TextView mAlertStatusView;
    private final Handler mHandler = new Handler();

    private TimerTask mFetchAlertsTask;
    private TimerTask mCheckWipersTask;
    private Timer mTimer;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            Log.i(TAG, "Bound to VehicleManager");
            mVehicle = ((VehicleManager.VehicleBinder)service).getService();
            mIsBound = true;

            mFetchAlertsTask = new FetchAlertsTask(mVehicle, mHandler,
                    mAlertStatusView);
            mCheckWipersTask = new CheckWipersTask(mVehicle, mHandler,
                    mWiperStatusView);
            mTimer = new Timer();
            mTimer.schedule(mFetchAlertsTask, 100, 60000);
            mTimer.schedule(mCheckWipersTask, 100, 300000);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.w(TAG, "RemoteVehicleManager disconnected unexpectedly");
            mVehicle = null;
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
        bindService(new Intent(this, VehicleManager.class),
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
