package com.example;

import com.example.model.Resume;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Day 5 演示：给简历仓库加上筛选、排序、分页。
 *
 * 为了让结果更明显，程序会先造 6 份测试数据（不依赖数据库），
 * 再依次演示：筛选、排序、分页、组合查询。
 */
public class Day5Demo {

    public static void main(String[] args) throws Exception {
        List<Resume> data = sampleData();
        System.out.println("=== 数据池：" + data.size() + " 份简历 ===");
        data.forEach(r -> System.out.printf("  %-4s %-6s %3d 个月  项目 %d  技能 %d 项  最近公司：%s%n",
                r.name(), r.city(), r.monthsOfExperience(), r.projectCount(),
                r.skills() == null ? 0 : r.skills().size(),
                r.latestCompany() == null ? "-" : r.latestCompany()));

        // ---------- 1. 只筛选 ----------
        System.out.println("\n=== 1. 筛选：会 Redis 的人 ===");
        var p1 = ResumeQuery.search(data, new ResumeQuery.Criteria().skill("Redis"),
                ResumeQuery.SortBy.EXPERIENCE_DESC, 1, 10);
        p1.items().forEach(r -> System.out.println("  → " + r.name() + "（" + r.experienceText() + "）"));
        System.out.println("  " + p1.summary());

        // ---------- 2. 范围筛选 ----------
        System.out.println("\n=== 2. 筛选：经验 ≥ 6 个月 ===");
        var p2 = ResumeQuery.search(data, new ResumeQuery.Criteria().minMonths(6),
                ResumeQuery.SortBy.EXPERIENCE_DESC, 1, 10);
        p2.items().forEach(r -> System.out.println("  → " + r.name() + "（" + r.experienceText() + "）"));
        System.out.println("  " + p2.summary());

        // ---------- 3. 排序 ----------
        System.out.println("\n=== 3. 排序：按经验从多到少（不筛选）===");
        var p3 = ResumeQuery.search(data, new ResumeQuery.Criteria(),
                ResumeQuery.SortBy.EXPERIENCE_DESC, 1, 10);
        p3.items().forEach(r -> System.out.println("  → " + r.name() + "（" + r.experienceText() + "）"));

        // ---------- 4. 分页 ----------
        System.out.println("\n=== 4. 分页：每页 2 条，逐页翻 ===");
        for (int page = 1; page <= 3; page++) {
            var p = ResumeQuery.search(data, new ResumeQuery.Criteria(),
                    ResumeQuery.SortBy.EXPERIENCE_DESC, page, 2);
            System.out.println("  " + p.summary());
            p.items().forEach(r -> System.out.println("      " + r.name() + "（" + r.experienceText() + "）"));
        }

        // ---------- 5. 组合查询 ----------
        System.out.println("\n=== 5. 组合：杭州 + 会 Java + 经验 ≥ 3 个月，按项目数排序 ===");
        var c = new ResumeQuery.Criteria().city("杭州").skill("Java").minMonths(3);
        var p5 = ResumeQuery.search(data, c, ResumeQuery.SortBy.PROJECT_DESC, 1, 10);
        p5.items().forEach(r -> System.out.println("  → " + r.name()
                + "（" + r.city() + "，" + r.experienceText() + "，项目 " + r.projectCount() + "）"));
        System.out.println("  " + p5.summary());

        // ---------- 6. 边界：查不到 / 页码越界 ----------
        System.out.println("\n=== 6. 边界情况 ===");
        var none = ResumeQuery.search(data, new ResumeQuery.Criteria().skill("Rust"), null, 1, 10);
        System.out.println("  查不存在的技能 → " + none.summary() + "，" + none.totalPages() + " 页");
        var overflow = ResumeQuery.search(data, new ResumeQuery.Criteria(), null, 999, 2);
        System.out.println("  页码填 999  → 自动回退到 " + overflow.summary());
        var zero = ResumeQuery.search(data, new ResumeQuery.Criteria(), null, 1, 0);
        System.out.println("  每页填 0    → 自动用默认值 " + zero.summary());

        // ---------- 7. 如果本地有真实数据，也查一遍 ----------
        Path real = Path.of("data", "resumes.jsonl");
        if (real.toFile().exists()) {
            ResumeStore store = new ResumeStore(real);
            var all = store.findAll();
            System.out.println("\n=== 7. 查真实仓库（" + all.size() + " 份）===");
            var realPage = ResumeQuery.search(all, new ResumeQuery.Criteria(),
                    ResumeQuery.SortBy.EXPERIENCE_DESC, 1, 10);
            System.out.println("  " + realPage.summary());
            realPage.items().forEach(r -> System.out.println("  → " + r.name() + "（" + r.experienceText() + "）"));
        }
    }

    /** 造测试数据：覆盖不同城市、经验、技能组合，方便看出筛选效果 */
    private static List<Resume> sampleData() {
        List<Resume> list = new ArrayList<>();
        list.add(new Resume("张伟", "13800138000", "zhangwei@example.com", "杭州",
                2, "本科", "杭州电子科技大学", "Java 后端开发",
                List.of("Java", "Redis", "MySQL", "Spring Boot"), "某电商科技公司", 1));
        list.add(new Resume("李娜", "13900139000", "lina@example.com", "北京",
                14, "硕士", "北京邮电大学", "后端开发工程师",
                List.of("Java", "Kafka", "MySQL", "Docker"), "某金融科技公司", 3));
        list.add(new Resume("王强", "13700137000", "wangqiang@example.com", "杭州",
                8, "本科", "浙江大学", "Java 开发工程师",
                List.of("Java", "Redis", "MyBatis"), "某物流平台", 2));
        list.add(new Resume("陈静", "13600136000", "chenjing@example.com", "上海",
                26, "硕士", "复旦大学", "高级后端工程师",
                List.of("Java", "Spring Cloud", "MySQL", "Redis", "Kafka"), "某互联网大厂", 5));
        list.add(new Resume("刘洋", "13500135000", "liuyang@example.com", "杭州",
                5, "本科", "浙江工业大学", "AI 应用开发",
                List.of("Java", "Python", "Prompt 工程"), "某 AI 创业公司", 2));
        list.add(new Resume("赵敏", "13400134000", "zhaomin@example.com", "深圳",
                0, "本科", "深圳大学", "前端开发",
                List.of("JavaScript", "Vue", "CSS"), null, 1));
        return list;
    }
}