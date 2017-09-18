package com.zk.server;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by dell on 2017/9/13.
 */

public class SocketServer {
    private static final String TAG = SocketServer.class.getSimpleName();
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC; // H.264 Advanced Video Coding
    private static final int VIDEO_BITRATE = 125000; // 500Kbps
    private static final int FRAME_RATE = 30; // 30 fps
    private static final int IFRAME_INTERVAL = 5; // 2 seconds between I-frames
    private static final int TIMEOUT_US = 0;
    private ServerSocket mServerSocket;
    private Socket mSocket;
    private PrintWriter mPrintWriter;
    private OutputStream mOutputStream;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private MediaCodec mEncoder;
    private Surface mSurface;
    private int mWidth = 720;
    private int mHeight = 1280;
    private int mDpi = 1;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
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
                    mOutputStream = mSocket.getOutputStream();
                    mPrintWriter = new PrintWriter(mSocket.getOutputStream());
                    InputStream inputStream = mSocket.getInputStream();
                    Log.d(TAG, "beginListen 1");
                    mImageReader = ImageReader.newInstance(
                            mWidth,
                            mHeight,
                            PixelFormat.RGBA_8888,// a pixel两节省一些内存 个2个字节 此处RGB_565 必须和下面 buffer处理一致的格式
                            1);
                    /*mVirtualDisplay = mMediaProjection.createVirtualDisplay("remote_control",
                            mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            mImageReader.getSurface(), null, null);*/
                    recordVirtualDisplay();
                    Log.d(TAG, "beginListen end");
                    mOutputStream.close();
                    mOutputStream = null;
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

    public void setMediaProjection(MediaProjection mediaProjection) {
        mMediaProjection = mediaProjection;
        try {
            prepareEncoder();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void prepareEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        Log.d(TAG, "created video format: " + format);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mEncoder.createInputSurface();
        Log.d(TAG, "created input surface: " + mSurface);
        mEncoder.start();
    }

    private void recordVirtualDisplay() throws IOException {
        while (!mSocket.isClosed()) {
            if (true) {
                mVirtualDisplay = mMediaProjection.createVirtualDisplay("remote_control",
                        mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mImageReader.getSurface(), null, null);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                /*mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {*/
                Image image = mImageReader.acquireLatestImage();
                Log.d(TAG, "acquireLatestImage image = " + image);
                if (image != null) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    final Image.Plane[] planes = image.getPlanes();
                    final ByteBuffer buffer = planes[0].getBuffer();

                    /*int size = buffer.remaining();
                    byte[] bytes = new byte[size];
                    buffer.get(bytes);
                    mOutputStream.write(size >> 24);
                    mOutputStream.write(size >> 16);
                    mOutputStream.write(size >> 8);
                    mOutputStream.write(size);
                    mOutputStream.write(bytes);
                    mOutputStream.flush();*/


                    //每个像素的间距
                    int pixelStride = planes[0].getPixelStride();
                    //总的间距
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * width;
                    Log.d(TAG,"write1");
                    Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height,
                            Bitmap.Config.ARGB_8888);
                    Log.d(TAG,"write2");
                    bitmap.copyPixelsFromBuffer(buffer);
                    Log.d(TAG,"write3");
                    //bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                    if (bitmap != null) {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream);
                        Log.d(TAG,"write");
                        int size = outputStream.size();
                        mOutputStream.write(size >> 24);
                        mOutputStream.write(size >> 16);
                        mOutputStream.write(size >> 8);
                        mOutputStream.write(size);
                        mOutputStream.write(outputStream.toByteArray());
                        mOutputStream.flush();
                        bitmap.recycle();
                    }
                    image.close();
                }
                if (mVirtualDisplay != null) {
                    mVirtualDisplay.release();
                }
                    /*}
                }, 300);*/
                continue;
            }
            int eobIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            switch (eobIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "VideoSenderThread,MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
//                    LogTools.d("VideoSenderThread,MediaCodec.INFO_TRY_AGAIN_LATER");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "VideoSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" +
                            mEncoder.getOutputFormat().toString());
                    break;
                default:
                    Log.d(TAG, "VideoSenderThread,MediaCode,eobIndex=" + eobIndex);
                    /**
                     * we send sps pps already in INFO_OUTPUT_FORMAT_CHANGED
                     * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                     */
                    if (mBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && mBufferInfo.size != 0) {
                        ByteBuffer realData = mEncoder.getOutputBuffers()[eobIndex];
                        /*realData.position(mBufferInfo.offset);
                        realData.limit(mBufferInfo.offset + mBufferInfo.size);
                        Image image = mEncoder.getInputImage(eobIndex);
                        Image.Plane[] plane = image.getPlanes();*/
                        byte[] finalBuff = //plane[0].getBuffer().array();
                                new byte[mBufferInfo.size];
                        //intToBytes(finalBuff, realDataLength);
                        realData.get(finalBuff);
                        onFrame(finalBuff, 0, finalBuff.length, mBufferInfo.flags);
                        //avcEncoder.offerEncoder(finalBuff);
                        /*if ((finalBuff[3] == 0 && finalBuff[4] == 0 && finalBuff[5] == 1)
                                || (finalBuff[3] == 0 && finalBuff[4] == 0 && finalBuff[5] == 0 && finalBuff[6] == 1)) {
                            onFrame(finalBuff, 3, finalBuff.length - 3, mBufferInfo.flags);
                        } else {
                            finalBuff[0] = 0;
                            finalBuff[1] = 0;
                            finalBuff[2] = 1;
                            onFrame(finalBuff, 0, finalBuff.length, mBufferInfo.flags);
                        }*/
                        if (mOutputStream != null) {
                            try {
                                Log.d(TAG, "ByteBuffer byte size = " + finalBuff.length + " realDataLength = " + mBufferInfo.size);
                                //mOutputStream.write(finalBuff);
                                mOutputStream.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    mEncoder.releaseOutputBuffer(eobIndex, false);
                    break;
            }
        }
    }

