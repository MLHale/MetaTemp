package com.example.student.metatemp;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.preference.PreferenceManager;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.app.NotificationCompat;
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
    private SharedPreferences sharedPreferences;
    private MetaWearBoard mwBoard;
    private ThermistorFragment.ThermistorCallback thermistorCallback;
    private final int TIME_DELAY_PERIOD = 3000;
    private MainActivity activity;
    private List<MultiChannelTemperature.Source> tempSources = null;
    private Messenger tMessenger;

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
        this.sharedPreferences = getApplicationContext().getSharedPreferences("com.example.student.metatemp_preferences", 0); // 0 - for private mode
        Bundle extras = intent.getExtras();
        if (extras != null) {
            tMessenger = (Messenger)extras.get("handlerExtra");
        }

        return tBinder;
    }

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

    public void getCurrentTemp(MetaWearBoard mwBoard) {
        this.mwBoard = mwBoard;
        final Context context = this;
        final int loThresh = Integer.parseInt(sharedPreferences.getString("low", "-1"));
        final int hiThresh = Integer.parseInt(sharedPreferences.getString("high", "-1"));
        final boolean doNotify = sharedPreferences.getBoolean("notifications_new_message", false);
        final String doRingtone = sharedPreferences.getString("notifications_new_message_ringtone", "");
        final boolean doVibrate = sharedPreferences.getBoolean("notifications_new_message_vibrate", false);

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

                        // Send message to MainActivity
                        android.os.Message mesg = android.os.Message.obtain(null, 1, 0, 0);
                        Bundle data = new Bundle();
                        data.putString("temp", text);
                        mesg.setData(data);
                        try {
                            tMessenger.send(mesg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        if (t < loThresh || t > hiThresh) {
                            // Notification
                            if (doNotify) {
                                NotificationCompat.Builder nBuilder;
                                int nId = 731119; // So can update the notification later
                                if (t < loThresh) {
                                    nBuilder =
                                            new NotificationCompat.Builder(context)
                                                    .setSmallIcon(R.drawable.ic_ac_unit_white_24dp)
                                                    .setContentTitle("Too Cold!")
                                                    .setContentText("MetaWear Current Temperature: " + text);
                                } else {
                                    nBuilder =
                                            new NotificationCompat.Builder(context)
                                                    .setSmallIcon(R.drawable.ic_whatshot_white_24dp)
                                                    .setContentTitle("Too Hot!")
                                                    .setContentText("MetaWear Current Temperature: " + text);
                                }

                                // Create an explicit intent for an Activity in your app
                                Intent resultIntent = new Intent(context, MainActivity.class);

                                // The stack builder object will contain an artificial back stack for the
                                // started Activity.
                                // This ensures that navigating backward from the Activity leads out of
                                // your application to the Home screen.
                                TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                                // Add the back stack for the Intent (but not the Intent itself)
                                stackBuilder.addParentStack(MainActivity.class);
                                // Add the Intent that starts the Activity to top of stack
                                stackBuilder.addNextIntent(resultIntent);
                                PendingIntent resultPendingIntent =
                                        stackBuilder.getPendingIntent(
                                                0,
                                                PendingIntent.FLAG_UPDATE_CURRENT
                                        );
                                nBuilder.setContentIntent(resultPendingIntent);
                                NotificationManager mNotificationManager =
                                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                                mNotificationManager.notify(nId, nBuilder.build());
                            }

                        }

                    }
                });
            }
        });
    }
}


