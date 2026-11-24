package com.example;

/**
 * 一条对话消息。
 * role 只有三种：system（人设/规则）、user（用户说话）、assistant（模型说话）。
 * 用 record 而不是 class：它天生不可变，适合当"值"传来传去。
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /** 转成 API 需要的 {"role":"user","content":"..."} */
    public String toJson() {
        return "{\"role\":\"" + role + "\",\"content\":"
                + com.alibaba.fastjson.JSON.toJSONString(content) + "}";
    }
}