package recovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.rocksdb.*;
import net.minecraft.nbt.*;
import com.github.luben.zstd.Zstd;
import com.google.gson.*;

class PublicWorkflowTest {
    @TempDir Path temp;
    @Test void sourceSelectionCopyConversionAndTamperDetection() throws Exception {
        RocksDB.loadLibrary();Path original=temp.resolve(".voxy");Path storage=original.resolve("saves/example/world/storage");Files.createDirectories(storage);
        var descriptors=List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY),new ColumnFamilyDescriptor("world_sections".getBytes()),new ColumnFamilyDescriptor("id_mappings".getBytes()));
        List<ColumnFamilyHandle> handles=new ArrayList<>();
        try(DBOptions options=new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);RocksDB db=RocksDB.open(options,storage.toString(),descriptors,handles)) {
            CompoundTag state=new CompoundTag();state.putString("Name","minecraft:stone");CompoundTag mapping=new CompoundTag();mapping.putInt("id",1);mapping.put("block_state",state);
            db.put(handles.get(2),ByteBuffer.allocate(4).putInt((1<<30)|1).array(),gzip(mapping));
            CompoundTag biome=new CompoundTag();biome.putInt("id",0);biome.putString("biome_id","minecraft:plains");
            db.put(handles.get(2),ByteBuffer.allocate(4).putInt(2<<30).array(),gzip(biome));
            byte[] raw=new byte[SectionDecoder.BASE+8];ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);b.putLong(0).putLong(1);b.putLong(SectionDecoder.BASE,1L<<27);
            db.put(handles.get(1),new byte[8],Zstd.compress(raw));
            for(var handle:handles)handle.close();
        }finally{for(var descriptor:descriptors)descriptor.getOptions().close();}
        String originalHash=SourceSnapshot.sha256(storage.resolve("CURRENT"));
        var prepared=SourceSnapshot.prepare(original,temp.resolve("job"),line->{});
        assertEquals(1,prepared.candidates().size());assertEquals("Overworld candidate",prepared.candidates().getFirst().dimension());
        JsonObject job=new JsonObject();job.addProperty("minecraftVersion","1.21.11");job.addProperty("originalEvidence",original.toString());job.addProperty("snapshot",prepared.snapshot().toString());job.addProperty("manifest",prepared.manifest().toString());job.addProperty("database",prepared.candidates().getFirst().relativePath());job.addProperty("output",temp.resolve("world").toString());job.addProperty("maxChunks",1);job.add("bounds",Config.JSON.toJsonTree(new Bounds(0,15,0,15,0,15)));
        Path config=temp.resolve("job.json");Files.writeString(config,Config.JSON.toJson(job));
        Main.run(config,line->{});
        JsonObject report=JsonParser.parseString(Files.readString(temp.resolve("world/recovery-report.json"))).getAsJsonObject();
        assertEquals(4096,report.get("recoveredNonAirBlocks").getAsInt());assertEquals(1,report.get("verifiedChunks").getAsInt());
        assertTrue(Files.exists(temp.resolve("world/level.dat")));assertFalse(Files.exists(temp.resolve("world/INCOMPLETE.txt")));
        assertEquals(originalHash,SourceSnapshot.sha256(storage.resolve("CURRENT")));
        assertThrows(IllegalArgumentException.class,()->Main.run(config,line->{}));
        Config checked=Config.read(config);Files.writeString(checked.database().resolve("CURRENT"),"tampered");assertThrows(IOException.class,checked::verifySnapshot);
    }
    @Test void rejectsMissingDatabasesAndSourceOverlap()throws Exception {
        Path empty=Files.createDirectory(temp.resolve("empty"));
        assertThrows(IOException.class,()->SourceSnapshot.prepare(empty,temp.resolve("job"),line->{}));
        assertThrows(IOException.class,()->SourceSnapshot.prepare(empty,empty.resolve("job"),line->{}));
        assertEquals("Nether (unsupported)",SourceSnapshot.dimension(Set.of("minecraft:nether_wastes")));
        assertEquals("End (unsupported)",SourceSnapshot.dimension(Set.of("minecraft:the_end")));
    }
    private byte[] gzip(CompoundTag tag)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();NbtIo.writeCompressed(tag,out);return out.toByteArray();}
}
