package com.example;

/**
 * 一次调用的结果：正文 + 本次消耗的 token 数。
 * 把 token 数带出来，是因为"成本"是 AI 应用必须随时看得见的指标。
 */
public record Reply(String content, int promptTokens, int completionTokens) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}