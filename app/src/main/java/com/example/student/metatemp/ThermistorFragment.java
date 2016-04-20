package com.example.student.metatemp;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.MultiChannelTemperature;
import com.mbientlab.metawear.module.Timer;
//import com.mbientlab.temperatureTracker.model.TemperatureSample;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Copyright 2014 MbientLab Inc. All rights reserved.
 * <p/>
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 * <p/>
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 * <p/>
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 * <p/>
 * <p/>
 * Created by Lance Gleason of Polyglot Programming LLC. on 4/26/15.
 * http://www.polyglotprogramminginc.com
 * https://github.com/lgleasain
 * Twitter: @lgleasain
 */

public class ThermistorFragment extends Fragment {
    private MultiChannelTemperature tempModule;
    private Logging loggingModule;
    private Editor editor;
    private MetaWearBoard mwBoard;
    private ThermistorFragment.ThermistorCallback thermistorCallback;
    private SharedPreferences sharedPreferences;
    private final int TIME_DELAY_PERIOD = 3000;
    private MainActivity activity;
    private List<MultiChannelTemperature.Source> tempSources = null;

    public interface ThermistorCallback {
        void startDownload();

        void totalDownloadEntries(int entries);

        void downloadProgress(int entriesDownloaded);

        void downloadFinished();

//        GraphFragment getGraphFragment();
    }

    @Override
    public void onResume() {
        super.onResume();
        thermistorCallback = (ThermistorFragment.ThermistorCallback) getActivity();
    }

    private final RouteManager.MessageHandler loggingMessageHandler = new RouteManager.MessageHandler() {
        @Override
        public void process(Message msg) {
            Log.i("MainActivity", String.format("Ext thermistor: %.3fC", msg.getData(Float.class)));
            java.sql.Date date = new java.sql.Date(msg.getTimestamp().getTimeInMillis());

//            TextView temptext = (TextView) getActivity().findViewById(R.id.temperature);
//            temptext.setText(msg.getData(Float.class).intValue() + "°");
//            TemperatureSample sample = new TemperatureSample(date,  msg.getData(Float.class).longValue(), mwBoard.getMacAddress());
//            sample.save();
        }
    };

    private final AsyncOperation.CompletionHandler<RouteManager> temperatureHandler = new AsyncOperation.CompletionHandler<RouteManager>() {
        @Override
        public void success(RouteManager result) {
            result.setLogMessageHandler("mystream", loggingMessageHandler);
            editor.putInt(mwBoard.getMacAddress() + "_log_id", result.id());
            editor.apply();
            editor.commit();

            // Read temperature from the NRF soc chip
            try {
                AsyncOperation<Timer.Controller> taskResult = mwBoard.getModule(Timer.class)
                        .scheduleTask(new Timer.Task() {
                            @Override
                            public void commands() {
                                tempModule.readTemperature(tempModule.getSources().get(MultiChannelTemperature.MetaWearRChannel.NRF_DIE));
                            }
                        }, TIME_DELAY_PERIOD, false);
                taskResult.onComplete(new AsyncOperation.CompletionHandler<Timer.Controller>() {
                    @Override
                    public void success(Timer.Controller result) {
                        // start executing the task
                        result.start();
                    }
                });
            }catch (UnsupportedModuleException e){
                Log.e("Temperature Fragment", e.toString());
            }
        }

        @Override
        public void failure(Throwable error) {
            Log.e("AsyncResult", "Error in CompletionHandler",
                    error);
        }
    };

