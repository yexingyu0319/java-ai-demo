# Day 2 复盘：从"一次性脚本"到"有记忆的对话应用"

> 目标：把 `Main` 里的逻辑抽成 `AIService`，用 `List` 维护历史，实现带上下文的多轮对话。
> 结果：✅ 跑通，并用 `/reset` 做了对照实验验证记忆机制。

> 💡 **觉得术语太多看不懂？先跳到文末《十、术语翻译（大白话版）》，看完再回来读正文。**

---

## 一、今天做成的这件事

昨天的代码是一次性的：问一句、答一句、程序结束。

今天它变成了**应用**：能连续对话、能记住你上一句说过什么、能随时清空记忆、能看见每轮花了多少 token。

判断标准很简单——**昨天的代码复制一份改个问题就得重写；今天的代码换个入口（Web 接口、Dify 工具）一行都不用改。**

---

## 二、工程结构

```
day1-ai/
├── pom.xml
├── DAY1-总结.md
├── DAY2-总结.md
└── src/main/java/com/example/
    ├── Main.java          ← 只管交互：读输入、打印（换壳不换芯）
    ├── AIService.java     ← 业务核心：历史管理 + HTTP + 解析 + 成本统计
    ├── ChatMessage.java   ← record：role + content，三个静态工厂
    └── Reply.java         ← record：content + 本次 token 消耗
```

| 类 | 职责 | 关键点 |
|---|---|---|
| `Main` | 控制台交互 | 不碰 HTTP、不碰 JSON，只调用 `AIService` |
| `AIService` | 对话核心 | 维持 `history`，每次请求全量重发 |
| `ChatMessage` | 值对象 | `record` 不可变；`system()/user()/assistant()` 三个工厂方法避免手写 role 字符串拼错 |
| `Reply` | 结果对象 | 把 `usage` 里的 token 数带出来，成本可见 |

为什么用 `record` 而不是普通 `class`：消息对象天然是"值"，创建后不该被改。用 `record` 由编译器保证不可变，还省掉 getter/equals/hashCode 一堆样板代码。**Java 17+ 的新特性，面试可以主动提。**

---

## 三、核心机制：历史在客户端，每次全量重发

`buildRequestBody()` 里没有任何"只发最新一条"的优化 —— 它遍历**整个** `history` 拼成 JSON 数组。

实测证据（三轮对话，看 token 变化）：

```
你> 我叫小明，正在准备 FDE 实习面试，目标岗位是前向部署工程师。
AI> 好的小明，记住了……
    [本轮 tokens: 输入 100 / 输出 43]

你> 我叫什么？我的目标岗位是什么？
AI> 你叫小明，目标岗位是前向部署工程师（FDE），正在准备 FDE 实习面试。
    [本轮 tokens: 输入 155 / 输出 23]        ← 输入涨了 55

你> /reset
你> 我叫什么？
AI> 你还没有告诉我你的名字……
    [本轮 tokens: 输入 84 / 输出 46]         ← 降回 84
```

| 轮次 | 输入 tokens | 里面装了什么 |
|---|---|---|
| 第 1 轮 | 100 | system 人设 + 你这一句 |
| 第 2 轮 | 155 | system + 上一问 + 上一答 + 这一问 |
| reset 后 | 84 | 只剩 system 人设 |

**多出来的 55 个 token，就是"记忆"的真实成本。** 记住这张表，你就理解了三个常见现象：

1. 为什么对话越长越贵 —— 历史每轮重发，input token 线性增长；
2. 为什么有上下文长度上限 —— 数组无限增长，迟早超模型窗口；
3. 为什么真实产品要做历史截断 / 摘要压缩 —— 成本与效果的权衡。

---

## 四、三个关键设计

### ① system 人设常驻，且永不裁剪

```java
// 构造时注入一次，之后一直待在 history[0]
history.add(ChatMessage.system(systemPrompt));
```

```java
// 裁剪时从索引 1 开始删，跳过 system
int dropFrom = (history.get(0).role().equals("system")) ? 1 : 0;
```

不这么写，聊久了人设会被当成"旧历史"裁掉，模型开始放飞自我。

### ② 历史必须有上限

```java
private static final int MAX_HISTORY_MESSAGES = 20;
```

超过就成对丢弃最早的一问一答（丢一条会让 role 错位，模型可能困惑）。

### ③ `/reset` 的语义：清记忆、留人设

```java
history.removeIf(m -> !m.role().equals("system"));
```

