package com.example.model;

import java.util.List;

/**
 * 一份结构化的简历。
 *
 * 用 record：字段声明完就不用再写 get/set/toString，编译器自动生成。
 * 注意 record 的"读取方法"叫 name()、email()，不是 getName() —— 这点很重要，
 * 它决定了后面序列化时用哪个 JSON 库。
 *
 * 关于工作经验：这里存的是【月份】而不是【年】。
 * 原因见 ResumeParser 的注释 —— 模型给"0.2 年"这种值，存月更准确、也更好比较。
 */
public record Resume(
        String name,          // 姓名
        String phone,         // 手机号（已规范化为纯数字）
        String email,         // 邮箱
        String city,          // 现居城市
        int monthsOfExperience, // 工作/实习经验，单位：月
        String education,     // 最高学历
        String school,        // 学校
        String jobIntention,  // 求职意向
        List<String> skills,  // 技能列表（已去重、去空白）
        String latestCompany, // 最近一段公司
        int projectCount      // 项目数量
) {

    /** 给打印用：把月数换成人话 */
    public String experienceText() {
        if (monthsOfExperience <= 0) {
            return "无经验";
        }
        if (monthsOfExperience < 12) {
            return monthsOfExperience + " 个月";
        }
        return String.format("%.1f 年", monthsOfExperience / 12.0);
    }
}