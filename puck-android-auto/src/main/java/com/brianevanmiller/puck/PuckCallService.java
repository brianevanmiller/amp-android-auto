package com.brianevanmiller.puck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service that keeps the microphone available to the WebView while Puck is
 * in the background (e.g. only the car screen is showing), and routes call audio to the
 * car over Bluetooth.
 */
public class PuckCallService extends Service {
    static final String ACTION_CALL = "com.brianevanmiller.puck.CALL";
    static final String ACTION_KEEP_ALIVE = "com.brianevanmiller.puck.KEEP_ALIVE";
    static final String ACTION_HANG_UP = "com.brianevanmiller.puck.HANG_UP";

    private static final String CHANNEL = "call";
    private static final int NOTIFICATION_ID = 1;

    private static boolean running;

    /** Starts a call from the car. Returns false if Android won't let us open the mic right now. */
    static boolean startCall(Context context) {
        return start(context, ACTION_CALL);
    }

    static void keepAlive(Context context) {
        if (!running) start(context, ACTION_KEEP_ALIVE);
    }

    static void stop(Context context) {
        if (running) context.stopService(new Intent(context, PuckCallService.class));
    }

    private static boolean start(Context context, String action) {
        try {
            context.startForegroundService(new Intent(context, PuckCallService.class).setAction(action));
            return true;
        } catch (RuntimeException e) {
            // ForegroundServiceStartNotAllowedException and friends.
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_HANG_UP.equals(action)) {
            PuckCall.get(this).hangUp();
            return START_NOT_STICKY;
        }
        if (!running) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } catch (RuntimeException e) {
                stopSelf();
                PuckCall.get(this).fail("Puck can't use the microphone right now. Open Puck on your phone and allow it.");
                return START_NOT_STICKY;
            }
            running = true;
            routeToCar();
        }
        if (ACTION_CALL.equals(action)) PuckCall.get(this).call();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        AudioManager audio = getSystemService(AudioManager.class);
        if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice();
        audio.setMode(AudioManager.MODE_NORMAL);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Send call audio and mic through the car's hands-free (Bluetooth) link when there is one.
    private void routeToCar() {
        AudioManager audio = getSystemService(AudioManager.class);
        audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if (Build.VERSION.SDK_INT < 31) return;
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                audio.setCommunicationDevice(device);
                return;
            }
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Puck calls", NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent hangUp = PendingIntent.getService(this, 1,
                new Intent(this, PuckCallService.class).setAction(ACTION_HANG_UP), PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle("Puck")
                .setContentText("On a call with Puck")
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .addAction(new Notification.Action.Builder(null, "Hang up", hangUp).build())
                .build();
    }
}
