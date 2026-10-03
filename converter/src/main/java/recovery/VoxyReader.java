package recovery;

import org.rocksdb.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import net.minecraft.nbt.*;

public final class VoxyReader implements AutoCloseable {
    private final DBOptions options=new DBOptions().setCreateIfMissing(false).setCreateMissingColumnFamilies(false);
    private final List<ColumnFamilyHandle> handles=new ArrayList<>();
    private final List<ColumnFamilyDescriptor> descriptors;
    private final RocksDB db;
    private final ColumnFamilyHandle sections, mappings;
    private final LinkedHashMap<Long,long[]> cache=new LinkedHashMap<>(64,.75f,true);
    public final Map<Integer,CompoundTag> states=new TreeMap<>();
    public final Map<Integer,String> biomes=new TreeMap<>();
    public long missingSections, decodedSections;
    static { RocksDB.loadLibrary(); }
    public VoxyReader(Path directory) throws Exception {
        List<byte[]> names;
        try(Options list=new Options()) { names=RocksDB.listColumnFamilies(list,directory.toString()); }
        descriptors=names.stream().map(ColumnFamilyDescriptor::new).toList();
        db=RocksDB.openReadOnly(options,directory.toString(),descriptors,handles);
        sections=family(names,"world_sections"); mappings=family(names,"id_mappings");
        CompoundTag air=new CompoundTag(); air.putString("Name","minecraft:air"); states.put(0,air);
        try(RocksIterator it=db.newIterator(mappings)) {
            for(it.seekToFirst();it.isValid();it.next()) {
                if(it.key().length!=4) throw new IOException("Invalid mapping key");
                int key=ByteBuffer.wrap(it.key()).getInt(), id=key&0x3fffffff, type=key>>>30;
                CompoundTag tag=NbtIo.readCompressed(new ByteArrayInputStream(it.value()),NbtAccounter.create(1<<20));
                if(tag.getIntOr("id",-1)!=id) throw new IOException("Mapping ID mismatch");
                if(type==1) { if(id>0xfffff) throw new IOException("Block ID overflow"); states.put(id,tag.getCompound("block_state").orElseThrow()); }
                else if(type==2) { if(id>511) throw new IOException("Biome ID overflow"); biomes.put(id,tag.getString("biome_id").orElseThrow()); }
                else throw new IOException("Unknown mapping type");
            }
            it.status();
        }
    }
    private ColumnFamilyHandle family(List<byte[]> names,String name) {
        for(int i=0;i<names.size();i++) if(new String(names.get(i),StandardCharsets.UTF_8).equals(name)) return handles.get(i);
        throw new IllegalArgumentException("Missing column family "+name);
    }
    public long[] section(int x,int y,int z) throws Exception {
        long key=SectionDecoder.key(x,y,z);
        if(cache.containsKey(key)) return cache.get(key);
        byte[] compressed=db.get(sections,ByteBuffer.allocate(8).putLong(key).array());
        long[] result=compressed==null?null:SectionDecoder.decode(key,compressed);
        if(result==null) missingSections++; else {
            decodedSections++;
            for(long v:result) if(!states.containsKey(SectionDecoder.block(v)) || (SectionDecoder.block(v)!=0 && !biomes.containsKey(SectionDecoder.biome(v)))) throw new IOException("Unmapped voxel in section "+key);
        }
        cache.put(key,result); if(cache.size()>64) cache.remove(cache.keySet().iterator().next());
        return result;
    }
    public void close() { for(var h:handles) h.close(); db.close(); for(var d:descriptors)d.getOptions().close(); options.close(); }
}
