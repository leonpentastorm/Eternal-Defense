package dev.eternaldefense.beacon;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The Loadout Station's rules: kits in coins, coin slots, where a coin comes from, the cooldown, the lock, walking in, the swap, the flare's coin. */
class LoadoutRulesTest {
    static CompoundTag stack(String id,int count){var t=new CompoundTag();t.putString("id",id);t.putByte("Count",(byte)count);return t;}
    /** A TaCZ gun as it is saved: its gun id, the round in the chamber, the loaded ammo and two attachments (each a saved stack). */
    static CompoundTag gun(){
        var t=stack("tacz:modern_kinetic_gun",1);var tag=new CompoundTag();
        tag.putString("GunId","tacz:ak47");tag.putInt("GunCurrentAmmoCount",30);tag.putBoolean("HasBulletInBarrel",true);tag.putString("GunFireMode","AUTO");
        var scope=stack("tacz:attachment",1);var st=new CompoundTag();st.putString("AttachmentId","tacz:scope_acog_ta31");scope.put("tag",st);tag.put("AttachmentSCOPE",scope);
        var grip=stack("tacz:attachment",1);var gt=new CompoundTag();gt.putString("AttachmentId","tacz:grip_vertical");grip.put("tag",gt);tag.put("AttachmentGRIP",grip);
        t.put("tag",tag);return t;
    }
    static List<CompoundTag> fullKit(){
        var kit=new ArrayList<CompoundTag>();
        kit.add(stack("minecraft:iron_boots",1));kit.add(stack("minecraft:iron_leggings",1));
        var chest=stack("minecraft:diamond_chestplate",1);var ench=new CompoundTag();var list=new ListTag();var e=new CompoundTag();e.putString("id","minecraft:protection");e.putShort("lvl",(short)4);list.add(e);ench.put("Enchantments",list);ench.putInt("Damage",17);chest.put("tag",ench);kit.add(chest);
        kit.add(stack("minecraft:turtle_helmet",1));kit.add(stack("minecraft:shield",1));kit.add(gun());
        var ammo=stack("tacz:ammo",60);var at=new CompoundTag();at.putString("AmmoId","tacz:762x39");ammo.put("tag",at);kit.add(ammo);
        for(int i=0;i<LoadoutRules.HOTBAR-2;i++){var s=stack("minecraft:cooked_beef",10+i);if(i==3){var n=new CompoundTag();var d=new CompoundTag();d.put("Name",StringTag.valueOf("{\"text\":\"Rations\"}"));n.put("display",d);s.put("tag",n);}kit.add(s);}
        return kit;
    }

