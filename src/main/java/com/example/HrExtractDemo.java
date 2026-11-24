package com.example;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Day 3 演示入口：读简历原文 → 调模型抽取 → 打印 JSON。
 *
 * 运行方式（IDEA 里直接 Run 这个类即可）：
 *   1) 默认读 sample-resume.txt
 *   2) 也可以把简历粘贴进 resume.txt，程序优先读它
 */
public class HrExtractDemo {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[ERROR] 环境变量 DEEPSEEK_API_KEY 未设置。");
            return;
        }

        Path dir = Path.of("").toAbsolutePath();
        Path custom = dir.resolve("resume.txt");
        Path sample = dir.resolve("sample-resume.txt");
        Path file = Files.exists(custom) ? custom : sample;

        if (!Files.exists(file)) {
            System.err.println("[ERROR] 找不到简历文件，当前目录：" + dir);
            return;
        }

        String resume = Files.readString(file, StandardCharsets.UTF_8);
        System.out.println("=== 读取简历：" + file.getFileName() + "（" + resume.length() + " 字符）===");

        long start = System.currentTimeMillis();
        ResumeExtractor extractor = new ResumeExtractor(apiKey);
        String json = extractor.extract(resume);
        long cost = System.currentTimeMillis() - start;

        System.out.println("\n=== 抽取结果（原始 JSON 字符串）===");
        System.out.println(json);

        System.out.println("\n=== 美化后 ===");
        System.out.println(pretty(json));

        // 顺手存一份，明天 Day 4 要把它转成 Java 对象并入库
        Path out = dir.resolve("output");
        Files.createDirectories(out);
        Path outFile = out.resolve("resume.json");
        Files.writeString(outFile, json, StandardCharsets.UTF_8);
        System.out.println("\n[已保存] " + outFile + "  （耗时 " + cost + " ms）");
    }

    private static String pretty(String json) {
        return com.alibaba.fastjson.JSON.toJSONString(
                com.alibaba.fastjson.JSON.parseObject(json), true);
    }
}