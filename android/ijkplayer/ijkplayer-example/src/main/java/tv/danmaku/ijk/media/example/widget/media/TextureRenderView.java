/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.example.widget.media;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v8.renderscript.RenderScript;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
/*
import com.daasuu.mp4compose.FillMode;
import com.daasuu.mp4compose.composer.Mp4Composer;
import com.daasuu.mp4compose.filter.GlSepiaFilter;
*/
import com.mtcnn_as.FaceDetector;
import com.sharpai.detector.Classifier;
import com.sharpai.detector.Detector;
import com.sharpai.pim.MotionDetectionRS;
//import com.zolad.videoslimmer.VideoSlimmer;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.Video;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import elanic.in.rsenhancer.processing.RSImageProcessor;
import tv.danmaku.ijk.media.example.activities.VideoActivity;
import tv.danmaku.ijk.media.example.utils.screenshot;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ISurfaceTextureHost;

import static com.arthenica.mobileffmpeg.FFmpeg.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.FFmpeg.RETURN_CODE_SUCCESS;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class TextureRenderView extends GLTextureView implements IRenderView {
    private static final String TAG = "TextureRenderView   ";
    private MeasureHelper mMeasureHelper;
    private Context mContext;
    private Handler mBackgroundHandler;

    private static final int PROCESS_SAVED_IMAGE_MSG = 1002;
    private static final int PROCESS_SAVED_IMAGE_MSG_NOTNOW = 2001;

    private int DETECTION_IMAGE_WIDTH = 854;
    private int DETECTION_IMAGE_HEIGHT = 480;
    private int PREVIEW_IMAGE_WIDTH = 1920;
    private int PREVIEW_IMAGE_HEIGHT = 1080;

    private static final int PROCESS_KEEP_ALIVE_MSG = 1004;

    private static final int PROCESS_FRAMES_AFTER_MOTION_DETECTED = 3;

    private RenderScript mRS = null;
    private MotionDetectionRS mMotionDetection;
    private RSImageProcessor mRSProcessor;
    private android.graphics.Rect mPreviousDiffRect = null;

    private FrameUpdateListener mFrameUpdateListener = null;

    private Detector mDetector = null;
    private FaceDetector mFaceDetector = null;

    private long mLastTaskSentTimestamp = 0L;
    private int mSavingCounter = 0;
    private TextureRenderView mTextureRender;

    private BackgroundSubtractor mMOG2;
    private boolean mSubtractorInited = false;
    private Rect mPreviousObjectRect = null;
    private int mIntersectCount = 0;
    private int mPreviousPersonNum = 0;

    private boolean mRecording = false;

    private long mLastCleanPicsTimestamp = 0L;

    public interface FrameUpdateListener {
        public void onFrameUpdate(long currentTime);
    }

    public void setFrameUpdateListener(FrameUpdateListener l) {
        mFrameUpdateListener = l;
    }

    static {
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV initialize success");
        } else {
            Log.i(TAG, "OpenCV initialize failed");
        }
    }
    /**
     * Initializes the UI and initiates the creation of a motion detector.
     */
    public void initDetectionContext() {
        String devModel = Build.MODEL;
        /*if (devModel != null && devModel.equals("JDN-W09") && PREVIEW_IMAGE_HEIGHT>960) {
            PREVIEW_IMAGE_WIDTH = 1280;
            PREVIEW_IMAGE_HEIGHT = 960;
        }*/

        DETECTION_IMAGE_HEIGHT = DETECTION_IMAGE_WIDTH * PREVIEW_IMAGE_HEIGHT  / PREVIEW_IMAGE_WIDTH;
        Log.i(TAG,"DETECTION_IMAGE_HEIGHT " + DETECTION_IMAGE_HEIGHT);

        mRS = RenderScript.create(mContext);
        mMotionDetection = new MotionDetectionRS(mContext.getSharedPreferences(
                MotionDetectionRS.PREFS_NAME, Context.MODE_PRIVATE),mRS,
                PREVIEW_IMAGE_WIDTH,PREVIEW_IMAGE_HEIGHT,DETECTION_IMAGE_WIDTH,DETECTION_IMAGE_HEIGHT);
        mRSProcessor = new RSImageProcessor(mRS);
        mRSProcessor.initialize(DETECTION_IMAGE_WIDTH, DETECTION_IMAGE_HEIGHT);

        mDetector = new Detector(mContext);
        mFaceDetector = new FaceDetector(mContext);

        mMOG2 = Video.createBackgroundSubtractorKNN(5,100,false);//Video.createBackgroundSubtractorMOG2();
        //mMOG2 = Video.createBackgroundSubtractorMOG2();//Video.createBackgroundSubtractorMOG2();
        //mMOG2.setHistory(5);
        //mMOG2.setDetectShadows(false);
        //mMOG2.setComplexityReductionThreshold(0);
    }
    class MyCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {

            File file = null;
            URL url;
            HttpURLConnection urlConnection = null;
            switch (msg.what) {
                case PROCESS_SAVED_IMAGE_MSG:
                    Log.d(TAG, "Processing file: " + msg.obj);
                    file = new File(msg.obj.toString());
                    try {
                        url = new URL("http://127.0.0.1:" + 3000 + "/api/post?url=" + msg.obj);

                        urlConnection = (HttpURLConnection) url
                                .openConnection();

                        int responseCode = urlConnection.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            Log.d(TAG, "connect success ");
                        } else {
                            file.delete();
                        }
                    } catch (Exception e) {
                        file.delete();
                        urlConnection = null;
                        //e.printStackTrace();
                        Log.v(TAG, "Detector is not running");
                    } finally {
                        if (urlConnection != null) {
                            urlConnection.disconnect();
                             return true;
                        }
                    }
                    break;
                case PROCESS_KEEP_ALIVE_MSG:
                    Log.d(TAG, "Processing keep alive");
                    try {
                        url = new URL("http://127.0.0.1:3380/camera_keepalive");

                        urlConnection = (HttpURLConnection) url
                                .openConnection();
                        int responseCode = urlConnection.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            Log.d(TAG, "keep alive success ");
                        } else {
                            Log.d(TAG, "keep alive failed ");
                        }
                    } catch (Exception e) {
                        urlConnection = null;
                        //e.printStackTrace();
                        Log.v(TAG, "Monitor is not running");
                    } finally {
                        if (urlConnection != null) {
                            urlConnection.disconnect();
                            return true;
                        }
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    }
    public TextureRenderView(Context context) {
        super(context);
        mContext = context;
        mTextureRender = this;
        HandlerThread handlerThread = new HandlerThread("BackgroundThread");
        handlerThread.start();
        MyCallback callback = new MyCallback();
        mBackgroundHandler = new Handler(handlerThread.getLooper(), callback);

        initView(context);
        initDetectionContext();
    }

    public TextureRenderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTextureRender = this;
        initView(context);
        initDetectionContext();
    }

    public TextureRenderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTextureRender = this;
        initView(context);
        initDetectionContext();
    }
    /*
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public TextureRenderView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
            initView(context);
            initDetectionContext();
        }
    */
    private void initView(Context context) {
        mMeasureHelper = new MeasureHelper(this);
        mSurfaceCallback = new SurfaceCallback(this);
        setSurfaceTextureListener(mSurfaceCallback);

        mLastCleanPicsTimestamp = System.currentTimeMillis();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public boolean shouldWaitForResize() {
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        mSurfaceCallback.willDetachFromWindow();
        super.onDetachedFromWindow();
        mSurfaceCallback.didDetachFromWindow();
    }

    //--------------------
    // Layout & Measure
    //--------------------
    @Override
    public void setVideoSize(int videoWidth, int videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            mMeasureHelper.setVideoSize(videoWidth, videoHeight);
            requestLayout();
        }
    }

    @Override
    public void setVideoSampleAspectRatio(int videoSarNum, int videoSarDen) {
        if (videoSarNum > 0 && videoSarDen > 0) {
            mMeasureHelper.setVideoSampleAspectRatio(videoSarNum, videoSarDen);
            requestLayout();
        }
    }

    @Override
    public void setVideoRotation(int degree) {
        mMeasureHelper.setVideoRotation(degree);
        setRotation(degree);
    }

    @Override
    public void setAspectRatio(int aspectRatio) {
        mMeasureHelper.setAspectRatio(aspectRatio);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mMeasureHelper.doMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(mMeasureHelper.getMeasuredWidth(), mMeasureHelper.getMeasuredHeight());
    }

    //--------------------
    // TextureViewHolder
    //--------------------

    public IRenderView.ISurfaceHolder getSurfaceHolder() {
        return new InternalSurfaceHolder(this, mSurfaceCallback.mSurfaceTexture, mSurfaceCallback);
    }

    private static final class InternalSurfaceHolder implements IRenderView.ISurfaceHolder {
        private TextureRenderView mTextureView;
        private SurfaceTexture mSurfaceTexture;
        private ISurfaceTextureHost mSurfaceTextureHost;

        public InternalSurfaceHolder(@NonNull TextureRenderView textureView,
                                     @Nullable SurfaceTexture surfaceTexture,
                                     @NonNull ISurfaceTextureHost surfaceTextureHost) {
            mTextureView = textureView;
            mSurfaceTexture = surfaceTexture;
            mSurfaceTextureHost = surfaceTextureHost;
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        public void bindToMediaPlayer(IMediaPlayer mp) {
            Log.d(TAG,"GL bindToMediaPlayer");
            if (mp == null)
                return;

            if(mTextureView.mRender!=null && mTextureView.mRender.getVideoTexture() != null){
                mp.setSurface(new Surface(mTextureView.mRender.getVideoTexture()));
            } else {
                Log.e(TAG,"can't set surface to media player due to surface is not initialed");

                final Handler h = new Handler();
                h.postDelayed(new Runnable()
                {
                    private long time = 0;

                    @Override
                    public void run()
                    {
                        // do stuff then
                        // can call h again after work!
                        time += 1000;
                        Log.d(TAG, "Recheck if surface created, going for... " + time);
                        if(mTextureView.mRender!=null && mTextureView.mRender.getVideoTexture() != null){
                            mp.setSurface(new Surface(mTextureView.mRender.getVideoTexture()));
                        } else {
                            h.postDelayed(this, 1000);
                        }
                    }
                }, 1000); // 1 second delay (takes millis)
            }
            /*if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) &&
                    (mp instanceof ISurfaceTextureHolder)) {
                ISurfaceTextureHolder textureHolder = (ISurfaceTextureHolder) mp;
                mTextureView.mSurfaceCallback.setOwnSurfaceTexture(false);

                SurfaceTexture surfaceTexture = textureHolder.getSurfaceTexture();
                if (surfaceTexture != null) {
                    mTextureView.setSurfaceTexture(surfaceTexture);
                } else {
                    textureHolder.setSurfaceTexture(mSurfaceTexture);
                    textureHolder.setSurfaceTextureHost(mTextureView.mSurfaceCallback);
                }
            } else {
                mp.setSurface(openSurface());
            }*/
        }

        @NonNull
        @Override
        public IRenderView getRenderView() {
            return mTextureView;
        }

        @Nullable
        @Override
        public SurfaceHolder getSurfaceHolder() {
            return null;
        }

        @Nullable
        @Override
        public SurfaceTexture getSurfaceTexture() {
            return mSurfaceTexture;
        }

        @Nullable
        @Override
        public Surface openSurface() {
            if (mSurfaceTexture == null)
                return null;
            return new Surface(mSurfaceTexture);
        }
    }
    FastVideoTextureRenderer mRender;
    //-------------------------
    // SurfaceHolder.Callback
    //-------------------------

    @Override
    public void addRenderCallback(IRenderCallback callback) {
        mSurfaceCallback.addRenderCallback(callback);
    }

    @Override
    public void removeRenderCallback(IRenderCallback callback) {
        mSurfaceCallback.removeRenderCallback(callback);
    }

    private SurfaceCallback mSurfaceCallback;

    class SurfaceCallback implements TextureView.SurfaceTextureListener, ISurfaceTextureHost {
        private long mStartTime = 0;
        private SurfaceTexture mSurfaceTexture;
        private boolean mIsFormatChanged;
        private int mWidth;
        private int mHeight;

        private boolean mOwnSurfaceTexture = true;
        private boolean mWillDetachFromWindow = false;
        private boolean mDidDetachFromWindow = false;

        private WeakReference<TextureRenderView> mWeakRenderView;
        private Map<IRenderCallback, Object> mRenderCallbackMap = new ConcurrentHashMap<IRenderCallback, Object>();


        public SurfaceCallback(@NonNull TextureRenderView renderView) {
            mWeakRenderView = new WeakReference<TextureRenderView>(renderView);
        }

        public void setOwnSurfaceTexture(boolean ownSurfaceTexture) {
            mOwnSurfaceTexture = ownSurfaceTexture;
        }

        public void addRenderCallback(@NonNull IRenderCallback callback) {
            mRenderCallbackMap.put(callback, callback);

            ISurfaceHolder surfaceHolder = null;
            if (mSurfaceTexture != null) {
                if (surfaceHolder == null)
                    surfaceHolder = new InternalSurfaceHolder(mWeakRenderView.get(), mSurfaceTexture, this);
                callback.onSurfaceCreated(surfaceHolder, mWidth, mHeight);
            }

            if (mIsFormatChanged) {
                if (surfaceHolder == null)
                    surfaceHolder = new InternalSurfaceHolder(mWeakRenderView.get(), mSurfaceTexture, this);
                callback.onSurfaceChanged(surfaceHolder, 0, mWidth, mHeight);
            }
        }

        public void removeRenderCallback(@NonNull IRenderCallback callback) {
            mRenderCallbackMap.remove(callback);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mSurfaceTexture = surface;
            mIsFormatChanged = false;
            mWidth = 0;
            mHeight = 0;
            mRender = new FastVideoTextureRenderer(mContext,surface,width,height,mTextureRender);

            Log.d(TAG,"GL onSurfaceTextureAvailable");

            ISurfaceHolder surfaceHolder = new InternalSurfaceHolder(mWeakRenderView.get(), surface, this);
            for (IRenderCallback renderCallback : mRenderCallbackMap.keySet()) {
                renderCallback.onSurfaceCreated(surfaceHolder, 0, 0);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            mSurfaceTexture = surface;
            mIsFormatChanged = true;
            mWidth = width;
            mHeight = height;

            ISurfaceHolder surfaceHolder = new InternalSurfaceHolder(mWeakRenderView.get(), surface, this);
            for (IRenderCallback renderCallback : mRenderCallbackMap.keySet()) {
                renderCallback.onSurfaceChanged(surfaceHolder, 0, width, height);
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mSurfaceTexture = surface;
            mIsFormatChanged = false;
            mWidth = 0;
            mHeight = 0;

            ISurfaceHolder surfaceHolder = new InternalSurfaceHolder(mWeakRenderView.get(), surface, this);
            for (IRenderCallback renderCallback : mRenderCallbackMap.keySet()) {
                renderCallback.onSurfaceDestroyed(surfaceHolder);
            }

            Log.d(TAG, "onSurfaceTextureDestroyed: destroy: " + mOwnSurfaceTexture);
            return mOwnSurfaceTexture;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            long currentTime = System.currentTimeMillis();
            if (mFrameUpdateListener != null) {
                mFrameUpdateListener.onFrameUpdate(currentTime);
            }
        }

        //-------------------------
        // ISurfaceTextureHost
        //-------------------------

        @Override
        public void releaseSurfaceTexture(SurfaceTexture surfaceTexture) {
            if (surfaceTexture == null) {
                Log.d(TAG, "releaseSurfaceTexture: null");
            } else if (mDidDetachFromWindow) {
                if (surfaceTexture != mSurfaceTexture) {
                    Log.d(TAG, "releaseSurfaceTexture: didDetachFromWindow(): release different SurfaceTexture");
                    surfaceTexture.release();
                } else if (!mOwnSurfaceTexture) {
                    Log.d(TAG, "releaseSurfaceTexture: didDetachFromWindow(): release detached SurfaceTexture");
                    surfaceTexture.release();
                } else {
                    Log.d(TAG, "releaseSurfaceTexture: didDetachFromWindow(): already released by TextureView");
                }
            } else if (mWillDetachFromWindow) {
                if (surfaceTexture != mSurfaceTexture) {
                    Log.d(TAG, "releaseSurfaceTexture: willDetachFromWindow(): release different SurfaceTexture");
                    surfaceTexture.release();
                } else if (!mOwnSurfaceTexture) {
                    Log.d(TAG, "releaseSurfaceTexture: willDetachFromWindow(): re-attach SurfaceTexture to TextureView");
                    setOwnSurfaceTexture(true);
                } else {
                    Log.d(TAG, "releaseSurfaceTexture: willDetachFromWindow(): will released by TextureView");
                }
            } else {
                if (surfaceTexture != mSurfaceTexture) {
                    Log.d(TAG, "releaseSurfaceTexture: alive: release different SurfaceTexture");
                    surfaceTexture.release();
                } else if (!mOwnSurfaceTexture) {
                    Log.d(TAG, "releaseSurfaceTexture: alive: re-attach SurfaceTexture to TextureView");
                    setOwnSurfaceTexture(true);
                } else {
                    Log.d(TAG, "releaseSurfaceTexture: alive: will released by TextureView");
                }
            }
        }

        public void willDetachFromWindow() {
            Log.d(TAG, "willDetachFromWindow()");
            mWillDetachFromWindow = true;
        }

        public void didDetachFromWindow() {
            Log.d(TAG, "didDetachFromWindow()");
            mDidDetachFromWindow = true;
        }
    }
    private double calcIOU(Rect rect1,Rect rect2){
        android.graphics.Rect rect_1 = new android.graphics.Rect(rect1.x,
                rect1.y,
                rect1.x+rect1.width,
                rect1.y+rect1.height);

        android.graphics.Rect rect_2 = new android.graphics.Rect(rect2.x,
                rect2.y,
                rect2.x+rect2.width,
                rect2.y+rect2.height);

        Log.d(TAG,"UO Area 1 "+rect_1.toShortString()+" 2 "+rect_2.toShortString());
        rect_1.intersect(rect_2);
        return 1.0*rect_1.width()*rect_2.height()/(rect_2.width()*rect_2.height());

    }
    public boolean detectObjectChanges(Bitmap bmp){

        // Let's detect if there's big changes
        long tsMatStart = System.currentTimeMillis();

        Mat rgba = new Mat();
        Bitmap resizedBmp = mMotionDetection.resizeBmp(bmp,DETECTION_IMAGE_WIDTH,DETECTION_IMAGE_HEIGHT);
        Utils.bitmapToMat(resizedBmp, rgba);

        Mat rgb = new Mat();
        Mat fgMask = new Mat();

        //Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2GRAY);
        //Imgproc.GaussianBlur(rgb,rgb,new Size(21, 21), 0);

        final List<Rect> rects = new ArrayList<>();
        if(mSubtractorInited == true){
            mMOG2.apply(rgb,fgMask,0.5);

            //reference https://github.com/melvincabatuan/BackgroundSubtraction/blob/master/app/jni/ImageProcessing.cpp#L78
            //Imgproc.GaussianBlur(fgMask,fgMask,new Size(11,11), 3.5,3.5);
            //Imgproc.threshold(fgMask,fgMask,10,255,Imgproc.THRESH_BINARY);

            final List<MatOfPoint> points = new ArrayList<>();
            final Mat hierarchy = new Mat();
            Imgproc.findContours(fgMask, points, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            Rect biggest = null;
            for (MatOfPoint item :points){
                //Log.d(TAG,"UO Area result "+item);

                double area = Imgproc.contourArea(item);
                if(area > 1000){
                    Rect rect = Imgproc.boundingRect(item);
                    if(biggest==null){
                        biggest = rect;
                    } else if(rect.area()>biggest.area()){
                        biggest = rect;
                    }
                    rects.add(rect);
                }
            }
            if(biggest != null){
                if(mPreviousObjectRect !=null){
                    //mPreviousObjectRect.
                    double iou = calcIOU(biggest,mPreviousObjectRect);
                    if(iou > 0.4){
                        mIntersectCount++;
                        Log.d(TAG,"UO Intersect IOU "+iou+" count "+mIntersectCount);
                        if(mIntersectCount>2){
                            if(mRecording){
                                Log.i(TAG,"There is something spotted ");
                                FFmpeg.cancel();
                                String result = FFmpeg.getLastCommandOutput();
                                Log.d(TAG,"FFMPEG output is "+result);
                            }
                        }
                    } else {
                        mIntersectCount = 0;
                        mPreviousObjectRect = biggest;
                    }
                } else {
                    mIntersectCount = 0;
                    mPreviousObjectRect = biggest;
                }
            } else {
                mPreviousObjectRect = null;
                mIntersectCount = 0;
            }

            long tsMatEnd = System.currentTimeMillis();
            Log.v(TAG,"time diff (Mat Run) "+(tsMatEnd-tsMatStart));
        } else {
            mSubtractorInited = true;
            tsMatStart = System.currentTimeMillis();
            mMOG2.apply(rgb,fgMask,-1);

            long tsMatEnd = System.currentTimeMillis();
            Log.v(TAG,"time diff (Mat Train) "+(tsMatEnd-tsMatStart));
        }
        double areaTotal = 0;
        for(Rect rect:rects){
            Log.d(TAG,"UO Area "+rect.area()+" rect "+rect.toString());
            areaTotal += rect.area();
        }
        double diffarea = areaTotal /(DETECTION_IMAGE_WIDTH*DETECTION_IMAGE_HEIGHT)/100;

        VideoActivity.setPixelDiff(diffarea);
        return rects.size()>0;
    }
    public int doFaceDetectionAndSendTask(List<Classifier.Recognition> result,Bitmap bmp){
        long tsStart;
        long tsEnd;
        int face_num = 0;
        String filename = "";
        File file = null;

        for(final Classifier.Recognition recognition:result){
            tsStart = System.currentTimeMillis();
            RectF rectf = recognition.getLocation();
            Log.d(TAG,"recognition rect: "+rectf.toString());
            Bitmap personBmp = getCropBitmapByCPU(bmp,rectf);
            int num = mFaceDetector.predict_image(personBmp);
            tsEnd = System.currentTimeMillis();
            Log.v(TAG,"time diff (FD) "+(tsEnd-tsStart));
            if(num > 0){
                face_num+=num;
                try {
                    tsStart = System.currentTimeMillis();
                    file = screenshot.getInstance()
                            .saveScreenshotToPicturesFolder(mContext, personBmp, "frame_");

                    filename = file.getAbsolutePath();
                    tsEnd = System.currentTimeMillis();
                    Log.v(TAG,"time diff (Save) "+(tsEnd-tsStart));

                } catch (Exception e) {
                    e.printStackTrace();

                    //delete all jpg file in Download dir when disk is full
                    deleteAllCapturedPics();
                }
                //bitmap.recycle();
                //bitmap = null;
                if(filename.equals("")){
                    continue;
                }
                if(file == null){
                    continue;
                }

                mLastTaskSentTimestamp = System.currentTimeMillis();
                mBackgroundHandler.obtainMessage(PROCESS_SAVED_IMAGE_MSG, filename).sendToTarget();
            }
        }

        VideoActivity.setNumberOfFaces(face_num);
        return face_num;
    }
    public void doSendDummyTask(){
        mBackgroundHandler.obtainMessage(PROCESS_KEEP_ALIVE_MSG).sendToTarget();

        return;
    }
    private void checkIfNeedSendDummyTask(){

        long tm = System.currentTimeMillis();
        if (tm - mLastTaskSentTimestamp > 30*1000) {
            mLastTaskSentTimestamp = System.currentTimeMillis();
            doSendDummyTask();
            Log.d(TAG,"To send dummy task to keep alive for client status");
        }

    }
    public void processBitmap(Bitmap bmp){
        long tsStart = System.currentTimeMillis();
        long tsEnd;

        boolean bigChanged = true;

        //clean up pictures every 2 mins
        if (tsStart-mLastCleanPicsTimestamp > 2*60*1000) {
            Log.d("##RDBG", "clean pictures every 2 mins");
            mLastCleanPicsTimestamp = tsStart;
            deleteAllCapturedPics();
        }

        if(mPreviousPersonNum == 0){
            bigChanged = mMotionDetection.detect(bmp);
            VideoActivity.setMotionStatus(bigChanged);
            tsEnd = System.currentTimeMillis();

            Log.v(TAG,"time diff (motion) "+(tsEnd-tsStart));
            VideoActivity.setPixelDiff(mMotionDetection.getPercentageOfDifferentPixels());
            if(!bigChanged){
                if(mSavingCounter > 0){
                    mSavingCounter--;
                } else {
                    //bmp.recycle();
                    VideoActivity.setMotionStatus(false);
                    VideoActivity.setNumberOfFaces(0);
                    boolean ifChanged = detectObjectChanges(bmp);
                    Log.d(TAG,"Object changed after person leaving: "+ifChanged);
                    checkIfNeedSendDummyTask();
                    if(!ifChanged && mRecording){
                        FFmpeg.cancel();
                        String result = FFmpeg.getLastCommandOutput();
                        Log.d(TAG,"FFMPEG output is "+result);
                    }
                    return;
                }
            } else {
                mSavingCounter=PROCESS_FRAMES_AFTER_MOTION_DETECTED;
            }
        }
        tsStart = System.currentTimeMillis();
        List<Classifier.Recognition> result =  mDetector.processImage(bmp);
        int personNum = result.size();
        mPreviousPersonNum = personNum;
        VideoActivity.setNumberOfPerson(personNum);
        tsEnd = System.currentTimeMillis();
        Log.v(TAG,"time diff (OD) "+(tsEnd-tsStart));

        if(personNum>0){
            doFaceDetectionAndSendTask(result,bmp);
            if(mRecording==false){
                mRecording = true;
                //"-i", url, "-acodec", "copy", "-vcodec", "copy", targetFile.toString()
                Log.v(TAG,"FFMPEG Starting video recording");
                File mp4File = getOutputMediaFile("video_");
                File mp4File2 = getOutputMediaFile("video_resized_");
                String command = "-i "+VideoActivity.getVideoURL()+
                        //" -c:v h264_mediacodec -b:v 500k"+
                        //" -vf scale=-2:640 " +
                        " -vcodec copy " +
                        mp4File;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //Do something after 100ms
                        FFmpeg.execute(command);
                        int rc = FFmpeg.getLastReturnCode();
                        String output = FFmpeg.getLastCommandOutput();

                        mRecording = false;
                        if (rc == RETURN_CODE_SUCCESS) {
                            Log.i(Config.TAG, "Record Command execution completed successfully: "+output);
                        } else if (rc == RETURN_CODE_CANCEL) {
                            Log.i(Config.TAG, "Record Command execution cancelled by user.");
                            /*
                            new Mp4Composer(mp4File.toString(), mp4File2.toString())
                                    .size( DETECTION_IMAGE_WIDTH,  DETECTION_IMAGE_HEIGHT)
                                    .videoBitrate(500000)
                                    .mute(true)
                                    .listener(new Mp4Composer.Listener() {
                                        @Override
                                        public void onProgress(double progress) {
                                            Log.d(TAG, "Record onProgress = " + progress);
                                        }

                                        @Override
                                        public void onCompleted() {
                                            Log.d(TAG, "Record onCompleted()");
                                            //runOnUiThread(() -> {
                                            //    Toast.makeText(context, "codec complete path =" + destPath, Toast.LENGTH_SHORT).show();
                                            //});
                                        }

                                        @Override
                                        public void onCanceled() {
                                            Log.d(TAG, "Record onCanceled");
                                        }

                                        @Override
                                        public void onFailed(Exception exception) {
                                            Log.e(TAG, "Record onFailed()", exception);
                                        }
                                    })
                                    .start();
                            Log.i(Config.TAG, "Record Resize Started");
                                   */
                        } else {
                            Log.i(Config.TAG, String.format("Record Command execution failed with rc=%d and output=%s.", rc, output));
                        }
                    }
                }).start();
                //mFFmpegRecorder = new FFmpegRecorder(mContext,VideoActivity.getVideoURL(),mp4File);
            }
        }

        checkIfNeedSendDummyTask();

        return;
    }
    private File getOutputMediaFile(String filename) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        File mediaStorageDirectory = new File(
                getSDCardPath()
                        + File.separator+Environment.DIRECTORY_DOWNLOADS);
        // Create the storage directory if it does not exist
        if (!mediaStorageDirectory.exists()) {
            if (!mediaStorageDirectory.mkdirs()) {
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmmss_SSS").format(new Date());
        File mediaFile;
        String mImageName = filename + timeStamp + ".mp4";
        mediaFile = new File(mediaStorageDirectory.getPath() + File.separator + mImageName);
        return mediaFile;
    }
    private File getSDCardPath(){
            String path = null;
            File sdCardFile = null;
            List<String> sdCardPossiblePath = Arrays.asList("external_sd", "ext_sd", "external", "extSdCard");
            for (String sdPath : sdCardPossiblePath) {
                File file = new File("/mnt/", sdPath);
                if (file.isDirectory() && file.canWrite()) {
                    path = file.getAbsolutePath();
                    String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
                    File testWritable = new File(path, "test_" + timeStamp);
                    if (testWritable.mkdirs()) {
                        testWritable.delete();
                    }
                    else {
                        path = null;
                    }
                }
            }
            if (path != null) {
                sdCardFile = new File(path);
            }
            else {
                sdCardFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
            }
            return sdCardFile;
    }
    private Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(/*www.j  a va2  s  .  co  m*/
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        //if (source != null && !source.isRecycled()) {
        //    source.recycle();
        //}

        return resultBitmap;
    }
    private Thread mDeletePicsThread = null;

    private void deleteAllCapturedPics() {
        if (mDeletePicsThread != null) {
            return;
        }

        long now = System.currentTimeMillis();

        mDeletePicsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File[] files = f.listFiles();

                    for (File fPic: files) {
                        if (fPic.isFile()/* && fPic.getPath().endsWith(".jpg")*/ && (now - fPic.lastModified() > 1               *60*1000)) {
                            fPic.delete();
                        }
                    }
                }
                catch (Exception ex) {}

                mDeletePicsThread = null;
            }
        });
        mDeletePicsThread.start();
    }

    //--------------------
    // Accessibility
    //--------------------

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(TextureRenderView.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(TextureRenderView.class.getName());
    }
}