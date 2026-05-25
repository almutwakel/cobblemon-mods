package com.cobblemonbridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Patches RCTmod's {@code DataPackManager.findResource} so trainer IDs in a non-{@code rctmod}
 * namespace (like our datapack's {@code server:gym_01_clay}) don't crash the client renderer.
 *
 * <p>The bug: {@code findResource(trainerId, type)} concatenates the trainer id into a texture
 * path like {@code <type>/<trainerId>.<suffix>} and passes that to
 * {@code ResourceLocation.fromNamespaceAndPath("rctmod", ...)}. When {@code trainerId} contains
 * a colon (because it's stored as a namespaced ResourceLocation in string form), the path is
 * invalid and Minecraft throws {@code ResourceLocationException} — crashing the client the
 * moment it tries to render a tagged trainer.
 *
 * <p>Fix: strip everything up to and including the first colon in {@code trainerId} before path
 * construction. We don't ship trainer textures for our gym leaders, so the resource lookup
 * will fail to find a matching file and RCT's normal fallback returns its bundled
 * {@code FALLBACK_TEXTURE} (the default trainer skin). The entity renders fine and the client
 * stays connected.
 *
 * <p>{@code remap = false} because RCTmod is a NeoForge mod (Mojmap names at runtime),
 * not vanilla MC — no name mapping needed.
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.api.data.pack.DataPackManager", remap = false)
public class DataPackManagerMixin {

    @ModifyVariable(
        method = "findResource(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Optional;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String cobblemonbridge$stripTrainerNamespace(String trainerId) {
        if (trainerId == null) return trainerId;
        int colon = trainerId.indexOf(':');
        return (colon >= 0) ? trainerId.substring(colon + 1) : trainerId;
    }
}
