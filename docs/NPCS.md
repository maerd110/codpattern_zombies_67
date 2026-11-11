0.2.12b 的 NPC 编辑集成在僵尸部署工具中，并支持按时间顺序执行剧情。客户端和服务端需要同时更新。使用管理员账号手持僵尸部署工具，按 Ctrl+右键打开部署界面，选定已有关卡后点击顶部的“NPC 剧情”。如果当前关卡有部署修改，先点击部署工具的保存按钮。

1. 在“基本信息”页面点击“新增 NPC”。设置显示名称、聊天名字、名字与正文颜色、皮肤资源、站立／坐着／躺下、存活状态、无敌开关和对话。每句对话间隔默认 1 秒，支持小数，0 表示连续发送。“使用玩家当前位置和朝向”可以直接记录所在位置。
2. 在“触发规则”页面新增规则，选择玩家交互或自动检查，设置最低波次，勾选屏障条件和需要先完成的剧情规则。“波次或任一屏障满足即可”可以实现到第 14 波或开启指定屏障后触发，关闭时需要全部已填写条件满足。
3. 在“执行动作”页面为当前规则新增动作。页面顶部依次为“新增执行动作”“当前规则”“当前动作序号”。支持发送对话、交出或领取物品、改变 NPC、放置方块和开启屏障。类型和目标等选项可以点击选择，选项较多时提供搜索。点击“从背包中选择物品”可从包含主副手的背包图标列表中选取，也保留手持物品读取。放置方块动作中可以新增多个放置位置，每个位置单独设置坐标和方块，并用“选择部署方块”从带图标、支持搜索的列表中选择。
4. “改变 NPC 设置”动作中的每项属性都有“保持／启用修改”按钮。可只改变某些属性，例如只修改位置和默认对话。规则和动作可以上移、下移或删除。
5. 点击“保存 NPC 配置”，或点击“保存并预览”直接查看场景。“收起查看地图”保留当前编辑内容，Ctrl+右键可以继续编辑。返回部署工具或重新读取时，未保存修改会显示确认提示。

规则中的“动作默认间隔”默认 1 秒，从上一个动作完成后开始等待。每个动作可以单独启用“执行此动作前等待”并填写秒数，覆盖该规则的默认间隔，第一步默认立即开始。对话会按行发送，对话动作的最后一句发送后才开始计算下一动作的间隔。“基本信息”里的对话间隔同时作为对话动作的默认值，单个对话动作也可覆盖它。所有时间值支持 0 到 3600 秒，并按服务器游戏刻执行。旧配置中明确保存的间隔（包括 0）继续生效，缺少间隔字段时使用 1 秒。

“聊天名字”只用于聊天栏，留空时使用 NPC 的显示名称。名字和对话正文可以分别输入 `#RRGGBB` 颜色，例如 `#ffaa00`，也可以点击对应的“预设颜色”从 16 种颜色中选择。基本对话和发送聊天对话动作都使用该 NPC 当前的名字与颜色。“改变 NPC 设置”动作也可修改这些属性，使剧情中途切换发言名字和颜色。未设置颜色时使用白色。

在触发规则中启用“房间玩家持有物品条件”，选择物品与数量。该条件检查同一房间内任一参与玩家的背包、快捷栏和主副手，单个玩家多个格子的数量可以合并。满足数量的检查本身保留原物品。该物品条件与规则已配置的其他条件共同生效。

无敌开关默认开启，阻止普通攻击、创造模式玩家攻击和原版可绕过无敌标记的伤害。关闭后 NPC 可以受到伤害，被杀死后保留倒地遗体并停止接受新交互，剧情动作可以复活它。预览 NPC 具有伤害保护。每局重新生成时恢复保存的初始状态和无敌设置。

NPC 编号和规则编号可以保留自动生成值。修改编号时，编辑器会同步更新已有目标引用和前置规则。删除被引用的 NPC 或规则后，可以在其他规则的前置条件中取消对应勾选，并调整动作的目标，再保存。每次保存都会核对服务器上的配置版本，遇到其他编辑者修改时会保留当前工作副本并提示冲突。

