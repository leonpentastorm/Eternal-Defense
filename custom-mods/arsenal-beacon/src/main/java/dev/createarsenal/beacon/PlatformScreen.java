package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Responsive two-column browser; bounded server pages and only selected-recipe costs. */
final class PlatformScreen extends BeaconClient.PanelScreen {
    private static CompoundTag data=new CompoundTag();
    private final List<BeaconClient.Hover> hoverItems=new ArrayList<>();
    private final List<Button> rows=new ArrayList<>();
    private EditBox search,pageJump;private Button craft,upgrade,coins,previous,next,ageButton,armoryButton,turretButton,suppliesButton,typeButton;
    private int selected,costScroll,ageFilter,workshopPage,dragPage;private boolean upgrades,navigating,dragging;
    private String weaponType="all";private WeaponBrowser.Layout layout;
    private static final int[] AGE_COLORS={0xffffff,0xe8c17b,0xa7ca8b,0x6cd4ef,0xc29aff,0xff86bd,0xffffff,0xe8b85c,0xffa36c,0xb7e3b2,0xffffff,0x6cd4ef};
    private static final int INK=0xffedf5f8,MUTED=0xff9fb3bc,CYAN=0xff5ae2df;
    PlatformScreen(){super(armor()?"UNIVERSAL ARMOR PLATFORM":"UNIVERSAL WEAPON PLATFORM");}
    static void receive(WeaponPlatform.State packet){
        data=packet.data();var mc=Minecraft.getInstance();
        if(packet.open()){mc.setScreen(data.getString("notice").isEmpty()?new PlatformScreen():new NoticeScreen(data));return;}
        PlatformScreen screen=mc.screen instanceof PlatformScreen s?s:mc.screen instanceof TypeScreen s?s.parent:null;
        if(screen!=null){screen.message=packet.message();screen.ageFilter=data.getInt("ageFilter");screen.weaponType=armor()?ArmorPlatform.normalize(data.getString("weaponType")):WeaponBrowser.normalize(data.getString("weaponType"));screen.navigating=false;screen.selected=Math.min(screen.selected,Math.max(0,screen.shown().size()-1));}
    }
    private static final class TypeScreen extends BeaconClient.PanelScreen {
        private final PlatformScreen parent;
        TypeScreen(PlatformScreen parent){super(armor()?"CHOOSE GEAR TYPE":"CHOOSE WEAPON TYPE");this.parent=parent;}
        @Override protected void init(){super.init();pw=Math.min(490,width-16);ph=Math.min(250,height-16);left=(width-pw)/2;top=(height-ph)/2;int cw=(pw-34)/3;var counts=data.getCompound("typeCounts");
            var types=armor()?ArmorPlatform.TYPES:WeaponBrowser.TYPES;for(int i=0;i<types.size();i++){String type=types.get(i);Button b=button((armor()?ArmorPlatform.label(type):WeaponBrowser.label(type))+" ("+counts.getInt(type)+")",left+14+(i%3)*(cw+3),top+54+(i/3)*30,cw,key->{parent.weaponType=type;Minecraft.getInstance().setScreen(parent);parent.browse(0);});b.setMessage(b.getMessage().copy().withStyle(style->style.withColor(type.equals(parent.weaponType)?0x5ae2df:0xffffff)));}
            button("Back",left+pw/2-40,top+ph-33,80,b->onClose());
        }
        @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){panel(g);text(g,"Combines with the current Age and name search.",left+14,top+34,MUTED);super.render(g,mx,my,partial);}
    }
    private static final class NoticeScreen extends BeaconClient.PanelScreen {
        private final CompoundTag state;
        NoticeScreen(CompoundTag state){super("WEAPON PLATFORM");this.state=state.copy();}
        @Override protected void init(){super.init();pw=Math.min(400,width-16);ph=Math.min(190,height-16);left=(width-pw)/2;top=(height-ph)/2;clearWidgets();button("Got it",left+pw/2-45,top+ph-34,90,b->onClose());}
        @Override public void onClose(){WeaponPlatform.request("close","",0,"");super.onClose();}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){panel(g);text(g,state.getString("notice"),left+16,top+48,CYAN);
            String hint=state.getString("notice").equals("Equip a gun")?"Hold a gun in your main hand, then press ["+ControlHints.interact()+"] while looking at this table.":state.getInt("locked")>0?state.getInt("locked")+" compatible items require later Ages. Open Workshop at a nearby Weapon Platform to upgrade this table.":"This gun has no craftable compatible components in the installed catalogue. Try another gun.";
            wrap(g,hint,left+16,top+72,pw-32,MUTED);super.render(g,mx,my,partial);
        }
    }
    @Override protected void init(){
        String query=search==null?data.getString("query"):search.getValue();super.init();layout=WeaponBrowser.layout(width,height,weapons()||armor());pw=layout.width();ph=layout.height();left=(width-pw)/2;top=(height-ph)/2;clearWidgets();rows.clear();int leftWidth=layout.listWidth();ageFilter=data.getInt("ageFilter");if(search==null)weaponType=armor()?ArmorPlatform.normalize(data.getString("weaponType")):WeaponBrowser.normalize(data.getString("weaponType"));
        search=addRenderableWidget(new EditBox(font,left+14,top+64,leftWidth-58,20,Component.literal("Search weapon name or pack")));
        search.setMaxLength(80);search.setValue(query);
        button("Find",left+leftWidth-40,top+64,54,b->browse(0));
        button("X",left+pw-34,top+8,20,b->onClose());
        ageButton=button("All Ages",left+14,top+86,leftWidth,b->{ageFilter=data.getString("kind").equals("ammo")?(ageFilter==5?10:ageFilter==10?11:ageFilter>=11?0:ageFilter+1):(ageFilter>=5?0:ageFilter+1);browse(0);});
        if(weapons()){
            int cw=(leftWidth-6)/3;
            armoryButton=button("Create Armory",left+14,top+108,cw,b->{upgrades=false;ageFilter=7;browse(0);});
            turretButton=button("Turrets",left+17+cw,top+108,cw,b->{upgrades=false;ageFilter=8;browse(0);});
            suppliesButton=button("Supplies",left+20+cw*2,top+108,cw,b->{upgrades=false;ageFilter=9;browse(0);});
            typeButton=button("Type: All weapon types",left+14,top+130,leftWidth,b->Minecraft.getInstance().setScreen(new TypeScreen(this)));
        }
        if(armor())typeButton=button("Type: All gear types",left+14,top+108,leftWidth,b->Minecraft.getInstance().setScreen(new TypeScreen(this)));
        for(int i=0;i<layout.capacity();i++){int index=i;rows.add(addRenderableWidget(Button.builder(Component.literal(""),b->{selected=index;costScroll=0;}).bounds(left+14+(i%layout.columns())*(layout.cellWidth()+4),top+layout.listTop()+(i/layout.columns())*rowHeight(),layout.cellWidth(),rowHeight()-1).build()));}
        previous=button("<",left+14,top+ph-34,22,b->navigate(page()-1));next=button(">",left+40,top+ph-34,22,b->navigate(page()+1));
        pageJump=addRenderableWidget(new EditBox(font,left+68,top+ph-34,32,20,Component.literal("Jump to page")));pageJump.setMaxLength(6);pageJump.setFilter(value->value.matches("[0-9]*"));
        button("Go",left+104,top+ph-34,26,b->jump());
        int right=left+leftWidth+22,rw=pw-leftWidth-36;
        button("Recipe",right,top+64,(rw-4)/2,b->{upgrades=false;selected=0;costScroll=0;});
        button(data.getString("kind").equals("gun")?"Workshop":"Table info",right+rw/2+2,top+64,(rw-4)/2,b->{upgrades=true;selected=0;workshopPage=0;costScroll=0;});
        craft=button("Craft selected",right,top+ph-55,data.getString("kind").equals("ammo")?(rw-4)/2:rw,b->{var recipe=chosen();if(recipe!=null)request("craft",recipe.getString("recipe"),0);});
        if(data.getString("kind").equals("ammo"))coins=button("Buy: coins",right+rw/2+2,top+ph-55,(rw-4)/2,b->{var recipe=chosen();if(recipe!=null)request("buyAmmoCoins",recipe.getString("recipe"),0);});
        upgrade=button("Upgrade Age",right,top+ph-55,rw,b->{var row=chosen();if(row!=null)request(row.getString("action"),"",row.getLong("pos"));});
        selected=Math.min(selected,layout.capacity()-1);workshopPage=WeaponBrowser.page(workshopPage,management().size(),layout.capacity());
        if(data.getInt("pageSize")!=layout.capacity())browse((data.getInt("page")*Math.max(1,data.getInt("pageSize")))/layout.capacity());
    }
    private int rowHeight(){return layout.rowHeight();}
    private void request(String action,String recipe,long target){WeaponPlatform.request(action,search.getValue(),data.getInt("page"),recipe,ageFilter,target,weaponType,layout.capacity());}
    private void browse(int page){selected=0;costScroll=0;navigating=true;WeaponPlatform.request("browse",search.getValue(),Math.max(0,page),"",ageFilter,0,weaponType,layout.capacity());}
    private int page(){return upgrades?workshopPage:data.getInt("page");}
    private int total(){return upgrades?management().size():data.getInt("total");}
    private int lastPage(){return WeaponBrowser.lastPage(total(),layout.capacity());}
    private void navigate(int page){int target=WeaponBrowser.page(page,total(),layout.capacity());if(upgrades){workshopPage=target;selected=costScroll=0;}else if(!navigating&&target!=data.getInt("page"))browse(target);}
    private void jump(){try{navigate(Integer.parseInt(pageJump.getValue())-1);}catch(NumberFormatException ignored){}pageJump.setFocused(false);}
    private static boolean armor(){return data.getString("kind").equals("armor");}
    private boolean weapons(){return data.getString("kind").equals("gun");}
    private ListTag management(){
        var list=new ListTag();if(!weapons())return list;
        list.add(managementRow("Upgrade weapon table",data.getInt("age"),"upgrade",0,data.getList("upgrades",Tag.TAG_COMPOUND),new ItemStack(ArsenalBeacon.GUN_PLATFORM.get())));
        list.add(managementRow("Buy ammo table",0,"buyAmmo",0,data.getList("buyAmmo",Tag.TAG_COMPOUND),new ItemStack(ArsenalBeacon.AMMO_PLATFORM.get())));
        list.add(managementRow("Buy attachment table",0,"buyAttachment",0,data.getList("buyAttachment",Tag.TAG_COMPOUND),new ItemStack(ArsenalBeacon.ATTACHMENT_PLATFORM.get())));
        list.add(managementRow("Buy armor table",0,"buyArmor",0,data.getList("buyArmor",Tag.TAG_COMPOUND),new ItemStack(ArsenalBeacon.ARMOR_PLATFORM.get())));
        for(var child:data.getList("children",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)child;var pos=net.minecraft.core.BlockPos.of(row.getLong("pos"));
            var entry=managementRow("Upgrade "+row.getString("kind"),row.getInt("age"),"upgradeChild",row.getLong("pos"),row.getList("upgrades",Tag.TAG_COMPOUND),new ItemStack(ArmorPlatform.station(row.getString("kind")).get()));
            entry.putString("location",pos.getX()+", "+pos.getY()+", "+pos.getZ());list.add(entry);
        }return list;
    }
    private CompoundTag managementRow(String label,int age,String action,long pos,ListTag costs,ItemStack output){var row=new CompoundTag();row.putString("label",label);row.putInt("age",age);row.putString("action",action);row.putLong("pos",pos);row.put("costs",costs);row.put("output",output.save(new CompoundTag()));return row;}
    private ListTag shown(){return upgrades?management():data.getList("recipes",Tag.TAG_COMPOUND);}
    private CompoundTag chosen(){var rows=shown();int index=selected+(upgrades?workshopPage*layout.capacity():0);return index>=0&&index<rows.size()?rows.getCompound(index):null;}
    @Override public void onClose(){WeaponPlatform.request("close","",0,"");super.onClose();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257&&pageJump.isFocused()){jump();return true;}if(key==257&&search.isFocused()){browse(0);return true;}if(!search.isFocused()&&!pageJump.isFocused()){if(key==266||key==267||key==268||key==269){navigate(key==268?0:key==269?lastPage():page()+(key==266?-1:1));return true;}if(PlatformKeys.matches(key,scan)){browse(data.getInt("page"));return true;}}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double mx,double my,double amount){if(mx>=left+14&&mx<left+14+layout.listWidth()&&my>=top+layout.listTop()&&my<top+ph-38){navigate(page()+(amount>0?-1:1));return true;}if(mx>=left+layout.listWidth()+22){costScroll=Math.max(0,costScroll-(int)amount);return true;}return super.mouseScrolled(mx,my,amount);}
    private boolean overScrollbar(double mx,double my){return mx>=left+layout.listWidth()+6&&mx<left+layout.listWidth()+14&&my>=top+layout.listTop()&&my<=top+ph-39;}
    private void dragTo(double my){double progress=(my-(top+layout.listTop()))/Math.max(1,ph-39-layout.listTop());dragPage=(int)Math.round(Math.max(0,Math.min(1,progress))*lastPage());}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&lastPage()>0&&overScrollbar(mx,my)){dragging=true;dragTo(my);return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(dragging){dragTo(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(dragging){dragging=false;dragTo(my);navigate(dragPage);return true;}return super.mouseReleased(mx,my,button);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        hoverItems.clear();panel(g);int age=data.getInt("age"),lw=layout.listWidth(),right=left+lw+22,rw=pw-lw-36;
        int badgeWidth=(pw-28)/4;String[] kinds={"gun","ammo","attachment","armor"};
        for(int i=0;i<4;i++){int value=data.getInt(kinds[i]+"Age"),x=left+14+i*badgeWidth;g.fill(x,top+33,x+badgeWidth-3,top+61,0xff213747);g.fill(x,top+33,x+badgeWidth-3,top+35,0xff000000|AGE_COLORS[value]);text(g,(i==0?"WEAPONS":i==1?"AMMO":i==2?"ATTACHMENTS":"ARMOR & GEAR"),x+5,top+38,MUTED);text(g,font.plainSubstrByWidth(value==0?"No nearby table":"Age "+value+" - "+WeaponPlatform.AGES[value],badgeWidth-10),x+5,top+50,0xff000000|AGE_COLORS[value]);}
        text(g,font.plainSubstrByWidth(ageFilter>=7&&ageFilter<=9?(data.getBoolean("standalone")?"PROGRESSION: ":"CREATE PROGRESS: ")+CreateUnlocks.stage(data.getInt("createLevel")):"BROWSING: "+data.getString("kind").toUpperCase(Locale.ROOT)+(data.getString("kind").equals("ammo")?" | Coins: "+data.getInt("ammoCoins"):""),lw),left+14,top+(weapons()?154:armor()?132:114),CYAN);
        ageButton.visible=!upgrades;ageButton.setMessage(Component.literal(ageFilter==10?"Loose ammunition":ageFilter==11?"Compatible magazines":ageFilter==0?"All Ages":ageFilter==6?"Supplies":ageFilter>=7?"Back to normal category":WeaponPlatform.AGES[ageFilter]).withStyle(style->style.withColor(AGE_COLORS[ageFilter])));
        if(armoryButton!=null){armoryButton.setMessage(Component.literal("Create Armory").withStyle(s->s.withColor(ageFilter==7?AGE_COLORS[7]:0xffffff)));turretButton.setMessage(Component.literal("Turrets").withStyle(s->s.withColor(ageFilter==8?AGE_COLORS[8]:0xffffff)));suppliesButton.setMessage(Component.literal("Supplies").withStyle(s->s.withColor(ageFilter==9?AGE_COLORS[9]:0xffffff)));}
        if(typeButton!=null){typeButton.visible=!upgrades;typeButton.active=data.getBoolean("typeFilterAvailable")&&!navigating;typeButton.setMessage(Component.literal(data.getBoolean("typeFilterAvailable")?"Type: "+(armor()?ArmorPlatform.label(weaponType):WeaponBrowser.label(weaponType)):"Weapon types apply to guns"));}
        var recipes=shown();int start=upgrades?workshopPage*layout.capacity():0,total=upgrades?recipes.size():data.getInt("total"),page=upgrades?workshopPage:data.getInt("page");
        for(int i=0;i<rows.size();i++){
            Button row=rows.get(i);row.visible=start+i<recipes.size();row.active=row.visible;
            if(!row.visible)continue;
            row.setMessage(Component.empty());
        }
        previous.active=page>0&&(upgrades||!navigating);next.active=page<lastPage()&&(upgrades||!navigating);
        if(!pageJump.isFocused())pageJump.setValue(Integer.toString((dragging?dragPage:page)+1));
        text(g,font.plainSubstrByWidth(total==0?"0 matches":(page*layout.capacity()+1)+"-"+Math.min(total,(page+1)*layout.capacity())+" / "+total,lw-136),left+136,top+ph-28,MUTED);
        int sx=left+lw+6,sy=top+layout.listTop(),sh=ph-39-layout.listTop();g.fill(sx,sy,sx+8,sy+sh,0xff203541);
        int thumb=Math.min(sh,Math.max(12,sh/Math.max(1,lastPage()+1))),thumbY=sy+(lastPage()==0?0:(int)((sh-thumb)*(dragging?dragPage:page)/(double)lastPage()));g.fill(sx+1,thumbY,sx+7,thumbY+thumb,CYAN);
        craft.visible=!upgrades;upgrade.visible=upgrades;upgrade.active=false;
        CompoundTag chosen=chosen();int targetAge=chosen==null?0:chosen.getInt("age");boolean buying=upgrades&&targetAge==0;
        upgrade.setMessage(Component.literal(buying?"Buy station":"Upgrade Age"));upgrade.active=upgrades&&weapons()&&chosen!=null&&(buying||targetAge<5&&(targetAge!=2||data.getBoolean("netherVisited")));
        craft.active=!navigating&&chosen!=null&&chosen.getBoolean("unlocked")&&!chosen.getBoolean("invalid");
        if(coins!=null){coins.visible=!upgrades;coins.active=craft.active&&(data.getBoolean("creative")||chosen.getInt("coinCost")<=data.getInt("ammoCoins"));coins.setMessage(Component.literal(data.getBoolean("creative")?"Buy: free":chosen==null?"Buy: coins":"Buy: "+chosen.getInt("coinCost")+" coins"));}
        craft.setMessage(Component.literal(chosen==null?"Craft selected":data.getBoolean("creative")?"Craft: free":data.getString("kind").equals("ammo")?"Craft: materials":"Craft "+Math.max(1,chosen.getInt("outputCount"))+" item(s)"));
        ListTag costs=chosen==null?new ListTag():chosen.getList("costs",Tag.TAG_COMPOUND);
        String heading=upgrades?!weapons()?"Manage at weapon table":buying?"New Frontier station":targetAge==5?"Maximum Age":"Next: "+WeaponPlatform.AGES[Math.min(5,targetAge+1)]:chosen==null?data.getString("notice").isEmpty()?"No matching recipes":data.getString("notice"):ItemStack.of(chosen.getCompound("output")).getHoverName().getString();
        boolean compact=ph<280;int costTop=compact?126:144;
        g.enableScissor(right,top+95,right+rw,top+(compact?108:123));if(compact)text(g,font.plainSubstrByWidth(heading,rw),right,top+96,CYAN);else wrap(g,heading,right,top+96,rw,CYAN);g.disableScissor();
        text(g,font.plainSubstrByWidth(upgrades?(chosen!=null&&!chosen.getString("location").isEmpty()?chosen.getString("location"):"Place stations within 8 blocks"):data.getBoolean("creative")?"Creative: free purchases":"Materials (inventory allocation)",rw),right,top+(compact?112:126),MUTED);
        boolean locked=!upgrades&&chosen!=null&&!chosen.getBoolean("unlocked");
        if(locked){if(compact)text(g,font.plainSubstrByWidth("How to unlock : "+chosen.getString("requirement"),rw),right,top+ph-76,0xffed997f);else{int y=top+ph-97;for(var line:font.split(Component.literal("How to unlock : "+chosen.getString("requirement")),rw)){if(y>=top+ph-56)break;g.drawString(font,line,right,y,0xffed997f,false);y+=11;}}}
        int visible=Math.max(1,compact?(ph-79-costTop)/22:(ph-(locked?253:237))/22);costScroll=Math.min(costScroll,Math.max(0,costs.size()-visible));
        for(int i=costScroll;i<Math.min(costs.size(),costScroll+visible);i++){
            var cost=costs.getCompound(i);ItemStack item=ItemStack.of(cost.getCompound("item"));int y=top+costTop+(i-costScroll)*22;
            g.renderItem(item,right,y);hoverItems.add(new BeaconClient.Hover(item,right,y));
            text(g,font.plainSubstrByWidth(cost.getString("label").isEmpty()?item.getHoverName().getString():cost.getString("label"),rw-23),right+22,y,cost.getInt("have")>=cost.getInt("count")?INK:0xffed997f);
            text(g,cost.getInt("have")+" / "+cost.getInt("count"),right+22,y+10,MUTED);
        }
        if(!upgrades&&chosen!=null&&chosen.getBoolean("magazine"))text(g,font.plainSubstrByWidth("Empty / "+chosen.getInt("capacity")+" rounds — fill with loose ammo",rw),right,top+ph-(compact?74:83),MUTED);
        if(upgrades&&targetAge==2)text(g,!data.getBoolean("netherVisited")?"Team must visit the Nether":costs.size()>visible?"Nether required / scroll":"Nether materials required",right,top+ph-70,0xffed997f);
        else if(costs.size()>visible&&!locked&&(!compact||chosen==null||!chosen.getBoolean("magazine")))text(g,"Scroll for "+costs.size()+" ingredients",right,top+ph-70,MUTED);
        if(ArmorPlatform.component(data.getString("kind"))&&!upgrades)text(g,font.plainSubstrByWidth(data.getInt("locked")+" items need an Age / Create milestone",rw),right,top+ph-33,MUTED);
        if(ArmorPlatform.component(data.getString("kind"))&&chosen==null)wrap(g,"Hold a gun and press ["+ControlHints.interact()+"] at this table. Upgrade it at the weapon table.",left+14,top+149,lw,MUTED);
        super.render(g,mx,my,partial);
        // A colored border complements the selected special category label.
        if(weapons()&&ageFilter>=7){Button b=ageFilter==7?armoryButton:ageFilter==8?turretButton:suppliesButton;int c=0xff000000|AGE_COLORS[ageFilter];g.fill(b.getX(),b.getY(),b.getX()+b.getWidth(),b.getY()+1,c);g.fill(b.getX(),b.getY()+b.getHeight()-1,b.getX()+b.getWidth(),b.getY()+b.getHeight(),c);}
        // Icons drawn after buttons remain visible, but their hitboxes belong to the JEI handler.
        for(int i=0;i<rows.size();i++)if(rows.get(i).visible){Button b=rows.get(i);var recipe=recipes.getCompound(start+i);var output=ItemStack.of(recipe.getCompound("output"));int x=b.getX()+3,y=b.getY()+3;g.renderItem(output,x,y);hoverItems.add(new BeaconClient.Hover(output,x,y));int era=Math.max(0,Math.min(5,recipe.getInt("age")));String label=upgrades?recipe.getString("label")+(era>0?" / "+WeaponPlatform.AGES[era]:""):output.getHoverName().getString();String prefix=(i==selected?"> ":"")+(!upgrades&&!recipe.getBoolean("unlocked")?"[Locked] ":"");int color=AGE_COLORS[recipe.getBoolean("special")?Math.max(7,ageFilter):era];text(g,font.plainSubstrByWidth(prefix+label,b.getWidth()-27),b.getX()+24,b.getY()+7,0xff000000|color);if(i==selected){g.fill(b.getX(),b.getY(),b.getX()+b.getWidth(),b.getY()+1,CYAN);g.fill(b.getX(),b.getY()+b.getHeight()-1,b.getX()+b.getWidth(),b.getY()+b.getHeight(),CYAN);}}
        if(!message.isEmpty())text(g,font.plainSubstrByWidth(message,pw-28),left+14,top+ph-12,CYAN);
        var hover=itemAt(mx,my);if(hover!=null)g.renderComponentTooltip(font,List.of(hover.item().getHoverName(),Component.literal(ControlHints.jei())),mx,my);
        else if(pageJump.isMouseOver(mx,my)||overScrollbar(mx,my))g.renderComponentTooltip(font,List.of(Component.literal("Page "+(dragging?dragPage+1:page+1)+" / "+(lastPage()+1)),Component.literal("Scroll the list or use Page Up / Page Down."),Component.literal("Home / End: first / last. Type a page and press Enter.")),mx,my);
        else if(!upgrades)for(int i=0;i<rows.size();i++){Button row=rows.get(i);if(row.visible&&mx>=row.getX()&&mx<row.getX()+row.getWidth()&&my>=row.getY()&&my<row.getY()+row.getHeight()){
            var recipe=recipes.getCompound(i);int era=Math.max(1,Math.min(5,recipe.getInt("age")));g.renderComponentTooltip(font,List.of(ItemStack.of(recipe.getCompound("output")).getHoverName(),Component.literal(recipe.getString("progression")).withStyle(style->style.withColor(AGE_COLORS[era]))),mx,my);break;
        }}
    }
    @Override BeaconClient.Hover itemAt(double x,double y){for(var item:hoverItems)if(x>=item.x()&&x<item.x()+16&&y>=item.y()&&y<item.y()+16)return item;return null;}
}