    public void intToBytes(byte[] dst, int value) {
        dst[3] = (byte) ((value >> 24) & 0xFF);
        dst[2] = (byte) ((value >> 16) & 0xFF);
        dst[1] = (byte) ((value >> 8) & 0xFF);
        dst[0] = (byte) (value & 0xFF);
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

    private MediaCodec mDecoder;
    private Surface mSurfacePre;

    public void setSurface(Surface surface) {
        mSurfacePre = surface;
        try {
            prepareDecoder();
        } catch (IOException e) {
            e.printStackTrace();
        }
        /*avcEncoder = new AvcEncoder();
        avcEncoder.setOnFrameListener(new AvcEncoder.OnFrameListener() {
            @Override
            public void onFrame(byte[] output) {
                SocketServer.this.onFrame(output, 0, output.length, mBufferInfo.flags);
            }
        });*/
    }

    private void prepareDecoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        Log.d(TAG, "created video format: " + format);
        mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
        mDecoder.configure(format, mSurfacePre, null, 0);
        Log.d(TAG, "created input surface: " + mSurface);
        mDecoder.start();
    }

    private int mCount = 0;

    public void onFrame(byte[] buf, int offset, int length, int flag) {
        Log.d(TAG, "onFrame length = " + length);
        /*avcEncoder.offerEncoder(buf);
        if (true) {
            return;
        }*/
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
        int inputBufferIndex = mDecoder.dequeueInputBuffer(TIMEOUT_US);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mDecoder.queueInputBuffer(inputBufferIndex, 0, length, mCount * 1000000 / FRAME_RATE, 0);
            mCount++;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mDecoder.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0) {
            mDecoder.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mDecoder.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    private AvcEncoder avcEncoder;
}
