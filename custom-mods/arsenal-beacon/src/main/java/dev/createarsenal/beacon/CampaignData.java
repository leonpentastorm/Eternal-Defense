package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** One shared Overworld beacon, persisted with its raid and damage journal. */
public final class CampaignData extends SavedData {
    public String phase="unplaced";
    public BlockPos beacon=BlockPos.ZERO;
    public BlockPos rewardChest;
    public int raidLimit=10;
    public int health=1000,core,logistics,defense,restoration,victories,rewardTier,raidTier,score,wave,deaths;
    public long preparationTicks,raidTicks,waveTicks,campaignSerial;
    public int reconnaissance,raidsStarted,vertical,respitePurchases,nextRaidBonus;
    public long respiteTicks;
    public boolean introCompleted,introRaid;
    public boolean hardRaid,bossSpawned,bossKilled;
    public UUID bossId;
    public int spawnRemaining,spawnCooldown,breachClock,scanCursor;
    public int wavePlayers=1,waveVeteran;
    public boolean showBoundary=true,showBeam=true,showHurtbox=true;
    public long lastAttackTick=-1; // Transient: old attack warnings never survive a world reload.
    public boolean underAttack(long now){return installed()&&lastAttackTick>=0&&now>=lastAttackTick&&now-lastAttackTick<100;}
    public boolean victoryRestoration;
    public final Set<UUID> participants=new HashSet<>();
    public final Set<UUID> raiders=new HashSet<>();
    public final Map<Long,BlockState> snapshot=new HashMap<>();
    public final Map<Long,Damage> damage=new LinkedHashMap<>();
    public final Map<String,Integer> baseCounts=new HashMap<>();
    public final List<CompoundTag> rewards=new ArrayList<>();
    /** The reward chest: part of the beacon itself. Claimed rewards wait here (54 slots) until the team takes them. */
    public final net.minecraft.world.SimpleContainer rewardBox=new net.minecraft.world.SimpleContainer(54);
    /** Kind of the raid in progress and of the next one (see {@link RaidTypes}); "normal" for an ordinary raid. */
    public String raidType="normal",nextRaidType="normal";
    public final List<CompoundTag> destroyedTurrets=new ArrayList<>();
    public record Damage(BlockState before,CompoundTag entity) {}
    public static CampaignData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CampaignData::load,CampaignData::new,"create_arsenal_beacon");
    }
    public int radius(){return Rules.radius(core);}
    public int below(){return Rules.below(vertical);}
    public int above(){return Rules.above(vertical);}
    public int height(){return below()+above()+1;}
    public int maximumHealth(){return Rules.maximumHealth(core);}
    public boolean active(){return phase.equals("raid")||phase.equals("snapshot")||phase.equals("restore");}
    public boolean installed(){return !phase.equals("unplaced")&&!phase.equals("decommissioning");}
    public void resetProgress() {
        rewardChest=null;raidLimit=10;raidsStarted=0;hardRaid=bossSpawned=bossKilled=false;bossId=null;
        core=logistics=defense=restoration=reconnaissance=vertical=victories=rewardTier=raidTier=score=wave=deaths=0;respiteTicks=0;respitePurchases=nextRaidBonus=0;introRaid=false;
        wavePlayers=1;waveVeteran=0;preparationTicks=raidTicks=waveTicks=0;spawnRemaining=spawnCooldown=breachClock=scanCursor=0;
        health=1000;showBoundary=showBeam=showHurtbox=true;lastAttackTick=-1;victoryRestoration=false;rewards.clear();rewardBox.clearContent();raidType=nextRaidType="normal";participants.clear();raiders.clear();baseCounts.clear();
        setDirty();
    }
    public void finishDecommission() {
        if(!damage.isEmpty()||!destroyedTurrets.isEmpty())throw new IllegalStateException("Pending repairs must remain journaled");
        resetProgress();snapshot.clear();beacon=BlockPos.ZERO;phase="unplaced";setDirty();
    }
    public boolean inside(BlockPos p){
        long dx=p.getX()-beacon.getX(),dz=p.getZ()-beacon.getZ();
        return Math.abs(dx)<=radius() && Math.abs(dz)<=radius() && p.getY()>=beacon.getY()-below() && p.getY()<=beacon.getY()+above();
    }
    public static CampaignData load(CompoundTag n) {
        CampaignData d=new CampaignData();d.raidLimit=n.contains("raidLimit")?Math.max(0,Math.min(10,n.getInt("raidLimit"))):10;d.rewardChest=n.contains("rewardChest")?BlockPos.of(n.getLong("rewardChest")):null;d.phase=n.getString("phase");d.beacon=BlockPos.of(n.getLong("beacon"));
        d.vertical=n.contains("vertical")?Math.max(0,Math.min(4,n.getInt("vertical"))):4;d.introCompleted=!n.contains("introCompleted")||n.getBoolean("introCompleted");d.introRaid=n.getBoolean("introRaid");d.respiteTicks=Math.max(0,n.getLong("respiteTicks"));d.respitePurchases=Math.max(0,Math.min(1000,n.getInt("respitePurchases")));d.nextRaidBonus=Math.max(0,Math.min(3,n.getInt("nextRaidBonus")));
        d.wavePlayers=Math.max(1,Math.min(8,n.getInt("wavePlayers")));d.waveVeteran=Math.max(0,Math.min(3,n.getInt("waveVeteran")));
        d.health=n.getInt("health");d.core=Math.max(0,Math.min(3,n.getInt("core")));d.logistics=n.getInt("logistics");d.defense=n.getInt("defense");d.restoration=n.getInt("restoration");
        d.victories=n.getInt("victories");d.rewardTier=n.getInt("rewardTier");d.raidTier=n.getInt("raidTier");d.score=n.getInt("score");d.wave=n.getInt("wave");d.deaths=n.getInt("deaths");
        d.waveTicks=n.getLong("waveTicks");d.reconnaissance=n.getInt("reconnaissance");d.campaignSerial=n.getLong("campaignSerial");
        d.preparationTicks=n.getLong("preparationTicks");d.raidTicks=n.getLong("raidTicks");d.spawnRemaining=n.getInt("spawnRemaining");d.spawnCooldown=n.getInt("spawnCooldown");d.scanCursor=n.getInt("scanCursor");d.showBoundary=n.getBoolean("showBoundary");d.showBeam=!n.contains("showBeam")||n.getBoolean("showBeam");d.showHurtbox=!n.contains("showHurtbox")||n.getBoolean("showHurtbox");
        for(Tag t:n.getList("participants",Tag.TAG_STRING))d.participants.add(UUID.fromString(t.getAsString()));
        for(Tag t:n.getList("raiders",Tag.TAG_STRING))d.raiders.add(UUID.fromString(t.getAsString()));
        ListTag palette=n.getList("palette",Tag.TAG_COMPOUND);List<BlockState> states=new ArrayList<>();
        for(Tag t:palette)states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),(CompoundTag)t));
        long[] positions=n.getLongArray("positions");int[] indexes=n.getIntArray("states");
        for(int i=0;i<Math.min(positions.length,indexes.length);i++)if(indexes[i]>=0&&indexes[i]<states.size())d.snapshot.put(positions[i],states.get(indexes[i]));
        for(Tag t:n.getList("damage",Tag.TAG_COMPOUND)) {
            CompoundTag c=(CompoundTag)t;
            d.damage.put(c.getLong("pos"),new Damage(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),c.getCompound("state")),c.contains("entity")?c.getCompound("entity"):null));
        }
        for(Tag t:n.getList("rewards",Tag.TAG_COMPOUND))d.rewards.add(((CompoundTag)t).copy());
        for(Tag t:n.getList("rewardBox",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;int slot=c.getInt("Slot");if(slot>=0&&slot<54)d.rewardBox.setItem(slot,net.minecraft.world.item.ItemStack.of(c));}
        d.raidType=n.contains("raidType")?n.getString("raidType"):"normal";d.nextRaidType=n.contains("nextRaidType")?n.getString("nextRaidType"):"normal";
        for(Tag t:n.getList("destroyedTurrets",Tag.TAG_COMPOUND))d.destroyedTurrets.add(((CompoundTag)t).copy());
        d.victoryRestoration=n.getBoolean("victoryRestoration");d.raidsStarted=n.contains("raidsStarted")?n.getInt("raidsStarted"):d.victories;d.hardRaid=n.getBoolean("hardRaid");d.bossSpawned=n.getBoolean("bossSpawned");d.bossKilled=n.getBoolean("bossKilled");d.bossId=n.hasUUID("bossId")?n.getUUID("bossId"):null;
        CompoundTag counts=n.getCompound("baseCounts");for(String key:counts.getAllKeys())d.baseCounts.put(key,counts.getInt(key));
        return d;
    }
    @Override public CompoundTag save(CompoundTag n) {
        n.putInt("raidLimit",raidLimit);if(rewardChest!=null)n.putLong("rewardChest",rewardChest.asLong());n.putString("phase",phase);n.putLong("beacon",beacon.asLong());
        n.putInt("vertical",vertical);n.putBoolean("introCompleted",introCompleted);n.putBoolean("introRaid",introRaid);n.putLong("respiteTicks",respiteTicks);n.putInt("respitePurchases",respitePurchases);n.putInt("nextRaidBonus",nextRaidBonus);
        n.putInt("wavePlayers",wavePlayers);n.putInt("waveVeteran",waveVeteran);
        n.putInt("health",health);n.putInt("core",core);n.putInt("logistics",logistics);n.putInt("defense",defense);n.putInt("restoration",restoration);
        n.putInt("victories",victories);n.putInt("rewardTier",rewardTier);n.putInt("raidTier",raidTier);n.putInt("score",score);n.putInt("wave",wave);n.putInt("deaths",deaths);
        n.putLong("waveTicks",waveTicks);n.putInt("reconnaissance",reconnaissance);n.putLong("campaignSerial",campaignSerial);
        n.putLong("preparationTicks",preparationTicks);n.putLong("raidTicks",raidTicks);n.putInt("spawnRemaining",spawnRemaining);n.putInt("spawnCooldown",spawnCooldown);n.putInt("scanCursor",scanCursor);n.putBoolean("showBoundary",showBoundary);n.putBoolean("showBeam",showBeam);n.putBoolean("showHurtbox",showHurtbox);
        ListTag ps=new ListTag();participants.forEach(u->ps.add(StringTag.valueOf(u.toString())));n.put("participants",ps);
        ListTag rs=new ListTag();raiders.forEach(u->rs.add(StringTag.valueOf(u.toString())));n.put("raiders",rs);
        Map<BlockState,Integer> paletteMap=new HashMap<>();ListTag palette=new ListTag();long[] positions=new long[snapshot.size()];int[] states=new int[snapshot.size()];int cursor=0;
        for(var entry:snapshot.entrySet()) {
            int index=paletteMap.computeIfAbsent(entry.getValue(),s->{int k=palette.size();palette.add(NbtUtils.writeBlockState(s));return k;});
            positions[cursor]=entry.getKey();states[cursor++]=index;
        }
        n.put("palette",palette);n.putLongArray("positions",positions);n.putIntArray("states",states);
        ListTag ds=new ListTag();damage.forEach((p,d)->{CompoundTag c=new CompoundTag();c.putLong("pos",p);c.put("state",NbtUtils.writeBlockState(d.before()));if(d.entity()!=null)c.put("entity",d.entity());ds.add(c);});n.put("damage",ds);
        ListTag boxTag=new ListTag();for(int i=0;i<54;i++)if(!rewardBox.getItem(i).isEmpty()){var c=new CompoundTag();rewardBox.getItem(i).save(c);c.putInt("Slot",i);boxTag.add(c);}n.put("rewardBox",boxTag);n.putString("raidType",raidType);n.putString("nextRaidType",nextRaidType);
        ListTag rewardsTag=new ListTag();rewards.forEach(t->rewardsTag.add(t.copy()));n.put("rewards",rewardsTag);
        ListTag turretTags=new ListTag();destroyedTurrets.forEach(t->turretTags.add(t.copy()));n.put("destroyedTurrets",turretTags);n.putBoolean("victoryRestoration",victoryRestoration);n.putInt("raidsStarted",raidsStarted);n.putBoolean("hardRaid",hardRaid);n.putBoolean("bossSpawned",bossSpawned);n.putBoolean("bossKilled",bossKilled);if(bossId!=null)n.putUUID("bossId",bossId);
        CompoundTag counts=new CompoundTag();baseCounts.forEach(counts::putInt);n.put("baseCounts",counts);
        return n;
    }
}
