# Day 5 复盘：给仓库加上筛选、排序、分页

## 零基础导读（30 秒看完骨架）

今天干的事，用一句话说：

> **从一堆简历里，按条件挑出人、排好队、一次只给一页。**

就像招聘时翻抽屉：
1. **筛选**——只要"会 Redis 的""在杭州的"；
2. **排序**——从经验最多的开始排；
3. **分页**——一次只看 10 份，别一次给你 1000 份。

这三件事是**所有后台系统的标配**。你以后用 SQL 写 `WHERE / ORDER BY / LIMIT`，本质就是这三步。

---

## 一、为什么顺序不能乱：筛选 → 排序 → 分页

这是今天最重要的一条。

```
❌ 错误顺序：先分页，再筛选
   第 1 页取 10 条 → 再从中挑合格的
   结果：只拿到"第 1 页里的合格者"，可能只有 2 个人，
        而全库其实有 50 个合格者，你永远看不到。

✅ 正确顺序：先筛选，再排序，最后分页
   全库筛出 50 个合格者 → 按经验排序 → 取第 1 页 10 条
```

**一句话：分页必须是最后一步。** 先分页等于"只在第一页里找"，等于漏人。

---

## 二、三个动作分别怎么实现

### ① 筛选：一堆条件，逐个判断"不符合就淘汰"

```java
private static boolean matches(Resume r, Criteria c) {
    if (c.skill != null && !hasSkill(r, c.skill)) return false;   // 不满足就 false
    if (c.minMonths != null && r.monthsOfExperience() < c.minMonths) return false;
    if (c.city != null && !contains(r.city(), c.city)) return false;
    ...
    return true;   // 全部通过才算合格
}
```

这写法叫「**卫语句**」：条件不满足就提前 `return false`，比一层层嵌套 `if` 好读得多。

多个条件之间是 **"并且"（AND）** 的关系——所有条件都满足才留下。

### ② 排序：告诉程序"按哪个字段、从大到小还是小到大"

```java
Comparator<Resume> byExp = Comparator.comparingInt(Resume::monthsOfExperience);
byExp.reversed()   // 加 .reversed() 就是倒序
```

`Comparator.comparingInt(Resume::monthsOfExperience)` 的意思是：
> "比较两份简历时，看它们的 `monthsOfExperience` 字段。"

`::` 这个写法叫**方法引用**，等价于 `r -> r.monthsOfExperience()`，只是更短。

### ③ 分页：算出"从第几条到第几条"

```java
int from = (pageNo - 1) * pageSize;    // 第 3 页每页 10 条 → 从第 20 条开始（下标）
int to   = Math.min(from + pageSize, total);
list.subList(from, to)
```

注意：**下标从 0 开始**，所以"第 3 页"要减 1 再乘。
`Math.min` 是为了防止最后一页不够数时越界。

---

## 三、跑出来的真实结果

### 筛选：会 Redis 的人

```
→ 陈静（2.2 年）
→ 王强（8 个月）
→ 张伟（2 个月）
第 1/1 页，共 3 条，本页 3 条
```

6 份简历里，会 Redis 的只有 3 个。**注意顺序是按经验倒序**——因为我在调用时同时指定了排序。

### 分页：每页 2 条

```
第 1/3 页：陈静（2.2 年）、李娜（1.2 年）
第 2/3 页：王强（8 个月）、刘洋（5 个月）
第 3/3 页：张伟（2 个月）、赵敏（无经验）
```

### 组合查询：杭州 + 会 Java + 经验 ≥ 3 个月

```
→ 王强（杭州，8 个月，项目 2）
→ 刘洋（杭州，5 个月，项目 2）
共 2 条
```

6 个人里同时满足三个条件的只有 2 个。**这就是招聘系统每天在做的事。**

---

## 四、边界情况：真正区分新手和老手的地方

| 情况 | 新手写法 | 我们的处理 |
|---|---|---|
| 查不到数据 | 崩 / 返回 null | 返回空列表 + `共 0 条`，`totalPages()` 返回 0 |
| 用户输入页码 999 | 数组越界崩 | **自动回退到最后一页** |
| 用户输入页码 -1 | 崩 | 自动纠正为第 1 页 |
| 每页填 0 或负数 | 除零异常 | 自动用默认值 10 |

实测：

```
查不存在的技能 → 共 0 条，0 页
页码填 999     → 自动回退到 第 3/3 页
每页填 0       → 自动用默认值 第 1/1 页
```

> **面试点**：面试官问"你的接口怎么处理异常参数"，答"分页参数做了边界兜底、页码越界自动回退最后一页"，比说"加了 try-catch"专业得多。

---

## 五、顺手修掉的一个 bug：重复入库

跑第二遍时发现：

```
=== 查真实仓库（3 份）===
  → 张伟（2 个月）
  → 张伟（2 个月）
  → 张伟（2 个月）
```

原因是 `Day4Demo` 跑了三次，每次 `save()` 都无脑追加一行。

**根因**：没有"唯一性约束"。

**修法**（对应数据库的"主键/唯一索引"）：

```java
public boolean save(Resume resume) throws IOException {
    if (exists(resume)) {
        return false;      // 已存在，跳过
    }
    ...
}

public boolean exists(Resume resume) throws IOException {
    for (Resume r : findAll()) {
        if (Objects.equals(r.name(), resume.name())
                && Objects.equals(r.phone(), resume.phone())
                && Objects.equals(r.school(), resume.school())) {
            return true;
        }
    }
    return false;
}
```

用「姓名 + 手机号 + 学校」当**业务主键**——这三个字段组合起来，基本能唯一确定一个人。

