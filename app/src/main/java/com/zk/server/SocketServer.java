package com.zk.server;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by dell on 2017/9/13.
 */

public class SocketServer {
    private static final String TAG = SocketServer.class.getSimpleName();
    private ServerSocket mServerSocket;
    private Socket mSocket;
    private PrintWriter mPrintWriter;
    public static Handler mHandler;

    public SocketServer() {
        try {
            mServerSocket = new ServerSocket(1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * socket监听数据
     */
    public void beginListen() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "beginListen");
                    mSocket = mServerSocket.accept();
                    mPrintWriter = new PrintWriter(mSocket.getOutputStream());
                    InputStream inputStream = mSocket.getInputStream();
                    Log.d(TAG, "beginListen 1");
                    while (!mSocket.isClosed()) {
                        byte[] bt = new byte[50];
                        inputStream.read(bt);
                        String str = new String(bt);
                        sendMessage(str);
                    }
                    Log.d(TAG, "beginListen end");
                    mPrintWriter.close();
                    mPrintWriter = null;
                    inputStream.close();
                    mSocket.close();
                    mSocket = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendMessage(String message) {
        Message msg = new Message();
        msg.obj = message;
        mHandler.sendMessage(msg);
    }

    public void sendMsg(final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mSocket != null && mPrintWriter != null) {
                    mPrintWriter.write(message);
                    mPrintWriter.flush();
                }
            }
        }).start();
    }
}
