package recovery;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

class RegistryTest {
    @Test void targetRegistryNeverSilentlySubstitutesUnknownStates() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        CompoundTag tag=new CompoundTag();tag.putString("Name","minecraft:does_not_exist");
        assertThrows(IllegalArgumentException.class,()->MinecraftWriter.resolve(tag));
        tag.putString("Name","minecraft:oak_stairs");
        assertThrows(IllegalArgumentException.class,()->MinecraftWriter.resolve(tag));
        CompoundTag props=new CompoundTag();props.putString("facing","north");props.putString("half","bottom");props.putString("shape","straight");props.putString("waterlogged","false");tag.put("Properties",props);
        assertFalse(MinecraftWriter.resolve(tag).isAir());
        props.putString("facing","sideways");assertThrows(IllegalArgumentException.class,()->MinecraftWriter.resolve(tag));
        props.putString("facing","north");props.remove("shape");props.putString("nonexistent","straight");assertThrows(IllegalArgumentException.class,()->MinecraftWriter.resolve(tag));
    }
}
