# 暂停进度：2026-11-13（北京时间）

用户要关闭应用并关机，要求先保存，上午再继续。此处暂停，不继续发布或推送。

## 当前任务

修复 0.2.28b 的两个反馈，版本号保持 0.2.28b：

1. 分段对话点击回应后，鼠标不要回屏幕中央；其他通过 MC 标准流程打开的界面也保留鼠标位置。
2. 区域发放道具的成功提示与 NPC 一致：`“道具实际名字”已放入背包 × 数量`。

## 已保存的实现

- `ZombiesScreenCursor`：使用 Forge 的界面 Opening、Closing、Init.Post 事件记录与恢复归一化鼠标位置。覆盖标准界面打开和切换；窗口失焦时不移动桌面鼠标；返回游戏仍使用原版的鼠标捕获和视角控制。未新增 Mixin。
- 分段问答推进直接发送下一段，不再先关闭界面再打开。客户端替换对话窗口前将旧窗口标记为已提交，避免 removed 回调发送多余的关闭请求。
- `ZombiesNpcInventory.receivedMessage` 统一成功提示，NPC 和区域奖励共用。区域奖励成功后逐件显示道具名、NBT 自定义名字和数量；背包满等未成功情况不发送成功提示。
- 相关部署说明和回归检查已更新。

## 验证结果

- `build/verification/cursor-receipts-028.log`：82 项服务器测试及部署检查通过。该次客户端检查失败，原因是隐藏、失焦的测试窗口不能验证系统物理鼠标位置。
- 后续将客户端检查改为记录平台鼠标移动请求，同时走真实 Minecraft 界面打开、鼠标释放、对话替换流程，并验证后台窗口不会移动鼠标。
- `build/verification/cursor-client-028.log`：`build runClient` 成功，104 项客户端显示与交互检查通过。
- 后续修改仅涉及客户端鼠标平台调用隔离和测试；服务器的奖励逻辑未再改变。
- 尚未在用户完整整合包的前台联机窗口中人工验证。

## 下次继续

1. 查看本检查点后的 Git 状态和差异，保持用户暂停时的修改。
2. 核对最新 `build/libs/codpattern-zombies-0.2.28b.jar` 包含 `ZombiesScreenCursor` 等新类、版本正确且不含 test 源集的检查类，更新 SHA256 和本次发布记录。`build/verification/release-0.2.28b.json` 仍是上次发布的记录，不能当作此次补丁的最终记录。
3. 完成必要的最终核对后，按此前用户授权推送 GitHub，并给用户新的同名安装包。已通过的检查无需无理由全部重复。

## 仓库与环境

- 仓库：`C:/Users/Lenovo/Desktop/Me/僵尸模式/codpattern_zombies_67`，分支 `main`。
- 暂停前远程已发布提交：`f3e610f6d94072c150f4d0fad022f8c43ca767ba`。
- 本次改动将保存为其后的本地检查点提交；此检查点尚未推送。
- 作者：`maerd110 <yyu139301@gmail.com>`。
- JDK：`D:/`；`GRADLE_USER_HOME`：仓库的 `build/gradle-home`。
- 不要运行 `gradlew clean`，该目录内保存了离线依赖缓存。
- GitHub 连接使用单次命令代理参数：`-c http.proxy=http://127.0.0.1:7890 -c http.version=HTTP/1.1`。

常用验证命令（PowerShell）：

```powershell
$env:JAVA_HOME='D:\'
$env:GRADLE_USER_HOME=(Join-Path (Get-Location) 'build\gradle-home')
$env:GRADLE_OPTS='-Dnet.minecraftforge.gradle.check.certs=false -Dfile.encoding=UTF-8'
.\gradlew.bat build runGameTestServer runClient runZombiesDeployCompatSuite -PnpcGameTests -PnpcClientPreview -PgameTestRunDirectory=build/gametest/coop-028 --offline --continue --console=plain
```
