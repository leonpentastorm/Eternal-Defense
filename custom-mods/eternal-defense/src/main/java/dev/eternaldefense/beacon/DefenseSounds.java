package dev.eternaldefense.beacon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** The mod's own sounds: the raid songs, the victory fanfare, the support cannon */
final class DefenseSounds {
    private DefenseSounds(){}
    static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,EternalDefense.ID);
    static final RegistryObject<SoundEvent> VICTORY=of("music.victory");
    /** One event per raid song (music. ), in the order of RaidPlaylist.SONGS. */
    private static final java.util.Map<String,RegistryObject<SoundEvent>> SONGS=new java.util.LinkedHashMap<>();
    static {for(var song:RaidPlaylist.SONGS)SONGS.put(song.id(),of("music."+song.id()));}
    /** The sound event of a raid song (null for an unknown id). */
    static SoundEvent song(String id){var event=SONGS.get(id);return event==null?null:event.get();}
    /** The hand-tuned effects: the cannon, the Bunker Buster, the cluster bomblets, the shockwave, the attack alarm. */
    static final RegistryObject<SoundEvent> CANNON_FIRE=of("cannon.fire"),CANNON_TURNING=of("cannon.turning"),CANNON_TURNING_DONE=of("cannon.turning_done");
    static final RegistryObject<SoundEvent> BUNKER_BUSTER=of("bunker.buster"),CLUSTER_STRIKE=of("fire.cluster_strike"),SHOCKWAVE=of("fire.shockwave"),BEACON_ATTACKED=of("beacon.attacked");
    /** The flare radio lines (tools/audio/import_chatter.py), one event each, per flare kind */
    private static final java.util.Map<SupportCalls.Kind,List<RegistryObject<SoundEvent>>> CHATTER=new java.util.EnumMap<>(SupportCalls.Kind.class);
    static {for(var kind:SupportCalls.Kind.values())CHATTER.put(kind,java.util.stream.IntStream.rangeClosed(1,SupportChatter.count(kind)).mapToObj(n->of(SupportChatter.event(kind,n))).toList());}
    /** The sound of one radio line (1-based) of a flare kind. */
    static SoundEvent chatter(SupportCalls.Kind kind,int line){return CHATTER.get(kind).get(line-1).get();}
    /** The incoming-shell scream: played where a shell or bomb will land, see OrdnanceClient. */
    static final RegistryObject<SoundEvent> INCOMING=of("ordnance.incoming");
    /** Made by tools/audio: the sounds of the fire supports that are not explosions. */
    static final RegistryObject<SoundEvent> CRYO=of("fire.cryo"),NAPALM=of("fire.napalm"),GRAVITY_HUM=of("fire.gravity_hum"),STARSHELL=of("fire.starshell");
    /** Tactical Operations: the table's confirm and refuse, the satellite scan, a warband's contact alert. */
    static final RegistryObject<SoundEvent> TACTICAL_ACCEPT=of("tactical.accept"),TACTICAL_REFUSED=of("tactical.refused"),TACTICAL_SCAN=of("tactical.scan"),
        TACTICAL_SCAN_DONE=of("tactical.scan_done"),TACTICAL_CONTACT=of("tactical.contact");
    /** The specialty seller's radio: static as the Command Table makes contact */
    static final RegistryObject<SoundEvent> SELLER_STATIC=of("seller.radio_static");
    /** The Handheld Radio's short burst of static */
    static final RegistryObject<SoundEvent> RADIO=of("radio.static");
    /** The war horn: a FOB planted, a FOB's raid starting (Fobs.horn). */
    static final RegistryObject<SoundEvent> WAR_HORN=of("fob.war_horn");
    private static final List<RegistryObject<SoundEvent>> ORDER_CHATTER=java.util.stream.IntStream.rangeClosed(1,SupplyCatapult.ORDER_LINES).mapToObj(n->of("seller.order_chatter_"+n)).toList();
    /** One order line (1-based). */
    static SoundEvent orderChatter(int line){return ORDER_CHATTER.get(line-1).get();}
    /** The jingle of a cleared mission. */
    static final RegistryObject<SoundEvent> MISSION_CLEARED=of("music.mission_cleared");
    private static RegistryObject<SoundEvent> of(String id){return SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation(EternalDefense.ID,id)));}
}
