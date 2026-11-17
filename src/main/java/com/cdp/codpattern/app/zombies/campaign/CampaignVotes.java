package com.cdp.codpattern.app.zombies.campaign;

import com.cdp.codpattern.compat.fpsmatch.map.zombies.ZombiesMap;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;

/** Electorate is frozen at opening; offline/leaving players cannot block online teammates. */
public final class CampaignVotes {
    public record Option(String id,String text){}
    public static final class Vote {
        final UUID token=UUID.randomUUID();final ZombiesMap map;final String text;final List<Option> options;
        final Set<UUID> electorate=new HashSet<>();final Map<UUID,String> ballots=new HashMap<>();
        final Consumer<String> resolve;Set<String> finalists;long deadline;int round;
        Vote(ZombiesMap map,String text,List<Option> options,Consumer<String> resolve){this.map=map;this.text=text;this.options=List.copyOf(options);this.resolve=resolve;for(var p:map.npcPlayers())if(connected(p))electorate.add(p.getUUID());deadline=map.getServerLevel().getGameTime()+600;}
    }
    private static final Map<ZombiesMap,Vote> VOTES=new IdentityHashMap<>();
    private CampaignVotes(){}
    public static boolean connected(ServerPlayer p){return p.connection!=null&&!p.hasDisconnected()&&p.getServer().getPlayerList().getPlayer(p.getUUID())==p;}
    public static boolean active(ZombiesMap map){return VOTES.containsKey(map);}
    public static void open(ZombiesMap map,String text,List<Option> options,Consumer<String> resolve){if(VOTES.containsKey(map))throw new IllegalArgumentException("本房间已有剧情投票，请完成后再继续");if(options.isEmpty())throw new IllegalArgumentException("没有可投票的选项");VOTES.put(map,new Vote(map,text,options,resolve));}
    private static Set<UUID> online(Vote v){var ids=new HashSet<UUID>();for(var p:v.map.npcPlayers())if(connected(p)&&v.electorate.contains(p.getUUID()))ids.add(p.getUUID());return ids;}
    public static void cast(ServerPlayer player,String token,String option){var v=VOTES.values().stream().filter(x->x.token.toString().equals(token)&&x.map.npcPlayers().contains(player)).findFirst().orElseThrow(()->new IllegalArgumentException("投票已结束"));if(!online(v).contains(player.getUUID())||v.options.stream().noneMatch(o->o.id().equals(option))||v.finalists!=null&&!v.finalists.contains(option))throw new IllegalArgumentException("无法选择该选项");if(v.round==2&&!leader(v).equals(player.getUUID()))throw new IllegalArgumentException("平票由房主决定");v.ballots.put(player.getUUID(),option);if(v.round==2)finish(v,option);else tally(v,false);}
    public static void tick(){for(var v:List.copyOf(VOTES.values())){if(!v.map.currentPhase().isRoundRunning()){VOTES.remove(v.map);continue;}if(v.map.getServerLevel().getGameTime()>=v.deadline)tally(v,true);}}
    private static UUID leader(Vote v){var s=CampaignRuntime.session(v.map);if(s!=null&&online(v).contains(s.host))return s.host;return v.map.npcPlayers().stream().filter(p->online(v).contains(p.getUUID())).min(Comparator.comparingInt((ServerPlayer p)->p.latency).thenComparing(p->p.getUUID().toString())).map(ServerPlayer::getUUID).orElse(new UUID(0,0));}
    private static void tally(Vote v,boolean expired){var online=online(v);if(online.isEmpty()){v.deadline=v.map.getServerLevel().getGameTime()+20;return;}if(v.round==2)return;if(!expired&&!v.ballots.keySet().containsAll(online))return;
        var totals=new LinkedHashMap<String,Integer>();for(var o:v.options)if(v.finalists==null||v.finalists.contains(o.id()))totals.put(o.id(),0);for(var id:online){String choice=v.ballots.get(id);if(totals.containsKey(choice))totals.merge(choice,1,Integer::sum);}int best=totals.values().stream().mapToInt(i->i).max().orElse(0);var winners=totals.entrySet().stream().filter(e->e.getValue()==best).map(Map.Entry::getKey).toList();if(winners.size()==1){finish(v,winners.get(0));return;}v.finalists=new HashSet<>(winners);v.ballots.clear();v.round++;v.deadline=v.map.getServerLevel().getGameTime()+600;
    }
    private static void finish(Vote v,String result){if(VOTES.remove(v.map,v))v.resolve.accept(result);}
    public static JsonObject view(ServerPlayer p){for(var v:VOTES.values())if(v.electorate.contains(p.getUUID())&&online(v).contains(p.getUUID())){var o=new JsonObject();o.addProperty("token",v.token.toString());o.addProperty("text",v.text);o.addProperty("round",v.round);o.addProperty("seconds",Math.max(0,(v.deadline-v.map.getServerLevel().getGameTime()+19)/20));o.addProperty("decider",leader(v).equals(p.getUUID()));o.addProperty("selected",v.ballots.getOrDefault(p.getUUID(),""));var choices=new JsonArray();for(var option:v.options)if(v.finalists==null||v.finalists.contains(option.id())){var row=new JsonObject();row.addProperty("id",option.id());row.addProperty("name",option.text());choices.add(row);}o.add("options",choices);return o;}return new JsonObject();}
    public static void clear(ZombiesMap map){VOTES.remove(map);}
    public static void stop(net.minecraft.server.MinecraftServer server){VOTES.keySet().removeIf(m->m.getServerLevel().getServer()==server);}
}
