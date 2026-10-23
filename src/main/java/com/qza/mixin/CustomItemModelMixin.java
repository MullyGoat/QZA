package com.qza.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.qza.custom.CustomItemState;
import com.qza.custom.CustomItems;
import com.qza.inventory.ItemProtection;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public class CustomItemModelMixin {
    @ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object qzaCustomModel(Object original, @Local(argsOnly = true) ItemStackRenderState output,
                                  @Local(argsOnly = true) ItemStack item, @Local(argsOnly = true) ItemDisplayContext context,
                                  @Local(argsOnly = true) ItemOwner owner) {
        CustomItems.Look look = CustomItems.skinFor(item, owner);
        ((CustomItemState) output).qzaSetSkin(look == null ? null : look.format(),
                look == null ? null : ItemProtection.uuid(item));
        ((CustomItemState) output).qzaSetSize(look == null ? CustomItems.DEFAULT_SIZE : CustomItems.sizeFor(item));
        LivingEntity holder = owner == null ? null : owner.asLivingEntity();
        ((CustomItemState) output).qzaSetOwner(holder == null ? -1 : holder.getId());
        return look == null ? original : CustomItems.model(look, context);
    }
}
