package com.sss.michael.demo;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.text.TextUtils;
import android.widget.Toast;

import com.sss.michael.demo.base.BaseActivity;
import com.sss.michael.demo.databinding.ActivityTtsStreamBinding;
import com.sss.michael.exo.SimpleExoPlayerView;
import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.constant.ExoEqualizerPreset;
import com.sss.michael.exo.widget.ExoEqPanelView;

/**
 * PCM 流式 TTS 演示页。
 *
 * <p>该页面使用 {@link TtsStreamPlayer} 演示业务层如何把腾讯云流式 TTS 回调得到的 PCM 数据
 * 接入播放器新增的 PCM 流式 API，并同时复用频谱和均衡器能力。
 */
public class TtsStreamActivity extends BaseActivity<ActivityTtsStreamBinding> {

    private static final String DEFAULT_DEMO_TEXT =
            "你好，这里是 SimpleSuperExoPlayer 的 PCM 流式播放演示。" +
                    "当前示例会把腾讯云流式语音合成返回的 PCM 数据持续推送给播放器。" +
                    "你可以在播放过程中观察频谱变化，并实时拖动均衡器查看声音效果。";
    private static final int DEFAULT_TENCENT_VOICE_TYPE = 1001;

    private SimpleExoPlayerView playerView;
    private TtsStreamPlayer ttsStreamPlayer;

    @Override
    protected int setLayout() {
        return R.layout.activity_tts_stream;
    }

    @Override
    protected void init() {
        playerView = new SimpleExoPlayerView(this);
        playerView.setExoFFTCallBack(new IExoFFTCallBack() {
            @Override
            public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
            }

            @Override
            public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
                binding.columnView.setVisibility(binding.rbColumnView.isChecked() ? VISIBLE : GONE);
                binding.columnView.onMagnitudeReady(sampleRateHz, magnitude);

                binding.glow.setVisibility(binding.rbGlow.isChecked() ? VISIBLE : GONE);
                binding.glow.onMagnitudeReady(sampleRateHz, magnitude);
            }
        });

        ttsStreamPlayer = new TtsStreamPlayer(this, playerView);
        ttsStreamPlayer.setOnTtsPlayerCallback(new TtsStreamPlayer.OnTtsPlayerCallback() {
            @Override
            public void onStart() {
                binding.tvStatus.setText("状态：开始合成并播放");
            }

            @Override
            public void onPause() {
                binding.tvStatus.setText("状态：已暂停输出");
            }

            @Override
            public void onResume() {
                binding.tvStatus.setText("状态：继续播放");
            }

            @Override
            public void onStop() {
                binding.tvStatus.setText("状态：已停止");
            }

            @Override
            public void onEnd() {
                binding.tvStatus.setText("状态：合成结束，播放器正在收尾播放缓冲");
            }

            @Override
            public void onError(String errorMessage) {
                binding.tvStatus.setText("状态：发生错误");
                Toast.makeText(TtsStreamActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPositionMs, int bufferedPercentage) {
            }
        });

        binding.etInput.setText(DEFAULT_DEMO_TEXT);
        binding.tvStatus.setText("状态：待开始");

        binding.btnStart.setOnClickListener(v -> {
            String inputText = binding.etInput.getText() == null
                    ? ""
                    : binding.etInput.getText().toString().trim();
            if (TextUtils.isEmpty(inputText)) {
                Toast.makeText(TtsStreamActivity.this, "请输入要合成的文本", Toast.LENGTH_SHORT).show();
                return;
            }
            ttsStreamPlayer.start(inputText);
        });
        binding.btnPause.setOnClickListener(v -> ttsStreamPlayer.onPause());
        binding.btnResume.setOnClickListener(v -> ttsStreamPlayer.onResume());
        binding.btnStop.setOnClickListener(v -> ttsStreamPlayer.onStop());

        // 倍速是播放器本地实时能力，切换后会立即作用到当前 PCM 流。
        binding.rgSpeed.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_speed_075) {
                ttsStreamPlayer.setPlaybackSpeed(0.75f);
            } else if (checkedId == R.id.rb_speed_15) {
                ttsStreamPlayer.setPlaybackSpeed(1.5f);
            } else if (checkedId == R.id.rb_speed_2) {
                ttsStreamPlayer.setPlaybackSpeed(2.0f);
            } else {
                ttsStreamPlayer.setPlaybackSpeed(1.0f);
            }
        });

        // 音色使用腾讯云 voiceType。它属于 TTS 合成请求参数，不走播放器本地变声。
        binding.btnApplyVoiceType.setOnClickListener(v -> {
            String voiceTypeText = binding.etVoiceType.getText() == null
                    ? ""
                    : binding.etVoiceType.getText().toString().trim();
            int voiceType = parseVoiceTypeOrDefault(voiceTypeText);
            ttsStreamPlayer.setVoiceType(voiceType);
            binding.tvStatus.setText("状态：已设置腾讯云音色 voiceType=" + voiceType);
        });

        binding.eq.setOnEqChangeListener(new ExoEqPanelView.OnEqChangeListener() {
            @Override
            public void onBandGainChanged(int band, float gain) {
            }

            @Override
            public void onAllGainsBandGainChanged(float[] allGains) {
                System.arraycopy(allGains, 0, ExoEqualizerPreset.CUSTOM.getGains(), 0, allGains.length);
                ExoEqualizerPreset.CUSTOM.save(allGains);
                playerView.setEqualizer(ExoEqualizerPreset.CUSTOM);
            }
        });
    }

    private int parseVoiceTypeOrDefault(String voiceTypeText) {
        if (TextUtils.isEmpty(voiceTypeText)) {
            return DEFAULT_TENCENT_VOICE_TYPE;
        }
        try {
            int voiceType = Integer.parseInt(voiceTypeText);
            return voiceType > 0 ? voiceType : DEFAULT_TENCENT_VOICE_TYPE;
        } catch (NumberFormatException e) {
            Toast.makeText(this, "voiceType 无效，已使用默认音色", Toast.LENGTH_SHORT).show();
            return DEFAULT_TENCENT_VOICE_TYPE;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ttsStreamPlayer.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ttsStreamPlayer.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ttsStreamPlayer.onDestroy();
        playerView.release();
    }
}
