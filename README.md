![image](https://img.shields.io/badge/Android%20Min%20SDK-21+-brightgreen.svg)

一款基于 **ExoPlayer** 打造的播放器库，聚焦竖向滑动短视频播放场景与视频播放场景，提供高性能、高定制化、交互体验极致的播放解决方案。

## 🌟 核心特性

### 1. 场景化交互体验

- 抖音风格竖向分页滑动
- 进度条触摸加粗 + 圆点弹出动画、流式箭头渐变动画、流光加载文字
- 音频均衡器触摸震动反馈、手势缩放 / 亮度 / 音量调节
- 音效处理器可自定义且实时调整播放器的音频效果
- 断点续播与试看时间限制的完整支持
- 完整的播放状态联动（加载中 / 播放完成 / 播放错误 / 暂停等状态视图自动切换）
- 智能预加载：支持按网络状态（WiFi / 流量）、设备性能动态调整预加载数量
- 视图缓存优化、资源自动释放（避免内存泄漏）
- 手势交互与 UI 渲染解耦，避免滑动卡顿

### 2. 组件体系

#### 2.1 基础交互 UI 组件

| 组件名                    | 功能描述                                     |
| ------------------------- | -------------------------------------------- |
| `ExoSeekBar`              | 抖音风格进度条（普通 / 抖音模式切换）        |
| `ExoDouyinArrowView`      | 抖音流式箭头（透明度渐变流动动画）           |
| `ExoLoadingText`          | 流光加载文字（渐变遮罩 + 呼吸缩放动画）      |
| `ExoEqPanelView`          | 10 段音频均衡器面板（触摸调整 + 频谱可视化） |
| `ExoStrongGradientButton` | 多状态渐变按钮（正常 / 按下 / 禁用）         |

#### 2.2 播放状态视图组件

| 组件名                     | 功能描述                                         |
| -------------------------- | ------------------------------------------------ |
| `ExoComponentCompleteView` | 播放完成视图（重播 / 退出全屏按钮，自动显隐）    |
| `ExoComponentErrorView`    | 播放错误视图（错误提示 + 重试按钮，出错时触发）  |
| `ExoComponentLoadingView`  | 加载中视图（缓冲动画 + 网速 / 流量展示）         |
| `ExoComponentPausedView`   | 暂停视图（暂停按钮，暂停时自动显示）             |
| `ExoComponentSpeedView`    | 倍速组件（0.25X~2.0X 动画展开 / 收起列表）       |
| `ExoComponentTitleBarView` | 横屏标题栏（模拟状态栏，显示时间 / 电量 / 网络） |

#### 2.3 播放控制栏组件

| 组件名                                 | 功能描述                                               |
| -------------------------------------- | ------------------------------------------------------ |
| `ExoLiveControlBarView`                | 直播专用控制栏（播放 / 暂停 / 全屏 / 刷新 / 调试入口） |
| `ExoShortVideoControlBarView`          | 短视频竖屏控制栏（播放 / 暂停 / 进度条 / 全屏）        |
| `ExoShortVideoLandscapeControlBarView` | 短视频横屏控制栏（倍速 / 进度时间 / 刷新）             |
| `ExoShortVideoSimpleControlBarView`    | 短视频极简下拉控制栏（倍速锁定 / 边缘手势）            |
| `ExoVodControlBarView`                 | 普通视频控制栏（播放 / 暂停 / 倍速 / 全屏 / 刷新）     |

### 3. 高可定制性

- 丰富的回调接口，支持自定义播放控制、手势交互、音频可视化扩展
- 全局配置中心，支持动态调整核心参数
- 控制组件可感知生命周期与播放器回调，支持按需无限组合，可自定义扩展新的播放控制视图进行热插拔装载且无任何耦合度

## 📚 核心功能详解

### 本组件库已实现播放交互、状态联动、组件渲染等全流程自动化，开发者无需关注底层绑定/监听逻辑，仅需通过以下方式完成定制化和核心能力调用：

```
binding.simplePlayer.play(
    ExoPlayMode.VOD,       // 播放模式：VOD(点播)/LIVE(直播)/SHORT_VIDEO(短视频)
    0,                     // 断点续播进度（0则从头播放，组件自动记录上次进度）
    "https://xxx.com/video.mp4"  // 播放地址
);

binding.simplePlayer.pause();       // 暂停播放（自动显示暂停视图）
binding.simplePlayer.resume();      // 恢复播放（自动隐藏暂停视图）
binding.simplePlayer.replay();      // 重新播放（自动重置进度、隐藏完成视图）
binding.simplePlayer.seekTo(10000); // 跳转进度（自动同步进度条、状态视图）
binding.simplePlayer.setPlayLocationLastTime("上次播放至"); // 断点续播提示文案（自动显示/隐藏）


```

### 接口 / 类说明

|             接口 / 类名              |  类型  |                    核心作用                     |
| :----------------------------------: | :----: | :---------------------------------------------: |
|          `ExoGestureEnable`          |   类   |             控制各类手势的禁用状态              |
|        `IExoControlComponent`        |  接口  |         定义播放控制 UI 组件的核心规范          |
|           `IExoController`           |  接口  |     播放器核心控制，解耦 UI 与底层播放逻辑      |
|      `IExoPcmStreamController`       |  接口  |   面向 PCM 推流场景的独立控制面，不污染 URL 播放语义   |
|          `IExoFFTCallBack`           |  接口  |            回调音频 FFT 频谱相关数据            |
|        `IExoGestureCallBack`         |  接口  |              回调所有手势交互事件               |
|           `IExoLifecycle`            |  接口  |           回调播放器生命周期相关事件            |
|         `IExoNotifyCallBack`         |  接口  |             回调播放器核心状态变更              |
|      `IExoOnPageChangeListener`      |  接口  |           回调短视频列表页面切换事件            |
|         `IExoPlayerListener`         | 抽象类 |         封装 ExoPlayer 底层所有回调事件         |
|         `IExoScaleCallBack`          |  接口  |             控制播放器缩放相关操作              |
| `OnExoVideoPlayRecyclerViewCallBack` |  接口  | 回调短视频列表 ExoVideoPlayRecyclerView交互事件 |

### PCM 流式播放

`play(mode, lastPlayTime, url)` 仍然是库内原有的 URL / `MediaSource` 播放主链，本次新增的是一条独立的 PCM 流式播放链路，适用于 TTS、边下发边播的语音流以及业务层自己持有原始 PCM 数据的场景。

PCM 模式的设计原则：

- 独立于 `IExoController` 语义，流式能力通过 `IExoPcmStreamController` 单独暴露。
- 底层使用 `AudioTrack.MODE_STREAM`，不会阻塞上游音频回调线程。
- 继续复用现有频谱和均衡器处理器，所以 PCM 模式下频谱组件、EQ 面板仍然可用。
- PCM v1 仅支持 `PCM_16BIT` 输入，不负责 mp3/aac/opus 解码。
- PCM 模式下暂不支持 `seekTo`、`refresh`、`rePlay`、倍速、全屏和视频手势，这些调用会被安全忽略并记录日志。

#### ExoPcmStreamConfig

`ExoPcmStreamConfig` 用于描述一次 PCM 会话的固定格式与缓冲策略，当前包含以下字段：

- `sampleRateHz`
- `channelCount`
- `encoding`
- `bufferSizeInBytes`
- `audioUsage`
- `contentType`
- `maxQueuedDurationMs`

默认值：

- `sampleRateHz = 16000`
- `channelCount = 1`
- `encoding = AudioFormat.ENCODING_PCM_16BIT`
- `bufferSizeInBytes = minBufferSize * 2`
- `audioUsage = AudioAttributes.USAGE_MEDIA`
- `contentType = AudioAttributes.CONTENT_TYPE_SPEECH`
- `maxQueuedDurationMs = 5000`

#### 最小可用示例

```java
SimpleExoPlayerView playerView = findViewById(R.id.simple_player);
playerView.useDefaultComponents();

ExoPcmStreamConfig config = new ExoPcmStreamConfig()
        .setSampleRateHz(16000)
        .setChannelCount(1)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT);

playerView.startPcmStream(config);

// 业务层在收到一段 PCM 数据后持续推入播放器
playerView.appendPcmData(pcmBytes, 0, pcmBytes.length);

// 数据全部送达后，通知播放器把剩余缓冲播放完成
playerView.completePcmStream();
```

#### 腾讯云 TTS 接入方式示例

播放器只接收 PCM 数据，不依赖腾讯云 SDK。本库建议业务层在自己的 TTS 回调里做一层适配：

```java
SimpleExoPlayerView playerView = findViewById(R.id.simple_player);

ExoPcmStreamConfig config = new ExoPcmStreamConfig()
        .setSampleRateHz(16000)
        .setChannelCount(1)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT);

playerView.startPcmStream(config);

// 伪代码：这里的 callback 来自业务层自己的腾讯云 TTS 封装
ttsCallback = new TtsCallback() {
    @Override
    public void onAudioResult(ByteBuffer audioBuffer) {
        playerView.appendPcmData(audioBuffer);
    }

    @Override
    public void onSynthesisEnd() {
        playerView.completePcmStream();
    }

    @Override
    public void onError(Throwable throwable) {
        playerView.cancelPcmStream();
    }
};
```

#### 行为说明

- `pause()` / `resume()` 在 PCM 模式下会直接暂停和恢复 `AudioTrack`。
- `setEqualizer(...)` 会同时作用于 URL 主链和 PCM 流式链路，保证 UI 行为一致。
- `IExoFFTCallBack`、`ExoComponentSpectrumView` 在 PCM 模式下仍会持续收到频谱数据。
- `getQueuedPcmDurationMs()` 可用于观察当前排队待播时长，帮助业务层做限流和调试。
- 如果队列超过 `maxQueuedDurationMs`，播放器会回调错误并丢弃超限分片，避免持续堆积导致内存风险。

## 🏗️ 架构设计

### 整体分层

| 分层       | 核心职责                         | 核心类 / 组件                                          |
| ---------- | -------------------------------- | ------------------------------------------------------ |
| 业务接入层 | 对外暴露 API，回调分发           | `SimpleExoPlayerView`/`ExoVideoPlayRecyclerView`       |
| UI 组件层  | 自定义交互控件，支持样式定制     | 基础交互组件 / 播放状态组件 / 控制栏组件               |
| 控制基类层 | 通用逻辑封装，组件复用           | `BaseExoControlComponent`/`IExoControlComponentLayout` |
| 播放核心层 | 播放器管理、预加载、状态接收控制 | `ExoVideoView`/`ExoPreloadHelper`                      |
| 工具辅助层 | 通用工具方法、配置管理           | utils                                                  |
| 配置层     | 全局配置参数                     | `ExoConfig`                                            |

## ⚠️ 注意事项

权限申请：确保添加网络权限（播放网络视频）和震动权限（均衡器触摸反馈）

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.VIBRATE" />
```



## 🚀 快速开始

### 环境要求

- Android Min SDK: 21+
- ExoPlayer 版本: 1.8.0

#### Step 1: 添加仓库

在项目根目录 `build.gradle` 中添加：

```
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

#### Step 2: 添加依赖



```
dependencies {
    implementation 'com.github.michael007js:SimpleSuperExoPlayer:0.0.1'
}
```

### 2. 基础使用示例

#### Step 1: 初始化

```
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ExoConfig.init(this, "exo_config", true);
    }
}
```

#### Step 2: 布局中添加播放器列表

```
<com.sss.michael.exo.SimpleExoPlayerView
	android:id="@+id/simple_player"
	android:layout_width="match_parent"
	android:layout_height="match_parent" />
```

#### Step 3: 代码中使用

```
binding.simplePlayer.play(ExoPlayMode.VOD, 0/*断点续播进度，0从头开始*/, playUrl);
//断点续播提示
binding.simplePlayer.setPlayLocationLastTime("上次播放");
//使用默认交互组件，可热插拔自定义
binding.simplePlayer.useDefaultComponents();

// PCM 流式播放示例
binding.simplePlayer.startPcmStream(new ExoPcmStreamConfig()
        .setSampleRateHz(16000)
        .setChannelCount(1)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT));
binding.simplePlayer.appendPcmData(pcmBytes, 0, pcmBytes.length);
binding.simplePlayer.completePcmStream();

```

## 📄 许可证

```
Copyright 2026 ExoShortVideoSDK Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 📞 联系作者

- Email: michael007js@gmail.com
- [michael的小站](https://www.michael007js.cn)
