package com.example;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示 trimHistory() 为什么要从索引 1 开始删。
 * 不做网络请求，纯本地逻辑对比。
 */
public class TrimDemo {

    /** 正确写法：跳过 system */
    static List<String> trimCorrect(List<String> history, int max) {
        List<String> h = new ArrayList<>(history);
        while (h.size() > max) {
            int dropFrom = h.get(0).startsWith("system") ? 1 : 0;
            h.remove(dropFrom);
            if (h.size() > dropFrom) {
                h.remove(dropFrom);
            }
        }
        return h;
    }

    /** 错误写法：从索引 0 开始删 */
    static List<String> trimWrong(List<String> history, int max) {
        List<String> h = new ArrayList<>(history);
        while (h.size() > max) {
            h.remove(0);
            if (h.size() > 0) {
                h.remove(0);
            }
        }
        return h;
    }

    static void print(String title, List<String> list) {
        System.out.println(title);
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("   索引 %d: %s%n", i, list.get(i));
        }
        System.out.println();
    }

    public static void main(String[] args) {
        List<String> history = new ArrayList<>();
        history.add("system   : 你是一位耐心的 Java 导师，只回答 Java 问题");
        history.add("user     : 我叫小明");
        history.add("assistant: 好的小明，记住了");
        history.add("user     : 我叫什么");
        history.add("assistant: 你叫小明");
        history.add("user     : 那我的目标岗位呢");
        history.add("assistant: 前向部署工程师");
        history.add("user     : 今天天气怎么样");

        int max = 6;
        System.out.println("========= 删除之前（共 " + history.size() + " 条，上限 " + max + "）=========");
        print("", history);

        System.out.println("========= 写法 A：从索引 1 开始删（正确）=========");
        print("", trimCorrect(history, max));

        System.out.println("========= 写法 B：从索引 0 开始删（错误）=========");
        print("", trimWrong(history, max));

        System.out.println("========= 结论 =========");
        System.out.println("写法 A 保留了第 0 条的 system 人设");
        System.out.println("写法 B 把 system 也删掉了 → 模型不再知道自己的身份和规则");
    }
}