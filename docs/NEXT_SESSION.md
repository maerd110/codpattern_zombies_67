# 当前交付：0.2.29b 屏障与武器池部署

用户要求集成屏障参数和墙枪／神秘箱武器池编辑，并更新版本号；已实现为 0.2.29b。

- 部署工具入口：“屏障与武器池”。原有 NPC、剧情和物件部署入口保留。
- `ZombiesRulesEditorService` 按关卡编辑原有 barrier_groups.json、weapon_wall.json、mystery_box.json、weapon_rules.json。独立文件版本校验、管理员手持工具校验、等待阶段限制、原子替换及数据校验。
- `ZombiesRulesScreen`：分组与入口卡片、正整数编号、价格、带 NBT 的钥匙、怪物／玩家出生组启停；品阶彩色卡片、权重与刷新参数、全枪包图标选择、每枪权重；品阶伤害和强化等级／价格。
- 枪械来自服务器 TaCZ 实际加载的 CommonGunIndex，客户端按本地枪包显示模型和名称。选枪卡片匹配所选品阶颜色。概率使用原有相对权重，显示初始占比和品阶内占比。
- 保存后返回物件工具刷新入口选项，下一次开局读取新参数；对局中的配置保持冻结。
- 品阶定义仍被池引用时禁止删除；错误权重、枪械、钥匙和并发旧版本不能覆盖文件。

## 验证

- `build/verification/rules-editor-029-final.log`：84 项服务器测试与完整首轮客户端及部署兼容检查通过。
- `build/verification/rules-editor-029-client.log`：最后一次客户端检查和安装包构建。最后调整仅是图标构建时优先使用枪包实际注册的物品类型，服务器代码未变。
- `build/verification/npc-client/result.txt`：112 项客户端显示和交互检查。
- 具体操作见 `docs/RULES_EDITOR.md`，安装包为 `build/libs/codpattern-zombies-0.2.29b.jar`。
- 尚未在用户完整整合包前台多人联机中人工验证。

## 环境

仓库分支 main；作者 maerd110 <yyu139301@gmail.com>。JDK 位于 D:/，GRADLE_USER_HOME 使用 build/gradle-home。不要执行 gradlew clean（缓存位于 build 中）。GitHub 单次代理参数：`-c http.proxy=http://127.0.0.1:7890 -c http.version=HTTP/1.1`。
