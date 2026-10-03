package recovery;

import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.HexFormat;

public record Config(Path original, Path snapshot, Path manifest, Path database, Path output, Bounds bounds, long maxChunks) {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config read(Path file) throws IOException {
        file=file.toAbsolutePath().normalize();
        JsonObject o=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(!o.get("minecraftVersion").getAsString().equals("1.21.11")) throw new IllegalArgumentException("This converter targets Minecraft 1.21.11 only");
        Path parent=file.getParent();
        Path original=parent.resolve(o.get("originalEvidence").getAsString()).toRealPath();
        Path snapshot=parent.resolve(o.get("snapshot").getAsString()).toRealPath();
        Path db=snapshot.resolve(o.get("database").getAsString()).toRealPath();
        Path output=parent.resolve(o.get("output").getAsString()).toAbsolutePath().normalize();
        Path ancestor=output; while(!Files.exists(ancestor)) ancestor=ancestor.getParent();
        output=ancestor.toRealPath().resolve(ancestor.relativize(output)).normalize();
        if(!db.startsWith(snapshot) || output.startsWith(snapshot) || snapshot.startsWith(output) || output.startsWith(original) || original.startsWith(output) || snapshot.startsWith(original) || original.startsWith(snapshot)) throw new IllegalArgumentException("Original evidence, output and snapshot must be separate");
        Bounds b=JSON.fromJson(o.get("bounds"),Bounds.class);
        long cap=o.get("maxChunks").getAsLong();
        if(cap<1 || b.chunkCount()>cap) throw new IllegalArgumentException("Selection exceeds maxChunks safety limit: "+b.chunkCount());
        return new Config(original,snapshot,parent.resolve(o.get("manifest").getAsString()).toRealPath(),db,output,b,cap);
    }
    public int verifySnapshot() throws Exception {
        int count=0;
        java.util.Set<Path> expected=new java.util.HashSet<>();
        for(JsonElement e:JsonParser.parseString(Files.readString(manifest)).getAsJsonArray()) {
            JsonObject item=e.getAsJsonObject();
            Path f=snapshot.resolve(item.get("path").getAsString().replace('\\','/')).normalize();
            if(!f.startsWith(snapshot)) throw new IOException("Unsafe manifest path");
            f=f.toRealPath(); if(!f.startsWith(snapshot)) throw new IOException("Snapshot symlink escapes root");
            if(!f.startsWith(database)) continue;
            expected.add(f);
            if(Files.size(f)!=item.get("size").getAsLong()) throw new IOException("Snapshot size mismatch: "+f);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=Files.newInputStream(f)) { byte[] buf=new byte[1<<20]; int n; while((n=in.read(buf))!=-1) digest.update(buf,0,n); }
            if(!HexFormat.of().formatHex(digest.digest()).equals(item.get("sha256").getAsString())) throw new IOException("Snapshot hash mismatch: "+f);
            count++;
        }
        if(count==0) throw new IOException("Database not covered by manifest");
        try(var paths=Files.walk(database)) {
            for(Path path:paths.filter(Files::isRegularFile).toList()) if(!expected.contains(path.toRealPath())) throw new IOException("Unverified extra file in snapshot database: "+path);
        }
        return count;
    }
}