> 这事的通用名字叫「**幂等**」：同样的操作执行多次，结果和执行一次一样。
> 面试常问："用户重复点提交按钮怎么办？" 答案就是这个。

---

## 六、Java 语法小课堂

| 写法 | 意思 |
|---|---|
| `Comparator.comparingInt(Resume::monthsOfExperience)` | 造一个"按经验字段比较"的规则 |
| `.reversed()` | 把比较规则反过来（从大到小） |
| `Resume::monthsOfExperience` | 方法引用，等价于 `r -> r.monthsOfExperience()` |
| `List.subList(from, to)` | 取列表的一段（含 from，不含 to） |
| `switch` 表达式（箭头写法） | Java 14+ 的新写法，直接返回值，不用 break |
| `record Page<T>(...)` | 带泛型的 record，`T` 是"任意类型"的占位符 |
| `Objects.equals(a, b)` | 安全的比较，两个都是 null 也不崩 |
| `Math.min(a, b)` | 取小的那个 |

---

## 七、自测三问

1. 为什么分页必须放在最后一步？
   → 先分页会把范围限死在"第一页"，漏掉后面的合格数据。
2. 用户传 `pageNo=999` 但只有 3 页，应该怎么处理？
   → 回退到第 3 页（或返回空列表），不能抛异常崩掉。
3. 什么叫"幂等"？我们的 `save` 怎么做到的？
   → 同样操作执行多次结果不变；靠"存在就跳过"保证。

---

## 八、术语翻译（大白话版）

| 术语 | 人话 |
|---|---|
| **筛选 / filter** | 按条件挑人，不符合的淘汰 |
| **排序 / sort** | 按某个字段排队 |
| **分页 / pagination** | 一次只给一页，别一次给全部 |
| **Comparator** | "比较规则"，告诉程序怎么排队 |
| **卫语句** | 条件不满足就提前 return，少写嵌套 |
| **业务主键** | 用来唯一确定一条记录的字段组合（如 姓名+手机号） |
| **幂等** | 同样操作做多次，结果和做一次一样 |
| **边界情况** | 空数据、页码越界、参数为 0 这些极端输入 |

---

## 九、算法题详解：第 704 题「二分查找」

> 零基础版。这题是"有序数组里快速找数"的标准解法。

### 1）题目在说什么

给一个**已经排好序**的数组，和一个目标值，找出目标值的位置；找不到返回 -1。

```
nums = [-1, 0, 3, 5, 9, 12], target = 9
答案 = 4（因为 9 在第 4 个位置）
```

### 2）最笨的办法：从头找到尾

```java
for (int i = 0; i < nums.length; i++) {
    if (nums[i] == target) return i;
}
return -1;
```

能过，但**慢**：100 万个数字，最坏要找 100 万次。而数组是排好序的——这个信息完全没用上。

### 3）聪明办法：每次砍掉一半

想象你在字典里找 "M"：
- 你**不会**从第一页开始翻；
- 你会直接翻到中间，看是 L 还是 N —— 然后决定往前翻还是往后翻。

**每次砍掉一半，100 万个数只需要 20 次就能找到。**

```
left = 0, right = 5, mid = 2
nums[2] = 3 < 9  →  目标在右半边，left = 3

left = 3, right = 5, mid = 4
nums[4] = 9 == 9  →  找到了，返回 4
```

### 4）完整代码（逐行解释）

```java
public static int search(int[] nums, int target) {
    int left = 0;                    // 左边界
    int right = nums.length - 1;     // 右边界（最后一个下标）

    while (left <= right) {          // 注意是 <=，不是 <
        int mid = left + (right - left) / 2;   // 中点，防溢出的写法

        if (nums[mid] == target) {
            return mid;              // 找到了
        } else if (nums[mid] < target) {
            left = mid + 1;          // 目标在右边
        } else {
            right = mid - 1;         // 目标在左边
        }
    }
    return -1;                       // 找完了没有
}
```

**三个细节，全是面试常考的：**

1. `while (left <= right)` —— 必须是 `<=`。写成 `<` 的话，只剩一个元素时会直接跳出，漏掉答案。
2. `mid = left + (right - left) / 2` —— 不写 `(left + right) / 2` 是因为两个大数相加可能**溢出**变成负数。
3. `left = mid + 1` / `right = mid - 1` —— 加减 1 是必须的，否则可能死循环。

### 5）快慢对比

| 办法 | 100 万个数字 | 说法 |
|---|---|---|
| 从头找 | 最坏 100 万次 | O(n) |
| 二分 | 最多 20 次 | O(log n) |

**这就是为什么数据库索引要用 B+ 树——本质就是二分思想。**

### 6）三个常见坑

1. `while (left < right)` → 漏掉最后一个元素
2. `(left + right) / 2` → 大数组时溢出
3. `left = mid` 而不是 `left = mid + 1` → 死循环

### 7）面试官可能的追问

| 追问 | 怎么答 |
|---|---|
| "数组没排序能用二分吗？" | 不能，二分的**前提就是有序** |
| "如果有重复元素，要返回第一个？" | 找到后不急着返回，继续往左收缩边界 |
| "这题和今天的筛选有什么共同点？" | 都是"用有序/结构化信息，把 O(n) 降到 O(log n)" |

> 📎 可运行代码：`src/main/java/com/example/leetcode/LeetCodeSolutions.java`

---

## 十、明天（Day 6）

1. 这些查询能不能用 SQL 写？对比一下 Java 手写和 SQL 的差别；
2. 引入 H2 数据库，把 `resumes.jsonl` 换成真正的表；
3. 为第三周的 Spring Boot 接口做准备。

---

*记录于 Day 5。工程位置：`D:\dev\projects\day1-ai`*