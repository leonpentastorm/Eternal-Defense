package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/**
 * Responsive two-column browser; bounded server pages and only selected-recipe costs.
 * Recipe costs and current-table upgrades share this browser; starter installations are fabricated at the beacon.
 */
final class PlatformScreen extends BeaconClient.PanelScreen {
    private static CompoundTag data=new CompoundTag();
    private final List<BeaconClient.Hover> hoverItems=new ArrayList<>();
    private final List<Ui.UiButton> rows=new ArrayList<>();
    private EditBox search,pageJump;private Ui.UiButton find,go,craft,upgrade,coins,previous,next,ageButton,armoryButton,turretButton,suppliesButton,typeButton,recipeTab,upgradeTab;
    private int selected,costScroll,ageFilter,upgradePage,dragPage;private boolean upgrades,navigating,dragging;
    private String weaponType="all";private WeaponBrowser.Layout layout;
    private ListTag paintedRows=new ListTag();private int paintedStart;
    private static final int[] AGE_COLORS=Ui.AGE;
    private static final String[] KINDS={"gun","ammo","attachment","armor"};
    PlatformScreen(){super(title());}
    private static Component title(){String kind=data.getString("kind");return Ui.t("platform.title."+(Ui.has("platform.title."+kind)?kind:"gun"));}
    @Override Component subtitle(){String kind=data.getString("kind");return Ui.has("platform.subtitle."+kind)?Ui.t("platform.subtitle."+kind):null;}
    static void receive(WeaponPlatform.State packet){
        data=packet.data();var mc=Minecraft.getInstance();
        if(packet.open()){mc.setScreen(new PlatformScreen());return;}
        PlatformScreen screen=mc.screen instanceof PlatformScreen s?s:mc.screen instanceof TypeScreen s?s.parent:null;
        if(screen!=null){screen.message=packet.message();screen.ageFilter=data.getInt("ageFilter");screen.weaponType=armor()?ArmorPlatform.normalize(data.getString("weaponType")):WeaponBrowser.normalize(data.getString("weaponType"));screen.navigating=false;screen.selected=Math.min(screen.selected,Math.max(0,screen.shown().size()-1));}
    }
    private static Component typeLabel(String type){return Ui.t("type."+(armor()?"armor.":"gun.")+type);}
    private static Component ageName(int age){return Ui.t("age."+Math.max(0,Math.min(5,age)));}

