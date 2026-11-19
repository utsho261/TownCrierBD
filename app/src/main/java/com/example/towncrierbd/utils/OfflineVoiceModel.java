package com.example.towncrierbd.utils;

import android.content.res.AssetManager;
import com.k2fsa.sherpa.onnx.*;
import java.io.IOException;
import java.io.InputStream;

/** Pinned Apache-2.0 models bundled by prepareOfflineVoice; Java calls the upstream Kotlin API. */
final class OfflineVoiceModel implements AutoCloseable {
    interface Cancellation { void check() throws InterruptedException; }
    static final class MissingModelException extends IOException {
        MissingModelException(IOException cause) { super("Offline speech model is not in this APK", cause); }
    }
    private final OnlineRecognizer recognizer;
    private final OnlineStream stream;
    private final StringBuilder transcript = new StringBuilder();
    private final Cancellation cancellation;
    private int lastRate = 16000;
    OfflineVoiceModel(AssetManager assets, String language, Cancellation cancellation) throws IOException {
        this.cancellation = cancellation;
        boolean bengali = "bn-BD".equals(language);
        if (!bengali && !"en-US".equals(language)) throw new IllegalArgumentException("Unsupported audio language");
        String dir = bengali ? "voice/bn/" : "voice/en/";
        for (String name : new String[]{"encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt"}) {
            try (InputStream ignored = assets.open(dir + name)) {}
            catch (IOException e) { throw new MissingModelException(e); }
        }
        OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
        transducer.setEncoder(dir + "encoder.onnx");transducer.setDecoder(dir + "decoder.onnx");transducer.setJoiner(dir + "joiner.onnx");
        OnlineModelConfig model = new OnlineModelConfig();model.setTransducer(transducer);
        model.setTokens(dir + "tokens.txt");model.setNumThreads(2);model.setProvider("cpu");
        model.setModelType(bengali ? "zipformer2" : "zipformer");
        FeatureConfig feature = new FeatureConfig();feature.setSampleRate(16000);feature.setFeatureDim(80);
        feature.setDither(bengali ? 0.00003f : 0f);
        OnlineRecognizerConfig config = new OnlineRecognizerConfig();config.setFeatConfig(feature);config.setModelConfig(model);
        config.setDecodingMethod("modified_beam_search");config.setMaxActivePaths(4);config.setEnableEndpoint(true);
        recognizer = new OnlineRecognizer(assets, config);
        try { stream = recognizer.createStream(""); }
        catch (RuntimeException | LinkageError | OutOfMemoryError e) { recognizer.release();throw e; }
    }
    void accept(float[] samples, int rate) throws InterruptedException {
        cancellation.check();lastRate = rate;
        // Upstream resamples to 16 kHz internally and retains continuous state across buffers.
        stream.acceptWaveform(samples, rate);drain();
        if (recognizer.isEndpoint(stream)) {appendResult();recognizer.reset(stream);}
    }
    private void drain() throws InterruptedException {
        while (recognizer.isReady(stream)) { cancellation.check();recognizer.decode(stream); }
    }
    private void appendResult() {
        String text = recognizer.getResult(stream).getText();
        if (text != null && !text.trim().isEmpty()) transcript.append(text.trim()).append(' ');
    }
    String finish() throws InterruptedException {
        cancellation.check();
        stream.acceptWaveform(new float[lastRate], lastRate); // One second of padding flushes the final word.
        stream.inputFinished();drain();appendResult();return transcript.toString().trim();
    }
    @Override public void close() {try {stream.release();} finally {recognizer.release();}}
}
