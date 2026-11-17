# 当前交付：0.2.34b 战役存档与连续关卡第一版

## 已实现

- 默认 H 个人主页（可在 MC 按键设置修改）：创建战役、选择/继续/命名/删除个人故事存档、历史章节起点回玩建立独立分支、个人剧情仓库、账号收藏和他人公开展示。
- 部署工具新增“战役与跨关剧情”，沿用卡片与分层详情：战役/章节顺序、重要状态、人物身份与各关 NPC 绑定、剧情物品、任务、收藏、事件/效果。Gson records 校验引用、权限、WAITING阶段、并发编辑修订号，使用既有分段上传通道。
- CampaignStore：配置 world/serverconfig/codpattern_zombies/campaigns.json，数据 world/data/codpattern_zombies/campaign-state.json；账户+多名队员存档同一 copy-on-write 原子文件事务，写入强制落盘，失败保留旧磁盘及提交状态。
- CampaignRuntime：按房间尝试 UUID 隔离故事，存档历史与个人收藏分开；队员选择保留路线，不覆盖不兼容旧档；同路线继续更新对应档；通关后也可保留刚完成的结果。
- 按个人存档持有剧情物品；新加入路线继承房主检查点当时仍持有的道具，匹配同一路线则保留自己的仓库。GIVE/TAKE 可个人或每名在线成员，整事件回滚；账号收益只在结算发给在线房间成员。
- 安全检查点保存 NPC 生死/姿态/位置和完成规则、屏障开启、顺序地图事件、指引、波次与重要状态；中断/失败后从最后成功检查点恢复，战斗资源重新初始化。
- 实际开局及中途加入接入原房间/装备/传送/回收接口。启动战役先 beginAttempt，与原开局投票生命周期一致，不能跳过未完成的恢复。
- 通关暂停旧结算倒计时，房主选择下一关或结束；可在连续章节复用同一部署地图。下一关排队前后检查房间身份与占用；客户端旧请求必须匹配 instance。
- 房主掉线或离开转交在线最低延迟成员，不改变世界；结算/保存失败等待期间全员离线60秒释放房间，空名单立即释放。
- NPC 动作新增 CAMPAIGN 事件，条件新增 CAMPAIGN_STATE / CAMPAIGN_ITEM；玩家选择块 teamVote 开关，全队投票、平票重投、再平票房主选择。冻结投票初始成员，超时弃权，断线成员不阻塞。分段对话转投票关闭旧窗口，自返回必须重新交互。
- 第一版账号收藏以卡片展示，没有实体红枪、称号、全服邮件；同地图多个世界副本尚未实现。完整任务日志、分支章节出口、迁移工具、主动复制等详见 docs/CAMPAIGNS.md。

## 验证

- build/verification/campaign-034-full.log：103项必需服务器检查、130项客户端渲染交互、完整构建与部署兼容回归全部通过。截图与结果见 build/verification/npc-client/。
- 新增10项GameTests：真实完整开局/中途加入/统一初始装备与点数银行重置，队员采用更多进度路线与通关后保留，历史回玩隔离，个人消耗，离线不领奖与房主移交，实际写入失败后原子保留和重试，磁盘重新读取，投票平票规则与分段对话转投票/自返回，非法引用/权限。
- 客户端新增个人主页、战役卡片详情、收藏和投票的640/320渲染交互，保持原NPC与部署回归。旧有 UI、场景快照、枪械、银行验证继续执行。
- 测试专用真实关卡需要填入有效的墙枪池，原模板空传奇池会被原部署校验拒绝；测试没有放松生产校验。
- 安装包 build/libs/codpattern-zombies-0.2.34b.jar。NPC dialogue网络协议11，客户端与服务端均需更新。
- 仍需用户完整整合包四人实服验收。先在测试世界布置两关，验证保存、回玩、保留队友路线与切关。

## 已定玩法

以 docs/CAMPAIGNS.md 和本会话为准。旧 docs/多人副本与战役存档方案.md 已标为历史提案，房主独占进度、共享剧情背包、跨关继承战斗装备已撤销。收藏属于账号收益，不属于存档。

## 环境

main；作者 maerd110 <yyu139301@gmail.com>。JDK D:/；GRADLE_USER_HOME=build/gradle-home；绝对不要 clean。
完整检查：build runGameTestServer runClient runZombiesDeployCompatSuite -PnpcGameTests -PnpcClientPreview -PgameTestRunDirectory=build/gametest/campaign-034-final --offline --continue --console=plain。
GitHub单次代理：-c http.proxy=http://127.0.0.1:7890 -c http.version=HTTP/1.1。
