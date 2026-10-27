# 第三方素材与许可

本仓库原创的安卓应用代码和工具代码采用 MIT 许可，见 [LICENSE](LICENSE)。该许可不涵盖第三方角色形象、动画、精灵图或原始表情包。

爱弥斯出自《鸣潮》（Wuthering Waves），角色及相关视觉设计归 Kuro Games / 相应权利方所有。本项目为非官方、非商业的同人安卓适配；素材仅供个人学习交流，本仓库不授予额外的商业使用许可。涉及素材的再分发或商业使用，请遵循来源声明并取得相应授权。

## 动画来源

- [hachimi-ai/dsh-aemeath](https://github.com/hachimi-ai/dsh-aemeath)：使用扩展精灵图第 9–14 行，转换为开心摆手、害羞眨眼、好奇张望、安静发呆、偷偷打盹、开心冒头。该项目声明插件代码采用 MIT，角色素材仅供个人学习交流。本项目没有包含其 DSH 皮肤或壁纸。
- [culture-flask/dsh-aemeath-pet](https://github.com/culture-flask/dsh-aemeath-pet)：使用六个原始透明 GIF，保留原始帧和时序，并生成向左走动的镜像。该项目采用 Apache-2.0，其协议全文保留在 [CULTURE-APACHE-2.0.txt](app/src/main/assets/CULTURE-APACHE-2.0.txt)。
- culture-flask 项目标注的原始表情包作者为 B 站「Neuro音乐」，来源：[爱弥斯表情包动态](https://www.bilibili.com/opus/1167003396733927448)。行与帧编排源自 [ChuyuZhong/aemeath-codex-pet](https://github.com/ChuyuZhong/aemeath-codex-pet)。

转换过程包括 GIF 抽帧、透明像素处理、左右镜像、图集重排、相同帧存储去重和动画名称本地化；没有使用 AI 重新绘制角色。动作映射与图集校验值见 [merge-report.json](merge-report.json)，处理脚本见 [tools/merge-assets.py](tools/merge-assets.py)。

动画图集与动作配置位于 `app/src/main/assets/`。应用内“素材来源与说明”和 APK 随附的 `NOTICES.txt` 也保留了来源信息。

## 抛射算法

`ThrowTrail.java` 和 `ThrowMotion.java` 移植自 [PC2005-cloud/dsh-pet](https://github.com/PC2005-cloud/dsh-pet) 的 `dsh-pet/src/shared/physics.ts`，参考版本为 `932de8a80951f9bd8beeb87fd05f129b52586b3e`。复用轨迹采样、松手速度估算、软限速、重力、边缘反弹、地面摩擦和积分细分；安卓部分增加 dp 单位换算、完整窗口边界限制、中断和开关。

Copyright (c) 2026 PC2005-cloud，MIT 许可全文保留在 [DSH-PET-MIT.txt](app/src/main/assets/DSH-PET-MIT.txt)，并随安装包提供。没有引入 DSH 的 AI、任务状态、余额或网络连接。

## 气泡短句

`app/src/main/assets/chatter.json` 区分 `game-voice` 与 `fan-written`。两条游戏语音短句参考 [爱弥斯故事和语音整理页](https://genshin-builds.com/cn/wuthering-waves/characters/aemeath/info)，相关游戏文本权利归相应权利方所有；其余为本项目原创二创文案。没有包含官方语音音频，也没有运行时 AI 文本生成。代码许可不对原始游戏台词授予额外权利。
