# STEPX Neo 背屏工坊

适用于 STEPX Neo 17 × 23 点阵背屏的 Android 应用。提供表情、文字、像素画、动画和自动电量显示，使用原生 Java 开发，无第三方依赖。

当前版本 **1.3**，包名 `com.workbuddy.backscreen`。

## 下载安装

在本仓库的 [Releases](../../releases) 下载 `BackScreenStudio-1.3.apk`，传到手机并安装。也可使用：

```sh
adb install -r BackScreenStudio-1.3.apk
```

应用依赖 StepOS 的设备专用背屏接口，已在 STEPX Neo（PZ261A、Android 16）验证。其他机型或系统版本的兼容性尚未验证，无需 root。

## 功能

- **表情与文字**：调用系统背屏表情、显示自定义文字。
- **像素编辑**：17 × 23 等比例画布、多级亮度画笔、橡皮、填充、清空、图片导入及多帧动画。
- **像素预设**：爱心、笑脸、星星、加载、音乐和黑桃。
- **实时电量**：大数字、电池、环形三种样式，支持实时显示、仅充电时显示、充满保留，样式选择自动保存。
- **后台显示**：前台服务持有画面，离开编辑页后继续播放；保存显示快照，在普通进程回收后尝试恢复。
- **Amoo 协同**：助手和系统动画接管时暂停自定义输出，确认恢复空闲后重新显示原有内容。

## 使用提示

进入「电量」选择样式，先点击「在背屏预览 12 秒」，再开启自动显示。像素页点击「播放」或「推送当前帧」后可以退出应用。

请允许通知，常驻通知提供停止显示入口。系统限制后台运行时，可使用设置页的「允许后台运行」。背屏需要朝向允许显示的方向，或开启系统背屏常显。

动画运行时使用局部唤醒锁，会增加耗电；静态画面不持有唤醒锁。普通进程回收、开机和应用更新后会尝试恢复，但系统调度仍可能延迟。用户手动「强行停止」后，需重新打开应用；不能保证服务永不被系统终止。

## 构建

需要 macOS 或 Linux、`zsh`、`zip`、JDK 17、Android SDK Platform 37 和 Build Tools 37.0.0。SDK 工具需预先安装，构建本身不下载依赖，不使用 Gradle。

```sh
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/android-sdk"
# 可选：指定其他已安装的 Build Tools 目录
# export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/37.0.0"
zsh build.sh
zsh test.sh
```

macOS 可通过 `export JAVA_HOME="$(/usr/libexec/java_home -v 17)"` 选择 JDK。默认 SDK 路径为 `$HOME/Library/Android/sdk`；也支持 `ANDROID_SDK_ROOT`。

输出为项目根目录的 `BackScreenStudio.apk`。首次构建会生成本地测试签名密钥 `debug.keystore`；密钥和构建产物均被 Git 忽略。本地新生成的签名与 Releases 安装包不同，不能直接覆盖安装；如需共存开发，请自行修改包名，或备份后卸载旧版再安装。请保存自己的签名密钥以便后续升级。

## 测试与验证范围

`test.sh` 在构建后运行三个 JVM 测试：

- 电量渲染：三种样式 0–100%、LED 掩膜、数值辨识和满电稳定性。
- 显示快照：单帧、多帧、起始帧恢复，以及损坏和越界数据拒绝。
- Amoo 交接：活动状态让出、未知状态等待、稳定空闲后恢复。

1.3 已通过实体 AI 按键唤醒、对话退出后的恢复验证。开机恢复代码已实现，尚未完成整机重启验收。

## 源码结构

| 文件 | 用途 |
| --- | --- |
| `MainActivity.java` | 五个功能页和操作入口 |
| `BackScreenApi.java` | StepOS 背屏接口与系统动画监听 |
| `ChargeService.java` | 电量、像素画和动画的后台输出 |
| `HandoffPolicy.java` | Amoo 接管与恢复策略 |
| `FrameArchive.java` | 原子快照保存和 CRC 校验 |
| `ChargeArt.java` / `PixelArt.java` | 电量和像素图案渲染 |
| `PixelEditorView.java` | 像素编辑画布 |

Java 源码位于 `app/src/main/java/com/workbuddy/backscreen/`。

## 版本记录

- **1.3**：接入系统动画回调和实际播放状态查询，修复 Amoo 退出后静态背屏不恢复的问题。
- **1.2**：增加前台服务、持久快照、后台动画与恢复机制。
- **1.1**：统一深色界面，增加三种电量样式，重绘像素预设。

本项目为设备自定义工具，与设备厂商无隶属关系。
