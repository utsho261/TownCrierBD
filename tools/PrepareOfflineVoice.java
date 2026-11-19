import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.util.*;

/** Public build-time downloads only. No uploaded audio, API account, key or billing. */
public class PrepareOfflineVoice {
    static final class Entry {
        final String path,hash,url;final long size;
        Entry(String path,long size,String hash,String url){this.path=path;this.size=size;this.hash=hash;this.url=url;}
        long maxBytes(){return "tokens".equals(hash)?65536:size;}
    }
    public static void main(String[] args) throws Exception {
        Path project=Paths.get(args.length==0?".":args[0]).toAbsolutePath().normalize();
        boolean offline=Arrays.asList(args).contains("--offline");
        List<Entry> entries=manifest(project.resolve("tools/offline-voice-manifest.tsv"));
        if(Arrays.asList(args).contains("--verify-manifest")){System.out.println("Offline voice manifest valid: "+entries.size()+" pinned files.");return;}
        Path cache=project.resolve(".offline-voice");Files.createDirectories(cache);
        try(FileChannel channel=FileChannel.open(cache.resolve("prepare.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.lock()) {
            for(Entry entry:entries) {
                Path target=cache.resolve(entry.path);boolean ready;
                try{ready=valid(target,entry);}catch(IOException corrupt){ready=false;}
                if(ready)continue;
                if(offline)throw new IOException("Offline voice file missing/invalid: "+entry.path+". Build once with Internet and Gradle Offline mode OFF. No billing/account/key is needed.");
                Files.createDirectories(target.getParent());Path partial=target.resolveSibling(target.getFileName()+".part");
                System.out.println("Downloading free offline voice: "+entry.path);
                try {
                    download(entry,partial);
                    if(!valid(partial,entry))throw new IOException("Integrity check failed: "+entry.path);
                    try{Files.move(partial,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                    catch(AtomicMoveNotSupportedException e){Files.move(partial,target,StandardCopyOption.REPLACE_EXISTING);}
                } finally{Files.deleteIfExists(partial);}
            }
        }
        System.out.println("Offline Bengali + English voice ready. Models will be bundled in the APK; no speech server is needed.");
    }
    static List<Entry> manifest(Path path) throws IOException {
        List<Entry> entries=new ArrayList<>();Set<String> paths=new HashSet<>();
        for(String line:Files.readAllLines(path,StandardCharsets.UTF_8)) {
            if(line.isBlank()||line.startsWith("#"))continue;
            String[] cells=line.split("\t",-1);if(cells.length!=4)throw new IOException("Invalid manifest row");
            Entry e;try{e=new Entry(cells[0],Long.parseLong(cells[1]),cells[2],cells[3]);}catch(NumberFormatException ex){throw new IOException("Invalid model size",ex);}
            if(!e.path.matches("(?:runtime|assets/voice/(?:bn|en))/[A-Za-z0-9._-]+")||!paths.add(e.path)||e.size<=0||e.size>200_000_000||!(e.hash.matches("[a-f0-9]{64}")||e.hash.equals("tokens")))throw new IOException("Invalid manifest entry: "+e.path);
            boolean github=e.url.equals("https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar");
            boolean hf=e.url.matches("https://huggingface.co/(?:alphacep/vosk-model-small-streaming-bn|csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17)/resolve/[a-f0-9]{40}/[A-Za-z0-9._/-]+")&&!e.url.contains("/../");
            if(!github&&!hf)throw new IOException("Pin an official HTTPS release/commit URL");
            if(e.hash.equals("tokens")&&(!e.path.endsWith("tokens.txt")||e.size>10000))throw new IOException("Invalid vocabulary entry");
            entries.add(e);
        }
        if(entries.size()!=9)throw new IOException("Expected runtime and both language packs");
        for(String language:new String[]{"bn","en"})for(String name:new String[]{"encoder.onnx","decoder.onnx","joiner.onnx","tokens.txt"})
            if(!paths.contains("assets/voice/"+language+"/"+name))throw new IOException("Incomplete language pack");
        return entries;
    }
    static boolean valid(Path file,Entry entry) throws IOException {
        if(!Files.isRegularFile(file)||Files.size(file)>entry.maxBytes()||Files.size(file)==0)return false;
        if("tokens".equals(entry.hash)) {
            // Commit-pinned vocabulary: reject HTML, truncated files and duplicate/missing IDs.
            List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
            if(lines.size()!=entry.size||!lines.get(0).equals("<blk> 0"))return false;
            for(int i=0;i<lines.size();i++){
                String line=lines.get(i);int split=line.lastIndexOf(' ');
                if(split<=0||!line.substring(split+1).equals(String.valueOf(i)))return false;
            }
            return true;
        }
        return Files.size(file)==entry.size&&digest(file).equals(entry.hash);
    }
    static String digest(Path file) throws IOException {
        try {
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)sha.update(buffer,0,count);}
            StringBuilder hex=new StringBuilder();for(byte b:sha.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
    static void download(Entry entry,Path output) throws IOException {
        URL url=new URL(entry.url);
        for(int redirects=0;redirects<8;redirects++) {
            if(!url.getProtocol().equals("https"))throw new IOException("Only HTTPS downloads are allowed");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(30000);connection.setReadTimeout(60000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","TownCrierBD-OfflineVoice-Build/1.0");
            try {
                int code=connection.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Missing redirect");url=new URL(url,location);continue;}
                if(code!=200)throw new IOException("Download failed (HTTP "+code+"): "+entry.path+". Check Internet and rebuild; no billing setup needed.");
                try(InputStream in=connection.getInputStream();OutputStream out=Files.newOutputStream(output)){
                    byte[] buffer=new byte[65536];int count;long total=0,reported=0;
                    while((count=in.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download cancelled");
                        total+=count;if(total>entry.maxBytes())throw new IOException("Unexpected download size: "+entry.path);out.write(buffer,0,count);
                        if(total-reported>=10_000_000){System.out.println("  "+(total/1_000_000)+" MB");reported=total;}
                    }
                }
                return;
            }finally{connection.disconnect();}
        }
        throw new IOException("Too many model download redirects");
    }
}
