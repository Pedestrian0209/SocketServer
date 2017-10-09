package com.zk.server;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.view.InputDeviceCompat;
import android.util.Log;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by dell on 2017/9/13.
 */

public class SocketServer {
    private static final String TAG = SocketServer.class.getSimpleName();
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int VIDEO_BITRATE = 500000; // 500Kbps
    private static final int FRAME_RATE = 30; // 30 fps
    private static final int IFRAME_INTERVAL = 1; // 2 seconds between I-frames
    private static final int TIMEOUT_US = 0;
    private static InputManager im;
    private static Method injectInputEventMethod;
    //private static IWindowManager wm;
    private ServerSocket mServerSocket;
    private Socket mSocket;
    private PrintWriter mPrintWriter;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private static MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private MediaCodec mEncoder;
    private Surface mSurface;
    private int mWidth = 720;
    private int mHeight = 1280;
    private int mDpi = 260;
    public static Handler mHandler;
    private AudioRecord mAudioRecord;

    public SocketServer() {
        try {
            mServerSocket = new ServerSocket(1024);
            init();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void init(int width, int height, int dpi) {
        mWidth = width;
        mHeight = height;
        mDpi = dpi;
        Log.d(TAG, "init mWidth = " + mWidth + " mHeight = " + mHeight + " mDpi = " + mDpi);
    }

    private void init() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        //Method getServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", new Class[]{String.class});
        //wm = IWindowManager.Stub.asInterface((IBinder) getServiceMethod.invoke(null, new Object[]{"window"}));

        im = (InputManager) InputManager.class.getDeclaredMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
        MotionEvent.class.getDeclaredMethod("obtain", new Class[0]).setAccessible(true);
        injectInputEventMethod = InputManager.class.getMethod("injectInputEvent", new Class[]{InputEvent.class, Integer.TYPE});

    }

    private PointItem getPoint(String str) {
        String[] strs = str.split("#");
        return new PointItem(Integer.valueOf(strs[1]), Integer.valueOf(strs[2]), Integer.valueOf(strs[3]));
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
                    mInputStream = mSocket.getInputStream();
                    Log.d(TAG, "beginListen 1");
                    mVirtualDisplay = mMediaProjection.createVirtualDisplay("remote_control",
                            mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            mSurface, null, null);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            receiveMsg();
                        }
                    }).start();
                    recordAudio();
                    recordVideo();
                    //recordVirtualDisplay();
                    Log.d(TAG, "beginListen end");
                    mOutputStream.close();
                    mOutputStream = null;
                    mPrintWriter.close();
                    mPrintWriter = null;
                    mInputStream.close();
                    mInputStream = null;
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

    private void recordVirtualDisplay() throws IOException {
        mImageReader = ImageReader.newInstance(
                mWidth,
                mHeight,
                PixelFormat.RGBA_8888,// a pixel两节省一些内存 个2个字节 此处RGB_565 必须和下面 buffer处理一致的格式
                1);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("remote_control",
                mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
        while (!mSocket.isClosed()) {
            Image image = mImageReader.acquireLatestImage();
            if (image != null) {
                Log.d(TAG, "acquireLatestImage image = " + image);
                int width = image.getWidth();
                int height = image.getHeight();
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                /*int length = buffer.limit() - buffer.position();
                byte[] bufferArray = new byte[length];
                buffer.get(bufferArray);*/
                //每个像素的间距
                int pixelStride = planes[0].getPixelStride();
                //总的间距
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;
                int bmpWidth = width + rowPadding / pixelStride;
                Log.d(TAG, "write1 width = " + width + " height = " + height + " pixelStride = " + pixelStride + " rowStride = " + rowStride + " rowPadding = " + rowPadding);
                Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height,
                        Bitmap.Config.ARGB_8888);
                Log.d(TAG, "write2 width + rowPadding / pixelStride = " + width + rowPadding / pixelStride);
                bitmap.copyPixelsFromBuffer(buffer);
                Log.d(TAG, "write3");
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                Log.d(TAG, "write4");
                if (bitmap != null) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream);
                    Log.d(TAG, "write");
                    int size = outputStream.size();
                    mOutputStream.write(size >> 24);
                    mOutputStream.write(size >> 16);
                    mOutputStream.write(size >> 8);
                    mOutputStream.write(size);
                    mOutputStream.write(outputStream.toByteArray());
                    mOutputStream.flush();
                    Log.d(TAG, "write end " + size);
                    bitmap.recycle();
                }
                /*mOutputStream.write(width >> 24);
                mOutputStream.write(width >> 16);
                mOutputStream.write(width >> 8);
                mOutputStream.write(width);
                mOutputStream.write(height >> 24);
                mOutputStream.write(height >> 16);
                mOutputStream.write(height >> 8);
                mOutputStream.write(height);
                mOutputStream.write(bmpWidth >> 24);
                mOutputStream.write(bmpWidth >> 16);
                mOutputStream.write(bmpWidth >> 8);
                mOutputStream.write(bmpWidth);
                mOutputStream.write(length >> 24);
                mOutputStream.write(length >> 16);
                mOutputStream.write(length >> 8);
                mOutputStream.write(length);
                mOutputStream.write(bufferArray);
                mOutputStream.flush();
                Log.d(TAG, "write end");*/
                image.close();
            }
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mImageReader != null) {
            mImageReader.close();
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

    private void recordAudio() {
        int minBufferSize = AudioRecord.getMinBufferSize(44100,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 5);
        int audioRecoderSliceSize = 44100 / 10;
        int audioRecoderBufferSize = audioRecoderSliceSize * 2;
        final byte[] audioBuffer = new byte[audioRecoderBufferSize + 1];

        if (AudioRecord.STATE_INITIALIZED != mAudioRecord.getState()) {
            Log.e(TAG, "audioRecord.getState()!=AudioRecord.STATE_INITIALIZED!");
            return;
        }

        if (AudioRecord.SUCCESS != mAudioRecord.setPositionNotificationPeriod(audioRecoderSliceSize)) {
            Log.e(TAG, "AudioRecord.SUCCESS != audioRecord.setPositionNotificationPeriod(" + audioRecoderSliceSize + ")");
            return;
        }

        mAudioRecord.startRecording();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!mSocket.isClosed()) {
                    audioBuffer[0] = 0;
                    int size = mAudioRecord.read(audioBuffer, 1, audioBuffer.length - 1) + 1;
                    Log.d(TAG, "recordAudio size = " + size);
                    if (size > 0) {
                        write(audioBuffer, size);
                    }
                }
                mAudioRecord.stop();
                mAudioRecord.release();
            }
        }).start();
    }

    private synchronized void write(byte[] buffer, int size) {
        try {
            mOutputStream.write(size >> 24);
            mOutputStream.write(size >> 16);
            mOutputStream.write(size >> 8);
            mOutputStream.write(size);
            mOutputStream.write(buffer);
            mOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public byte[] configbyte;

    private void recordVideo() {
        while (!mSocket.isClosed()) {
            ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
            //Log.d(TAG,"recordVirtualDisplay1 outputBufferIndex = " + outputBufferIndex);
            while (outputBufferIndex >= 0) {
                Log.d(TAG, "recordVirtualDisplay1 outputBufferIndex = " + outputBufferIndex);
                //Log.i("AvcEncoder", "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size + 1];
                outData[0] = 1;
                outputBuffer.get(outData, 1, bufferInfo.size);
                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    Log.d(TAG, "recordVirtualDisplay1 2");
                    configbyte = new byte[bufferInfo.size + 1];
                    configbyte = outData;
                } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    Log.d(TAG, "recordVirtualDisplay1 1");
                    byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                    System.arraycopy(outData, 1, keyframe, configbyte.length, outData.length - 1);

                    //onFrame(keyframe, 0, keyframe.length, 0);
                    if (mOutputStream != null) {
                        write(keyframe, keyframe.length);
                    }
                } else {
                    Log.d(TAG, "recordVirtualDisplay1 0");
                    //onFrame(outData, 0, outData.length, 0);
                    if (mOutputStream != null) {
                        write(outData, outData.length);
                    }
                }

                mEncoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
            }
        }
        mEncoder.stop();
        mEncoder.release();
    }

    private void receiveMsg() {
        //Log.d(TAG, "receiveMsg");
        while (mInputStream != null) {
            byte[] result = new byte[20];
            try {
                int code = mInputStream.read(result);
                //Log.d(TAG, "receiveMsg code = " + code);
                if (code >= 0) {
                    String msg = new String(result);
                    Log.d(TAG, "receiveMsg msg = " + msg);
                    if (msg.startsWith("TOUCH")) {
                        handleTouchEvent(getPoint(msg));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private static long mDownTime;

    private void handleTouchEvent(PointItem item) throws InvocationTargetException, IllegalAccessException {
        Log.d(TAG, "handleTouchEvent item = " + item.toString());
        switch (item.event) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = SystemClock.uptimeMillis();
                injectMotionEvent(im, injectInputEventMethod, InputDeviceCompat.SOURCE_TOUCHSCREEN, item.event, mDownTime, mDownTime, item.x, item.y, 1.0f);
                break;
            default:
                if (mDownTime <= 0) {
                    mDownTime = SystemClock.uptimeMillis();
                }
                injectMotionEvent(im, injectInputEventMethod, InputDeviceCompat.SOURCE_TOUCHSCREEN, item.event, mDownTime, SystemClock.uptimeMillis(), item.x, item.y, 1.0f);
                break;
        }
    }

    private void injectMotionEvent(InputManager im, Method injectInputEventMethod, int inputSource, int action, long downTime, long eventTime, float x, float y, float pressure) throws InvocationTargetException, IllegalAccessException {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, pressure, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(inputSource);
        injectInputEventMethod.invoke(im, new Object[]{event, Integer.valueOf(0)});
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

    private final int kSampleRate = 44100;
    private final int kChannelMode = AudioFormat.CHANNEL_IN_STEREO;
    private final int kEncodeFormat = AudioFormat.ENCODING_PCM_16BIT;

    private void main() {
        int minBufferSize = AudioRecord.getMinBufferSize(kSampleRate, kChannelMode,
                kEncodeFormat);
        AudioRecord mRecord = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX,
                kSampleRate, kChannelMode, kEncodeFormat, minBufferSize * 2);
    }
}
