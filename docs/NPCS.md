NPC 配置随关卡保存在规则目录的 `npcs.json` 中。游戏内执行 `/zombiesnpc "关卡名" list` 可以查看实际文件路径。旧关卡缺少此文件时按空 NPC 列表处理。0.2.8b 的客户端和服务端需要同时更新。

编辑需要管理员权限等级 2，并且关卡处于等待状态。普通参与者可以在对局中右键与活着的 NPC 交互。NPC 使用人形模型，皮肤填写客户端资源包中的 PNG 资源位置，例如 `story:textures/entity/guide.png`。该路径对应资源包内的 `assets/story/textures/entity/guide.png`，建议使用标准宽手臂的 64×64 玩家皮肤。所有客户端都需要安装相应资源包。

先在已有僵尸关卡内站到 NPC 的初始位置，执行以下命令。把 `关卡名` 替换为部署工具里显示的名称。

```mcfunction
/zombiesnpc "关卡名" create guide
/zombiesnpc "关卡名" guide set {"appearance":{"name":"向导","pose":"SITTING","alive":true},"dialogue":"请替我找来三颗绿宝石。"}
/zombiesnpc "关卡名" preview
```

移动到新位置后执行 `/zombiesnpc "关卡名" move guide`，即可记录当前位置和朝向。编辑后可再次执行 `preview` 更新预览。预览中的 NPC 不执行剧情和交易。开局会自动采用已保存的配置，预览会随之替换。

可用命令如下。

| 命令 | 用途 |
| --- | --- |
| `/zombiesnpc "关卡名" list` | 列出 NPC ID 和配置文件位置 |
| `/zombiesnpc "关卡名" create guide` | 在执行者当前位置创建 NPC |
| `/zombiesnpc "关卡名" remove guide` | 删除 NPC 配置 |
| `/zombiesnpc "关卡名" show guide` | 输出完整 NPC JSON |
| `/zombiesnpc "关卡名" move guide` | 把初始位置和朝向设为执行者当前位置 |
| `/zombiesnpc "关卡名" guide set <JSON>` | 合并 NPC 属性，支持一次修改多个字段 |
| `/zombiesnpc "关卡名" guide rule <JSON>` | 添加剧情规则，同 ID 时替换原规则并移至末尾 |
| `/zombiesnpc "关卡名" reload` | 验证磁盘配置并清除旧预览 |
| `/zombiesnpc "关卡名" preview` | 生成初始状态的 NPC 预览 |
| `/zombiesnpc "关卡名" clear` | 清除预览 |

`set` 对对象逐层合并，对数组整体替换。例如删除所有规则可执行 `/zombiesnpc "关卡名" guide set {"rules":[]}`。需要删除某一条规则或调整优先顺序时，可编辑文件中的 `rules` 数组，或通过 `set` 提交完整数组。NPC 和规则 ID 使用字母、数字、下划线和连字符。

以下交易规则会在玩家交出三颗绿宝石后，给予两颗钻石并打开屏障分组 1。屏障分组必须已在关卡内配置完成。

```mcfunction
/zombiesnpc "关卡名" guide rule {"id":"delivery","trigger":"INTERACT","actions":[{"kind":"TAKE_ITEM","item":"minecraft:emerald","count":3},{"kind":"GIVE_ITEM","item":"minecraft:diamond","count":2},{"kind":"OPEN_BARRIER","barrierGroup":1},{"kind":"DIALOGUE","text":"谢谢。通道已经打开。"}]}
```

每条规则每局只执行一次，由整局玩家共享完成状态。多个玩家同时交互时，只有首次成功执行会交收物品。一次交互按 `rules` 数组顺序执行首条符合条件且未完成的交互规则。物品不足或背包放不下奖励时，保留物品和规则状态，玩家可以稍后重试。所有交出物品会先一起检查、扣除，再计算奖励空间，奖励不会被用于满足同一规则的交出要求。物品 ID 和 NBT 必须完全匹配，也支持原有屏障使用的 `minecraft:物品{NBT}` 和完整 ItemStack SNBT 格式。

只交谈即可触发的剧情，使用 `INTERACT` 规则并填写 `DIALOGUE`、`SET_BLOCK` 或 `OPEN_BARRIER` 动作即可。

