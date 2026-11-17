# OmniEval — 全模态智能评估

Android 原生 Kotlin + Jetpack Compose 项目。

## 第一阶段原型
- 任意长度文本输入
- 系统图片选择
- ML Kit 中文本地 OCR
- 中英文界面切换
- 离线规则型初步评估
- GitHub Actions 自动构建 Debug APK

## 构建
运行 `gradle assembleDebug`。
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 重要边界
当前版本是可继续开发的原型，不是完整多模态智能评估系统。离线评分只是规则检查提示，不代表内容正确率。图片暂时只提取文字，在线模型、联网来源核验、图像语义分析、历史记录和 PDF 报告尚未接入。

## 后续阶段
1. 统一任务、证据、评估维度和结果数据模型
2. 接入可配置的在线多模态模型适配器
3. 联网来源核验并为事实主张附来源
4. 图像语义、图表读取、图文一致性检查
5. 历史记录、报告导出和评估规则配置
