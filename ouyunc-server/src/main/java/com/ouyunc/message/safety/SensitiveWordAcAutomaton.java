package com.ouyunc.message.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

/**
 * Aho-Corasick 多模式敏感词匹配器。
 * <p>构建后只读，可多线程共享。拉丁字母按小写匹配，中文原样。词库变更应新建实例，勿原地改树。</p>
 */
public final class SensitiveWordAcAutomaton {

    /** AC 自动机根节点。 */
    private final Node root = new Node();

    /**
     * 用词集合构建自动机（含 fail 指针）。
     *
     * @param words 敏感词，空/空白项会被跳过
     */
    public SensitiveWordAcAutomaton(Iterable<String> words) {
        if (words != null) {
            for (String word : words) {
                if (word != null && !word.isBlank()) {
                    insert(normalize(word));
                }
            }
        }
        buildFail();
    }

    /**
     * 扫描文本，返回去重且保序的命中词（归一化后的词形）。
     *
     * @param text 原文
     * @return 命中列表，未命中为空列表
     */
    public List<String> findAll(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String normalized = normalize(text);
        List<String> hits = new ArrayList<>();
        Node cur = root;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            while (cur != root && !cur.next.containsKey(c)) {
                cur = cur.fail;
            }
            cur = cur.next.getOrDefault(c, root);
            Node emit = cur;
            while (emit != root) {
                if (emit.word != null) {
                    hits.add(emit.word);
                }
                emit = emit.fail;
            }
        }
        if (hits.isEmpty()) {
            return List.of();
        }
        // 去重保序
        LinkedList<String> unique = new LinkedList<>();
        for (String hit : hits) {
            if (!unique.contains(hit)) {
                unique.add(hit);
            }
        }
        return Collections.unmodifiableList(unique);
    }

    /**
     * 按命中词从长到短替换为等长掩码，避免短词破坏长词。
     *
     * @param text     原文
     * @param hits     命中词
     * @param maskChar 掩码字符，取首字符，空则 *
     * @return 脱敏文本
     */
    public String mask(String text, List<String> hits, String maskChar) {
        if (text == null || hits == null || hits.isEmpty()) {
            return text;
        }
        String ch = (maskChar == null || maskChar.isBlank()) ? "*" : maskChar.substring(0, 1);
        String out = text;
        List<String> sorted = new ArrayList<>(hits);
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String hit : sorted) {
            if (hit == null || hit.isEmpty()) {
                continue;
            }
            String replacement = ch.repeat(Math.max(1, hit.length()));
            out = out.replaceAll("(?i)" + java.util.regex.Pattern.quote(hit),
                    java.util.regex.Matcher.quoteReplacement(replacement));
        }
        return out;
    }

    /**
     * 将词插入 Trie。
     *
     * @param word 已归一化的词
     */
    private void insert(String word) {
        Node cur = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            cur = cur.next.computeIfAbsent(c, k -> new Node());
        }
        cur.word = word;
    }

    /**
     * BFS 构建 fail 指针。
     */
    private void buildFail() {
        Queue<Node> queue = new LinkedList<>();
        root.fail = root;
        for (Node child : root.next.values()) {
            child.fail = root;
            queue.offer(child);
        }
        while (!queue.isEmpty()) {
            Node parent = queue.poll();
            for (Map.Entry<Character, Node> e : parent.next.entrySet()) {
                char c = e.getKey();
                Node child = e.getValue();
                Node f = parent.fail;
                while (f != root && !f.next.containsKey(c)) {
                    f = f.fail;
                }
                child.fail = f.next.getOrDefault(c, root);
                if (child.fail == child) {
                    child.fail = root;
                }
                queue.offer(child);
            }
        }
    }

    /**
     * 拉丁字母转小写，中文不变。
     *
     * @param s 原文
     * @return 归一化结果
     */
    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * AC 树节点。
     */
    private static final class Node {
        /** 子边。 */
        private final Map<Character, Node> next = new HashMap<>();
        /** 失败指针。 */
        private Node fail;
        /** 若本节点为词尾，存放归一化后的词。 */
        private String word;
    }
}
