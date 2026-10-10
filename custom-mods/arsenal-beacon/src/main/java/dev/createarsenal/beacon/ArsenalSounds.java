package dev.createarsenal.beacon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own sounds (files under {@code assets/arsenal_beacon/sounds}, made by {@code tools/audio}): the raid music, the victory fanfare,
 * the support cannon, the flares and the fire supports. Range follows the volume a sound is played at (16 blocks per unit of volume), so a
 * blast played at volume 8 is heard 128 blocks away.
 */
final class ArsenalSounds {
    private ArsenalSounds(){}
    static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,ArsenalBeacon.ID);
    static final RegistryObject<SoundEvent> RAID_NORMAL=of("music.raid_normal"),RAID_BOSS=of("music.raid_boss"),RAID_SPECIAL=of("music.raid_special"),VICTORY=of("music.victory");
    static final RegistryObject<SoundEvent> CANNON_FIRE=of("cannon.fire"),CANNON_TRAVERSE=of("cannon.traverse"),CANNON_CLANK=of("cannon.clank"),CANNON_LOCK=of("cannon.lock");
    static final RegistryObject<SoundEvent> FLARE_SIGNAL=of("flare.signal"),SHELL_WHISTLE=of("shell.whistle"),BOMB_WHISTLE=of("bomb.whistle"),SHELL_EXPLOSION=of("shell.explosion");
    static final RegistryObject<SoundEvent> BUNKER_IMPACT=of("bunker.impact"),BUNKER_DIG=of("bunker.dig");
    static final RegistryObject<SoundEvent> CLUSTER=of("fire.cluster"),CRYO=of("fire.cryo"),NAPALM=of("fire.napalm"),GRAVITY_HUM=of("fire.gravity_hum"),GRAVITY_IMPLODE=of("fire.gravity_implode"),
        SHOCKWAVE=of("fire.shockwave"),STARSHELL=of("fire.starshell");
    private static RegistryObject<SoundEvent> of(String id){return SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation(ArsenalBeacon.ID,id)));}
}
