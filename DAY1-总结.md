# Day 1 复盘：用纯 Java 打通大模型 API

> 目标：不写一行 Python，用 OkHttp 调通 DeepSeek，解析 JSON，控制台打印回复。
> 结果：✅ 跑通，退出码 0。

> 💡 **觉得术语太多看不懂？先跳到文末《十、术语翻译（大白话版）》，看完再回来读正文。**

---

## 一、今天做成的这一件事

一段 Java 程序，把一句话发给 DeepSeek，拿回一段回答，打印在控制台。

听起来简单，但它跑通的是一条**完整的生产级链路**：Maven 管理依赖 → OkHttp 发 HTTPS → Bearer Token 鉴权 → JSON 序列化 / 反序列化 → 异常处理。

后面所有花活（多轮对话、流式输出、RAG、Function Calling、Dify 自定义工具），底层都是这条链路。

---

## 二、数据是怎么流动的（核心逻辑）

```
你的问题（String）
   │
   │ ① 序列化：拼成 JSON 请求体
   ▼
{ "model": "deepseek-chat",
  "messages": [ { "role": "user", "content": "用三句话解释 RAG" } ],
  "temperature": 0.7,
  "stream": false }
   │
   │ ② HTTP POST，带上 Authorization: Bearer sk-xxx
   ▼
https://api.deepseek.com/chat/completions
   │
   │ ③ 模型推理，返回 JSON
   ▼
{ "id": "...",
  "choices": [ { "index": 0,
                 "message": { "role": "assistant", "content": "RAG 是……" },
                 "finish_reason": "stop" } ],
  "usage": { "prompt_tokens": 20, "completion_tokens": 300, "total_tokens": 320 } }
   │
   │ ④ 反序列化：取 choices[0].message.content
   ▼
回答（String） → System.out.println
```

代码里的四段，和上面四个箭头一一对应：

| 箭头 | 代码 | 关键点 |
|---|---|---|
| ① | `JSONObject` + `JSONArray` 拼 body | `messages` 是**数组**，因为对话可以有多条 |
| ② | `new Request.Builder().url().addHeader().post()` | 鉴权在 header，不在 body |
| ③ | `CLIENT.newCall(request).execute()` | `execute()` 是同步阻塞，适合控制台程序 |
| ④ | `JSON.parseObject(raw)` → `choices[0]...content` | 取的是**嵌套三层**的字段 |

---

## 三、请求体四个字段的含义

| 字段 | 作用 | 取值范围 |
|---|---|---|
| `model` | 用哪个模型 | `deepseek-chat`（通用）/ `deepseek-reasoner`（推理） |
| `messages` | 对话内容数组，每条含 `role` + `content` | role：`system`（人设）/ `user`（用户）/ `assistant`（模型） |
| `temperature` | 随机性。越低越稳、越高越发散 | 0 ~ 2，代码里 0.7 |
| `stream` | 是否逐字返回 | `false` 一次性给全；`true` 流式（Day 3 做打字机效果用） |

---

## 四、最关键的一个认知：大模型 API 是「无状态」的

**服务器不记得你刚才说过什么。** 每一次请求都是全新的、互不相干的。

所以所谓「多轮对话」，真相是：**客户端每次都把完整历史重新发一遍**。

```
第 1 轮请求：messages = [用户:你好]
第 2 轮请求：messages = [用户:你好, 模型:你好!, 用户:我叫小明]
第 3 轮请求：messages = [用户:你好, 模型:你好!, 用户:我叫小明, 模型:好的, 用户:我叫什么?]
```

这解释了三个现象：

1. **为什么 Day 2 要用 `List` 存历史** —— 不存，模型就"失忆"；
2. **为什么对话越长越贵** —— 计费按 input token 算，历史每次都重发一遍；
3. **为什么会有上下文长度上限** —— 历史无限增长，迟早超模型窗口。

面试如果被问「你怎么实现多轮对话」，答“客户端维护 messages 数组、每次全量重发”，比答“模型有记忆”专业得多。

---

## 五、三个「为什么不这么写」

| 写法 | 为什么 | 不这么写会怎样 |
|---|---|---|
| `OkHttpClient` 用 `static final` 复用 | 连接池和线程池挂在 client 上 | 每次 new 都重新建连，高并发下直接崩 |
| `readTimeout` 设 60 秒 | 大模型是边生成边返回，首字可能就要几秒 | 默认 10 秒，长回答必超时 |
| Key 从 `System.getenv` 读 | 代码要进 Git，Key 不能进 | 推到 GitHub 几分钟内就被爬虫盗刷 |

---

## 六、错误码速查表

