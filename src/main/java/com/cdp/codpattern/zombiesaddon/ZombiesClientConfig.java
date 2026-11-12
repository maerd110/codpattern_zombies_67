package com.cdp.codpattern.zombiesaddon;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client preference shared by the vanilla key binding page and the wheel. */
public final class ZombiesClientConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue HOLD_WHEEL;
    static{var b=new ForgeConfigSpec.Builder();HOLD_WHEEL=b.comment("Hold the team wheel key and release to select; false toggles the wheel on each press.").define("holdCommandWheel",false);SPEC=b.build();}
    public static boolean holdWheel(){return SPEC.isLoaded()&&HOLD_WHEEL.get();}
    public static void setHoldWheel(boolean hold){HOLD_WHEEL.set(hold);if(SPEC.isLoaded())SPEC.save();}
    private ZombiesClientConfig(){}
}
