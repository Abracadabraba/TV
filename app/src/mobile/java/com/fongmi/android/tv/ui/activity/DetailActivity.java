package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.cast.CastVideo;
import com.fongmi.android.tv.databinding.ActivityDetailBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.ExoUtil;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.receiver.PiPReceiver;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.custom.dialog.CastDialog;
import com.fongmi.android.tv.ui.custom.dialog.ControlDialog;
import com.fongmi.android.tv.ui.custom.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.custom.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.custom.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.Prefers;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Utils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class DetailActivity extends BaseActivity implements Clock.Callback, CustomKeyDownVod.Listener, CastDialog.Listener, PiPReceiver.Listener, TrackDialog.Listener, ControlDialog.Listener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, ParseAdapter.OnClickListener {

    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private ActivityDetailBinding mBinding;
    private EpisodeAdapter mEpisodeAdapter;
    private SearchAdapter mSearchAdapter;
    private ControlDialog mControlDialog;
    private ParseAdapter mParseAdapter;
    private CustomKeyDownVod mKeyDown;
    private ExecutorService mExecutor;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private PiPReceiver mReceiver;
    private List<Dialog> mDialogs;
    private List<String> mBroken;
    private History mHistory;
    private Players mPlayers;
    private boolean fullscreen;
    private boolean initTrack;
    private boolean initAuto;
    private boolean autoMode;
    private boolean useParse;
    private boolean rotate;
    private boolean stop;
    private boolean lock;
    private int toggleCount;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private String url;
    private PiP mPiP;

    public static void push(FragmentActivity activity, Uri uri) {
        if ("smb".equals(uri.getScheme()) || "http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
            push(activity, uri.toString());
        } else {
            file(activity, FileChooser.getPathFromUri(activity, uri));
        }
    }

    public static void file(FragmentActivity activity, String path) {
        String name = new File(path).getName();
        if (Utils.hasPermission(activity)) start(activity, "push_agent", "file://" + path, name);
        else PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> start(activity, "push_agent", "file://" + path, name));
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName());
    }

    public static void push(Activity activity, String url) {
        start(activity, "push_agent", url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        Intent intent = new Intent(activity, DetailActivity.class);
        intent.putExtra("name", name);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    private String getName() {
        return getIntent().getStringExtra("name");
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private String getId() {
        return getIntent().getStringExtra("id");
    }

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + ApiConfig.getCid();
    }

    private Site getSite() {
        return ApiConfig.get().getSite(getKey());
    }

    private Vod.Flag getFlag() {
        return mFlagAdapter.getActivated();
    }

    private Vod.Flag.Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private int getPlayer() {
        return mHistory != null && mHistory.getPlayer() != -1 ? mHistory.getPlayer() : getSite().getPlayerType() != -1 ? getSite().getPlayerType() : Prefers.getPlayer();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : Prefers.getScale();
    }

    private PlayerView getExo() {
        return Prefers.getRender() == 0 ? mBinding.surface : mBinding.texture;
    }

    private IjkVideoView getIjk() {
        return mBinding.ijk;
    }

    private boolean isReplay() {
        return Prefers.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getCallingActivity() != null && getCallingActivity().getShortClassName().contains(CollectActivity.class.getSimpleName());
    }

    @Override
    protected boolean transparent() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
        mBinding.swipeLayout.setRefreshing(true);
        getIntent().putExtras(intent);
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mKeyDown = CustomKeyDownVod.create(this, mBinding.video);
        mFrameParams = mBinding.video.getLayoutParams();
        mBinding.progressLayout.showProgress();
        mBinding.swipeLayout.setEnabled(false);
        mReceiver = new PiPReceiver(this);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mPlayers = new Players().init();
        mDialogs = new ArrayList<>();
        mBroken = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mPiP = new PiP();
        setRecyclerView();
        setVideoView();
        setViewModel();
        showProgress();
        checkId();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.actor.setOnClickListener(view -> onActor());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.back.setOnClickListener(view -> onFull());
        mBinding.control.full.setOnClickListener(view -> onFull());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.lock.setOnClickListener(view -> onLock());
        mBinding.control.share.setOnClickListener(view -> onShare());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.loop.setOnClickListener(view -> onLoop());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
        mBinding.control.seek.setListener(mPlayers);
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setHasFixedSize(true);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.LIST));
        mBinding.search.setAdapter(mSearchAdapter = new SearchAdapter(this::setSearch));
        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this, ViewType.DARK));
    }

    private void setPlayerView() {
        getIjk().setPlayer(mPlayers.getPlayer());
        mBinding.control.action.player.setText(mPlayers.getPlayerText());
        getExo().setVisibility(mPlayers.isExo() ? View.VISIBLE : View.GONE);
        getIjk().setVisibility(mPlayers.isIjk() ? View.VISIBLE : View.GONE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Prefers.getReset()]);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(getActivity(), view));
    }

    private void setDecodeView() {
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
    }

    private void setVideoView() {
        mPlayers.set(getExo(), getIjk());
        if (ResUtil.isLand(this)) enterFullscreen();
        getExo().getSubtitleView().setStyle(ExoUtil.getCaptionStyle());
        getIjk().getSubtitleView().setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observeForever(mObserveDetail);
        mViewModel.player.observeForever(mObservePlayer);
        mViewModel.search.observeForever(mObserveSearch);
        mViewModel.episode.observe(this, episode -> {
            onItemClick(episode);
            hideSheet();
        });
    }

    private void checkId() {
        if (TextUtils.isEmpty(getId()) || getId().startsWith("msearch:")) setEmpty();
        else getDetail();
    }

    private void getDetail() {
        mViewModel.detailContent(getKey(), getId());
    }

    private void getDetail(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("id", item.getVodId());
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        Clock.get().setCallback(null);
        mPlayers.stop();
        getDetail();
    }

    private void setDetail(Result result) {
        mBinding.swipeLayout.setRefreshing(false);
        if (result.getList().isEmpty()) setEmpty();
        else setDetail(result.getList().get(0));
    }

    private void setEmpty() {
        if (isFromCollect()) {
            finish();
        } else if (getName().isEmpty()) {
            mBinding.swipeLayout.setEnabled(true);
            mBinding.progressLayout.showEmpty();
        } else {
            checkSearch(false);
        }
    }

    private void setDetail(Vod item) {
        mBinding.progressLayout.showContent();
        mBinding.video.setTag(item.getVodPic());
        mBinding.name.setText(item.getVodName());
        setText(mBinding.remark, 0, item.getVodRemarks());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.actor, R.string.detail_actor, Html.fromHtml(item.getVodActor()).toString());
        setText(mBinding.content, 0, Html.fromHtml(item.getVodContent()).toString());
        setText(mBinding.director, R.string.detail_director, Html.fromHtml(item.getVodDirector()).toString());
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        mFlagAdapter.addAll(item.getVodFlags());
        setOther(mBinding.other, item);
        checkFlag(item);
        checkKeepImg();
    }

    private void setText(TextView view, int resId, String text) {
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setText(resId > 0 ? getString(resId, text) : text);
        view.setTag(text);
    }

    private void setOther(TextView view, Vod item) {
        StringBuilder sb = new StringBuilder();
        if (!item.getVodYear().isEmpty()) sb.append(getString(R.string.detail_year, item.getVodYear())).append("  ");
        if (!item.getVodArea().isEmpty()) sb.append(getString(R.string.detail_area, item.getVodArea())).append("  ");
        if (!item.getTypeName().isEmpty()) sb.append(getString(R.string.detail_type, item.getTypeName())).append("  ");
        view.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        view.setText(Utils.substring(sb.toString(), 2));
    }

    private void getPlayer(Vod.Flag flag, Vod.Flag.Episode episode, boolean replay) {
        mBinding.control.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        updateHistory(episode, replay);
        showProgress();
        setUrl(null);
    }

    private void setPlayer(Result result) {
        setUseParse(ApiConfig.hasParse() && ((result.getPlayUrl().isEmpty() && ApiConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1));
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setParseVisible(isUseParse());
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        int timeout = getSite().isChangeable() ? Constant.TIMEOUT_PLAY : -1;
        mPlayers.start(result, isUseParse(), timeout);
        mBinding.swipeLayout.setRefreshing(false);
    }

    @Override
    public void onItemClick(Vod.Flag item, boolean force) {
        if (item.isActivated()) return;
        mFlagAdapter.setActivated(item);
        mBinding.flag.scrollToPosition(mFlagAdapter.getPosition());
        setEpisodeAdapter(item.getEpisodes());
        seamless(item, force);
    }

    @Override
    public void onItemClick(Vod.Flag.Episode item) {
        if (item.isActivated()) return;
        mFlagAdapter.toggle(item);
        notifyItemChanged(mEpisodeAdapter);
        mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
        onRefresh();
    }

    @Override
    public void onItemClick(Parse item) {
        ApiConfig.get().setParse(item);
        notifyItemChanged(mParseAdapter);
        onRefresh();
    }

    private void setEpisodeAdapter(List<Vod.Flag.Episode> items) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.nextRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prevRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 5 ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(items.size() < 5 ? View.GONE : View.VISIBLE);
        mEpisodeAdapter.addAll(items);
    }

    private void seamless(Vod.Flag flag, boolean force) {
        if (!force && !getSite().isChangeable()) return;
        Vod.Flag.Episode episode = flag.find(mHistory.getVodRemarks());
        if (episode == null || episode.isActivated()) return;
        mHistory.setVodRemarks(episode.getName());
        onItemClick(episode);
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        initSearch(name, false);
    }

    private void onMore() {
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(mEpisodeAdapter.getItems()).show(this);
    }

    private void onActor() {
        mBinding.actor.setMaxLines(mBinding.actor.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onContent() {
        mBinding.content.setMaxLines(mBinding.content.getMaxLines() == 2 ? Integer.MAX_VALUE : 2);
    }

    private void onReverse() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    private boolean onChange() {
        if (getSite().isChangeable()) checkSearch(true);
        else checkFlag();
        return true;
    }

    private void onCast() {
        CastDialog.create().history(mHistory).video(CastVideo.get(getName(), getUrl())).fm(true).show(this);
    }

    private void onFull() {
        setR1Callback();
        toggleFullscreen();
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        RefreshEvent.keep();
        checkKeepImg();
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onShare() {
        ShareCompat.IntentBuilder builder = new ShareCompat.IntentBuilder(this).setType("text/plain").setText(getUrl());
        builder.getIntent().putExtra("title", mBinding.control.title.getText());
        builder.getIntent().putExtra("name", mBinding.control.title.getText());
        builder.startChooser();
    }

    private void checkPlay() {
        setR1Callback();
        checkPlayImg(!mPlayers.isPlaying());
        if (mPlayers.isPlaying()) mPlayers.pause();
        else mPlayers.play();
    }

    private void checkNext() {
        setR1Callback();
        if (mHistory.isRevPlay()) onPrev();
        else onNext();
    }

    private void checkPrev() {
        setR1Callback();
        if (mHistory.isRevPlay()) onNext();
        else onPrev();
    }

    private void onNext() {
        Vod.Flag.Episode item = mEpisodeAdapter.getNext();
        if (item.isActivated()) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
        else onItemClick(item);
    }

    private void onPrev() {
        Vod.Flag.Episode item = mEpisodeAdapter.getPrev();
        if (item.isActivated()) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
        else onItemClick(item);
    }

    private void onRotate() {
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void onSetting() {
        mControlDialog = ControlDialog.create().detail(mBinding).history(mHistory).players(mPlayers).parse(isUseParse()).show(this);
    }

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onLoop() {
        mBinding.control.action.loop.setActivated(!mBinding.control.action.loop.isActivated());
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        mHistory.setScale(index = index == array.length - 1 ? 0 : ++index);
        setScale(index);
        setR1Callback();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(mPlayers.addSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(mPlayers.toggleSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        setR1Callback();
        return true;
    }

    private void onRefresh() {
        onReset(false);
    }

    private void onReset() {
        onReset(isReplay());
    }

    private void onReset(boolean replay) {
        Clock.get().setCallback(null);
        if (mFlagAdapter.getItemCount() == 0) return;
        if (mEpisodeAdapter.getItemCount() == 0) return;
        getPlayer(getFlag(), getEpisode(), replay);
    }

    private boolean onResetToggle() {
        Prefers.putReset(Math.abs(Prefers.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Prefers.getReset()]);
        return true;
    }

    private void onPlayer() {
        mPlayers.togglePlayer();
        setPlayerView();
        setR1Callback();
        onRefresh();
    }

    private void onDecode() {
        mPlayers.toggleDecode();
        mPlayers.set(getExo(), getIjk());
        setDecodeView();
        setR1Callback();
        onRefresh();
    }

    private void onEnding() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || current < duration / 2) return;
        mHistory.setEnding(duration - current);
        mBinding.control.action.ending.setText(mPlayers.stringToTime(mHistory.getEnding()));
        setR1Callback();
    }

    private boolean onEndingReset() {
        mHistory.setEnding(0);
        mBinding.control.action.ending.setText(R.string.play_ed);
        setR1Callback();
        return true;
    }

    private void onOpening() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || current > duration / 2) return;
        mHistory.setOpening(current);
        mBinding.control.action.opening.setText(mPlayers.stringToTime(mHistory.getOpening()));
        setR1Callback();
    }

    private boolean onOpeningReset() {
        mHistory.setOpening(0);
        mBinding.control.action.opening.setText(R.string.play_op);
        setR1Callback();
        return true;
    }

    private void onEpisodes() {
        mDialogs.add(EpisodeListDialog.create(this).episodes(mEpisodeAdapter.getItems()).show());
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) getDetail();
        else onRefresh();
    }

    private void toggleFullscreen() {
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
    }

    private void enterFullscreen() {
        if (isFullscreen()) return;
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        setRequestedOrientation(mPlayers.isPortrait() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getIjk().getSubtitleView().setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mBinding.control.full.setVisibility(View.GONE);
        setRotate(mPlayers.isPortrait());
        App.post(mR3, 2000);
        setFullscreen(true);
        hideControl();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        getIjk().getSubtitleView().setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
        mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
        mBinding.control.full.setVisibility(View.VISIBLE);
        mBinding.video.setLayoutParams(mFrameParams);
        App.post(mR3, 2000);
        setFullscreen(false);
        setRotate(false);
        hideControl();
    }

    private int getLockOrient() {
        if (isLock()) {
            return ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        } else if (isRotate()) {
            return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        } else if (Utils.isAutoRotate()) {
            return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        } else {
            return ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        }
    }

    private void showProgress() {
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.widget.progress.setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl() {
        mBinding.control.setting.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.keep.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.back.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.share.setVisibility(getUrl() == null ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(getUrl() == null ? View.GONE : View.VISIBLE);
        mBinding.control.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        checkPlayImg(mPlayers.isPlaying());
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideSheet() {
        for (Dialog dialog : mDialogs) dialog.dismiss();
        for (Fragment fragment : getSupportFragmentManager().getFragments()) if (fragment instanceof BottomSheetDialogFragment) ((BottomSheetDialogFragment) fragment).dismiss();
        mDialogs.clear();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        App.post(mR2, Constant.INTERVAL_TRAFFIC);
    }

    private void setOrient() {
        if (Utils.isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void checkFlag(Vod item) {
        mBinding.flag.setVisibility(item.getVodFlags().isEmpty() ? View.GONE : View.VISIBLE);
        if (isVisible(mBinding.flag)) checkHistory(item);
        else ErrorEvent.episode();
    }

    private void checkHistory(Vod item) {
        mHistory = History.find(getHistoryKey());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        onItemClick(mHistory.getFlag(), true);
        if (mHistory.isRevSort()) reverseEpisode(true);
        mBinding.control.action.opening.setText(mHistory.getOpening() == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
        mBinding.control.action.ending.setText(mHistory.getEnding() == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        mPlayers.setPlayer(getPlayer());
        setScale(getScale());
        setPlayerView();
        setDecodeView();
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(ApiConfig.getCid());
        history.setVodPic(item.getVodPic());
        history.setVodName(item.getVodName());
        history.findEpisode(item.getVodFlags());
        return history;
    }

    private void updateHistory(Vod.Flag.Episode item, boolean replay) {
        replay = replay || !item.equals(mHistory.getEpisode());
        long position = replay ? 0 : mHistory.getPosition();
        mHistory.setPosition(position);
        mHistory.setEpisodeUrl(item.getUrl());
        mHistory.setVodRemarks(item.getName());
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setCreateTime(System.currentTimeMillis());
    }

    private void checkKeepImg() {
        mBinding.control.keep.setImageResource(Keep.find(getHistoryKey()) == null ? R.drawable.ic_control_keep_off : R.drawable.ic_control_keep_on);
    }

    private void checkPlayImg(boolean playing) {
        mBinding.control.play.setImageResource(playing ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mPiP.update(this, playing);
    }

    private void checkLockImg() {
        mBinding.control.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(ApiConfig.getCid());
        keep.setSiteName(getSite().getName());
        keep.setVodPic(mBinding.video.getTag().toString());
        keep.setVodName(mBinding.name.getText().toString());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    @Override
    public void onTrackClick(Track item) {
        item.setKey(getHistoryKey());
        item.save();
    }

    @Override
    public void onTimeChanged() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current >= 0 && duration > 0) App.execute(() -> mHistory.update(current, duration));
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + current >= duration) {
            Clock.get().setCallback(null);
            checkNext();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerEvent(PlayerEvent event) {
        switch (event.getState()) {
            case 0:
                setPosition();
                setInitTrack(true);
                setTrackVisible(false);
                setUrl(event.getUrl());
                Clock.get().setCallback(this);
                break;
            case Player.STATE_IDLE:
                break;
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                stopSearch();
                checkRotate();
                resetToggle();
                hideProgress();
                mPlayers.reset();
                setDefaultTrack();
                setTrackVisible(true);
                checkPlayImg(mPlayers.isPlaying());
                mHistory.setPlayer(mPlayers.getPlayer());
                mBinding.control.size.setText(mPlayers.getSizeText());
                if (isVisible(mBinding.control.getRoot())) showControl();
                break;
            case Player.STATE_ENDED:
                checkEnded();
                break;
        }
    }

    private void setPosition() {
        mPlayers.seekTo(Math.max(mHistory.getOpening(), mHistory.getPosition()), false);
    }

    private void checkRotate() {
        if (isFullscreen() && !isRotate() && mPlayers.isPortrait()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            setRotate(true);
        }
    }

    private void checkEnded() {
        if (mBinding.control.action.loop.isActivated()) {
            onReset(true);
        } else {
            checkNext();
        }
    }

    private void setTrackVisible(boolean visible) {
        mBinding.control.action.text.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_TEXT) ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setTrackVisible();
    }

    private void setDefaultTrack() {
        if (isInitTrack()) {
            setInitTrack(false);
            mPlayers.setTrack(Track.find(getHistoryKey()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        if (mPlayers.addRetry() > event.getRetry()) checkError(event);
        else onRefresh();
    }

    private void checkError(ErrorEvent event) {
        if (getSite().getPlayerType() == -1 && event.isFormat() && getToggleCount() < 2 && mPlayers.getPlayer() != Players.SYS) {
            toggleCount++;
            nextPlayer();
        } else {
            resetToggle();
            onError(event);
        }
    }

    private void nextPlayer() {
        mPlayers.nextPlayer();
        setPlayerView();
        onRefresh();
    }

    private void onError(ErrorEvent event) {
        mBinding.swipeLayout.setEnabled(true);
        Clock.get().setCallback(null);
        showError(event.getMsg());
        mPlayers.stop();
        startFlow();
    }

    private void startFlow() {
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.getItemCount() == 0) return;
        ApiConfig.get().setParse(mParseAdapter.first());
        notifyItemChanged(mParseAdapter);
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (mSearchAdapter.getItemCount() == 0) initSearch(getName(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        stopSearch();
        setAutoMode(auto);
        setInitAuto(auto);
        startSearch(keyword);
    }

    private void startSearch(String keyword) {
        mSearchAdapter.clear();
        mExecutor = Executors.newFixedThreadPool(Constant.THREAD_POOL * 2);
        for (Site site : ApiConfig.get().getSites()) {
            if (site.getKey().equals(getKey())) continue;
            if (isAutoMode() && !site.isChangeable()) continue;
            if (site.isSearchable()) mExecutor.execute(() -> search(site, keyword));
        }
    }

    private void stopSearch() {
        if (mExecutor != null) mExecutor.shutdownNow();
    }

    private void search(Site site, String keyword) {
        try {
            mViewModel.searchContent(site, keyword);
        } catch (Throwable ignored) {
        }
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        Iterator<Vod> iterator = items.iterator();
        while (iterator.hasNext()) if (mismatch(iterator.next())) iterator.remove();
        mBinding.search.setVisibility(View.VISIBLE);
        mSearchAdapter.addAll(items);
        if (isInitAuto()) nextSite();
    }

    private void setSearch(Vod item) {
        setAutoMode(false);
        getDetail(item);
    }

    private boolean mismatch(Vod item) {
        String keyword = getName();
        if (mBroken.contains(item.getVodId())) return true;
        if (isAutoMode()) return !item.getVodName().equals(keyword);
        else return !item.getVodName().contains(keyword);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Vod.Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        onItemClick(flag, true);
    }

    private void nextSite() {
        if (mSearchAdapter.getItemCount() == 0) return;
        Vod item = mSearchAdapter.get(0);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mSearchAdapter.remove(0);
        mBroken.add(getId());
        setInitAuto(false);
        getDetail(item);
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Utils.toggleFullscreen(this, this.fullscreen = fullscreen);
    }

    private boolean isInitTrack() {
        return initTrack;
    }

    private void setInitTrack(boolean initTrack) {
        this.initTrack = initTrack;
    }

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    private void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public boolean isLock() {
        return lock;
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getToggleCount() {
        return toggleCount;
    }

    public void resetToggle() {
        this.toggleCount = 0;
    }

    private void notifyItemChanged(RecyclerView.Adapter<?> adapter) {
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    @Override
    public void onCastTo() {
        checkPlayImg(false);
        mPlayers.pause();
    }

    @Override
    public void onScale(int tag) {
        mHistory.setScale(tag);
        setScale(tag);
    }

    @Override
    public void onParse(Parse item) {
        onItemClick(item);
    }

    @Override
    public void onSpeedUp() {
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onBrightEnd() {
        mBinding.widget.bright.setVisibility(View.GONE);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onVolumeEnd() {
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    @Override
    public void onSeek(int time) {
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        mBinding.widget.time.setText(mPlayers.getPositionTime(time));
        hideProgress();
    }

    @Override
    public void onSeekEnd(int time) {
        mBinding.widget.seek.setVisibility(View.GONE);
        mPlayers.seekTo(time);
        mPlayers.play();
        showProgress();
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        if (!isFullscreen()) {
            App.post(this::enterFullscreen, 250);
        } else if (mPlayers.isPlaying()) {
            mPlayers.pause();
            showControl();
        } else {
            mPlayers.play();
            hideControl();
        }
    }

    @Override
    public void onControlPlay() {
        mBinding.control.play.performClick();
    }

    @Override
    public void onControlNext() {
        mBinding.control.next.performClick();
    }

    @Override
    public void onControlPrev() {
        mBinding.control.prev.performClick();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        mPiP.enter(this, getScale() == 2);
        if (isLock()) App.post(this::onLock, 500);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            mReceiver.register(this);
            enterFullscreen();
            hideControl();
            hideSheet();
        } else {
            exitFullscreen();
            mReceiver.unregister(this);
            if (isStop()) finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (ResUtil.isPort(this) && !isRotate()) exitFullscreen();
        else if (ResUtil.isLand(this)) enterFullscreen();
        else if (isFullscreen()) Utils.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus) Utils.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setStop(false);
        if (PiP.isIn(this)) {
            PlaybackService.stop();
        } else {
            mPlayers.play();
            Clock.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        setStop(true);
        if (PiP.isIn(this)) {
            PlaybackService.start();
        } else {
            mPlayers.pause();
            Clock.stop();
        }
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock()) {
            stopSearch();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPlayers.release();
        Clock.get().release();
        RefreshEvent.history();
        PlaybackService.stop();
        App.removeCallbacks(mR1, mR2, mR3);
        mViewModel.result.removeObserver(mObserveDetail);
        mViewModel.player.removeObserver(mObservePlayer);
        mViewModel.search.removeObserver(mObserveSearch);
    }
}
