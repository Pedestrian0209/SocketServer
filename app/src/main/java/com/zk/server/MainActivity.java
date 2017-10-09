package com.zk.server;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.zk.server.IScreenControlAidlInterface;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private SocketServer mSocketServer;
    private TextView mTxt;
    private MediaProjectionManager mMediaProjectionManager;
    private IScreenControlAidlInterface mIScreenControlAidlInterface;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mIScreenControlAidlInterface = IScreenControlAidlInterface.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mIScreenControlAidlInterface = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.write).setOnClickListener(this);
        mTxt = (TextView) findViewById(R.id.txt);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int dpi = metrics.densityDpi;
        mSocketServer = new SocketServer();
        //mSocketServer.init(width, height, dpi);
        mSocketServer.beginListen();
        SocketServer.mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                mTxt.setText(mTxt.getText().toString() + "\n" + msg.obj.toString());
            }
        };
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), 0);
        bindService(new Intent(this, FloatWindowService.class), mConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.write:
                if (mSocketServer != null) {
                    mSocketServer.sendMsg("hehe");
                }
                break;
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this, FloatWindowService.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != 0) {
            Log.d(TAG, "onActivityResult requestCode = " + requestCode);
            return;
        }
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.d(TAG, "media projection is null");
            return;
        }
        mSocketServer.setMediaProjection(mediaProjection);
        //moveTaskToBack(true);
    }
}
