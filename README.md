# OmniEval — 全模态智能评估

Android 原生 Kotlin + Jetpack Compose 项目。

## 当前版本（第二阶段起步）

- 通用文本评估：核心主张、内部逻辑、事实与证据、数据与计算、反例、结论边界、验证建议
- 可配置 OpenAI Chat Completions 兼容接口（HTTPS）
- 支持将单张图片随请求发送给具备视觉能力的模型
- API Key 使用 Android Keystore AES-GCM 加密后保存在本机
- 中英文界面切换
- GitHub Actions 自动构建 Debug APK

## 首次使用

1. 安装 APK，打开“AI 模型连接”。
2. 填写服务商提供的 HTTPS API Base URL、模型名称和 API Key。
3. 点击“保存连接设置”。
4. 输入待评估内容；如需图像分析，可添加一张图片。
5. 点击“开始 AI 智能评估”。

默认接口地址为 `https://api.openai.com/v1`，默认模型名为 `gpt-4o-mini`。可改为其他兼容 Chat Completions 的服务商地址和模型。不同服务商对视觉输入、令牌参数和响应格式的支持可能不同。

## 安全与隐私说明

- APK 不内置任何开发者 API Key；用户自行填写并承担其服务商费用。
- API Key 通过 Android Keystore 派生的 AES-GCM 密钥加密保存于本机。
- 请求会把输入文本以及用户主动选择的图片发送到用户配置的 API 服务商。
- 请只使用可信 HTTPS 服务端；不要向不可信服务商提交敏感资料。
- 生产级部署更推荐由自有后端代理模型请求，以便统一管理密钥、限流和审计。

## 当前边界

当前已接入的是模型推理接口，不等于独立的联网事实核验。模型报告中没有实际检索并展示的来源，均不得视为已独立证实。当前图片能力依赖所配置模型是否支持视觉输入；暂支持单张图片，大小上限 8 MB。PDF、Office 文档、表格文件、多图批处理、联网搜索与逐条来源引用、历史记录和报告导出仍待后续开发。

## 构建

运行 `gradle assembleDebug`。
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。
