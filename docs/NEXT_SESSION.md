# 当前交付：0.2.33b 可调场景容量与大配置分段上传

用户询问 4096 格限制，进一步要求能无限制则无限制，否则作为独立参数由部署者调整。已实现为每关卡独立设置，并保留资源保护：

- ZombiesScenarioConfig 新增 snapshotLimits（SnapshotLimits），maxRegionBlocks 默认 65536、maxEventBlocks 默认 131072。允许 1..1048576，0／负数／超过保护范围拒绝。旧配置自动补默认值。保留原有 5／6／7 参数构造。
- 配置构造时检查每区域及每事件合计，报错给出实际体积、设置上限与事件名。空气计入。Scene／Region 原本固定4096验证改为结构完整性和防溢出验证，MapEvent不再为计数生成全部 placements。
- 部署 UI 任意条目列表左上“场景容量设置”；区域详情“调整本关卡场景容量上限”。设置独立详情页，不能被删除；返回保留工作副本。源场景下方实时显示体积与上限。工作副本容量可直接用于 capture，之后与配置一同保存。
- ScenarioEditorService.capture 在世界读取／数组分配前检查所提交容量，默认取已保存当前关卡设置；权限及等待阶段限制保留。effective 导入旧波次时保留 snapshotLimits，运行时使用保存并冻结的配置。
- 场景捕获缓存状态名称；放置计划缓存解析的状态 NBT，区域按格迭代，不先生成 Position/BlockPlacement 对象列表。区域边界与设备交叉保护整区检查一次；仅按区块加载。还原缓存相同 NBT 状态解析。保持失败回滚和原有实体恢复。
- 编辑网络原 C2S28KB限制会阻止大配置保存。增加 EditorFragment 消息，NPC dialogue 协议 9->10。每片最多28000字节；管理员手持部署工具才能上传；按玩家隔离，只接收有序同token/map/op/total的片段。最多33片／900000压缩字节、解压2MiB、16并发、30秒超时，完整后才调用原保存服务。停服清空缓存。小请求沿用旧消息。Scenario/Rules编辑页面允许检查可分段的大请求。

## 验证

- build/verification/limits-033-full.log：完整 build、93 项服务器必需检查、121 项客户端显示交互检查、部署兼容套件全部一次通过。
- 真正 capture/save/apply/restore 65536 格，包含实际世界标记方块，验证新容量保存与effective读取、低上限提前拒绝、大场景放置与还原。整段测试含存盘耗时3545ms，只作为开发环境证据，不是性能保证；大场景仍同步执行，可能短暂卡顿。
- 随机180KB不易压缩JSON通过多个实际编码／解码片段重组，检查单包<32767字节、部分数据不执行、内容和请求身份不变、未授权及串token拒绝。
- 检查旧配置默认值、0/负数/极大值拒绝、单事件合计限制、设置页写入真实参数、返回保留工作副本。已查看 scene-capacity-settings-640.png / 320.png。
- 安装包 build/libs/codpattern-zombies-0.2.33b.jar。版本、新生产类和测试类排除核对通过；校验值见 build/verification/release-0.2.33b.json。
- 客户端与服务器都需更新（编辑网络协议变为10）。未在用户完整整合包前台多人环境人工复测。

## 环境

仓库 main；作者 maerd110 <yyu139301@gmail.com>。JDK D:/，GRADLE_USER_HOME=build/gradle-home。不要运行 clean。GitHub 单次代理：`-c http.proxy=http://127.0.0.1:7890 -c http.version=HTTP/1.1`。
构建：build runGameTestServer runClient runZombiesDeployCompatSuite -PnpcGameTests -PnpcClientPreview -PgameTestRunDirectory=build/gametest/coop-033 --offline --continue --console=plain。
