package com.ljyh.mei.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.text.toLowerCase
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import coil3.ImageLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ljyh.mei.MainActivity
import com.ljyh.mei.R
import com.ljyh.mei.constants.IsShuffleModeKey
import com.ljyh.mei.constants.DesktopLyricsEnabledKey
import com.ljyh.mei.constants.AutoEqEnabledKey
import com.ljyh.mei.constants.AutoEqIntensityKey
import com.ljyh.mei.constants.MusicQuality
import com.ljyh.mei.constants.MusicQualityKey
import com.ljyh.mei.constants.NoAudioSourceKey
import com.ljyh.mei.constants.RepeatModeKey
import com.ljyh.mei.constants.UserAgent
import com.ljyh.mei.data.repository.AutoEqRepository
import com.ljyh.mei.playback.equalizer.AudioEffectsController
import com.ljyh.mei.playback.equalizer.FreeverbProcessor
import com.ljyh.mei.playback.equalizer.ParametricEqualizerProcessor
import com.ljyh.mei.playback.equalizer.SoftLimiterProcessor
import com.ljyh.mei.playback.equalizer.auto.AutoEqPlanner
import com.ljyh.mei.playback.lyrics.DesktopLyricsController
import com.ljyh.mei.playback.transition.SmartTransitionController
import com.ljyh.mei.playback.transition.TransitionAudioProbe
import com.ljyh.mei.playback.transition.TransitionDeckPlayer
import com.ljyh.mei.playback.sleep.SleepDetectionController
import com.ljyh.mei.playback.source.MediaUriProvider
import com.ljyh.mei.playback.source.PlaybackSourceInfo
import com.ljyh.mei.playback.source.MusicLoadErrorHandlingPolicy
import com.ljyh.mei.data.model.SongEntity
import com.ljyh.mei.data.model.createPlaceholder
import com.ljyh.mei.data.model.toMediaItem
import com.ljyh.mei.playback.queue.PlaybackQueueSnapshot
import com.ljyh.mei.playback.queue.toSnapshotSong
import com.ljyh.mei.playback.queue.PlaybackQueueSnapshotCodec
import com.ljyh.mei.playback.queue.QueueEntrySnapshot
import com.ljyh.mei.playback.queue.SnapshotSong
import com.ljyh.mei.playback.queue.queueEntryId
import com.ljyh.mei.playback.queue.queueSource
import com.ljyh.mei.playback.queue.withQueueEntry
import com.ljyh.mei.constants.LastPlaybackQueueKey
import com.ljyh.mei.playback.source.SourceNotFoundException
import com.ljyh.mei.playback.usb.UsbFloatingKeepAlive
import com.ljyh.mei.playback.usb.UsbNativeBridge
import com.ljyh.mei.playback.UsbOutputState
import com.ljyh.mei.playback.usb.UsbPlaybackPreferences
import com.ljyh.mei.playback.SwitchingAudioSink
import com.ljyh.mei.playback.UsbExclusiveOutputProvider
import com.ljyh.mei.playback.usb.hasPcmOutput
import com.ljyh.mei.playback.isUsbAudioOutput
import com.ljyh.mei.playback.usbDeviceChange
import com.ljyh.mei.playback.usb.usbPlaybackPreferences
import com.ljyh.mei.playback.shouldReconfigureUsbPlayback
import com.ljyh.mei.utils.lyric.LyricManager
import com.ljyh.mei.constants.UsbExclusivePlaybackKey
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import android.hardware.usb.UsbManager
import android.os.Build
import android.media.AudioManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import coil3.imageLoader
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.data.model.api.GetSongUrlV1
import com.ljyh.mei.data.model.room.Song
import com.ljyh.mei.data.network.api.ApiService
import com.ljyh.mei.data.network.api.WeApiService
import com.ljyh.mei.di.repository.HistoryRepository
import com.ljyh.mei.di.repository.SongRepository
import com.ljyh.mei.extensions.currentMetadata
import com.ljyh.mei.extensions.mediaItems
import com.ljyh.mei.playback.CacheManager.getCacheDataSourceFactory
import com.ljyh.mei.playback.CacheManager.isContentFullyCached
import com.ljyh.mei.utils.image.CoilBitmapLoader
import com.ljyh.mei.utils.dataStore
import com.ljyh.mei.utils.get
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.Locale.getDefault
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


