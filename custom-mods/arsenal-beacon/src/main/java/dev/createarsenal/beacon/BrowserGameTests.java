package dev.createarsenal.beacon;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class BrowserGameTests {
    @GameTest(template="empty3x3x3",batch="browser014",timeoutTicks=200)
    public static void nativeWeaponTypesCombineWithAgeSearchAndSpecialCategories(GameTestHelper h){
        var p=UpgradeGameTests.player(h,"browser014");var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);
        var source=WeaponPlatform.weaponEntries(p,"",false);var types=new HashMap<String,Integer>();for(var e:source)types.merge(WeaponTypes.of(e),1,Integer::sum);
        int checked=0;for(String type:WeaponBrowser.TYPES){
            var state=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,"",0,0,type,40));int expected=type.equals("all")?source.size():types.getOrDefault(type,0);
            h.assertTrue(state.getInt("total")==expected&&state.getInt("pageSize")==40,"Native category count matches the maintained gun catalogue: "+type);
            var recipes=state.getList("recipes",Tag.TAG_COMPOUND);h.assertTrue(recipes.size()==Math.min(40,expected),"Larger pages contain only the bounded requested recipes");
            for(var tag:recipes){var row=(CompoundTag)tag;var e=source.stream().filter(entry->entry.recipeId().toString().equals(row.getString("recipe"))).findFirst().orElseThrow();h.assertTrue(type.equals("all")||type.equals(WeaponTypes.of(e)),"No wrong-type gun leaks into the filter");checked++;}
            var age2=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,"",2,0,type,40));int eraCount=(int)source.stream().filter(e->e.gate().age()==2&&(type.equals("all")||type.equals(WeaponTypes.of(e)))).count();h.assertTrue(age2.getInt("total")==eraCount,"Type and Age filters intersect");
        }
        var query=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","ak47",0,"",0,0,"rifle",40));h.assertTrue(query.getInt("total")>0,"Full gun/name search composes with native rifle classification");
        for(int special:new int[]{7,8,9}){var state=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,"",special,0,"pistol",40));var all=WeaponPlatform.specialEntries(p,"",special);int expected=special==7?(int)all.stream().filter(e->WeaponTypes.of(e).equals("pistol")).count():all.size();h.assertTrue(state.getInt("total")==expected,"Create weapons filter by type; turrets/supplies stay independent of weapon types");}
        com.mojang.logging.LogUtils.getLogger().info("0.14 native browser coverage: {} type-filtered rows checked; category counts {}",checked,types);h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="browser014",timeoutTicks=100)
    public static void largerPagesAreCompleteBoundedAndPreserveRequestContext(GameTestHelper h){
        var p=UpgradeGameTests.player(h,"pages014");var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);Set<String> expected=new HashSet<>();WeaponPlatform.weaponEntries(p,"",false).forEach(e->expected.add(e.recipeId().toString()));Set<String> actual=new HashSet<>();
        for(int page=0;page<=WeaponBrowser.lastPage(expected.size(),40);page++){var state=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",page,"",0,0,"all",40));h.assertTrue(state.getInt("page")==page&&state.getList("recipes",Tag.TAG_COMPOUND).size()<=40,"Sequential large pages stay bounded");for(var row:state.getList("recipes",Tag.TAG_COMPOUND))h.assertTrue(actual.add(((CompoundTag)row).getString("recipe")),"No duplicated or skipped page entries");}
        h.assertTrue(actual.equals(expected),"Every available gun remains reachable in the larger browser");var clamp=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",Integer.MAX_VALUE,"",0,0,"invalid",Integer.MAX_VALUE));h.assertTrue(clamp.getInt("pageSize")==40&&clamp.getString("weaponType").equals("all")&&clamp.getInt("page")==WeaponBrowser.lastPage(expected.size(),40),"Untrusted filters and page sizes are clamped server-side");
        var request=new WeaponPlatform.Request("craft","1911",3,"tacz:m1911",2,123,"pistol",22);var buf=new FriendlyByteBuf(Unpooled.buffer());try{WeaponPlatform.Request.encode(request,buf);h.assertTrue(request.equals(WeaponPlatform.Request.decode(buf))&&!buf.isReadable(),"Search, Age, type, page size and target survive craft/request wire encoding");}finally{buf.release();}
        var state=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,"",0,0,"all",40));var packet=new FriendlyByteBuf(Unpooled.buffer());try{WeaponPlatform.State.encode(new WeaponPlatform.State(state,false,""),packet);h.assertTrue(packet.readableBytes()<262144,"Even the largest page remains comfortably below the 1MB Minecraft packet limit");}finally{packet.release();}h.succeed();
    }
}
