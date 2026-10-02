package dev.aller.platform;

import dev.aller.AllerClient;
//? if <26.1 {
/*import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.resources.Identifier;
//?}

/** Resource identifiers were renamed in 26.1; everything else goes through here. */
public final class Ids {
    private Ids() {}

    //? if <26.1 {
    /*public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(AllerClient.ID, path);
    }

    public static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
    *///?} else {
    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(AllerClient.ID, path);
    }

    /** One of Minecraft's own ids ("block.beacon.activate"). */
    public static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }
    //?}
}
