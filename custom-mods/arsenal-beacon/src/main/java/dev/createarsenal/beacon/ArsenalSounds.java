package dev.createarsenal.beacon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/**
 * The mod's own sounds (files under {@code assets/arsenal_beacon/sounds}; the owner's raid songs and effects are converted by {@code tools/audio}
 * and the rest is made there): the raid songs, the victory fanfare,
 * the support cannon, the flare radio chatter and the fire supports. Range follows the volume a sound is played at (16 blocks per unit of volume), so a
 * blast played at volume 8 is heard 128 blocks away.
 */
final class ArsenalSounds {
    private ArsenalSounds(){}
    static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,ArsenalBeacon.ID);
    static final RegistryObject<SoundEvent> VICTORY=of("music.victory");
    /** One event per raid song ({@code music.<id>}), in the order of {@link RaidPlaylist#SONGS}. */
    private static final java.util.Map<String,RegistryObject<SoundEvent>> SONGS=new java.util.LinkedHashMap<>();
    static {for(var song:RaidPlaylist.SONGS)SONGS.put(song.id(),of("music."+song.id()));}
    /** The sound event of a raid song (null for an unknown id). */
    static SoundEvent song(String id){var event=SONGS.get(id);return event==null?null:event.get();}
    /** The owner's hand-tuned effects (0.0.18, converted by tools/audio/import_sfx.py): the cannon, the Bunker Buster, the cluster bomblets, the shockwave, the attack alarm. */
    static final RegistryObject<SoundEvent> CANNON_FIRE=of("cannon.fire"),CANNON_TURNING=of("cannon.turning"),CANNON_TURNING_DONE=of("cannon.turning_done");
    static final RegistryObject<SoundEvent> BUNKER_BUSTER=of("bunker.buster"),CLUSTER_STRIKE=of("fire.cluster_strike"),SHOCKWAVE=of("fire.shockwave"),BEACON_ATTACKED=of("beacon.attacked");
    /**
     * The owner's flare radio lines (tools/audio/import_chatter.py), one event each, per flare kind: six fire support lines (0.0.19), two
     * supply and three recall lines (0.0.22). {@link SupportChatter} picks one when a flare's call is taken.
     */
    private static final java.util.Map<SupportCalls.Kind,List<RegistryObject<SoundEvent>>> CHATTER=new java.util.EnumMap<>(SupportCalls.Kind.class);
    static {for(var kind:SupportCalls.Kind.values())CHATTER.put(kind,java.util.stream.IntStream.rangeClosed(1,SupportChatter.count(kind)).mapToObj(n->of(SupportChatter.event(kind,n))).toList());}
    /** The sound of one radio line (1-based) of a flare kind. */
    static SoundEvent chatter(SupportCalls.Kind kind,int line){return CHATTER.get(kind).get(line-1).get();}
    /** The owner's incoming-shell scream (0.0.18, back in 0.0.20 after the 0.0.19 whistle): played where a shell or bomb will land, see {@link OrdnanceClient}. */
    static final RegistryObject<SoundEvent> INCOMING=of("ordnance.incoming");
    /** Made by tools/audio (0.0.16): the sounds of the fire supports that are not explosions. Explosions use the game's own explosion sound. */
    static final RegistryObject<SoundEvent> CRYO=of("fire.cryo"),NAPALM=of("fire.napalm"),GRAVITY_HUM=of("fire.gravity_hum"),STARSHELL=of("fire.starshell");
    /** Tactical Operations (0.0.20, made by tools/audio/tactical.py): the table's confirm and refuse, the satellite scan, a warband's contact alert. */
    static final RegistryObject<SoundEvent> TACTICAL_ACCEPT=of("tactical.accept"),TACTICAL_REFUSED=of("tactical.refused"),TACTICAL_SCAN=of("tactical.scan"),
        TACTICAL_SCAN_DONE=of("tactical.scan_done"),TACTICAL_CONTACT=of("tactical.contact");
    /**
     * The specialty seller's radio (0.0.34, the owner's recordings, tools/audio/import_chatter.py): static as the Command Table makes contact,
     * and one of four order lines when a purchase goes through ({@link SupplyCatapult#ORDER_LINES}).
     */
    static final RegistryObject<SoundEvent> SELLER_STATIC=of("seller.radio_static");
    /** The owner's war horn (0.0.34, tools/audio/import_sfx.py): a FOB planted, a FOB's raid starting ({@link Fobs#horn}). */
    static final RegistryObject<SoundEvent> WAR_HORN=of("fob.war_horn");
    private static final List<RegistryObject<SoundEvent>> ORDER_CHATTER=java.util.stream.IntStream.rangeClosed(1,SupplyCatapult.ORDER_LINES).mapToObj(n->of("seller.order_chatter_"+n)).toList();
    /** One order line (1-based). */
    static SoundEvent orderChatter(int line){return ORDER_CHATTER.get(line-1).get();}
    /**
     * The jingle of a cleared mission. A placeholder until the owner's song arrives: its sounds.json entry plays the victory fanfare. Dropping
     * the song in as {@code sounds/music/mission_cleared.ogg} and pointing the entry at it is all that is needed.
     */
    static final RegistryObject<SoundEvent> MISSION_CLEARED=of("music.mission_cleared");
    private static RegistryObject<SoundEvent> of(String id){return SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation(ArsenalBeacon.ID,id)));}
}
