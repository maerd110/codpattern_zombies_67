# 续作记录：0.2.28b 鼠标与领取提示修复

2026-11-13（北京时间）：用户已要求继续；已从本地检查点 `1426c74` 恢复工作。

## 已完成

- 分段问答推进直接发送下一段，不先关闭窗口；客户端替换旧对话时避免多余的关闭请求。
- 通过 Minecraft 标准流程打开或切换界面时保留鼠标位置；窗口失焦时不移动桌面鼠标，回到游戏仍使用原版视角控制。实现位于 `ZombiesScreenCursor`，没有新增 Mixin。
- NPC 与区域奖励共用 `ZombiesNpcInventory.receivedMessage`，逐项提示实际道具名、自定义名字和数量：`“道具名”已放入背包 × 数量`。
- 82 项服务器检查、104 项客户端显示与交互检查已通过。客户端检查使用真实界面打开及鼠标释放流程，记录平台鼠标移动请求，避免隐藏窗口无法移动物理鼠标造成误报。
- 已核对 `build/libs/codpattern-zombies-0.2.28b.jar` 的版本、新增类和测试类隔离，更新 `build/verification/release-0.2.28b.json` 的校验值与验证记录。
- 保持版本号 0.2.28b；按此前授权推送并交付更新后的同名安装包。完整整合包的前台多人窗口尚未人工验证。

## 验证文件

- `build/verification/cursor-receipts-028.log`：服务器 82 项及部署检查通过；该次客户端检查受隐藏窗口限制失败，后续客户端检查已通过。
- `build/verification/cursor-client-028.log`：最终构建与客户端检查成功。
- `build/verification/npc-client/result.txt`：104 项检查通过。
- `build/verification/release-0.2.28b.json`：最新安装包 SHA256 及验证说明。

## 环境

- 仓库：`C:/Users/Lenovo/Desktop/Me/僵尸模式/codpattern_zombies_67`，分支 `main`。
- JDK：`D:/`；Gradle 缓存：仓库内 `build/gradle-home`。
- 不要运行 `gradlew clean`，缓存位于 build 目录中。
- GitHub 连接使用单次参数：`-c http.proxy=http://127.0.0.1:7890 -c http.version=HTTP/1.1`。
