package com.example;

import com.example.model.Resume;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Day 4 演示：JSON 文本 → Java 对象 → 校验 → 入库 → 查询。
 *
 * 运行前提：先跑过 HrExtractDemo（生成 output/resume.json）。
 * 如果没有，本程序会自动调用模型重新抽取一次。
 */
public class Day4Demo {

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path jsonFile = root.resolve("output").resolve("resume.json");

        // ---------- 1. 拿到 JSON ----------
        String json;
        if (Files.exists(jsonFile)) {
            json = Files.readString(jsonFile, StandardCharsets.UTF_8);
            System.out.println("=== 1. 读取已有 JSON：" + jsonFile.getFileName() + " ===");
        } else {
            System.out.println("=== 1. 没有现成 JSON，现场调用模型抽取 ===");
            String key = System.getenv("DEEPSEEK_API_KEY");
            String resume = Files.readString(root.resolve("sample-resume.txt"), StandardCharsets.UTF_8);
            json = new ResumeExtractor(key).extract(resume);
        }

        // ---------- 2. JSON → Java 对象 ----------
        Resume resume = ResumeParser.parse(json);
        System.out.println("\n=== 2. 转成 Java 对象 ===");
        System.out.println("  姓名        : " + resume.name());
        System.out.println("  手机（已规范化）: " + resume.phone());
        System.out.println("  邮箱        : " + resume.email());
        System.out.println("  城市        : " + resume.city());
        System.out.println("  经验（月）   : " + resume.monthsOfExperience() + "  → " + resume.experienceText());
        System.out.println("  学历 / 学校  : " + resume.education() + " / " + resume.school());
        System.out.println("  技能（" + resume.skills().size() + " 项）: " + resume.skills());
        System.out.println("  最近公司    : " + resume.latestCompany());
        System.out.println("  项目数      : " + resume.projectCount());

        // ---------- 3. 校验 ----------
        System.out.println("\n=== 3. 校验 ===");
        List<String> issues = ResumeValidator.validate(resume);
        if (issues.isEmpty()) {
            System.out.println("  ✅ 全部通过");
        } else {
            issues.forEach(i -> System.out.println("  ⚠️ " + i));
        }
        System.out.println("  可入库：" + ResumeValidator.canSave(issues));

        // ---------- 4. 入库 ----------
        ResumeStore store = new ResumeStore(root.resolve("data").resolve("resumes.jsonl"));
        if (ResumeValidator.canSave(issues)) {
            store.save(resume);
            System.out.println("\n=== 4. 已写入 " + store.file().getFileName() + "，当前共 " + store.count() + " 份 ===");
        }

        // ---------- 5. 演示校验拦截坏数据 ----------
        System.out.println("\n=== 5. 脏数据拦截演示 ===");
        String badJson = """
                {"name":"","phone":"12345","email":"not-an-email","city":"上海",
                 "yearsOfExperience":"two years","education":"本科","school":null,
                 "jobIntention":null,"skills":["Java","  ","Java"],"latestCompany":null,
                 "projectCount":-1}
                """;
        Resume bad = ResumeParser.parse(badJson);
        System.out.println("  解析结果 → 姓名=[" + bad.name() + "] 手机=[" + bad.phone()
                + "] 经验月数=" + bad.monthsOfExperience() + " 技能=" + bad.skills());
        List<String> badIssues = ResumeValidator.validate(bad);
        badIssues.forEach(i -> System.out.println("  ⛔ " + i));
        System.out.println("  可入库：" + ResumeValidator.canSave(badIssues) + "（应该为 false）");

        // ---------- 6. 查询 ----------
        System.out.println("\n=== 6. 查库：谁会 Redis？ ===");
        List<Resume> hits = store.findBySkill("Redis");
        if (hits.isEmpty()) {
            System.out.println("  没有找到");
        } else {
            for (Resume r : hits) {
                System.out.println("  → " + r.name() + "（" + r.school() + "，" + r.experienceText() + "）");
            }
        }
    }
}