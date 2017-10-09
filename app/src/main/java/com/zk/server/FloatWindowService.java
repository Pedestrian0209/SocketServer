package com.zk.server;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

/**
 * Created by dell on 2017/10/9.
 */

public class FloatWindowService extends Service {
    private static final int NOTIFICATION_ID = 3;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder builder;

    private IScreenControlAidlInterface.Stub mBinder = new IScreenControlAidlInterface.Stub() {
        @Override
        public void startScreenRecord() throws RemoteException {

        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initNotification();
        FloatWindowManager.createSmallWindow(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
        FloatWindowManager.removeSmallWindow(this);
    }

    private void initNotification() {
        builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("远程桌面")
                .setContentText("您正在进行远程桌面控制")
                .setOngoing(true)
                .setDefaults(Notification.DEFAULT_VIBRATE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
