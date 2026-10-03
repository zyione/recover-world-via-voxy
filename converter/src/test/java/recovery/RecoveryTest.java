package recovery;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.nio.file.*;
import com.github.luben.zstd.Zstd;

class RecoveryTest {
    @Test void inclusiveNegativeCoordinates() {
        Bounds b=new Bounds(-2217,1783,56,319,-3140,860);
        assertEquals(63001,b.chunkCount());assertTrue(b.contains(-2217,56,-3140));assertFalse(b.contains(-2217,55,-3140));
        assertEquals(4,new Bounds(-224,-193,64,95,-1152,-1121).chunkCount());
        assertThrows(IllegalArgumentException.class,()->new Bounds(1,0,56,319,0,1));
        assertThrows(IllegalArgumentException.class,()->new Bounds(0,1,56,565,0,1));
    }
    @Test void signedSectionKeyMatchesOriginalSerializerFixture() { assertEquals(13510789486870416L,SectionDecoder.key(-7,2,-36)); }
    @Test void realSourceSectionIsDecodedWithoutLoss() throws Exception {
        Path dir=Path.of("../research/evidence/priority-area");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(dir.resolve("sample-lod0.zstd")),"Optional private fixture is not present");
        long key=13510789486870416L;
        long[] actual=SectionDecoder.decode(key,Files.readAllBytes(dir.resolve("sample-lod0.zstd")));
        assertEquals(32768,actual.length);
        assertEquals(8106,java.util.Arrays.stream(actual).filter(v->SectionDecoder.block(v)!=0).count());
        assertEquals(0,SectionDecoder.block(actual[7|(12<<5)|(22<<10)]));
        byte[] raw=Files.readAllBytes(dir.resolve("sample-lod0.raw"));
        assertArrayEquals(SectionDecoder.decodeRaw(key,raw,raw.length),actual);
    }
    @Test void malformedSectionsFailClosed() {
        long key=SectionDecoder.key(-1,-2,-3);byte[] valid=new byte[SectionDecoder.BASE+8];
        ByteBuffer b=ByteBuffer.wrap(valid).order(ByteOrder.LITTLE_ENDIAN);b.putLong(key).putLong(1);
        assertEquals(32768,SectionDecoder.decode(key,Zstd.compress(valid)).length);
        assertThrows(IllegalArgumentException.class,()->SectionDecoder.decode(key+16,Zstd.compress(valid)));
        b.putShort(16,(short)1);assertThrows(IllegalArgumentException.class,()->SectionDecoder.decode(key,Zstd.compress(valid)));
        b.putShort(16,(short)0);b.putLong(SectionDecoder.BASE,1);assertThrows(IllegalArgumentException.class,()->SectionDecoder.decode(key,Zstd.compress(valid)));
        assertThrows(IllegalArgumentException.class,()->SectionDecoder.decode(key,new byte[]{1,2,3}));
        assertThrows(IllegalArgumentException.class,()->SectionDecoder.decodeRaw(key,valid,16));
        b.putLong(8,0);assertThrows(IllegalArgumentException.class,()->SectionDecoder.decode(key,Zstd.compress(valid)));
    }
}