@UnstableApi
@AndroidEntryPoint
class MusicService : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {

    lateinit var player: TransitionDeckPlayer
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var transitionController: SmartTransitionController
    val context = this
    private lateinit var mediaSession: MediaLibrarySession
    private val desktopLyricsToggleCommand = SessionCommand(DESKTOP_LYRICS_TOGGLE_ACTION, Bundle.EMPTY)
    @Volatile private var desktopLyricsEnabled = false

    lateinit var sleepTimer: SleepTimer
    private val serviceJob = SupervisorJob()
    var scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var historyJob: Job? = null
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var baseMediaSourceFactory: DefaultMediaSourceFactory
    val currentMediaMetadata = MutableStateFlow<MediaMetadata?>(null)

    @Inject
    lateinit var mediaUriProvider: MediaUriProvider

    private var errorCount = 0 // 记录连续错误的次数，防止死循环

    private val binder = MusicBinder()

    lateinit var queueManager: PlaybackQueueManager
    var queueTitle: String? = null
    private var isAudioEffectSessionOpened = false

    // --- 智能睡眠检测（2.0 移植）---
    var sleepDetectionController: SleepDetectionController? = null

    // --- 桌面歌词（2.0 移植）---
    private lateinit var desktopLyricsController: DesktopLyricsController

    private var queueSaveJob: Job? = null
    @Volatile private var restoringQueue = true