下面是可写入文件的完整示例。坐标需要替换为关卡范围内的实际位置，屏障分组 1 需要事先存在。达到第 14 波，或分组 1 被打开时，向导会移动并发送聊天消息。`anyCondition` 设为 `false` 时，填写的波次和全部屏障条件需要同时满足。

```json
{
  "version": 1,
  "npcs": [
    {
      "id": "guide",
      "appearance": {
        "name": "向导",
        "skin": "minecraft:textures/entity/player/wide/steve.png",
        "pose": "SITTING",
        "alive": true,
        "position": { "x": 10.5, "y": 64, "z": 20.5, "yaw": 90 }
      },
      "dialogue": "你准备好了吗？",
      "rules": [
        {
          "id": "relocate",
          "trigger": "AUTO",
          "minWave": 14,
          "openedBarriers": [1],
          "anyCondition": true,
          "requires": [],
          "actions": [
            {
              "kind": "NPC",
              "dialogue": "赶快前往出口。",
              "appearance": {
                "pose": "STANDING",
                "position": { "x": 25.5, "y": 64, "z": 30.5, "yaw": 180 }
              }
            },
            { "kind": "DIALOGUE", "text": "我到出口了，快来！" }
          ]
        }
      ]
    }
  ]
}
```

规则的 `trigger` 可取 `INTERACT` 或 `AUTO`。`minWave` 默认 0，表示没有波次条件。`openedBarriers` 是需要打开的屏障分组列表。`requires` 是需要先完成的规则，填写 `NPC_ID.规则_ID`，例如 `["guide.delivery"]`，也支持引用其他 NPC 的规则。

通过前置规则可以编写多阶段交互，例如先交出物品，之后才出现另一段对话或开启新的交易。`AUTO` 在波次间歇和战斗阶段检查条件，支持由其他 NPC 的交互推进剧情。对话会发送给当前房间的玩家。

动作可按需组合。

| `kind` | 所需字段及效果 |
| --- | --- |
| `NPC` | `appearance` 修改名字、皮肤、位置、朝向、姿态或生死状态，`dialogue` 修改之后交互时的默认对话，省略的属性沿用当前值 |
| `DIALOGUE` | `text` 发到房间聊天栏 |
| `TAKE_ITEM` | `item`、`count` 从交互玩家背包交出物品 |
| `GIVE_ITEM` | `item`、`count` 向交互玩家背包给予物品 |
| `SET_BLOCK` | `position`、`block` 在地图内设置方块，例如 `"block":"minecraft:oak_door[open=true]"` |
| `OPEN_BARRIER` | `barrierGroup` 免费打开已有屏障分组，并应用该组的怪物和玩家出生点策略 |

`NPC` 和 `DIALOGUE` 动作可填写 `target` 指定另一个 NPC 的 ID，省略时使用当前 NPC。姿态支持 `STANDING`、`SITTING`、`LYING`。`alive:false` 显示为倒地遗体并停止接受交互，后续 `NPC` 动作可将其设回存活。`AUTO` 动作可以在 NPC 死亡后继续推进剧情。物品交收动作只能用于 `INTERACT`。

`SET_BLOCK` 的位置限制在同一关卡、同一维度，不能覆盖关卡已配置的屏障、窗口或交互设备。新旧方块均须为静态建筑方块、空气或门类，例如石头、木板、玻璃、台阶、楼梯、围墙、栅栏、门与活板门。流体、重力方块、火、红石设备、随机刻变化方块和带方块实体数据的容器等会被拒绝。方块动作抑制相邻方块连锁更新，以保证每局可恢复。它按指定坐标设置一个方块。双格门需要为上下两格分别配置动作，也可以直接使用 `OPEN_BARRIER` 打开关卡屏障。方块状态采用原版命令的格式。

对局结束、管理员结束、服务器正常关闭和开局回滚会清除 NPC、触发记录及区块加载票据，并恢复剧情修改过的方块。再次开局时重新读取已保存的 NPC 初始配置。原始方块恢复信息保存在世界数据中，服务器启动时会尝试恢复上次留下的修改。用于任务的 NPC 由剧情控制状态，并具有伤害保护。

自动化检查可在 Java 17 环境运行。

```powershell
.\gradlew.bat runGameTestServer -PnpcGameTests -PgameTestRunDirectory=build/gametest/npc
.\gradlew.bat build
```
