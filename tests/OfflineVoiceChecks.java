import com.example.towncrierbd.utils.PcmAudio;
import com.example.towncrierbd.utils.WavePcmReader;
import com.example.towncrierbd.utils.VoiceKeywordMatcher;
import com.example.towncrierbd.utils.AudioItemSelection;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OfflineVoiceChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static void near(float value,float expected,String label){check(Math.abs(value-expected)<.00005,label);}
    static void rejects(Runnable r,String label){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}check(failed,label);}
    public static void main(String[] args)throws Exception{
        ByteBuffer mono=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        mono.putShort(Short.MIN_VALUE).putShort((short)0).putShort(Short.MAX_VALUE).putShort((short)16384).flip();
        float[] samples=PcmAudio.toMono(mono,1,false);check(samples.length==4,"Preserve frame count");
        near(samples[0],-1,"Negative full scale");near(samples[1],0,"Silence");near(samples[2],32767/32768f,"Positive full scale");near(samples[3],.5f,"PCM normalization");
        check(mono.position()==0,"Do not change caller position");
        ByteBuffer stereo=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);stereo.putShort((short)16384).putShort((short)-16384).putShort((short)16384).putShort((short)0).flip();
        samples=PcmAudio.toMono(stereo,2,false);near(samples[0],0,"Opposite stereo phases");near(samples[1],.25f,"Stereo average");
        ByteBuffer floats=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);floats.putFloat(Float.NaN).putFloat(Float.POSITIVE_INFINITY).putFloat(2).putFloat(-2).putFloat(.25f).flip();
        samples=PcmAudio.toMono(floats,1,true);near(samples[0],0,"Sanitize NaN");near(samples[1],0,"Sanitize Infinity");near(samples[2],1,"Positive clipping");near(samples[3],-1,"Negative clipping");near(samples[4],.25f,"Float PCM");
        ByteBuffer offset=ByteBuffer.wrap(new byte[]{99,99,0,64,99,99});offset.position(2);offset.limit(4);near(PcmAudio.toMono(offset,1,false)[0],.5f,"Decoder offsets");
        rejects(()->PcmAudio.toMono(ByteBuffer.allocate(3),2,false),"Partial frame rejected");rejects(()->PcmAudio.toMono(ByteBuffer.allocate(4),0,false),"Zero channels rejected");
        rejects(()->PcmAudio.validateFormat(0,1),"Invalid rate rejected");rejects(()->PcmAudio.validateFormat(16000,9),"Invalid channels rejected");
        for(int rate:new int[]{8000,16000,22050,44100,48000,96000})PcmAudio.validateFormat(rate,2);check(true,"Common file rates accepted");
        for(String transcript:new String[]{"আলু পেঁয়াজ রসুন","POTATOES ONIONS GARLIC"}){
            VoiceKeywordMatcher.MatchResult result=VoiceKeywordMatcher.match(transcript);check(result.matchedProducts.containsAll(Arrays.asList("Potato","Onion","Garlic")),"Model-language output matches catalog");
            Set<String> cats=new LinkedHashSet<>(),items=new LinkedHashSet<>();items.add("Tomato");AudioItemSelection ownership=new AudioItemSelection();ownership.apply(cats,items,result.matchedCategories,result.matchedProducts);ownership.clear(cats,items);check(items.equals(Collections.singleton("Tomato")),"Delete retains manual choice");
        }
        Path root=Paths.get(args.length==0?".":args[0]);check(PrepareOfflineVoice.manifest(root.resolve("tools/offline-voice-manifest.tsv")).size()==9,"Both language packs pinned");
        Path tmp=Files.createTempDirectory("offline-voice-tests");
        try{
            Path file=tmp.resolve("sample");Files.write(file,"abc".getBytes(StandardCharsets.UTF_8));
            PrepareOfflineVoice.Entry entry=new PrepareOfflineVoice.Entry("runtime/test",3,"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad","https://example.test");check(PrepareOfflineVoice.valid(file,entry),"SHA256 verification");
            Files.write(file,"abd".getBytes(StandardCharsets.UTF_8));check(!PrepareOfflineVoice.valid(file,entry),"Same-size corruption rejected");Files.write(file,"a".getBytes(StandardCharsets.UTF_8));check(!PrepareOfflineVoice.valid(file,entry),"Truncation rejected");
            PrepareOfflineVoice.Entry vocab=new PrepareOfflineVoice.Entry("assets/voice/bn/tokens.txt",3,"tokens","https://example.test");
            Files.write(file,"<blk> 0\nআলু 1\n▁ 2\n".getBytes(StandardCharsets.UTF_8));check(PrepareOfflineVoice.valid(file,vocab),"Unicode vocabulary accepted");
            Files.write(file,"<blk> 0\nআলু 1\n▁ 1\n".getBytes(StandardCharsets.UTF_8));check(!PrepareOfflineVoice.valid(file,vocab),"Duplicate IDs rejected");Files.write(file,"<html>Gateway error</html>".getBytes(StandardCharsets.UTF_8));check(!PrepareOfflineVoice.valid(file,vocab),"Error pages rejected");
        }finally{Files.deleteIfExists(tmp.resolve("sample"));Files.delete(tmp);}
        Path wav=Files.createTempFile("voice-pcm",".wav");
        try{
            ByteBuffer w=ByteBuffer.allocate(70).order(ByteOrder.LITTLE_ENDIAN);
            w.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(62).put("WAVE".getBytes(StandardCharsets.US_ASCII));w.put("JUNK".getBytes(StandardCharsets.US_ASCII)).putInt(1).put((byte)7).put((byte)0);
            w.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)2).putInt(44100).putInt(176400).putShort((short)4).putShort((short)16);
            w.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(16);for(int i=0;i<4;i++)w.putShort((short)16384).putShort((short)0);
            Files.write(wav,w.array());check(WavePcmReader.isWave(wav.toString()),"Recognize RIFF content");
            try(WavePcmReader reader=new WavePcmReader(wav.toString())){
                check(reader.sampleRate==44100&&reader.channels==2&&reader.totalFrames==4,"Read padded WAVE header");float[] frames=reader.read();check(frames.length==4,"Preserve short WAV");near(frames[3],.25f,"WAV stereo downmix");check(reader.read()==null,"Stop at data boundary");
            }
            Files.write(wav,Arrays.copyOf(w.array(),69));boolean rejected=false;try(WavePcmReader ignored=new WavePcmReader(wav.toString())){}catch(java.io.IOException e){rejected=true;}check(rejected,"Truncated WAVE rejected");
        }finally{Files.delete(wav);}
        System.out.println("Passed "+checks+" offline voice checks.");
    }
}
