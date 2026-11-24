package com.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型对话服务。
 *
 * 核心认知：大模型 API 是无状态的 —— 服务器不记得上一句说了什么。
 * 所以"多轮对话"完全靠客户端自己维护一份历史，每次请求把历史全部重发一遍。
 */
public class AIService {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;

    /** 是否要求模型只返回 JSON（response_format=json_object） */
    private final boolean jsonMode;

    /** 随机性：0 最稳定可复现，1 以上发散。抽取类任务必须用 0 */
    private double temperature = 0.7;

    private final List<ChatMessage> history = new ArrayList<>();

    private int totalPromptTokens;
    private int totalCompletionTokens;
    private int rounds;

    public AIService(String apiKey) {
        this(apiKey, "deepseek-chat", null, false);
    }

    public AIService(String apiKey, String model, String systemPrompt) {
        this(apiKey, model, systemPrompt, false);
    }

    public AIService(String apiKey, String model, String systemPrompt, boolean jsonMode) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey 不能为空");
        }
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? "deepseek-chat" : model;
        this.systemPrompt = systemPrompt;
        this.jsonMode = jsonMode;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(120))
                .build();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(ChatMessage.system(systemPrompt));
        }
    }

    /** 链式设置温度：new AIService(...).temperature(0) */
    public AIService temperature(double t) {
        this.temperature = t;
        return this;
    }

    // ------------------------------------------------------------------
    // 一、普通（非流式）调用：等模型全部写完，一次性返回
    // ------------------------------------------------------------------
    public Reply chat(String userInput) throws IOException {
        history.add(ChatMessage.user(userInput));

        JSONObject json = JSON.parseObject(post(buildRequestBody(false)));

        String content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        history.add(ChatMessage.assistant(content));
        // 注意这里要取 usage 节点，不要把整个响应对象传进去
        return accumulate(json.getJSONObject("usage"), content);
    }

    // ------------------------------------------------------------------
    // 二、流式调用：模型每生成一小段就推过来一段（SSE 协议）
    // onToken 每收到一个片段就被回调一次，调用方可以立刻打印 → 打字机效果
    // ------------------------------------------------------------------
    public Reply chatStream(String userInput, Consumer<String> onToken) throws IOException {
        history.add(ChatMessage.user(userInput));

        StringBuilder full = new StringBuilder();
        JSONObject lastUsage = null;

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(buildRequestBody(true).toJSONString(), JSON_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String err = response.body() == null ? "" : response.body().string();
                throw new IOException("HTTP " + response.code() + " -> " + err);
            }

            // 响应体是一条条"行"，格式固定：
            //   data: {"choices":[{"delta":{"content":"你"}}]}
            //   data: {"choices":[{"delta":{"content":"好"}}]}
            //   data: [DONE]
            BufferedSource source = response.body().source();
            String line;
            while ((line = source.readUtf8Line()) != null) {
                if (line.isEmpty() || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JSONObject chunk = JSON.parseObject(payload);

                // 流的最后一个包会带 usage（前提是请求了 stream_options.include_usage）
                if (chunk.getJSONObject("usage") != null) {
                    lastUsage = chunk.getJSONObject("usage");
                }

                JSONArray choices = chunk.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) {
                    continue;
                }
                JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                if (delta == null) {
                    continue;
                }
                String piece = delta.getString("content");
                if (piece != null && !piece.isEmpty()) {
                    full.append(piece);
                    onToken.accept(piece);   // 立刻把这一小段交给调用方
                }
            }
        }

        String content = full.toString();
        history.add(ChatMessage.assistant(content));
        return accumulate(lastUsage, content);
    }

    // ------------------------------------------------------------------

    private Reply accumulate(JSONObject usage, String content) {
        int pt = 0;
        int ct = 0;
        if (usage != null) {
            pt = usage.getIntValue("prompt_tokens");
            ct = usage.getIntValue("completion_tokens");
            totalPromptTokens += pt;
            totalCompletionTokens += ct;
        }
        rounds++;
        trimHistory();
        return new Reply(content, pt, ct);
    }

    private JSONObject buildRequestBody(boolean stream) {
        JSONArray messages = new JSONArray();
        for (ChatMessage m : history) {
            JSONObject o = new JSONObject();
            o.put("role", m.role());
            o.put("content", m.content());
            messages.add(o);
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", stream);

        if (stream) {
            // 不加这段，流式返回里就没有 usage，成本不可见
            JSONObject opts = new JSONObject();
            opts.put("include_usage", true);
            body.put("stream_options", opts);
        }
        if (jsonMode) {
            JSONObject rf = new JSONObject();
            rf.put("type", "json_object");
            body.put("response_format", rf);
        }
        return body;
    }

    private String post(JSONObject body) throws IOException {
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String raw = (response.body() == null) ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " -> " + raw);
            }
            return raw;
        }
    }

    private void trimHistory() {
        while (history.size() > MAX_HISTORY_MESSAGES) {
            int dropFrom = (history.get(0).role().equals("system")) ? 1 : 0;
            history.remove(dropFrom);
            if (history.size() > dropFrom) {
                history.remove(dropFrom);
            }
        }
    }

    public void reset() {
        history.removeIf(m -> !m.role().equals("system"));
        rounds = 0;
        totalPromptTokens = 0;
        totalCompletionTokens = 0;
    }

    public List<ChatMessage> history() {
        return List.copyOf(history);
    }

    public int rounds() {
        return rounds;
    }

    public int totalTokens() {
        return totalPromptTokens + totalCompletionTokens;
    }
}