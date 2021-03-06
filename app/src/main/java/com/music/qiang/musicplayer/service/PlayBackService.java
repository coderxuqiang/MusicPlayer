package com.music.qiang.musicplayer.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.music.qiang.musicplayer.events.MusicProgressEvent;
import com.music.qiang.musicplayer.events.PlayModeEvent;
import com.music.qiang.musicplayer.events.PlaybackEvent;
import com.music.qiang.musicplayer.events.QueueSkipEvent;
import com.music.qiang.musicplayer.events.ServiceControlEvent;
import com.music.qiang.musicplayer.model.MusicFile;
import com.music.qiang.musicplayer.playback.LocalPlayback;
import com.music.qiang.musicplayer.playback.PlayBackManager;
import com.music.qiang.musicplayer.playback.QueueManager;
import com.music.qiang.musicplayer.support.MediaNotificationManager;
import com.music.qiang.musicplayer.support.utils.StringUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

/**
 * 通过service方式管理后台播放音乐的服务
 */
public class PlayBackService extends Service {

    //*****************类和对象*******************
    private QueueManager queueManager;
    private PlayBackManager playBackManager;
    private MediaNotificationManager mediaNotificationManager;
    private ArrayList<MusicFile> playList;

    //*****************基本数据类型****************
    private int currentIndex = 0;
    /**
     * 播放模式，0-列表循环；1-单曲循环；2-随机播放
     */
    private int currentMode = 0;
    private String musicId;
    private String from;


    public PlayBackService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);

        mediaNotificationManager = new MediaNotificationManager(this);
        mediaNotificationManager.showNotification();
    }

    /**
     * @param intent  The Intent supplied to {@link Context#startService},
     *                as given.  This may be null if the service is being restarted after
     *                its process has gone away, and it had previously returned anything
     *                except {@link #START_STICKY_COMPATIBILITY}.
     * @param flags   Additional data about this start request.  Currently either
     *                0, {@link #START_FLAG_REDELIVERY}, or {@link #START_FLAG_RETRY}.
     * @param startId A unique integer representing this specific request to
     *                start.  Use with {@link #stopSelfResult(int)}.
     * @return The return value indicates what semantics the system should
     * use for the service's current started state.  It may be one of the
     * constants associated with the {@link #START_CONTINUATION_MASK} bits.
     * @see #stopSelfResult(int)
     * <p/>
     * 必须返回整型数。整型数是一个值，用于描述系统应该如何在服务终止的情况下继续运行服务
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundleObject = intent.getExtras();
        from = bundleObject.getString("from");
        currentMode = bundleObject.getInt("currentMode");
        // 如果是从列表页启动service时，获取播放列表和最开始播放的media
        if (!StringUtils.isNullOrEmpty(from) && "list".equals(from)) {
            playList = (ArrayList<MusicFile>) bundleObject.getSerializable("playList");
            currentIndex = bundleObject.getInt("playIndex");
            musicId = playList.get(currentIndex).musicId;
        } else {
            playList = null;
        }

        if (playList != null && playList.size() > 0) {
            queueManager = QueueManager.getInstance();
            queueManager.setmQueue(playList);
            // 设置队列播放模式 0-列表循环；1-单曲循环；2-随机播放
            if (0 == currentMode) {
                queueManager.setCurrentQueue(playList, String.valueOf(musicId));
            } else if (1 == currentMode) {
                queueManager.setSingleCycleQueue(playList, String.valueOf(musicId));
            } else if (2 == currentMode) {
                queueManager.setRandomQueue(playList, String.valueOf(musicId));
            }
        } else {
            queueManager = QueueManager.getInstance();
        }

        LocalPlayback playback = LocalPlayback.getInstance();
        // 创建播放类管理者
        playBackManager = new PlayBackManager(queueManager, playback);

        // 如果是从播放列表进入的，则重新播放
        if (!StringUtils.isNullOrEmpty(from) && "list".equals(from) ) {
            playBackManager.handlePlay();
        } else {
            if (queueManager.getCurrentMusic() != null) {
                // 更新ui
                EventBus.getDefault().post(queueManager.getCurrentMusic());
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        playBackManager.handleStop();
        mediaNotificationManager.stopNotification();
    }

    /**
     * 停止播放当前
     */
    public void stopPlay() {
        playBackManager.handleStop();
        mediaNotificationManager.stopNotification();
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void playControlEvent(ServiceControlEvent event) {
        mediaNotificationManager.refreshUI(null);
        switch (event.state) {
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_BUFFERING:
                playBackManager.handlePlay();
                break;
            case PlaybackState.STATE_PLAYING:
                playBackManager.handlePause();
                break;
            case PlaybackState.STATE_STOPPED:
                playBackManager.handlePlay();
                break;
        }
    }

    /**
     * eventbus订阅者-播放状态修改
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.POSTING)
    public void playBackEvent(PlaybackEvent event) {
        mediaNotificationManager.refreshUI(null);
    }

    /**
     * eventbus订阅者-播放队列切换
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.POSTING)
    public void queueSkipEvent(QueueSkipEvent event) {
        switch (event.cmd) {
            case 0:
                playBackManager.handlePre();
                break;
            case 1:
                playBackManager.handleNext(true);
                break;
        }
    }

    /**
     * 拖动进度条
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.POSTING)
    public void musicPorgressEvent(MusicProgressEvent event) {
        playBackManager.handleSeekto(event.progress);
    }

    /**
     * 当前播放变更时，对ui重新赋值
     * @param file
     */
    @Subscribe(threadMode = ThreadMode.POSTING)
    public void mediaUpdateEvent(MusicFile file) {
        mediaNotificationManager.refreshUI(file);
    }

    /**
     * eventbus订阅者-播放队列切换
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.POSTING)
    public void modeChangeEvent(PlayModeEvent event) {
        switch (event.mode) {
            // 列表循环
            case 0:
                queueManager.setCurrentQueue(null, null);
                break;
            // 单曲循环
            case 1:
                queueManager.setSingleCycleQueue(null, null);
                break;
            // 随机播放
            case 2:
                queueManager.setRandomQueue(null, null);
                break;
        }
    }
}
