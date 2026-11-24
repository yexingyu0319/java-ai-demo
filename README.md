# Java AI Demo —— 用纯 Java 打通大模型（不写一行 Python）

一个用 **Java + OkHttp** 调用大模型 API 的最小可运行工程，覆盖从「发送一次请求」到「多轮对话」「流式输出」「结构化数据抽取」的完整链路。

做这个项目的出发点：**不用 Python 生态，用 Java 后端工程师熟悉的方式把大模型能力接进业务系统。**

---

## 功能

| 功能 | 入口类 | 说明 |
|---|---|---|
| 单轮问答 | `Main` | 最小闭环：拼 JSON → 发请求 → 解析响应 |
| 多轮对话 | `Main` | 客户端维护 `messages` 历史，支持 `/reset` 清空记忆 |
| 流式输出 | `Main` | 解析 SSE，逐段打印，实现打字机效果 |
| 流式 vs 非流式对比 | `StreamDemo` | 输出首字延迟、总耗时、片段数，量化"感知速度" |
| 简历结构化抽取 | `HrExtractDemo` | 让模型扮演 HR，把简历原文转成严格 JSON |

---

## 技术栈

- **Java 21**（使用 `record`、文本块等新特性）
- **OkHttp 4.12** —— HTTP 客户端，含连接复用与超时控制
- **Fastjson 1.2.83** —— JSON 序列化 / 反序列化
- **Maven** —— 依赖与构建管理
- **DeepSeek API** —— 兼容 OpenAI Chat Completions 协议

---

## 快速开始

### 1. 准备环境

- JDK 21
- Maven 3.9+
- 一个 DeepSeek API Key（[platform.deepseek.com](https://platform.deepseek.com)）

### 2. 配置 API Key（**不要写进代码**）

```powershell
setx DEEPSEEK_API_KEY "sk-你的key"
```

设置后**重新打开终端**（或重启 IDE）才会生效。

### 3. 运行

```bash
# 多轮对话（流式，默认）
mvn compile exec:java

# 流式 vs 非流式对比
mvn compile exec:java -Dexec.mainClass=com.example.StreamDemo

# 简历结构化抽取（默认读 sample-resume.txt）
mvn compile exec:java -Dexec.mainClass=com.example.HrExtractDemo
```

在 IDEA 里也可以直接右键对应的类 → Run。

---

## 项目结构

```
.
├── pom.xml
├── sample-resume.txt            # 简历样例（虚构数据）
├── src/main/java/com/example/
│   ├── Main.java                # 控制台多轮对话入口
│   ├── AIService.java           # 核心：历史管理 + HTTP + SSE 解析 + 成本统计
│   ├── ChatMessage.java         # 消息值对象（record）
│   ├── Reply.java               # 调用结果值对象（record）
│   ├── ResumeExtractor.java     # 简历 → JSON 抽取器（含校验与重试）
│   ├── HrExtractDemo.java       # 简历抽取入口
│   └── StreamDemo.java          # 流式 vs 非流式性能对比
└── DAY1-总结.md / DAY2-总结.md / DAY3-总结.md   # 每日复盘
```

---

## 三个关键设计

**1. 历史由客户端维护，每次全量重发**

大模型 API 是无状态的，服务器不记得上一句。因此"多轮对话"的实现方式是：客户端保存 `List<ChatMessage>`，每次请求把整份历史重新发一遍。
实测数据：第 1 轮 input 100 tokens，第 2 轮涨到 155 tokens —— 多出来的就是"记忆"的成本。

**2. system 人设常驻，裁剪时永不丢弃**

历史超过上限时成对丢弃最早的问答，但跳过索引 0 的 system 消息，保证人设不丢。

**3. 抽取类任务强制 JSON**

`response_format=json_object` + `temperature=0` + 输出后校验重试，三道保险确保拿到的不是"一段解释"而是"一张表格"。

---

## 安全说明

- **API Key 只从环境变量读取**，仓库中不含任何密钥；
- 代码里搜不到 `sk-` 开头的字符串，可放心公开；
- `resume.txt`（个人真实简历）已在 `.gitignore` 中排除。

---

## 学习记录

本项目是「四周 AI 应用开发冲刺」的产物，每日复盘见：

- [Day 1：用纯 Java 打通大模型 API](DAY1-总结.md)
- [Day 2：从脚本到应用（多轮对话）](DAY2-总结.md)
- [Day 3：流式输出与结构化抽取](DAY3-总结.md)
