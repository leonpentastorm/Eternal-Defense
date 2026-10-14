package dev.eternaldefense.beacon;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import java.util.ArrayList;
import java.util.List;

/** The closet display of the Loadout screens: the local player's own figure wearing a kit, lit on a turning stand. */
final class KitMannequin {
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};
    private static boolean drawing;
    private RemotePlayer figure;private List<ItemStack> worn=List.of();
    private float turned;private double dragX=Double.NaN;

    /** Dresses the figure in a kit (kit order: feet, legs, chest, head, off hand, hotbar); an empty list leaves it bare. */
    void wear(List<ItemStack> kit){
        if(figure==null){var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;figure=new RemotePlayer(mc.level,mc.player.getGameProfile());}
        if(same(kit,worn))return;
        var copy=new ArrayList<ItemStack>();for(var s:kit)copy.add(s.copy());worn=copy;
        for(int i=0;i<ARMOR.length;i++)figure.setItemSlot(ARMOR[i],stack(i));
        figure.setItemSlot(EquipmentSlot.OFFHAND,stack(LoadoutRules.OFFHAND_SLOT));
        figure.setItemSlot(EquipmentSlot.MAINHAND,stack(LoadoutRules.HOTBAR_FIRST));
    }
    private ItemStack stack(int i){return i<worn.size()?worn.get(i).copy():ItemStack.EMPTY;}
    private static boolean same(List<ItemStack> a,List<ItemStack> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!ItemStack.matches(a.get(i),b.get(i)))return false;return true;}

    // Turning: slowly by itself, or by dragging across the display
    boolean press(double mx,double my,int x,int y,int w,int h){if(mx<x||mx>=x+w||my<y||my>=y+h)return false;dragX=mx;return true;}
    boolean drag(double mx){if(Double.isNaN(dragX))return false;turned+=(float)(mx-dragX)*2f;dragX=mx;return true;}
    void release(){dragX=Double.NaN;}
    boolean dragging(){return !Double.isNaN(dragX);}

    /** The display at (x, y), w by h: back wall, spotlight, stand and the figure. */
    void render(GuiGraphics g,int x,int y,int w,int h,int scale){
        int cx=x+w/2,floor=y+h-16;
        g.fill(x,y,x+w,y+h,Ui.EDGE);g.fillGradient(x+1,y+1,x+w-1,y+h-1,0xff16252f,0xff060b0f);
        // The closet rail and the lamp over the stand
        g.fill(x+4,y+5,x+w-4,y+7,Ui.BRASS_DK);g.fill(x+4,y+5,x+w-4,y+6,Ui.BRASS);
        g.fill(cx-7,y+7,cx+7,y+10,Ui.SLATE_HI);g.fill(cx-5,y+10,cx+5,y+11,0xffbff6ff);
        // The spotlight: a soft cone widening toward the stand
        int cone=floor-(y+11);
        for(int r=0;r<cone;r++){float f=r/(float)cone;int half=5+(int)(f*(w/2f-12));int a=(int)(46*(1f-f*.7f));g.fill(cx-half,y+11+r,cx+half,y+12+r,(a<<24)|0x9fe8ff);}
        // The stand: a brass-rimmed disc
        for(int r=-5;r<=5;r++){int half=(int)((w*.36)*Math.sqrt(1-(r/5.5)*(r/5.5)));g.fill(cx-half,floor+r,cx+half,floor+r+1,r<0?0xff2a4555:r==0?Ui.BRASS:0xff111e28);}
        if(figure==null)return;
        float angle=turned+(dragging()?0f:(Util.getMillis()%24000L)*.015f);
        figure.yBodyRot=180f+angle;figure.yBodyRotO=figure.yBodyRot;figure.setYRot(180f+angle);figure.yRotO=figure.getYRot();
        figure.yHeadRot=figure.getYRot();figure.yHeadRotO=figure.yHeadRot;figure.setXRot(-4f);figure.xRotO=figure.getXRot();
        var camera=new Quaternionf().rotateX((float)Math.toRadians(-6));var pose=new Quaternionf().rotateZ((float)Math.PI).mul(camera);
        g.enableScissor(x+1,y+1,x+w-1,y+h-1);
        drawing=true;
        try{InventoryScreen.renderEntityInInventory(g,cx,floor-1,scale,pose,camera,figure);}finally{drawing=false;}
        g.disableScissor();
    }

    @Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
    public static final class NoName {
        // The figure in the closet carries no name tag
        @SubscribeEvent public static void closetName(RenderNameTagEvent e){if(drawing)e.setResult(Event.Result.DENY);}
    }
}
