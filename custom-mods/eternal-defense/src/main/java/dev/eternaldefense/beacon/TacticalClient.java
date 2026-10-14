package dev.eternaldefense.beacon;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side of Tactical Operations (0.0.20): the shared mission for the status card, the satellite images, and the Command Table screen
 * (a radar of the region with an X per offer, and the offer cards). The server decides everything; this only shows it and sends menu buttons.
 */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TacticalClient {
    private TacticalClient(){}
    @SubscribeEvent public static void screens(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(EternalDefense.TABLE_MENU.get(),TableScreen::new));}

    // ---- the mission line ----------------------------------------------------------------------------------------------------------------
    static Hunts.MissionSync mission=new Hunts.MissionSync(false,"","",0,0,0,0,false,0);
    static void mission(Hunts.MissionSync m){mission=m;}
    static boolean missionActive(){return mission.active();}
    /** An offer's class as a rarity on the table: Patrol blue, Garrison purple, Warlord gold (the chosen X is red). */
    static int kindColor(String kind){return switch(kind){case "garrison"->Ui.EPIC_PURPLE;case "warlord"->Ui.LEGEND_GOLD;default->Ui.RARE_BLUE;};}
    /** The mission's map or the Recovery Shovel in either hand: the Tactical Operations line shows only then (0.0.21). */
    static boolean holdingMissionItem(){
        var p=Minecraft.getInstance().player;if(p==null)return false;
        for(var s:List.of(p.getMainHandItem(),p.getOffhandItem()))
            if(s.is(EternalDefense.CONTROLLER.get())||Hunts.mapMission(s)==mission.id()&&mission.id()!=0)return true;
        return false;
    }
    /** "OP IRON HOUND | Garrison | 640 m NE | 7/14 left": distance and bearing from where this player stands. */
    static Component missionLine(){
        var p=Minecraft.getInstance().player;if(p==null||!mission.active())return Component.empty();
        int distance=(int)Math.round(Math.hypot(mission.x()+.5-p.getX(),mission.z()+.5-p.getZ()));
        String compass=TacticalRules.compass(TacticalRules.bearing(p.getX(),p.getZ(),mission.x()+.5,mission.z()+.5));
        Component kind=Ui.t("tactical.class."+mission.kind());
        return mission.engaged()?Ui.t("hud.mission",mission.codename(),kind,distance,compass,mission.remaining(),mission.total())
            :Ui.t("hud.mission.waiting",mission.codename(),kind,distance,compass);
    }

    // ---- the satellite images ------------------------------------------------------------------------------------------------------------
    private static final ChunkedPayload.Assembler ASSEMBLER=new ChunkedPayload.Assembler();
    static final int SIZE=TacticalRules.SCAN_SIZE,PIXELS=SIZE*SIZE;
    /** The images as received (version -1: none yet), their Region scale, centre and scan time. */
    static int version=-1,regionScale=2,centerX,centerZ;static long scannedAt=-1;
    static final byte[][] colors={new byte[PIXELS],new byte[PIXELS]},fresh={new byte[PIXELS],new byte[PIXELS]};
    static final ResourceLocation[] TEXTURE={new ResourceLocation(EternalDefense.ID,"tactical/base"),new ResourceLocation(EternalDefense.ID,"tactical/region")},
        PREVIOUS={new ResourceLocation(EternalDefense.ID,"tactical/base_previous"),new ResourceLocation(EternalDefense.ID,"tactical/region_previous")};
    private static final DynamicTexture[] textures=new DynamicTexture[2],previous=new DynamicTexture[2];
    /** A new image wipes in line by line over the old one for this long. */
    static final long REVEAL_MS=1200;
    static long revealStart;
    /** The scan's progress from the server (sweep line). */
    static boolean scanning,scanRegion;static int scanRow,scanVersion=-1;

    static void image(ChunkedPayload.Part part){
        byte[] raw;
        try{raw=ASSEMBLER.accept(part);}catch(IllegalArgumentException e){ASSEMBLER.clear();com.mojang.logging.LogUtils.getLogger().warn("[hunts] dropped a broken scan image transfer: {}",e.getMessage());return;}
        if(raw==null)return;
        if(raw.length!=TacticalScan.HEADER+4*PIXELS){com.mojang.logging.LogUtils.getLogger().warn("[hunts] scan image of the wrong size ({} bytes)",raw.length);return;}
        var in=ByteBuffer.wrap(raw);
        version=in.getInt();regionScale=Math.max(1,Math.min(64,in.getInt()));centerX=in.getInt();centerZ=in.getInt();scannedAt=in.getLong();
        in.get(colors[0]);in.get(fresh[0]);in.get(colors[1]);in.get(fresh[1]);
        for(int view=0;view<2;view++)upload(view);
        revealStart=net.minecraft.Util.getMillis();
    }
    static void progress(TacticalScan.ScanProgress m){scanning=m.running();scanRegion=m.region();scanRow=m.row();scanVersion=m.version();}

    private static void upload(int view){
        var mc=Minecraft.getInstance();var manager=mc.getTextureManager();
        if(previous[view]!=null){manager.release(PREVIOUS[view]);previous[view]=null;}
        if(textures[view]!=null){
            // keep the old picture under the wipe
            var old=new DynamicTexture(SIZE,SIZE,false);old.getPixels().copyFrom(textures[view].getPixels());old.upload();
            previous[view]=old;manager.register(PREVIOUS[view],old);
        }
        if(textures[view]==null){textures[view]=new DynamicTexture(SIZE,SIZE,false);manager.register(TEXTURE[view],textures[view]);}
        paint(textures[view].getPixels(),colors[view],fresh[view]);textures[view].upload();
    }
    /** Live pixels bright, stale ones dimmed, biome-only ones darker and hatched. */
    static void paint(NativeImage image,byte[] colors,byte[] fresh){
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            int i=y*SIZE+x,packed=colors[i]&0xff;
            int abgr=(packed>>2)==0?0xff120c0a:MapColor.getColorFromPackedId(packed);
            abgr=switch(fresh[i]){
                case ScanSampler.LIVE->abgr;
                case ScanSampler.STALE->shade(abgr,.62f);
                default->shade(abgr,((x+y)&3)==0?.28f:.45f);
            };
            image.setPixelRGBA(x,y,abgr);
        }
    }
    static int shade(int abgr,float f){
        int r=(int)((abgr&0xff)*f),g=(int)(((abgr>>8)&0xff)*f),b=(int)(((abgr>>16)&0xff)*f);
        return 0xff000000|(b<<16)|(g<<8)|r;
    }

    // ---- the table screen ----------------------------------------------------------------------------------------------------------------
    /** The open table's fresh state (after a button or a change on the board), see {@link Hunts.TableState}. */
    static void state(CompoundTag data){
        var screen=Minecraft.getInstance().screen;
        if(data.contains("fobId")){if(screen instanceof FobClient.FobScreen fob)fob.update(data);return;}   // the same packet refreshes the FOB screen (0.0.23)
        if(data.contains("gate")){GateClient.state(data);return;}   // and a gate screen (0.0.24)
        if(data.contains("enchant")){EnchantClient.state(data);return;}   // and the enchanting table (0.0.27)
        if(screen instanceof TableScreen table)table.update(data);
    }
    /** Kept while the table is reopened: which view and which offer. */
    static int view=1;static long selected=-1;
    /** The first FOB row shown on the FOBs tab (a small window scrolls). */
    static int fobScroll;

    static final class TableScreen extends AbstractContainerScreen<TacticalBlocks.TableMenu> {
        private CompoundTag data;private long openedAt,abandonArmed;
        private int size,radarX,radarY,sideX,sideW;
        private final List<Ui.UiButton> rows=new ArrayList<>();
        private Ui.UiButton scanButton,upgradeButton,acceptButton,abandonButton,reprintButton,sosButton;
        TableScreen(TacticalBlocks.TableMenu menu,Inventory inv,Component title){super(menu,inv,title);data=menu.data;openedAt=net.minecraft.Util.getMillis();scanning=data.getBoolean("scanning");}
        void update(CompoundTag next){data=next;openedAt=net.minecraft.Util.getMillis();scanning=next.getBoolean("scanning");rebuildWidgets();}
        CompoundTag data(){return data;}

        private List<CompoundTag> offers(){var out=new ArrayList<CompoundTag>();for(var t:data.getList("offers",Tag.TAG_COMPOUND))out.add((CompoundTag)t);return out;}
        private CompoundTag offer(){
            var all=offers();for(var o:all)if(o.getLong("id")==selected)return o;
            for(var o:all)if(o.getBoolean("active")){selected=o.getLong("id");return o;}
            if(!all.isEmpty()){selected=all.get(0).getLong("id");return all.get(0);}
            return null;
        }
        private long elapsedTicks(){return (net.minecraft.Util.getMillis()-openedAt)/50;}
        private long now(){return data.getLong("now")+elapsedTicks();}
        private int mk(){return data.getInt("mk");}
        private int range(){return data.getInt("range");}
        private BlockPos beacon(){return BlockPos.of(data.getLong("beacon"));}

        @Override protected void init(){
            imageWidth=Math.min(width-12,560);imageHeight=Math.min(height-12,340);
            super.init();clearWidgets();rows.clear();sosButton=null;
            size=Math.max(96,Math.min(imageHeight-28-24-30,imageWidth-24-150));
            radarX=leftPos+12;radarY=topPos+28+24;sideX=radarX+size+10;sideW=leftPos+imageWidth-12-sideX;
            addRenderableWidget(new Ui.UiButton(leftPos+imageWidth-28,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            // toolbar: Base / Region, then SCAN MAP and the satellite upgrade
            // three tabs (0.0.31: FOBs, the FOB management view), each as wide as its label
            int tx=radarX;String[] tabs={"tactical.view.base","tactical.view.region","tactical.view.fobs"};
            for(int i=0;i<tabs.length;i++){int page=i,tw=font.width(Ui.t(tabs[i]))+14;var b=addRenderableWidget(new Ui.UiButton(tx,topPos+31,tw,18,Ui.t(tabs[i]),Ui.Look.TAB,x->{view=page;rebuildWidgets();}));b.selected=view==i;tx+=tw+2;}
            if(view==2){fobView();return;}
            // 0.0.32 (owner): under the map, right: the FOBs' names on or off
            Component names=Ui.t("tactical.fob.names");int nw=font.width(names)+30+font.width(Ui.t("off"))+26;
            namesButton=addRenderableWidget(new Ui.UiButton(radarX+size-nw,radarY+size+3,nw,14,names,Ui.Look.TOGGLE,b->{fobNames=!fobNames;((Ui.UiButton)b).on=fobNames;}));
            namesButton.on=fobNames;namesButton.visible=!fobs().isEmpty();
            namesButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t("tactical.fob.names.tip")));
            scanButton=addRenderableWidget(new Ui.UiButton(tx+4,topPos+31,Math.max(56,radarX+size-tx-4),18,Ui.t("tactical.scan"),Ui.Look.PRIMARY,b->click(TacticalBlocks.button(TacticalBlocks.SCAN,0))));
            upgradeButton=addRenderableWidget(new Ui.UiButton(sideX,topPos+31,sideW,18,Ui.t("tactical.upgrade",mk()+1),Ui.Look.NORMAL,b->click(TacticalBlocks.button(TacticalBlocks.UPGRADE,0))));
            upgradeButton.visible=data.contains("upgradeItem");upgradeButton.active=data.getBoolean("uplink")&&(data.getBoolean("creative")||data.getInt("upgradeHave")>=data.getInt("upgradeAmount"));
            upgradeButton.warning=upgradeButton.visible&&!upgradeButton.active&&data.getBoolean("uplink");
            // the offer list
            var all=offers();int rowY=radarY;
            for(var o:all){
                long id=o.getLong("id");
                var row=new Ui.UiButton(sideX,rowY,sideW,20,Component.empty(),Ui.Look.ROW,b->{selected=id;abandonArmed=0;rebuildWidgets();});
                row.selected=id==selected;row.accent=kindColor(o.getString("kind"));row.painter((g,self,hover)->paintRow(g,self,o)).noLabel();
                rows.add(addRenderableWidget(row));rowY+=22;
            }
            var o=offer();for(var r:rows)r.selected=false;
            if(o!=null)for(int i=0;i<all.size();i++)if(all.get(i).getLong("id")==o.getLong("id"))rows.get(i).selected=true;
            int buttonsY=topPos+imageHeight-30;
            acceptButton=addRenderableWidget(new Ui.UiButton(sideX,buttonsY,sideW,20,Ui.t("tactical.accept"),Ui.Look.PRIMARY,b->{var sel=offer();if(sel!=null)click(TacticalBlocks.button(TacticalBlocks.ACCEPT,sel.getInt("index")));}));
            abandonButton=addRenderableWidget(new Ui.UiButton(sideX,buttonsY,sideW/2-1,20,Ui.t("tactical.abandon"),Ui.Look.DANGER,b->abandon()));
            reprintButton=addRenderableWidget(new Ui.UiButton(sideX+sideW/2+1,buttonsY,sideW-sideW/2-1,20,Ui.t("tactical.reprint"),Ui.Look.NORMAL,b->click(TacticalBlocks.button(TacticalBlocks.REPRINT,0))));
            boolean active=o!=null&&o.getBoolean("active");String blocked=data.getString("blocked");
            acceptButton.visible=o!=null&&!active;acceptButton.active=blocked.isEmpty();acceptButton.warning=!blocked.isEmpty();
            abandonButton.visible=reprintButton.visible=active;
            if(abandonArmed>net.minecraft.Util.getMillis()){abandonButton.setMessage(Ui.t("tactical.abandon.confirm"));}
        }
        // ---- FOB management (0.0.31) -----------------------------------------------------------------------------------------------------------
        /** 0.0.32 (owner): the map's switch for the FOBs' names and distances (their flags, zones and arrows stay), kept while the game runs. */
        static boolean fobNames=true;
        private Ui.UiButton namesButton;
        /** The FOBs, the one calling for help first. */
        private List<CompoundTag> fobs(){var out=new ArrayList<CompoundTag>();for(var t:data.getList("fobs",Tag.TAG_COMPOUND))out.add((CompoundTag)t);out.sort((a,b)->Boolean.compare(b.getBoolean("sos"),a.getBoolean("sos")));return out;}
        private int fobX(){return radarX;}
        private int fobW(){return leftPos+imageWidth-12-radarX;}
        private int fobRoom(){return topPos+imageHeight-22-(radarY+14);}
        private int fobRowH(){int n=Math.max(1,fobs().size());return Math.max(38,Math.min(52,fobRoom()/n));}
        /** How many rows fit; a small window scrolls the rest with the mouse wheel. */
        private int fobRows(){return Math.max(1,fobRoom()/fobRowH());}
        private List<CompoundTag> shownFobs(){var all=fobs();fobScroll=Math.max(0,Math.min(fobScroll,all.size()-fobRows()));return all.subList(fobScroll,Math.min(all.size(),fobScroll+fobRows()));}
        /** The FOB list: the SOS row gets Reprint SOS map, every row Contact Specialty Seller (0.0.33), under the state chip. */
        // 0.0.40 (owner): the row's buttons sit 4 px above the card's lower edge (the card is h - 3 tall; they touched it at y + h - 22)
        private void fobView(){
            if(seller()!=null){sellerView();return;}
            int y=radarY+14,h=fobRowH();
            for(var f:shownFobs()){
                int right=fobX()+fobW()-6;
                if(f.getBoolean("sos")){
                    sosButton=addRenderableWidget(new Ui.UiButton(right-112,y+h-25,112,18,Ui.t("tactical.fob.reprint"),Ui.Look.DANGER,b->click(TacticalBlocks.button(TacticalBlocks.REPRINT_SOS,0))));
                    sosButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t(data.getBoolean("sosCarried")?"tactical.fob.reprint.carried":"tactical.fob.reprint.tip")));
                    right-=116;
                }
                long id=f.getLong("id");String closed=f.getString("seller");int sw=sellButtonWidth(f);
                if(sw>0){
                    var sell=addRenderableWidget(new Ui.UiButton(right-sw,y+h-25,sw,18,sellLabel(sw),Ui.Look.PRIMARY,b->{sellerFob=id;sellerItem=0;sellerScroll=0;contact();rebuildWidgets();}).icon(RADIO));
                    sell.active=closed.isEmpty();
                    sell.setTooltip(net.minecraft.client.gui.components.Tooltip.create(closed.isEmpty()?Ui.t("tactical.seller.tip"):Ui.t("tactical.seller.closed."+closed,f.getInt("level"),CatapultRules.SELLER_LEVEL)));
                }
                y+=h;
            }
        }
        // ---- the specialty seller (0.0.33, owner) ------------------------------------------------------------------------------------------------
        /** The FOB whose seller is open (-1: the list), the item picked, the grid's scroll: kept while the game runs. */
        static long sellerFob=-1;static int sellerItem,sellerScroll;
        private final List<Ui.UiButton> sellButtons=new ArrayList<>();
        /** The row's seller button: its whole label when the row's lines still fit beside it, else the short one. */
        private int sellButtonWidth(CompoundTag f){
            int full=font.width(Ui.t("tactical.seller.open"))+16+RADIO_W,other=f.getBoolean("sos")?116:0;
            int need=Math.max(font.width(where(f)),f.getBoolean("sos")?font.width(Ui.t("tactical.fob.sos",FobClient.sosTime(f.getLong("sosLeft")),FobClient.sosTime(Math.max(1,data.getLong("sosTotal"))))):font.width(route(f)));
            int shortW=font.width(Ui.t("tactical.seller.open.short"))+16+RADIO_W;
            if(fobW()-16-full-4-other>=need)return full;
            // an SOS row is crowded by Reprint SOS map: the seller steps aside there on a small window until the SOS is over
            return fobW()-16-shortW-4-other>=need||!f.getBoolean("sos")?shortW:0;
        }
        private Component sellLabel(int w){return font.width(Ui.t("tactical.seller.open"))+16+RADIO_W<=w?Ui.t("tactical.seller.open"):Ui.t("tactical.seller.open.short");}
        /** 0.0.34 (owner): the handheld radio on Contact Specialty Seller, and its static as the line opens (the owner's recording). */
        static final net.minecraft.resources.ResourceLocation RADIO=new net.minecraft.resources.ResourceLocation(EternalDefense.ID,"textures/gui/radio.png");
        static final int RADIO_W=16;
        private static void contact(){Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(DefenseSounds.SELLER_STATIC.get(),1f,1f));}
        private Component where(CompoundTag f){return Ui.t("tactical.fob.where",f.getInt("distance"),TacticalRules.compass(f.getDouble("bearing")),f.getInt("villagers"));}
        private Component route(CompoundTag f){return f.getBoolean("route")?Ui.t("tactical.fob.route",Ui.t("gate.signal."+f.getString("signal"))):Ui.t("tactical.fob.no_route");}
        /** The FOB the seller is open for, or null (it is gone, or its seller closed). */
        private CompoundTag seller(){if(sellerFob<0)return null;for(var f:fobs())if(f.getLong("id")==sellerFob&&f.getString("seller").isEmpty())return f;return null;}
        private RouteRules.Specialty specialtyOf(CompoundTag f){return RouteRules.Specialty.orFirst(f.getString("specialty"));}
        private int sellTop(){return radarY+28;}
        private int sellBottom(){return topPos+imageHeight-56;}
        private int sellCols(){return Math.max(1,(fobW()-viewW()-8)/22);}
        /** 0.0.34 (owner): the live catapult at the right of the seller, the grid at its left. */
        private int viewW(){return Math.max(90,Math.min(150,fobW()*2/5));}
        private int viewX(){return fobX()+fobW()-viewW();}
        private int sellRows(){return Math.max(1,(sellBottom()-sellTop())/22);}
        private void sellerView(){
            var f=seller();var options=RouteRules.options(RouteRules.Slot.SPECIALTY,specialtyOf(f));sellButtons.clear();
            sellerItem=Math.max(0,Math.min(sellerItem,options.size()-1));
            int cols=sellCols(),rows=sellRows(),total=(options.size()+cols-1)/cols;sellerScroll=Math.max(0,Math.min(sellerScroll,Math.max(0,total-rows)));
            for(int i=sellerScroll*cols;i<options.size()&&i<(sellerScroll+rows)*cols;i++){
                int index=i,col=(i-sellerScroll*cols)%cols,row=(i-sellerScroll*cols)/cols;var loot=options.get(i);var stack=GateClient.icon(loot.id());
                var b=new Ui.UiButton(fobX()+col*22,sellTop()+row*22,20,20,Component.empty(),Ui.Look.NORMAL,x->{sellerItem=index;rebuildWidgets();});
                b.selected=i==sellerItem;
                b.painter((g,self,hover)->{g.renderItem(stack,self.getX()+2,self.getY()+2);g.pose().pushPose();g.pose().translate(0,0,200);GateClient.tierBadge(g,font,loot.tier(),self.getX()+1,self.getY()+1);g.pose().popPose();}).noLabel();
                sellButtons.add(addRenderableWidget(b));
            }
            int y=topPos+imageHeight-40,x=fobX()+fobW();
            var back=addRenderableWidget(new Ui.UiButton(x-50,y,50,18,Ui.t("gate.pick.back"),Ui.Look.NORMAL,b->{sellerFob=-1;rebuildWidgets();}));x-=54;
            int index=f.getInt("index");boolean creative=data.getBoolean("creative"),room=f.getInt("queue")<data.getInt("maxQueue");
            for(int k=CatapultRules.LOTS.length-1;k>=0;k--){
                int kind=k,lots=CatapultRules.LOTS[k],price=lots*data.getInt("lotPrice");Component label=Ui.t(lots==1?"tactical.seller.buy":"tactical.seller.buy.lots",lots,price);int bw=font.width(label)+14;
                var buy=addRenderableWidget(new Ui.UiButton(x-bw,y,bw,18,label,k==0?Ui.Look.PRIMARY:Ui.Look.NORMAL,b->click(SupplyCatapult.sellButton(index,sellerItem,kind))));
                buy.active=room&&(creative||data.getInt("ardent")>=price);buy.warning=!buy.active;
                buy.setTooltip(net.minecraft.client.gui.components.Tooltip.create(!room?Ui.t("tactical.seller.busy"):buy.active?Ui.t("tactical.seller.buy.tip",lots):Ui.t("tactical.seller.short")));
                x-=bw+4;
            }
            sellDetailRight=x-6;
        }
        private int sellDetailRight;
        private void drawSeller(GuiGraphics g,int mx,int my){
            var f=seller();var spec=specialtyOf(f);var options=RouteRules.options(RouteRules.Slot.SPECIALTY,spec);
            int x=fobX(),w=fobW(),y=radarY;
            Component balance=data.getBoolean("creative")?Ui.t("fob.shop.creative.short"):Ui.t("tactical.seller.ardent",data.getInt("ardent"));
            Ui.text(g,font,Ui.t("tactical.seller.title",f.getString("name")),x,y,Ui.BRASS,w-font.width(balance)-8);Ui.right(g,font,balance,x+w,y,Ui.INK);
            int iw=GateClient.specialtyIcons(g,spec.id(),x,y+11)+3;
            // the specialty's name, the lot's price, the boxes waiting: what fits (a small window drops the boxes, then the name)
            Component sub=null;Object[] args={Ui.t("gate.specialty."+spec.id()),data.getInt("lotPrice"),f.getInt("queue"),data.getInt("maxQueue")};
            for(String k:new String[]{"tactical.seller.sub","tactical.seller.sub.short","tactical.seller.sub.price"}){sub=Ui.t(k,args);if(font.width(sub)<=w-iw)break;}
            Ui.text(g,font,sub,x+iw,y+15,Ui.MUTED,w-iw);
            int cols=sellCols(),total=(options.size()+cols-1)/cols;
            if(total>sellRows()){Component more=Ui.t("tactical.seller.scroll");Ui.text(g,font,more,x,sellBottom()+1,Ui.MUTED,viewX()-x-8);}
            // the catapult itself, live, and what it is doing
            int vx=viewX(),vy=sellTop(),vw=viewW(),vh=sellBottom()-sellTop()+10;
            Ui.inset(g,vx,vy,vw,vh);
            CatapultView.draw(g,f.getLong("id"),vx+1,vy+1,vw-2,vh-14);
            var live=CatapultView.of(f.getLong("id"));
            Ui.text(g,font,CatapultView.caption(live),vx+4,vy+vh-11,live!=null&&live.now().throwAge()>=0?Ui.BRASS:Ui.INK,vw-8);
            var loot=options.get(Math.max(0,Math.min(sellerItem,options.size()-1)));int lot=CatapultRules.lotSize(loot);
            int dy=topPos+imageHeight-40;g.renderItem(GateClient.icon(loot.id()),x,dy+1);
            Ui.text(g,font,Ui.t("tactical.seller.lot",lot,GateClient.icon(loot.id()).getHoverName()),x+19,dy+5,Ui.INK,sellDetailRight-x-19);
        }
        @Override public boolean mouseScrolled(double mx,double my,double delta){
            if(view==2&&seller()!=null){sellerScroll=Math.max(0,sellerScroll-(int)Math.signum(delta));rebuildWidgets();return true;}
            if(view==2&&fobs().size()>fobRows()){fobScroll=Math.max(0,fobScroll-(int)Math.signum(delta));rebuildWidgets();return true;}
            return super.mouseScrolled(mx,my,delta);
        }
        private void drawFobView(GuiGraphics g){
            if(seller()!=null){drawSeller(g,0,0);return;}
            int x=fobX(),w=fobW(),y=radarY;var all=fobs();
            Component title=Ui.t("tactical.fob.title",all.size(),data.getInt("fobMax"));
            Ui.text(g,font,title,x,y,Ui.BRASS,w);
            if(all.size()>fobRows()){Component more=Ui.t("tactical.fob.scroll",fobScroll+1,fobScroll+shownFobs().size(),all.size());
                if(font.width(title)+12+font.width(more)<=w)Ui.right(g,font,more,x+w,y,Ui.MUTED);}
            y+=14;
            if(all.isEmpty()){Ui.wrap(g,font,Ui.t("tactical.fob.none"),x,y+4,w,Ui.MUTED,4);return;}
            int h=fobRowH();long total=Math.max(1,data.getLong("sosTotal"));
            for(var f:shownFobs()){
                boolean sos=f.getBoolean("sos");String state=f.getString("state");
                Ui.card(g,x,y,w,h-3,sos?Ui.RED:FobClient.stateColor(state));
                int chip=Ui.chip(g,font,Ui.t("fob.state."+state),x+w-8-Math.min(90,font.width(Ui.t("fob.state."+state))+12),y+4,FobClient.stateColor(state),90);
                // 0.0.32: the specialty's icon and the research level (five pips) left of the state
                int sx=x+w-8-chip-4-GateClient.specialtyIconsWidth(f.getString("specialty"));GateClient.specialtyIcons(g,f.getString("specialty"),sx,y+2);
                Ui.pips(g,sx-Ui.pipsWidth(RouteRules.MAX_LEVEL)-4,y+7,f.getInt("level"),RouteRules.MAX_LEVEL,FobClient.OLIVE);
                Ui.text(g,font,Component.literal(f.getString("name")),x+8,y+5,Ui.BRASS,sx-Ui.pipsWidth(RouteRules.MAX_LEVEL)-12-x-8);
                Component where=Ui.t("tactical.fob.where",f.getInt("distance"),TacticalRules.compass(f.getDouble("bearing")),f.getInt("villagers"));
                int sw=sellButtonWidth(f),buttons=(sw>0?sw+4:0)+(sos?116:0);   // the row's buttons sit at its bottom right
                Ui.text(g,font,where,x+8,y+16,Ui.INK,w-16-buttons);
                Component route=f.getBoolean("route")?Ui.t("tactical.fob.route",Ui.t("gate.signal."+f.getString("signal"))):Ui.t("tactical.fob.no_route");
                if(!sos)Ui.text(g,font,route,x+8,y+27,f.getBoolean("route")?GateClient.color(f.getString("signal")):Ui.MUTED,w-16-buttons);
                else{
                    long left=f.getLong("sosLeft");int bw=Math.max(40,w-16-buttons);
                    Ui.text(g,font,Ui.t("tactical.fob.sos",FobClient.sosTime(left),FobClient.sosTime(total)),x+8,y+27,Ui.RED,bw);
                    if(h>=46)Ui.bar(g,x+8,y+h-10,bw,4,Math.max(0,Math.min(1,left/(float)total)),Ui.RED);
                }
                y+=h;
            }
        }
        /** FOBs on the map (0.0.31): each zone's columns, a flag on the FOB, its name; beyond the map's edge an arrow at the edge points to it. */
        private void drawFobs(GuiGraphics g,int x0,int y0,int x1,int y1){
            double perPixel=viewScale()*(double)SIZE/size;
            for(var f:fobs()){
                boolean sos=f.getBoolean("sos");int color=sos?Ui.RED:FobClient.stateColor(f.getString("state"));int fill=(color&0x00ffffff)|0x40000000;
                for(long col:f.getLongArray("zone")){
                    int cx=net.minecraft.world.level.ChunkPos.getX(col)*16,cz=net.minecraft.world.level.ChunkPos.getZ(col)*16;
                    float[] a=toScreen(cx,cz),b=toScreen(cx+16,cz+16);
                    int ax=(int)Math.floor(a[0]),ay=(int)Math.floor(a[1]),bx=Math.max(ax+1,(int)Math.ceil(b[0])),by=Math.max(ay+1,(int)Math.ceil(b[1]));
                    if(bx<x0||by<y0||ax>x1||ay>y1)continue;g.fill(ax,ay,bx,by,fill);
                }
                float[] at=toScreen(f.getInt("x")+.5,f.getInt("z")+.5);Component name=Component.literal(f.getString("name"));
                if(inside(at,x0+2,y0+2,x1-2,y1-2)){
                    int fx=(int)at[0],fy=(int)at[1];flag(g,fx,fy,color);
                    if(!fobNames)continue;   // 0.0.32: names switched off, the flag stays
                    int lw=font.width(name),lx=Mth.clamp(fx+5,x0+2,x1-lw-2),ly=Mth.clamp(fy-10,y0+2,y1-10);
                    g.fill(lx-1,ly-1,lx+lw+1,ly+9,0xb0000000);Ui.text(g,font,name,lx,ly,color);
                }else{
                    // the arrow at the edge, pointing at the FOB, its name beside it on the map's side
                    double cx=(x0+x1)/2.0,cy=(y0+y1)/2.0,dx=at[0]-cx,dy=at[1]-cy,len=Math.max(1e-6,Math.hypot(dx,dy));
                    double hw=(x1-x0)/2.0-8,hh=(y1-y0)/2.0-8,k=Math.min(Math.abs(dx)<1e-6?1e9:hw/Math.abs(dx),Math.abs(dy)<1e-6?1e9:hh/Math.abs(dy));
                    float ex=(float)(cx+dx*k),ey=(float)(cy+dy*k);
                    arrow(g,ex,ey,(float)Math.atan2(dy,dx),color);
                    if(!fobNames)continue;   // 0.0.32: names switched off, the arrow stays
                    Component label=Ui.t("tactical.fob.arrow",name,f.getInt("distance"));int lw=font.width(label);
                    int lx=(int)Mth.clamp(ex-lw/2f-(float)(dx/len)*(lw/2f+6),x0+2,x1-lw-2),ly=(int)Mth.clamp(ey-4-(float)(dy/len)*12,y0+2,y1-11);
                    // a long name reaching the middle steps above the beacon's mark
                    if(lx<cx+7&&lx+lw>cx-7&&ly<cy+8&&ly+9>cy-9)ly=(int)Mth.clamp(cy-21,y0+2,y1-11);
                    g.fill(lx-1,ly-1,lx+lw+1,ly+9,0xb0000000);Ui.text(g,font,label,lx,ly,color);
                }
            }
        }
        /** A small flag: a pole and a pennant, outlined in black. */
        private static void flag(GuiGraphics g,int x,int y,int color){
            g.fill(x-1,y-8,x+2,y+2,0xff000000);g.fill(x,y-7,x+1,y+1,0xffedf5f8);
            g.fill(x+1,y-8,x+7,y-3,0xff000000);g.fill(x+1,y-7,x+6,y-4,color);
        }
        /** A filled triangle pointing along {@code angle} (radians, screen coordinates). */
        private static void arrow(GuiGraphics g,float x,float y,float angle,int color){
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle));
            for(int i=0;i<=6;i++){int half=(6-i)/2+1;g.fill(i-4,-half-1,i-3,half+1,0xff000000);}
            for(int i=0;i<=5;i++){int half=(5-i)/2;g.fill(i-3,-half,i-2,half+1,color);}
            g.pose().popPose();
        }
        private CompoundTag hoveredFob(int mx,int my){
            if(view==2||!Ui.inside(mx,my,radarX,radarY,size,size))return null;
            for(var f:fobs()){float[] at=toScreen(f.getInt("x")+.5,f.getInt("z")+.5);if(Math.abs(mx-at[0])<=5&&Math.abs(my-at[1]+3)<=6)return f;}
            return null;
        }
        private void abandon(){
            long now=net.minecraft.Util.getMillis();
            if(abandonArmed<now){abandonArmed=now+5000;abandonButton.setMessage(Ui.t("tactical.abandon.confirm"));return;}
            abandonArmed=0;click(TacticalBlocks.button(TacticalBlocks.ABANDON,0));
        }
        private void click(int id){BeaconNetwork.menuButton(menu.containerId,id);}

        @Override public void containerTick(){
            super.containerTick();
            if(abandonButton!=null&&abandonArmed!=0&&abandonArmed<net.minecraft.Util.getMillis()){abandonArmed=0;abandonButton.setMessage(Ui.t("tactical.abandon"));}
            if(scanButton!=null&&view!=2){
                long ready=data.getLong("scanReady")-now();
                scanButton.active=data.getBoolean("uplink")&&!scanning&&ready<=0;
                scanButton.setMessage(scanning?Ui.t("tactical.scan.running",scanPercent()):ready>0?Ui.t("tactical.scan.cooldown",(ready+19)/20):Ui.t("tactical.scan"));
            }
        }
        private int scanPercent(){return (int)Math.round(((scanRegion?SIZE:0)+scanRow)*100.0/(2*SIZE));}

        // ---- drawing ----------------------------------------------------------------------------------------------------------------------
        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);
            Ui.header(g,font,leftPos,topPos,imageWidth,Ui.t("tactical.title"),subtitle());
            if(view==2)drawFobView(g);
            else{drawRadar(g,mx,my);drawSide(g);}
            var message=data.getString("message");
            if(!message.isEmpty()){boolean good=message.endsWith(".accepted")||message.endsWith(".upgraded")||message.endsWith(".reprinted")||message.endsWith(".sos_reprinted")||message.endsWith(".scan_started")||message.endsWith(".abandoned")||message.equals("tactical.seller.bought");
                Ui.text(g,font,Component.translatable("gui.eternal_defense."+message),radarX,topPos+imageHeight-14,good?Ui.CYAN:Ui.ORANGE,view==2?fobW():size);}
        }
        private Component subtitle(){
            if(!data.getBoolean("uplink"))return Ui.t("tactical.no_uplink.short");
            return Ui.t("tactical.subtitle",Mth.clamp(mk(),1,3),range(),TacticalRules.offers(mk()),scannedAgo());
        }
        private Component scannedAgo(){
            if(data.getLong("scannedAt")<0)return Ui.t("tactical.scanned.never");
            long seconds=Math.max(0,(now()-data.getLong("scannedAt"))/20);
            return seconds<60?Ui.t("tactical.scanned.seconds",seconds):seconds<7200?Ui.t("tactical.scanned.minutes",seconds/60):Ui.t("tactical.scanned.hours",seconds/3600);
        }
        /** The status chip: NO UPLINK, RAID IN PROGRESS, RAID WARNING, or the time to the next raid. */
        private void drawStatus(GuiGraphics g,int x,int y,int w){
            String blocked=data.getString("blocked");
            if(blocked.equals("no_uplink"))Ui.chip(g,font,Ui.t("tactical.status.no_uplink"),x,y,Ui.RED,w);
            else if(blocked.equals("raid"))Ui.chip(g,font,Ui.t("tactical.status.raid"),x,y,Ui.RED,w);
            else if(blocked.equals("raid_warning"))Ui.chip(g,font,Ui.t("tactical.status.raid_warning"),x,y,Ui.ORANGE,w);
            else if(blocked.equals("busy"))Ui.chip(g,font,Ui.t("tactical.status.busy"),x,y,Ui.BRASS,w);
            else Ui.chip(g,font,Ui.t("tactical.status.ready"),x,y,Ui.CYAN,w);
        }

        private int viewScale(){return view==0?1:version>=0&&centerX==beacon().getX()&&centerZ==beacon().getZ()?regionScale:Math.max(1,2*range()/SIZE);}
        /** Screen position of a world column on the radar. */
        private float[] toScreen(double x,double z){
            var b=beacon();double s=viewScale()*(double)SIZE/size;
            return new float[]{(float)(radarX+size/2.0+(x-(b.getX()+.5))/s),(float)(radarY+size/2.0+(z-(b.getZ()+.5))/s)};
        }
        private void drawRadar(GuiGraphics g,int mx,int my){
            int x0=radarX,y0=radarY,x1=x0+size,y1=y0+size;
            g.fill(x0-1,y0-1,x1+1,y1+1,Ui.EDGE);g.fill(x0,y0,x1,y1,0xff0c1418);
            var b=beacon();boolean image=version>=0&&centerX==b.getX()&&centerZ==b.getZ()&&textures[view]!=null;
            if(image){
                long since=net.minecraft.Util.getMillis()-revealStart;
                if(since<REVEAL_MS&&previous[view]!=null){
                    g.blit(PREVIOUS[view],x0,y0,size,size,0,0,SIZE,SIZE,SIZE,SIZE);
                    int edge=y0+(int)(size*since/(double)REVEAL_MS);
                    g.enableScissor(x0,y0,x1,edge);g.blit(TEXTURE[view],x0,y0,size,size,0,0,SIZE,SIZE,SIZE,SIZE);g.disableScissor();
                    g.fill(x0,edge,x1,edge+1,0xcc5ae2df);
                }else g.blit(TEXTURE[view],x0,y0,size,size,0,0,SIZE,SIZE,SIZE,SIZE);
            }else{
                for(int i=1;i<8;i++){int gx=x0+size*i/8,gy=y0+size*i/8;g.fill(gx,y0,gx+1,y1,0xff16252c);g.fill(x0,gy,x1,gy+1,0xff16252c);}
                // 0.0.31: under the map, so a FOB's flag or arrow never lands on it (left of the names switch)
                Ui.text(g,font,Ui.t("tactical.no_scan"),x0,y1+4,Ui.MUTED,size-(namesButton!=null&&namesButton.visible?namesButton.getWidth()+6:0));
            }
            // the sweep line of a running scan, on the view it is filling
            if(scanning&&(scanRegion?1:0)==view){int sy=y0+scanRow*size/SIZE;g.fill(x0,sy,x1,sy+1,0xff5ae2df);g.fill(x0,Math.max(y0,sy-6),x1,sy,0x225ae2df);}
            g.enableScissor(x0,y0,x1,y1);
            double perPixel=viewScale()*(double)SIZE/size;
            // range rings (Region) or the 64-block rings (Base), then the beacon zone
            int ringStep=view==0?64:range()/4;
            for(int r=ringStep;r<=(view==0?128:range());r+=ringStep)ring(g,x0+size/2f,y0+size/2f,(float)(r/perPixel),0x553d5c6d);
            ring(g,x0+size/2f,y0+size/2f,(float)(data.getInt("zone")/perPixel),0xaa5ae2df);
            if(view==1)ring(g,x0+size/2f,y0+size/2f,(float)(TacticalRules.minDistance(data.getInt("zone"))/perPixel),0x44c9a24b);
            Ui.beaconGlyph(g,x0+size/2-4,y0+size/2-6);
            // this player
            var p=minecraft.player;if(p!=null){float[] at=toScreen(p.getX(),p.getZ());if(inside(at,x0,y0,x1,y1)){int px=(int)at[0],py=(int)at[1];g.fill(px-1,py-1,px+2,py+2,0xff000000);g.fill(px,py,px+1,py+1,0xffffffff);}}
            // an X per offer (an arrow at the edge when it lies outside the view)
            long pulse=net.minecraft.Util.getMillis()/300%2;
            for(var o:offers()){
                float[] at=toScreen(o.getInt("x")+.5,o.getInt("z")+.5);boolean active=o.getBoolean("active"),chosen=o.getLong("id")==selected;
                // the selected offer's X is red; the others show their class (blue, purple, gold)
                int color=chosen?Ui.RED:kindColor(o.getString("kind"));
                float cx=Mth.clamp(at[0],x0+4,x1-5),cy=Mth.clamp(at[1],y0+4,y1-5);boolean edge=cx!=at[0]||cy!=at[1];
                if(edge){g.fill((int)cx-2,(int)cy-2,(int)cx+3,(int)cy+3,0xff000000);g.fill((int)cx-1,(int)cy-1,(int)cx+2,(int)cy+2,color);continue;}
                cross(g,(int)cx,(int)cy,active&&pulse==0?0xffffffff:color,chosen||active?4:3);
                if(chosen||active)ring(g,cx+.5f,cy+.5f,7,active?0xffed7369:0xccedf5f8);
            }
            drawFobs(g,x0,y0,x1,y1);
            g.disableScissor();
            // scale label
            // the scale on a dark strip, readable over snow and sand
            Component scale=Ui.t(view==0?"tactical.view.base.scale":"tactical.view.region.scale",view==0?128:range());
            g.fill(x0,y1-13,x0+Math.min(size,font.width(scale)+8),y1,0xcc0a1218);Ui.text(g,font,scale,x0+4,y1-11,Ui.INK,size-8);
        }
        private static boolean inside(float[] at,int x0,int y0,int x1,int y1){return at[0]>=x0&&at[0]<x1&&at[1]>=y0&&at[1]<y1;}
        private static void ring(GuiGraphics g,float cx,float cy,float r,int color){
            if(r<2)return;int steps=Math.max(24,(int)(r*4));
            for(int i=0;i<steps;i++){double a=i*Math.PI*2/steps;int x=(int)(cx+Math.cos(a)*r),y=(int)(cy+Math.sin(a)*r);g.fill(x,y,x+1,y+1,color);}
        }
        private static void cross(GuiGraphics g,int x,int y,int color,int arm){
            for(int i=-arm;i<=arm;i++){g.fill(x+i-1,y+i-1,x+i+2,y+i+2,0xaa000000);g.fill(x+i-1,y-i-1,x+i+2,y-i+2,0xaa000000);}
            for(int i=-arm;i<=arm;i++){g.fill(x+i,y+i,x+i+1,y+i+1,color);g.fill(x+i,y-i,x+i+1,y-i+1,color);}
        }


        private void paintRow(GuiGraphics g,Ui.UiButton row,CompoundTag o){
            int x=row.getX(),y=row.getY();
            cross(g,x+9,y+10,kindColor(o.getString("kind")),3);
            Ui.text(g,font,Component.literal(o.getString("codename")),x+18,y+2,o.getBoolean("active")?Ui.BRASS:Ui.INK,row.getWidth()-22);
            Component line=o.getBoolean("active")?Ui.t("tactical.row.active",Ui.t("tactical.class."+o.getString("kind")),o.getInt("distance"),TacticalRules.compass(o.getDouble("bearing")))
                :Ui.t("tactical.row",Ui.t("tactical.class."+o.getString("kind")),o.getInt("distance"),TacticalRules.compass(o.getDouble("bearing")));
            Ui.text(g,font,line,x+18,y+11,Ui.MUTED,row.getWidth()-22);
        }
        private void drawSide(GuiGraphics g){
            int x=sideX,w=sideW,listEnd=radarY+rows.size()*22;
            if(rows.isEmpty()){Ui.wrap(g,font,Ui.t(data.getBoolean("uplink")?"tactical.empty":"tactical.no_offers"),x,radarY+2,w,Ui.MUTED,4);listEnd=radarY+44;}
            var o=offer();
            int y=listEnd+4,bottom=topPos+imageHeight-34;
            drawStatus(g,x,y,w);y+=16;
            if(!data.getBoolean("uplink")){
                // why there is no uplink, and how to get one: this replaces the card until the link is back
                String problem=data.getString("uplinkProblem");
                if(!problem.isEmpty())y=Ui.wrap(g,font,Ui.t("tactical.uplink."+problem),x,y,w,Ui.ORANGE,3)+4;
                Ui.wrap(g,font,Ui.t("tactical.no_uplink.help"),x,y,w,Ui.MUTED,Math.max(1,(bottom-y)/10));return;
            }
            if(o==null)return;
            if(bottom-y<40)return;
            String kind=o.getString("kind");
            Ui.card(g,x,y,w,bottom-y,kindColor(kind));
            int cx=x+8,cw=w-12,cy=y+5;
            Ui.text(g,font,Component.literal(o.getString("codename")),cx,cy,Ui.BRASS,cw);cy+=11;
            Ui.text(g,font,Ui.t("tactical.card.class",Ui.t("tactical.class."+kind)),cx,cy,Ui.INK,cw-28);
            Ui.pips(g,x+w-4-Ui.pipsWidth(4),cy+1,o.getInt("danger"),4,o.getInt("danger")>=3?Ui.RED:Ui.ORANGE);cy+=11;
            Ui.text(g,font,Ui.t("tactical.card.distance",o.getInt("distance"),TacticalRules.compass(o.getDouble("bearing"))),cx,cy,Ui.MUTED,cw);cy+=10;
            cy=Ui.wrap(g,font,Ui.t(kind.equals("warlord")?"tactical.card.size.warlord":"tactical.card.size",o.getInt("min"),o.getInt("max")),cx,cy,cw,Ui.MUTED,2)+1;
            if(cy+16<=bottom){
                g.renderItem(new ItemStack(EternalDefense.ARDENT_ENERGY.get()),cx-2,cy-3);Ui.text(g,font,Component.literal("x"+o.getInt("ardent")),cx+15,cy+2,Ui.CYAN,30);
                g.renderItem(new ItemStack(EternalDefense.AMMO_COIN.get()),cx+46,cy-3);Ui.text(g,font,Component.literal("x"+o.getInt("coins")),cx+63,cy+2,Ui.CYAN,30);cy+=16;
            }
            Component note=o.getBoolean("active")?missionState():Ui.t("tactical.card.note."+kind);
            if(cy+9<=bottom)Ui.wrap(g,font,note,cx,cy,cw,o.getBoolean("active")?Ui.INK:Ui.MUTED,Math.max(1,(bottom-cy-2)/10));
        }
        private Component missionState(){
            return switch(data.getString("missionState")){
                case "ENGAGED"->Ui.t("tactical.card.engaged",data.getInt("remaining"),data.getInt("total"));
                default->Ui.t("tactical.card.waiting",TacticalRules.APPROACH);
            };
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);
            if(view==2){
                var f=seller();if(f==null)return;var options=RouteRules.options(RouteRules.Slot.SPECIALTY,specialtyOf(f));int cols=sellCols();
                for(int i=0;i<sellButtons.size();i++)if(sellButtons.get(i).isHovered()&&sellerScroll*cols+i<options.size()){
                    var loot=options.get(sellerScroll*cols+i);var lines=new ArrayList<Component>();lines.add(GateClient.icon(loot.id()).getHoverName());
                    if(loot.tier()>0)lines.add(Ui.t("gate.pick.tier",RouteRules.TIERS.substring(loot.tier(),loot.tier()+1)).copy().withStyle(st->st.withColor(GateClient.tierColor(loot.tier()))));
                    lines.add(Ui.t("tactical.seller.lot.tip",CatapultRules.lotSize(loot),data.getInt("lotPrice")));
                    g.renderComponentTooltip(font,lines,mx,my);return;
                }
                return;
            }
            var fob=hoveredFob(mx,my);
            if(fob!=null){g.renderComponentTooltip(font,List.of(Component.literal(fob.getString("name")),Ui.t("fob.state."+fob.getString("state")),Ui.t("tactical.fob.where",fob.getInt("distance"),TacticalRules.compass(fob.getDouble("bearing")),fob.getInt("villagers"))),mx,my);return;}
            // hover: an X on the radar, the upgrade price, a blocked Accept
            var hovered=hoveredOffer(mx,my);
            if(hovered!=null)g.renderComponentTooltip(font,List.of(Component.literal(hovered.getString("codename")),Ui.t("tactical.row",Ui.t("tactical.class."+hovered.getString("kind")),hovered.getInt("distance"),TacticalRules.compass(hovered.getDouble("bearing")))),mx,my);
            else if(upgradeButton.visible&&upgradeButton.isHovered()){
                var item=BuiltInRegistries.ITEM.get(new ResourceLocation(data.getString("upgradeItem")));
                g.renderComponentTooltip(font,List.of(Ui.t("tactical.upgrade.tip",mk()+1,TacticalRules.range(mk()+1),TacticalRules.offers(mk()+1)),
                    Ui.t("tactical.upgrade.cost",data.getInt("upgradeHave"),data.getInt("upgradeAmount"),new ItemStack(item).getHoverName())),mx,my);
            }
            else if(acceptButton.visible&&acceptButton.isHovered()&&!data.getString("blocked").isEmpty())
                g.renderComponentTooltip(font,List.of(Ui.t("tactical.result."+data.getString("blocked"))),mx,my);
            else if(scanButton.isHovered())g.renderComponentTooltip(font,List.of(Ui.t("tactical.scan.tip")),mx,my);
        }
        private CompoundTag hoveredOffer(int mx,int my){
            if(view==2||!Ui.inside(mx,my,radarX,radarY,size,size))return null;
            for(var o:offers()){float[] at=toScreen(o.getInt("x")+.5,o.getInt("z")+.5);
                float cx=Mth.clamp(at[0],radarX+4,radarX+size-5),cy=Mth.clamp(at[1],radarY+4,radarY+size-5);
                if(Math.abs(mx-cx)<=5&&Math.abs(my-cy)<=5)return o;}
            return null;
        }
        @Override public boolean mouseClicked(double mx,double my,int button){
            var o=hoveredOffer((int)mx,(int)my);
            if(o!=null&&button==0){selected=o.getLong("id");abandonArmed=0;rebuildWidgets();return true;}
            return super.mouseClicked(mx,my,button);
        }
    }
}
