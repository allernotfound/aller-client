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
    *///?} else {
    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(AllerClient.ID, path);
    }
    //?}
}
