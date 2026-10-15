package dev.eternaldefense.beacon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Operator commands for trying the raid features: elites, modifiers, the raid timer, drops and the gun balancer */
final class OperatorCommands {
    private OperatorCommands(){}
    static List<LiteralArgumentBuilder<CommandSourceStack>> all(){return List.of(elite(),raidMod(),raidTimer(),raidInfo(),balance(),ages());}
    private static LiteralArgumentBuilder<CommandSourceStack> op(String name){return Commands.literal(name).requires(s->s.hasPermission(2));}
    private static void say(CommandContext<CommandSourceStack> c,String text){c.getSource().sendSuccess(()->Component.literal(text),false);}
    private static int fail(CommandContext<CommandSourceStack> c,String text){c.getSource().sendFailure(Component.literal(text));return 0;}
    private static String pct(double v){return String.format(Locale.ROOT,"%.1f%%",v*100);}

    // /eternaldefense elite <kind> [level] [mob]: an elite 6 blocks in front of you (in a raid it joins the raid)
    static LiteralArgumentBuilder<CommandSourceStack> elite(){
        return op("elite").then(Commands.argument("kind",StringArgumentType.word()).suggests((c,b)->{for(var k:Elites.Kind.values())b.suggest(k.id);return b.buildFuture();})
            .executes(c->elite(c,1,null))
            .then(Commands.argument("level",IntegerArgumentType.integer(1,5)).executes(c->elite(c,IntegerArgumentType.getInteger(c,"level"),null))
                .then(Commands.argument("mob",ResourceLocationArgument.id()).suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                    .executes(c->elite(c,IntegerArgumentType.getInteger(c,"level"),ResourceLocationArgument.getId(c,"mob"))))));
    }
    private static int elite(CommandContext<CommandSourceStack> c,int level,ResourceLocation mobId) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=c.getSource().getPlayerOrException();var l=p.serverLevel();
        var kind=Elites.Kind.of(StringArgumentType.getString(c,"kind"));
        if(kind==null)return fail(c,"Unknown elite. Use one of: "+String.join(", ",Arrays.stream(Elites.Kind.values()).map(k->k.id).toList()));
        EntityType<?> type=mobId!=null?BuiltInRegistries.ENTITY_TYPE.getOptional(mobId).orElse(null):kind==Elites.Kind.LONG_SHOT?EntityType.SKELETON:EntityType.ZOMBIE;
        if(type==null)return fail(c,"Unknown mob "+mobId);
        var look=p.getLookAngle();var at=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,BlockPos.containing(p.getX()+look.x*6,p.getY(),p.getZ()+look.z*6));
        var d=CampaignData.get(l.getServer().overworld());boolean raid=d.phase.equals("raid")&&l==l.getServer().overworld();
        Mob mob;
        if(raid)mob=EternalDefense.raider(l,d,type,"grunt",d.raidType,at);
        else{
            if(!(type.create(l) instanceof Mob m))return fail(c,mobId+" is not a mob");
            m.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,p.getYRot()+180,0);m.setPersistenceRequired();
            mob=l.addFreshEntity(m)?m:null;
        }
        if(mob==null)return fail(c,"No room for it there.");
        if(kind==Elites.Kind.LONG_SHOT&&!Elites.ranged(mob))say(c,"A Long Shot only shoots as a skeleton or a pillager (raids never make this one).");
        if(Elites.melee(kind)&&Elites.shooter(mob))say(c,"This power works only on hits it lands itself; a mob that shoots rarely lands one (raids never make this one).");
        Elites.make(mob,kind,kind==Elites.Kind.BADASS?level:1,0);if(!raid)Elites.LOOSE.add(mob.getUUID());
        RaidTypes.broadcast(l.getServer().overworld(),d);
        float size=Elites.scale(kind,level);double health=1+Elites.healthBonus(kind,kind==Elites.Kind.BADASS?level:1);
        String slow=kind==Elites.Kind.BADASS?", "+String.format(Locale.ROOT,"%.2f",Elites.slowdown(level,mob instanceof net.minecraft.world.entity.monster.Creeper)*100).replaceAll("\\.?0+$","")+"% slower":"";
        String blast=mob instanceof net.minecraft.world.entity.monster.Creeper creeper?", blast radius "+Elites.blast(creeper):"";
        say(c,Elites.title(kind,level,mob).getString()+": health "+Math.round(mob.getMaxHealth())+" ("+String.format(Locale.ROOT,"%.1f",health)+"x), size "+String.format(Locale.ROOT,"%.1f",size)+"x"+slow+blast+", drops "+Elites.reward(kind,level,0)+" Ardent"+(raid?", in the raid.":" (not a raid mob)."));
        return 1;
    }

    // /eternaldefense raid-mod list | clear | special <type> | <id>: the next raid's modifiers, anywhere
    static LiteralArgumentBuilder<CommandSourceStack> raidMod(){
        return op("raid-mod")
            .then(Commands.literal("list").executes(OperatorCommands::listMods))
            .then(Commands.literal("clear").executes(c->{var d=campaign(c);d.chosenMods.clear();d.nextRaidType="normal";d.setDirty();say(c,"Next raid: ordinary, no modifiers.");return 1;}))
            .then(Commands.literal("special").then(Commands.argument("type",StringArgumentType.word()).suggests((c,b)->{b.suggest("normal");for(var t:RaidTypes.SPECIAL)b.suggest(t);return b.buildFuture();}).executes(c->{
                var d=campaign(c);if(!d.phase.equals("preparation"))return fail(c,"Choose the next raid between raids (now: "+d.phase+").");
                String type=StringArgumentType.getString(c,"type");if(!RaidTypes.special(type)&&!type.equals("normal"))return fail(c,"Unknown raid. Use normal, "+String.join(", ",RaidTypes.SPECIAL)+".");
                d.nextRaidType=type;d.setDirty();return listMods(c);})))
            .then(Commands.argument("id",StringArgumentType.word()).suggests((c,b)->{for(var m:RaidModifiers.MODS)b.suggest(m.id());return b.buildFuture();}).executes(c->{
                var d=campaign(c);if(!d.phase.equals("preparation"))return fail(c,"Choose modifiers between raids (now: "+d.phase+").");
                say(c,RaidModifiers.flip(d,StringArgumentType.getString(c,"id")));return listMods(c);}));
    }
    private static int listMods(CommandContext<CommandSourceStack> c){
        var d=campaign(c);boolean prep=d.phase.equals("preparation");var mods=prep?d.chosenMods:d.activeMods;String type=prep?d.nextRaidType:d.raidType;
        say(c,(prep?"Next raid (started by Start raid or by its timer)":"This raid")+": "+RaidTypes.name(type).getString()+", payout +"+RaidModifiers.payoutPercent(mods)+"%, Ardent drops +"+RaidModifiers.dropPercent(mods,type)+"% of the base chance");
        for(var m:RaidModifiers.MODS)say(c,(mods.contains(m.id())?" [x] ":" [ ] ")+m.id()+": payout +"+m.payout()+"%, drops +"+m.drops()+"%");
        say(c,"A special raid (raid-mod special <type>) adds drops +"+RaidModifiers.SPECIAL_DROPS+"%");
        return 1;
    }

    // /eternaldefense raid-timer <seconds>: the time until the next raid is due
    static LiteralArgumentBuilder<CommandSourceStack> raidTimer(){
        return op("raid-timer").then(Commands.argument("seconds",IntegerArgumentType.integer(0)).executes(c->{
            var l=c.getSource().getServer().overworld();var d=CampaignData.get(l);
            if(!d.phase.equals("preparation"))return fail(c,"The raid timer runs between raids (now: "+d.phase+").");
            int seconds=IntegerArgumentType.getInteger(c,"seconds");
            d.respiteTicks=0;d.preparationTicks=Math.max(0,Rules.intervalDays(d.rewardTier)*24000L-seconds*20L);d.setDirty();
            long time=Math.floorMod(l.getDayTime(),24000L);
            say(c,"The raid is due in "+seconds+" s (bought break time cleared). It then starts at night (day time "+Rules.RAID_HOUR_FROM+" to "+Rules.RAID_HOUR_UNTIL+", now "+time+") with a player near the beacon"+(Rules.raidHour(l.getDayTime())?".":": /time set "+(Rules.RAID_HOUR_FROM+1000)+"."));
            BeaconNetwork.syncNearby(l,d);return 1;}));
    }

    // /eternaldefense raid-info: what the next raid (or the running one) brings, pays and drops
    static LiteralArgumentBuilder<CommandSourceStack> raidInfo(){
        return op("raid-info").executes(c->{
            var server=c.getSource().getServer();var l=server.overworld();var d=CampaignData.get(l);
            boolean prep=d.phase.equals("preparation");var mods=prep?d.chosenMods:d.activeMods;String type=prep?d.nextRaidType:d.raidType;
            int level=BeaconNetwork.prospectiveTier(server,d),players=Math.max(1,RaidBalance.defenders(l,d)),veteran=Rules.veteranPressure(level,d.victories);
            say(c,"Phase "+d.phase+" | "+(prep?"next":"this")+" raid level "+level+" ("+RaidTypes.name(type).getString()+") | "+Rules.waves(level)+" waves | wave 1 "+RaidModifiers.attackers(Rules.waveEnemies(level,1,players,veteran,false),type,mods)+" mobs for "+players+" defender(s)");
            say(c,"Elites "+String.format(Locale.ROOT,"%.1f",RaidModifiers.elites(level,mods))+" a wave on average"+(level<5?", at least one in the raid":"")+" | modifiers "+(mods.isEmpty()?"none":String.join(", ",mods)));
            double base=DefenseConfig.ENERGY_CHANCE.get(),mult=ArdentEnergy.multiplier(DefenseConfig.MARKED_MULTIPLIER.get(),RaidModifiers.dropPercent(mods,type),0);
            say(c,"Ardent from a red-marked mob: "+pct(base)+" x "+String.format(Locale.ROOT,"%.2f",mult)+" = "+pct(base*mult)+" (+"+pct(DefenseConfig.LOOTING_BONUS.get())+" a Looting level) | raid pay "+RaidModifiers.raised(Rules.raidArdent(level),RaidModifiers.payoutPercent(mods))+" Ardent + 1 a wave with an elite"+(RaidTypes.special(type)?" + "+RaidTypes.bonusEnergy(level)+" special":""));
            if(d.phase.equals("raid")){
                var kinds=new TreeMap<String,Integer>();int alive=0;
                for(var id:d.raiders)if(l.getEntity(id) instanceof Mob m&&m.isAlive()){alive++;var k=Elites.kind(m);if(k!=null)kinds.merge(k.id+(k==Elites.Kind.BADASS?" "+Elites.ROMAN[Math.max(1,Math.min(5,m.getPersistentData().getInt(Elites.LEVEL)))]:""),1,Integer::sum);}
                say(c,"Wave "+d.wave+"/"+Rules.waves(d.raidTier)+" | to spawn "+d.spawnRemaining+" | alive "+alive+" | elites alive "+kinds+" | elite waves so far "+d.eliteWaves);
            }
            return 1;});
    }

    // /eternaldefense balance [gun]: Age medians and the guns that need a rare part, or one gun's numbers
    static LiteralArgumentBuilder<CommandSourceStack> balance(){
        return op("balance").executes(c->{
                var rm=c.getSource().getServer().getRecipeManager();var med=CraftBalance.medians(rm);var s=CraftBalance.settings();
                var line=new StringBuilder("Age medians (damage a second, guns):");
                for(int a=1;a<=5;a++)line.append(" ").append(a).append("=").append(med.containsKey(a)?String.format(Locale.ROOT,"%.1f",med.get(a)):"-").append(" (").append(CraftBalance.peers().getOrDefault(a,0)).append(")");
                say(c,line.toString());
                var strong=new ArrayList<String>();
                for(var e:WeaponPlatform.entries(rm,"gun","")){if(CreateUnlocks.armory(e))continue;double r=CraftBalance.ratio(e);if(r<s.outlierFrom())continue;
                    var rare=CraftBalance.surcharge(e,List.of());int units=CraftBalance.units(r,s.outlierFrom(),s.step(),s.maxUnits());
                    strong.add(String.format(Locale.ROOT,"%s Age %d x%.2f %s",CraftBalance.gunId(e),e.gate().age(),r,rare==null?"":units+" part(s)"));}
                say(c,strong.size()+" guns at "+s.outlierFrom()+"x their Age's median or more (each needs a rare part unless its recipe has one):");
                strong.stream().limit(20).forEach(t->say(c," "+t));if(strong.size()>20)say(c," ... and "+(strong.size()-20)+" more (all in the server log)");
                com.mojang.logging.LogUtils.getLogger().info("[Eternal Defense] strong guns: {}",strong);
                return 1;})
            .then(Commands.argument("gun",ResourceLocationArgument.id()).executes(c->{
                var rm=c.getSource().getServer().getRecipeManager();String id=ResourceLocationArgument.getId(c,"gun").toString();
                var e=WeaponPlatform.entries(rm,"gun","").stream().filter(x->CraftBalance.gunId(x).equals(id)||x.recipeId().toString().equals(id)).findFirst().orElse(null);
                if(e==null)return fail(c,"No gun table recipe makes "+id);
                var med=CraftBalance.medians(rm).get(e.gate().age());double power=CraftBalance.power(CraftBalance.gunId(e)),r=CraftBalance.ratio(e);
                say(c,id+": Age "+e.gate().age()+(CraftBalance.override(e.gate().id(),e.recipeId().toString(),id)!=null?" (set by an Age line)":"")+String.format(Locale.ROOT,", %.1f damage a second, Age median %s, %.2f times",power,med==null?"-":String.format(Locale.ROOT,"%.1f",med),r));
                try{var costs=WeaponPlatform.costs(e);var b=new StringBuilder("Costs:");for(var cost:costs){var items=cost.ingredient().getItems();b.append(" ").append(cost.count()).append(" ").append(items.length==0?"?":BuiltInRegistries.ITEM.getKey(items[0].getItem()).getPath()).append(",");}say(c,b.substring(0,b.length()-1));}
                catch(ReflectiveOperationException ex){return fail(c,"Its recipe could not be read.");}
                return 1;}));
    }

    // /eternaldefense ages [reload]: the Ages set by hand (data and config/eternal-defense-ages.json)
    static LiteralArgumentBuilder<CommandSourceStack> ages(){
        return op("ages").executes(c->{var o=new TreeMap<>(CraftBalance.overrides());say(c,o.size()+" Ages set by hand: "+o);return 1;})
            .then(Commands.literal("reload").executes(c->{int n=CraftBalance.reload();say(c,"Read again: "+n+" Ages set by hand (config/eternal-defense-ages.json and the mod's own). The tables use them at once; the recipe rules after /reload.");return 1;}));
    }

    private static CampaignData campaign(CommandContext<CommandSourceStack> c){return CampaignData.get(c.getSource().getServer().overworld());}
}
