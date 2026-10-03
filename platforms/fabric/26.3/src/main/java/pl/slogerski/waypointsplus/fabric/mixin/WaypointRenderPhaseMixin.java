package pl.slogerski.waypointsplus.fabric.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
abstract class WaypointRenderPhaseMixin {
    @Shadow @Final private SubmitNodeStorage submitNodeStorage;
    @Unique private boolean waypointsplus$hasOverlaySubmits;

    @Inject(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
    private void waypointsplus$collectOverlayState(CallbackInfo ci) {
        waypointsplus$hasOverlaySubmits = false;
        for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
            if (!collection.alwaysOnTopGizmos.isEmpty()) {
                waypointsplus$hasOverlaySubmits = true;
                break;
            }
        }
    }

    @Inject(method = "frameHasAlwaysOnTopGizmos", at = @At("RETURN"), cancellable = true)
    private void waypointsplus$includeOverlaySubmits(CallbackInfoReturnable<Boolean> cir) {
        if (waypointsplus$hasOverlaySubmits) cir.setReturnValue(true);
    }
}
