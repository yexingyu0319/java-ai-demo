package com.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;

/**
 * 简历抽取器：把一段简历原文变成严格 JSON。
 *
 * 这里是 Day 3 的重点 —— Prompt 工程的"结构化输出"套路：
 *   1. system 里写死角色（招聘系统解析引擎）+ 输出格式（JSON 骨架）
 *   2. 明确禁止项（不要解释、不要 Markdown 代码块、不要编造）
 *   3. 打开 response_format=json_object，从协议层强制 JSON
 *   4. temperature=0，让同样的输入尽量得到同样的输出
 *   5. 拿到结果后【一定要校验】，不合格就带着错误信息重试一次
 */
public class ResumeExtractor {

    private static final String SYSTEM_PROMPT = """
            你是一个招聘系统的简历解析引擎，唯一职责是把简历原文转换成结构化 JSON。

            严格遵守以下规则：
            1. 只输出 JSON 本体，不要输出任何解释文字、不要使用 ```json 代码块包裹；
            2. 简历里没有的信息填 null，绝对不要编造或猜测；
            3. skills 必须是字符串数组，每项是一个具体技能名，不要带括号说明；
            4. yearsOfExperience 是数字（可以是小数），完全没有工作/实习经历就填 0；
            5. 所有字段都必须出现，顺序如下：

            {
              "name": "姓名",
              "phone": "手机号，保留原始格式",
              "email": "邮箱",
              "city": "现居城市",
              "yearsOfExperience": 0.5,
              "education": "最高学历，例如 本科",
              "school": "学校名称",
              "jobIntention": "求职意向",
              "skills": ["技能1", "技能2"],
              "latestCompany": "最近一段公司名称，没有则 null",
              "projectCount": 1
            }
            """;

    private final AIService ai;

    public ResumeExtractor(String apiKey) {
        // jsonMode = true → 请求里带 response_format={"type":"json_object"}
        // temperature(0) → 抽取任务要稳定，不要发挥
        this.ai = new AIService(apiKey, "deepseek-chat", SYSTEM_PROMPT, true).temperature(0);
    }

    /**
     * 抽取简历。会做一次校验，不合格自动重试一次（把错误原因反馈给模型）。
     */
    public String extract(String resumeText) throws Exception {
        String first = ai.chat(resumeText).content();
        if (isValidJson(first)) {
            return normalize(first);
        }

        System.out.println("[校验失败] 首次输出不是合法 JSON，带错误信息重试一次……");
        String retryPrompt = "你上次的输出不是合法 JSON：" + first
                + "\n请重新输出，严格遵守 system 中的 JSON 格式，不要任何多余字符。";
        String second = ai.chat(retryPrompt).content();
        if (isValidJson(second)) {
            return normalize(second);
        }
        throw new IllegalStateException("两次都没拿到合法 JSON，实际输出：" + second);
    }

    private boolean isValidJson(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            JSON.parseObject(normalize(text), Feature.OrderedField);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 兜底清理：模型偶尔还是会包一层 ```json ... ```，这里剥掉 */
    private String normalize(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s;
    }
}