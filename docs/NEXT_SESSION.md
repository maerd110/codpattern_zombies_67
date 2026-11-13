# 当前交付：0.2.31b 中文品阶显示名称

用户发现品阶只有内部编号，填写“终极”无法保存，要求增加中文名称。现已实现：

- “品阶与强化”选择品阶后，分别提供“显示名称（支持中文）”与“内部编号”。显示名称最多 64 个字符，存于 weapon_rules.json 的 displayName；编号保留英文数字等稳定绑定。新增品阶自动生成 custom_N 与可编辑的名称。
- 改显示名称不改 ID，保持武器池、装备的引用、伤害和权重。编辑页使用自己的草稿与服务端返回的品阶定义显示名称，不依赖正在运行房间的名称。
- 房间协作快照同步 displayName。ZombiesRarityDisplay 读取当前房间名称和颜色，墙枪／交互提示、NPC 支援与选枪、枪械标记等统一显示中文。
- 旧配置或旧快照没有名称时，内置品阶保持原中文名，自定义品阶显示内部编号。
- 编号格式错误和重复分别报错，格式错误说明中文应该填显示名称，并列出允许字符；名称输入框与内部编号增加说明。

## 验证

- build/verification/rarity-name-031.log：完整 build、87 项服务器必需检查、客户端和部署兼容套件一次通过。
- build/verification/npc-client/result.txt：119 项客户端显示及交互检查通过。
- 检查覆盖中文名称编辑且 ID 不变、存盘／重读／房间快照、客户端墙枪文字与颜色、NPC 品阶文字、旧快照回退、无效编号和重复编号不同报错，以及原有回归。
- 已查看 custom-rarity-640.png 与 rarity-display-name-320.png，名称与编号分开显示，控件未溢出。
- 安装包 build/libs/codpattern-zombies-0.2.31b.jar，元数据与 src/test 预览／测试类排除已核对；校验值见 build/verification/release-0.2.31b.json。
- 尚未在用户完整整合包的前台多人环境人工复测。客户端、服务端都需更新，名称修改保存后在下次开局读取。

## 环境

仓库分支 main；作者 maerd110 <yyu139301@gmail.com>。JDK 位于 D:/，GRADLE_USER_HOME 使用 build/gradle-home。不要执行 gradlew clean（缓存位于 build 中）。GitHub 单次代理参数：`-c http.proxy=http://127.0.0.1:7890 -c http.version=HTTP/1.1`。

构建：build runGameTestServer runClient runZombiesDeployCompatSuite -PnpcGameTests -PnpcClientPreview -PgameTestRunDirectory=build/gametest/coop-031 --offline --continue --console=plain。
