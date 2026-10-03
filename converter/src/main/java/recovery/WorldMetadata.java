package recovery;

import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.flat.*;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.flag.FeatureFlags;

/** New recovery-world metadata. Seed zero is a placeholder, never claimed as recovered. */
final class WorldMetadata {
    static void write(Path output,Bounds bounds,HolderLookup.Provider registries) throws Exception {
        var biome=registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var flat=new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct(List.of())),biome,List.of());
        // One air layer is a valid flat preset and produces an empty world.
        flat.getLayersInfo().add(new FlatLayerInfo(1,net.minecraft.world.level.block.Blocks.AIR)); flat.updateLayers();
        var dimensions=registries.lookupOrThrow(Registries.DIMENSION_TYPE);
        Map<net.minecraft.resources.ResourceKey<LevelStem>,LevelStem> stems=new LinkedHashMap<>();
        stems.put(LevelStem.OVERWORLD,new LevelStem(dimensions.getOrThrow(BuiltinDimensionTypes.OVERWORLD),new FlatLevelSource(flat)));
        stems.put(LevelStem.NETHER,new LevelStem(dimensions.getOrThrow(BuiltinDimensionTypes.NETHER),new FlatLevelSource(flat)));
        stems.put(LevelStem.END,new LevelStem(dimensions.getOrThrow(BuiltinDimensionTypes.END),new FlatLevelSource(flat)));
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        Tag worldgen=WorldGenSettings.encode(ops,new WorldOptions(0,false,false),new WorldDimensions(stems)).getOrThrow();
        WorldGenSettings.CODEC.parse(ops,worldgen).getOrThrow();
        CompoundTag data=NbtUtils.addCurrentDataVersion(new CompoundTag());
        var version=SharedConstants.getCurrentVersion(); CompoundTag v=new CompoundTag();
        v.putString("Name",version.name());v.putInt("Id",version.dataVersion().version());v.putString("Series",version.dataVersion().series());v.putBoolean("Snapshot",!version.stable());
        data.put("Version",v);data.put("WorldGenSettings",worldgen);data.putString("LevelName","Voxy Recovery - 1.21.11");
        data.putInt("version",19133);data.putInt("GameType",3);data.putBoolean("hardcore",false);data.putBoolean("allowCommands",true);data.putBoolean("initialized",true);
        data.putByte("Difficulty",(byte)0);data.putLong("Time",0);data.putLong("DayTime",6000);data.putLong("LastPlayed",System.currentTimeMillis());
        int x=(int)Math.floorDiv((long)bounds.minX()+bounds.maxX(),2),z=(int)Math.floorDiv((long)bounds.minZ()+bounds.maxZ(),2);
        int y=Math.max(bounds.minY(),Math.min(86,bounds.maxY()));
        data.store("spawn",LevelData.RespawnData.CODEC,LevelData.RespawnData.of(Level.OVERWORLD,new BlockPos(x,y,z),0,0));
        var rules=new GameRules(FeatureFlags.DEFAULT_FLAGS);
        rules.set(GameRules.RANDOM_TICK_SPEED,0,null);rules.set(GameRules.SPAWN_MOBS,false,null);rules.set(GameRules.ADVANCE_TIME,false,null);rules.set(GameRules.ADVANCE_WEATHER,false,null);
        data.store("game_rules",GameRules.codec(FeatureFlags.DEFAULT_FLAGS),rules);
        data.store(WorldDataConfiguration.MAP_CODEC,WorldDataConfiguration.DEFAULT);
        CompoundTag root=new CompoundTag();root.put("Data",data);NbtIo.writeCompressed(root,output.resolve("level.dat"));
        var saved=NbtIo.readCompressed(output.resolve("level.dat"),NbtAccounter.create(16L<<20)).getCompound("Data").orElseThrow();
        WorldGenSettings.CODEC.parse(ops,saved.get("WorldGenSettings")).getOrThrow();
        LevelSettings.parse(new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE,saved),WorldDataConfiguration.DEFAULT);
    }
}
