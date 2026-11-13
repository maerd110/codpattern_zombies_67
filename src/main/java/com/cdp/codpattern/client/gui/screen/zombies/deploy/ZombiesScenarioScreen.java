package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.app.zombies.scenario.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.*;

/** Card browser with separate, bounded detail forms. Navigation never replaces the working copy. */
public final class ZombiesScenarioScreen extends Screen {
    private static ZombiesScenarioScreen active;
    private final Screen parent;
    private final String map;
    private JsonObject data;
    private String saved="",revision="",message="";
    private boolean editable,suspended;
    private UUID pending;
    private long deadline,lastClick;
    private JsonObject lastCard,captureTarget,selectionRegion;
    private boolean selectingSource,selectingReward;
    private int page,left,formWidth,bottom;
    private GuiGraphics graphics;
    private List<String> ruleIds=List.of(),mobIds=List.of("minecraft:zombie");
    private final Deque<Level> navigation=new ArrayDeque<>();
    private final List<Consumer<Integer>> rows=new ArrayList<>();
    private final List<Runnable> labels=new ArrayList<>();
    private final List<CardBox> cards=new ArrayList<>();
    private record CardBox(int index,int x,int y,int w,int h) { }
    private static final class Level {
        final String kind,title;
        final JsonArray entries;
        int selected=-1,scroll,detailScroll;
        boolean detail;
        Level(String kind,String title,JsonArray entries){this.kind=kind;this.title=title;this.entries=entries;}
        JsonObject selected(){return selected<0||selected>=entries.size()?null:entries.get(selected).getAsJsonObject();}
    }
    public ZombiesScenarioScreen(Screen parent,String map){super(Component.literal("关卡波次与剧情"));this.parent=parent;this.map=map;active=this;}
    public static void disconnected(){active=null;}
    public static boolean reopenIfSuspended(String map){if(active==null||!active.suspended||!active.map.equals(map))return false;active.suspended=false;active.selectionRegion=null;net.minecraft.client.Minecraft.getInstance().setScreen(active);return true;}
    public void load(){request("load","");}
    private boolean canEdit(){return editable&&pending==null&&data!=null;}
    private boolean dirty(){return data!=null&&!data.toString().equals(saved);}
    private JsonArray list(String key){return data.getAsJsonArray(key);}
    private static String str(JsonObject o,String k,String def){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():def;}
    private static JsonObject position(BlockPos p){var o=new JsonObject();o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());return o;}
    private static BlockPos pos(JsonObject o){return ZombiesNpcRepository.GSON.fromJson(o,BlockPos.class);}
    private BlockPos playerPos(){return minecraft.player==null?BlockPos.ZERO:minecraft.player.blockPosition();}
    private BlockPos targetPos(){return minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit?hit.getBlockPos():playerPos();}
    private Level level(){return navigation.peekLast();}
    private void root(){navigation.clear();if(data!=null){String[] keys={"waves","speakers","dialogues","events","tasks","areaRewards"};String[] titles={"波次怪物","出场人物","波前对话","地图事件","任务书","区域道具"};navigation.add(new Level(keys[page],titles[page],list(keys[page])));}lastCard=null;}
    private void request(String op,String body){
        try{ZombiesNpcEditorWire.checkRequest(body);pending=UUID.randomUUID();deadline=System.currentTimeMillis()+20000;ZombiesNpcDialogueNetwork.edit(new ZombiesNpcDialogueNetwork.EditorRequest(pending,map,op,body));message=op.equals("capture")?"正在读取源场景…":"正在读取／保存…";}
        catch(RuntimeException e){pending=null;captureTarget=null;message=detail(e);}
        refresh();
    }
    public static void accept(ZombiesNpcDialogueNetwork.EditorReply reply){
        if(active==null||!reply.token().equals(active.pending))return;var s=active;s.pending=null;
        try{
            var r=ZombiesNpcRepository.GSON.fromJson(reply.payload(),ZombiesScenarioEditorService.Snapshot.class);if(!s.map.equals(r.map()))return;
            if(!r.error().isBlank()){s.captureTarget=null;s.message=r.error();s.refresh();return;}
            if(r.scene()!=null){
                if(s.captureTarget==null)return;
                var region=s.captureTarget;s.captureTarget=null;
                region.add("scene",ZombiesNpcRepository.GSON.toJsonTree(r.scene()));region.addProperty("mode","SCENE");syncSceneExtent(region);
                s.message="场景已读取到工作副本："+r.scene().sizeX()+" × "+r.scene().sizeY()+" × "+r.scene().sizeZ()+"；请保存配置。";
            }else{
                s.data=ZombiesNpcRepository.GSON.toJsonTree(r.config()).getAsJsonObject();s.saved=s.data.toString();s.revision=r.revision();s.ruleIds=r.rules();s.mobIds=r.mobs();s.editable=r.editable();s.root();s.message="配置已读取／保存。";
            }
            s.refresh();
        }catch(RuntimeException e){s.captureTarget=null;s.message=detail(e);s.refresh();}
    }
    private static String detail(RuntimeException e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private Button button(String text,int x,int y,int w,Runnable action,boolean enabled){
        var b=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(text,Math.max(8,w-8))),v->{try{action.run();}catch(RuntimeException e){message=detail(e);}}).bounds(x,y,w,20).build());b.active=enabled;b.setTooltip(Tooltip.create(Component.literal(text)));return b;
    }
    @Override protected void init(){
        rows.clear();labels.clear();cards.clear();left=12;formWidth=width-24;bottom=height-42;
        if(data!=null&&navigation.isEmpty())root();
        button("收起查看地图",width-122,3,110,()->{suspended=true;minecraft.setScreen(null);},pending==null&&minecraft.player!=null);
        int w=(width-34)/6;String[] names={"波次怪物","出场人物","波前对话","地图事件","任务书","区域道具"};
        for(int i=0;i<6;i++){int j=i;button((page==i?"● ":"")+names[i],12+i*(w+2),27,w,()->{page=j;root();message="";refresh();},pending==null);}
        int bw=(width-28)/3;
        button("保存配置",12,height-27,bw,this::save,canEdit());
        button("重新读取",14+bw,height-27,bw,()->{if(dirty())minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)load();},Component.literal("放弃未保存的修改？"),Component.empty()));else load();},pending==null);
        button("返回部署工具",16+bw*2,height-27,bw,this::exit,pending==null);
        if(data==null)return;
        var l=level();
        button(l.detail||navigation.size()>1?"‹ 返回上级":"条目列表",12,54,Math.min(100,(width-30)/3),this::back,pending==null&&(l.detail||navigation.size()>1));
        int x=16+Math.min(100,(width-30)/3),remaining=width-x-12,cell=(remaining-4)/2;
        if(!l.detail){
            button("新增"+kindLabel(l.kind),x,54,cell,this::add,canEdit());
            button("编辑所选",x+cell+4,54,cell,this::open,pending==null&&l.selected()!=null);
            buildCards(l);
        }else{
            button("删除此条目",x,54,cell,this::deleteSelected,canEdit());
            button(l.title,x+cell+4,54,cell,()->{},false);
            left=Math.max(12,(width-620)/2);formWidth=Math.min(width-24,620);
            buildDetail(l,l.selected());
            int count=Math.max(1,(bottom-86)/26);l.detailScroll=Math.max(0,Math.min(l.detailScroll,Math.max(0,rows.size()-count)));
            for(int i=l.detailScroll;i<Math.min(rows.size(),l.detailScroll+count);i++)rows.get(i).accept(82+(i-l.detailScroll)*26);
        }
    }
    private static String kindLabel(String kind){return switch(kind){case "waves"->"波次";case "speakers"->"出场人物";case "dialogues"->"波前对话";case "events"->"地图事件";case "tasks"->"任务指引";case "areaRewards"->"区域道具";case "rewardItems"->"奖励道具";case "mobs"->"怪物";case "lines"->"台词";default->"区域变化";};}
    private int accent(String kind){return switch(kind){case "waves","mobs"->0xFF72B8E0;case "speakers"->0xFFE9BD70;case "dialogues","lines"->0xFF82D4B3;default->0xFFBAA1E5;};}
    private void buildCards(Level l){
        int cols=Math.max(1,Math.min(4,(width-24)/230)),gap=10,cw=(width-24-gap*(cols-1))/cols,ch=66;
        int visible=Math.max(1,(bottom-84)/(ch+gap)),total=(l.entries.size()+cols-1)/cols;l.scroll=Math.max(0,Math.min(l.scroll,Math.max(0,total-visible)));
        for(int i=l.scroll*cols;i<Math.min(l.entries.size(),(l.scroll+visible)*cols);i++){
            int x=12+(i%cols)*(cw+gap),y=82+(i/cols-l.scroll)*(ch+gap);cards.add(new CardBox(i,x,y,cw,ch));addRenderableWidget(new CardButton(l,i,x,y,cw,ch));
        }
    }
    private final class CardButton extends Button {
        private final Level owner;private final int index;
        CardButton(Level owner,int index,int x,int y,int w,int h){super(x,y,w,h,Component.literal(cardTitle(owner,index)),b->selectCard(owner,index),DEFAULT_NARRATION);this.owner=owner;this.index=index;active=pending==null;setTooltip(Tooltip.create(Component.literal(cardTitle(owner,index)+"\n"+summary(owner,index))));}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
            int x=getX(),y=getY(),w=getWidth(),h=getHeight(),color=accent(owner.kind);boolean chosen=owner.selected==index;
            g.fill(x,y,x+w,y+h,chosen?color:isHoveredOrFocused()?0xFF58758C:0xFF304758);
            g.fill(x+1,y+1,x+w-1,y+h-1,chosen?0xFF203848:isHoveredOrFocused()?0xFF1D2F3F:0xFF142330);
            g.fill(x+1,y+1,x+4,y+h-1,color);
            g.drawString(font,font.plainSubstrByWidth(cardTitle(owner,index),w-26),x+13,y+10,0xFFE4EFF3,false);
            var lines=font.split(Component.literal(summary(owner,index)),w-26);for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),x+13,y+29+i*12,0xFF9EB5C5,false);
            if(chosen)g.fill(x+w-11,y+10,x+w-7,y+14,color);
        }
    }
    private String cardTitle(Level l,int i){var o=l.entries.get(i).getAsJsonObject();return switch(l.kind){case "waves","dialogues"->"第 "+str(o,"wave","?")+" 波";case "speakers"->str(o,"name","未命名人物");case "events","tasks","areaRewards"->str(o,"name","未命名条目");case "rewardItems"->"道具 "+(i+1)+" · "+str(o,"item","");case "mobs"->entityName(str(o,"entity","minecraft:zombie"));case "lines"->"台词 "+(i+1)+" · "+speakerName(str(o,"speaker",""));default->"区域 "+(i+1)+" · "+(str(o,"mode",o.has("scene")?"SCENE":"FILL").equals("SCENE")?"场景快照":"方块填充");};}
    private String summary(Level l,int i){var o=l.entries.get(i).getAsJsonObject();return switch(l.kind){
        case "waves"->o.getAsJsonArray("mobs").size()+" 种怪物 · 存活上限 "+str(o,"maxAlive","默认");
        case "speakers"->str(o,"id","")+" · 名字 "+str(o,"nameColor","white")+" / 正文 "+str(o,"textColor","white");
        case "dialogues"->o.getAsJsonArray("lines").size()+" 句台词 · 开播等待 "+str(o,"delaySeconds","0")+" 秒";
        case "events"->(Boolean.parseBoolean(str(o,"automatic","true"))?"自动 · ≥ "+str(o,"minWave","0")+" 波":"由 NPC 动作触发")+" · "+o.getAsJsonArray("regions").size()+" 处变化";
        case "tasks"->"≥ "+str(o,"minWave","0")+" 波 · "+str(o,"text","");
        case "areaRewards"->o.getAsJsonArray("items").size()+" 种道具 · 每名玩家每局一次";
        case "rewardItems"->"数量 "+str(o,"count","1")+" · 保留物品数据";
        case "mobs"->"数量 "+str(o,"count","1")+" · 生命倍率 "+str(o,"healthMultiplier","默认")+" · 击杀积分 "+str(o,"killPoints","默认");
        case "lines"->str(o,"text","").replace('\n',' ')+" · "+str(o,"seconds","1")+" 秒";
        default->str(o,"mode",o.has("scene")?"SCENE":"FILL").equals("SCENE")?(o.has("scene")&&!o.get("scene").isJsonNull()?sceneSummary(o):"尚未读取源场景"):str(o,"block","minecraft:stone");
    };}
    private String sceneSummary(JsonObject region){var s=region.getAsJsonObject("scene");return str(s,"sizeX","?")+" × "+str(s,"sizeY","?")+" × "+str(s,"sizeZ","?")+" · "+s.getAsJsonArray("palette").size()+" 种方块状态 · "+(s.has("blockEntities")?s.getAsJsonArray("blockEntities").size():0)+" 个方块数据 · "+(s.has("entities")?s.getAsJsonArray("entities").size():0)+" 个实体";}
    private String entityName(String id){var location=net.minecraft.resources.ResourceLocation.tryParse(id);if(location==null)return id;return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(location).getDescription().getString();}
    private String speakerName(String id){if(id.isBlank())return "旁白";return list("speakers").asList().stream().map(JsonElement::getAsJsonObject).filter(o->str(o,"id","").equals(id)).map(o->str(o,"name",id)).findFirst().orElse(id);}
    private void selectCard(Level l,int i){long now=System.currentTimeMillis();var object=l.entries.get(i).getAsJsonObject();boolean twice=lastCard==object&&now-lastClick<350;lastCard=object;lastClick=now;l.selected=i;if(twice)open();else refresh();}
    private void open(){var l=level();if(l==null||l.selected()==null)return;l.detail=true;l.detailScroll=0;lastCard=null;refresh();}
    private void back(){var l=level();if(l==null)return;if(l.detail)l.detail=false;else if(navigation.size()>1)navigation.removeLast();lastCard=null;message="";refresh();}
    private void browse(String kind,String title,JsonArray entries){navigation.addLast(new Level(kind,title,entries));lastCard=null;refresh();}
    private void deleteSelected(){var l=level();if(!canEdit()||l.selected()==null)return;String name=cardTitle(l,l.selected);minecraft.setScreen(new ConfirmScreen(ok->{if(ok){l.entries.remove(l.selected);l.selected=-1;l.detail=false;message="条目已从工作副本删除。";}minecraft.setScreen(this);},Component.literal("删除“"+name+"”？"),Component.literal("保存配置后生效")));}
    private void add(){
        var l=level();var o=new JsonObject();
        switch(l.kind){
            case "waves"->{int n=l.entries.asList().stream().mapToInt(e->e.getAsJsonObject().get("wave").getAsInt()).max().orElse(0)+1;o.addProperty("wave",n);o.addProperty("maxAlive",8);o.addProperty("fastestSpawnIntervalTicks",20);o.addProperty("slowestSpawnIntervalTicks",50);o.add("mobs",new JsonArray());}
            case "speakers"->{o.addProperty("id",unique(l.entries,"speaker_"));o.addProperty("name","新人物");o.addProperty("nameColor","white");o.addProperty("textColor","white");}
            case "dialogues"->{int n=1;Set<Integer> used=new HashSet<>();l.entries.forEach(e->used.add(e.getAsJsonObject().get("wave").getAsInt()));while(used.contains(n))n++;o.addProperty("wave",n);o.addProperty("delaySeconds",0);o.add("lines",new JsonArray());}
            case "events"->{o.addProperty("id",unique(l.entries,"event_"));o.addProperty("name","新事件");o.addProperty("automatic",true);o.addProperty("minWave",1);o.addProperty("rule","");o.addProperty("weather","CLEAR");o.add("regions",new JsonArray());}
            case "tasks"->{o.addProperty("id",unique(l.entries,"task_"));o.addProperty("name","新任务指引");o.addProperty("automatic",true);o.addProperty("minWave",1);o.addProperty("rule","");o.addProperty("text","前往目标区域");}
            case "areaRewards"->{o.addProperty("id",unique(l.entries,"area_"));o.addProperty("name","新区域道具");o.addProperty("minWave",0);o.addProperty("rule","");o.add("from",position(playerPos()));o.add("to",position(playerPos()));var items=new JsonArray();var item=new JsonObject();item.addProperty("item","minecraft:emerald");item.addProperty("count",1);items.add(item);o.add("items",items);}
            case "rewardItems"->{o.addProperty("item","minecraft:emerald");o.addProperty("count",1);}
            case "mobs"->{o.addProperty("entity","minecraft:zombie");o.addProperty("count",1);}
            case "lines"->{o.addProperty("speaker","");o.addProperty("text","新台词");o.addProperty("seconds",1);}
            case "regions"->{o.addProperty("mode","SCENE");o.addProperty("block","minecraft:stone");for(String key:List.of("from","to","sourceFrom","sourceTo"))o.add(key,position(playerPos()));}
        }
        l.entries.add(o);l.selected=l.entries.size()-1;l.detail=true;l.detailScroll=0;message="新增条目尚未保存。";refresh();
    }
    private void refresh(){if(minecraft!=null)rebuildWidgets();}
    private void heading(String text){rows.add(y->labels.add(()->{graphics.fill(left,y,left+formWidth,y+21,0xFF203646);graphics.fill(left,y,left+3,y+21,accent(level().kind));graphics.drawString(font,font.plainSubstrByWidth(text,formWidth-18),left+9,y+6,0xFFE1EDF0,false);}));}
    private void hint(String text){for(var line:font.split(Component.literal(text),formWidth-10))rows.add(y->labels.add(()->graphics.drawString(font,line,left+5,y+6,0xFF91A9BA,false)));}
    private void row(String text,Runnable click){rows.add(y->button(text,left,y,formWidth,click,canEdit()));}
    private void pair(String a,Runnable aa,String b,Runnable bb){rows.add(y->{button(a,left,y,formWidth/2-2,aa,canEdit());button(b,left+formWidth/2,y,formWidth/2,bb,canEdit());});}
    private void field(String caption,JsonObject o,String key,String def){field(caption,o,key,def,()->{});}
    private void field(String caption,JsonObject o,String key,String def,Runnable change){rows.add(y->{int label=Math.min(170,formWidth/3);labels.add(()->graphics.drawString(font,font.plainSubstrByWidth(caption,label-6),left,y+6,0xFFBFD0DA,false));var box=addRenderableWidget(new EditBox(font,left+label,y,formWidth-label,20,Component.literal(caption)));box.setMaxLength(8192);box.setValue(str(o,key,def));box.setEditable(canEdit());box.setResponder(v->{o.addProperty(key,v);message="";try{change.run();}catch(RuntimeException ignored){}});});}
    private void select(String caption,JsonObject o,String key,List<String> options,Function<String,String> label){String value=str(o,key,"");row(caption+"："+label.apply(value)+" ▾",()->minecraft.setScreen(new ZombiesNpcChoiceScreen(this,caption,options,value,label,v->{o.addProperty(key,v);refresh();})));}
    private void toggle(String caption,JsonObject o,String key,boolean def){row(caption+"："+(Boolean.parseBoolean(str(o,key,""+def))?"是":"否"),()->{o.addProperty(key,!Boolean.parseBoolean(str(o,key,""+def)));refresh();});}
    private void buildDetail(Level l,JsonObject o){if(o==null){l.detail=false;return;}heading(cardTitle(l,l.selected));
        switch(l.kind){
            case "waves"->{heading("波次参数");field("波次编号",o,"wave","1");toggle("首领入场音效",o,"bossIntro",false);for(String k:List.of("healthMultiplier","damageMultiplier","speedMultiplier","maxAlive","fastestSpawnIntervalTicks","slowestSpawnIntervalTicks"))field(waveLabel(k),o,k,"");row("管理怪物 · "+o.getAsJsonArray("mobs").size()+" 种",()->browse("mobs",cardTitle(l,l.selected)+" / 怪物",o.getAsJsonArray("mobs")));hint("可选倍率留空时继承关卡默认值，20 刻约为 1 秒。");}
            case "mobs"->{heading("种类与数量");select("怪物种类",o,"entity",mobIds,v->entityName(v)+" · "+v);field("数量",o,"count","1");heading("独立倍率与积分");for(String k:List.of("healthMultiplier","damageMultiplier","speedMultiplier","killPoints","assistPoints"))field(waveLabel(k),o,k,"");}
            case "speakers"->{heading("人物身份");hint("稳定编号："+str(o,"id",""));field("显示名字",o,"name","");heading("显示格式");field("名字颜色",o,"nameColor","white");field("正文颜色",o,"textColor","white");colorPicker(o,"nameColor");colorPicker(o,"textColor");toggle("粗体",o,"bold",false);toggle("斜体",o,"italic",false);}
            case "dialogues"->{heading("播放设置");field("播放波次",o,"wave","1");field("开播前等待（秒）",o,"delaySeconds","0");row("管理台词 · "+o.getAsJsonArray("lines").size()+" 句",()->browse("lines",cardTitle(l,l.selected)+" / 台词",o.getAsJsonArray("lines")));hint("台词按卡片顺序播放，最后一句停留结束后才刷怪。");row("在界面预览本波台词",()->{preview=preview==o?null:o;refresh();});if(preview==o){heading("台词预览");for(var e:o.getAsJsonArray("lines")){var line=e.getAsJsonObject();try{var speaker=list("speakers").asList().stream().map(JsonElement::getAsJsonObject).filter(s->str(s,"id","").equals(str(line,"speaker",""))).findFirst().orElse(null);var c=speaker==null?Component.literal(str(line,"text","")):ZombiesNpcRepository.GSON.fromJson(speaker,ZombiesScenarioConfig.Speaker.class).say(str(line,"text",""));for(var v:font.split(c,formWidth-10))rows.add(y->labels.add(()->graphics.drawString(font,v,left+5,y+6,0xFFFFFFFF,false)));}catch(RuntimeException e0){hint(detail(e0));}}}}
            case "lines"->{heading("台词内容");var ids=new ArrayList<String>(List.of(""));list("speakers").forEach(e->ids.add(e.getAsJsonObject().get("id").getAsString()));select("出场人物",o,"speaker",ids,this::speakerName);field("台词正文",o,"text","");field("本句停留（秒）",o,"seconds","1");heading("播放顺序");pair("上移台词",()->move(-1),"下移台词",()->move(1));}
            case "tasks"->{heading("任务身份");hint("NPC 动作引用编号："+str(o,"id",""));field("任务名称",o,"name","");heading("触发条件");toggle("自动检查触发",o,"automatic",true);field("最低波次（0不限）",o,"minWave","0");var refs=new ArrayList<String>(List.of(""));refs.addAll(ruleIds);select("需要已完成的 NPC 规则",o,"rule",refs,v->v.isBlank()?"不限":v);heading("顶部任务指引");field("更新后的任务文本（留空清除）",o,"text","");hint("自动条件同时满足时更新一次。关闭自动检查后，可由 NPC 的触发地图事件／任务指引动作更新；多次自动更新按条目顺序执行。");}
            case "events"->{heading("事件身份");hint("NPC 动作引用编号："+str(o,"id",""));field("事件名称",o,"name","");heading("自动触发条件");toggle("自动检查触发",o,"automatic",true);field("最低波次（0不限）",o,"minWave","0");var refs=new ArrayList<String>(List.of(""));refs.addAll(ruleIds);select("需要已完成的 NPC 规则",o,"rule",refs,v->v.isBlank()?"不限":v);hint("波次与规则完成条件需同时满足；关闭自动检查后由 NPC 动作触发。");heading("地图变化");row("管理区域变化 · "+o.getAsJsonArray("regions").size()+" 处",()->browse("regions",str(o,"name","事件")+" / 区域变化",o.getAsJsonArray("regions")));select("房间天气",o,"weather",List.of("","CLEAR","RAIN","THUNDER"),v->switch(v){case "CLEAR"->"晴朗";case "RAIN"->"下雨";case "THUNDER"->"雷雨";default->"不改变";});field("房间时间（空为不变）",o,"time","");pair("设为白天",()->{o.addProperty("time",1000);refresh();},"设为夜晚",()->{o.addProperty("time",18000);refresh();});hint("每个事件每局一次；房间天气和时间不改变其他关卡。");}
            case "regions"->regionDetail(o);
            case "areaRewards"->areaRewardDetail(o);
            case "rewardItems"->{heading("发放道具");field("物品 ID 或完整物品数据",o,"item","minecraft:emerald");field("数量",o,"count","1");row("从背包读取道具",()->rewardItemPicker(o));hint("保留自定义名字和 NBT；同一区域的所有道具一起检查背包空间，足够时才发放。");}
        }
    }
    private JsonObject preview;
    private void move(int amount){var l=level();int to=l.selected+amount;if(to<0||to>=l.entries.size())return;var object=l.entries.get(l.selected);l.entries.set(l.selected,l.entries.get(to));l.entries.set(to,object);l.selected=to;refresh();}
    private void colorPicker(JsonObject o,String key){select(key.equals("nameColor")?"名字预设颜色":"正文预设颜色",o,key,List.of("white","gray","dark_gray","black","gold","yellow","red","dark_red","green","dark_green","aqua","dark_aqua","blue","dark_blue","light_purple","dark_purple"),Function.identity());}
    private static String waveLabel(String k){return switch(k){case "healthMultiplier"->"生命倍率";case "damageMultiplier"->"伤害倍率";case "speedMultiplier"->"速度倍率";case "maxAlive"->"同时存活上限";case "fastestSpawnIntervalTicks"->"最快刷怪间隔（刻）";case "slowestSpawnIntervalTicks"->"最慢刷怪间隔（刻）";case "killPoints"->"击杀积分";default->"助攻积分";};}
    private void regionDetail(JsonObject r){
        heading("变化方式");String mode=str(r,"mode",r.has("scene")?"SCENE":"FILL");
        row("变化方式："+(mode.equals("SCENE")?"场景快照":"方块填充")+" ▾",()->minecraft.setScreen(new ZombiesNpcChoiceScreen(this,"变化方式",List.of("SCENE","FILL"),mode,v->v.equals("SCENE")?"场景快照":"方块填充",v->{r.addProperty("mode",v);if(v.equals("SCENE")){ensureSource(r);if(r.has("scene"))syncSceneExtent(r);}refresh();})));
        if(mode.equals("SCENE")){
            ensureSource(r);heading("源场景：你搭建好的变化后场景");hint("在源场景选择两个角，再读取。保存空气、模组方块、朝向和容器等附加数据。");
            row("到地图框选源场景",()->beginSelection(r,true));
            positionFields("源起点",r.getAsJsonObject("sourceFrom"),()->{});positionFields("源终点",r.getAsJsonObject("sourceTo"),()->{});
            pair("取准星方块为源起点",()->{r.add("sourceFrom",position(targetPos()));refresh();},"取准星方块为源终点",()->{r.add("sourceTo",position(targetPos()));refresh();});
            row("同时保存场景实体："+(captureEntities(r)?"开启":"关闭"),()->{r.addProperty("captureEntities",!captureEntities(r));refresh();});
            hint("保存盔甲架、展示框和普通生物；修改开关后需重新读取。玩家、剧情 NPC、波次怪物与临时掉落不保存。");
            row("读取源区域为目标场景",()->capture(r));
            hint(r.has("scene")&&!r.get("scene").isJsonNull()?"已读取："+sceneSummary(r):"尚未读取场景；读取后请保存配置。");
            heading("放置位置：关卡中要发生变化的区域");positionFields("目标原点",r.getAsJsonObject("from"),()->syncSceneExtent(r));
            row("到地图选择放置位置",()->beginSelection(r,false));
            row("取准星方块为目标原点",()->{r.add("from",position(targetPos()));if(r.has("scene"))syncSceneExtent(r);refresh();});
            hint("目标区域大小自动跟随场景。源场景可放在关卡外，读取时需在其附近。");
        }else{
            heading("目标填充范围");positionFields("起点",r.getAsJsonObject("from"),()->{});positionFields("终点",r.getAsJsonObject("to"),()->{});
            pair("取准星方块为起点",()->{r.add("from",position(targetPos()));refresh();},"取准星方块为终点",()->{r.add("to",position(targetPos()));refresh();});
            heading("填充内容");field("部署方块及状态",r,"block","minecraft:stone");row("选择部署方块",()->blockPicker(r));row("清空区域为空气",()->{r.addProperty("block","minecraft:air");refresh();});
        }
    }
    private void areaRewardDetail(JsonObject area){
        heading("区域道具身份");field("名称",area,"name","");
        heading("进入触发的范围");row("到地图框选触发区域",()->{beginSelection(area,false);selectingReward=true;});
        positionFields("区域起点",area.getAsJsonObject("from"),()->{});positionFields("区域终点",area.getAsJsonObject("to"),()->{});
        pair("取准星方块为起点",()->{area.add("from",position(targetPos()));refresh();},"取准星方块为终点",()->{area.add("to",position(targetPos()));refresh();});
        heading("发放内容");row("管理奖励道具 · "+area.getAsJsonArray("items").size()+" 种",()->browse("rewardItems",str(area,"name","区域")+" / 道具",area.getAsJsonArray("items")));
        heading("可选前置条件");field("最低波次（0不限）",area,"minWave","0");var refs=new ArrayList<String>(List.of(""));refs.addAll(ruleIds);select("需要已完成的 NPC 规则",area,"rule",refs,v->v.isBlank()?"不限":v);
        hint("每名存活队员每局领取一次。背包不足时不发放，整理后在区域内自动重试。范围必须在本关卡内。");
    }
    private void rewardItemPicker(JsonObject item){
        if(minecraft.player==null)return;var entries=new ArrayList<ZombiesNpcStackPickerScreen.Entry>();var inventory=minecraft.player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++){var stack=inventory.getItem(i);if(!stack.isEmpty())entries.add(new ZombiesNpcStackPickerScreen.Entry(stack.copy(),stack.save(new net.minecraft.nbt.CompoundTag()).toString(),com.cdp.codpattern.client.zombies.ZombiesGunNames.display(stack).getString(),stack.getCount()));}
        minecraft.setScreen(new ZombiesNpcStackPickerScreen(this,"选择区域奖励道具",entries,chosen->{item.addProperty("item",chosen.value());item.addProperty("count",chosen.count());refresh();}));
    }
    private void beginSelection(JsonObject region,boolean source){selectionRegion=region;selectingSource=source;selectingReward=false;suspended=true;minecraft.setScreen(null);}
    public static boolean selecting(){return active!=null&&active.suspended&&active.selectionRegion!=null;}
    /** Client-side corner selection only; capture permission, size and block state are verified by the server. */
    public static boolean selectionInput(boolean attack,boolean use,boolean finish,BlockPos hit){
        if(!selecting()||!attack&&!use)return false;var s=active;
        if(finish){s.selectionRegion=null;s.suspended=false;net.minecraft.client.Minecraft.getInstance().setScreen(s);return true;}
        if(hit==null)return true;
        if(s.selectingReward){s.selectionRegion.add(attack?"from":"to",position(hit));}
        else if(s.selectingSource){s.selectionRegion.add(attack?"sourceFrom":"sourceTo",position(hit));}
        else {s.selectionRegion.add("from",position(hit));syncSceneExtent(s.selectionRegion);s.selectionRegion=null;s.suspended=false;net.minecraft.client.Minecraft.getInstance().setScreen(s);}
        return true;
    }
    public static net.minecraft.world.phys.AABB selectionBounds(){
        if(!selecting())return null;try{var s=active;var r=s.selectionRegion;var a=pos(r.getAsJsonObject(s.selectingSource&&!s.selectingReward?"sourceFrom":"from"));var b=pos(r.getAsJsonObject(s.selectingSource&&!s.selectingReward?"sourceTo":"to"));
            return new net.minecraft.world.phys.AABB(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1.0,Math.max(a.getY(),b.getY())+1.0,Math.max(a.getZ(),b.getZ())+1.0);
        }catch(RuntimeException e){return null;}
    }
    public static String selectionHint(){return !selecting()?"":active.selectingReward?"框选道具触发区域：左键起点 · 右键终点 · Shift+右键完成":active.selectingSource?"框选源场景：左键起点 · 右键终点 · Shift+右键完成":"选择目标位置：点击要放置场景的最小角方块";}
    private void ensureSource(JsonObject r){if(!r.has("sourceFrom")||r.get("sourceFrom").isJsonNull())r.add("sourceFrom",r.get("from").deepCopy());if(!r.has("sourceTo")||r.get("sourceTo").isJsonNull())r.add("sourceTo",r.get("to").deepCopy());}
    private void positionFields(String label,JsonObject p,Runnable change){for(String k:List.of("x","y","z"))field(label+" "+k,p,k,"0",change);}
    private static void syncSceneExtent(JsonObject r){if(!r.has("scene")||r.get("scene").isJsonNull())return;var scene=ZombiesNpcRepository.GSON.fromJson(r.get("scene"),ZombiesScenarioConfig.Scene.class);r.add("to",position(scene.endAt(pos(r.getAsJsonObject("from")))));}
    private static boolean captureEntities(JsonObject region){if(region.has("captureEntities"))return region.get("captureEntities").getAsBoolean();if(region.has("scene")&&!region.get("scene").isJsonNull()){var scene=region.getAsJsonObject("scene");return scene.has("includesEntities")&&scene.get("includesEntities").getAsBoolean();}return true;}
    private void capture(JsonObject r){var request=new JsonObject();request.addProperty("includeEntities",captureEntities(r));request.add("from",r.get("sourceFrom").deepCopy());request.add("to",r.get("sourceTo").deepCopy());captureTarget=r;request("capture",request.toString());}
    private void blockPicker(JsonObject r){var choices=new ArrayList<ZombiesNpcStackPickerScreen.Entry>();for(var b:net.minecraft.core.registries.BuiltInRegistries.BLOCK)if(ZombiesNpcRuntime.supportsStoryBlock(b.defaultBlockState())){var stack=new net.minecraft.world.item.ItemStack(b);if(!stack.isEmpty())choices.add(new ZombiesNpcStackPickerScreen.Entry(stack,net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString(),stack.getHoverName().getString(),1));}minecraft.setScreen(new ZombiesNpcStackPickerScreen(this,"选择部署方块",choices,x->{r.addProperty("block",x.value());refresh();}));}
    private static String unique(JsonArray a,String prefix){int i=1;while(true){String id=prefix+i;if(a.asList().stream().noneMatch(e->str(e.getAsJsonObject(),"id","").equals(id)))return id;i++;}}
    private void normalize(JsonElement e){if(e.isJsonObject()){var o=e.getAsJsonObject();for(var k:List.copyOf(o.keySet())){var v=o.get(k);if(v.isJsonPrimitive()&&v.getAsString().isBlank()&&Set.of("healthMultiplier","damageMultiplier","speedMultiplier","maxAlive","fastestSpawnIntervalTicks","slowestSpawnIntervalTicks","killPoints","assistPoints","time","weather").contains(k))o.remove(k);else normalize(v);}}else if(e.isJsonArray())e.getAsJsonArray().forEach(this::normalize);}
    private void save(){try{var candidate=data.deepCopy();normalize(candidate);var config=ZombiesNpcRepository.GSON.fromJson(candidate,ZombiesScenarioConfig.class);var r=new JsonObject();r.addProperty("revision",revision);r.add("config",ZombiesNpcRepository.GSON.toJsonTree(config));request("save",r.toString());}catch(RuntimeException e){message=detail(e);}}
    @Override public void tick(){if(pending!=null&&System.currentTimeMillis()>deadline){pending=null;captureTarget=null;message="请求超时，请重试。";refresh();}for(var c:children())if(c instanceof EditBox b)b.tick();}
    @Override public boolean mouseScrolled(double x,double y,double delta){var l=level();if(l==null)return false;if(l.detail)l.detailScroll+=delta>0?-2:2;else l.scroll+=delta>0?-1:1;refresh();return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_S&&hasControlDown()&&canEdit()){save();return true;}if(!(getFocused() instanceof EditBox)){if(key==GLFW.GLFW_KEY_ENTER&&level()!=null&&!level().detail){open();return true;}if(key==GLFW.GLFW_KEY_DELETE&&canEdit()){deleteSelected();return true;}}return super.keyPressed(key,scan,mods);}
    @Override public void onClose(){if(level()!=null&&(level().detail||navigation.size()>1))back();else exit();}
    private void exit(){if(dirty())minecraft.setScreen(new ConfirmScreen(ok->{if(ok){active=null;minecraft.setScreen(parent);}else minecraft.setScreen(this);},Component.literal("放弃未保存的关卡剧情修改？"),Component.empty()));else{active=null;minecraft.setScreen(parent);}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        graphics=g;g.fill(0,0,width,height,0xF0101923);String title="关卡波次与剧情 · "+map+(dirty()?" *":"");g.drawString(font,font.plainSubstrByWidth(title,width-146),12,10,0xFF83D9BA,false);
        if(data==null)g.drawCenteredString(font,"正在加载关卡配置…",width/2,108,0xFF9EB5C5);
        else{var l=level();if(!l.detail&&l.entries.isEmpty())g.drawCenteredString(font,"暂无条目，点击上方新增。",width/2,116,0xFF9EB5C5);for(var r:labels)r.run();
            String hint=l.detail?"只编辑当前条目 · Esc 返回列表":"单击选中 · 双击或点击“编辑所选”进入 · 滚轮浏览";
            g.drawString(font,font.plainSubstrByWidth(message.isBlank()?hint:message,width-24),12,height-39,message.isBlank()?0xFF91A9BA:0xFFFFCE7E,false);
        }
        super.render(g,mx,my,partial);
    }
}
