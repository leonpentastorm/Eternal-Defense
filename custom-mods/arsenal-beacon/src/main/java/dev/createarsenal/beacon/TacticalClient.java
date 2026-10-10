package dev.createarsenal.beacon;

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
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TacticalClient {
    private TacticalClient(){}
    @SubscribeEvent public static void screens(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(ArsenalBeacon.TABLE_MENU.get(),TableScreen::new));}

    // ---- the mission line ----------------------------------------------------------------------------------------------------------------
    static Hunts.MissionSync mission=new Hunts.MissionSync(false,"","",0,0,0,0,false);
    static void mission(Hunts.MissionSync m){mission=m;}
    static boolean missionActive(){return mission.active();}
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
    static final ResourceLocation[] TEXTURE={new ResourceLocation(ArsenalBeacon.ID,"tactical/base"),new ResourceLocation(ArsenalBeacon.ID,"tactical/region")},
        PREVIOUS={new ResourceLocation(ArsenalBeacon.ID,"tactical/base_previous"),new ResourceLocation(ArsenalBeacon.ID,"tactical/region_previous")};
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
    static void state(CompoundTag data){if(Minecraft.getInstance().screen instanceof TableScreen screen)screen.update(data);}
    /** Kept while the table is reopened: which view and which offer. */
    static int view=1;static long selected=-1;

    static final class TableScreen extends AbstractContainerScreen<TacticalBlocks.TableMenu> {
        private CompoundTag data;private long openedAt,abandonArmed;
        private int size,radarX,radarY,sideX,sideW;
        private final List<Ui.UiButton> rows=new ArrayList<>();
        private Ui.UiButton scanButton,upgradeButton,acceptButton,abandonButton,reprintButton;
        TableScreen(TacticalBlocks.TableMenu menu,Inventory inv,Component title){super(menu,inv,title);data=menu.data;openedAt=net.minecraft.Util.getMillis();scanning=data.getBoolean("scanning");}
        void update(CompoundTag next){data=next;openedAt=net.minecraft.Util.getMillis();scanning=next.getBoolean("scanning");rebuildWidgets();}

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
            imageWidth=Math.min(width-12,440);imageHeight=Math.min(height-12,292);
            super.init();clearWidgets();rows.clear();
            size=Math.max(96,Math.min(imageHeight-28-24-30,imageWidth-24-150));
            radarX=leftPos+12;radarY=topPos+28+24;sideX=radarX+size+10;sideW=leftPos+imageWidth-12-sideX;
            addRenderableWidget(new Ui.UiButton(leftPos+imageWidth-28,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            // toolbar: Base / Region, then SCAN MAP and the satellite upgrade
            var base=addRenderableWidget(new Ui.UiButton(radarX,topPos+31,60,18,Ui.t("tactical.view.base"),Ui.Look.TAB,b->{view=0;rebuildWidgets();}));
            var region=addRenderableWidget(new Ui.UiButton(radarX+62,topPos+31,60,18,Ui.t("tactical.view.region"),Ui.Look.TAB,b->{view=1;rebuildWidgets();}));
            base.selected=view==0;region.selected=view==1;
            scanButton=addRenderableWidget(new Ui.UiButton(radarX+128,topPos+31,Math.max(70,size-128),18,Ui.t("tactical.scan"),Ui.Look.PRIMARY,b->click(TacticalBlocks.button(TacticalBlocks.SCAN,0))));
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
        private void abandon(){
            long now=net.minecraft.Util.getMillis();
            if(abandonArmed<now){abandonArmed=now+5000;abandonButton.setMessage(Ui.t("tactical.abandon.confirm"));return;}
            abandonArmed=0;click(TacticalBlocks.button(TacticalBlocks.ABANDON,0));
        }
        private void click(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}

        @Override public void containerTick(){
            super.containerTick();
            if(abandonButton!=null&&abandonArmed!=0&&abandonArmed<net.minecraft.Util.getMillis()){abandonArmed=0;abandonButton.setMessage(Ui.t("tactical.abandon"));}
            if(scanButton!=null){
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
            drawRadar(g,mx,my);
            drawSide(g);
            var message=data.getString("message");
            if(!message.isEmpty()){boolean good=message.endsWith(".accepted")||message.endsWith(".upgraded")||message.endsWith(".reprinted")||message.endsWith(".scan_started")||message.endsWith(".abandoned");
                Ui.text(g,font,Component.translatable("gui.arsenal_beacon."+message),radarX,topPos+imageHeight-14,good?Ui.CYAN:Ui.ORANGE,size);}
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
                Ui.wrap(g,font,Ui.t("tactical.no_scan"),x0+8,y0+size/2-8,size-16,Ui.MUTED,3);
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
                int color=kindColor(o.getString("kind"));
                float cx=Mth.clamp(at[0],x0+4,x1-5),cy=Mth.clamp(at[1],y0+4,y1-5);boolean edge=cx!=at[0]||cy!=at[1];
                if(edge){g.fill((int)cx-2,(int)cy-2,(int)cx+3,(int)cy+3,0xff000000);g.fill((int)cx-1,(int)cy-1,(int)cx+2,(int)cy+2,color);continue;}
                cross(g,(int)cx,(int)cy,active&&pulse==0?0xffffffff:color,chosen||active?4:3);
                if(chosen||active)ring(g,cx+.5f,cy+.5f,7,active?0xffed7369:0xccedf5f8);
            }
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
        static int kindColor(String kind){return switch(kind){case "garrison"->Ui.ORANGE;case "warlord"->Ui.RED;default->Ui.CYAN;};}

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
                g.renderItem(new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get()),cx-2,cy-3);Ui.text(g,font,Component.literal("x"+o.getInt("ardent")),cx+15,cy+2,Ui.CYAN,30);
                g.renderItem(new ItemStack(ArsenalBeacon.AMMO_COIN.get()),cx+46,cy-3);Ui.text(g,font,Component.literal("x"+o.getInt("coins")),cx+63,cy+2,Ui.CYAN,30);cy+=16;
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
            if(!Ui.inside(mx,my,radarX,radarY,size,size))return null;
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
