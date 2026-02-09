package cn.sss.michael.exo.callback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.util.ExoLog;

import java.util.List;

/**
 * @author Michael by 61642
 * @date 2025/12/30 14:40
 * @Description ExoPlayer 监听器抽象类
 * 基于 Media3 Player.Listener 接口，实现了全量回调覆盖及详细参数分类。
 */
public abstract class IExoPlayerListener implements Player.Listener {
 
    //日志前缀
    private final String prefix = "======PlayerCallbackLog>>>>>>";

    // ======================== 核心播放状态与意图回调 ======================== //

    /**
     * 播放状态改变。
     *
     * @param state 1. STATE_IDLE: 初始状态、停止或播放失败。
     *              2. STATE_BUFFERING: 缓冲中，无法立即播放。
     *              3. STATE_READY: 准备就绪，可以播放或正在播放。
     *              4. STATE_ENDED: 播放列表播放完毕。
     */
    @Override
    public void onPlaybackStateChanged(int state) {
        onExoPlaybackStateChanged(state);
//        if (ExoConfig.LOG_ENABLE) {
//            String stateDesc;
//            switch (state) {
//                case Player.STATE_IDLE:
//                    stateDesc = "STATE_IDLE(初始状态/停止/播放失败)";
//                    break;
//                case Player.STATE_BUFFERING:
//                    stateDesc = "STATE_BUFFERING(缓冲中，无法立即播放)";
//                    break;
//                case Player.STATE_READY:
//                    stateDesc = "STATE_READY(准备就绪/正在播放)";
//                    break;
//                case Player.STATE_ENDED:
//                    stateDesc = "STATE_ENDED(播放列表播放完毕)";
//                    break;
//                default:
//                    stateDesc = "未知状态(" + state + ")";
//            }
//             ExoLog.log(prefix + "播放状态改变：state=" + state + "，状态描述=" + stateDesc);
//        }
    }

