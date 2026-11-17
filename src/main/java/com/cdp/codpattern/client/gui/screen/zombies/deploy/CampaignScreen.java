package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.campaign.*;
import com.cdp.codpattern.client.zombies.CampaignClient;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Account home and authoring share the deployment palette, compact cards and drill-down forms. */
public final class CampaignScreen extends Screen {
    private static CampaignScreen active;
    private final Screen parent;
    private final boolean editor;
    private UUID pending;
    private long deadline;
    private JsonObject data=new JsonObject(),config;
    private String view="home",notice="",revision="",saved="";
    private final Deque<Level> navigation=new ArrayDeque<>();
    private final List<Consumer<Integer>> rows=new ArrayList<>();
    private final List<Label> labels=new ArrayList<>();
    private final List<Card> cards=new ArrayList<>();
    private int scroll;
    private record Level(String kind,JsonArray list,JsonObject item){}
    private record Label(String text,int x,int y,int color){}
    private record Card(int x,int y,int w,int h,String title,String detail,int color){}
    public CampaignScreen(Screen parent,boolean editor){super(Component.literal(editor?"战役与跨关剧情":"个人主页 · 战役旅程"));this.parent=parent;this.editor=editor;active=this;}
    public static void disconnected(){active=null;}
    public void load(){send(editor?"camp_edit":"camp_home",new JsonObject());}
    public void showVote(){view="vote";}
    public void pollUpdated(){if(view.equals("vote")){if(CampaignClient.vote.size()==0){if(parent==null)minecraft.setScreen(null);else onClose();}else refresh();}else if(!editor&&view.equals("home")&&pending==null)refresh();}
    public static void accept(UUID token,JsonObject response){if(active==null||!token.equals(active.pending))return;var s=active;s.pending=null;
        if(response.has("error")){s.notice=response.get("error").getAsString();s.refresh();return;}
        s.data=response;s.view=CampaignRuntime.str(response,"view");s.scroll=0;s.navigation.clear();s.notice=CampaignRuntime.str(response,"notice");
        if(s.view.equals("editor")){s.config=response.getAsJsonObject("config").deepCopy();s.revision=CampaignRuntime.str(response,"revision");s.prepareBindings();}
        if(response.has("vote"))CampaignClient.vote=response.getAsJsonObject("vote");if(response.has("room"))CampaignClient.room=response.getAsJsonObject("room");s.refresh();
    }
    private void send(String operation,JsonObject body){try{pending=CampaignClient.request(operation,body);deadline=System.currentTimeMillis()+20000;notice="正在处理…";}catch(RuntimeException e){notice=e.getMessage();pending=null;}refresh();}
    private static JsonObject body(String key,String value){var o=new JsonObject();o.addProperty(key,value);return o;}
    private void refresh(){if(minecraft!=null)rebuildWidgets();}
    private boolean dirty(){return config!=null&&!config.toString().equals(saved);}
    @Override protected void init(){active=this;rows.clear();labels.clear();cards.clear();
        button("返回",12,35,74,()->{if(!navigation.isEmpty()){navigation.removeLast();scroll=0;refresh();}else if(!view.equals("home")&&!editor)load();else onClose();});
        int toolbar=Math.min(110,(width-112)/2);
        if(editor){button("保存战役",width-12-toolbar,35,toolbar,this::save);button("重新读取",width-20-toolbar*2,35,toolbar,()->confirmDiscard(this::load));}
        else if(!view.equals("vote")){button("我的旅程",width-20-toolbar*2,35,toolbar,this::load);button("我的收藏",width-12-toolbar,35,toolbar,()->send("camp_profile",new JsonObject()));}
        switch(view){case "editor"->buildEditor();case "save"->buildSave();case "profile"->buildProfile();case "vote"->buildVote();default->buildHome();}
        int visible=Math.max(1,(height-112)/27);scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visible)));for(int i=scroll;i<Math.min(rows.size(),scroll+visible);i++)rows.get(i).accept(72+(i-scroll)*27);
        button("关闭",width-94,height-28,82,this::onClose);
        if(rows.size()>visible)labels.add(new Label("滚轮查看其余内容 · "+(scroll+1)+" / "+rows.size(),left()+7,height-45,0xFF9CB6C6));
    }
    private void buildHome(){
        line("战役存档记录故事路线；收藏与通关记录属于你的账号。",0xFF9CB6C6);
        if(CampaignClient.vote.size()>0)row("参与当前剧情投票",()->{view="vote";scroll=0;refresh();});
        var r=CampaignClient.room;if(r.size()>0){
            line("当前章节："+CampaignRuntime.str(r,"chapter"),0xFF83D9BA);
            boolean keep=r.has("keep")&&r.get("keep").getAsBoolean();row((keep?"● 已保留本次路线":"保留本次故事路线")+" · 原存档保留",()->{var b=new JsonObject();b.addProperty("keep",!keep);send("camp_keep",b);});
            row("查看当前存档剧情仓库",()->{view="warehouse";navigation.add(new Level("warehouse",new JsonArray(),r.getAsJsonObject("items")));refresh();});
            if(r.get("host").getAsBoolean()&&r.get("finished").getAsBoolean())row("带领在线队员继续下一关",()->send("camp_next",new JsonObject()));
            if(r.get("host").getAsBoolean())row("结束本次战役房间",()->confirm("结束本次游玩？","将保留最近成功写入的安全检查点。",()->send("camp_end",new JsonObject())));
            if(!CampaignRuntime.str(r,"error").isBlank()){line(CampaignRuntime.str(r,"error"),0xFFEF7777);row("重试保存检查点",()->send("camp_retry",new JsonObject()));}
        }
        if(view.equals("warehouse")){rows.clear();var inventory=navigation.getLast().item();inventoryRows(inventory);return;}
        if(data.has("saves"))for(var value:data.getAsJsonArray("saves")){var s=value.getAsJsonObject();var point=s.getAsJsonObject("point");card(CampaignRuntime.str(s,"name"),chapterName(CampaignRuntime.str(point,"chapter"))+" · "+CampaignRuntime.str(point,"checkpoint"),0xFF83D9BA,()->send("camp_detail",body("save",CampaignRuntime.str(s,"id"))));}
        line("开始新的战役",0xFFE9BD70);
        if(data.has("campaigns"))for(var c:data.getAsJsonArray("campaigns")){var o=c.getAsJsonObject();card(CampaignRuntime.str(o,"name"),o.getAsJsonArray("chapters").size()+" 个章节 · 使用关卡统一初始装备",0xFF72B8E0,()->confirm("开始新的战役路线？","当前房间在线队员将一起前往首关。",()->{var q=body("campaign",CampaignRuntime.str(o,"id"));q.addProperty("save","");send("camp_launch",q);}));}
        line("服务器成员的公开展示",0xFFBAA1E5);if(data.has("people"))for(var person:data.getAsJsonArray("people")){var o=person.getAsJsonObject();row(CampaignRuntime.str(o,"name")+" · 查看收藏架",()->send("camp_profile",body("owner",CampaignRuntime.str(o,"id"))));}
        if(data.has("public")){boolean pub=data.get("public").getAsBoolean();row("我的收藏公开展示："+(pub?"开启":"关闭"),()->{var b=new JsonObject();b.addProperty("public",!pub);send("camp_public",b);});}
    }
    private void buildSave(){var save=data.getAsJsonObject("save");line(CampaignRuntime.str(save,"name"),0xFF83D9BA);var point=CampaignStore.tip(save);line("最近检查点："+chapterName(CampaignRuntime.str(point,"chapter"))+" / "+CampaignRuntime.str(point,"checkpoint"),0xFFBDCFD8);
        field("存档名称",save,"name");row("保存名称",()->{var q=body("save",CampaignRuntime.str(save,"id"));q.addProperty("name",CampaignRuntime.str(save,"name"));q.addProperty("revision",CampaignRuntime.str(data,"revision"));send("camp_rename",q);});row("继续这份存档",()->launch(save,-1));row("查看这份存档的剧情仓库",()->{view="warehouse";navigation.add(new Level("warehouse",new JsonArray(),point.getAsJsonObject("items")));refresh();});
        line("历史章节 · 从起点回玩会建立新分支",0xFFE9BD70);var history=save.getAsJsonArray("history");for(int i=0;i<history.size();i++){var h=history.get(i).getAsJsonObject();if(!CampaignRuntime.str(h,"checkpoint").equals("chapter_start"))continue;int index=i;row(chapterName(CampaignRuntime.str(h,"chapter"))+" · 回玩",()->confirm("建立回玩分支？","原存档保留，新路线使用该章节开始时的历史。",()->launch(save,index)));}
        row("删除这份存档",()->confirm("确定删除这份故事存档？","此操作不影响账号收藏，也不影响其他故事存档。",()->{var q=body("save",CampaignRuntime.str(save,"id"));q.addProperty("revision",CampaignRuntime.str(data,"revision"));send("camp_delete",q);}));
    }
    private void inventoryRows(JsonObject inventory){line("存档剧情仓库",0xFFE9BD70);boolean empty=true;for(var pair:inventory.entrySet())if(pair.getValue().getAsInt()>0){empty=false;line(label("itemNames",pair.getKey())+" × "+pair.getValue().getAsInt(),0xFFBDCFD8);}if(empty)line("这份存档暂时没有剧情道具",0xFF9CB6C6);}
    private void launch(JsonObject save,int index){var q=body("save",CampaignRuntime.str(save,"id"));q.addProperty("campaign",CampaignRuntime.str(save,"campaign"));q.addProperty("index",index);send("camp_launch",q);}
    private void buildProfile(){line(CampaignRuntime.str(data,"name")+" 的收藏架",0xFF83D9BA);var collection=data.getAsJsonObject("collections");if(collection.size()==0)line("尚未获得纪念收藏",0xFF9CB6C6);for(var pair:collection.entrySet())card(label("collectionNames",pair.getKey()),"账号收藏 · 仅供展示",0xFFEF4444,()->{});line("亲自完成的章节",0xFFE9BD70);for(var pair:data.getAsJsonObject("completed").entrySet())line(chapterName(pair.getKey()),0xFFBDCFD8);}
    private void buildVote(){var vote=CampaignClient.vote;if(vote.size()==0){line("本次投票已结束",0xFF9CB6C6);return;}int round=vote.get("round").getAsInt();line(round==0?"全队剧情投票":round==1?"平票 · 再投一次":"再次平票 · 由房主决定",0xFFE9BD70);for(var text:CampaignRuntime.str(vote,"text").split("\n"))line(text,0xFFBDCFD8);line(round==2?"等待房主选择并列选项":"剩余 "+vote.get("seconds").getAsInt()+" 秒 · 未投视为弃权",0xFF9CB6C6);
        for(var option:vote.getAsJsonArray("options")){var o=option.getAsJsonObject();String id=CampaignRuntime.str(o,"id");row((id.equals(CampaignRuntime.str(vote,"selected"))?"● ":"")+CampaignRuntime.str(o,"name"),()->{if(round==2&&!vote.get("decider").getAsBoolean())return;var q=body("token",CampaignRuntime.str(vote,"token"));q.addProperty("option",id);send("camp_vote",q);});}
    }
    private void buildEditor(){
        if(config==null){line("正在读取战役配置…",0xFF9CB6C6);return;}
        if(navigation.isEmpty()){line("集中定义跨关身份，再在事件或 NPC 画布中引用。",0xFF9CB6C6);for(String kind:List.of("campaigns","states","actors","items","tasks","collections","events")){var list=config.getAsJsonArray(kind);card(kindName(kind),list.size()+" 个条目 · 点击进入",accent(kind),()->{navigation.add(new Level(kind,list,null));scroll=0;refresh();});}return;}
        var l=navigation.getLast();line(kindName(l.kind()),accent(l.kind()));
        if(l.item()==null){row("＋ 新增"+kindName(l.kind()),()->{var item=defaultItem(l.kind(),l.list());l.list().add(item);navigation.add(new Level(l.kind(),l.list(),item));scroll=0;refresh();});for(var v:l.list()){var o=v.getAsJsonObject();card(CampaignRuntime.str(o,"name").isBlank()?CampaignRuntime.str(o,"operation"):CampaignRuntime.str(o,"name"),CampaignRuntime.str(o,"id").isBlank()?CampaignRuntime.str(o,"key"):CampaignRuntime.str(o,"id"),accent(l.kind()),()->{navigation.add(new Level(l.kind(),l.list(),o));scroll=0;refresh();});}return;}
        var item=l.item();
        if(l.kind().equals("effects")){select("操作类型",item,"operation",Arrays.stream(CampaignDefinition.Operation.values()).map(Enum::name).toList(),CampaignScreen::operationName);
            String op=CampaignRuntime.str(item,"operation");String target=switch(op){case "STATE"->"states";case "TASK"->"tasks";case "ACTOR"->"actors";case "GIVE","TAKE"->"items";case "COLLECTION"->"collections";default->"";};
            if(target.isBlank())field("检查点内部编号",item,"key");else reference("目标条目",item,"key",target,false);
            if(op.equals("ACTOR"))select("人物状态",item,"value",List.of("alive","dead","hidden"),v->switch(v){case "alive"->"存活";case "dead"->"死亡";default->"隐藏";});else if(op.equals("TASK"))select("任务进度",item,"value",List.of("active","completed","failed"),v->switch(v){case "active"->"进行中";case "completed"->"已完成";default->"已失败";});else if(op.equals("STATE"))field("状态值／选择结果",item,"value");
            if(op.equals("GIVE")||op.equals("TAKE")){number("数量",item,"count");toggle("操作对象：",item,"everyone","全队每人一份","当前交互玩家");}
            if(op.equals("CHECKPOINT")||op.equals("COMPLETE"))line("在开局、波间休整或通关时执行；失败保留旧检查点。",0xFFE9BD70);
        }else if(l.kind().equals("bindings")){field("本关 NPC 内部编号",item,"local");reference("战役人物",item,"actor","actors",false);
        }else{
            field("内部编号",item,"id");field("显示名称",item,"name");
            switch(l.kind()){
                case "campaigns"->nested("编辑章节顺序","chapters",item);
                case "chapters"->{select("章节地图",item,"map",strings(data.getAsJsonArray("maps")),v->v);reference("通关账号收藏奖励",item,"reward","collections",true);nested("编辑跨关人物绑定","bindings",item);}
                case "events"->{reference("所属章节",item,"chapter","chapters",false);number("自动触发最低波次（0 为不按波次触发）",item,"minWave");field("前置本关 NPC 规则（可留空）",item,"rule");reference("前置战役状态（可留空）",item,"state","states",true);field("前置状态需要等于",item,"equalsValue");nested("编辑事件执行结果","effects",item);line("波次和规则都留空时，只由画布动作触发。",0xFF9CB6C6);}
                default->field(l.kind().equals("tasks")?"任务指引文本":"说明／展示介绍",item,"description");
            }
        }
        if(l.list().size()>1){row("向前移动",()->move(l,-1));row("向后移动",()->move(l,1));}
        row("删除此条目",()->confirm("删除此条目？","保存时会检查其他条目是否仍然引用它。",()->{l.list().remove(item);navigation.removeLast();scroll=0;refresh();}));
    }
    private void nested(String title,String key,JsonObject item){row(title,()->{if(!item.has(key))item.add(key,new JsonArray());navigation.add(new Level(key,item.getAsJsonArray(key),null));scroll=0;refresh();});}
    private void move(Level l,int step){int i=l.list().asList().indexOf(l.item()),next=i+step;if(next>=0&&next<l.list().size()){l.list().remove(i);l.list().asList().add(next,l.item());refresh();}}
    private void reference(String title,JsonObject item,String key,String kind,boolean blank){var values=new ArrayList<String>();if(blank)values.add("");JsonArray list;if(kind.equals("chapters")){list=new JsonArray();for(var c:config.getAsJsonArray("campaigns"))for(var ch:c.getAsJsonObject().getAsJsonArray("chapters"))list.add(ch);}else list=config.getAsJsonArray(kind);for(var value:list)values.add(CampaignRuntime.str(value.getAsJsonObject(),"id"));JsonArray source=list;select(title,item,key,values,v->v.isBlank()?"不设置":source.asList().stream().map(JsonElement::getAsJsonObject).filter(o->CampaignRuntime.str(o,"id").equals(v)).map(o->CampaignRuntime.str(o,"name")+" · "+v).findFirst().orElse(v));}
    private void select(String title,JsonObject item,String key,List<String> options,java.util.function.Function<String,String> label){row(title+"："+label.apply(CampaignRuntime.str(item,key)),()->minecraft.setScreen(new ZombiesNpcChoiceScreen(this,title,options,CampaignRuntime.str(item,key),label,v->{item.addProperty(key,v);refresh();})));}
    private void toggle(String title,JsonObject item,String key,String yes,String no){boolean value=item.has(key)&&item.get(key).getAsBoolean();row(title+(value?yes:no),()->{item.addProperty(key,!value);refresh();});}
    private void field(String title,JsonObject item,String key){line(title,0xFF9CB6C6);rows.add(y->{var box=addRenderableWidget(new EditBox(font,left()+5,y,span()-10,20,Component.literal(title)));box.setMaxLength(key.equals("description")?2048:256);box.setValue(CampaignRuntime.str(item,key));box.setResponder(v->item.addProperty(key,v));box.setEditable(pending==null);});}
    private void number(String title,JsonObject item,String key){field(title,item,key);}
    private JsonObject defaultItem(String kind,JsonArray list){var o=new JsonObject();int i=1;String prefix=switch(kind){case "chapters"->"chapter_";case "events"->"event_";default->"entry_";};while(true){String id=prefix+i;if(list.asList().stream().noneMatch(v->CampaignRuntime.str(v.getAsJsonObject(),"id").equals(id)))break;i++;}o.addProperty("id",prefix+i);o.addProperty("name","新"+kindName(kind));o.addProperty("description","");switch(kind){case "campaigns"->o.add("chapters",new JsonArray());case "chapters"->{o.addProperty("map","");o.addProperty("reward","");o.add("bindings",new JsonArray());}case "events"->{o.addProperty("chapter","");o.addProperty("minWave",0);o.addProperty("rule","");o.addProperty("state","");o.addProperty("equalsValue","");o.add("effects",new JsonArray());}case "effects"->{o.addProperty("operation","STATE");o.addProperty("key","");o.addProperty("value","");o.addProperty("count",1);o.addProperty("everyone",true);}case "bindings"->{o.addProperty("local","");o.addProperty("actor","");}}return o;}
    private void save(){try{var candidate=config.deepCopy();for(var c:candidate.getAsJsonArray("campaigns"))for(var ch:c.getAsJsonObject().getAsJsonArray("chapters")){var chapter=ch.getAsJsonObject();var bindings=new JsonObject();if(chapter.has("bindings"))for(var binding:chapter.getAsJsonArray("bindings")){var b=binding.getAsJsonObject();String key=CampaignRuntime.str(b,"local");if(bindings.has(key))throw new IllegalArgumentException("本关 NPC 绑定重复："+key);bindings.addProperty(key,CampaignRuntime.str(b,"actor"));}chapter.remove("bindings");chapter.add("actors",bindings);}
        for(var e:candidate.getAsJsonArray("events")){var event=e.getAsJsonObject();event.addProperty("minWave",Integer.parseInt(CampaignRuntime.str(event,"minWave")));for(var f:event.getAsJsonArray("effects")){var effect=f.getAsJsonObject();effect.addProperty("count",Integer.parseInt(CampaignRuntime.str(effect,"count")));}}
        CampaignStore.GSON.fromJson(candidate,CampaignDefinition.class);var q=body("revision",revision);q.add("config",candidate);send("camp_config",q);
        }catch(RuntimeException e){notice=e.getMessage();refresh();}}
    public void prepareBindings(){if(config==null)return;for(var c:config.getAsJsonArray("campaigns"))for(var ch:c.getAsJsonObject().getAsJsonArray("chapters")){var chapter=ch.getAsJsonObject();var list=new JsonArray();if(chapter.has("actors"))for(var pair:chapter.getAsJsonObject("actors").entrySet()){var o=new JsonObject();o.addProperty("local",pair.getKey());o.addProperty("actor",pair.getValue().getAsString());list.add(o);}chapter.remove("actors");chapter.add("bindings",list);}saved=config.toString();}
    private static List<String> strings(JsonArray array){return array==null?List.of():array.asList().stream().map(JsonElement::getAsString).toList();}
    private String label(String kind,String id){if(!data.has(kind))return id;return data.getAsJsonArray(kind).asList().stream().map(JsonElement::getAsJsonObject).filter(o->CampaignRuntime.str(o,"id").equals(id)).map(o->CampaignRuntime.str(o,"name")).findFirst().orElse(id);}
    private String chapterName(String id){if(data.has("campaigns"))for(var c:data.getAsJsonArray("campaigns"))for(var ch:c.getAsJsonObject().getAsJsonArray("chapters"))if(CampaignRuntime.str(ch.getAsJsonObject(),"id").equals(id))return CampaignRuntime.str(ch.getAsJsonObject(),"name");return id;}
    private int left(){return Math.max(12,(width-640)/2);}private int span(){return Math.min(width-24,640);}
    private void line(String text,int color){rows.add(y->labels.add(new Label(text,left()+7,y+5,color)));}
    private void row(String text,Runnable action){rows.add(y->button(text,left(),y,span(),action));}
    private void card(String title,String detail,int color,Runnable action){rows.add(y->{cards.add(new Card(left(),y,span(),48,title,detail,color));var b=new Button(left(),y,span(),48,Component.literal(title),ignored->action.run(),supplier->supplier.get()){@Override public void renderWidget(GuiGraphics g,int x,int y,float partial){}};b.active=pending==null;b.setTooltip(Tooltip.create(Component.literal(title+"\n"+detail)));addRenderableWidget(b);});rows.add(y->{});}
    private void button(String title,int x,int y,int w,Runnable action){var b=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(title,Math.max(10,w-12))),ignored->action.run()).bounds(x,y,w,20).build());b.active=pending==null;b.setTooltip(Tooltip.create(Component.literal(title)));}
    private void confirm(String title,String detail,Runnable action){minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)action.run();},Component.literal(title),Component.literal(detail)));}
    private void confirmDiscard(Runnable action){if(dirty())confirm("放弃尚未保存的战役修改？","",action);else action.run();}
    @Override public void onClose(){if(view.equals("vote"))CampaignClient.dismissVote();confirmDiscard(()->{active=null;minecraft.setScreen(parent);});}
    @Override public boolean mouseClicked(double x,double y,int button){if(parent==null&&button==0&&(x<left()-6||x>left()+span()+6)&&!view.equals("vote")){onClose();return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double delta){scroll+=delta>0?-1:1;refresh();return true;}
    @Override public void tick(){if(pending!=null&&System.currentTimeMillis()>deadline){pending=null;notice="请求超时，请重新读取；未确认的保存不会视为成功。";refresh();}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xE9101B24);g.fill(12,12,width-12,15,0xFF83D9BA);g.drawString(font,title,17,20,0xFF83D9BA,false);for(var c:cards){g.fill(c.x(),c.y(),c.x()+c.w(),c.y()+c.h(),0xF01B303D);g.fill(c.x(),c.y(),c.x()+3,c.y()+c.h(),c.color());g.drawString(font,font.plainSubstrByWidth(c.title(),c.w()-20),c.x()+10,c.y()+9,c.color(),false);g.drawString(font,font.plainSubstrByWidth(c.detail(),c.w()-20),c.x()+10,c.y()+28,0xFF9CB6C6,false);}for(var l:labels)g.drawString(font,font.plainSubstrByWidth(l.text(),span()-15),l.x(),l.y(),l.color(),false);g.drawString(font,font.plainSubstrByWidth(notice,width-120),14,height-24,0xFFE9BD70,false);super.render(g,x,y,partial);}
    private static String kindName(String k){return switch(k){case "campaigns"->"战役";case "chapters"->"章节";case "states"->"重要状态与选择";case "actors"->"跨关人物";case "items"->"剧情物品";case "tasks"->"战役任务";case "collections"->"账号收藏";case "events"->"战役事件";case "bindings"->"人物绑定";case "effects"->"执行结果";default->k;};}
    private static int accent(String k){return switch(k){case "campaigns","chapters"->0xFF72B8E0;case "items","collections"->0xFFE9BD70;case "events","effects"->0xFFBAA1E5;default->0xFF83D9BA;};}
    private static String operationName(String k){return switch(k){case "STATE"->"记录重要状态／选择";case "TASK"->"更新战役任务";case "ACTOR"->"设置跨关人物状态";case "GIVE"->"发放剧情物品";case "TAKE"->"消耗剧情物品";case "COLLECTION"->"解锁账号纪念收藏";case "CHECKPOINT"->"保存安全检查点";case "COMPLETE"->"完成章节并结算";default->k;};}
}