| 状态码 | 含义 | 你该做什么 |
|---|---|---|
| 200 | 成功 | 解析 `choices[0].message.content` |
| 400 | 请求体格式错 | 检查 JSON 字段名拼写 |
| 401 | Key 无效 / 未带 | 检查环境变量、`Bearer ` 后有没有空格 |
| 402 | 余额不足 | 充值 |
| 429 | 请求太频繁 | 降频、加退避重试 |
| 5xx | 服务端问题 | 等一会儿重试 |

代码里用的是 `if (!response.isSuccessful())` 统一兜住，并把原始报文打出来 —— 排查时永远先看原始报文。

---

## 七、今天真实踩过的坑（环境层面）

| 坑 | 现象 | 根因 | 解法 |
|---|---|---|---|
| Oracle Java 8 抢占 | 装了 JDK 21，`java -version` 仍是 1.8 | 系统 PATH 里的 `java8path` 排在前面 | 从 PATH 删掉那两条 Oracle 目录 |
| 环境变量不生效 | 改完 path，新终端还是旧值 | 资源管理器缓存在旧环境块 | 广播 `WM_SETTINGCHANGE` / 重启 explorer |
| 依赖一直"假死" | Maven 卡在同一行不动 | 阿里云某 CDN 节点收下请求不返数据 | `curl` 能秒下而 Maven 卡 = 换镜像（本次换华为云） |
| IDEA 导入卡住 | 同步 3 分钟无进展 | IDEA 顺带解析 `maven-site-plugin` → 几十个 `flexmark-ext-*` | 命令行 `mvn` 先把包灌进本地仓库 |
| 29 个红字 | `Main.java` 全是红色波浪线 | 项目未指定 JDK | `Setup SDK` 选 `D:\dev\jdk-21` |
| 控制台乱码 | 中文显示成 `���` | cmd 重定向文件用 GBK，Java 输出 UTF-8 | IDEA 里运行无此问题；文件读取要按 GBK 解码 |

**通用心法**：报错先分三层 —— 环境（SDK/PATH）、依赖（能不能下载）、代码（语法/逻辑）。今天的坑 90% 在第一、二层，不在代码。

---

## 八、自测三问（能答上来才算真懂）

1. 把 `messages` 里那条的 `role` 改成 `"system"`，效果会有什么不同？
2. 为什么 `temperature` 设成 0，两次请求的回答也可能不完全一样？
3. 想让模型"忘掉"前面所有对话，客户端该怎么做？（提示：不是改模型）

---

## 九、明天（Day 2）

1. 把 `Main` 里的逻辑抽成 `AIService` 类（可复用、可测试）；
2. 用 `List` 维护对话历史，实现**带上下文的多轮对话**；
3. 顺手刷 LeetCode 20（有效括号）。

---

## 十、术语翻译（大白话版）

| 术语 | 人话解释 |
|---|---|
| **API** | 别人开好的"服务窗口"。你按规定递材料、拿结果，不用知道里面怎么做的 |
| **API Key** | 这个窗口的**门禁卡**。一串乱码，证明"是你在用" |
| **HTTP / HTTPS** | 两台电脑之间递材料的通用规矩；HTTPS 相当于多加了一层加密信封 |
| **POST** | 递材料的方式之一——"我提交一份东西给你"（另一种叫 GET，是"我来取一份东西"） |
| **请求头 Header** | 贴在材料外面的**标签**，写"我是谁""这份材料是什么格式" |
| **Authorization: Bearer xxx** | 标签上写"门禁卡在下面这行，请核验" |
| **JSON** | 一张**带名字的表格**，程序能一格一格读出来 |
| **序列化** | Java 对象 → 一段文字（好寄出去） |
| **反序列化** | 一段文字 → Java 对象（好让程序用） |
| **OkHttp** | Java 里专门负责"送快递、收快递"的工具包 |
| **Maven 依赖** | 别人写好的现成零件。在配置里写个名字，它自动帮你取回来 |
| **超时 timeout** | 等多久还没回应就算失败，防止程序永远卡住 |
| **连接池** | 提前备好几条电话线重复用，不用每次重新拨号 |
| **token** | 模型眼里的"半个词"，也是**计费单位** |
| **状态码 401 / 402 / 429** | 401=门禁卡不对；402=余额不够；429=你问得太急了 |
| **环境变量** | 存在系统里的一张小纸条。程序能读到，但不会写进代码（所以 Key 不会泄露） |
| **控制台 Console** | 那个黑底白字、专门显示程序输出的窗口 |

---

*记录于 Day 1。工程位置：`D:\dev\projects\day1-ai`*
