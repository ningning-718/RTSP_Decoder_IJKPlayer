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

package tv.danmaku.ijk.media.example.activities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.application.Settings;
import tv.danmaku.ijk.media.example.fragments.TracksFragment;
import tv.danmaku.ijk.media.example.widget.media.AndroidMediaController;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.media.MeasureHelper;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

public class VideoActivity extends AppCompatActivity implements TracksFragment.ITrackHolder {
    private static final String TAG = "VideoActivity";

    private static String mVideoURL;
    private Uri    mVideoUri;

    private AndroidMediaController mMediaController;
    private IjkVideoView mVideoView;
    private TextView mToastTextView;
    private TableLayout mHudView;
    private DrawerLayout mDrawerLayout;
    private ViewGroup mRightDrawer;

    private Settings mSettings;
    private boolean mBackPressed;

    private long mLastFrameTimeStamp = 0L;

    public static String getVideoURL(){
        return mVideoURL;
    }

    private void quitAndStartLater() {
        Intent intent = new Intent(VideoActivity.this, VideoActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        //PendingIntent pendingIntent = PendingIntent.getActivity(VideoActivity.this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        Intent resultIntent = new Intent(this, SetupActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        PendingIntent pendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);

        mgr.set(AlarmManager.RTC, System.currentTimeMillis()+15000, pendingIntent);

        finish();
        System.exit(2);
    }
    public class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        public ExceptionHandler() {
        }
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            ex.printStackTrace();
            quitAndStartLater();
        }
    }
    private static boolean mHasMotion;
    private static int mFaceNum=0;
    private static int mPersonNum=0;
    private static double mPixelDiff;

    public static Intent newIntent(Context context, String videoUrl) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("videoUrl", videoUrl);
        return intent;
    }

    public static void intentTo(Context context, String videoUrl) {
        context.startActivity(newIntent(context, videoUrl));
    }
    public static double getPixelDiff(){
        return mPixelDiff;
    }

    public static void setPixelDiff(double pixedlDiff){
        mPixelDiff = pixedlDiff;
    }
    public static boolean getMotionStatus(){
        return mHasMotion;
    }
    public static int getPersonNum(){
        return mPersonNum;
    }
    public static int getFaceNum(){
        return mFaceNum;
    }

    public static void setMotionStatus(boolean motion){
        mHasMotion = motion;
    }
    public static void setNumberOfPerson(int number){
        mPersonNum = number;
    }
    public static void setNumberOfFaces(int number){
        mFaceNum = number;
    }
    public String getSavedCameraURL(){

        SharedPreferences sp = getSharedPreferences(CameraScanActivity.CAMERAIPKEY, Context.MODE_PRIVATE);
        mVideoURL = sp.getString("videoURL", "");

        return "";
        //String videoUrl = "rtsp://admin:abc12345@"+ci.getIp()+":554/cam/realmonitor?channel=1&subtype=0";
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
        setContentView(R.layout.activity_player);

        mSettings = new Settings(this);

        // handle arguments
        mVideoURL = getIntent().getStringExtra("videoUrl");
        /*if (TextUtils.isEmpty(mVideoURL)) {
            Toast.makeText(this, "Camera IP is not set, please scan and set it first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }*/

        // init UI
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, false);
        mMediaController.setSupportActionBar(actionBar);

        mToastTextView = (TextView) findViewById(R.id.toast_text_view);
        mHudView = (TableLayout) findViewById(R.id.hud_view);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mRightDrawer = (ViewGroup) findViewById(R.id.right_drawer);

        mDrawerLayout.setScrimColor(Color.TRANSPARENT);

        // init player
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);

        ImageView imageView =  findViewById(R.id.qr_view);
        try {
            // generate a 150x150 QR code
            Bitmap bm = encodeAsBitmap(getUniqueSerialNO(), 150, 150);

            if(bm != null) {
                imageView.setImageBitmap(bm);
            }
        } catch (WriterException e) {
            e.printStackTrace();
        }
        mVideoView.setMediaController(mMediaController);
        mVideoView.setHudView(mHudView);

        // prefer mVideoURL
        if (mVideoURL != null)
            mVideoView.setVideoRTSP(mVideoURL);
        else {
            Log.e(TAG, "Null Data Source\n");
            quitAndStartLater();
            return;
        }
        mVideoView.setVideoFrameUpdateListener(new IjkVideoView.VideoFrameUpdateListener() {
            @Override
            public void onVideoFrameUpdate(long tm) {
                mLastFrameTimeStamp = tm;
            }
        });
        mVideoView.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mp, int what, int extra) {
                quitAndStartLater();
                return false;
            }
        });
        mVideoView.start();

        Timer myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                long cur = System.currentTimeMillis();
                if (mLastFrameTimeStamp > 0 && cur - mLastFrameTimeStamp > 5000) {
                    Toast.makeText(VideoActivity.this, "frame timespan exceeds 5 seconds, exit!", Toast.LENGTH_LONG).show();

                    quitAndStartLater();
                }
            }

        }, 20000, 10000);
    }

    Bitmap encodeAsBitmap(String str,int width,int height) throws WriterException {
        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(str,
                    BarcodeFormat.QR_CODE, width, height, null);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }
        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, w, h);
        return bitmap;
    }
    private static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if ( !nif.getName().equalsIgnoreCase("eth0") &&
                        !nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    // res1.append(Integer.toHexString(b & 0xFF) + ":");
                    res1.append(String.format("%02X",b));
                }

                //if (res1.length() > 0) {
                //    res1.deleteCharAt(res1.length() - 1);
                //}
                return res1.toString();
            }
        } catch (Exception ex) {
            //handle exception
        }
        return "";
    }
    private String getUniqueSerialNO(){
        String UDID = getMacAddr();
        if (UDID == null || UDID.length() == 0) {
            UDID = android.provider.Settings.Secure.getString(this.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);
        }
        if (UDID == null || UDID.length() == 0) {
            UDID = "0000000";
        }

        return UDID.toLowerCase();
    }
    @Override
    public void onBackPressed() {
        mBackPressed = true;

        super.onBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mBackPressed || !mVideoView.isBackgroundPlayEnabled()) {
            mVideoView.stopPlayback();
            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        } else {
            mVideoView.enterBackground();
        }
        IjkMediaPlayer.native_profileEnd();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_ratio) {
            int aspectRatio = mVideoView.toggleAspectRatio();
            String aspectRatioText = MeasureHelper.getAspectRatioText(this, aspectRatio);
            mToastTextView.setText(aspectRatioText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_toggle_player) {
            int player = mVideoView.togglePlayer();
            String playerText = IjkVideoView.getPlayerText(this, player);
            mToastTextView.setText(playerText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_toggle_render) {
            int render = mVideoView.toggleRender();
            String renderText = IjkVideoView.getRenderText(this, render);
            mToastTextView.setText(renderText);
            mMediaController.showOnce(mToastTextView);
            return true;
        } else if (id == R.id.action_show_info) {
            mVideoView.showMediaInfo();
        } else if (id == R.id.action_show_tracks) {
            if (mDrawerLayout.isDrawerOpen(mRightDrawer)) {
                Fragment f = getSupportFragmentManager().findFragmentById(R.id.right_drawer);
                if (f != null) {
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.remove(f);
                    transaction.commit();
                }
                mDrawerLayout.closeDrawer(mRightDrawer);
            } else {
                Fragment f = TracksFragment.newInstance();
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.right_drawer, f);
                transaction.commit();
                mDrawerLayout.openDrawer(mRightDrawer);
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public ITrackInfo[] getTrackInfo() {
        if (mVideoView == null)
            return null;

        return mVideoView.getTrackInfo();
    }

    @Override
    public void selectTrack(int stream) {
        mVideoView.selectTrack(stream);
    }

    @Override
    public void deselectTrack(int stream) {
        mVideoView.deselectTrack(stream);
    }

    @Override
    public int getSelectedTrack(int trackType) {
        if (mVideoView == null)
            return -1;

        return mVideoView.getSelectedTrack(trackType);
    }
}