    @Test void aCoinKeepsAll14SlotsExactly(){
        var kit=fullKit();assertEquals(LoadoutRules.KIT_SLOTS,kit.size());
        var coin=LoadoutRules.pack(kit);var back=LoadoutRules.unpack(coin);
        for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)assertEquals(kit.get(i),back.get(i),"slot "+i);
        assertEquals(LoadoutRules.KIT_SLOTS,LoadoutRules.kitItems(coin));
        // The gun with its attachments, chamber and ammo count, after a save and a load of the coin tag
        var gun=LoadoutRules.unpack(coin.copy()).get(LoadoutRules.HOTBAR_FIRST).getCompound("tag");
        assertEquals("tacz:scope_acog_ta31",gun.getCompound("AttachmentSCOPE").getCompound("tag").getString("AttachmentId"));
        assertEquals(30,gun.getInt("GunCurrentAmmoCount"));assertTrue(gun.getBoolean("HasBulletInBarrel"));
    }
    @Test void emptySlotsStayEmptyAndCoinsOfTheSameKitAreEqual(){
        var kit=new ArrayList<CompoundTag>();for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)kit.add(new CompoundTag());
        kit.set(LoadoutRules.OFFHAND_SLOT,stack("minecraft:torch",32));kit.set(LoadoutRules.HOTBAR_FIRST+8,stack("minecraft:bread",5));
        var coin=LoadoutRules.pack(kit);assertEquals(2,LoadoutRules.kitItems(coin));
        var back=LoadoutRules.unpack(coin);for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)assertEquals(kit.get(i),back.get(i));
        assertEquals(coin,LoadoutRules.pack(LoadoutRules.unpack(coin)),"packing the same kit again gives an identical coin: they stack");
    }
    @Test void coinsAndFilledContainersAreRefused(){
        int limit=64*1024;
        var kit=fullKit();assertNull(LoadoutRules.kitProblem(kit,limit));
        var withCoin=fullKit();withCoin.set(LoadoutRules.HOTBAR_FIRST+3,stack(LoadoutRules.COIN_ID,1));assertEquals("coin",LoadoutRules.kitProblem(withCoin,limit));
        var shulker=stack("minecraft:shulker_box",1);var be=new CompoundTag();var items=new ListTag();items.add(stack("minecraft:diamond",64));be.put("Items",items);var st=new CompoundTag();st.put("BlockEntityTag",be);shulker.put("tag",st);
        var withShulker=fullKit();withShulker.set(LoadoutRules.HOTBAR_FIRST+4,shulker);assertEquals("container",LoadoutRules.kitProblem(withShulker,limit));
        var bundle=stack("minecraft:bundle",1);var bt=new CompoundTag();var bi=new ListTag();bi.add(stack("minecraft:arrow",16));bt.put("Items",bi);bundle.put("tag",bt);
        var withBundle=fullKit();withBundle.set(LoadoutRules.OFFHAND_SLOT,bundle);assertEquals("container",LoadoutRules.kitProblem(withBundle,limit));
        var emptyShulker=fullKit();emptyShulker.set(LoadoutRules.HOTBAR_FIRST+4,stack("minecraft:shulker_box",1));assertNull(LoadoutRules.kitProblem(emptyShulker,limit),"an empty box is only a box");
        assertFalse(LoadoutRules.carriesItems(gun()),"a gun's attachments are its own parts, not a container");
        var none=new ArrayList<CompoundTag>();for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)none.add(new CompoundTag());assertEquals("empty",LoadoutRules.kitProblem(none,limit));
    }
    @Test void theSizeGuardUsesTheSavedSize(){
        var kit=fullKit();int size=LoadoutRules.bytes(LoadoutRules.pack(kit));
        assertTrue(size>200&&size<4096,"a full kit is a few hundred bytes to a few KB: "+size);
        assertNull(LoadoutRules.kitProblem(kit,size));assertEquals("too_big",LoadoutRules.kitProblem(kit,size-1));
        var huge=fullKit();var book=stack("minecraft:writable_book",1);var t=new CompoundTag();var pages=new ListTag();for(int i=0;i<100;i++)pages.add(StringTag.valueOf("x".repeat(1000)));t.put("pages",pages);book.put("tag",t);huge.set(LoadoutRules.HOTBAR_FIRST+8,book);
        assertEquals("too_big",LoadoutRules.kitProblem(huge,64*1024),"a kit over 64 KB is refused");
        var unsaved=fullKit();var paper=stack("minecraft:paper",1);var pt=new CompoundTag();pt.putString("Filler","x".repeat(70*1024));paper.put("tag",pt);unsaved.set(LoadoutRules.HOTBAR_FIRST+8,paper);
        assertTrue(LoadoutRules.bytes(LoadoutRules.pack(unsaved))>70*1024,"a string too long to save counts in full");
        assertEquals("too_big",LoadoutRules.kitProblem(unsaved,64*1024),"a 70 KB string is refused (the game would save it empty)");
    }
    @Test void aSlotRemembersItsKind(){
        var a=LoadoutRules.pack(fullKit());var other=fullKit();other.set(LoadoutRules.OFFHAND_SLOT,stack("minecraft:totem_of_undying",1));var b=LoadoutRules.pack(other);
        assertTrue(LoadoutRules.accepts(null,a),"an empty slot without a kind takes any coin");
        assertTrue(LoadoutRules.accepts(a,a.copy()),"a slot takes more of its kind");
        assertFalse(LoadoutRules.accepts(a,b),"and nothing else");
        assertFalse(LoadoutRules.accepts(null,null));
    }
    @Test void theCoinComesFromTheSlotThenTheHandThenTheHub(){
        assertEquals(LoadoutRules.CoinSource.SLOT,LoadoutRules.source(3,true,true));
        assertEquals(LoadoutRules.CoinSource.HAND,LoadoutRules.source(0,true,true));
        assertEquals(LoadoutRules.CoinSource.HUB,LoadoutRules.source(0,false,true));
        assertEquals(LoadoutRules.CoinSource.NONE,LoadoutRules.source(0,false,false));
    }
    @Test void theCooldownSurvivesASave(){
        long now=10_000;long ready=LoadoutRules.readyAt(now,LoadoutRules.COOLDOWN_SECONDS);
        assertEquals(now+600,ready);assertEquals(30,LoadoutRules.secondsLeft(ready,now));assertEquals(1,LoadoutRules.secondsLeft(ready,ready-1));assertEquals(0,LoadoutRules.secondsLeft(ready,ready));
        var players=new LoadoutData.LoadoutPlayers();var id=UUID.randomUUID();players.setReadyAt(id,ready);players.setActive(id,7);
        var again=new LoadoutData.LoadoutPlayers();again.load(players.save());
        assertEquals(ready,again.readyAt(id),"a relog (or a restart) keeps the cooldown");assertEquals(7,again.active(id),"and the active slot");
        var someoneElse=UUID.randomUUID();assertEquals(0,again.active(someoneElse),"each player has their own active slot");
        again.setActive(someoneElse,99);assertEquals(LoadoutRules.COIN_SLOTS-1,again.active(someoneElse));
    }
    @Test void oneAtATimeAndOnePlayerAtATime(){
        var byMachine=new HashMap<UUID,Object>();var byPlayer=new HashMap<UUID,Object>();UUID station=UUID.randomUUID(),other=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        assertNull(LoadoutRules.lockProblem(byMachine,byPlayer,station,a));
        byMachine.put(station,a);byPlayer.put(a,station);
        assertEquals("busy",LoadoutRules.lockProblem(byMachine,byPlayer,station,b),"the second player racing for the door is refused");
        assertEquals("already",LoadoutRules.lockProblem(byMachine,byPlayer,other,a),"one print at a time per player");
        assertNull(LoadoutRules.lockProblem(byMachine,byPlayer,other,b));
    }
    @Test void walkingInNeedsTheFrontTheReachAndAClearLine(){
        double reach=LoadoutRules.leafReach();assertTrue(reach<=-23.66&&reach>-26,"the open leaves reach about half a block out (the right one -23.66, CHECKS.txt): "+reach);
        assertNull(LoadoutRules.approach(new double[]{8,-16,-40},reach),"straight in front, 1.8 blocks out");
        assertNull(LoadoutRules.approach(new double[]{14,-16,-60},reach),"a little to the side further out");
        assertEquals("front",LoadoutRules.approach(new double[]{8,-16,20},reach),"behind the machine");
        assertEquals("far",LoadoutRules.approach(new double[]{8,-16,-90},reach),"more than 4 blocks out");
        assertEquals("aside",LoadoutRules.approach(new double[]{-30,-16,-30},reach),"the line would cross the open leaf");
        var blocked=new double[]{0,0,-1.5};
        assertFalse(LoadoutRules.clear(new double[]{0,0,-3},new double[]{0,0,0},p->Math.abs(p[2]-blocked[2])<.05),"a block on the line");
        assertTrue(LoadoutRules.clear(new double[]{0,0,-3},new double[]{0,0,0},p->false));
        assertArrayEquals(new double[]{8,-16,-40},LoadoutRules.exitPoint(),1e-9,"the cell 2 blocks in front of the doors");
    }
    @Test void theTimelineIsSixToSevenSeconds(){
        var stand=new double[]{.5,-.84375,.3125};var exit=new double[]{.5,-1,-2.5};
        var near=LoadoutRules.timeline(LoadoutRules.distance(new double[]{.5,-1,-1.5},stand),LoadoutRules.distance(stand,exit));
        assertEquals(LoadoutRules.MIN_ENTRY,near.entry(),"a short walk still takes 12 ticks");
        var far=LoadoutRules.timeline(LoadoutRules.distance(new double[]{.5,-1,-4.2},stand),LoadoutRules.distance(stand,exit));
        for(var tl:List.of(near,far)){double seconds=tl.end()/20.0;assertTrue(seconds>=5.9&&seconds<=7.0,"total "+seconds+" s");}
        assertEquals(LoadoutModel.DOOR_OPEN,near.door(near.entry()-.001f),.5f);
        assertEquals(0f,near.door(near.cycleStart()+20),1e-6f);assertEquals(LoadoutModel.DOOR_OPEN,near.door(near.exitStart()+1),1e-6f);
        assertEquals(0f,near.door(near.end()),1e-6f);
        assertTrue(near.holding(near.swapTick())&&!near.holding(near.closeStart()),"held through the walk out, free while the doors close");
        assertEquals(near.cycleStart()+40,near.swapTick());
        var at=LoadoutRules.position(near,near.swapTick(),new double[]{.5,-1,-1.5},stand,exit);assertArrayEquals(stand,at,1e-9,"inside at the swap");
        var out=LoadoutRules.position(near,near.closeStart(),new double[]{.5,-1,-1.5},stand,exit);assertArrayEquals(exit,out,1e-9,"out in front when let go");
        for(float t=0;t<far.entry();t++){var a=LoadoutRules.position(far,t,new double[]{.5,-1,-4.2},stand,exit);var b=LoadoutRules.position(far,t+1,new double[]{.5,-1,-4.2},stand,exit);assertTrue(LoadoutRules.distance(a,b)<=LoadoutRules.WALK_SPEED+1e-9,"walks no faster than 0.2 blocks a tick");}
    }
    @Test void aLateClientCatchesUpWithoutAJump(){
        double[] stand={.5,-.84375,.3125},from={.5,-1,-4.2};var tl=LoadoutRules.timeline(LoadoutRules.distance(from,stand),2.8);
        double[] p=from.clone();double lag=0;
        for(int t=4;t<tl.entry();t++){var next=LoadoutRules.toward(p,LoadoutRules.position(tl,t+1,from,stand,stand));
            assertTrue(LoadoutRules.distance(p,next)<=LoadoutRules.CATCH_UP+1e-9,"never more than 0.45 blocks in a tick");p=next;lag=LoadoutRules.distance(p,LoadoutRules.position(tl,t+1,from,stand,stand));
            assertTrue(lag<LoadoutRules.DRIFT,"stays within the server's drift allowance: "+lag);}
        assertEquals(0,lag,1e-9,"on the path before the walk ends");
        assertArrayEquals(stand,LoadoutRules.toward(new double[]{.5,-.84375,.1},stand),1e-12,"near the path: on it at once");
    }
    @Test void aClientAFewTicksBehindIsNotPutBack(){
        double[] stand={.5,-.84375,.3125},from={.5,-1,-4.2},exit={.5,-1,-2.5};var tl=LoadoutRules.timeline(LoadoutRules.distance(from,stand),LoadoutRules.distance(stand,exit));
        for(int t=0;t<tl.closeStart();t++)for(int behind=0;behind<=LoadoutRules.LAG_TICKS;behind++){
            var client=LoadoutRules.position(tl,Math.max(0,t-behind),from,stand,exit);
            assertTrue(LoadoutRules.drift(tl,t,client,from,stand,exit)<1e-9,"a client "+behind+" ticks behind is on the path at tick "+t);}
        var aside=LoadoutRules.position(tl,10,from,stand,exit).clone();aside[0]+=1;
        assertTrue(LoadoutRules.drift(tl,10,aside,from,stand,exit)>LoadoutRules.DRIFT,"a block to the side is put back");
    }
    @Test void theSwapStopsBeforeAnythingChanges(){
        assertNull(LoadoutRules.swapProblem(true,true,true,true,true,true));
        assertEquals("logout",LoadoutRules.swapProblem(false,true,true,true,true,true));
        assertEquals("death",LoadoutRules.swapProblem(true,false,true,true,true,true));
        assertEquals("dimension",LoadoutRules.swapProblem(true,true,false,true,true,true));
        assertEquals("broken",LoadoutRules.swapProblem(true,true,true,false,true,true));
        assertEquals("no_coin",LoadoutRules.swapProblem(true,true,true,true,false,true));
        assertEquals("no_room",LoadoutRules.swapProblem(true,true,true,true,true,false));
    }
    @Test void aLoadoutCallTakesItsCoinOnlyWhenTheCannonFires(){
        var call=new LoadoutRules.Call();int[] coins={1};
        assertEquals(0,call.taken(),"throwing takes nothing");
        assertTrue(call.fire(()->coins[0]-->0));assertEquals(1,call.taken());assertEquals(0,coins[0]);
        assertTrue(call.fire(()->{throw new AssertionError("a second shot never takes another coin");}));
        var empty=new LoadoutRules.Call();int[] none={0};
        assertFalse(empty.fire(()->none[0]-->0),"no coin when the cannon fires: the call fails");
        assertEquals(0,empty.taken());assertFalse(empty.fired());
    }
    @Test void theCapsuleDoorSlidesOpenAndShut(){
        assertEquals(0f,LoadoutRules.capsuleDoor(100,-1,-1),1e-6f);
        assertEquals(0f,LoadoutRules.capsuleDoor(100,100,-1),1e-6f);
        assertEquals(LoadoutModel.CAPSULE_DOOR_TRAVEL/2,LoadoutRules.capsuleDoor(105,100,-1),1e-6f);
        assertEquals(LoadoutModel.CAPSULE_DOOR_TRAVEL,LoadoutRules.capsuleDoor(130,100,-1),1e-6f);
        assertEquals(LoadoutModel.CAPSULE_DOOR_TRAVEL,LoadoutRules.capsuleDoor(130,100,130),1e-6f);
        assertEquals(0f,LoadoutRules.capsuleDoor(140,100,130),1e-6f);
        assertEquals(.5f,LoadoutRules.capsuleFold(105,100),1e-6f);assertEquals(0f,LoadoutRules.capsuleFold(105,-1),1e-6f);
    }
    @Test void theCapsuleStandsOnItsOwnCollision(){
        for(var f:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){
            assertFalse(LoadoutModel.capsuleBoxes(0,f).isEmpty());assertFalse(LoadoutModel.capsuleBoxes(1,f).isEmpty(),"the nose reaches into the upper cell");
            for(int dy=0;dy<2;dy++)for(var b:LoadoutModel.capsuleBoxes(dy,f))assertTrue(b[0]>=0&&b[2]>=0&&b[3]<=16&&b[5]<=16&&b[1]>=0&&b[4]<=16);
        }
    }
}