    public boolean setupThermistorAndLogs(MetaWearBoard mwBoard, Editor editor) {
        this.editor = editor;
        this.mwBoard = mwBoard;

        try {
            tempModule = mwBoard.getModule(MultiChannelTemperature.class);
        } catch (UnsupportedModuleException e){
            Log.e("Thermistor Fragment", e.toString());
            return false;
        }

        List<MultiChannelTemperature.Source> tempSources= tempModule.getSources();
        MultiChannelTemperature.Source tempSource = tempSources.get(MultiChannelTemperature.MetaWearRChannel.NRF_DIE);
        tempModule.routeData().fromSource(tempSource).log("log_stream")
                .commit().onComplete(temperatureHandler);

        try {
            loggingModule = mwBoard.getModule(Logging.class);
            loggingModule.startLogging();
        } catch (UnsupportedModuleException e){
            Log.e("Thermistor Fragment", e.toString());
            return false;
        }
        return true;
    }

    public void startLogDownload(MetaWearBoard mwBoard, SharedPreferences sharedPreferences) {
        /*
           Before actually calling the downloadLog method, we will first gather the required
           data to compute the log timestamps and setup progress notifications.
           This means we will call downloadLog in one of the logging callback functions, and
           will start the callback chain here
         */

        this.sharedPreferences = sharedPreferences;
        this.mwBoard = mwBoard;

        try {
            loggingModule = mwBoard.getModule(Logging.class);
            loggingModule.startLogging();
            tempModule = mwBoard.getModule(MultiChannelTemperature.class);
        }catch (UnsupportedModuleException e){
            Log.e("Thermistor Fragment", e.toString());
        }

        RouteManager route = mwBoard.getRouteManager(sharedPreferences.getInt(mwBoard.getMacAddress() + "_log_id", 0));
        route.setLogMessageHandler("log_stream", loggingMessageHandler);

        loggingModule.downloadLog((float) 0.1, new Logging.DownloadHandler() {
            @Override
            public void onProgressUpdate(int nEntriesLeft, int totalEntries) {
                Log.i("Thermistor", String.format("Progress= %d / %d", nEntriesLeft,
                        totalEntries));
                thermistorCallback.totalDownloadEntries(totalEntries);
                thermistorCallback.downloadProgress(totalEntries - nEntriesLeft);
                if (nEntriesLeft == 0) {
//                    GraphFragment graphFragment = thermistorCallback.getGraphFragment();
//                    graphFragment.updateGraph();
                    thermistorCallback.downloadFinished();
                }
            }
        });
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                thermistorCallback.startDownload();
            }
        });
    }

    public void getCurrentTemp(MetaWearBoard mwBoard, SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        this.mwBoard = mwBoard;

        try {
            tempModule = mwBoard.getModule(MultiChannelTemperature.class);
            tempSources = tempModule.getSources();
        }catch (UnsupportedModuleException e){
            Log.e("Thermistor Fragment", e.toString());
            return;
        }

        // Route data from the nrf soc temperature sensor
        tempModule.routeData()
                .fromSource(tempSources.get(MultiChannelTemperature.MetaWearRChannel.NRF_DIE)).stream("temp_nrf_stream")
                .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {

            public void success(RouteManager result) {
                result.subscribe("temp_nrf_stream", new RouteManager.MessageHandler() {
                    @Override
                    public void process(Message msg) {
                        Log.i("ThermFrag_GetCurTemp", String.format("Ext thermistor: %.3fC",
                                msg.getData(Float.class)));
                        Float t = msg.getData(Float.class);
                        t = (t * 9/5.0f) +32; //convert to Fahrenheit
                        String text = Math.round(t) + "°";
                        Handler mHandler = ((MainActivity) getActivity()).getMsgHandler();
                        android.os.Message mesg = mHandler.obtainMessage();
                        Bundle data = new Bundle();
                        data.putString("temp", text);
                        mesg.setData(data);
                        mHandler.sendMessage(mesg);
//                        TextView temptext = (TextView) getActivity().findViewById(R.id.temperature);
//                        temptext.setText(msg.getData(Float.class).intValue() + "°");
//                        System.err.println(msg.getData(Float.class).intValue() + "°");
                    }
                });

                // Read temperature from the NRF soc chip
                //tempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearRChannel.NRF_DIE));
            }
        });
    }
}