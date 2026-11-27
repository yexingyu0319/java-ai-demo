package com.example;

import com.example.model.Resume;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 极简"简历仓库"：用一个文本文件存所有简历，一行一份。
 *
 * 为什么用这种"每行一个 JSON"（叫 JSON Lines / JSONL）而不是一个大 JSON 数组？
 *   1. 追加方便：新简历直接往文件末尾加一行，不用把整个文件读出来再重写；
 *   2. 抗损坏：某一行的 JSON 写坏了，不影响其他行；
 *   3. 好排查：可以用记事本直接打开看，一行一条记录。
 * 真实项目里这种格式常用于日志、离线数据导入。
 *
 * 注意：这里用 Jackson 而不是 fastjson —— fastjson 1.x 认不出 record 的访问器（name() 而非 getName()）。
 */
public class ResumeStore {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResumeStore(Path file) {
        this.file = file;
    }

    /**
     * 追加一份简历。
     *
     * 注意这里的"去重"：真实项目里靠数据库主键/唯一索引保证，
     * 我们用「姓名 + 手机号 + 学校」当业务主键，已存在就跳过。
     * 否则同一个人的简历会被反复写入，查询时出现一堆重复行。
     */
    public boolean save(Resume resume) throws IOException {
        if (exists(resume)) {
            return false;
        }
        Files.createDirectories(file.getParent());
        String line = mapper.writeValueAsString(resume);
        Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return true;
    }

    /** 判断同一份简历是否已存在（按业务主键比较） */
    public boolean exists(Resume resume) throws IOException {
        for (Resume r : findAll()) {
            if (Objects.equals(r.name(), resume.name())
                    && Objects.equals(r.phone(), resume.phone())
                    && Objects.equals(r.school(), resume.school())) {
                return true;
            }
        }
        return false;
    }

    /** 读出全部简历（跳过空行和坏行） */
    public List<Resume> findAll() throws IOException {
        List<Resume> list = new ArrayList<>();
        if (!Files.exists(file)) {
            return list;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                list.add(mapper.readValue(line, Resume.class));
            } catch (Exception e) {
                System.out.println("[跳过损坏记录] " + line.substring(0, Math.min(60, line.length())) + "…");
            }
        }
        return list;
    }

    /** 按技能筛选：找出会某个技能的所有人 */
    public List<Resume> findBySkill(String skill) throws IOException {
        List<Resume> hit = new ArrayList<>();
        for (Resume r : findAll()) {
            if (r.skills() == null) {
                continue;
            }
            for (String s : r.skills()) {
                if (s.equalsIgnoreCase(skill)) {
                    hit.add(r);
                    break;
                }
            }
        }
        return hit;
    }

    public int count() throws IOException {
        return findAll().size();
    }

    public Path file() {
        return file;
    }
}