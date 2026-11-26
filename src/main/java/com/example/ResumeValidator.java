package com.example;

import com.example.model.Resume;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 校验器：判断一份简历"能不能入库"。
 *
 * 思路：不抛异常，而是把问题一条条收集成清单返回。
 * 这样调用方可以决定：全部通过才入库、部分问题警告、或者整份丢弃重试。
 * 这比"抛异常中断"更实用 —— 批量处理 100 份简历时，你不想因为第 3 份有问题就全停。
 */
public class ResumeValidator {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w-]{2,}$");
    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 返回问题清单；空表示没问题 */
    public static List<String> validate(Resume r) {
        List<String> issues = new ArrayList<>();

        if (isBlank(r.name())) {
            issues.add("姓名为空");
        }
        if (isBlank(r.phone())) {
            issues.add("手机号为空");
        } else if (!MOBILE.matcher(r.phone()).matches()) {
            issues.add("手机号格式不合法：" + r.phone());
        }
        if (isBlank(r.email())) {
            issues.add("邮箱为空");
        } else if (!EMAIL.matcher(r.email()).matches()) {
            issues.add("邮箱格式不合法：" + r.email());
        }
        if (r.monthsOfExperience() < 0) {
            issues.add("经验月数为负：" + r.monthsOfExperience());
        }
        if (r.monthsOfExperience() > 600) {
            issues.add("经验月数异常（超过 50 年）：" + r.monthsOfExperience());
        }
        if (r.skills() == null || r.skills().isEmpty()) {
            issues.add("技能列表为空");
        }
        if (r.projectCount() < 0) {
            issues.add("项目数量为负：" + r.projectCount());
        }
        if (isBlank(r.jobIntention())) {
            issues.add("求职意向为空（不影响入库，建议补充）");
        }
        return issues;
    }

    /** 是否达到入库标准：只要没有"硬伤"就放行（求职意向缺失算软提醒） */
    public static boolean canSave(List<String> issues) {
        return issues.stream().noneMatch(i -> !i.contains("建议补充"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}