    /**
     * 播放意图或原因改变。
     *
     * @param playWhenReady true 请求播放；false 请求暂停。
     * @param reason        1. REASON_USER_REQUEST: 用户手动操作。
     *                      2. REASON_AUDIO_FOCUS_LOSS: 音频焦点丢失。
     *                      3. REASON_AUDIO_BECOMING_NOISY: 耳机拔出等。
     *                      4. REASON_REMOTE: 远程端更改。
     *                      5. REASON_END_OF_PLAYLIST: 媒体项目结束。
     *                      6. PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG: 准备就绪时播放更改原因抑制时间过长。
     */
    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        Player.Listener.super.onPlayWhenReadyChanged(playWhenReady, reason);
        if (ExoConfig.LOG_ENABLE) {
            String playWhenReadyDesc = playWhenReady ? "true(请求播放)" : "false(请求暂停)";
            String reasonDesc;
            switch (reason) {
                case Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST:
                    reasonDesc = "REASON_USER_REQUEST(用户手动操作)";
                    break;
                case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS:
                    reasonDesc = "REASON_AUDIO_FOCUS_LOSS(音频焦点丢失)";
                    break;
                case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY:
                    reasonDesc = "REASON_AUDIO_BECOMING_NOISY(音频设备异常，如耳机拔出)";
                    break;
                case Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE:
                    reasonDesc = "REASON_REMOTE(远程端更改)";
                    break;
                case Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM:
                    reasonDesc = "PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM(媒体项目结束)";
                    break;
                case Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG:
                    reasonDesc = "PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG(准备就绪时播放更改原因抑制时间过长)";
                    break;
                default:
                    reasonDesc = "未知原因(" + reason + ")";
            }
            ExoLog.log(prefix + "播放意图或原因改变：playWhenReady=" + playWhenReadyDesc + "，reason=" + reason + "，原因描述=" + reasonDesc);
        }
    }

    /**
     * “正在播放”状态改变（复合判定结果）。
     *
     * @param isPlaying true 表示播放器当前正在消耗时间线（即用户真实看到/听到内容在进行）。
     *                  注：当播放器处于 Ready 且未被暂停或缓冲时，此值为 true。
     */
    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        onExoIsPlayingChanged(isPlaying);
        if (ExoConfig.LOG_ENABLE) {
            String isPlayingDesc = isPlaying ? "true(正在真实播放)" : "false(未播放/缓冲中/暂停)";
            ExoLog.log(prefix + "正在播放状态改变：isPlaying=" + isPlayingDesc);
        }
    }

    /**
     * 播放被抑制的原因改变。
     *
     * @param playbackSuppressionReason 0. SUPPRESSION_REASON_NONE: 无抑制。
     *                                  1. SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS: 暂时失去音频焦点导致静音/暂停。
     */
    @Override
    public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
        Player.Listener.super.onPlaybackSuppressionReasonChanged(playbackSuppressionReason);
        if (ExoConfig.LOG_ENABLE) {
            String reasonDesc;
            switch (playbackSuppressionReason) {
                case Player.PLAYBACK_SUPPRESSION_REASON_NONE:
                    reasonDesc = "SUPPRESSION_REASON_NONE(无抑制)";
                    break;
                case Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS:
                    reasonDesc = "SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS(暂时失去音频焦点)";
                    break;
                default:
                    reasonDesc = "未知抑制原因(" + playbackSuppressionReason + ")";
            }
            ExoLog.log(prefix + "播放被抑制原因改变：playbackSuppressionReason=" + playbackSuppressionReason + "，原因描述=" + reasonDesc);
        }
    }

    /**
     * 加载状态改变。
     *
     * @param isLoading true 表示播放器正在分配内存、下载数据或处理源。
     */
    @Override
    public void onIsLoadingChanged(boolean isLoading) {
        Player.Listener.super.onIsLoadingChanged(isLoading);
        if (ExoConfig.LOG_ENABLE) {
            String isLoadingDesc = isLoading ? "true(正在加载资源/下载数据)" : "false(加载完成/未加载)";
            ExoLog.log(prefix + "加载状态改变：isLoading=" + isLoadingDesc);
        }
    }

    /**
     * 发生严重播放异常。
     *
     * @param error 包含 errorCode(错误码), message, cause(底层异常) 的对象。
     */
    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        onExoPlayerError(error);
        if (ExoConfig.LOG_ENABLE) {
            String causeMsg = error.getCause() != null ? error.getCause().getMessage() : "无底层异常";
            ExoLog.log(prefix + "发生严重播放异常：errorCode=" + error.errorCode + "，errorMessage=" + error.getMessage() + "，cause=" + causeMsg);
        }
    }

    /**
     * 播放器错误信息对象更新。
     *
     * @param error 最新的异常信息，可能为 null（当错误被清除时）。
     */
    @Override
    public void onPlayerErrorChanged(@Nullable PlaybackException error) {
        Player.Listener.super.onPlayerErrorChanged(error);
        if (ExoConfig.LOG_ENABLE) {
            String errorDesc;
            if (error == null) {
                errorDesc = "null(错误已清除)";
            } else {
                errorDesc = "errorCode=" + error.errorCode + "，message=" + error.getMessage();
            }
            ExoLog.log(prefix + "播放器错误信息更新：error=" + errorDesc);
        }
    }

    // ======================== 进度跳转与时间线回调 ======================== //

    /**
     * 播放位置非连续性改变（手动或自动跳转）。
     *
     * @param oldPosition 跳转前的位置，包含 windowIndex(媒体索引), positionMs(毫秒位置)等。
     * @param newPosition 跳转后的新位置信息。
     * @param reason      0. DISCONTINUITY_REASON_AUTO_TRANSITION: 自动切歌/切视频。
     *                    1. DISCONTINUITY_REASON_SEEK: 手动拖动进度条。
     *                    2. DISCONTINUITY_REASON_SEEK_ADJUSTMENT: 播放器为了对齐关键帧或处理边缘情况自动调整了跳转位置。
     *                    3. DISCONTINUITY_REASON_SKIP:由应用逻辑触发的跳过操作，如点击了“下一首”按钮 。
     *                    4. DISCONTINUITY_REASON_REMOVE: 移除媒体项导致位置变化。
     *                    4. DISCONTINUITY_REASON_INTERNAL: 开启了跳过静音功能，播放器自动跳过了静音片段。
     *                    4. DISCONTINUITY_REASON_SILENCE_SKIP: 内部原因。
     */
    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
        Player.Listener.super.onPositionDiscontinuity(oldPosition, newPosition, reason);
        if (ExoConfig.LOG_ENABLE) {
            String oldPosDesc = "windowIndex=" + oldPosition.windowIndex + "，positionMs=" + oldPosition.positionMs + "，mediaId=" + (oldPosition.mediaItem == null ? 0 : oldPosition.mediaItem.mediaId);
            String newPosDesc = "windowIndex=" + newPosition.windowIndex + "，positionMs=" + newPosition.positionMs + "，mediaId=" + (newPosition.mediaItem == null ? 0 : newPosition.mediaItem.mediaId);
            String reasonDesc;
            switch (reason) {
                case Player.DISCONTINUITY_REASON_AUTO_TRANSITION:
                    reasonDesc = "DISCONTINUITY_REASON_AUTO_TRANSITION(自动切歌/切视频)";
                    break;
                case Player.DISCONTINUITY_REASON_SEEK:
                    reasonDesc = "DISCONTINUITY_REASON_SEEK(手动拖动进度条)";
                    break;
                case Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
                    reasonDesc = "DISCONTINUITY_REASON_SEEK(播放器为了对齐关键帧或处理边缘情况自动调整了跳转位置)";
                    break;
                case Player.DISCONTINUITY_REASON_SKIP:
                    reasonDesc = "DISCONTINUITY_REASON_SKIP(由应用逻辑触发的跳过操作，如点击了“下一首”按钮)";
                    break;
                case Player.DISCONTINUITY_REASON_REMOVE:
                    reasonDesc = "DISCONTINUITY_REASON_REMOVE(移除媒体项导致位置变化)";
                    break;
                case Player.DISCONTINUITY_REASON_INTERNAL:
                    reasonDesc = "DISCONTINUITY_REASON_INTERNAL(播放器内部原因)";
                    break;
                case Player.DISCONTINUITY_REASON_SILENCE_SKIP:
                    reasonDesc = "DISCONTINUITY_REASON_SILENCE_SKIP(开启了跳过静音功能，播放器自动跳过了静音片段)";
                    break;
                default:
                    reasonDesc = "未知跳转原因(" + reason + ")";
            }
            ExoLog.log(prefix + "播放位置非连续性改变：旧位置=" + oldPosDesc + "，新位置=" + newPosDesc + "，reason=" + reason + "，原因描述=" + reasonDesc);
        }
    }

    /**
     * 时间线改变（播放列表或直播流窗口信息更新）。
     *
     * @param timeline 新的时间线对象，可用于查询播放列表长度、每个条目的时长。
     * @param reason   0. TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED: 播放列表增删改。
     *                 1. TIMELINE_CHANGE_REASON_SOURCE_UPDATE: 媒体源内部动态更新（如直播时长延长）。
     */
    @Override
    public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
        Player.Listener.super.onTimelineChanged(timeline, reason);
        if (ExoConfig.LOG_ENABLE) {
            String reasonDesc;
            switch (reason) {
                case Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED:
                    reasonDesc = "TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED(播放列表增删改)";
                    break;
                case Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE:
                    reasonDesc = "TIMELINE_CHANGE_REASON_SOURCE_UPDATE(媒体源内部动态更新，如直播)";
                    break;
                default:
                    reasonDesc = "未知时间线改变原因(" + reason + ")";
            }
            ExoLog.log(prefix + "时间线改变：播放列表长度=" + timeline.getWindowCount() + "，reason=" + reason + "，原因描述=" + reasonDesc);
        }
    }

    /**
     * 媒体项（MediaItem）切换回调。
     * 触发时机：当前播放的视频/音频结束并自动播放下一个，或者手动调用了 seekToNext/seekToPrevious/setMediaItem。
     * 此处通常用于上报埋点（视频切换次数）或更新 UI 的标题/封面
     *
     * @param mediaItem 新的媒体配置项对象。如果列表变为空，则为 null。
     *                  - mediaItem.mediaId: 唯一标识符。
     *                  - mediaItem.localConfiguration.uri: 播放地址。
     *                  - mediaItem.mediaMetadata: 包含标题、封面等元数据。
     * @param reason    切换原因。
     *                  0. MEDIA_ITEM_TRANSITION_REASON_REPEAT: 同一媒体项重复播放（循环模式）。
     *                  1. MEDIA_ITEM_TRANSITION_REASON_AUTO: 顺序播放完当前项后自动进入下一项。
     *                  2. MEDIA_ITEM_TRANSITION_REASON_SEEK: 手动触发的跳转（如点击下一曲或 Seek 到另一曲）。
     *                  3. MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED: 播放列表被修改（如移除当前项）导致的强制切换。
     */
    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        Player.Listener.super.onMediaItemTransition(mediaItem, reason);


        if (ExoConfig.LOG_ENABLE) {
            String mediaItemDesc = "null(无媒体项)";
            if (mediaItem != null) {
                String mid = mediaItem.mediaId != null ? mediaItem.mediaId : "未设置ID";
                String uri = (mediaItem.localConfiguration != null) ? mediaItem.localConfiguration.uri.toString() : "无URI配置";
                String title = (mediaItem.mediaMetadata != null && mediaItem.mediaMetadata.title != null)
                        ? mediaItem.mediaMetadata.title.toString() : "无标题";
                mediaItemDesc = String.format("ID[%s], Title[%s], URI[%s]", mid, title, uri);
            }

            String reasonDesc;
            switch (reason) {
                case Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT:
                    reasonDesc = "REPEAT (循环播放切换)";
                    break;
                case Player.MEDIA_ITEM_TRANSITION_REASON_AUTO:
                    reasonDesc = "AUTO (顺序自动切换)";
                    break;
                case Player.MEDIA_ITEM_TRANSITION_REASON_SEEK:
                    reasonDesc = "SEEK (手动操作/Seek切换)";
                    break;
                case Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED:
                    reasonDesc = "PLAYLIST_CHANGED (列表更新导致)";
                    break;
                default:
                    reasonDesc = "UNKNOWN (" + reason + ")";
            }
            ExoLog.log(prefix + "媒体项切换：mediaItem=" + mediaItemDesc + "，reason=" + reason + "，原因描述=" + reasonDesc);
        }
    }

    // ======================== 视频渲染与图形回调 ======================== //

    /**
     * 视频第一帧像素已渲染到 Surface 上。
     * 用于处理“首帧秒开”，隐藏封面图的最佳时机。
     */
    @Override
    public void onRenderedFirstFrame() {
        Player.Listener.super.onRenderedFirstFrame();
        onExoRenderedFirstFrame();
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "视频第一帧已成功渲染到Surface");
        }
    }

    /**
     * 视频分辨率或比例改变。
     *
     * @param videoSize 包含：width(宽), height(高), unappliedRotationDegrees(旋转角度), pixelWidthHeightRatio(像素宽高比)。
     */
    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        onExoVideoSizeChanged(videoSize);
        if (ExoConfig.LOG_ENABLE) {
            String videoSizeDesc = "width=" + videoSize.width + "，height=" + videoSize.height + "，旋转角度=" + videoSize.unappliedRotationDegrees + "，像素宽高比=" + videoSize.pixelWidthHeightRatio;
            ExoLog.log(prefix + "视频分辨率/比例改变：" + videoSizeDesc);
        }
    }

    /**
     * 渲染容器尺寸改变。
     *
     * @param width  像素宽度。
     * @param height 像素高度。
     */
    @Override
    public void onSurfaceSizeChanged(int width, int height) {
        Player.Listener.super.onSurfaceSizeChanged(width, height);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "渲染容器尺寸改变：width=" + width + "像素，height=" + height + "像素");
        }
    }

    // ======================== 音频与输出设备回调 ======================== //

    /**
     * 音频属性改变。
     *
     * @param audioAttributes 包含 contentType(内容类型), usage(使用场景), flags(标志位)。
     */
    @Override
    public void onAudioAttributesChanged(@NonNull AudioAttributes audioAttributes) {
        Player.Listener.super.onAudioAttributesChanged(audioAttributes);
        if (ExoConfig.LOG_ENABLE) {
            String contentTypeDesc;
            switch (audioAttributes.contentType) {
                case C.AUDIO_CONTENT_TYPE_MUSIC:
                    contentTypeDesc = "MUSIC (音乐/通用音频)";
                    break;
                case C.AUDIO_CONTENT_TYPE_MOVIE:
                    contentTypeDesc = "MOVIE (电影/视频，强调环绕感)";
                    break;
                case C.AUDIO_CONTENT_TYPE_SPEECH:
                    contentTypeDesc = "SPEECH (语音/播客，优化人声)";
                    break;
                case C.AUDIO_CONTENT_TYPE_SONIFICATION:
                    contentTypeDesc = "SONIFICATION (提示音/按键音)";
                    break;
                case C.AUDIO_CONTENT_TYPE_UNKNOWN:
                    contentTypeDesc = "UNKNOWN (未知类型)";
                    break;
                default:
                    contentTypeDesc = "OTHER (" + audioAttributes.contentType + ")";
            }

            String usageDesc;
            switch (audioAttributes.usage) {
                case C.USAGE_MEDIA:
                    usageDesc = "MEDIA (媒体播放，如音乐、视频、游戏)";
                    break;
                case C.USAGE_ALARM:
                    usageDesc = "ALARM (闹钟)";
                    break;
                case C.USAGE_NOTIFICATION:
                    usageDesc = "NOTIFICATION (通用通知)";
                    break;
                case C.USAGE_NOTIFICATION_RINGTONE:
                    usageDesc = "RINGTONE (来电铃声)";
                    break;
                case C.USAGE_VOICE_COMMUNICATION:
                    usageDesc = "VOICE_COMM (通话/VOIP)";
                    break;
                case C.USAGE_VOICE_COMMUNICATION_SIGNALLING:
                    usageDesc = "SIGNALLING (通话信令/按键音)";
                    break;
                case C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
                    usageDesc = "NAVIGATION (导航引导声)";
                    break;
                case C.USAGE_ASSISTANCE_SONIFICATION:
                    usageDesc = "SONIFICATION (系统交互提示声)";
                    break;
                case C.USAGE_GAME:
                    usageDesc = "GAME (游戏音效)";
                    break;
                case C.USAGE_ASSISTANT:
                    usageDesc = "ASSISTANT (智能助手反馈)";
                    break;
                default:
                    usageDesc = "OTHER (" + audioAttributes.usage + ")";
            }

            String flagsDesc = "0";
            if (audioAttributes.flags != 0) {
                StringBuilder sb = new StringBuilder();
                if ((audioAttributes.flags & C.FLAG_AUDIBILITY_ENFORCED) != 0)
                    sb.append("AUDIBILITY_ENFORCED(某些地区法律要求的强制声音) ");
//                if ((audioAttributes.flags & C.FLAG_) != 0) sb.append("HW_AV_SYNC(硬件音画同步) ");
                if ((audioAttributes.flags & android.media.AudioAttributes.FLAG_HW_AV_SYNC) != 0)
                    sb.append("HW_AV_SYNC(硬件音画同步) ");
                flagsDesc = sb.toString().trim();
            }

            ExoLog.log(prefix + "【音频属性改变】\n" +
                    "   内容类型：" + contentTypeDesc + "\n" +
                    "   使用场景：" + usageDesc + "\n" +
                    "   标志位：" + flagsDesc);
        }
    }

    /**
     * 音频会话 ID 改变。
     *
     * @param audioSessionId 系统分配的 ID，用于挂载外部音频特效（均衡器）。
     *                       注：用于在系统底层挂载音效插件（如均衡器、低音增强）。
     */
    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onAudioSessionIdChanged(int audioSessionId) {
        Player.Listener.super.onAudioSessionIdChanged(audioSessionId);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "音频会话ID改变：audioSessionId=" + audioSessionId + "（用于挂载均衡器等音频特效）");
        }
    }

    /**
     * 播放器内部音量级别改变。
     *
     * @param volume 浮点值，0.0 (静音) 到 1.0 (最大音量)。
     */
    @Override
    public void onVolumeChanged(float volume) {
        Player.Listener.super.onVolumeChanged(volume);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "播放器内部音量改变：volume=" + volume + "（0.0=静音，1.0=最大音量）");
        }
    }

    /**
     * 跳过静音状态改变。
     *
     * @param skipSilenceEnabled 是否启用了自动跳过音频静音片段。
     */
    @Override
    public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
        Player.Listener.super.onSkipSilenceEnabledChanged(skipSilenceEnabled);
        if (ExoConfig.LOG_ENABLE) {
            String skipDesc = skipSilenceEnabled ? "true(启用自动跳过静音片段)" : "false(禁用自动跳过静音片段)";
            ExoLog.log(prefix + "跳过静音状态改变：skipSilenceEnabled=" + skipDesc);
        }
    }

    /**
     * 输出设备信息改变（包含物理设备类型、音量范围、播放路径）。
     *
     * @param deviceInfo 设备信息。
     */
    @Override
    public void onDeviceInfoChanged(@NonNull DeviceInfo deviceInfo) {
        Player.Listener.super.onDeviceInfoChanged(deviceInfo);
        if (ExoConfig.LOG_ENABLE) {
            // 解析播放路径（本地播放 vs 远程投屏）
            String playbackTypeDesc;
            switch (deviceInfo.playbackType) {
                case DeviceInfo.PLAYBACK_TYPE_LOCAL:
                    playbackTypeDesc = "LOCAL(本地设备播放)";
                    break;
                case DeviceInfo.PLAYBACK_TYPE_REMOTE:
                    playbackTypeDesc = "REMOTE(远程投屏/Cast)";
                    break;
                default:
                    playbackTypeDesc = "UNKNOWN(" + deviceInfo.playbackType + ")";
            }


            // 获取音量范围（这对于自定义音量条非常关键）
            int minVol = deviceInfo.minVolume;
            int maxVol = deviceInfo.maxVolume;


            ExoLog.log(prefix + "【音频输出改变】\n" +
                    "   播放路径：" + playbackTypeDesc + "\n" +
                    "   音量区间：[" + minVol + " ~ " + maxVol + "]");
        }
    }

    /**
     * 硬件设备音量或静音状态改变。
     *
     * @param volume 当前音量等级。
     * @param muted  是否硬件静音。
     */
    @Override
    public void onDeviceVolumeChanged(int volume, boolean muted) {
        Player.Listener.super.onDeviceVolumeChanged(volume, muted);
        if (ExoConfig.LOG_ENABLE) {
            String mutedDesc = muted ? "true(硬件静音)" : "false(非硬件静音)";
            ExoLog.log(prefix + "硬件设备音量/静音改变：volume=" + volume + "，muted=" + mutedDesc);
        }
    }

    // ======================== 轨道选择、元数据与字幕回调 ======================== //

    /**
     * 轨道（Tracks）信息改变回调。
     *
     * @param tracks 包含当前流支持的所有视频轨、音轨、字幕轨及其选中状态。
     *               业务场景：用于动态生成“画质选择”、“音轨切换”菜单。
     */
    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
        if (ExoConfig.LOG_ENABLE) {
            for (Tracks.Group group : tracks.getGroups()) {
                String trackTypeDesc;
                switch (group.getType()) {
                    case C.TRACK_TYPE_VIDEO:
                        trackTypeDesc = "视频轨";
                        break;
                    case C.TRACK_TYPE_AUDIO:
                        trackTypeDesc = "音频轨";
                        break;
                    case C.TRACK_TYPE_TEXT:
                        trackTypeDesc = "字幕轨";
                        break;
                    case C.TRACK_TYPE_METADATA:
                        trackTypeDesc = "元数据轨";
                        break;
                    default:
                        trackTypeDesc = "未知轨道类型";
                }
                String groupDesc = "轨道类型=" + trackTypeDesc + "，轨道数量=" + group.getMediaTrackGroup().length + "，是否选中=" + group.isSelected();
                ExoLog.log(prefix + "轨道信息更新：" + groupDesc);
            }
        }
    }

    /**
     * 全局轨道选择参数改变回调。
     *
     * @param parameters 包含：强制选中的语言、允许的最大视频分辨率、是否支持偏好设置。
     */
    @Override
    public void onTrackSelectionParametersChanged(@NonNull TrackSelectionParameters parameters) {
        Player.Listener.super.onTrackSelectionParametersChanged(parameters);
        if (ExoConfig.LOG_ENABLE) {
            String audioLangs = parameters.preferredAudioLanguages.isEmpty() ? "默认" : parameters.preferredAudioLanguages.toString();
            String textLangs = parameters.preferredTextLanguages.isEmpty() ? "无" : parameters.preferredTextLanguages.toString();

            String maxVideoSize = (parameters.maxVideoWidth == Integer.MAX_VALUE)
                    ? "无限制" : parameters.maxVideoWidth + "x" + parameters.maxVideoHeight;

            String maxBitrate = (parameters.maxVideoBitrate == Integer.MAX_VALUE)
                    ? "无限制" : (parameters.maxVideoBitrate / 1000) + "kbps";

            String maxFrameRate = (parameters.maxVideoFrameRate == Integer.MAX_VALUE)
                    ? "无限制" : parameters.maxVideoFrameRate + "fps";

            String viewportSize = (parameters.viewportWidth == Integer.MAX_VALUE)
                    ? "全屏/未限制" : parameters.viewportWidth + "x" + parameters.viewportHeight;

            // forceHighestSupportedBitrate: 即使带宽不足也强制选择设备支持的最高码率轨道
            boolean forceHighest = parameters.forceHighestSupportedBitrate;

            StringBuilder disabledDesc = new StringBuilder();
            if (parameters.disabledTrackTypes.isEmpty()) {
                disabledDesc.append("无");
            } else {
                // 通过简单的常量比对转换
                if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
                    disabledDesc.append("视频 ");
                if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
                    disabledDesc.append("音频 ");
                if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
                    disabledDesc.append("字幕 ");
            }

            ExoLog.log(prefix + "【轨道参数更新】\n" +
                    "   音频语言：" + audioLangs + "\n" +
                    "   字幕语言：" + textLangs + "\n" +
                    "   视频约束：尺寸=" + maxVideoSize + "，码率=" + maxBitrate + "，帧率=" + maxFrameRate + "\n" +
                    "   渲染视口：" + viewportSize + "\n" +
                    "   强制最高码率：" + forceHighest + "\n" +
                    "   禁用轨道类型：" + disabledDesc);
        }
    }

    /**
     * 媒体项元数据（Metadata）改变。
     *
     * @param mediaMetadata 包含 title, artist, albumTitle, artworkUri(封面地址) 等。
     */
    @Override
    public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
        Player.Listener.super.onMediaMetadataChanged(mediaMetadata);
        if (ExoConfig.LOG_ENABLE) {
            CharSequence title = mediaMetadata.title != null ? mediaMetadata.title : "无";
            CharSequence artist = mediaMetadata.artist != null ? mediaMetadata.artist : "无";
            CharSequence albumTitle = mediaMetadata.albumTitle != null ? mediaMetadata.albumTitle : "无";
            CharSequence artworkUri = mediaMetadata.artworkUri != null ? mediaMetadata.artworkUri.toString() : "无";
            String metadataDesc = "标题=" + title + "，艺术家=" + artist + "，专辑=" + albumTitle + "，封面URI=" + artworkUri;
            ExoLog.log(prefix + "媒体项元数据改变：" + metadataDesc);
        }
    }

    /**
     * 整个播放列表（Playlist）元数据更新。
     *
     * @param mediaMetadata 包含 title, artist, albumTitle, artworkUri(封面地址) 等。
     */
    @Override
    public void onPlaylistMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
        Player.Listener.super.onPlaylistMetadataChanged(mediaMetadata);
        if (ExoConfig.LOG_ENABLE) {
            CharSequence title = mediaMetadata.title != null ? mediaMetadata.title : "无";
            CharSequence artist = mediaMetadata.artist != null ? mediaMetadata.artist : "无";
            CharSequence artworkUri = mediaMetadata.artworkUri != null ? mediaMetadata.artworkUri.toString() : "无";
            CharSequence metadataDesc = "播放列表标题=" + title + "，艺术家=" + artist + "，封面URI=" + artworkUri;
            ExoLog.log(prefix + "播放列表元数据改变：" + metadataDesc);
        }
    }

    /**
     * 字幕、歌词（Cues）内容更新。
     *
     * @param cueGroup 包含当前时间点需要渲染的字幕文本、位置、样式。
     */
    @Override
    public void onCues(@NonNull CueGroup cueGroup) {
        Player.Listener.super.onCues(cueGroup);
        if (ExoConfig.LOG_ENABLE) {
            List<Cue> cueList = cueGroup.cues;

            if (cueList == null || cueList.isEmpty()) {
                ExoLog.log(prefix + "字幕内容更新：当前无字幕内容（清空显示）");
                return;
            }

            StringBuilder cueDesc = new StringBuilder();
            for (int i = 0; i < cueList.size(); i++) {
                Cue cue = cueList.get(i);
                String cueText = (cue.text != null) ? cue.text.toString() : "空文本";

                float linePos = cue.line;
                String lineType = (cue.lineType == Cue.LINE_TYPE_FRACTION) ? "比例" : "行数";

                float size = cue.size;

                cueDesc.append("\n  [第").append(i + 1).append("条] ")
                        .append("内容=\"").append(cueText).append("\"")
                        .append(", 位置=").append(linePos).append("(").append(lineType).append(")")
                        .append(", 宽度占比=").append(size * 100).append("%");
            }

            ExoLog.log(prefix + "字幕内容更新：检测到 " + cueList.size() + " 条有效数据" + cueDesc.toString());
        }
    }

    /**
     * 定时元数据回调（ID3/EMSG）。
     *
     * @param metadata 包含嵌入在流中的私有数据信息。
     */
    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onMetadata(@NonNull Metadata metadata) {
        Player.Listener.super.onMetadata(metadata);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "定时元数据更新：元数据条目数量=" + metadata.length() + "，元数据类型=" + metadata.getClass().getSimpleName());
        }
    }

    // ======================== 设置、聚合与配置回调 ======================== //

    /**
     * 聚合事件通知回调。
     *
     * @param player 播放器实例引用。
     * @param events 一个位图集合，记录了在当前迭代中发生的所有改变（如状态变了+位置也变了）。
     *               业务价值：减少 UI 刷新频率，一次性处理多个状态同步。
     */
    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        Player.Listener.super.onEvents(player, events);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "聚合事件通知：当前迭代周期内事件数量=" + events.size() + "，播放器实例HashCode=" + player.hashCode());
        }
    }

    /**
     * 播放器可用指令（Commands）集合改变通知。
     *
     * @param availableCommands 包含当前状态下用户能做的事（如 COMMAND_SEEK_IN_CURRENT_ITEM）。
     */
    @Override
    public void onAvailableCommandsChanged(@NonNull Player.Commands availableCommands) {
        Player.Listener.super.onAvailableCommandsChanged(availableCommands);
        if (ExoConfig.LOG_ENABLE) {
            // 基础播放控制权限
            boolean canPause = availableCommands.contains(Player.COMMAND_PLAY_PAUSE);
            boolean canSeek = availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);

            // 播放列表/跨条目控制权限 (对短视频列表切换很有用)
            boolean canPrev = availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS);
            boolean canNext = availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT);

            // 进度条拖动细分（快进/快退）
            boolean canSeekForward = availableCommands.contains(Player.COMMAND_SEEK_FORWARD);
            boolean canSeekBack = availableCommands.contains(Player.COMMAND_SEEK_BACK);

            // 其它高级权限
            boolean canGetTracks = availableCommands.contains(Player.COMMAND_GET_TRACKS); // 是否允许获取/选择轨道（画质切换）
            boolean canSetVol = availableCommands.contains(Player.COMMAND_SET_VOLUME); // 是否允许调节音量

            StringBuilder sb = new StringBuilder();
            sb.append("    基础控制: [暂停/播放: ").append(canPause).append(", Seek: ").append(canSeek).append("]\n");
            sb.append("    列表控制: [上一首: ").append(canPrev).append(", 下一首: ").append(canNext).append("]\n");
            sb.append("    播放列表/跨条目控制权限: [快进: ").append(canSeekForward).append(", 快退: ").append(canSeekBack).append("]\n");
            sb.append("    进阶功能: [画质切换: ").append(canGetTracks).append(", 音量调节: ").append(canSetVol).append("]");

            ExoLog.log(prefix + "【播放器指令集变更】\n" + sb);
        }
    }


    /**
     * 播放速度与音调参数改变通知。
     *
     * @param playbackParameters speed(倍速，如1.5f), pitch(音调)。
     */
    @Override

    public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
        Player.Listener.super.onPlaybackParametersChanged(playbackParameters);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "播放速度与音调改变：倍速=" + playbackParameters.speed + "，音调=" + playbackParameters.pitch);
        }
    }

    /**
     * 循环播放模式改变通知。
     *
     * @param repeatMode 0-关闭, 1-单曲循环, 2-列表循环。
     */
    @Override
    public void onRepeatModeChanged(int repeatMode) {
        Player.Listener.super.onRepeatModeChanged(repeatMode);
        if (ExoConfig.LOG_ENABLE) {
            String repeatDesc;
            switch (repeatMode) {
                case Player.REPEAT_MODE_OFF:
                    repeatDesc = "REPEAT_MODE_OFF(关闭循环)";
                    break;
                case Player.REPEAT_MODE_ONE:
                    repeatDesc = "REPEAT_MODE_ONE(单曲循环)";
                    break;
                case Player.REPEAT_MODE_ALL:
                    repeatDesc = "REPEAT_MODE_ALL(列表循环)";
                    break;
                default:
                    repeatDesc = "未知循环模式(" + repeatMode + ")";
            }
            ExoLog.log(prefix + "循环播放模式改变：repeatMode=" + repeatMode + "，模式描述=" + repeatDesc);
        }
    }

    /**
     * 随机播放模式开关通知。
     *
     * @param shuffleModeEnabled true 开启随机播放。
     */
    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        Player.Listener.super.onShuffleModeEnabledChanged(shuffleModeEnabled);
        if (ExoConfig.LOG_ENABLE) {
            String shuffleDesc = shuffleModeEnabled ? "true(开启随机播放)" : "false(关闭随机播放)";
            ExoLog.log(prefix + "随机播放模式改变：shuffleModeEnabled=" + shuffleDesc);
        }
    }

    /**
     * 手动快退步长改变通知。
     *
     * @param seekBackIncrementMs 快退的时间长度（毫秒）。
     */
    @Override
    public void onSeekBackIncrementChanged(long seekBackIncrementMs) {
        Player.Listener.super.onSeekBackIncrementChanged(seekBackIncrementMs);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "手动快退步长改变：seekBackIncrementMs=" + seekBackIncrementMs + "毫秒");
        }
    }

    /**
     * 手动快进步长改变通知。
     *
     * @param seekForwardIncrementMs 快进的时间长度（毫秒）。
     */
    @Override
    public void onSeekForwardIncrementChanged(long seekForwardIncrementMs) {
        Player.Listener.super.onSeekForwardIncrementChanged(seekForwardIncrementMs);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "手动快进步长改变：seekForwardIncrementMs=" + seekForwardIncrementMs + "毫秒");
        }
    }

    /**
     * “跳转到上一项”判定的最大时长阈值通知。
     *
     * @param maxSeekToPreviousPositionMs 判定界限。
     *                                    注：如果当前位置小于此值，点击“上一个”会切到上一首；大于此值则回到本首起始点。
     */
    @Override
    public void onMaxSeekToPreviousPositionChanged(long maxSeekToPreviousPositionMs) {
        Player.Listener.super.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs);
        if (ExoConfig.LOG_ENABLE) {
            ExoLog.log(prefix + "跳转到上一项最大时长阈值改变：maxSeekToPreviousPositionMs=" + maxSeekToPreviousPositionMs + "毫秒（小于此值切上一首，大于此值回本首开头）");
        }
    }

    // ======================== 业务层抽象方法 ======================== //

    protected abstract void onExoPlaybackStateChanged(int state);

    protected abstract void onExoRenderedFirstFrame();

    protected abstract void onExoPlayerError(PlaybackException error);

    protected abstract void onExoIsPlayingChanged(boolean isPlaying);

    protected abstract void onExoVideoSizeChanged(@NonNull VideoSize videoSize);
}