NPC 配置随关卡保存在规则目录的 `npcs.json` 中。游戏内执行 `/zombiesnpc "关卡名" list` 可以查看实际文件路径。旧关卡缺少此文件时按空 NPC 列表处理。下面也保留了命令和文件格式，供批量配置使用。

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

每条规则每局执行一次，由整局玩家共享完成状态。一次交互启动首条符合条件且未完成的交互规则，规则中的动作按界面顺序连续执行。当前序列完成后，另一条交互规则需要再次右键启动。当前序列运行或暂停时，重复右键会保持当前进度。

每个物品动作单独检查并提交。例如先对话、给予一颗绿宝石、再交出一颗绿宝石可以顺序完成。给予的物品只进入背包的 27 个格子，会优先合并同类堆叠，不填入快捷栏或主副手。背包满时，即使快捷栏有空位，也会暂停领取。执行到物品不足或奖励空间不足的动作时，会暂停在该动作，约每秒检查一次，条件满足后自动继续。已执行的对话、奖励和地图变化保留，后续动作继续按序进行。物品交收始终针对启动该规则的玩家，该玩家离线、离开房间或死亡时序列暂停。重新满足参与条件后继续，结束对局会取消整个序列。物品 ID 和 NBT 必须完全匹配，也支持 `minecraft:物品{NBT}` 和完整 ItemStack SNBT。

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
        "chatName": "无线电中的向导",
        "nameColor": "gold",
        "textColor": "#ffffff",
        "skin": "minecraft:textures/entity/player/wide/steve.png",
        "pose": "SITTING",
        "alive": true,
        "invulnerable": true,
        "position": { "x": 10.5, "y": 64, "z": 20.5, "yaw": 90 }
      },
      "dialogue": "你准备好了吗？",
      "dialogueIntervalSeconds": 1,
      "rules": [
        {
          "id": "relocate",
          "trigger": "AUTO",
          "actionIntervalSeconds": 0.5,
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

`SET_BLOCK` 的位置限制在同一关卡、同一维度，不能覆盖关卡已配置的屏障、窗口或交互设备。新旧方块均须为静态建筑方块、空气或门类，例如石头、木板、玻璃、台阶、楼梯、围墙、栅栏、门与活板门。流体、重力方块、火、红石设备、随机刻变化方块和带方块实体数据的容器等会被拒绝。方块动作抑制相邻方块连锁更新，以保证每局可恢复。一个动作可以保存最多 256 个放置位置，每个位置使用独立的方块状态，整批检查通过后统一执行。旧的单位置配置可继续读取。双格门需要为上下两格分别配置动作，也可以直接使用 `OPEN_BARRIER` 打开关卡屏障。方块状态采用原版命令的格式。

对局结束、管理员结束、服务器正常关闭和开局回滚会清除 NPC、触发记录及区块加载票据，并恢复剧情修改过的方块。再次开局时重新读取已保存的 NPC 初始配置。原始方块恢复信息保存在世界数据中，服务器启动时会尝试恢复上次留下的修改。NPC 的状态由保存的常态设置、剧情动作与实际伤害共同决定。

自动化检查可在 Java 17 环境运行。

```powershell
.\gradlew.bat runGameTestServer -PnpcGameTests -PgameTestRunDirectory=build/gametest/npc
.\gradlew.bat build
```

客户端界面渲染检查可以运行 `.\gradlew.bat runClient -PnpcClientPreview`，结果和截图写入 `build/verification/npc-client`。检查使用隔离客户端，完成后自动退出。

新增字段兼容旧配置。NPC 的 `dialogueIntervalSeconds` 默认为 1，`appearance.invulnerable` 缺省时保持原有无敌状态。规则的 `actionIntervalSeconds` 默认 1，`requiredItem` 默认为空，`requiredCount` 默认 1。动作的 `delaySeconds` 和 `dialogueIntervalSeconds` 缺省时继承默认值，填写 0 时立即执行。`appearance.chatName` 为空时跟随显示名称，`nameColor` 和 `textColor` 支持原版英文颜色名或 `#RRGGBB`，动作省略这些字段时沿用当前值。`SET_BLOCK` 可使用 `placements` 数组，每项为 `{ "position": { "x": 1, "y": 64, "z": 2, "yaw": 0 }, "block": "minecraft:stone" }`。