    private static final class TypeScreen extends BeaconClient.PanelScreen {
        private final PlatformScreen parent;
        TypeScreen(PlatformScreen parent){super(armor()?Ui.t("platform.type.armor"):Ui.t("platform.type.weapon"));this.parent=parent;}
        @Override protected void init(){
            super.init();pw=Math.min(490,width-16);ph=Math.min(250,height-16);left=(width-pw)/2;top=(height-ph)/2;int cw=(pw-34)/3;var counts=data.getCompound("typeCounts");
            var types=armor()?ArmorPlatform.TYPES:WeaponBrowser.TYPES;
            for(int i=0;i<types.size();i++){
                String type=types.get(i);
                var b=button(Ui.t("platform.type.entry",typeLabel(type),counts.getInt(type)),left+14+(i%3)*(cw+3),top+54+(i/3)*30,cw,Ui.Look.NORMAL,key->{parent.weaponType=type;Minecraft.getInstance().setScreen(parent);parent.browse(0);});
                b.selected=type.equals(parent.weaponType);
            }
            closeButton();
            button(Ui.t("back"),left+pw/2-40,top+ph-34,80,Ui.Look.PRIMARY,b->onClose());
        }
        @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){panel(g);Ui.text(g,font,Ui.t("platform.type.note"),left+14,top+36,Ui.MUTED,pw-28);super.render(g,mx,my,partial);}
    }
    private static final class NoticeScreen extends BeaconClient.PanelScreen {
        private final CompoundTag state;
        NoticeScreen(CompoundTag state){super(title());this.state=state.copy();}
        @Override protected void init(){super.init();pw=Math.min(400,width-16);ph=Math.min(190,height-16);left=(width-pw)/2;top=(height-ph)/2;clearWidgets();button(Ui.t("platform.notice.ok"),left+pw/2-45,top+ph-34,90,Ui.Look.PRIMARY,b->onClose());}
        @Override public void onClose(){WeaponPlatform.request("close","",0,"");super.onClose();}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            panel(g);String notice=state.getString("notice");boolean equip=notice.equals("Equip a gun");
            // The server sends these two notices as English text; recognise them and show the localized copy.
            Component heading=equip?Ui.t("platform.notice.equip"):notice.startsWith("Upgrade your ")?Ui.t("platform.notice.upgrade",notice.substring("Upgrade your ".length())):Component.literal(notice);
            Ui.card(g,left+14,top+38,pw-28,22,Ui.ORANGE);Ui.text(g,font,heading,left+26,top+45,Ui.INK,pw-52);
            Component hint=equip?Ui.t("platform.notice.equip.hint",ControlHints.interact()):state.getInt("locked")>0?Ui.t("platform.notice.locked",state.getInt("locked")):Ui.t("platform.notice.none");
            Ui.wrap(g,font,hint,left+16,top+72,pw-32,Ui.MUTED,5);super.render(g,mx,my,partial);
        }
    }
    @Override protected void init(){
        String query=search==null?data.getString("query"):search.getValue();super.init();layout=WeaponBrowser.layout(width,height,weapons()||armor(),armor());pw=layout.width();ph=layout.height();left=(width-pw)/2;top=(height-ph)/2;clearWidgets();rows.clear();int leftWidth=layout.listWidth();ageFilter=data.getInt("ageFilter");if(search==null)weaponType=armor()?ArmorPlatform.normalize(data.getString("weaponType")):WeaponBrowser.normalize(data.getString("weaponType"));
        search=addRenderableWidget(new EditBox(font,left+19,top+70,leftWidth-68,10,Ui.t("platform.search")));
        search.setBordered(false);search.setMaxLength(80);search.setValue(query);search.setHint(Ui.t("platform.search.hint").copy().withStyle(s->s.withColor(0x74888f)));
        find=button(Ui.t("platform.find"),left+leftWidth-40,top+64,54,Ui.Look.PRIMARY,b->browse(0));
        closeButton();
        boolean compact=layout.compact();int half=(leftWidth-4)/2;
        ageButton=button(Component.empty(),left+14,top+86,compact&&(weapons()||armor())?half:leftWidth,Ui.Look.NORMAL,b->{ageFilter=data.getString("kind").equals("ammo")?(ageFilter==5?10:ageFilter==10?11:ageFilter>=11?0:ageFilter+1):(ageFilter>=5?0:ageFilter+1);browse(0);});
        if(weapons()){
            int cw=(leftWidth-6)/3;
            armoryButton=button(Ui.t("platform.cat.armory"),left+14,top+108,cw,Ui.Look.NORMAL,b->{upgrades=false;ageFilter=7;browse(0);});
            turretButton=button(Ui.t("platform.cat.turrets"),left+17+cw,top+108,cw,Ui.Look.NORMAL,b->{upgrades=false;ageFilter=8;browse(0);});
            suppliesButton=button(Ui.t("platform.cat.supplies"),left+20+cw*2,top+108,cw,Ui.Look.NORMAL,b->{upgrades=false;ageFilter=9;browse(0);});
            typeButton=button(Component.empty(),compact?left+18+half:left+14,compact?top+86:top+130,compact?leftWidth-half-4:leftWidth,Ui.Look.NORMAL,b->Minecraft.getInstance().setScreen(new TypeScreen(this)));
        }
        if(armor())typeButton=button(Component.empty(),compact?left+18+half:left+14,compact?top+86:top+108,compact?leftWidth-half-4:leftWidth,Ui.Look.NORMAL,b->Minecraft.getInstance().setScreen(new TypeScreen(this)));
        for(int i=0;i<layout.capacity();i++){
            int index=i;
            var row=new Ui.UiButton(left+14+(i%layout.columns())*(layout.cellWidth()+4),top+layout.listTop()+(i/layout.columns())*rowHeight(),layout.cellWidth(),rowHeight()-1,Component.empty(),Ui.Look.ROW,b->{selected=index;costScroll=0;});
            row.painter((g,self,hover)->paintRow(g,self,index)).noLabel();
            rows.add(addRenderableWidget(row));
        }
        previous=button(Component.literal("<"),left+14,top+ph-34,22,Ui.Look.NORMAL,b->navigate(page()-1));next=button(Component.literal(">"),left+40,top+ph-34,22,Ui.Look.NORMAL,b->navigate(page()+1));
        pageJump=addRenderableWidget(new EditBox(font,left+73,top+ph-28,22,10,Ui.t("platform.page")));pageJump.setBordered(false);pageJump.setMaxLength(6);pageJump.setFilter(value->value.matches("[0-9]*"));
        go=button(Ui.t("platform.go"),left+104,top+ph-34,26,Ui.Look.NORMAL,b->jump());
        int right=left+leftWidth+22,rw=pw-leftWidth-36;
        recipeTab=button(Ui.t("platform.tab.recipe"),right,top+64,(rw-4)/2,Ui.Look.TAB,b->{upgrades=false;selected=0;costScroll=0;});
        upgradeTab=button(Ui.t("platform.tab.upgrade"),right+rw/2+2,top+64,(rw-4)/2,Ui.Look.TAB,b->{upgrades=true;selected=0;upgradePage=0;costScroll=0;});
        craft=button(Component.empty(),right,top+ph-55,data.getString("kind").equals("ammo")?(rw-4)/2:rw,Ui.Look.PRIMARY,b->{var recipe=chosen();if(recipe!=null)request("craft",recipe.getString("recipe"),0);});
        if(data.getString("kind").equals("ammo"))coins=button(Component.empty(),right+rw/2+2,top+ph-55,(rw-4)/2,Ui.Look.NORMAL,b->{var recipe=chosen();if(recipe!=null)request("buyAmmoCoins",recipe.getString("recipe"),0);});
        upgrade=button(Component.empty(),right,top+ph-55,rw,Ui.Look.PRIMARY,b->{var row=chosen();if(row!=null)request(row.getString("action"),"",row.getLong("pos"));});
        selected=Math.min(selected,layout.capacity()-1);upgradePage=WeaponBrowser.page(upgradePage,management().size(),layout.capacity());
        if(data.getInt("pageSize")!=layout.capacity())browse((data.getInt("page")*Math.max(1,data.getInt("pageSize")))/layout.capacity());
    }
    private int rowHeight(){return layout.rowHeight();}
    private void request(String action,String recipe,long target){WeaponPlatform.request(action,search.getValue(),data.getInt("page"),recipe,ageFilter,target,weaponType,layout.capacity());}
    private void browse(int page){selected=0;costScroll=0;navigating=true;WeaponPlatform.request("browse",search.getValue(),Math.max(0,page),"",ageFilter,0,weaponType,layout.capacity());}
    private int page(){return upgrades?upgradePage:data.getInt("page");}
    private int total(){return upgrades?management().size():data.getInt("total");}
    private int lastPage(){return WeaponBrowser.lastPage(total(),layout.capacity());}
    private void navigate(int page){int target=WeaponBrowser.page(page,total(),layout.capacity());if(upgrades){upgradePage=target;selected=costScroll=0;}else if(!navigating&&target!=data.getInt("page"))browse(target);}
    private void jump(){try{navigate(Integer.parseInt(pageJump.getValue())-1);}catch(NumberFormatException ignored){}pageJump.setFocused(false);}
    private static boolean armor(){return data.getString("kind").equals("armor");}
    private boolean weapons(){return data.getString("kind").equals("gun");}
    private ListTag management(){
        var list=new ListTag();var kind=data.getString("kind");
        list.add(managementRow(Ui.plain("platform.upgrade_this"),data.getInt("age"),"upgrade",0,data.getList("upgrades",Tag.TAG_COMPOUND),new ItemStack(ArmorPlatform.station(kind).get())));
        return list;
    }
    private CompoundTag managementRow(String label,int age,String action,long pos,ListTag costs,ItemStack output){var row=new CompoundTag();row.putString("label",label);row.putInt("age",age);row.putString("action",action);row.putLong("pos",pos);row.put("costs",costs);row.put("output",output.save(new CompoundTag()));return row;}
    private ListTag shown(){return upgrades?management():data.getList("recipes",Tag.TAG_COMPOUND);}
    private CompoundTag chosen(){var rows=shown();int index=selected+(upgrades?upgradePage*layout.capacity():0);return index>=0&&index<rows.size()?rows.getCompound(index):null;}
    @Override public void onClose(){WeaponPlatform.request("close","",0,"");super.onClose();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257&&pageJump.isFocused()){jump();return true;}if(key==257&&search.isFocused()){browse(0);return true;}if(!search.isFocused()&&!pageJump.isFocused()){if(key==266||key==267||key==268||key==269){navigate(key==268?0:key==269?lastPage():page()+(key==266?-1:1));return true;}if(PlatformKeys.matches(key,scan)){browse(data.getInt("page"));return true;}}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double mx,double my,double amount){if(mx>=left+14&&mx<left+14+layout.listWidth()&&my>=top+layout.listTop()&&my<top+ph-38){navigate(page()+(amount>0?-1:1));return true;}if(mx>=left+layout.listWidth()+22){costScroll=Math.max(0,costScroll-(int)amount);return true;}return super.mouseScrolled(mx,my,amount);}
    private boolean overScrollbar(double mx,double my){return mx>=left+layout.listWidth()+6&&mx<left+layout.listWidth()+14&&my>=top+layout.listTop()&&my<=top+ph-39;}
    private void dragTo(double my){double progress=(my-(top+layout.listTop()))/Math.max(1,ph-39-layout.listTop());dragPage=(int)Math.round(Math.max(0,Math.min(1,progress))*lastPage());}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&!upgrades&&lastPage()>0&&overScrollbar(mx,my)){dragging=true;dragTo(my);return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(dragging){dragTo(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(dragging){dragging=false;dragTo(my);navigate(dragPage);return true;}return super.mouseReleased(mx,my,button);}

    /** One catalogue row: icon, full name (ellipsised only if it cannot fit), lock mark and written Age. */
    private void paintRow(GuiGraphics g,Ui.UiButton b,int index){
        int at=paintedStart+index;if(at>=paintedRows.size())return;
        var recipe=paintedRows.getCompound(at);var output=ItemStack.of(recipe.getCompound("output"));
        int x=b.getX()+3,y=b.getY()+3,era=Math.max(0,Math.min(5,recipe.getInt("age")));
        int accent=AGE_COLORS[recipe.getBoolean("special")?Math.max(7,ageFilter):era];
        boolean locked=!upgrades&&!recipe.getBoolean("unlocked");
        g.fill(b.getX()+1,b.getY()+1,b.getX()+3,b.getY()+b.getHeight()-1,accent);
        g.renderItem(output,x+2,y);hoverItems.add(new BeaconClient.Hover(output,x+2,y));
        Component tag=upgrades||era==0||recipe.getBoolean("special")?Component.empty():Ui.t("platform.age_short",era);
        int tagWidth=b.getWidth()>=150&&!tag.getString().isEmpty()?font.width(tag)+4:0;
        if(tagWidth>0)Ui.right(g,font,tag,b.getX()+b.getWidth()-5,b.getY()+7,accent);
        int lockWidth=locked?10:0;
        if(locked){int lx=b.getX()+b.getWidth()-5-tagWidth-7,ly=b.getY()+9;g.fill(lx,ly+2,lx+7,ly+7,Ui.DISABLED);g.fill(lx+1,ly-1,lx+2,ly+2,Ui.DISABLED);g.fill(lx+5,ly-1,lx+6,ly+2,Ui.DISABLED);g.fill(lx+1,ly-1,lx+6,ly,Ui.DISABLED);}
        Component name=upgrades?Component.literal(recipe.getString("label")+(era>0?" / ":"")).append(era>0?ageName(era):Component.empty()):output.getHoverName();
        Ui.text(g,font,name,b.getX()+25,b.getY()+7,locked?Ui.DISABLED:Ui.INK,b.getWidth()-30-tagWidth-lockWidth);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        hoverItems.clear();panel(g);int lw=layout.listWidth(),right=left+lw+22,rw=pw-lw-36;
        // Four separate station Ages, each with a written name and pips; the table you are using is outlined.
        int badgeWidth=(pw-28)/4;
        for(int i=0;i<4;i++){
            int value=Math.max(0,Math.min(5,data.getInt(KINDS[i]+"Age"))),x=left+14+i*badgeWidth,w=badgeWidth-3,y=top+33,accent=AGE_COLORS[value];
            boolean here=data.getString("kind").equals(KINDS[i]);
            if(here){g.fill(x-2,y-3,x+w+2,y+31,Ui.CYAN);g.fill(x-1,y-2,x+w+1,y+30,Ui.CYAN_FILL);}g.fill(x,y,x+w,y+28,here?Ui.CYAN:Ui.SLATE_HI);g.fill(x+1,y+1,x+w-1,y+27,Ui.SLATE);g.fill(x+1,y+1,x+w-1,y+3,accent);
            Ui.text(g,font,Ui.t("platform.station."+KINDS[i]),x+5,y+6,here?Ui.INK:Ui.MUTED,w-(w>=118?Ui.pipsWidth(5)+14:10));
            if(w>=118)Ui.pips(g,x+w-Ui.pipsWidth(5)-5,y+7,value,5,accent);
            Ui.text(g,font,value==0?Ui.t("platform.no_table"):Ui.t("platform.age_line",value,ageName(value)),x+5,y+17,value==0?Ui.DISABLED:accent,w-10);
        }
        boolean special=ageFilter>=7&&ageFilter<=9;
        Component browsing=special?Ui.t(data.getBoolean("standalone")?"platform.progression":"platform.create_progress",CreateUnlocks.stage(data.getInt("createLevel"))):Ui.t("platform.browsing."+data.getString("kind"),data.getInt("ammoCoins"));
        if(!layout.compact())Ui.text(g,font,browsing,left+14,top+(weapons()?154:armor()?132:114),Ui.CYAN,lw);
        ageButton.visible=!upgrades;
        ageButton.setMessage(ageFilter==10?Ui.t("platform.filter.loose"):ageFilter==11?Ui.t("platform.filter.magazines"):ageFilter==0?Ui.t("platform.filter.all"):ageFilter==6?Ui.t("platform.cat.supplies"):ageFilter>=7?Ui.t("platform.filter.back"):Ui.t("platform.age_line",ageFilter,ageName(ageFilter)).copy().withStyle(s->s.withColor(AGE_COLORS[ageFilter]&0xffffff)));
        ageButton.selected=ageFilter>0&&ageFilter<=5;ageButton.accent=AGE_COLORS[Math.max(0,Math.min(AGE_COLORS.length-1,ageFilter))];
        if(armoryButton!=null){
            armoryButton.visible=turretButton.visible=suppliesButton.visible=!upgrades;
            armoryButton.selected=ageFilter==7;turretButton.selected=ageFilter==8;suppliesButton.selected=ageFilter==9;
            armoryButton.accent=AGE_COLORS[7];turretButton.accent=AGE_COLORS[8];suppliesButton.accent=AGE_COLORS[9];
        }
        if(typeButton!=null){typeButton.visible=!upgrades;typeButton.active=data.getBoolean("typeFilterAvailable")&&!navigating;typeButton.setMessage(data.getBoolean("typeFilterAvailable")?Ui.t("platform.type.button",typeLabel(weaponType)):Ui.t("platform.type.unavailable"));}
        recipeTab.selected=!upgrades;upgradeTab.selected=upgrades;
        var recipes=shown();int start=upgrades?upgradePage*layout.capacity():0,total=upgrades?recipes.size():data.getInt("total"),page=upgrades?upgradePage:data.getInt("page");
        paintedRows=recipes;paintedStart=start;
        for(int i=0;i<rows.size();i++){var row=rows.get(i);row.visible=start+i<recipes.size();row.active=row.visible;row.selected=row.visible&&i==selected;if(row.visible&&start+i<recipes.size()){var r=recipes.getCompound(start+i);row.accent=AGE_COLORS[r.getBoolean("special")?Math.max(7,ageFilter):Math.max(0,Math.min(5,r.getInt("age")))];}}
        previous.active=page>0&&(upgrades||!navigating);next.active=page<lastPage()&&(upgrades||!navigating);
        // A one-row upgrade list needs no search, pager or scrollbar.
        search.visible=find.visible=previous.visible=next.visible=pageJump.visible=go.visible=!upgrades;
        for(int i=0;i<rows.size();i++)rows.get(i).setY(upgrades?top+64+i*rowHeight():top+layout.listTop()+(i/layout.columns())*rowHeight());
        if(!pageJump.isFocused())pageJump.setValue(Integer.toString((dragging?dragPage:page)+1));
        if(!upgrades){
            Ui.text(g,font,total==0?Ui.t("platform.no_matches"):Ui.t("platform.range",page*layout.capacity()+1,Math.min(total,(page+1)*layout.capacity()),total),left+136,top+ph-28,Ui.MUTED,lw-136);
            int sx=left+lw+6,sy=top+layout.listTop(),sh=ph-39-layout.listTop();g.fill(sx,sy,sx+8,sy+sh,Ui.DEEP);
            int thumb=Math.min(sh,Math.max(12,sh/Math.max(1,lastPage()+1))),thumbY=sy+(lastPage()==0?0:(int)((sh-thumb)*(dragging?dragPage:page)/(double)lastPage()));g.fill(sx+1,thumbY,sx+7,thumbY+thumb,dragging?Ui.INK:Ui.CYAN);
        }else Ui.wrap(g,font,Ui.t("platform.upgrade_info"),left+14,top+64+rowHeight()+8,lw,Ui.MUTED,6);
        craft.visible=!upgrades;upgrade.visible=upgrades;upgrade.active=false;
        CompoundTag chosen=chosen();int targetAge=chosen==null?0:chosen.getInt("age");boolean buying=upgrades&&targetAge==0;boolean baseGear=chosen!=null&&(chosen.getString("action").equals("buySupport")||chosen.getString("action").equals("buyExchange"));
        ListTag costs=chosen==null?new ListTag():chosen.getList("costs",Tag.TAG_COMPOUND);
        boolean missing=false;if(!data.getBoolean("creative"))for(var c:costs)if(((CompoundTag)c).getInt("have")<((CompoundTag)c).getInt("count"))missing=true;
        upgrade.setMessage(buying?Ui.t("platform.buy_station"):Ui.t("platform.upgrade_age"));upgrade.active=upgrades&&chosen!=null&&(buying||targetAge<5&&(targetAge!=2||data.getBoolean("netherVisited")));upgrade.warning=missing&&upgrade.active;
        craft.active=!navigating&&chosen!=null&&chosen.getBoolean("unlocked")&&!chosen.getBoolean("invalid");craft.warning=missing&&craft.active;
        if(coins!=null){coins.visible=!upgrades;coins.active=craft.active&&(data.getBoolean("creative")||chosen.getInt("coinCost")<=data.getInt("ammoCoins"));coins.setMessage(data.getBoolean("creative")?Ui.t("platform.buy_free"):chosen==null?Ui.t("platform.buy_coins"):Ui.t("platform.buy_cost",chosen.getInt("coinCost")));coins.warning=chosen!=null&&!data.getBoolean("creative")&&chosen.getInt("coinCost")>data.getInt("ammoCoins")&&craft.active;}
        craft.setMessage(chosen==null?Ui.t("platform.craft"):data.getBoolean("creative")?Ui.t("platform.craft_free"):missing?Ui.t("platform.craft_missing"):data.getString("kind").equals("ammo")?Ui.t("platform.craft_materials"):Ui.t("platform.craft_items",Math.max(1,chosen.getInt("outputCount"))));
        Component heading=upgrades?!weapons()?Ui.t("platform.heading.manage"):buying?baseGear?ItemStack.of(chosen.getCompound("output")).getHoverName():Ui.t("platform.heading.new"):targetAge==5?Ui.t("platform.heading.max"):Ui.t("platform.heading.next",ageName(Math.min(5,targetAge+1))):chosen==null?data.getString("notice").isEmpty()?Ui.t("platform.heading.none"):Component.literal(data.getString("notice")):ItemStack.of(chosen.getCompound("output")).getHoverName();
        boolean compact=ph<280;int costTop=compact?126:144;
        g.enableScissor(right,top+95,right+rw,top+(compact?108:123));
        boolean oneLine=font.width(heading)<=rw&&chosen!=null&&!upgrades;
        if(compact||oneLine)Ui.text(g,font,heading,right,top+96,Ui.CYAN,rw);else Ui.wrap(g,font,heading,right,top+96,rw,Ui.CYAN,2);
        if(oneLine&&!compact&&!chosen.getString("progression").isEmpty()){int era=Math.max(0,Math.min(5,chosen.getInt("age")));Ui.text(g,font,Component.literal(chosen.getString("progression")),right,top+109,AGE_COLORS[chosen.getBoolean("special")?Math.max(7,ageFilter):era],rw);}
        g.disableScissor();
        Ui.text(g,font,upgrades?(chosen!=null&&!chosen.getString("location").isEmpty()?Component.literal(chosen.getString("location")):baseGear?Ui.t("platform.base_gear"):Ui.t("platform.within_eight")):data.getBoolean("creative")?Ui.t("platform.creative_free"):Ui.t("platform.materials"),right,top+(compact?112:126),Ui.MUTED,rw);
        boolean locked=!upgrades&&chosen!=null&&!chosen.getBoolean("unlocked");
        if(locked){
            Component unlock=Ui.t("platform.unlock",chosen.getString("requirement"));
            if(compact)Ui.text(g,font,unlock,right,top+ph-76,Ui.ORANGE,rw);
            else{int y=top+ph-97;for(var line:font.split(unlock,rw)){if(y>=top+ph-56)break;g.drawString(font,line,right,y,Ui.ORANGE,false);y+=11;}}
        }
        int visible=Math.max(1,compact?(ph-79-costTop)/22:(ph-(locked?253:237))/22);costScroll=Math.min(costScroll,Math.max(0,costs.size()-visible));
        for(int i=costScroll;i<Math.min(costs.size(),costScroll+visible);i++){
            var cost=costs.getCompound(i);ItemStack item=ItemStack.of(cost.getCompound("item"));int y=top+costTop+(i-costScroll)*22;boolean enough=cost.getInt("have")>=cost.getInt("count");
            g.renderItem(item,right,y);hoverItems.add(new BeaconClient.Hover(item,right,y));
            Ui.text(g,font,Component.literal(cost.getString("label").isEmpty()?item.getHoverName().getString():cost.getString("label")),right+22,y,enough?Ui.INK:Ui.ORANGE,rw-23);
            Ui.text(g,font,enough||data.getBoolean("creative")?Ui.t("platform.cost",cost.getInt("have"),cost.getInt("count")):Ui.t("platform.cost_short",cost.getInt("have"),cost.getInt("count"),cost.getInt("count")-cost.getInt("have")),right+22,y+10,enough?Ui.MUTED:Ui.ORANGE,rw-23);
        }
        if(!upgrades&&chosen!=null&&chosen.getBoolean("magazine"))Ui.text(g,font,Ui.t("platform.magazine",chosen.getInt("capacity")),right,top+ph-(compact?74:83),Ui.MUTED,rw);
        if(upgrades&&targetAge==2)Ui.text(g,font,!data.getBoolean("netherVisited")?Ui.t("platform.nether"):costs.size()>visible?Ui.t("platform.nether_scroll"):Ui.t("platform.nether_materials"),right,top+ph-70,Ui.ORANGE,rw);
        else if(costs.size()>visible&&!locked&&(!compact||chosen==null||!chosen.getBoolean("magazine")))Ui.text(g,font,Ui.t("platform.scroll",costs.size()),right,top+ph-70,Ui.MUTED,rw);
        if(ArmorPlatform.component(data.getString("kind"))&&!upgrades)Ui.text(g,font,Ui.t("platform.later",data.getInt("locked")),right,top+ph-33,Ui.MUTED,rw);
        if(ArmorPlatform.component(data.getString("kind"))&&chosen==null)Ui.wrap(g,font,Ui.t("platform.hold_gun",ControlHints.interact()),left+14,top+149,lw,Ui.MUTED,3);
        if(!upgrades){Ui.field(g,left+14,top+64,lw-58,20,search.isFocused());Ui.field(g,left+68,top+ph-34,32,20,pageJump.isFocused());}
        super.render(g,mx,my,partial);
        footerLine(g,top+ph-12);
        var hover=itemAt(mx,my);if(upgradeTab.isHovered()||upgrade.visible&&upgrade.isHovered())Ui.materialTooltip(g,font,List.of(Ui.t("platform.upgrade_this")),upgradeTab.isHovered()?data.getList("upgrades",Tag.TAG_COMPOUND):costs,mx,my);else if(hover!=null)g.renderComponentTooltip(font,List.of(hover.item().getHoverName(),Component.literal(ControlHints.jei())),mx,my);
        else if(layout.compact()&&ageButton.visible&&ageButton.isMouseOver(mx,my)&&special)g.renderComponentTooltip(font,List.of(browsing),mx,my);
        else if(pageJump.isMouseOver(mx,my)||overScrollbar(mx,my))g.renderComponentTooltip(font,List.of(Ui.t("platform.page_tip",(dragging?dragPage+1:page+1),lastPage()+1),Ui.t("platform.page_tip.scroll"),Ui.t("platform.page_tip.keys")),mx,my);
        else if(!upgrades)for(int i=0;i<rows.size();i++){Ui.UiButton row=rows.get(i);if(row.visible&&Ui.inside(mx,my,row.getX(),row.getY(),row.getWidth(),row.getHeight())){
            var recipe=recipes.getCompound(i);int era=Math.max(1,Math.min(5,recipe.getInt("age")));
            var lines=new ArrayList<Component>(List.of(ItemStack.of(recipe.getCompound("output")).getHoverName(),Component.literal(recipe.getString("progression")).withStyle(style->style.withColor(AGE_COLORS[era]&0xffffff))));
            if(!recipe.getBoolean("unlocked"))lines.add(Ui.t("platform.locked_tip"));
            g.renderComponentTooltip(font,lines,mx,my);break;
        }}
    }
    @Override BeaconClient.Hover itemAt(double x,double y){for(var item:hoverItems)if(Ui.inside(x,y,item.x(),item.y(),16,16))return item;return null;}
}
