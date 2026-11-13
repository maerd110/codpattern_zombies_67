package com.cdp.codpattern.client.gui.screen.zombies.deploy;

import com.cdp.codpattern.app.zombies.deploy.ZombiesRulesEditorService;
import com.cdp.codpattern.app.zombies.npc.*;
import com.cdp.codpattern.client.zombies.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** Hierarchical cards edit the original JSON structure, with no separately maintained pool format. */
public final class ZombiesRulesScreen extends Screen {
    private static ZombiesRulesScreen active;
    private final Screen parent;private final String map;
    private String kind="wall",revision="",saved="",message="",selected="",page="root";
    private JsonObject data,group,pool,gun,entry;private List<String> guns=List.of();
    private List<ZombiesRulesEditorService.BarrierBinding> barriers=List.of();
    private Map<String,com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig.Rarity> rarityStyles=Map.of();
    private int focusGroup,focusEntry;
    private List<String> rarityIds=List.of("common","rare","epic","legendary");private boolean editable,changed;private UUID pending;private long deadline;private int scroll;
    private final List<Consumer<Integer>> rows=new ArrayList<>();private final List<Runnable> labels=new ArrayList<>();private GuiGraphics graphics;
    private static final List<String> KINDS=List.of("barriers","wall","box","rules");
    public ZombiesRulesScreen(Screen parent,String map){super(Component.literal("武器池与品阶"));this.parent=parent;this.map=map;active=this;}
    public ZombiesRulesScreen(Screen parent,String map,int group,int entry){this(parent,map);kind="barriers";focusGroup=group;focusEntry=entry;}
    private static String str(JsonObject o,String key,String fallback){return o!=null&&o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private static String title(String kind){return switch(kind){case "wall"->"墙枪武器池";case "box"->"神秘箱武器池";case "rules"->"品阶与强化";default->"屏障规则";};}
    private boolean canEdit(){return editable&&pending==null;}
    private boolean dirty(){return data!=null&&!data.toString().equals(saved);}
    public void load(){request("config_load");}
    private void request(String operation){try{var body=new JsonObject();body.addProperty("kind",kind);if(operation.equals("config_save")){body.addProperty("revision",revision);body.add("data",numbers(data));}ZombiesNpcEditorWire.checkRequest(body.toString());pending=UUID.randomUUID();deadline=System.currentTimeMillis()+20000;ZombiesNpcDialogueNetwork.edit(new ZombiesNpcDialogueNetwork.EditorRequest(pending,map,operation,body.toString()));message="正在读取／保存…";}catch(RuntimeException e){pending=null;message=e.getMessage();}refresh();}
    public static void accept(ZombiesNpcDialogueNetwork.EditorReply packet){
        if(active==null||!packet.token().equals(active.pending))return;var s=active;s.pending=null;
        try{var reply=ZombiesNpcRepository.GSON.fromJson(packet.payload(),ZombiesRulesEditorService.Snapshot.class);if(!reply.map().equals(s.map)||!reply.kind().equals(s.kind))throw new IllegalArgumentException("响应不匹配");if(!reply.error().isBlank())throw new IllegalArgumentException(reply.error());s.data=reply.data();s.saved=s.data.toString();s.revision=reply.revision();s.guns=reply.guns();s.rarityIds=reply.rarityIds()==null?s.rarityIds:reply.rarityIds();s.barriers=reply.barriers()==null?List.of():reply.barriers();var styles=new HashMap<String,com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig.Rarity>();if(reply.rarities()!=null)for(var style:reply.rarities())styles.put(style.getId(),style);s.rarityStyles=styles;s.editable=reply.editable();s.page="root";s.scroll=0;if(s.kind.equals("barriers")&&s.focusGroup>0)s.openBarrier(s.focusGroup,s.focusEntry);s.message="配置已读取／保存；开局使用保存后的配置。";}catch(RuntimeException e){s.message=e.getMessage();}s.refresh();
    }
    public static JsonObject numbers(JsonObject raw){var copy=raw.deepCopy();convert(copy);return copy;}
    private static final Set<String> NUMBERS=Set.of("schemaVersion","refreshIntervalWaves","cost","price","maxUpgradeLevel","initialWeight","weightDeltaPerRefresh","minWeight","maxWeight","weight","damageMultiplier");
    private static void convert(JsonElement value){if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet()){if(NUMBERS.contains(e.getKey())&&e.getValue().isJsonPrimitive()&&e.getValue().getAsJsonPrimitive().isString())try{e.setValue(new JsonPrimitive(new java.math.BigDecimal(e.getValue().getAsString())));}catch(NumberFormatException ex){throw new IllegalArgumentException(e.getKey()+" 需要填写有效数字");}else convert(e.getValue());}else if(value.isJsonArray())value.getAsJsonArray().forEach(ZombiesRulesScreen::convert);}
    private void refresh(){if(minecraft!=null)rebuildWidgets();}
    private Button button(String text,int x,int y,int w,Runnable action,boolean enabled){var b=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(text,Math.max(8,w-8))),v->{try{action.run();}catch(RuntimeException e){message=e.getMessage();refresh();}}).bounds(x,y,w,20).build());b.active=enabled;b.setTooltip(Tooltip.create(Component.literal(text)));return b;}
    @Override protected void init(){
        rows.clear();labels.clear();int tw=(width-30)/4;
        for(int i=0;i<4;i++){String target=KINDS.get(i);button((kind.equals(target)?"● ":"")+title(target),12+i*(tw+2),30,tw,()->discard(()->{kind=target;data=null;page="root";scroll=0;load();}),pending==null);}
        button(page.equals("root")?"返回部署工具":"‹ 返回上级",12,56,115,this::onClose,pending==null);
        button("保存当前配置",width-132,56,120,()->{request("config_save");changed=true;},canEdit()&&data!=null);
        int bw=(width-28)/2;button("重新读取",12,height-28,bw,()->discard(this::load),pending==null);button("返回部署工具",16+bw,height-28,bw,()->discard(this::leave),pending==null);
        if(data==null)return;
        switch(page){case "root"->root();case "pool"->pool();case "guns"->gunCards();case "gun"->gun();case "group"->group();case "entry"->entry();case "upgrades"->upgrades();default->throw new IllegalStateException();}
        int count=Math.max(1,(height-133)/26);scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-count)));for(int i=scroll;i<Math.min(rows.size(),scroll+count);i++)rows.get(i).accept(86+(i-scroll)*26);
    }
    private void go(String target){page=target;scroll=0;refresh();}
    private void text(String value){rows.add(y->labels.add(()->graphics.drawString(font,font.plainSubstrByWidth(value,width-28),14,y+6,0xFF9DB5C4,false)));}
    private void action(String caption,Runnable run){rows.add(y->button(caption,12,y,width-24,run,canEdit()));}
    private void field(String label,JsonObject object,String key,String fallback){rows.add(y->{int split=Math.min(185,width/2);labels.add(()->graphics.drawString(font,font.plainSubstrByWidth(label,split-20),14,y+6,0xFFE8EFED,false));var box=addRenderableWidget(new EditBox(font,split,y,width-split-12,20,Component.literal(label)));box.setMaxLength(16384);box.setValue(str(object,key,fallback));box.setEditable(canEdit());box.setResponder(v->object.addProperty(key,v));});}
    private int color(){return pool==null?0xFF83D9BA:rarityColor(str(pool,kind.equals("wall")?"rarityId":"id",""));}
    private int rarityColor(String id){if(kind.equals("rules")&&data!=null)for(var raw:pools()){var p=raw.getAsJsonObject();if(str(p,"id","").equals(id)){String value=str(p,"color",com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig.defaultColor(id));if(com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig.validColor(value))return 0xFF000000|Integer.parseInt(value.substring(1),16);}}var style=rarityStyles.get(id);return style!=null?style.argbColor():0xFF000000|Integer.parseInt(com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig.defaultColor(id).substring(1),16);}

    private String rarityName(String id){return ZombiesRarityDisplay.fromRarityId(id).map(ZombiesRarityDisplay.Entry::label).orElse(id);}
    private void card(String name,String detail,int color,ItemStack stack,Runnable click){rows.add(y->{var b=addRenderableWidget(new Button(12,y,width-24,23,Component.empty(),ignored->{click.run();},narration->narration.get()){@Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),isHoveredOrFocused()?0xFF294354:0xFF1A2C3A);g.fill(getX(),getY(),getX()+3,getY()+getHeight(),color);int x=getX()+9;if(!stack.isEmpty()){g.renderItem(stack,x,getY()+3);x+=23;}g.drawString(font,font.plainSubstrByWidth(name,Math.max(8,getWidth()/2-(x-getX())-8)),x,getY()+7,color,false);g.drawString(font,font.plainSubstrByWidth(detail,getWidth()/2-16),getX()+getWidth()/2,getY()+7,0xFFAFC5D1,false);}});b.active=pending==null;b.setTooltip(Tooltip.create(Component.literal(name+"\n"+detail)));});}
    private JsonArray pools(){return data.getAsJsonArray(kind.equals("rules")?"rarities":"rarityPools");}
    private void root(){
        text("关卡："+map+" · "+ZombiesRulesEditorService.filename(kind));
        if(kind.equals("barriers")){text("先在地图框选创建屏障，再从原设置中编辑参数。这里只列出已部署的屏障。");for(var binding:barriers)card(binding.name(),"分组 "+binding.group()+" / 入口 "+binding.entry(),0xFF83D9BA,ItemStack.EMPTY,()->openBarrier(binding.group(),binding.entry()));if(barriers.isEmpty())text("尚未部署屏障，请返回物件部署，框选创建屏障。");return;}

        if(!kind.equals("rules")){field("每几波刷新一次",data,"refreshIntervalWaves","5");if(kind.equals("box"))field("抽奖价格",data,"cost","950");text("概率由权重计算；权重为 0 的枪不刷出，空品阶不参与抽取。");}else action("编辑强化等级与价格",()->go("upgrades"));
        for(var value:pools()){var p=value.getAsJsonObject();String id=str(p,kind.equals("wall")?"rarityId":"id","");int color=rarityColor(id);card(rarityName(id),kind.equals("rules")?"伤害倍率 "+str(p,"damageMultiplier","1"):p.getAsJsonArray("guns").size()+" 把枪 · 初始占比 "+percent(p),color,ItemStack.EMPTY,()->{pool=p;go("pool");});}
        if(kind.equals("rules"))action("新增自定义品阶",()->{var p=new JsonObject();int i=1;while(hasId("custom_"+i))i++;p.addProperty("id","custom_"+i);p.addProperty("damageMultiplier",1);p.addProperty("color","#83D9BA");pools().add(p);pool=p;go("pool");});
        else action("添加品阶",()->{var options=new ArrayList<>(rarityIds);minecraft.setScreen(new ZombiesNpcChoiceScreen(this,"选择品阶",options,"",this::rarityName,id->{if(hasId(id)){message="该品阶已经存在";return;}var p=new JsonObject();p.addProperty(kind.equals("wall")?"rarityId":"id",id);p.addProperty("initialWeight",1);p.addProperty("weightDeltaPerRefresh",0);p.addProperty("minWeight",0);p.addProperty("maxWeight",100);if(kind.equals("wall"))p.addProperty("price",500);p.add("guns",new JsonArray());pools().add(p);pool=p;go("pool");}));});
    }
    private boolean hasId(String id){return pools().asList().stream().anyMatch(p->str(p.getAsJsonObject(),kind.equals("wall")?"rarityId":"id","").equals(id));}
    private static double numeric(JsonObject o,String key){try{return o.get(key).getAsDouble();}catch(Exception e){return 0;}}
    private String percent(JsonObject p){double sum=0,own=0;for(var raw:pools()){var candidate=raw.getAsJsonObject();if(candidate.getAsJsonArray("guns").asList().stream().noneMatch(g->numeric(g.getAsJsonObject(),"weight")>0))continue;double w=Math.max(numeric(candidate,"minWeight"),Math.min(numeric(candidate,"maxWeight"),numeric(candidate,"initialWeight")));sum+=w;if(candidate==p)own=w;}return String.format(Locale.ROOT,"%.1f%%",sum>0?100*own/sum:0);}
    private void pool(){String key=kind.equals("wall")?"rarityId":"id";text("当前品阶："+rarityName(str(pool,key,"")));if(kind.equals("rules")){field("品阶编号",pool,"id","");field("伤害倍率",pool,"damageMultiplier","1");card("品阶颜色",str(pool,"color",com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig.defaultColor(str(pool,"id",""))),color(),ItemStack.EMPTY,()->{if(canEdit())minecraft.setScreen(new ZombiesRarityColorScreen(this,str(pool,"color",com.cdp.codpattern.config.zombies.ZombiesWeaponRulesConfig.defaultColor(str(pool,"id",""))),value->{pool.addProperty("color",value);refresh();}));});}else{for(String field:List.of("initialWeight","weightDeltaPerRefresh","minWeight","maxWeight"))field(switch(field){case "initialWeight"->"初始权重";case "weightDeltaPerRefresh"->"每次刷新的权重增量";case "minWeight"->"权重下限";default->"权重上限";},pool,field,"0");if(kind.equals("wall"))field("此品阶墙枪价格",pool,"price","500");action("编辑此品阶枪械 · "+pool.getAsJsonArray("guns").size()+" 把",()->go("guns"));}action("删除此品阶",()->confirm(()->{pools().remove(pool);go("root");}));}
    private void gunCards(){text("品阶："+rarityName(str(pool,kind.equals("wall")?"rarityId":"id","")));var items=pool.getAsJsonArray("guns");double total=items.asList().stream().mapToDouble(v->Math.max(0,numeric(v.getAsJsonObject(),"weight"))).sum();for(var raw:items){var g=raw.getAsJsonObject();String id=str(g,"gunId","");card(ZombiesGunNames.displayId(id).getString(),"权重 "+str(g,"weight","0")+String.format(Locale.ROOT," · 品阶内 %.1f%%",total>0?100*Math.max(0,numeric(g,"weight"))/total:0),color(),stack(id),()->{gun=g;go("gun");});}action("从所有枪包添加枪械",()->pickGun(id->{if(items.asList().stream().anyMatch(v->str(v.getAsJsonObject(),"gunId","").equals(id))){message="此品阶已有这把枪";return;}gun=new JsonObject();gun.addProperty("gunId",id);gun.addProperty("weight",100);items.add(gun);go("gun");}));}
    private ItemStack stack(String id){var key=net.minecraft.resources.ResourceLocation.tryParse(id);if(key==null)return ItemStack.EMPTY;var builder=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(key);var built=builder.build();return built.isEmpty()?builder.forceBuild():built;}
    private void pickGun(Consumer<String> accept){var entries=guns.stream().map(id->new ZombiesNpcStackPickerScreen.Entry(stack(id),id,ZombiesGunNames.displayId(id).getString(),1)).toList();minecraft.setScreen(new ZombiesNpcStackPickerScreen(this,"选择枪械 · "+entries.size()+" 把",entries,e->accept.accept(e.value()),color()).gunPacks());}
    private void gun(){text(ZombiesGunNames.displayId(str(gun,"gunId","")).getString()+" · "+str(gun,"gunId",""));field("枪械权重（0 不出现）",gun,"weight","100");action("更换枪械",()->pickGun(id->{if(pool.getAsJsonArray("guns").asList().stream().anyMatch(e->e!=gun&&str(e.getAsJsonObject(),"gunId","").equals(id)))throw new IllegalArgumentException("此品阶已有这把枪");gun.addProperty("gunId",id);refresh();}));action("删除此枪械",()->confirm(()->{pool.getAsJsonArray("guns").remove(gun);go("guns");}));}
    private void openBarrier(int groupId,int entryId){
        if(groupId<=0||entryId<=0){message="请先在屏障原设置填写正整数的分组与规则编号，并保存。";refresh();return;}
        if(barriers.stream().noneMatch(b->b.group()==groupId&&b.entry()==entryId)){message="屏障已变化，请重新读取。";refresh();return;}
        var groups=data.getAsJsonObject("groups");selected=Integer.toString(groupId);group=groups.getAsJsonObject(selected);
        if(group==null){if(!canEdit())return;group=new JsonObject();group.add("entries",new JsonObject());groups.add(selected,group);}
        var entries=group.getAsJsonObject("entries");entry=entries.getAsJsonObject(Integer.toString(entryId));
        if(entry==null){if(!canEdit())return;entry=new JsonObject();entry.addProperty("cost",0);entry.addProperty("requiredItem","");entries.add(Integer.toString(entryId),entry);}
        go("entry");
    }
    private void group(){
        text("已部署屏障 · 分组 "+selected+" 的联动效果（同组入口共享）");
        for(String k:List.of("spawnGroupChanges","playerSpawnGroupChanges")){if(!group.has(k))group.add(k,JsonParser.parseString("{\"enable\":[],\"disable\":[]}"));var changes=group.getAsJsonObject(k);text(k.equals("spawnGroupChanges")?"开门后刷怪组变化":"开门后玩家出生组变化");ids("启用组（逗号分隔）",changes,"enable");ids("停用组（逗号分隔）",changes,"disable");}
    }
    private void ids(String label,JsonObject target,String key){if(!target.has(key))target.add(key,new JsonArray());rows.add(y->{int split=Math.min(185,width/2);labels.add(()->graphics.drawString(font,font.plainSubstrByWidth(label,split-20),14,y+6,0xFFE8EFED,false));var box=addRenderableWidget(new EditBox(font,split,y,width-split-12,20,Component.literal(label)));String value=target.get(key).isJsonArray()?String.join(",",target.getAsJsonArray(key).asList().stream().map(JsonElement::getAsString).toList()):target.get(key).getAsString();box.setValue(value);box.setEditable(canEdit());box.setResponder(v->{try{var list=new JsonArray();if(!v.isBlank())for(String id:v.split(",",-1))list.add(Integer.parseInt(id.trim()));target.add(key,list);}catch(RuntimeException e){target.addProperty(key,v);}});});}
    private void entry(){
        String entryId=group.getAsJsonObject("entries").entrySet().stream().filter(e->e.getValue()==entry).map(Map.Entry::getKey).findFirst().orElse("");
        text("已部署屏障 · 分组 "+selected+" / 规则编号 "+entryId);
        field("点数价格",entry,"cost","0");field("钥匙物品 / 完整 NBT（可空）",entry,"requiredItem","");action("从背包选择钥匙",this::pickKey);action("编辑开门后刷怪／玩家出生组变化",()->go("group"));text("同分组、同规则编号的屏障共享这些参数；分组与编号在原物件设置修改。");
    }
    private void upgrades(){var upgrades=data.getAsJsonObject("upgrades");field("最大强化等级",upgrades,"maxUpgradeLevel","2");var levels=upgrades.getAsJsonObject("levels");for(var e:levels.entrySet()){text("强化等级 "+e.getKey());field("价格",e.getValue().getAsJsonObject(),"price","0");field("伤害倍率",e.getValue().getAsJsonObject(),"damageMultiplier","1");}action("新增下一级参数",()->{int i=1;while(levels.has(Integer.toString(i)))i++;levels.add(Integer.toString(i),JsonParser.parseString("{\"price\":0,\"damageMultiplier\":1}"));refresh();});text("最大等级之外的参数会保留；只有 1 至最大等级生效。");}
    private void pickKey(){if(minecraft.player==null)return;var choices=new ArrayList<ZombiesNpcStackPickerScreen.Entry>();var inv=minecraft.player.getInventory();for(int i=0;i<inv.getContainerSize();i++){var stack=inv.getItem(i);if(!stack.isEmpty())choices.add(new ZombiesNpcStackPickerScreen.Entry(stack.copy(),stack.save(new net.minecraft.nbt.CompoundTag()).toString(),stack.getHoverName().getString(),1));}minecraft.setScreen(new ZombiesNpcStackPickerScreen(this,"选择钥匙（保留完整 NBT）",choices,chosen->{entry.addProperty("requiredItem",chosen.value());refresh();}));}
    private void confirm(Runnable action){minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)action.run();},Component.literal("删除此条目？"),Component.literal("引用它的屏障或品阶需要同步调整。")));}
    private void discard(Runnable action){if(dirty())minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)action.run();},Component.literal("放弃当前未保存的修改？"),Component.empty()));else action.run();}
    private void leave(){active=null;minecraft.setScreen(parent);if(changed&&parent instanceof ZombiesDeployToolScreen tool)tool.refreshRules();}
    @Override public void onClose(){if(pending!=null)return;if(page.equals("root"))discard(this::leave);else go(switch(page){case "gun"->"guns";case "guns"->"pool";case "group"->"entry";case "entry"->"root";default->"root";});}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll+=amount>0?-2:2;refresh();return true;}
    @Override public void tick(){if(pending!=null&&System.currentTimeMillis()>deadline){pending=null;message="请求超时，工作副本保留，请重试。";refresh();}for(var child:children())if(child instanceof EditBox box)box.tick();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){graphics=g;g.fill(0,0,width,height,0xF0101923);g.drawString(font,title.getString()+" · "+font.plainSubstrByWidth(map,width/3)+(dirty()?" *":""),12,10,0xFF83D9BA,false);for(var label:labels)label.run();int visible=Math.max(1,(height-133)/26);if(rows.size()>visible){int span=Math.max(1,height-136),thumb=Math.max(8,span*visible/rows.size()),offset=(span-thumb)*scroll/Math.max(1,rows.size()-visible);g.fill(width-5,86+offset,width-3,86+offset+thumb,0xFF83D9BA);}g.drawString(font,font.plainSubstrByWidth(message==null?"":message,width-24),12,height-43,0xFFFFCD78,false);super.render(g,x,y,partial);}
}
