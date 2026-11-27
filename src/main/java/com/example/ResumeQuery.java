package com.example;

import com.example.model.Resume;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 简历查询：筛选、排序、分页。
 *
 * 这三个动作是所有后台系统的标配，不管你用 Java、Python 还是 SQL，
 * 概念完全一样：
 *   筛选（filter）→ 从一堆里挑出符合条件的
 *   排序（sort）  → 把挑出来的按某个字段排队
 *   分页（page）  → 一次只返回一页，不是全部
 *
 * 顺序很重要：先筛 → 再排 → 最后分页。
 * 如果先分页再筛选，你拿到的就是"第 1 页里的合格者"，而不是"合格者里的第 1 页"。
 */
public class ResumeQuery {

    /** 查询条件：都用"可空"表示"不限制这一项" */
    public static class Criteria {
        public String skill;              // 要求会的技能，如 "Redis"
        public Integer minMonths;         // 经验下限（月）
        public Integer maxMonths;         // 经验上限（月）
        public String city;               // 城市
        public String school;             // 学校（包含即可）
        public String keyword;            // 姓名/技能/公司里的关键词

        public Criteria skill(String v)      { this.skill = v; return this; }
        public Criteria minMonths(int v)     { this.minMonths = v; return this; }
        public Criteria maxMonths(int v)     { this.maxMonths = v; return this; }
        public Criteria city(String v)       { this.city = v; return this; }
        public Criteria school(String v)     { this.school = v; return this; }
        public Criteria keyword(String v)    { this.keyword = v; return this; }
    }

    /** 排序方式 */
    public enum SortBy {
        EXPERIENCE_DESC,   // 经验多→少
        EXPERIENCE_ASC,    // 经验少→多
        NAME_ASC,          // 姓名拼音序
        PROJECT_DESC       // 项目数多→少
    }

    /** 一页的结果 */
    public record Page<T>(
            List<T> items,   // 这一页的数据
            int total,       // 符合条件的一共几条
            int pageNo,      // 当前第几页（从 1 开始）
            int pageSize      // 每页几条
    ) {
        public int totalPages() {
            return pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        }

        public boolean hasNext() {
            return pageNo < totalPages();
        }

        public String summary() {
            return String.format("第 %d/%d 页，共 %d 条，本页 %d 条",
                    pageNo, totalPages(), total, items.size());
        }
    }

    /** 主方法：筛选 → 排序 → 分页 */
    public static Page<Resume> search(List<Resume> all, Criteria c, SortBy sortBy,
                                      int pageNo, int pageSize) {
        // ---------- 1. 筛选 ----------
        List<Resume> filtered = new ArrayList<>();
        for (Resume r : all) {
            if (matches(r, c)) {
                filtered.add(r);
            }
        }

        // ---------- 2. 排序 ----------
        if (sortBy != null) {
            filtered.sort(comparatorOf(sortBy));
        }

        // ---------- 3. 分页 ----------
        return paginate(filtered, pageNo, pageSize);
    }

    /** 判断一份简历是否符合全部条件（条件之间是"并且"的关系） */
    private static boolean matches(Resume r, Criteria c) {
        if (c == null) {
            return true;
        }
        if (c.skill != null && !hasSkill(r, c.skill)) {
            return false;
        }
        if (c.minMonths != null && r.monthsOfExperience() < c.minMonths) {
            return false;
        }
        if (c.maxMonths != null && r.monthsOfExperience() > c.maxMonths) {
            return false;
        }
        if (c.city != null && !contains(r.city(), c.city)) {
            return false;
        }
        if (c.school != null && !contains(r.school(), c.school)) {
            return false;
        }
        if (c.keyword != null && !keywordHit(r, c.keyword)) {
            return false;
        }
        return true;
    }

    private static boolean hasSkill(Resume r, String skill) {
        if (r.skills() == null) {
            return false;
        }
        for (String s : r.skills()) {
            if (s.equalsIgnoreCase(skill)) {
                return true;
            }
        }
        return false;
    }

    /** 忽略大小写、忽略 null 的"包含"判断 */
    private static boolean contains(String text, String part) {
        return text != null && part != null
                && text.toLowerCase().contains(part.toLowerCase());
    }

    private static boolean keywordHit(Resume r, String kw) {
        if (contains(r.name(), kw) || contains(r.latestCompany(), kw)
                || contains(r.school(), kw) || contains(r.jobIntention(), kw)) {
            return true;
        }
        if (r.skills() != null) {
            for (String s : r.skills()) {
                if (contains(s, kw)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Comparator<Resume> comparatorOf(SortBy sortBy) {
        Comparator<Resume> byExp = Comparator.comparingInt(Resume::monthsOfExperience);
        Comparator<Resume> byName = Comparator.comparing(
                r -> r.name() == null ? "" : r.name());
        Comparator<Resume> byProject = Comparator.comparingInt(Resume::projectCount);
        return switch (sortBy) {
            case EXPERIENCE_DESC -> byExp.reversed();
            case EXPERIENCE_ASC -> byExp;
            case NAME_ASC -> byName;
            case PROJECT_DESC -> byProject.reversed();
        };
    }

    /** 分页：把一页切出来。注意 pageNo 从 1 开始，越界要兜住 */
    static <T> Page<T> paginate(List<T> list, int pageNo, int pageSize) {
        if (pageSize <= 0) {
            pageSize = 10;
        }
        int total = list.size();
        int totalPages = (total + pageSize - 1) / pageSize;

        // 页码兜底：小于 1 按 1，超过总页数按最后一页
        if (pageNo < 1) {
            pageNo = 1;
        }
        if (totalPages > 0 && pageNo > totalPages) {
            pageNo = totalPages;
        }

        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<T> items = (from >= total) ? new ArrayList<>() : new ArrayList<>(list.subList(from, to));
        return new Page<>(items, total, pageNo, pageSize);
    }
}