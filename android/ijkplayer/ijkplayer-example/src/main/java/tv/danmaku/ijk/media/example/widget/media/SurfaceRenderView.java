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
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.seu.magicfilter.camera.RTSPGlSurfaceView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ISurfaceTextureHolder;

public class SurfaceRenderView extends RTSPGlSurfaceView implements IRenderView ,GLSurfaceView.Renderer {
    private MeasureHelper mMeasureHelper;
    private Context mContext;

    public SurfaceRenderView(Context context) {
        super(context);
        initView(context);
    }

    /*@Override
    public void onDrawFrame(GL10 gl) {
    }*/
    public SurfaceRenderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    /*public SurfaceRenderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SurfaceRenderView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView(context);
    }*/

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        super.onSurfaceCreated(gl,config);
        Log.d(TAG,"GL onGLSurfaceCreated");
        mSurfaceCallback.onSurfaceTextureAvailable(super.getSurfaceTexture(),1920,1080);
        //initView(mContext);
    }
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        super.onSurfaceChanged(gl, width, height);
        Log.d(TAG,"GL onGLSurfaceChanged");
        mSurfaceCallback.onSurfaceTextureSizeChanged(1920,1080);
    }

    private void initView(Context context) {
        mContext = context;
        mMeasureHelper = new MeasureHelper(this);
        mSurfaceCallback = new SurfaceCallback(this,super.getSurfaceTexture());
        getHolder().addCallback(mSurfaceCallback);
        //noinspection deprecation
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public boolean shouldWaitForResize() {
        return true;
    }

    //--------------------
    // Layout & Measure
    //--------------------
    @Override
    public void setVideoSize(int videoWidth, int videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            mMeasureHelper.setVideoSize(videoWidth, videoHeight);
            getHolder().setFixedSize(videoWidth, videoHeight);
            super.setVideoSize(videoWidth, videoHeight);
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
        Log.e("", "SurfaceView doesn't support rotation (" + degree + ")!\n");
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
    // SurfaceViewHolder
    //--------------------

    private static final class InternalSurfaceHolder implements IRenderView.ISurfaceHolder {
        private SurfaceRenderView mSurfaceView;
        private SurfaceHolder mSurfaceHolder;
        private SurfaceTexture mSurfaceTexture;

        public InternalSurfaceHolder(@NonNull SurfaceRenderView surfaceView,
                                     @Nullable SurfaceHolder surfaceHolder,
                                     @NonNull SurfaceTexture surfaceTexture) {
            Log.d(TAG,"GL new InternalSurfaceHolder");
            mSurfaceView = surfaceView;
            mSurfaceHolder = surfaceHolder;
            mSurfaceTexture = surfaceTexture;
        }

        public void bindToMediaPlayer(IMediaPlayer mp) {
            Log.d(TAG,"GL bindToMediaPlayer");
            if (mp == null)
                return;

            //mp.setSurface(new Surface(mSurfaceTexture));
            if (mp != null) {
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) &&
                        (mp instanceof ISurfaceTextureHolder)) {
                    ISurfaceTextureHolder textureHolder = (ISurfaceTextureHolder) mp;
                    textureHolder.setSurfaceTexture(null);
                }
                mp.setDisplay(mSurfaceHolder);
            }
        }

        @NonNull
        @Override
        public IRenderView getRenderView() {
            Log.d(TAG,"GL getRenderView");
            return mSurfaceView;
        }

        @Nullable
        @Override
        public SurfaceHolder getSurfaceHolder() {
            Log.d(TAG,"GL getSurfaceHolder");
            return null;
        }

        @Nullable
        @Override
        public SurfaceTexture getSurfaceTexture() {
            Log.d(TAG,"GL getSurfaceTexture");
            return null;
            //return mSurfaceTexture;
        }

        @Nullable
        @Override
        public Surface openSurface() {
            if (mSurfaceTexture == null)
                return null;
            return new Surface(mSurfaceTexture);
            /*
            if (mSurfaceHolder == null)
                return null;
            return mSurfaceHolder.getSurface();
            */
        }
    }

    //-------------------------
    // SurfaceHolder.Callback
    //-------------------------

    @Override
    public void addRenderCallback(IRenderCallback callback) {
        Log.d(TAG,"GL addRenderCallback");
        mSurfaceCallback.addRenderCallback(callback);
    }

    @Override
    public void removeRenderCallback(IRenderCallback callback) {
        Log.d(TAG,"GL removeRenderCallback");
        mSurfaceCallback.removeRenderCallback(callback);
    }

    private SurfaceCallback mSurfaceCallback;

    private static final class SurfaceCallback implements SurfaceHolder.Callback {
        private SurfaceHolder mSurfaceHolder;
        private boolean mIsFormatChanged;
        private int mFormat;
        private int mWidth;
        private int mHeight;

        private WeakReference<SurfaceRenderView> mWeakSurfaceView;
        private WeakReference<SurfaceTexture> mSurfaceTexture;

        private Map<IRenderCallback, Object> mRenderCallbackMap = new ConcurrentHashMap<IRenderCallback, Object>();

        public SurfaceCallback(@NonNull SurfaceRenderView surfaceView,
                               @NonNull SurfaceTexture surfaceTexture) {
            mWeakSurfaceView = new WeakReference<SurfaceRenderView>(surfaceView);
            mSurfaceTexture = new WeakReference<SurfaceTexture>(surfaceTexture);
        }

        public void addRenderCallback(@NonNull IRenderCallback callback) {
            Log.d(TAG,"GL addRenderCallback");
            mRenderCallbackMap.put(callback, callback);

            ISurfaceHolder surfaceHolder = null;
            if (mSurfaceHolder != null) {
                if (surfaceHolder == null)
                    surfaceHolder = new InternalSurfaceHolder(mWeakSurfaceView.get(), null,
                            mSurfaceTexture.get());
                callback.onSurfaceCreated(surfaceHolder, mWidth, mHeight);
            }

            if (mIsFormatChanged) {
                if (surfaceHolder == null)
                    surfaceHolder = new InternalSurfaceHolder(mWeakSurfaceView.get(), null,
                            mSurfaceTexture.get());
                callback.onSurfaceChanged(surfaceHolder, mFormat, mWidth, mHeight);
            }
        }

        public void removeRenderCallback(@NonNull IRenderCallback callback) {
            mRenderCallbackMap.remove(callback);
        }

        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //mSurfaceTexture = surface;
            mIsFormatChanged = false;
            mWidth = 0;
            mHeight = 0;
            //mSurfaceTexture.set(surface);
            mSurfaceTexture = new WeakReference<SurfaceTexture>(surface);
            //mRender = new VideoTextureRenderer(mContext,surface,width,height,mTextureRender);

            Log.d(TAG,"GL onSurfaceTextureAvailable");

            ISurfaceHolder surfaceHolder = new SurfaceRenderView.InternalSurfaceHolder(mWeakSurfaceView.get(), null, surface);
            for (IRenderCallback renderCallback : mRenderCallbackMap.keySet()) {
                renderCallback.onSurfaceCreated(surfaceHolder, width, height);
            }
        }

        public void onSurfaceTextureSizeChanged(int width, int height) {
            //mSurfaceTexture = surface;
            Log.d(TAG,"GL onSurfaceTextureSizeChanged");
            mIsFormatChanged = true;
            mWidth = width;
            mHeight = height;

            ISurfaceHolder surfaceHolder = new SurfaceRenderView.InternalSurfaceHolder(mWeakSurfaceView.get(), null, mSurfaceTexture.get());
            for (IRenderCallback renderCallback : mRenderCallbackMap.keySet()) {
                renderCallback.onSurfaceChanged(surfaceHolder, 0, width, height);
            }
        }
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceHolder = holder;
            mIsFormatChanged = false;
            mFormat = 0;
            mWidth = 0;
            mHeight = 0;
            Log.d(TAG,"GL view surfaceCreated");

            /*
            ISurfaceHolder surfaceHolder = new InternalSurfaceHolder(mWeakSurfaceView.get(), null,
                    mSurfaceTexture.get());
            for (IRenderCallback renderCallback : mRenderCallbackMap.keySet()) {
                renderCallback.onSurfaceCreated(surfaceHolder, 0, 0);
            }*/
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mSurfaceHolder = null;
            mIsFormatChanged = false;
            mFormat = 0;
            mWidth = 0;
            mHeight = 0;

            Log.d(TAG,"GL view surfaceDestroyed");
            /*
            ISurfaceHolder surfaceHolder = new InternalSurfaceHolder(mWeakSurfaceView.get(), null,
                    mSurfaceTexture.get());
            for (IRenderCallback renderCallback : mRenderCallbackMap.keySet()) {
                renderCallback.onSurfaceDestroyed(surfaceHolder);
            }*/
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format,
                                   int width, int height) {
            mSurfaceHolder = holder;
            mIsFormatChanged = true;
            mFormat = format;
            mWidth = width;
            mHeight = height;

            Log.d(TAG,"GL view surfaceChanged");
            //mMeasureHelper.setVideoSize(width, height);

            /*
            ISurfaceHolder surfaceHolder = new InternalSurfaceHolder(mWeakSurfaceView.get(), null,
                    mSurfaceTexture.get());
            for (IRenderCallback renderCallback : mRenderCallbackMap.keySet()) {
                renderCallback.onSurfaceChanged(surfaceHolder, format, width, height);
            }*/
        }
    }

    //--------------------
    // Accessibility
    //--------------------

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(SurfaceRenderView.class.getName());
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            info.setClassName(SurfaceRenderView.class.getName());
        }
    }
}
