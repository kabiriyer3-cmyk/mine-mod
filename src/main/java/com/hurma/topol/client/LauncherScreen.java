package com.hurma.topol.client;

import com.hurma.topol.Topol;
import com.hurma.topol.entity.LauncherEntity;
import com.hurma.topol.net.Packets;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Responsive original instrument-panel GUI. The second click carries a short-lived server nonce. */
public final class LauncherScreen extends Screen {
    private static final int W=350,H=280;
    private static final Identifier PANEL=Topol.id("textures/gui/console.png");
    private final LauncherEntity launcher;
    private final int minRange,maxRange;
    private TextFieldWidget x,z;
    private UiButton deploy,stow,arm,payload;
    private int left,top,result;
    private float scale;
    private boolean destructive;
    private long token,until;
    public LauncherScreen(LauncherEntity launcher,Packets.Console p) {
        super(Text.translatable("screen.topol.title"));
        this.launcher=launcher;minRange=p.minRange();maxRange=p.maxRange();destructive=launcher.destructive();
    }
    public int entity(){return launcher.getId();}
    public void response(Packets.Console p) {
        result=p.result();token=p.token();until=client.world==null?0:client.world.getTime()+100;
        SoundFx.ui(result==1||result==8?"ui_confirm":result==0||result==13?"ui_beep":"ui_error");
        updateButtons();
    }
    @Override protected void init() {
        scale=Math.min(1,Math.min((width-12)/(float)W,(height-12)/(float)H));
        left=(int)((width/scale-W)/2);top=(int)((height/scale-H)/2);
        String oldX=x==null?Integer.toString(launcher.targetX()):x.getText();
        String oldZ=z==null?Integer.toString(launcher.targetZ()):z.getText();
        x=new TextFieldWidget(textRenderer,left+23,top+87,137,18,Text.literal("X"));
        z=new TextFieldWidget(textRenderer,left+190,top+87,137,18,Text.literal("Z"));
        for(TextFieldWidget field:new TextFieldWidget[]{x,z}) {
            field.setMaxLength(9);field.setDrawsBackground(false);
            field.setTextPredicate(s->s.matches("-?[0-9]{0,8}"));addDrawableChild(field);
        }
        x.setText(oldX);z.setText(oldZ);
        payload=addDrawableChild(new UiButton(left+16,top+142,318,23,"",()->{
            if(token==0){destructive=!destructive;updateButtons();}
        }));
        deploy=addDrawableChild(new UiButton(left+16,top+175,150,23,"screen.topol.deploy",()->send(0)));
        stow=addDrawableChild(new UiButton(left+184,top+175,150,23,"screen.topol.stow",()->send(token==0?1:4)));
        arm=addDrawableChild(new UiButton(left+16,top+207,318,25,"screen.topol.arm",()->send(token==0?2:3)));
        addDrawableChild(new UiButton(left+W-29,top+11,16,16,"×",this::close));
        updateButtons();
    }
    private int coordinate(TextFieldWidget f){try{return Integer.parseInt(f.getText());}catch(NumberFormatException e){return Integer.MIN_VALUE;}}
    private double range() {
        int xx=coordinate(x),zz=coordinate(z);
        return Math.hypot(xx+.5-launcher.launchBase().x,zz+.5-launcher.launchBase().z);
    }
    private void send(int action) {
        int xx=coordinate(x),zz=coordinate(z);
        if((action==2||action==3)&&(xx==Integer.MIN_VALUE||zz==Integer.MIN_VALUE)){result=4;SoundFx.ui("ui_error");return;}
        ClientPlayNetworking.send(new Packets.Control(launcher.getId(),action,xx,zz,destructive?1:0,token));
        if(action==3||action==4){token=0;result=8;updateButtons();}
    }
    private void updateButtons() {
        if(arm==null)return;
        int s=launcher.state();
        deploy.active=s==LauncherEntity.STOWED&&token==0;
        stow.active=s==LauncherEntity.READY||s==LauncherEntity.EMPTY||token!=0;
        stow.setMessage(Text.translatable(token==0?"screen.topol.stow":"screen.topol.cancel"));
        arm.active=s==LauncherEntity.READY&&launcher.loaded();
        arm.setMessage(Text.translatable(token==0?"screen.topol.arm":"screen.topol.fire"));
        arm.danger=token!=0;
        payload.setMessage(Text.translatable(destructive?"screen.topol.blast":"screen.topol.inert"));
        payload.active=token==0;payload.danger=destructive;
        x.setEditable(token==0);z.setEditable(token==0);
    }
    @Override public void tick() {
        if(client==null||client.world==null||launcher.isRemoved()){close();return;}
        if(token!=0&&client.world.getTime()>until){token=0;result=5;}
        updateButtons();
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close() {
        if(token!=0&&client!=null&&client.world!=null)send(4);
        super.close();
    }
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta) {
        c.fill(0,0,width,height,0xa30d140f);
        c.getMatrices().push();c.getMatrices().scale(scale,scale,1);
        RenderSystem.enableBlend();
        c.drawTexture(PANEL,left,top,W,H,0f,0f,1200,880,1200,880);
        c.drawText(textRenderer,title,left+17,top+19,0xe2edc6,false);
        int state=launcher.state();
        c.drawText(textRenderer,Text.translatable("state.topol."+state),left+17,top+35,state==3?0xffbc75:0xa9d4a1,false);
        c.drawText(textRenderer,Text.translatable(launcher.loaded()?"screen.topol.loaded":"screen.topol.empty"),left+17,top+49,0x839d7e,false);
        if(state==LauncherEntity.DEPLOYING||state==LauncherEntity.STOWING) {
            c.fill(left+16,top+62,left+334,top+64,0xff243c2a);
            c.fill(left+16,top+62,left+16+(int)(318*launcher.deployment(delta)),top+64,0xff9dbb77);
        }
        c.drawText(textRenderer,"X",left+17,top+73,0xa5b98c,false);c.drawText(textRenderer,"Z",left+184,top+73,0xa5b98c,false);
        c.fill(left+16,top+84,left+166,top+107,0xff0c160e);c.fill(left+184,top+84,left+334,top+107,0xff0c160e);
        c.fill(left+16,top+106,left+166,top+107,0xff6d8a59);c.fill(left+184,top+106,left+334,top+107,0xff6d8a59);
        double range=range();boolean allowed=range>=minRange&&range<=maxRange;
        c.drawText(textRenderer,Text.translatable("screen.topol.range",Long.toString(Math.round(range))),left+17,top+114,allowed?0xcad8b2:0xe2a579,false);
        c.drawText(textRenderer,Text.translatable("screen.topol.limits",minRange,maxRange),left+17,top+126,0x889a7d,false);
        super.render(c,(int)(mouseX/scale),(int)(mouseY/scale),delta);
        Text status=token!=0&&destructive?Text.translatable("screen.topol.confirm"):Text.translatable("message.topol."+result);
        int yy=top+243;
        for(var line:textRenderer.wrapLines(status,318)) {
            if(yy>top+264)break;
            c.drawText(textRenderer,line,left+17,yy,token!=0?0xffb56f:0xafbda0,false);yy+=10;
        }
        c.getMatrices().pop();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){return super.mouseClicked(mx/scale,my/scale,button);}
    @Override public boolean mouseReleased(double mx,double my,int button){return super.mouseReleased(mx/scale,my/scale,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return super.mouseDragged(mx/scale,my/scale,button,dx/scale,dy/scale);}
    @Override public void mouseMoved(double mx,double my){super.mouseMoved(mx/scale,my/scale);}

    private static final class UiButton extends PressableWidget {
        final Runnable action;
        boolean danger;
        UiButton(int x,int y,int w,int h,String key,Runnable action) {
            super(x,y,w,h,key.equals("×")?Text.literal(key):Text.translatable(key));this.action=action;
        }
        @Override public void onPress(){action.run();}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta) {
            var font=net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            int bg=!active?0xff222a20:danger?isHovered()?0xff7f492c:0xff5e3526:isHovered()?0xff4d6240:0xff34452c;
            int edge=!active?0xff394334:danger?0xffba7c4b:0xff748961;
            c.fill(getX(),getY(),getX()+width,getY()+height,bg);
            c.fill(getX(),getY(),getX()+width,getY()+1,edge);
            c.fill(getX(),getY()+height-1,getX()+width,getY()+height,0xff11190e);
            if(isFocused())c.fill(getX(),getY(),getX()+2,getY()+height,0xffd8e1be);
            c.drawCenteredTextWithShadow(font,getMessage(),getX()+width/2,getY()+(height-8)/2,active?0xe3e8cc:0x67715f);
        }
        @Override protected void appendClickableNarrations(NarrationMessageBuilder b){appendDefaultNarrations(b);}
        @Override public void playDownSound(SoundManager manager){SoundFx.ui("ui_beep");}
    }
}
