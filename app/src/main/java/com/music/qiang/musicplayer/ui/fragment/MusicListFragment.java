package com.music.qiang.musicplayer.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.music.qiang.musicplayer.R;
import com.music.qiang.musicplayer.model.MusicFile;
import com.music.qiang.musicplayer.ui.activity.MusicPlayActivity;
import com.music.qiang.musicplayer.ui.adapter.MusicListAdapter;

import java.util.ArrayList;

/**
 * 音乐列表fragment
 */
public class MusicListFragment extends Fragment {

    // ****************Views*******************
    private View rootView;
    private RecyclerView rv_music_list;

    // ****************对象********************
    private Context mContext;
    private RecyclerView.LayoutManager mLayoutManager;
    private MusicListAdapter musicListAdapter;
    private ArrayList<MusicFile> musicFiles;

    public MusicListFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_music_list, container, false);
        mContext = getActivity();
        initViews();
        initData();

        return rootView;
    }

    private void initViews() {
        rv_music_list = (RecyclerView) rootView.findViewById(R.id.rv_content_main_music_list);
    }

    private void initData() {
        initRecyclerView();
    }

    private void initRecyclerView() {
        // 1. 创建线性LayoutManager
        mLayoutManager = new LinearLayoutManager(mContext);
        rv_music_list.setLayoutManager(mLayoutManager);
        // 2. 如果可以确定每个item的高度是固定的，设置这个选项可以提高性能
        rv_music_list.setHasFixedSize(true);
        // 3. 创建并设置适配器
        getMusicList();
        musicListAdapter = new MusicListAdapter(mContext, musicFiles);
        rv_music_list.setAdapter(musicListAdapter);
        // 4. 添加分割线
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(rv_music_list.getContext(),
                LinearLayout.VERTICAL);
        rv_music_list.addItemDecoration(dividerItemDecoration);

        // 5. 设置点击事件
        musicListAdapter.setOnItemClickListener(new MusicListAdapter.MyItemClickListener() {
            @Override
            public void onItemClickListener(View view, int position) {
                Intent intent = new Intent(mContext, MusicPlayActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("from", "list");
                bundle.putInt("playIndex", position);
                bundle.putSerializable("playList", musicFiles);
                intent.putExtras(bundle);
                startActivity(intent);
            }

            @Override
            public void onItemLongClickListener(View view, int position) {

            }
        });
    }

    /**
     * 获得媒体文件
     */
    private void getMusicList() {
        musicFiles = new ArrayList<>();
        Cursor cursor = mContext.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        cursor.moveToFirst();
        for (int i = 0; i < cursor.getCount(); i++) {
            MusicFile file = new MusicFile();
            file.musicId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
            file.musicName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
            file.musicAlbum = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
            file.musicArtist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
            file.musicPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
            file.musicSize = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
            file.musicTime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
            file.musicAlubmId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
            musicFiles.add(file);
            cursor.moveToNext();
        }
        cursor.close();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

}
