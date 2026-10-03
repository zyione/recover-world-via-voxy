package recovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import net.minecraft.nbt.NbtUtils;

class NativeCropTest {
    @TempDir Path output;
    @Test void partialXYZCropPreservesSourceStatesAcrossNegativeChunkBoundaries() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(Path.of("config/poc.json")),"Optional private fixture is not present");
        Config config=Config.read(Path.of("config/poc.json"));
        Bounds crop=new Bounds(-221,-197,70,86,-1150,-1128);
        long[] raw=SectionDecoder.decode(13510789486870416L,Files.readAllBytes(Path.of("../research/evidence/priority-area/sample-lod0.zstd")));
        long expectedNonAir=0;
        try(VoxyReader source=new VoxyReader(config.database())) {
            MinecraftWriter writer=new MinecraftWriter(source);
            for(int cz=-72;cz<=-71;cz++)for(int cx=-14;cx<=-13;cx++) {
                var chunk=writer.makeChunk(source,crop,cx,cz);
                for(var section:chunk.sectionData())for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                    int wx=cx*16+x,wy=section.y()*16+y,wz=cz*16+z;
                    var actual=section.chunkSection().getBlockState(x,y,z);
                    if(!crop.contains(wx,wy,wz))assertTrue(actual.isAir());
                    else {
                        // Independent fixture coordinates: original source section starts at (-224,64,-1152).
                        int index=(wx+224)+(wz+1152)*32+(wy-64)*1024;
                        int id=SectionDecoder.block(raw[index]);
                        assertEquals(source.states.get(id),NbtUtils.writeBlockState(actual));
                        if(id!=0)expectedNonAir++;
                    }
                }
                writer.writeAndVerify(output,chunk);
            }
            assertEquals(expectedNonAir,writer.recoveredNonAir);
            assertEquals(0,writer.unknownBlocks);assertEquals(4,writer.verifiedChunks);
            WorldMetadata.write(output,crop,writer.registries);
            assertTrue(Files.size(output.resolve("level.dat"))>0);
        }
    }
}
