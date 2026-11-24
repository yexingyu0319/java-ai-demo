package com.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 控制台多轮对话（Day 3：默认走流式输出，逐字打印）。
 *
 * Main 只做三件事：读输入、调 AIService、打印。
 */
public class Main {

    private static final String SYSTEM_PROMPT = """
            你是一位耐心的 Java 后端技术导师，服务对象是计算机专业大三学生。
            要求：
            1. 用中文回答，先说结论再解释，不要长篇大论；
            2. 涉及代码时给出可运行的 Java 示例；
            3. 学生提到"我"的个人信息（名字、项目、目标）时，要记住并在后续对话中体现。
            """;

    public static void main(String[] args) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[ERROR] 环境变量 DEEPSEEK_API_KEY 未设置。");
            return;
        }

        AIService ai = new AIService(apiKey, "deepseek-chat", SYSTEM_PROMPT);

        System.out.println("========================================");
        System.out.println("  Java 版 AI 对话（多轮记忆 + 流式输出）");
        System.out.println("  exit 退出 / /reset 清空记忆 / /stream 切换流式");
        System.out.println("========================================");

        boolean streaming = true;
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        while (true) {
            System.out.print("\n你> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break;
            }
            if (input.equalsIgnoreCase("/reset")) {
                ai.reset();
                System.out.println("[已清空记忆，人设保留]");
                continue;
            }
            if (input.equalsIgnoreCase("/stream")) {
                streaming = !streaming;
                System.out.println("[流式输出：" + (streaming ? "开" : "关") + "]");
                continue;
            }

            try {
                if (streaming) {
                    // 关键：每收到一小段就立刻打印，不清空、不换行 → 视觉上的"打字机"
                    AtomicInteger chunks = new AtomicInteger();
                    System.out.print("AI> ");
                    Reply reply = ai.chatStream(input, piece -> {
                        System.out.print(piece);
                        System.out.flush();
                        chunks.incrementAndGet();
                    });
                    System.out.println();
                    System.out.printf("    [流式片段 %d 个 | 输入 %d / 输出 %d tokens | 累计 %d 轮]%n",
                            chunks.get(), reply.promptTokens(), reply.completionTokens(), ai.rounds());
                } else {
                    Reply reply = ai.chat(input);
                    System.out.println("AI> " + reply.content());
                    System.out.printf("    [输入 %d / 输出 %d tokens | 累计 %d 轮]%n",
                            reply.promptTokens(), reply.completionTokens(), ai.rounds());
                }
            } catch (IOException e) {
                System.out.println("[请求失败] " + e.getMessage());
            }
        }

        System.out.printf("%n[会话结束] 共 %d 轮，累计消耗 %d tokens%n", ai.rounds(), ai.totalTokens());
    }
}