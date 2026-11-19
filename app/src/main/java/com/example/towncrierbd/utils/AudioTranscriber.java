package com.example.towncrierbd.utils;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Actual recorded/imported audio is recognized on the phone; no network service is used. */
public final class AudioTranscriber {
    private AudioTranscriber() {}
    // Serial work prevents concurrent model allocations when audio/language changes quickly.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r,"offline-post-audio"));
    public interface Callback {
        void complete(String text);
        void failed(Throwable error);
        default void progress(int percent) {}
    }
    public static final class Job {
        private volatile boolean cancelled;
        private Future<?> future;
        public void cancel() {cancelled=true;if(future!=null)future.cancel(true);}
        private void check() throws InterruptedException {
            if(cancelled || Thread.currentThread().isInterrupted())throw new InterruptedException("Audio recognition cancelled");
        }
    }
    private interface Progress {void update(int percent);}
    public static Job transcribe(Context context, String path, String language, Callback callback) {
        Context app=context.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());Job job=new Job();
        job.future=WORKER.submit(()->{
            try {
                job.check();
                Progress progress=percent->main.post(()->{if(!job.cancelled)callback.progress(percent);});
                progress.update(-1);
                String text;
                try(OfflineVoiceModel model=new OfflineVoiceModel(app.getAssets(),language,job::check)) {
                    job.check();
                    if(WavePcmReader.isWave(path))readWave(path,model,job,progress);
                    else readEncodedAudio(path,model,job,progress);
                    text=model.finish();
                }
                job.check();main.post(()->{if(!job.cancelled)callback.complete(text);});
            } catch(Exception | LinkageError | OutOfMemoryError error) {
                main.post(()->{if(!job.cancelled)callback.failed(error);});
            }
        });
        return job;
    }
    private static void readWave(String path, OfflineVoiceModel model, Job job, Progress progress) throws Exception {
        try(WavePcmReader wave=new WavePcmReader(path)) {
            int previous=-1;float[] samples;
            while((samples=wave.read())!=null) {
                job.check();model.accept(samples,wave.sampleRate);
                int percent=wave.percent();if(percent!=previous){progress.update(percent);previous=percent;}
            }
        }
    }
    private static void readEncodedAudio(String path, OfflineVoiceModel model, Job job, Progress progress) throws Exception {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try {
            extractor.setDataSource(path);MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat candidate=extractor.getTrackFormat(i);String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){format=candidate;extractor.selectTrack(i);break;}
            }
            if(format==null)throw new IllegalArgumentException("No supported audio track");
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            PcmAudio.validateFormat(rate,channels);
            long durationUs=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION):0;
            format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            boolean inputDone=false,outputDone=false;int idle=0,lastPercent=-1,encoding=AudioFormat.ENCODING_PCM_16BIT;
            while(!outputDone) {
                job.check();boolean moved=false;
                if(!inputDone) {
                    int index=codec.dequeueInputBuffer(10000);
                    if(index>=0) {
                        ByteBuffer input=codec.getInputBuffer(index);
                        if(input==null)throw new IllegalStateException("Audio decoder unavailable");
                        input.clear();int size=extractor.readSampleData(input,0);
                        if(size<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                        else{codec.queueInputBuffer(index,0,size,extractor.getSampleTime(),0);extractor.advance();}
                        moved=true;
                    }
                }
                int output=codec.dequeueOutputBuffer(info,10000);
                if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat decoded=codec.getOutputFormat();rate=decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    encoding=decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)?decoded.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                    PcmAudio.validateFormat(rate,channels);
                    if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&encoding!=AudioFormat.ENCODING_PCM_FLOAT)throw new IllegalArgumentException("Unsupported PCM encoding");
                    moved=true;
                } else if(output>=0) {
                    try {
                        ByteBuffer data=codec.getOutputBuffer(output);
                        if(data!=null&&info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) {
                            data.position(info.offset);data.limit(info.offset+info.size);
                            int frameBytes=channels*(encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2);
                            while(data.hasRemaining()) {
                                job.check();int count=Math.min(data.remaining(),frameBytes*4096);
                                ByteBuffer block=data.slice();block.limit(count);
                                float[] samples=PcmAudio.toMono(block,channels,encoding==AudioFormat.ENCODING_PCM_FLOAT);
                                data.position(data.position()+count);model.accept(samples,rate);
                            }
                        }
                        outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                        int percent=durationUs>0?(int)Math.min(99,Math.max(0,info.presentationTimeUs*100/durationUs)):0;
                        if(percent!=lastPercent){progress.update(percent);lastPercent=percent;}
                    } finally {codec.releaseOutputBuffer(output,false);}
                    moved=true;
                }
                idle=moved?0:idle+1;if(idle>1500)throw new IllegalStateException("Audio decoding timed out");
            }
        } finally {
            try {if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}}
            finally {extractor.release();}
        }
    }
    public static String errorMessage(Throwable error, boolean english) {
        if(error instanceof OutOfMemoryError)return english?"Not enough free memory. Close other apps, then tap to retry. Your audio is saved.":"ফোনের মেমোরি কম। অন্য অ্যাপ বন্ধ করে আবার চাপুন। অডিও রাখা আছে।";
        if(error instanceof LinkageError||error instanceof OfflineVoiceModel.MissingModelException)return english?"Offline voice files are missing or incompatible. Install the new APK built with offline voice. Your audio is saved.":"অফলাইন ভয়েস ফাইল পাওয়া যায়নি বা এই ফোনে চলছে না। অফলাইন ভয়েসসহ নতুন APK ইনস্টল করুন। অডিও রাখা আছে।";
        return english?"Could not read this audio. Tap to retry, check its language, or choose another recording.":"এই অডিও পড়া যায়নি। আবার চেষ্টা করতে চাপুন, ভাষা দেখুন অথবা অন্য রেকর্ডিং বাছুন।";
    }
}
