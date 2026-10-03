package recovery;

/** Inclusive block coordinates, including negative coordinates. */
public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    public Bounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Minimum exceeds maximum");
        if (minY < -64 || maxY > 319) throw new IllegalArgumentException("Overworld Y must be between -64 and 319");
        if (minX < -30_000_000 || maxX > 29_999_999 || minZ < -30_000_000 || maxZ > 29_999_999) throw new IllegalArgumentException("Outside Minecraft world limits");
    }
    public boolean contains(int x, int y, int z) { return x>=minX && x<=maxX && y>=minY && y<=maxY && z>=minZ && z<=maxZ; }
    public long chunkCount() { return ((long)Math.floorDiv(maxX,16)-Math.floorDiv(minX,16)+1)*((long)Math.floorDiv(maxZ,16)-Math.floorDiv(minZ,16)+1); }
}
