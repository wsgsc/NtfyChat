# NtfyChat - 开源即时通讯应用

[English](README.md) | [简体中文](README.zh-CN.md)

---

## 项目简介
NtfyChat 是一个基于 [ntfy](https://github.com/binwiederhier/ntfy) 的开源聊天软件。

**工作原理**：你可以设置订阅连接、用户名和窗口密码。只要订阅连接和窗口密码保持一致，就可以实现加密聊天。原理比较简单，采用对称加密算法，简单高效地保护你的私密对话。

## 功能特性
- 📱 基于 ntfy 协议的实时消息传递
- 🚀 无需配置服务器 - 默认使用 ntfy.sh 官方服务器
- 🔒 支持端到端加密
- 🌐 可自建服务器
- 📎 支持文件和图片分享
- 🎨 Material Design 设计风格
- 🔔 推送通知
- 💬 支持群聊功能

## 下载安装
- **Google Play 商店**：即将上线
- **F-Droid**：即将上线
- **GitHub Releases**：[下载 APK](https://github.com/wsgsc/NtfyChat/releases)

## 构建说明
详细的构建说明请参考 [ntfy 官方文档](https://docs.ntfy.sh/develop/#android-app)。

```bash
# 克隆仓库
git clone https://github.com/wsgsc/NtfyChat.git
cd NtfyChat

# 构建应用
./gradlew assembleRelease
```

## 技术栈
- **开发语言**：Kotlin
- **架构模式**：MVVM + Repository 模式
- **数据库**：Room (SQLite)
- **网络请求**：OkHttp
- **UI 框架**：Material Design Components
- **依赖注入**：手动依赖注入
- **推送通知**：Firebase Cloud Messaging（仅 Play 版本）

## 参与贡献
欢迎贡献代码！请随时提交 Pull Request 或创建 Issue。

## 翻译
我们使用 [Weblate](https://hosted.weblate.org/projects/ntfy/) 进行多语言翻译，欢迎参与翻译工作！

## 开源协议
本项目采用 [Apache License 2.0](LICENSE) 协议。

基于 [Philipp C. Heckel](https://heckel.io) 开发的 [ntfy](https://github.com/binwiederhier/ntfy) 项目。

