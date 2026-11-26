package com.example;

import com.alibaba.fastjson.JSON;
import com.example.model.Resume;

import java.util.List;

/**
 * 验证：fastjson 1.2.83 到底能不能处理 record。
 * 这是为了回答"为什么 Day 4 又引入了 Jackson"。
 */
public class FastjsonRecordTest {

    public static void main(String[] args) {
        Resume r = new Resume(
                "张伟", "13800138000", "zhangwei@example.com", "杭州",
                2, "本科", "杭州电子科技大学", "Java 后端开发工程师（实习）",
                List.of("Java", "Redis", "Docker"), "某电商科技公司", 1);

        System.out.println("=== 1. fastjson 把 record 转成 JSON ===");
        String json = JSON.toJSONString(r);
        System.out.println("结果：" + json);
        System.out.println("长度：" + json.length() + " 字符");

        System.out.println("\n=== 2. fastjson 把 JSON 转回 record ===");
        String sample = "{\"name\":\"李娜\",\"phone\":\"13900139000\",\"email\":\"lina@example.com\","
                + "\"city\":\"北京\",\"monthsOfExperience\":12,\"education\":\"硕士\","
                + "\"school\":\"某大学\",\"jobIntention\":\"后端开发\",\"skills\":[\"Java\"],"
                + "\"latestCompany\":\"某公司\",\"projectCount\":2}";
        try {
            Resume back = JSON.parseObject(sample, Resume.class);
            System.out.println("结果：" + back);
            System.out.println("姓名字段：" + back.name());
        } catch (Exception e) {
            System.out.println("失败：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}