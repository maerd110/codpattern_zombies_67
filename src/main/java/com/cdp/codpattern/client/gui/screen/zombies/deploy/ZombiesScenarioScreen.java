package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.app.zombies.scenario.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** Working-copy editor. Every added entry stays visible as an expandable card, rather than a hidden selection menu. */
public final class ZombiesScenarioScreen extends Screen {
    private static ZombiesScenarioScreen active;
    private final Screen parent;private final String map;
    private JsonObject data;private String saved="",revision="",message="";private boolean editable;private boolean suspended;
    private UUID pending;private long deadline;
    private int page,scroll,expanded=-1;private final List<Consumer<Integer>> rows=new ArrayList<>();private final List<Runnable> labels=new ArrayList<>();private GuiGraphics graphics;
    private int left,formWidth,bottom;
    public ZombiesScenarioScreen(Screen parent,String map){super(Component.literal("关卡波次与剧情"));this.parent=parent;this.map=map;active=this;}
    public static void disconnected(){active=null;}
    public static boolean reopenIfSuspended(String map){if(active==null||!active.suspended||!active.map.equals(map))return false;active.suspended=false;net.minecraft.client.Minecraft.getInstance().setScreen(active);return true;}
    public void load(){request("load","");}
    private boolean canEdit(){return editable&&pending==null&&data!=null;}
    private JsonArray list(String key){return data.getAsJsonArray(key);}
    private static String str(JsonObject o,String k,String def){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():def;}
    private boolean dirty(){return data!=null&&!data.toString().equals(saved);}
    private void request(String op,String body){try{ZombiesNpcEditorWire.checkRequest(body);pending=UUID.randomUUID();deadline=System.currentTimeMillis()+20000;ZombiesNpcDialogueNetwork.edit(new ZombiesNpcDialogueNetwork.EditorRequest(pending,map,op,body));message="正在读取／保存…";}catch(RuntimeException e){pending=null;message=e.getMessage();}if(minecraft!=null)rebuildWidgets();}
    public static void accept(ZombiesNpcDialogueNetwork.EditorReply reply){if(active==null||!reply.token().equals(active.pending))return;var s=active;s.pending=null;
        try{var r=ZombiesNpcRepository.GSON.fromJson(reply.payload(),ZombiesScenarioEditorService.Snapshot.class);if(!s.map.equals(r.map()))return;
            if(!r.error().isBlank()){s.message=r.error();s.rebuildWidgets();return;}
            s.data=ZombiesNpcRepository.GSON.toJsonTree(r.config()).getAsJsonObject();s.saved=s.data.toString();s.revision=r.revision();s.ruleIds=r.rules();s.mobIds=r.mobs();s.editable=r.editable();s.message="配置已读取／保存。";s.rebuildWidgets();
        }catch(RuntimeException e){s.message=e.getMessage();s.rebuildWidgets();}}
    private List<String> ruleIds=List.of(),mobIds=List.of("minecraft:zombie");
    private Button button(String text,int x,int y,int w,Runnable click,boolean enabled){var b=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(text,Math.max(8,w-8))),v->{try{click.run();}catch(RuntimeException e){message=e.getMessage();}}).bounds(x,y,w,20).build());b.active=enabled;b.setTooltip(Tooltip.create(Component.literal(text)));return b;}
    @Override protected void init(){rows.clear();labels.clear();left=12;formWidth=width-24;bottom=height-38;
        button("收起查看地图",width-122,3,110,()->{suspended=true;minecraft.setScreen(null);},pending==null&&minecraft.player!=null);
        int w=(width-30)/4;String[] names={"波次怪物","出场人物","波前对话","地图事件"};for(int i=0;i<4;i++){int j=i;button((page==i?"● ":"")+names[i],12+i*(w+2),27,w,()->{page=j;expanded=-1;scroll=0;rebuildWidgets();},pending==null);}
        int bw=(width-28)/3;button("保存配置",12,height-27,bw,this::save,canEdit());button("重新读取",14+bw,height-27,bw,()->{if(dirty())minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)load();},Component.literal("放弃未保存的修改？"),Component.empty()));else load();},pending==null);button("返回部署工具",16+bw*2,height-27,bw,this::onClose,pending==null);
        if(data==null){heading("正在加载关卡配置…");}else switch(page){case 0->waves();case 1->speakers();case 2->dialogues();case 3->events();}
        int count=Math.max(1,(bottom-58)/24);scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-count)));for(int i=scroll;i<Math.min(rows.size(),scroll+count);i++)rows.get(i).accept(56+(i-scroll)*24);
    }
    private void refresh(){rebuildWidgets();}
    private void heading(String text){rows.add(y->labels.add(()->graphics.drawString(font,font.plainSubstrByWidth(text,formWidth),left,y+6,0xFFD2E0E5,false)));}
    private void styled(Component text){rows.add(y->labels.add(()->graphics.drawString(font,text,left,y+6,0xFFFFFFFF,false)));}
    private void row(String text,Runnable click){rows.add(y->button(text,left,y,formWidth,click,canEdit()));}
    private void pair(String a,Runnable aa,String b,Runnable bb){rows.add(y->{button(a,left,y,formWidth/2-2,aa,canEdit());button(b,left+formWidth/2,y,formWidth/2,bb,canEdit());});}
    private void field(String caption,JsonObject o,String key,String def){rows.add(y->{int label=Math.min(170,formWidth/3);labels.add(()->graphics.drawString(font,font.plainSubstrByWidth(caption,label-6),left,y+6,0xFFBFD0DA,false));var box=addRenderableWidget(new EditBox(font,left+label,y,formWidth-label,20,Component.literal(caption)));box.setMaxLength(8192);box.setValue(str(o,key,def));box.setEditable(canEdit());box.setResponder(v->{o.addProperty(key,v);message="";});});}
    private void select(String caption,JsonObject o,String key,List<String> options,Function<String,String> label){String value=str(o,key,"");row(caption+"："+label.apply(value)+" ▾",()->minecraft.setScreen(new ZombiesNpcChoiceScreen(this,caption,options,value,label,v->{o.addProperty(key,v);refresh();})));}
    private void toggle(String caption,JsonObject o,String key,boolean def){row(caption+"："+(Boolean.parseBoolean(str(o,key,""+def))?"是":"否"),()->{o.addProperty(key,!Boolean.parseBoolean(str(o,key,""+def)));refresh();});}
    private void card(String caption,int i){rows.add(y->button((expanded==i?"▼ ":"▶ ")+caption,left,y,formWidth,()->{expanded=expanded==i?-1:i;refresh();},pending==null));}
    private void remove(JsonArray array,int i){row("删除此条目",()->{array.remove(i);expanded=-1;refresh();});}
    private void waves(){var waves=list("waves");row("新增波次",()->{int n=waves.asList().stream().mapToInt(e->e.getAsJsonObject().get("wave").getAsInt()).max().orElse(0)+1;var w=new JsonObject();w.addProperty("wave",n);w.addProperty("maxAlive",8);w.addProperty("fastestSpawnIntervalTicks",20);w.addProperty("slowestSpawnIntervalTicks",50);w.add("mobs",new JsonArray());waves.add(w);expanded=waves.size()-1;refresh();});
        heading("空怪物列表可作为空波；未填写的倍率沿用关卡默认值。");
        for(int i=0;i<waves.size();i++){int j=i;var w=waves.get(i).getAsJsonObject();card("第 "+str(w,"wave","?")+" 波 · "+w.getAsJsonArray("mobs").size()+" 种怪物",i);if(expanded!=i)continue;
            field("波次编号",w,"wave","1");toggle("首领入场音效",w,"bossIntro",false);
            for(String k:List.of("healthMultiplier","damageMultiplier","speedMultiplier","maxAlive","fastestSpawnIntervalTicks","slowestSpawnIntervalTicks"))field(waveLabel(k),w,k,"");
            var mobs=w.getAsJsonArray("mobs");row("新增怪物",()->{var m=new JsonObject();m.addProperty("entity","minecraft:zombie");m.addProperty("count",1);mobs.add(m);refresh();});
            for(int k=0;k<mobs.size();k++){int index=k;var m=mobs.get(k).getAsJsonObject();heading("怪物 "+(k+1));select("怪物种类",m,"entity",mobIds,v->{var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.tryParse(v));return type==null?v:type.getDescription().getString()+" · "+v;});field("数量",m,"count","1");
                for(String key:List.of("healthMultiplier","damageMultiplier","speedMultiplier","killPoints","assistPoints"))field(waveLabel(key),m,key,"");row("删除怪物 "+(k+1),()->{mobs.remove(index);refresh();});}
            remove(waves,j);
        }
    }
    private static String waveLabel(String k){return switch(k){case "healthMultiplier"->"生命倍率";case "damageMultiplier"->"伤害倍率";case "speedMultiplier"->"速度倍率";case "maxAlive"->"同时存活上限";case "fastestSpawnIntervalTicks"->"最快刷怪间隔（刻）";case "slowestSpawnIntervalTicks"->"最慢刷怪间隔（刻）";case "killPoints"->"击杀积分";default->"助攻积分";};}
    private void speakers(){var a=list("speakers");row("新增出场人物",()->{var s=new JsonObject();s.addProperty("id",unique(a,"speaker_"));s.addProperty("name","新人物");s.addProperty("nameColor","white");s.addProperty("textColor","white");a.add(s);expanded=a.size()-1;refresh();});
        for(int i=0;i<a.size();i++){var s=a.get(i).getAsJsonObject();card(str(s,"name","")+" ["+str(s,"id","")+"]",i);if(expanded!=i)continue;
            // Stable IDs prevent dialogue references from silently changing when a display name is edited.
            heading("人物编号："+str(s,"id",""));field("显示名字",s,"name","");field("名字颜色",s,"nameColor","white");field("正文颜色",s,"textColor","white");colorPicker(s,"nameColor");colorPicker(s,"textColor");toggle("粗体",s,"bold",false);toggle("斜体",s,"italic",false);remove(a,i);
        }
    }
    private void colorPicker(JsonObject s,String key){select(key.equals("nameColor")?"名字预设颜色":"正文预设颜色",s,key,List.of("white","gray","dark_gray","black","gold","yellow","red","dark_red","green","dark_green","aqua","dark_aqua","blue","dark_blue","light_purple","dark_purple"),Function.identity());}
    private void dialogues(){var ds=list("dialogues");row("新增波前对话",()->{var d=new JsonObject();int n=1;var used=new HashSet<Integer>();ds.forEach(e->used.add(e.getAsJsonObject().get("wave").getAsInt()));while(used.contains(n))n++;d.addProperty("wave",n);d.add("lines",new JsonArray());ds.add(d);expanded=ds.size()-1;refresh();});heading("每句停留完毕后再显示下一句，最后一句结束后才允许刷怪。");
        for(int i=0;i<ds.size();i++){var d=ds.get(i).getAsJsonObject();card("第 "+str(d,"wave","?")+" 波 · "+d.getAsJsonArray("lines").size()+" 句",i);if(expanded!=i)continue;field("播放波次",d,"wave","1");field("开播前等待（秒）",d,"delaySeconds","0");var a=d.getAsJsonArray("lines");
            row("新增波前台词",()->{var l=new JsonObject();l.addProperty("speaker","");l.addProperty("text","新台词");l.addProperty("seconds",1);a.add(l);refresh();});
            for(int k=0;k<a.size();k++){int index=k;var l=a.get(k).getAsJsonObject();heading("台词 "+(k+1));var ids=new ArrayList<String>(List.of(""));list("speakers").forEach(e->ids.add(e.getAsJsonObject().get("id").getAsString()));
                select("出场人物",l,"speaker",ids,v->v.isBlank()?"旁白（无名字）":speakerName(v));field("台词正文",l,"text","");field("本句停留（秒）",l,"seconds","1");pair("上移台词 "+(k+1),()->{if(index>0){var e=a.get(index);a.set(index,a.get(index-1));a.set(index-1,e);refresh();}},"下移台词 "+(k+1),()->{if(index<a.size()-1){var e=a.get(index);a.set(index,a.get(index+1));a.set(index+1,e);refresh();}});row("删除台词 "+(k+1),()->{a.remove(index);refresh();});}
            row("在界面预览本波台词",()->{preview=preview==d?null:d;refresh();});if(preview==d){heading("台词预览");for(var e:a){var l=e.getAsJsonObject();try{var speaker=list("speakers").asList().stream().map(JsonElement::getAsJsonObject).filter(s->str(s,"id","").equals(str(l,"speaker",""))).findFirst().orElse(null);Component c=speaker==null?Component.literal(str(l,"text","")):ZombiesNpcRepository.GSON.fromJson(speaker,ZombiesScenarioConfig.Speaker.class).say(str(l,"text",""));styled(c);}catch(RuntimeException ex){heading(ex.getMessage());}}}
            remove(ds,i);
        }
    }
    private JsonObject preview;
    private String speakerName(String id){return list("speakers").asList().stream().map(JsonElement::getAsJsonObject).filter(o->str(o,"id","").equals(id)).map(o->str(o,"name",id)).findFirst().orElse(id);}
    private void events(){var a=list("events");row("新增地图事件",()->{var e=new JsonObject();e.addProperty("id",unique(a,"event_"));e.addProperty("name","新事件");e.addProperty("automatic",true);e.addProperty("minWave",1);e.addProperty("rule","");e.addProperty("weather","CLEAR");e.add("regions",new JsonArray());a.add(e);expanded=a.size()-1;refresh();});heading("每个事件每局执行一次；天气与时间仅影响本房间玩家的显示。");heading("可收起界面移动／瞄准，Ctrl+右键部署工具返回后取准星坐标。");
        for(int i=0;i<a.size();i++){var e=a.get(i).getAsJsonObject();card(str(e,"name","")+" ["+str(e,"id","")+"]",i);if(expanded!=i)continue;heading("事件编号（NPC 动作引用）："+str(e,"id",""));field("事件名称",e,"name","");toggle("自动检查触发",e,"automatic",true);field("最低波次（0不限）",e,"minWave","0");var refs=new ArrayList<String>(List.of(""));refs.addAll(ruleIds);select("需要已完成的 NPC 规则",e,"rule",refs,v->v.isBlank()?"不限":v);heading("波次与 NPC 完成条件需同时满足；关闭自动检查后由 NPC 动作触发。");
            select("房间天气",e,"weather",List.of("","CLEAR","RAIN","THUNDER"),v->switch(v){case "CLEAR"->"晴朗";case "RAIN"->"下雨";case "THUNDER"->"雷雨";default->"不改变";});field("房间时间（空为不变）",e,"time","");pair("设为白天",()->{e.addProperty("time",1000);refresh();},"设为夜晚",()->{e.addProperty("time",18000);refresh();});
            var rs=e.getAsJsonArray("regions");row("新增方块／区域变化",()->{var r=new JsonObject();var pos=playerPos();r.add("from",position(pos));r.add("to",position(pos));r.addProperty("block","minecraft:stone");rs.add(r);refresh();});
            for(int k=0;k<rs.size();k++){int j=k;var r=rs.get(k).getAsJsonObject();heading("区域 "+(k+1)+"（两点相同即单个方块，最多4096格）");positionFields("起点",r.getAsJsonObject("from"));positionFields("终点",r.getAsJsonObject("to"));pair("取准星方块为起点",()->{r.add("from",position(targetPos()));refresh();},"取准星方块为终点",()->{r.add("to",position(targetPos()));refresh();});field("部署方块及状态",r,"block","minecraft:stone");row("选择部署方块",()->blockPicker(r));row("清空区域为空气",()->{r.addProperty("block","minecraft:air");refresh();});row("删除区域 "+(k+1),()->{rs.remove(j);refresh();});}
            remove(a,i);
        }
    }
    private void positionFields(String label,JsonObject p){for(String k:List.of("x","y","z"))field(label+" "+k,p,k,"0");}
    private static JsonObject position(BlockPos pos){var o=new JsonObject();o.addProperty("x",pos.getX());o.addProperty("y",pos.getY());o.addProperty("z",pos.getZ());return o;}
    private BlockPos playerPos(){return minecraft.player==null?BlockPos.ZERO:minecraft.player.blockPosition();}
    private BlockPos targetPos(){return minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit?hit.getBlockPos():playerPos();}
    private void blockPicker(JsonObject r){var choices=new ArrayList<ZombiesNpcStackPickerScreen.Entry>();for(var b:net.minecraft.core.registries.BuiltInRegistries.BLOCK)if(ZombiesNpcRuntime.supportsStoryBlock(b.defaultBlockState())){var stack=new net.minecraft.world.item.ItemStack(b);if(!stack.isEmpty())choices.add(new ZombiesNpcStackPickerScreen.Entry(stack,net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString(),stack.getHoverName().getString(),1));}minecraft.setScreen(new ZombiesNpcStackPickerScreen(this,"选择部署方块",choices,x->{r.addProperty("block",x.value());refresh();}));}
    private static String unique(JsonArray a,String prefix){int i=1;while(true){String id=prefix+i;if(a.asList().stream().noneMatch(e->str(e.getAsJsonObject(),"id","").equals(id)))return id;i++;}}
    private void normalize(JsonElement e){if(e.isJsonObject()){var o=e.getAsJsonObject();for(var k:List.copyOf(o.keySet())){var v=o.get(k);if(v.isJsonPrimitive()&&v.getAsString().isBlank()&&Set.of("healthMultiplier","damageMultiplier","speedMultiplier","maxAlive","fastestSpawnIntervalTicks","slowestSpawnIntervalTicks","killPoints","assistPoints","time","weather").contains(k))o.remove(k);else normalize(v);}}else if(e.isJsonArray())e.getAsJsonArray().forEach(this::normalize);}
    private void save(){try{var candidate=data.deepCopy();normalize(candidate);var config=ZombiesNpcRepository.GSON.fromJson(candidate,ZombiesScenarioConfig.class);var r=new JsonObject();r.addProperty("revision",revision);r.add("config",ZombiesNpcRepository.GSON.toJsonTree(config));request("save",r.toString());}catch(RuntimeException e){message=e.getMessage();}}
    @Override public void tick(){if(pending!=null&&System.currentTimeMillis()>deadline){pending=null;message="请求超时，请重试。";refresh();}for(var c:children())if(c instanceof EditBox b)b.tick();}
    @Override public boolean mouseScrolled(double x,double y,double delta){scroll+=delta>0?-2:2;refresh();return true;}
    @Override public void onClose(){if(dirty())minecraft.setScreen(new ConfirmScreen(ok->{if(ok){active=null;minecraft.setScreen(parent);}else minecraft.setScreen(this);},Component.literal("放弃未保存的关卡剧情修改？"),Component.empty()));else{active=null;minecraft.setScreen(parent);}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){graphics=g;g.fill(0,0,width,height,0xF0101923);g.drawString(font,font.plainSubstrByWidth("关卡波次与剧情 · "+map,width-146),12,10,0xFF83D9BA,false);for(var r:labels)r.run();g.drawString(font,font.plainSubstrByWidth(message,width-24),12,height-38,0xFFFFCE7E,false);super.render(g,mx,my,partial);}
}
