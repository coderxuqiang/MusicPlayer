package com.music.qiang.musicplayer.ui.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.music.qiang.musicplayer.R;
import com.music.qiang.musicplayer.events.MusicProgressEvent;
import com.music.qiang.musicplayer.events.PlayModeEvent;
import com.music.qiang.musicplayer.events.PlaybackEvent;
import com.music.qiang.musicplayer.events.QueueSkipEvent;
import com.music.qiang.musicplayer.events.ServiceControlEvent;
import com.music.qiang.musicplayer.model.MusicFile;
import com.music.qiang.musicplayer.playback.LocalPlayback;
import com.music.qiang.musicplayer.service.PlayBackService;
import com.music.qiang.musicplayer.support.utils.StringUtils;
import com.music.qiang.musicplayer.support.utils.ViewUtils;
import com.music.qiang.musicplayer.ui.view.RoundImageView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MusicPlayActivity extends AppCompatActivity implements View.OnClickListener {

    //***************views***************
    /**
     * 开始/暂停 按钮
     */
    private ImageView playButton, playPre, playNext, playQueueTypeIcon, playQueueIcon;
    private TextView musicPlayTime, musicPlayAlltime;
    private SeekBar seekBar;
    private RoundImageView roundImageView;
    private Toolbar toolbar;
    private FrameLayout rubberLayout;

    //***************基本数据***************
    /**
     * 播放模式，0-列表循环；1-单曲循环；2-随机播放
     */
    private int currentMode = 0;
    /**
     * 标记从哪里跳转过来，list:播放列表 notification:通知栏 等等
     * 只有从播放列表过来的才重新获取播放列表
     */
    private String from;
    private ArrayList<MusicFile> playList;
    private int playIndex;
    /**
     * 当前旋转动画的值
     */
    private float currentRotationValue;

    //***************对象***************
    private SharedPreferences sharedPreferences;
    private ObjectAnimator rubberRotation;
    /**
     * 播放业务处理类
     */
    private LocalPlayback localPlayback;
    private DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
    private Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
    private final ScheduledExecutorService mExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> mScheduleFuture;
    private final Handler mHandler = new Handler();
    private final Runnable mUpdateProgressTask = new Runnable() {
        @Override
        public void run() {
            updateSeekbar();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_play);
        EventBus.getDefault().register(this);
        fetchIntents();
        initViews();
        registListener();
        initData();

        Intent intent = new Intent(this, PlayBackService.class);
        Bundle bundle = new Bundle();
        bundle.putInt("currentMode", currentMode);
        bundle.putString("from", from);
        if (playList != null) {
            bundle.putSerializable("playList", playList);
            bundle.putInt("playIndex", playIndex);
        }
        intent.putExtras(bundle);
        startService(intent);
        // 如果进入页面时正在播放，开始旋转动画
        if (localPlayback.getState() == PlaybackState.STATE_PLAYING && rubberRotation == null) {
            startAnimation();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        stopSeekbarUpdate();
        startAnimation();
        mExecutorService.shutdown();
    }

    private void fetchIntents() {
        Bundle bundleObject = getIntent().getExtras();
        if (bundleObject != null) {
            from = bundleObject.getString("from");
            if (from != null && "list".equals(from)) {
                playList = (ArrayList<MusicFile>) bundleObject.getSerializable("playList");
                playIndex = bundleObject.getInt("playIndex");
            }
        }
    }

    private void initViews() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        //setTitle(playList.get(playIndex).musicName);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        rubberLayout = (FrameLayout) findViewById(R.id.fl_activity_music_play_rubber);
        roundImageView = (RoundImageView) findViewById(R.id.riv_activity_music_play);
        playButton = (ImageView) findViewById(R.id.ib_fragment_music_play);
        playPre = (ImageView) findViewById(R.id.ib_fragment_music_play_pre);
        playNext = (ImageView) findViewById(R.id.ib_fragment_music_play_next);
        playQueueTypeIcon = (ImageView) findViewById(R.id.iv_activity_music_play_type);
        playQueueIcon = (ImageView) findViewById(R.id.iv_activity_music_play_more);
        seekBar = (SeekBar) findViewById(R.id.sb_activity_music_play);
        musicPlayTime = (TextView) findViewById(R.id.tv_activity_music_play_time);
        musicPlayAlltime = (TextView) findViewById(R.id.tv_activity_music_play_all_time);
    }

    private void initData() {
        // 设置数字格式，如01
        df.setMinimumIntegerDigits(2);
        localPlayback = LocalPlayback.getInstance();
        sharedPreferences = getSharedPreferences("playback", MODE_PRIVATE);
        currentMode = sharedPreferences.getInt("playMode", 0);
        switch (currentMode) {
            case 0:
                playQueueTypeIcon.setImageResource(R.mipmap.ic_music_play_repeat_dark);
                break;
            case 1:
                playQueueTypeIcon.setImageResource(R.mipmap.ic_music_play_repeat_one_dark);
                break;
            case 2:
                playQueueTypeIcon.setImageResource(R.mipmap.ic_music_play_random_dark);
                break;
        }
    }

    private void registListener() {
        playButton.setOnClickListener(this);
        playPre.setOnClickListener(this);
        playNext.setOnClickListener(this);
        playQueueTypeIcon.setOnClickListener(this);
        playQueueIcon.setOnClickListener(this);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                String minute = df.format((progress / 1000 / 60) % 60);
                String second = df.format((progress / 1000) % 60);
                musicPlayTime.setText(minute + ":" + second);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopSeekbarUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                EventBus.getDefault().post(new MusicProgressEvent(seekBar.getProgress()));
                scheduleSeekbarUpdate();
            }
        });
    }

    private void scheduleSeekbarUpdate() {
        stopSeekbarUpdate();
        if (!mExecutorService.isShutdown()) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            mHandler.post(mUpdateProgressTask);
                        }
                    }, 0, 1000, TimeUnit.MILLISECONDS);
        }
    }

    private void updateSeekbar() {
        if (localPlayback == null) {
            return;
        }
        long currentPosition = localPlayback.getCurrentStreamPosition();
        seekBar.setProgress((int) currentPosition);
        String minute = df.format((currentPosition / 1000 / 60) % 60);
        String second = df.format((currentPosition / 1000) % 60);
        musicPlayTime.setText(minute + ":" + second);
    }

    private void stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture.cancel(false);
        }
    }

    private void refreshUI(MusicFile file) {
        /*Picasso.with(this)
                .load(ContentUris.withAppendedId(sArtworkUri, playList.get(playIndex).musicAlubmId))
                .error(R.mipmap.ic_black_rubber)
                .into(roundPaddingImageView);*/
        //localPlayback = LocalPlayback.getInstance();
        setBlurBackground(file);

        toolbar.setTitle(file.musicName);
        toolbar.setSubtitle(file.musicArtist);

        if (StringUtils.canParseInt(file.musicTime)) {
            // 音乐时长，单位毫秒
            int musicTime = Integer.parseInt(file.musicTime);
            seekBar.setMax(musicTime);

            scheduleSeekbarUpdate();

            String minute = df.format((musicTime / 1000 / 60) % 60);
            String second = df.format((musicTime / 1000) % 60);
            musicPlayAlltime.setText(minute + ":" + second);
        }
    }

    /**
     * 设置毛玻璃背景
     */
    @SuppressWarnings("deprecation")
    private void setBlurBackground(MusicFile file) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), ContentUris.withAppendedId(sArtworkUri, file.musicAlubmId));
            //final Bitmap blurBmp = BlurUtil.fastblur(this, bitmap, 25);//0-25，表示模糊值
            //final Bitmap blurBmp = FastBlurUtil.doBlur(bitmap, 8, false);//0-25，表示模糊值
            //final Drawable newBitmapDrawable = new BitmapDrawable(blurBmp); // 将Bitmap转换为Drawable
            roundImageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
            roundImageView.setVisibility(View.GONE);
        }
    }

    /**
     * 开始动画
     * */
    @SuppressLint("NewApi")
    public void startAnimation() {
        // 设置动画，从上次停止位置开始,这里是顺时针旋转360度
        rubberRotation = ObjectAnimator.ofFloat(rubberLayout, "Rotation",
                currentRotationValue - 360, currentRotationValue);
        // 设置持续时间
        rubberRotation.setDuration(40000);
        // 设置循环播放
        rubberRotation.setRepeatCount(ObjectAnimator.INFINITE);
        rubberRotation.setInterpolator(new LinearInterpolator());
        // 设置动画监听
        rubberRotation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // 监听动画执行的位置，以便下次开始时，从当前位置开始
                currentRotationValue = (Float) animation.getAnimatedValue();

            }
        });
        rubberRotation.start();
    }

    /**
     * 暂停动画
     * */
    @SuppressLint("NewApi")
    public void pauseAnimation() {
        if (rubberRotation != null) {
            rubberRotation.cancel();
        }
        rubberLayout.clearAnimation();// 清除此View身上的动画
    }

    private void stopAnimation() {
        if (rubberRotation != null) {
            rubberRotation.cancel();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ib_fragment_music_play:
                LocalPlayback playback = LocalPlayback.getInstance();
                switch (playback.getState()) {
                    case PlaybackState.STATE_PLAYING:
                        stopSeekbarUpdate();
                        break;
                }
                EventBus.getDefault().post(new ServiceControlEvent(playback.getState()));
                break;
            case R.id.ib_fragment_music_play_pre:
                EventBus.getDefault().post(new QueueSkipEvent(0));
                break;
            case R.id.ib_fragment_music_play_next:
                EventBus.getDefault().post(new QueueSkipEvent(1));
                break;
            // 切换播放模式，列表 列表循环 单曲循环
            case R.id.iv_activity_music_play_type:
                if (currentMode == 0) {
                    currentMode = 1;
                    playQueueTypeIcon.setImageResource(R.mipmap.ic_music_play_repeat_one_dark);
                    ViewUtils.toast(this, "单曲循环", true);
                } else if (currentMode == 1) {
                    currentMode = 2;
                    playQueueTypeIcon.setImageResource(R.mipmap.ic_music_play_random_dark);
                    ViewUtils.toast(this, "随机播放", true);
                } else if (currentMode == 2) {
                    currentMode = 0;
                    playQueueTypeIcon.setImageResource(R.mipmap.ic_music_play_repeat_dark);
                    ViewUtils.toast(this, "列表循环", true);
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("playMode", currentMode);
                editor.apply();
                EventBus.getDefault().post(new PlayModeEvent(currentMode));
                break;
            case R.id.iv_activity_music_play_more:
                break;
        }
    }

    /**
     * 当前播放变更时，对ui重新赋值
     * @param file
     */
    @Subscribe(threadMode = ThreadMode.POSTING)
    public void mediaUpdateEvent(MusicFile file) {
        refreshUI(file);
    }

    /**
     * eventbus订阅者-播放状态修改
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.POSTING)
    public void playBackEvent(PlaybackEvent event) {
        switch (event.state) {
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_STOPPED:
                playButton.setImageResource(R.mipmap.ic_music_play_dark);
                pauseAnimation();
                break;
            case PlaybackState.STATE_PLAYING:
                playButton.setImageResource(R.mipmap.ic_music_pause_dark);
                startAnimation();
                break;

        }
    }

}
