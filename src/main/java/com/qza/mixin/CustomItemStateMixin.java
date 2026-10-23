package com.qza.mixin;

import com.qza.custom.CustomItemState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public class CustomItemStateMixin implements CustomItemState {
    @Unique
    private String qzaSkin;

    @Unique
    private String qzaUuid;

    @Unique
    private int qzaSize = 100;

    @Unique
    private int qzaOwner = -1;

    @Override
    public String qzaSkin() {
        return qzaSkin;
    }

    @Override
    public String qzaUuid() {
        return qzaUuid;
    }

    @Override
    public int qzaSize() {
        return qzaSize;
    }

    @Override
    public void qzaSetSize(int size) {
        qzaSize = size;
    }

    @Override
    public int qzaOwner() {
        return qzaOwner;
    }

    @Override
    public void qzaSetOwner(int owner) {
        qzaOwner = owner;
    }

    @Override
    public void qzaSetSkin(String skin, String uuid) {
        qzaSkin = skin;
        qzaUuid = uuid;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void qzaClearSkin(CallbackInfo ci) {
        qzaSkin = null;
        qzaUuid = null;
        qzaSize = 100;
        qzaOwner = -1;
    }
}
