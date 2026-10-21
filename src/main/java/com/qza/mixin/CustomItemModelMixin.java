package com.qza.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.qza.custom.CustomItemState;
import com.qza.custom.CustomItems;
import com.qza.inventory.ItemProtection;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public class CustomItemModelMixin {
    @ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object qzaCustomModel(Object original, @Local(argsOnly = true) ItemStackRenderState output,
                                  @Local(argsOnly = true) ItemStack item, @Local(argsOnly = true) ItemDisplayContext context) {
        CustomItems.Look look = CustomItems.skin(item);
        ((CustomItemState) output).qzaSetSkin(look == null ? null : look.format(),
                look == null ? null : ItemProtection.uuid(item));
        return look == null ? original : CustomItems.model(look, context);
    }
}
