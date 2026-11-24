package com.example;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Day 3 证据程序：同一个问题，分别用流式和非流式跑一遍，对比"首字延迟"和"总耗时"。
 *
 * 结论预期：两者总耗时接近，但流式的首个字出现得早得多 ——
 * 这就是"感知速度"的来源，也是所有 AI 产品都做打字机效果的原因。
 */
public class StreamDemo {

    private static final String SYSTEM = "你是 Java 技术讲师，回答分点、简洁，不要超过 250 字。";

    public static void main(String[] args) throws Exception {
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("[ERROR] 环境变量 DEEPSEEK_API_KEY 未设置。");
            return;
        }
        String question = (args.length > 0)
                ? String.join(" ", args)
                : "用大约 200 字解释 JVM 内存模型，分点说明。";

        // ---------- 1. 流式 ----------
        System.out.println("========== 流式（stream=true） ==========");
        AIService s1 = new AIService(key, "deepseek-chat", SYSTEM);

        AtomicLong firstTokenMs = new AtomicLong(0);
        AtomicInteger chunks = new AtomicInteger();
        long t0 = System.currentTimeMillis();

        Reply r1 = s1.chatStream(question, piece -> {
            if (firstTokenMs.get() == 0) {
                firstTokenMs.set(System.currentTimeMillis() - t0);
            }
            chunks.incrementAndGet();
            System.out.print(piece);   // 立刻打印，不换行
            System.out.flush();        // 强制刷出缓冲区，否则会攒一批才显示
        });
        long totalStream = System.currentTimeMillis() - t0;
        System.out.println();
        System.out.printf("%n[流式结果] 首字 %d ms | 全部完成 %d ms | 片段 %d 个 | 输出 %d tokens%n",
                firstTokenMs.get(), totalStream, chunks.get(), r1.completionTokens());

        // ---------- 2. 非流式（对照） ----------
        System.out.println("\n========== 非流式（stream=false） ==========");
        AIService s2 = new AIService(key, "deepseek-chat", SYSTEM);
        long t1 = System.currentTimeMillis();
        Reply r2 = s2.chat(question);
        long totalPlain = System.currentTimeMillis() - t1;
        System.out.println(r2.content());
        System.out.printf("%n[非流式结果] 首字 = 全部到达 %d ms | 输出 %d tokens%n", totalPlain, r2.completionTokens());

        // ---------- 3. 对比 ----------
        System.out.println("\n========== 对比 ==========");
        System.out.printf("流式首字等待：%d ms%n", firstTokenMs.get());
        System.out.printf("非流式首字等待：%d ms（用户盯着空屏幕等这么久）%n", totalPlain);
        System.out.printf("总耗时：流式 %d ms vs 非流式 %d ms（差距主要来自网络抖动）%n", totalStream, totalPlain);
        System.out.printf("感知提速：约 %.1f 倍%n", (double) totalPlain / Math.max(1, firstTokenMs.get()));
    }
}