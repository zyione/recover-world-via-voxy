package recovery;

import java.nio.*;
import com.github.luben.zstd.Zstd;

/** Pinned Voxy SaveLoadSystem3 format; never calls its destructive storage loader. */
public final class SectionDecoder {
    public static final int BASE = 65552, MAX_BYTES = BASE + 32768 * 8;
    public static long key(int x, int y, int z) {
        return ((long)(y&255)<<52) | ((long)(z&0xffffff)<<28) | ((long)(x&0xffffff)<<4);
    }
    public static long[] decode(long expectedKey, byte[] compressed) {
        byte[] raw = new byte[MAX_BYTES];
        long size;
        try { size = Zstd.decompress(raw, compressed); }
        catch(com.github.luben.zstd.ZstdException e) { throw new IllegalArgumentException("Invalid or oversized Zstd section",e); }
        if (Zstd.isError(size)) throw new IllegalArgumentException("Invalid Zstd frame: " + Zstd.getErrorName(size));
        return decodeRaw(expectedKey, raw, Math.toIntExact(size));
    }
    static long[] decodeRaw(long expectedKey, byte[] raw, int length) {
        if(length < BASE || length > MAX_BYTES || length > raw.length) throw new IllegalArgumentException("Invalid section size");
        ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        if(b.getLong()!=expectedKey) throw new IllegalArgumentException("Section key mismatch");
        long meta=b.getLong(); int count=(int)(meta&65535);
        if(count<1 || count>32768 || (meta>>>24)!=0 || length!=BASE+count*8) throw new IllegalArgumentException("Invalid section metadata/length");
        long[] lut=new long[count];
        for(int i=0;i<count;i++) { lut[i]=b.getLong(BASE+i*8); if((lut[i]&0x7ffffffL)!=0) throw new IllegalArgumentException("Unknown voxel flags"); }
        long[] voxels=new long[32768];
        for(int i=0;i<voxels.length;i++) { int index=Short.toUnsignedInt(b.getShort(16+i*2)); if(index>=count) throw new IllegalArgumentException("Invalid palette index"); voxels[i]=lut[index]; }
        return voxels;
    }
    public static int block(long v) { return (int)((v>>>27)&0xfffff); }
    public static int biome(long v) { return (int)((v>>>47)&511); }
}
