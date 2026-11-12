package com.cdp.codpattern.client.zombies;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
/** Resolve gun-pack names using the recipient's resources, never the server's language index. */
public final class ZombiesGunNames {
    public static Component display(ItemStack stack){
        if(stack.isEmpty())return Component.literal("未持枪");
        if(stack.hasCustomHoverName())return stack.getHoverName();
        var gun=IGun.getIGunOrNull(stack);if(gun==null)return stack.getHoverName();
        return displayId(gun.getGunId(stack).toString());
    }
    public static Component displayId(String value){var id=net.minecraft.resources.ResourceLocation.tryParse(value);if(id==null)return Component.literal(value);return TimelessAPI.getClientGunIndex(id).<Component>map(index->Component.translatable(index.getName())).orElseGet(()->Component.literal(value));}
    private ZombiesGunNames(){}
}
