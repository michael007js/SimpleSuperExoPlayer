package com.sss.michael.exo.callback;

import com.sss.michael.exo.bean.ExoPcmStreamConfig;

import java.nio.ByteBuffer;

/**
 * PCM 推流播放控制接口。
 *
 * <p>该接口只面向“业务层主动推送 PCM 数据”的播放场景，不与
 * {@link IExoController#play(com.sss.michael.exo.constant.ExoPlayMode, long, String)} 的 URL /
 * MediaSource 播放语义混用。标准调用顺序如下：
 *
 * <ol>
 *     <li>先调用 {@link #startPcmStream(ExoPcmStreamConfig)} 初始化本次流式会话。</li>
 *     <li>通过 {@code appendPcmData(...)} 持续追加 16-bit PCM 数据分片。</li>
 *     <li>当业务层确认不会再有新数据时，调用 {@link #completePcmStream()}。</li>
 *     <li>若需要中止会话，则调用 {@link #cancelPcmStream()}；组件销毁时继续沿用 {@code release()}。</li>
 * </ol>
 *
 * <p>所有追加方法都应当满足“对生产线程无阻塞”的要求：实现层负责复制输入数据到内部队列，
 * 再由专用工作线程串行执行 DSP 处理和 AudioTrack 写入。
 */
public interface IExoPcmStreamController {

    /**
     * 启动新的 PCM 流式播放会话。
     *
     * <p>调用后播放器会切换到 PCM 模式，清理上一轮 PCM 会话状态，并按 {@code config}
     * 初始化内部 AudioTrack 与处理链资源。
     *
     * <p>配置对象会被视为本次会话的只读快照。如果采样率、声道数或排队策略需要变化，应结束
     * 当前会话后重新调用本方法启动新的流。
     *
     * @param config 本次 PCM 会话的格式、音频属性与队列上限配置
     */
    void startPcmStream(ExoPcmStreamConfig config);

    /**
     * 以 {@link ByteBuffer} 形式追加一段 PCM 数据。
     *
     * <p>实现层必须复制 {@code buffer} 当前剩余区间的数据，因为上游合成回调通常会复用同一块
     * 缓冲区，不能直接持有调用方的可变引用。
     *
     * @param buffer 当前 remaining 区间内存放 PCM 16-bit 音频数据的缓冲区
     */
    void appendPcmData(ByteBuffer buffer);

    /**
     * 以字节数组形式追加一段 PCM 数据。
     *
     * <p>实现层应只复制 {@code offset}/{@code length} 指定的有效区间，并把复制后的数据放入内部
     * 队列，由异步工作线程继续处理和播放。
     *
     * @param data 包含 PCM 16-bit 音频数据的源数组
     * @param offset 本次有效 PCM 数据的起始偏移
     * @param length 本次需要追加的 PCM 字节数
     */
    void appendPcmData(byte[] data, int offset, int length);

    /**
     * 声明当前 PCM 输入已经结束。
     *
     * <p>调用后表示本次会话不会再收到新的 PCM 数据。播放器应继续把已排队的数据播放完，待内部
     * 队列真正清空后再进入完成态。
     */
    void completePcmStream();

    /**
     * 立即取消当前 PCM 会话。
     *
     * <p>取消是破坏性操作：实现层必须尽快停止播放、清空待播队列，并将本次流视为“中止”而非
     * “自然播完结束”。
     */
    void cancelPcmStream();

    /**
     * 返回播放器当前是否处于 PCM 流式模式。
     *
     * <p>该状态描述的是“控制面当前路由到哪条播放链路”，而不是瞬时的播放/暂停状态。即使流
     * 已完成，只要尚未切回其他模式或被显式取消，仍可视为 PCM 模式。
     *
     * @return {@code true} 表示当前由 PCM 流式链路接管控制，{@code false} 表示不是
     */
    boolean isPcmStreaming();

    /**
     * 返回当前等待播放的 PCM 预计时长。
     *
     * <p>该值用于调试、上游限流和诊断展示，本质上是根据排队字节数与当前 PCM 格式推导出的
     * 近似值，不保证达到采样级精度。
     *
     * @return 当前待播 PCM 的预计时长，单位毫秒
     */
    long getQueuedPcmDurationMs();
}
