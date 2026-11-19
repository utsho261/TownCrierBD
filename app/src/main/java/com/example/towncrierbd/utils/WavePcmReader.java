package com.example.towncrierbd.utils;

import java.io.*;
import java.nio.ByteBuffer;

/** PCM16 / float32 RIFF-WAVE reader for phones without an audio/raw MediaCodec decoder. */
public final class WavePcmReader implements AutoCloseable {
    private final RandomAccessFile file;
    public final int sampleRate, channels;
    public final boolean floatingPoint;
    public final long totalFrames;
    private final long dataEnd;
    private long framesRead;
    public static boolean isWave(String path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            if (file.length() < 12 || file.readInt() != 0x52494646) return false;
            file.skipBytes(4); return file.readInt() == 0x57415645;
        }
    }
    public WavePcmReader(String path) throws IOException {
        file = new RandomAccessFile(path, "r");
        try {
            if (file.length() < 12 || file.readInt() != 0x52494646) throw new IOException("Not RIFF-WAVE");
            long riffEnd = uint32() + 8;
            if (file.readInt() != 0x57415645 || riffEnd > file.length() || riffEnd < 12) throw new IOException("Invalid WAVE length");
            int rate=0, channelCount=0, encoding=0, bits=0, alignment=0;
            long start=-1, bytes=0;
            while (file.getFilePointer()+8 <= riffEnd) {
                int id=file.readInt();long length=uint32(), offset=file.getFilePointer();
                if (length > riffEnd-offset) throw new IOException("Truncated WAVE chunk");
                if (id==0x666d7420) {
                    if(length<16)throw new IOException("Invalid WAVE format");
                    encoding=uint16();channelCount=uint16();long hz=uint32();uint32();alignment=uint16();bits=uint16();
                    if(hz>Integer.MAX_VALUE)throw new IOException("Invalid WAVE rate");rate=(int)hz;
                    if(encoding==0xfffe) {
                        if(length<40)throw new IOException("Invalid extended WAVE format");
                        int extra=uint16(),validBits=uint16();uint32();long subformat=uint32();
                        byte[] guid=new byte[12];file.readFully(guid);
                        byte[] expected={0,0,0x10,0,(byte)0x80,0,0,(byte)0xaa,0,0x38,(byte)0x9b,0x71};
                        if(extra<22 || validBits!=bits || !java.util.Arrays.equals(guid,expected) || subformat>3)throw new IOException("Unsupported extended WAVE");
                        encoding=(int)subformat;
                    }
                } else if(id==0x64617461 && start<0) {start=offset;bytes=length;}
                file.seek(offset+length+(length&1));
            }
            PcmAudio.validateFormat(rate,channelCount);
            if(!((encoding==1&&bits==16)||(encoding==3&&bits==32)) || alignment!=channelCount*(bits/8))
                throw new IOException("Use PCM16 or float32 WAVE audio");
            if(start<0 || bytes==0 || bytes%alignment!=0)throw new IOException("Empty or incomplete WAVE audio");
            sampleRate=rate;channels=channelCount;floatingPoint=encoding==3;
            totalFrames=bytes/alignment;dataEnd=start+bytes;file.seek(start);
        } catch(IOException | RuntimeException e) {file.close();throw e;}
    }
    public float[] read() throws IOException {
        long remaining=dataEnd-file.getFilePointer();if(remaining==0)return null;
        int frameBytes=channels*(floatingPoint?4:2);
        int count=(int)Math.min(remaining,4096L*frameBytes);
        byte[] bytes=new byte[count];file.readFully(bytes);framesRead+=count/frameBytes;
        return PcmAudio.toMono(ByteBuffer.wrap(bytes),channels,floatingPoint);
    }
    public int percent(){return (int)Math.min(99,framesRead*100/totalFrames);}
    private long uint32() throws IOException {return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));}
    private int uint16() throws IOException {return Short.toUnsignedInt(Short.reverseBytes(file.readShort()));}
    @Override public void close() throws IOException {file.close();}
}