    // --- USB 独占输出（2.0 移植）---
    @Volatile private var usbExclusiveEnabled = false
    @Volatile private var usbPreferences = UsbPlaybackPreferences()
    private lateinit var usbKeepAlive: UsbFloatingKeepAlive
    private lateinit var usbManager: UsbManager
    @Volatile private var usbDevices: Map<Int, String> = emptyMap()
    private var usbReconfigureJob: Job? = null
    private val usbPermissionRequested = mutableSetOf<Int>()
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == USB_PERMISSION_ACTION) {
                val granted = usbManager.deviceList.values.any { it.hasPcmOutput() && usbManager.hasPermission(it) }
                refreshUsbDevices(forceReconfigure = granted)
                if (!granted && usbExclusiveEnabled) UsbOutputState.store.fallback("未获得 USB 访问权限，使用普通输出")
            } else {
                refreshUsbDevices()
            }
        }
    }

    @Inject
    lateinit var lyricManager: LyricManager

    // --- DSP 音效链（2.0 移植）：参数均衡器 / 混响 / 输出限制器 ---
    private val parametricEqualizerProcessor = ParametricEqualizerProcessor()
    private val reverbProcessor = FreeverbProcessor()
    private val limiterProcessor = SoftLimiterProcessor()
    private val standbyEqualizerProcessor = ParametricEqualizerProcessor()
    private val standbyReverbProcessor = FreeverbProcessor()
    private val standbyLimiterProcessor = SoftLimiterProcessor()
    private val firstTransitionProbe = TransitionAudioProbe()
    private val secondTransitionProbe = TransitionAudioProbe()
    private lateinit var audioEffectsController: AudioEffectsController
    private lateinit var standbyAudioEffectsController: AudioEffectsController

    @Inject
    lateinit var autoEqRepository: AutoEqRepository

    @Inject
    lateinit var weApiService: WeApiService
    @Inject
    lateinit var apiService: ApiService


    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var songRepository: SongRepository
    override fun onCreate() {
        super.onCreate()
        baseMediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory())
            .setLoadErrorHandlingPolicy(MusicLoadErrorHandlingPolicy()) // 应用自定义错误策略

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.app_name_en
            )
        )

        val usbInitialSettings = runBlocking { context.dataStore.data.first() }
        usbExclusiveEnabled = usbInitialSettings[UsbExclusivePlaybackKey] ?: false
        usbPreferences = usbInitialSettings.usbPlaybackPreferences()
        usbManager = getSystemService(UsbManager::class.java)
        usbDevices = connectedUsbDevices()
        updateUsbEnvironment()

        val audioAttributes = AudioAttributes.Builder()
            //音频焦点变化 比如来电话了
            .setUsage(C.USAGE_MEDIA)
            //AUDIO_CONTENT_TYPE_MUSIC 表明内容类型是音乐。这有助于系统选择合适的音频处理方式和路由（比如通过扬声器还是耳机输出）。
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // 双解码器 deck：歌曲边界处两路解码重叠，实现无缝智能过渡（2.0 移植）。
        fun buildDeck(
            processor: ParametricEqualizerProcessor,
            reverb: FreeverbProcessor,
            limiter: SoftLimiterProcessor,
            probe: TransitionAudioProbe,
            focused: Boolean,
        ) =
            ExoPlayer.Builder(this)
                .setMediaSourceFactory(baseMediaSourceFactory)
                .setRenderersFactory(createRenderersFactory(processor, reverb, limiter, probe))
                .setHandleAudioBecomingNoisy(focused)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(audioAttributes, focused)
                .setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .build().apply {
                    setPreloadConfiguration(ExoPlayer.PreloadConfiguration(PLAYLIST_PRELOAD_DURATION_US))
                }

        val firstDeck = buildDeck(parametricEqualizerProcessor, reverbProcessor, limiterProcessor, firstTransitionProbe, true)
        val secondDeck = buildDeck(standbyEqualizerProcessor, standbyReverbProcessor, standbyLimiterProcessor, secondTransitionProbe, false)
        // Publish the player before wiring migrated 2.0 features. Several controllers keep a
        // reference to MusicService.player, so constructing them inside the assignment's apply
        // block would read the lateinit property before the assignment has completed.
        player = TransitionDeckPlayer(firstDeck, secondDeck, audioAttributes)
        audioPlayer = AudioPlayer(player)

        player.apply {
                //添加监听器
                addListener(this@MusicService)
                //睡眠定时
                sleepTimer = SleepTimer(scope, this)
                addListener(sleepTimer)
                addListener(this@MusicService)
                // DSP 音效链与 AutoEQ 观察者
                audioEffectsController = AudioEffectsController(
                    this@MusicService, scope,
                    parametricEqualizerProcessor, reverbProcessor, limiterProcessor,
                )
                standbyAudioEffectsController = AudioEffectsController(this@MusicService, scope, standbyEqualizerProcessor)
                observeAutoEq(scope)
                // 智能过渡（双 deck 无缝切换）
                transitionController = SmartTransitionController(
                    this@MusicService, player,
                    mapOf(
                        player.active to firstTransitionProbe,
                        player.standby to secondTransitionProbe,
                    ),
                    scope, audioPlayer::cancelFade,
                    { !sleepTimer.pauseWhenSongEnd },
                    { mediaId ->
                        val quality = context.dataStore[MusicQualityKey]?.lowercase(getDefault())
                            ?: MusicQuality.EXHIGH.text
                        mediaUriProvider.resolveMediaUri(mediaId, quality)
                    },
                )
                // 智能睡眠检测
                sleepDetectionController = SleepDetectionController(this@MusicService, scope, sleepTimer)
                // 桌面歌词悬浮窗
                desktopLyricsController = DesktopLyricsController(this@MusicService, player, lyricManager, scope)
                desktopLyricsController.start()

                // USB 独占输出：设备热插拔监听与设置观察
                val usbFilter = IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                    addAction(USB_PERMISSION_ACTION)
                }
                androidx.core.content.ContextCompat.registerReceiver(
                    this@MusicService, usbReceiver, usbFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                requestUsbPermissionIfNeeded()
                clearLegacyUsbMixerPreferences()
                usbKeepAlive = UsbFloatingKeepAlive(this@MusicService, player) { usbPreferences.floatingKeepAlive }
                usbKeepAlive.start(scope)
                scope.launch {
                    context.dataStore.data.map { (it[UsbExclusivePlaybackKey] ?: false) to it.usbPlaybackPreferences() }
                        .distinctUntilChanged()
                        .collect { (enabled, settings) ->
                            val reconfigure = enabled != usbExclusiveEnabled ||
                                (enabled && settings.formatPolicy() != usbPreferences.formatPolicy())
                            usbPreferences = settings
                            usbExclusiveEnabled = enabled
                            usbKeepAlive.update()
                            if (!reconfigure) return@collect
                            usbReconfigureJob?.cancel()
                            usbPermissionRequested.clear()
                            usbDevices = connectedUsbDevices()
                            // A changed format policy is an explicit fresh attempt, including after a format rejection.
                            UsbOutputState.store.resetNativeRetry()
                            updateUsbEnvironment()
                            requestUsbPermissionIfNeeded()
                            player.volume = 1f
                            if (enabled) {
                                player.active.skipSilenceEnabled = false
                                player.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
                            }
                            usbReconfigureJob = scope.launch { reprepareUsbTrack() }
                        }
                }
                // 添加监听，更新历史与 FM 模式
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        historyJob?.cancel()
                        if (mediaItem != null) {
                            historyJob = scope.launch {
                                delay(5000L.milliseconds)
                                try {
                                    recordHistory(mediaItem)
                                } catch (e: Exception) {
                                    Timber.tag("MusicService").e("add history record error $e")
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        scope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[IsShuffleModeKey] = shuffleModeEnabled
                            }
                        }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        scope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[RepeatModeKey] = repeatMode
                            }
                        }
                    }
                })
            }




        scope.launch {
            val shown = mutableMapOf<String, PlaybackSourceInfo>()
            mediaUriProvider.sourceInfo.collect { sources ->
                sources.forEach { (mediaId, info) ->
                    if (shown[mediaId] == info) return@forEach
                    shown[mediaId] = info
                    if (info.name == "网易云" || info.name == "本地") return@forEach
                    val detail = listOfNotNull(info.name, info.quality).joinToString(" · ")
                    Toast.makeText(context, "音源：$detail", Toast.LENGTH_SHORT).show()
                }
                shown.keys.retainAll(sources.keys)
            }
        }
        scope.launch {
            var quality = dataStore.data.first()[MusicQualityKey]
            dataStore.data.map { it[MusicQualityKey] }.distinctUntilChanged().collect { nextQuality ->
                if (nextQuality != quality) {
                    quality = nextQuality
                    reprepareCurrentTrack()
                }
            }
        }
        val singletonImageLoader = imageLoader
        val sessionPlayer = object : ForwardingSimpleBasePlayer(player) {
            override fun handleSeek(
                mediaItemIndex: Int,
                positionMs: Long,
                seekCommand: Int
            ): ListenableFuture<*> {
                // System media controls map "previous" to seekToPrevious(), whose default
                // behavior restarts the current item after the seek threshold.
                if (seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS) {
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem()
                    } else {
                        player.seekTo(0)
                    }
                    return Futures.immediateVoidFuture()
                }
                return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
            }
        }
        mediaSession = MediaLibrarySession.Builder(this, sessionPlayer, LibrarySessionCallback())
            .setMediaButtonPreferences(listOf(desktopLyricsButton(enabled = false)))
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setBitmapLoader(CoilBitmapLoader(this, singletonImageLoader))
            .build()

        scope.launch {
            dataStore.data
                .map { it[DesktopLyricsEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect { enabled ->
                    desktopLyricsEnabled = enabled
                    updateDesktopLyricsNotificationButton()
                }
        }


        restorePlayerState()
        scope.launch {
            while (isActive) {
                delay(10_000)
                if (!restoringQueue && player.mediaItemCount > 0) savePlaybackQueue()
            }
        }
        connectivityManager = getSystemService(ConnectivityManager::class.java)

        // 初始化队列管理器
        queueManager = PlaybackQueueManager( player, apiService, weApiService, scope)


    }

    private fun restorePlayerState() {
        scope.launch {
            try {
                val preferences = context.dataStore.data.firstOrNull() ?: return@launch
                val snapshot = PlaybackQueueSnapshotCodec.decode(preferences[LastPlaybackQueueKey])
                // A user action made while the service was starting takes precedence over the snapshot.
                val playerWasEmpty = player.mediaItemCount == 0
                if (playerWasEmpty && snapshot != null) {
                    queueManager.isFmMode = snapshot.isFmMode
                    val items = snapshot.ids.mapIndexed { index, id ->
                        val item = if (index == snapshot.currentIndex && snapshot.currentSong != null) {
                            snapshot.currentSong.toMediaMetadata().toMediaItem()
                        } else {
                            createPlaceholder(id)
                        }
                        val entry = snapshot.entries?.getOrNull(index)
                        item.withQueueEntry(entry?.source, entry?.entryId ?: java.util.UUID.randomUUID().toString())
                    }
                    player.setMediaItems(items, snapshot.currentIndex, snapshot.positionMs)
                    player.playWhenReady = false
                    player.prepare()
                    currentMediaMetadata.value = player.currentMetadata
                }

                if (playerWasEmpty) {
                    val savedShuffleMode = !queueManager.isFmMode && (preferences[IsShuffleModeKey] ?: false)
                    val savedRepeatMode = if (queueManager.isFmMode) Player.REPEAT_MODE_ALL
                        else preferences[RepeatModeKey] ?: Player.REPEAT_MODE_ALL
                    player.repeatMode = savedRepeatMode
                    player.shuffleModeEnabled = savedShuffleMode
                    queueManager.setShuffleModeEnabled(savedShuffleMode)
                    Timber.tag("MusicService").d(
                        "Restored queue: ${snapshot?.ids?.size ?: 0}, FM: ${queueManager.isFmMode}, shuffle: $savedShuffleMode, repeat: $savedRepeatMode"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("MusicService").e(e, "Failed to restore player state")
            } finally {
                restoringQueue = false
            }
        }
    }

    private fun scheduleQueueSave() {
        if (restoringQueue) return
        queueSaveJob?.cancel()
        queueSaveJob = scope.launch {
            delay(500)
            savePlaybackQueue()
        }
    }

    private suspend fun savePlaybackQueue() {
        val snapshot = if (player.mediaItemCount > 0 && player.currentMediaItemIndex >= 0) {
            PlaybackQueueSnapshot(
                ids = player.mediaItems.map(MediaItem::mediaId),
                entries = player.mediaItems.map { item ->
                    QueueEntrySnapshot(item.queueEntryId ?: java.util.UUID.randomUUID().toString(), item.queueSource)
                },
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                currentSong = snapshotCurrentSong(),
                isFmMode = queueManager.isFmMode,
            )
        } else null
        try {
            context.dataStore.edit { preferences ->
                if (snapshot == null) preferences.remove(LastPlaybackQueueKey)
                else preferences[LastPlaybackQueueKey] = PlaybackQueueSnapshotCodec.encode(snapshot)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("MusicService").e(e, "Failed to save playback queue")
        }
    }

    private fun snapshotCurrentSong(): SnapshotSong? {
        val item = player.currentMediaItem ?: return null
        val current = player.currentMetadata ?: (item.localConfiguration?.tag as? SongEntity)?.let {
            MediaMetadata(
                id = it.id,
                title = it.title,
                coverUrl = it.coverUrl,
                artists = listOf(MediaMetadata.Artist(it.artistId, it.artistName)),
                duration = it.duration,
                album = MediaMetadata.Album(it.albumId, it.albumName),
            )
        }
        return current?.takeIf { it.id.toString() == item.mediaId }?.toSnapshotSong()
    }


    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
                audioPlayer.startSmooth()
            } else {
                closeAudioEffectSession()
                audioPlayer.pauseSmooth()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
            scheduleQueueSave()
        }
    }


    private suspend fun recordHistory(mediaItem: MediaItem) {
        val metadata = mediaItem.mediaMetadata
        val artistList = metadata.extras?.getStringArrayList("artist_list") ?: listOf("未知歌手")
        val song = Song(
            id = mediaItem.mediaId,
            title = metadata.title?.toString() ?: "未知标题",
            artist = artistList,
            album = metadata.albumTitle?.toString() ?: "未知专辑",
            cover = metadata.artworkUri?.toString() ?: "",
            duration = metadata.durationMs ?: 0,
        )
        historyRepository.addToHistory(song)
    }


    private fun desktopLyricsButton(enabled: Boolean) =
        CommandButton.Builder(
            if (enabled) CommandButton.ICON_SUBTITLES else CommandButton.ICON_SUBTITLES_OFF,
        )
            .setDisplayName(if (enabled) "关闭桌面歌词" else "显示桌面歌词")
            .setSessionCommand(desktopLyricsToggleCommand)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()

    private fun updateDesktopLyricsNotificationButton() {
        if (::mediaSession.isInitialized) {
            mediaSession.setMediaButtonPreferences(listOf(desktopLyricsButton(desktopLyricsEnabled)))
        }
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val defaultSessionCommands = if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_AND_LIBRARY_COMMANDS
            }
            val defaultPlayerCommands = if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_PLAYER_COMMANDS
            }
            val availableSessionCommands = SessionCommands.Builder()
                .addSessionCommands(defaultSessionCommands.commands)
                .add(desktopLyricsToggleCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(availableSessionCommands)
                .setAvailablePlayerCommands(defaultPlayerCommands)
                .setMediaButtonPreferences(listOf(desktopLyricsButton(desktopLyricsEnabled)))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != DESKTOP_LYRICS_TOGGLE_ACTION) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            scope.launch {
                desktopLyricsEnabled = !(dataStore.data.first()[DesktopLyricsEnabledKey] ?: false)
                dataStore.edit { it[DesktopLyricsEnabledKey] = desktopLyricsEnabled }
                updateDesktopLyricsNotificationButton()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    fun playNext(items: List<MediaItem>) {
        scope.launch {
            queueManager.playNext(items)
        }
    }

    fun addToQueue(items: List<MediaItem>) {
        scope.launch {
            queueManager.addToQueue(items)
        }
    }

    fun setShuffleModeEnabled(isShuffle: Boolean) {
        queueManager.setShuffleModeEnabled(isShuffle)
    }

    private fun reprepareCurrentTrack() {
        if (player.mediaItemCount == 0) return
        val index = player.currentMediaItemIndex
        val position = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        player.stop()
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    override fun onDestroy() {
        CacheManager.release()
        queueSaveJob?.cancel()
        if (!restoringQueue) {
            runBlocking { savePlaybackQueue() }
        }
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        queueManager.release()
        transitionController.release()
        audioPlayer.release()

        player.release()
        queueManager.release()
        if (this@MusicService::audioEffectsController.isInitialized) {
            audioEffectsController.release()
            standbyAudioEffectsController.release()
        }
        sleepDetectionController?.close()
        if (this@MusicService::desktopLyricsController.isInitialized) {
            desktopLyricsController.close()
        }
        if (this@MusicService::usbKeepAlive.isInitialized) {
            unregisterReceiver(usbReceiver)
            usbKeepAlive.close()
        }
        usbReconfigureJob?.cancel()
        UsbOutputState.store.updateEnvironment(false, UsbNativeBridge.available, emptyMap())
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {

    }

    /**
     * Applies a per-song EQ profile when auto EQ is enabled. The profile is fetched off the
     * playback thread; a missing analysis simply restores the user's saved profile.
     */
    private fun connectedUsbDevices(): Map<Int, String> =
        usbManager.deviceList.values.filter { it.hasPcmOutput() }
            .associate { it.deviceId to (it.productName ?: "USB 解码器") }

    private fun updateUsbEnvironment() {
        UsbOutputState.store.updateEnvironment(usbExclusiveEnabled, UsbNativeBridge.available, usbDevices)
    }

    private fun requestUsbPermissionIfNeeded() {
        if (!usbExclusiveEnabled) return
        val devices = usbManager.deviceList.values.filter { it.hasPcmOutput() }
        val device = devices.singleOrNull() ?: return
        if (usbManager.hasPermission(device) || !usbPermissionRequested.add(device.deviceId)) return
        UsbOutputState.store.fallback("等待 USB 访问授权")
        val permissionIntent = Intent(USB_PERMISSION_ACTION).setPackage(packageName)
        val pendingIntent = PendingIntent.getBroadcast(this, device.deviceId, permissionIntent, PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun refreshUsbDevices(forceReconfigure: Boolean = false) {
        val devices = connectedUsbDevices()
        val change = usbDeviceChange(
            usbDevices.keys, devices.keys, usbExclusiveEnabled, UsbOutputState.status.value.deviceId,
        )
        val reconfiguring = usbReconfigureJob?.isActive == true
        usbPermissionRequested.retainAll(devices.keys)
        usbDevices = devices
        updateUsbEnvironment()
        requestUsbPermissionIfNeeded()
        if (!change.reconfigure && !forceReconfigure) return
        if (change.pause) player.pause()
        usbReconfigureJob?.cancel()
        usbReconfigureJob = scope.launch {
            if (reconfiguring || shouldReconfigureUsbPlayback(player.playbackState, player.playerError?.errorCode)) {
                reprepareUsbTrack()
            }
        }
    }

    private fun clearLegacyUsbMixerPreferences() {
        if (Build.VERSION.SDK_INT < 34) return
        val manager = getSystemService(AudioManager::class.java)
        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isUsbAudioOutput() }.forEach {
            runCatching { manager.clearPreferredMixerAttributes(attributes, it) }
        }
    }

    private suspend fun reprepareUsbTrack() {
        if (player.mediaItemCount == 0) return
        val index = player.currentMediaItemIndex
        val mediaId = player.currentMediaItem?.mediaId
        val position = player.currentPosition.coerceAtLeast(0L)
        player.stop()
        // Native close restores the kernel interfaces; let Android rediscover the system audio route.
        delay(if (usbExclusiveEnabled && UsbOutputState.store.nativeUsable()) 150L else 650L)
        if (player.currentMediaItem?.mediaId != mediaId || player.currentMediaItemIndex != index) return
        clearLegacyUsbMixerPreferences()
        player.seekTo(index, position)
        player.prepare()
        // stop()/prepare() retain playWhenReady, including a user pause during the release barrier.
    }

    private fun observeAutoEq(scope: CoroutineScope) {
        val enabled = context.dataStore.data.map { preferences ->
            (preferences[AutoEqEnabledKey] ?: false) to
                ((preferences[AutoEqIntensityKey] ?: 50) / 100f)
        }.distinctUntilChanged()
        scope.launch {
            combine(
                currentMediaMetadata.map { it?.id?.takeIf { id -> id > 0 } }.distinctUntilChanged(),
                enabled,
            ) { songId, config -> songId to config }
                .collectLatest { (songId, config) ->
                    val (autoEnabled, intensity) = config
                    val profile = if (autoEnabled && songId != null) {
                        autoEqRepository.features(songId)?.let { AutoEqPlanner.plan(it, intensity) }
                    } else null
                    audioEffectsController.setAutoProfile(profile)
                    standbyAudioEffectsController.setAutoProfile(profile)
                }
        }
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        val simpleCache = CacheManager.getSimpleCache(context)

        return ResolvingDataSource.Factory(getCacheDataSourceFactory(context)) { dataSpec ->
            runBlocking {
                val mediaId = dataSpec.key ?: error("No media key")
                val song = songRepository.getSong(mediaId).firstOrNull()
                    ?: songRepository.getSong("local_$mediaId").firstOrNull()
                val localFilePath = song?.path
                if (localFilePath != null) {
                    if (localFilePath.startsWith("content://")) {
                        Timber.tag("ResolvingDataSource").d(
                            "Using local content URI for mediaId: $mediaId, uri: $localFilePath"
                        )
                        return@runBlocking dataSpec.withUri(Uri.parse(localFilePath))
                    }
                    val file = File(localFilePath)
                    if (file.exists()) {
                        Timber.tag("ResolvingDataSource").d("Using local file for mediaId: $mediaId, filePath: ${file.path}")
                        return@runBlocking dataSpec.withUri(Uri.fromFile(file))
                    }
                }
                val quality = context.dataStore[MusicQualityKey]?.lowercase(getDefault()) ?: MusicQuality.EXHIGH.text
                val cacheKey = "$mediaId|${quality.lowercase()}|full-v1"
                if (isContentFullyCached(simpleCache, cacheKey)) {
                    Timber.tag("ResolvingDataSource").d("Fully cached on disk: $cacheKey")
                    return@runBlocking dataSpec.buildUpon().setKey(cacheKey).build()
                }

                val resolved = mediaUriProvider.resolveMedia(mediaId, quality)
                dataSpec.buildUpon().setUri(resolved.uri).setKey(resolved.cacheKey).build()
            }
        }
    }

    private fun createRenderersFactory(
        equalizer: ParametricEqualizerProcessor,
        reverb: FreeverbProcessor,
        limiter: SoftLimiterProcessor,
        probe: TransitionAudioProbe,
    ) =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink {
                val stereoDownmix = ChannelMixingAudioProcessor().apply {
                    for (channels in 1..8) putChannelMixingMatrix(stereoDownmixMatrix(channels))
                }
                val regular = DefaultAudioSink.Builder(this@MusicService)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            arrayOf(stereoDownmix, equalizer, reverb, limiter, probe),
                            SilenceSkippingAudioProcessor(2_000_000, 0.01f, 2_000_000, 0, 256),
                            SonicAudioProcessor()
                        )
                    ).build()
                val provider = UsbExclusiveOutputProvider(
                    this@MusicService,
                    onFailure = {
                        scope.launch {
                            if (usbExclusiveEnabled) {
                                player.pause()
                                usbReconfigureJob?.cancel()
                                usbReconfigureJob = scope.launch { reprepareUsbTrack() }
                            }
                        }
                    },
                    preferences = { usbPreferences },
                ) { usbExclusiveEnabled }
                val usb = DefaultAudioSink.Builder(this@MusicService)
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(false)
                    .setAudioProcessors(emptyArray())
                    .setAudioOutputProvider(provider)
                    .build()
                return SwitchingAudioSink(regular, usb, provider, { usbDevices.isNotEmpty() }) { usbExclusiveEnabled }
            }
        }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder
    override fun onPlayerError(error: PlaybackException) {
        Timber.tag("MusicService").e( "Player Error: ${error.errorCodeName}, ${error.message}")

        val isSourceError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.cause is SourceNotFoundException ||
                error.message?.contains("Unable to resolve url") == true

        if (isSourceError) {
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != null && connectivityManager.activeNetwork != null &&
                mediaUriProvider.recoverAfterError(mediaId)) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "当前音源失效，正在尝试其他音源", Toast.LENGTH_SHORT).show()
                }
                player.prepare()
                player.play()
                return
            }
            errorCount++
            Timber.tag("MusicService").e( "Play failure detected. Count: $errorCount")

            // 如果连续错误超过5次，停止播放，避免无限刷 API
            if (errorCount > 5) {
                Toast.makeText(context, "播放失败，已连续跳过多首歌曲", Toast.LENGTH_LONG).show()
                player.stop()
                errorCount = 0
                return
            }

            // 尝试跳到下一首
            if (player.hasNextMediaItem()) {
                // 不要在后台线程 Toast，发送事件或者只打印日志
                // 如果非要提示，用 Handler
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "资源无法加载，自动跳过", Toast.LENGTH_SHORT).show()
                }
                player.seekToNext()
                player.prepare()
                player.play()
            } else {
                // 列表播完了，或者没有下一首
                player.stop()
                Toast.makeText(context, "播放结束，部分歌曲无法加载", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 其他错误（如解码器错误），重置计数器并提示
            errorCount = 0
            Toast.makeText(context, "播放出错: ${error.errorCodeName}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // 如果成功切歌，重置错误计数器
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            // 注意：这里需要延迟一点或者确认新歌曲开始缓冲后再重置，
            // 简单的做法是：只要正常切歌了，我们暂时认为链条断了
            errorCount = 0

            // 触发 FM 模式检查 (见下文)
            checkFmModeLoadMore()
        }
    }

    private fun checkFmModeLoadMore() {
        if (!queueManager.isFmMode) return

        val current = player.currentMediaItemIndex
        val total = player.mediaItemCount
        val threshold = 3 // 剩余少于3首时加载

        if (total - current <= threshold) {
            scope.launch {
                queueManager.fetchAndAppendFmRecommendations()
            }
        }
    }

    fun isFmMode(): Boolean {
        return queueManager.isFmMode
    }

    companion object {
        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        private const val USB_PERMISSION_ACTION = "com.ljyh.mei.action.USB_PERMISSION"
        private const val DESKTOP_LYRICS_TOGGLE_ACTION = "com.ljyh.mei.action.TOGGLE_DESKTOP_LYRICS"
        private const val PLAYLIST_PRELOAD_DURATION_US = 5_000_000L
    }
}
