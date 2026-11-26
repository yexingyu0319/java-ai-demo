package com.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.model.Resume;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析器：把模型返回的 JSON 文本，变成 Java 对象。
 *
 * 为什么不直接 JSON.parseObject(json, Resume.class)？
 * 因为模型返回的内容【不可信】：
 *   - 数字可能是 "2 years" 这样的字符串
 *   - 手机号可能带短横线或空格
 *   - skills 里可能有空字符串或重复项
 * 直接反射绑定，一旦类型不符就抛异常或静默变成 null，很难排查。
 *
 * 所以这里逐字段安全取值 + 规范化，出问题也能给出清楚的原因。
 */
public class ResumeParser {

    /** 从 JSON 文本解析出一份简历 */
    public static Resume parse(String json) {
        JSONObject o = JSON.parseObject(json);

        return new Resume(
                text(o, "name"),
                normalizePhone(text(o, "phone")),
                text(o, "email"),
                text(o, "city"),
                normalizeMonths(o.get("yearsOfExperience")),   // ← 重点：年 → 月
                text(o, "education"),
                text(o, "school"),
                text(o, "jobIntention"),
                normalizeSkills(o.get("skills")),
                text(o, "latestCompany"),
                toInt(o.get("projectCount"))
        );
    }

    /** 安全取字符串：不存在或不是字符串，都返回 null，绝不抛异常 */
    private static String text(JSONObject o, String key) {
        Object v = o.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    /**
     * 把"经验年数"统一换成"月数"。
     *
     * 为什么必须换：模型给的是 0.2 年这种带小数的估算值，
     * 直接存年份，后面做「经验 >= 1 年」的筛选时会出现 0.2、0.25、0.17 各种抖动值。
     * 换成整数月（2 个月 / 3 个月），既可排序又不会因为精度产生歧义。
     *
     * 还兼容模型给的脏格式：数字 0.2、"2 years"、"6个月"、null。
     */
    private static int normalizeMonths(Object raw) {
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number number) {
            return (int) Math.round(number.doubleValue() * 12);
        }
        String s = String.valueOf(raw).trim();
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(s);
        if (!m.find()) {
            return 0;
        }
        double value = Double.parseDouble(m.group(1));
        // 文本里明确写了"月"，就不要乘 12
        if (s.contains("月")) {
            return (int) Math.round(value);
        }
        return (int) Math.round(value * 12);
    }

    /** 手机号只留数字：138-0013-8000 → 13800138000 */
    private static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        return digits.isEmpty() ? null : digits;
    }

    /** 技能列表：去掉空白项、去掉重复项、保持原有顺序 */
    private static List<String> normalizeSkills(Object raw) {
        if (!(raw instanceof JSONArray array)) {
            return new ArrayList<>();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (Object item : array) {
            if (item == null) {
                continue;
            }
            String s = String.valueOf(item).trim();
            if (!s.isEmpty()) {
                unique.add(s);
            }
        }
        return new ArrayList<>(unique);
    }

    /** 安全取整数：字符串数字也能转，转不了就当 0 */
    private static int toInt(Object raw) {
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}