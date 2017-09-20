package com.zk.server;

import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import java.io.DataOutputStream;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private SocketServer mSocketServer;
    private TextView mTxt;
    private MediaProjectionManager mMediaProjectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.write).setOnClickListener(this);
        mTxt = (TextView) findViewById(R.id.txt);
        mSocketServer = new SocketServer();
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
    }
}
