package recovery;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import java.util.function.Consumer;
import com.google.gson.*;

/** Copies evidence before opening any database. Only verified copies are inspected. */
public final class SourceSnapshot {
    public record Candidate(String relativePath, String dimension) {
        public String toString() { return dimension+" — "+relativePath; }
    }
    public record Prepared(Path original, Path snapshot, Path manifest, List<Candidate> candidates) {}
    public static Prepared prepare(Path input, Path job, Consumer<String> progress) throws Exception {
        Path original=input.toRealPath();
        if(!Files.isDirectory(original))throw new IOException("Select the .voxy folder, not a file.");
        Path destination=job.toAbsolutePath().normalize();
        Path ancestor=destination;while(!Files.exists(ancestor))ancestor=ancestor.getParent();
        destination=ancestor.toRealPath().resolve(ancestor.relativize(destination)).normalize();
        if(destination.startsWith(original)||original.startsWith(destination))throw new IOException("Working copy must be outside the source folder.");
        if(Files.exists(destination))throw new IOException("Working folder already exists.");
        List<Path> files=inventory(original);
        if(files.stream().noneMatch(p->p.getFileName().toString().equals("CURRENT")))throw new IOException("No Voxy database found. Select .voxy, its saves folder, or a database storage folder.");
        Path snapshot=destination.resolve("snapshot"), copied=snapshot.resolve("source");
        Files.createDirectories(copied);JsonArray manifest=new JsonArray();
        int done=0;
        for(Path source:files) {
            Path relative=original.relativize(source),target=copied.resolve(relative);
            String before=sha256(source);Files.createDirectories(target.getParent());Files.copy(source,target);
            if(!before.equals(sha256(target)))throw new IOException("Copy verification failed: "+relative);
            JsonObject item=new JsonObject();item.addProperty("path",snapshot.relativize(target).toString().replace('\\','/'));
            item.addProperty("size",Files.size(target));item.addProperty("sha256",before);manifest.add(item);
            if(++done%25==0 || done==files.size())progress.accept("Copied and verified "+done+" / "+files.size()+" files");
        }
        progress.accept("Checking that the source did not change during the copy...");
        if(!files.equals(inventory(original)))throw new IOException("Source changed during copying. Close Minecraft and try again.");
        for(int i=0;i<files.size();i++)if(!sha256(files.get(i)).equals(manifest.get(i).getAsJsonObject().get("sha256").getAsString()))throw new IOException("Source changed during copying. Close Minecraft and try again.");
        Path manifestFile=destination.resolve("manifest.json");Files.writeString(manifestFile,Config.JSON.toJson(manifest));
        List<Candidate> candidates=new ArrayList<>();
        for(Path source:files)if(source.getFileName().toString().equals("CURRENT")) {
            Path db=copied.resolve(original.relativize(source)).getParent();
            try(VoxyReader reader=new VoxyReader(db)) {
                Set<String> names=new HashSet<>(reader.biomes.values());
                String label=dimension(names);
                candidates.add(new Candidate(snapshot.relativize(db).toString(),label));
            }
        }
        if(candidates.isEmpty())throw new IOException("No supported Voxy databases found.");
        return new Prepared(original,snapshot,manifestFile,List.copyOf(candidates));
    }
    static String dimension(Set<String> names) {
        Set<String> nether=Set.of("minecraft:nether_wastes","minecraft:soul_sand_valley","minecraft:basalt_deltas","minecraft:crimson_forest","minecraft:warped_forest");
        Set<String> end=Set.of("minecraft:the_end","minecraft:end_highlands","minecraft:end_midlands","minecraft:small_end_islands","minecraft:end_barrens");
        if(names.isEmpty())return "Unknown dimension";
        if(nether.containsAll(names))return "Nether (unsupported)";
        if(end.containsAll(names))return "End (unsupported)";
        if(Collections.disjoint(names,nether)&&Collections.disjoint(names,end))return "Overworld candidate";
        return "Mixed/unknown dimension";
    }
    private static List<Path> inventory(Path root)throws IOException {
        try(var stream=Files.walk(root)) {
            List<Path> paths=stream.sorted().toList();
            for(Path p:paths)if(Files.isSymbolicLink(p)||!p.toRealPath().startsWith(root))throw new IOException("Source links are not supported: "+p);
            return paths.stream().filter(Files::isRegularFile).toList();
        }
    }
    static String sha256(Path path)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(path)) {byte[] buffer=new byte[1<<20];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();digest.update(buffer,0,n);}}
        return HexFormat.of().formatHex(digest.digest());
    }
}
