package com.qza.mixin;

import com.qza.pack.PackCombiner;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PackRepository.class)
public class PackPriorityMixin {
    @Inject(method = "openAllSelected", at = @At("RETURN"), cancellable = true)
    private void qzaCombinedOnTop(CallbackInfoReturnable<List<PackResources>> cir) {
        List<PackResources> ordered = PackCombiner.onTop(cir.getReturnValue());
        if (ordered != null) {
            cir.setReturnValue(ordered);
        }
    }
}
