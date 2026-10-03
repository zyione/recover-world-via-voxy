package recovery;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class Main {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Usage: recover <config.json>");
        run(Path.of(args[0]),System.out::println);
    }
    public static void run(Path file,java.util.function.Consumer<String> progress) throws Exception {
        Config config=Config.read(file);
        long started=System.nanoTime();
        progress.accept("Verifying source snapshot..."); int files=config.verifySnapshot();
        if(Files.exists(config.output()))throw new IllegalArgumentException("Output already exists. Choose a new output folder: "+config.output());
        Files.createDirectories(config.output());
        Files.writeString(config.output().resolve("INCOMPLETE.txt"),"Conversion in progress. Do not open this world.\n");
        Files.copy(file,config.output().resolve("job-config.json"));
        try(VoxyReader source=new VoxyReader(config.database())) {
            MinecraftWriter writer=new MinecraftWriter(source);
            progress.accept("Validated "+source.states.size()+" block mappings and "+source.biomes.size()+" biome mappings against Minecraft 1.21.11.");
            Bounds b=config.bounds(); long total=b.chunkCount(),done=0;
            for(int z=Math.floorDiv(b.minZ(),16);z<=Math.floorDiv(b.maxZ(),16);z++)for(int x=Math.floorDiv(b.minX(),16);x<=Math.floorDiv(b.maxX(),16);x++) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("Conversion cancelled; output remains marked incomplete");
                writer.writeAndVerify(config.output(),writer.makeChunk(source,b,x,z));
                done++; if(done%16==0 || done==total)progress.accept("Recovered and verified "+done+" / "+total+" chunks");
            }
            WorldMetadata.write(config.output(),b,writer.registries);
            config.verifySnapshot();
            Map<String,Object> report=new LinkedHashMap<>();
            report.put("completedAt",Instant.now().toString());report.put("minecraftVersion","1.21.11");report.put("dataVersion",4671);
            report.put("elapsedSeconds",(System.nanoTime()-started)/1e9);
            report.put("sourceDatabase",config.database().toString());report.put("sourceManifest",config.manifest().toString());
            report.put("placeholderSeed",0);report.put("sourceFormat","Voxy SaveLoadSystem3, commit 3eda859081bfeef786804a92645d4ba8d010ecae");
            report.put("validatedBlockMappings",source.states.size());report.put("validatedBiomeMappings",source.biomes.size());
            report.put("bounds",b);report.put("verifiedSourceFilesBeforeAndAfter",files);report.put("verifiedChunks",writer.verifiedChunks);
            report.put("recoveredNonAirBlocks",writer.recoveredNonAir);report.put("storedAirOrUnobservedZeroBlocks",writer.storedAir);
            report.put("missingSourceBlocksWrittenAsAir",writer.unknownBlocks);report.put("outsideSelectionWrittenAsAir",writer.croppedBlocks);
            report.put("syntheticPlainsBiomeCells",writer.syntheticBiomeCells);report.put("conflictingBiomeCells",writer.conflictingBiomeCells);
            report.put("emptyBlockEntityShells",writer.emptyBlockEntities);
            report.put("unresolvedBlockEntities",writer.unresolvedBlockEntities);
            report.put("seedRecovered",false);report.put("gameLoadVerified",false);
            report.put("limitations",List.of("Source air cannot be distinguished from unobserved zero voxels.","No entities, inventories, sign text, original block entity data or scheduled ticks recovered.","Lighting is marked for recalculation; a live Minecraft load has not been verified.","LOD0 records have no provenance guarantee; coarse LOD data is not expanded into invented blocks."));
            Files.writeString(config.output().resolve("recovery-report.json"),Config.JSON.toJson(report));
            Files.writeString(config.output().resolve("READ-ME-FIRST.txt"),"EXPERIMENTAL RECOVERY - Minecraft Java 1.21.11\nBlocks and biomes have passed native save/read-back comparison. Live game loading and lighting remain unverified.\nKeep this folder as the master; copy it before opening in Minecraft.\nMoving piston contents, inventories, entities and original seed are unavailable. Consult recovery-report.json.\nSeed 0 is a placeholder. New terrain is void. Outside the chosen bounds is empty.\n");
            Files.delete(config.output().resolve("INCOMPLETE.txt"));
            progress.accept("Native chunk export complete: "+config.output());
        } catch(Exception e) {
            Files.writeString(config.output().resolve("failure.txt"),e.toString()+"\nThis output is incomplete. Choose a new folder for another run.\n");
            throw e;
        }
    }
}
