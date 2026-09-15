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
 * Aho-Corasick 多模式匹配；词条按小写匹配（Latin），中文原样。
 */
public final class SensitiveWordAcAutomaton {

    private final Node root = new Node();

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

    private void insert(String word) {
        Node cur = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            cur = cur.next.computeIfAbsent(c, k -> new Node());
        }
        cur.word = word;
    }

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

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static final class Node {
        private final Map<Character, Node> next = new HashMap<>();
        private Node fail;
        private String word;
    }
}
