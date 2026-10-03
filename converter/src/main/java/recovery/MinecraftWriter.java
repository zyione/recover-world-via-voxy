package recovery;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import net.minecraft.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.ticks.ProtoChunkTicks;
import it.unimi.dsi.fastutil.shorts.ShortList;

public final class MinecraftWriter {
    final HolderLookup.Provider registries;
    final PalettedContainerFactory factory;
    final Map<Integer,BlockState> blocks=new HashMap<>();
    final Map<Integer,Holder<Biome>> biomes=new HashMap<>();
    public long recoveredNonAir, storedAir, unknownBlocks, croppedBlocks, syntheticBiomeCells, conflictingBiomeCells, emptyBlockEntities;
    public long verifiedChunks;
    public final List<Map<String,Object>> unresolvedBlockEntities=new ArrayList<>();
    public MinecraftWriter(VoxyReader source) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        registries=VanillaRegistries.createLookup();
        var lookup=registries.lookupOrThrow(Registries.BIOME);
        var bs=Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        IdMapper<Holder<Biome>> biomeIds=new IdMapper<>(); lookup.listElements().forEach(biomeIds::add);
        var bio=Strategy.createForBiomes(biomeIds);
        com.mojang.serialization.Codec<Holder<Biome>> biomeCodec=com.mojang.serialization.Codec.STRING.comapFlatMap(name -> {
            try { return com.mojang.serialization.DataResult.success(lookup.getOrThrow(ResourceKey.create(Registries.BIOME,Identifier.parse(name)))); }
            catch(Exception e) { return com.mojang.serialization.DataResult.error(()->"Unknown biome: "+name); }
        }, holder->holder.unwrapKey().orElseThrow().identifier().toString());
        var air=Blocks.AIR.defaultBlockState(); var plains=lookup.getOrThrow(Biomes.PLAINS);
        factory=new PalettedContainerFactory(bs,air,PalettedContainer.codecRW(BlockState.CODEC,bs,air),bio,plains,PalettedContainer.codecRO(biomeCodec,bio,plains));
        source.states.forEach((id,tag)->blocks.put(id,resolve(tag)));
        source.biomes.forEach((id,name)->biomes.put(id,lookup.getOrThrow(ResourceKey.create(Registries.BIOME,Identifier.parse(name)))));
    }
    static BlockState resolve(CompoundTag tag) {
        Identifier name=Identifier.parse(tag.getString("Name").orElseThrow());
        if(!BuiltInRegistries.BLOCK.containsKey(name)) throw new IllegalArgumentException("Unknown target block "+name);
        Block block=BuiltInRegistries.BLOCK.getValue(name); BlockState state=block.defaultBlockState();
        CompoundTag props=tag.getCompound("Properties").orElseGet(CompoundTag::new);
        if(props.size()!=state.getProperties().size()) throw new IllegalArgumentException("Incomplete block properties: "+tag);
        for(String key:props.keySet()) {
            Property<?> property=block.getStateDefinition().getProperty(key);
            if(property==null) throw new IllegalArgumentException("Unknown block property: "+tag);
            state=set(state,property,props.getString(key).orElseThrow());
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state,Property<T> property,String value) {
        return state.setValue(property,property.getValue(value).orElseThrow(()->new IllegalArgumentException("Invalid property value: "+value)));
    }
    public SerializableChunkData makeChunk(VoxyReader source, Bounds bounds, int cx, int cz) throws Exception {
        LevelChunkSection[] sections=new LevelChunkSection[24];
        List<CompoundTag> blockEntities=new ArrayList<>();
        List<SerializableChunkData.SectionData> data=new ArrayList<>();
        for(int sy=-4;sy<20;sy++) {
            var states=factory.createForBlockStates(); var palette=factory.createForBiomes();
            long[] voxels=null;
            if(sy*16<=bounds.maxY() && sy*16+15>=bounds.minY()) voxels=source.section(Math.floorDiv(cx,2),Math.floorDiv(sy,2),Math.floorDiv(cz,2));
            for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
                int wx=cx*16+x, wy=sy*16+y, wz=cz*16+z;
                if(!bounds.contains(wx,wy,wz)) { croppedBlocks++; continue; }
                if(voxels==null) { unknownBlocks++; continue; }
                long voxel=voxels[(wx&31)|((wz&31)<<5)|((wy&31)<<10)];
                BlockState state=blocks.get(SectionDecoder.block(voxel));
                if(state.isAir()) { storedAir++; continue; }
                recoveredNonAir++; states.set(x,y,z,state);
                if(state.hasBlockEntity()) {
                    if(state.is(Blocks.MOVING_PISTON)) {
                        unresolvedBlockEntities.add(Map.of("x",wx,"y",wy,"z",wz,"state",state.toString(),"reason","Missing moved block and piston progress; stored state preserved without invented block entity. May change when loaded in game."));
                        continue;
                    }
                    var entity=((EntityBlock)state.getBlock()).newBlockEntity(new BlockPos(wx,wy,wz),state);
                    if(entity==null) throw new IOException("Cannot create empty block entity: "+state);
                    blockEntities.add(entity.saveWithFullMetadata(registries)); emptyBlockEntities++;
                }
            }
            // Biomes are stored once per 4x4x4 cell. Air has lost its source biome.
            for(int by=0;by<4;by++) for(int bz=0;bz<4;bz++) for(int bx=0;bx<4;bx++) {
                TreeSet<Integer> found=new TreeSet<>();
                if(voxels!=null) for(int dy=0;dy<4;dy++) for(int dz=0;dz<4;dz++) for(int dx=0;dx<4;dx++) {
                    int wx=cx*16+bx*4+dx,wy=sy*16+by*4+dy,wz=cz*16+bz*4+dz;
                    if(!bounds.contains(wx,wy,wz))continue;
                    long voxel=voxels[(wx&31)|((wz&31)<<5)|((wy&31)<<10)];
                    if(SectionDecoder.block(voxel)!=0)found.add(SectionDecoder.biome(voxel));
                }
                if(found.size()==1)palette.set(bx,by,bz,biomes.get(found.first()));
                else { syntheticBiomeCells++; if(found.size()>1) conflictingBiomeCells++; }
            }
            sections[sy+4]=new LevelChunkSection(states,palette);
            data.add(new SerializableChunkData.SectionData(sy,sections[sy+4],null,null));
        }
        ChunkPos pos=new ChunkPos(cx,cz);
        var height=LevelHeightAccessor.create(-64,384);
        var proto=new ProtoChunk(pos,UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),height,factory,null);
        var types=ChunkStatus.FULL.heightmapsAfter(); Heightmap.primeHeightmaps(proto,types);
        Map<Heightmap.Types,long[]> maps=new EnumMap<>(Heightmap.Types.class);
        for(var type:types) maps.put(type,proto.getOrCreateHeightmapUnprimed(type).getRawData());
        CompoundTag structures=new CompoundTag(); structures.put("starts",new CompoundTag()); structures.put("References",new CompoundTag());
        return new SerializableChunkData(factory,pos,-4,0,0,ChunkStatus.FULL,null,null,UpgradeData.EMPTY,null,maps,new ChunkAccess.PackedTicks(List.of(),List.of()),new ShortList[24],false,data,List.of(),blockEntities,structures);
    }
    public void writeAndVerify(Path world, SerializableChunkData chunk) throws Exception {
        Path region=world.resolve("region"); Files.createDirectories(region);
        ChunkPos pos=chunk.chunkPos();
        Path file=region.resolve("r."+Math.floorDiv(pos.x,32)+"."+Math.floorDiv(pos.z,32)+".mca");
        var info=new RegionStorageInfo("Voxy Recovery",Level.OVERWORLD,"chunk");
        try(RegionFile r=new RegionFile(info,file,region,true)) {
            try(var out=r.getChunkDataOutputStream(pos)) { NbtIo.write(chunk.write(),out); }
        }
        // Reopen the saved region: this verifies the on-disk palettes, not just an in-memory codec round trip.
        try(RegionFile r=new RegionFile(info,file,region,true); var in=r.getChunkDataInputStream(pos)) {
            var tag=NbtIo.read(in,NbtAccounter.create(64L<<20));
            if(tag.getIntOr("DataVersion",0)!=4671) throw new IOException("Wrong Minecraft DataVersion");
            var actual=SerializableChunkData.parse(LevelHeightAccessor.create(-64,384),factory,tag);
            if(actual==null || !actual.chunkPos().equals(pos) || actual.chunkStatus()!=ChunkStatus.FULL)throw new IOException("Invalid saved chunk");
            for(var expected:chunk.sectionData()) {
                var got=actual.sectionData().stream().filter(s->s.y()==expected.y()).findFirst().orElseThrow().chunkSection();
                for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) if(got.getBlockState(x,y,z)!=expected.chunkSection().getBlockState(x,y,z))throw new IOException("Saved block state mismatch");
                for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++) if(!got.getBiomes().get(x,y,z).equals(expected.chunkSection().getBiomes().get(x,y,z)))throw new IOException("Saved biome mismatch");
            }
            if(!actual.blockEntities().equals(chunk.blockEntities()))throw new IOException("Block entity save mismatch");
            for(var e:chunk.heightmaps().entrySet()) if(!Arrays.equals(e.getValue(),actual.heightmaps().get(e.getKey())))throw new IOException("Heightmap save mismatch");
            verifiedChunks++;
        }
    }
}