这回答了 Day 1 文档里的自测第 3 题：**想让模型忘掉前面，清空客户端数组即可，不用动模型。**

---

## 五、system 消息 vs user 消息（Day 2 重点问答）

| 维度 | system | user |
|---|---|---|
| 位置 | 永远 `messages[0]`，一条 | 每轮追加在末尾 |
| 作用 | 定"它是谁、守什么规则"（人设/语气/格式/边界） | 定"这次要它做什么"（任务） |
| 优先级 | 高，规则性约束 | 低，内容性请求 |
| 生命周期 | 长期（裁剪时保留） | 短期（可能被裁掉） |

一句话：**system 是舞台设定，user 是台词。**

实操结论：**规则和内容要分离**。把"扮演 HR、只返回 JSON"写进 system，把简历原文放进 user —— 否则轮数一多，模型会淡忘 user 里的角色要求。

⚠️ 补充：system 不是安全边界。涉及权限的必须靠代码校验，不能靠 prompt 拦——这是做企业交付必须懂的一条。

---

## 六、LeetCode 20：有效的括号

```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c == '(')      stack.push(')');    // 压入"期望的右括号"
        else if (c == '[') stack.push(']');
        else if (c == '{') stack.push('}');
        else if (stack.isEmpty() || stack.pop() != c) return false;
    }
    return stack.isEmpty();                    // 必须判空！
}
```

三个要点：

1. **压入的是"期望的右括号"**，弹出时一行 `!= c` 就能比对，省掉映射表；
2. 用 `ArrayDeque` 而非 `Stack`（后者是同步的历史遗留类）；
3. `return stack.isEmpty()`，否则 `"((("` 会误判为 true。

复杂度：时间 O(n)，空间 O(n)。

---

## 七、今天的小坑

| 现象 | 原因 | 解法 |
|---|---|---|
| IDEA 里看不到新增的 3 个类 | 磁盘已变，IDE 未刷新虚拟文件系统 | `Ctrl + Alt + Y`（Reload from Disk）或 Maven 面板 ⟳ |
| `-Dexec.mainClass` 不起作用 | pom 里把 `mainClass` 写死了，命令行属性覆盖不了 | 改成 `${exec.mainClass}` 属性占位 |

---

## 八、自测三问

1. `trimHistory()` 为什么从索引 `1` 开始删，而不是 `0`？
   → system 人设必须常驻，否则聊久了人设消失。
2. 如果删掉 `history.add(ChatMessage.assistant(content))` 会怎样？
   → 模型只记得自己"被问过什么"，不记得"答过什么"，追问时会自我矛盾。
3. `/reset` 后人设为什么还在？
   → `reset()` 用 `removeIf` 只删非 system 的消息。

---

## 九、下一步（Day 3）

1. **流式输出**：`stream: true` + 解析 SSE，做打字机效果；
2. **结构化输出**：让模型扮演 HR，把简历原文提取成严格 JSON（为 Day 4 转实体类做铺垫）。

---

## 十、术语翻译（大白话版）

| 术语 | 人话解释 |
|---|---|
| **无状态** | 服务器**不记事**。每次对话它都当"第一次见你" |
| **上下文 / 历史** | 你把之前聊过的内容**一并再发一遍**，它才显得"记得" |
| **messages 数组** | 就是把历史消息按顺序排成的一列（像聊天记录的列表） |
| **system / user / assistant** | 三个角色：**定规矩的人 / 你 / 它自己** |
| **人设（system prompt）** | 开场前给它的一张"岗位说明书"，决定它的身份和语气 |
| **token 计费** | 按"半个词"收费。历史越长，每次重发的字数越多，钱越多 |
| **record** | Java 里一种**只装数据、不许改**的小盒子（省掉一堆样板代码） |
| **工厂方法** | 给盒子起名字的快捷方式。写 `user("你好")` 比手写 `"role":"user"` 不容易拼错 |
| **职责分离** | 一个类只管一件事：`Main` 管说话，`AIService` 管联系模型 |
| **栈 Stack** | 像**叠盘子**：后放上去的先拿走（后进先出） |
| **ArrayDeque** | Java 里更好用的"叠盘子"工具（比老的 `Stack` 快） |
| **边界用例** | 极端情况，比如空字符串、只有一个左括号 `"("` |
| **时间复杂度 O(n)** | 数据翻一倍，耗时大致也翻一倍 |
| **链式调用** | 一句接一句地配置，像 `.temperature(0)` 这样连着写 |

---

*记录于 Day 2。工程位置：`D:\dev\projects\day1-ai`*
