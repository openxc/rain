package com.ford.openxc.rain;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.openxc.measurements.Latitude;
import com.openxc.measurements.Longitude;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;

import com.openxc.remote.NoValueException;

import com.openxc.VehicleService;

import android.os.Handler;

import android.widget.TextView;

public class FetchAlertsTask implements Runnable {
    String mTargetUrl;
    VehicleService mVehicleService;
    Handler mHandler;
    TextView mAlertStatusView;

    public FetchAlertsTask(String url, VehicleService vehicleService,
            Handler handler, TextView alertStatusView) {
        mTargetUrl = url;
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
            latitudeValue = 28;
            longitudeValue = 131;
        }

        StringBuilder urlBuilder = new StringBuilder(mTargetUrl);
        urlBuilder.append(latitudeValue + ",");
        urlBuilder.append(longitudeValue + ".json");

        final URL wunderground;
        try {
            wunderground = new URL(urlBuilder.toString());
        } catch (MalformedURLException e) {
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                String result = "";
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

                final boolean hasResult = result != null;
                mHandler.post(new Runnable() {
                    public void run() {
                        mAlertStatusView.setText("" + hasResult);
                    }
                });
            }
        }).start();

        mHandler.postDelayed(this, 300000);
    }
}
