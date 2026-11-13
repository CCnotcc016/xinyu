package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.PageResult;
import com.xinyu.entity.SensitiveWord;
import com.xinyu.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 敏感词检测。
 *
 * <p>词库放在 database 里（sensitive_word 表），后台可以增删，改完立刻生效 --
 * 以前只读 classpath 的 txt，改一个词就得重新打包，等于没法运营。
 * 首次启动如果表是空的，自动把 sensitive-words.txt 里的词导入，老行为不变。</p>
 *
 * <p>匹配是「包含即命中」，没有分词也没有同音字处理：宁可漏，不愿意误杀。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private static final int MAX_WORD_LENGTH = 50;

    private final SensitiveWordMapper wordMapper;

    /** 热更新：整体替换引用，读的人不用加锁 */
    private volatile Set<String> words = Set.of();

    @PostConstruct
    public void init() {
        try {
            if (wordMapper.selectCount(null) == 0) {
                int imported = importFromFile();
                log.info("敏感词库为空，已从 sensitive-words.txt 导入 {} 个词", imported);
            }
            reload();
            log.info("敏感词库加载完成，共 {} 个词", words.size());
        } catch (Exception e) {
            // 数据库异常时退回文件词库，至少不让发帖直接挂掉
            words = loadFromFile();
            log.warn("敏感词库从数据库加载失败，已退回内置文件（{} 个词）: {}",
                    words.size(), e.getMessage());
        }
    }

    public boolean containsSensitive(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public PageResult<SensitiveWord> list(long page, long size, String keyword) {
        Page<SensitiveWord> result = wordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SensitiveWord>()
                        .like(StringUtils.hasText(keyword), SensitiveWord::getWord, keyword)
                        .orderByDesc(SensitiveWord::getId));
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public void add(String word) {
        String w = word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(w)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (w.length() > MAX_WORD_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                    "敏感词最长 " + MAX_WORD_LENGTH + " 个字");
        }
        long exists = wordMapper.selectCount(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, w));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该敏感词已经在库里了");
        }
        SensitiveWord entity = new SensitiveWord();
        entity.setWord(w);
        wordMapper.insert(entity);
        reload();
    }

    public void delete(Long id) {
        SensitiveWord entity = wordMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        wordMapper.deleteById(id);
        reload();
    }

    /** 重新从数据库加载（增删后自动调用） */
    public void reload() {
        List<SensitiveWord> rows = wordMapper.selectList(null);
        Set<String> loaded = new HashSet<>();
        for (SensitiveWord row : rows) {
            if (StringUtils.hasText(row.getWord())) {
                loaded.add(row.getWord().trim().toLowerCase(Locale.ROOT));
            }
        }
        words = Set.copyOf(loaded);
    }

    private int importFromFile() {
        Set<String> fileWords = loadFromFile();
        int imported = 0;
        for (String word : fileWords) {
            SensitiveWord entity = new SensitiveWord();
            entity.setWord(word);
            try {
                wordMapper.insert(entity);
                imported++;
            } catch (Exception e) {
                // 并发启动或唯一索引冲突：跳过即可
                log.debug("敏感词写入跳过: {}", word);
            }
        }
        return imported;
    }

    private Set<String> loadFromFile() {
        Set<String> result = new HashSet<>();
        try (InputStream is = new ClassPathResource("sensitive-words.txt").getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String w = line.trim();
                if (!w.isEmpty() && !w.startsWith("#")) {
                    result.add(w.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            log.warn("敏感词文件读取失败: {}", e.getMessage());
        }
        return result;
    }

    /** 供后台展示「共 N 个词」用 */
    public int size() {
        return words.size();
    }

    /** 当前词库快照（导出 / 排查看用） */
    public List<String> snapshot() {
        return new ArrayList<>(words).stream().sorted().toList();
    }
}
