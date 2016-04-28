package com.example.student.metatemp;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.MultiChannelTemperature;
import com.mbientlab.metawear.module.Timer;

import java.util.List;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class ThermistorService extends Service {
    private final IBinder tBinder = new ThermBinder();
    private MultiChannelTemperature tempModule;
    private Logging loggingModule;
    private SharedPreferences.Editor editor;
    private MetaWearBoard mwBoard;
    private ThermistorFragment.ThermistorCallback thermistorCallback;
    private SharedPreferences sharedPreferences;
    private final int TIME_DELAY_PERIOD = 3000;
    private MainActivity activity;
    private List<MultiChannelTemperature.Source> tempSources = null;
    private Messenger tMessenger;

    // TODO: Rename actions
    private static final String ACTION_FOO = "com.example.student.metatemp.action.FOO";
    private static final String ACTION_BAZ = "com.example.student.metatemp.action.BAZ";

    // TODO: Rename parameters
    private static final String EXTRA_PARAM1 = "com.example.student.metatemp.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "com.example.student.metatemp.extra.PARAM2";

//    public ThermistorService() { super("ThermistorService"); }
    public ThermistorService() {
        super();
    }

    public class ThermBinder extends Binder {
        ThermistorService getService() {
            return ThermistorService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            tMessenger = (Messenger)extras.get("handlerExtra");
        }

        return tBinder;
    }

//    /**
//     * Starts this service to perform action Foo with the given parameters. If
//     * the service is already performing a task this action will be queued.
//     *
//     * @see IntentService
//     */
//    // TODO: Customize helper method
//    public static void startActionFoo(Context context, String param1, String param2) {
//        Intent intent = new Intent(context, ThermistorService.class);
//        intent.setAction(ACTION_FOO);
//        intent.putExtra(EXTRA_PARAM1, param1);
//        intent.putExtra(EXTRA_PARAM2, param2);
//        context.startService(intent);
//    }
//
//    /**
//     * Starts this service to perform action Baz with the given parameters. If
//     * the service is already performing a task this action will be queued.
//     *
//     * @see IntentService
//     */
//    // TODO: Customize helper method
//    public static void startActionBaz(Context context, String param1, String param2) {
//        Intent intent = new Intent(context, ThermistorService.class);
//        intent.setAction(ACTION_BAZ);
//        intent.putExtra(EXTRA_PARAM1, param1);
//        intent.putExtra(EXTRA_PARAM2, param2);
//        context.startService(intent);
//    }
//
//    @Override
//    protected void onHandleIntent(Intent intent) {
//        if (intent != null) {
//            final String action = intent.getAction();
//            if (ACTION_FOO.equals(action)) {
//                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
//                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
//                handleActionFoo(param1, param2);
//            } else if (ACTION_BAZ.equals(action)) {
//                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
//                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
//                handleActionBaz(param1, param2);
//            }
//        }
//    }
//
//    /**
//     * Handle action Foo in the provided background thread with the provided
//     * parameters.
//     */
//    private void handleActionFoo(String param1, String param2) {
//        // TODO: Handle action Foo
//        throw new UnsupportedOperationException("Not yet implemented");
//    }
//
//    /**
//     * Handle action Baz in the provided background thread with the provided
//     * parameters.
//     */
//    private void handleActionBaz(String param1, String param2) {
//        // TODO: Handle action Baz
//        throw new UnsupportedOperationException("Not yet implemented");
//    }

    private final RouteManager.MessageHandler loggingMessageHandler = new RouteManager.MessageHandler() {
        @Override
        public void process(Message msg) {
            Log.i("ThermService", String.format("Ext thermistor: %.3fC", msg.getData(Float.class)));
//            java.sql.Date date = new java.sql.Date(msg.getTimestamp().getTimeInMillis());
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
                Log.e("Temperature Service", e.toString());
            }
        }

        @Override
        public void failure(Throwable error) {
            Log.e("AsyncResult", "Error in CompletionHandler",
                    error);
        }
    };

    public boolean setupThermistorAndLogs(MetaWearBoard mwBoard, SharedPreferences.Editor editor) {
        this.editor = editor;
        this.mwBoard = mwBoard;

        try {
            tempModule = mwBoard.getModule(MultiChannelTemperature.class);
        } catch (UnsupportedModuleException e){
            Log.e("Thermistor Service", e.toString());
            return false;
        }

        List<MultiChannelTemperature.Source> tempSources= tempModule.getSources();
        MultiChannelTemperature.Source tempSource = tempSources.get(MultiChannelTemperature.MetaWearRChannel.NRF_DIE);
        tempModule.routeData().fromSource(tempSource).log("log_stream_service")
                .commit().onComplete(temperatureHandler);

        try {
            loggingModule = mwBoard.getModule(Logging.class);
            loggingModule.startLogging();
        } catch (UnsupportedModuleException e){
            Log.e("Thermistor Service", e.toString());
            return false;
        }
        return true;
    }

    public void getCurrentTemp(MetaWearBoard mwBoard, SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        this.mwBoard = mwBoard;

        try {
            tempModule = mwBoard.getModule(MultiChannelTemperature.class);
            tempSources = tempModule.getSources();
        }catch (UnsupportedModuleException e){
            Log.e("Thermistor Service", e.toString());
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
                        Log.i("ThermSrvc_GetCurTemp", String.format("Ext thermistor: %.3fC",
                                msg.getData(Float.class)));
                        Float t = msg.getData(Float.class);
                        t = (t * 9/5.0f) +32; //convert to Fahrenheit
                        String text = Math.round(t) + "";
                        // TODO: IMPLEMENT THE BELOW
//                        Handler mHandler = ((MainActivity) getActivity()).getMsgHandler();
//                        android.os.Message mesg = mHandler.obtainMessage();
                        android.os.Message mesg = android.os.Message.obtain(null, 1, 0, 0);
                        Bundle data = new Bundle();
                        data.putString("temp", text);
                        mesg.setData(data);
//                        mHandler.sendMessage(mesg);
                        try {
                            tMessenger.send(mesg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }
}


