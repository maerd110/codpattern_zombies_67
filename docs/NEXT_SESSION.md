# 当前交付：0.2.32b 通用模组方块和实体场景快照

用户要求扩展场景快照，未来新增建筑模组自动支持，并能保存实体。已完成：

- 新增 ZombiesSceneSnapshots，以注册表解析通用方块状态，保存 BlockEntity 的完整标准 NBT。已安装模组的普通方块不再受旧原版实现类型名单限制；场景保存容器物品、名字等附加数据。
- Scene 增加 blockEntities、entities、includesEntities；保留原 5 参数构造与旧 JSON（默认仅方块，不删除旧场景目标实体）。SceneBlockEntity 保存相对格子坐标和 SNBT，SceneEntity 保存相对位置的完整实体 SNBT。
- 新增 UI“同时保存场景实体”开关，默认新快照开启。修改开关需重新读取；已有快照从 includesEntities 回显，旧快照关闭。摘要显示方块数据和实体数量。
- 保存盔甲架、展示框、普通生物等标准实体数据及装备、骑乘树。复制时生成新 UUID，平移位置、挂墙锚点和坐标形式的拴绳锚点。玩家、剧情 NPC、房间登记波次怪、投射物、经验球、临时掉落不参与。目标区域内普通实体被替换，源场景不变。
- ZombiesSceneEntityJournal 使用 SavedData 记录被替换的原实体及新实体 UUID。活跃实体 UUID 集合不落盘；对局结束还原原实体，副本即使离开区域仍清理；重启／后续区块加载会拒绝孤立副本。ServerStarted 在方块恢复后恢复实体。
- ZombiesNpcBlockJournal 提供完整格子快照、两阶段还原及记录检查点；同状态容器替换也能重新创建正确的 BlockEntity。失败回滚恢复本次方块、附加数据、目标实体并退回 journal 检查点，不撤销先前成功事件。
- 显式逐个检查 EntityJoin 添加结果；原版 tryAddFreshEntityWithPassengers 不传播 Forge 事件拒绝，不能用来判断成功。
- NPC 的旧 SET_BLOCK 静态限制保留，仅抽出 validateSceneCell 共用地图边界和屏障／交互设备保护。地图事件走新的通用场景引擎。

## 验证

- build/verification/scenes-032-final.log：build、90 项服务器必需检查、119 项客户端显示交互检查、部署兼容套件全部通过。
- 新检查使用实际 TaCZ 注册方块，验证容器 NBT、盔甲架装备及新 UUID、源实体不变、目标实体替换与移动副本清理、展示框锚点／物品／旋转、生成拒绝的完整回滚、孤立副本拦截、持久 journal 重建后的恢复。旧权限、远距离、尺寸限制及屏障保护仍有效。
- 中间构建发现并修复添加实体 helper 忽略 Forge 取消的问题；原“箱子必须拒绝”旧测试已按新要求更新。源实体测试用区块票保持来源加载，避免把正常卸载误判为删除。
- 已查看 scenario-captured-scene-640.png，实体开关和数据摘要显示正常。
- 安装包 build/libs/codpattern-zombies-0.2.32b.jar；新生产类、版本、Mixin 路径和测试类排除已核对；SHA256 见 build/verification/release-0.2.32b.json。
- 客户端和服务器都需更新。旧快照需重新读取并保存才包含新增数据。尚未在用户完整建筑模组整合包中逐一人工复测；依赖外部文件或特殊跨区域联动的模组仍可能需要适配。

## 限额与语义

每场景最多 4096 格、128 个实体（含骑乘），每事件最多 16 区域、8192 格、256 个新实体。附加数据总字符上限 1500000、单对象 262144，沿用编辑网络 2 MiB 解压及请求压缩大小上限。事件区域不可互相重叠。普通生物保留 AI，并设置持续存在，避免自然消失；不会加入波次怪物统计。源场景可以在地图外 256 格内，目标必须位于关卡内。

## 环境

仓库 main；作者 maerd110 <yyu139301@gmail.com>。JDK D:/，GRADLE_USER_HOME=build/gradle-home。不要运行 clean。GitHub 单次代理：`-c http.proxy=http://127.0.0.1:7890 -c http.version=HTTP/1.1`。
构建：build runGameTestServer runClient runZombiesDeployCompatSuite -PnpcGameTests -PnpcClientPreview -PgameTestRunDirectory=build/gametest/coop-032 --offline --continue --console=plain。
