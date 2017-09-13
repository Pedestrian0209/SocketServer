package com.zk.server;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private SocketServer mSocketServer;
    private TextView mTxt;

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
}
