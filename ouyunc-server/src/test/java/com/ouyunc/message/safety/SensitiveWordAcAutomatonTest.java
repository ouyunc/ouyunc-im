package com.ouyunc.message.safety;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双数组 AC 与暴力多模式结果对齐，并覆盖打码、大小写和十万级词库构建。
 */
class SensitiveWordAcAutomatonTest {

    @Test
    void classicSuffixLinksEmitLongerThenShorter() {
        SensitiveWordAcAutomaton automaton = new SensitiveWordAcAutomaton(List.of("he", "she", "his", "hers"));
        assertEquals(List.of("she", "he", "hers"), automaton.findAll("ushers"));
        assertEquals(List.of("his"), automaton.findAll("his"));
    }

    @Test
    void latinIsCaseInsensitiveAndChineseStaysExact() {
        SensitiveWordAcAutomaton automaton = new SensitiveWordAcAutomaton(List.of("违禁", "BadWord"));
        assertEquals(List.of("违禁", "badword"), automaton.findAll("这里有违禁内容和BadWord"));
        assertEquals("这里有**内容和*******", automaton.mask("这里有违禁内容和BadWord",
                automaton.findAll("这里有违禁内容和BadWord"), "*"));
    }

    @Test
    void maskKeepsLongestFirstAndDoesNotOverlapReplace() {
        SensitiveWordAcAutomaton automaton = new SensitiveWordAcAutomaton(List.of("abc", "bcd", "aa"));
        assertEquals("***d", automaton.mask("abcd", List.of("abc", "bcd"), "*"));
        assertEquals("**a", automaton.mask("aaa", List.of("aa"), "*"));
        assertEquals("x###y", automaton.mask("xa+by", List.of("a+b"), "#"));
    }

    @Test
    void lengthChangingCaseFoldFallsBackWithoutThrowing() {
        SensitiveWordAcAutomaton automaton = new SensitiveWordAcAutomaton(List.of("bad"));
        String raw = "\u0130bad";
        assertTrue(raw.toLowerCase(Locale.ROOT).length() != raw.length());
        assertEquals("\u0130***", automaton.mask(raw, List.of("bad"), "*"));
    }

    @Test
    void skipsBlankAndDeduplicates() {
        SensitiveWordAcAutomaton automaton = new SensitiveWordAcAutomaton(Arrays.asList(" ", "", "aa", "aa", null));
        assertEquals(List.of("aa"), automaton.findAll("aaa"));
        assertTrue(new SensitiveWordAcAutomaton(List.of()).findAll("aa").isEmpty());
        assertEquals("aa", new SensitiveWordAcAutomaton(List.of()).mask("aa", List.of(), "*"));
    }

    @Test
    void matchesBruteForceOnRandomCorpus() {
        Random random = new Random(42);
        List<String> words = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            words.add(randomWord(random));
        }
        SensitiveWordAcAutomaton automaton = new SensitiveWordAcAutomaton(words);
        for (int round = 0; round < 40; round++) {
            String text = randomText(random);
            assertEquals(brute(words, text), automaton.findAll(text), text);
        }
    }

    @Test
    void hundredThousandWordsBuildAndHitPlantedTerm() {
        int count = 100_000;
        List<String> words = new ArrayList<>(count + 1);
        for (int i = 0; i < count; i++) {
            words.add("w" + i + "z");
        }
        words.add("违禁品");
        for (int i = 0; i < 5000; i++) {
            words.add(String.valueOf((char) (0x4E00 + i)));
        }
        SensitiveWordAcAutomaton automaton = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> new SensitiveWordAcAutomaton(words));
        assertEquals(List.of("违禁品", "品"), automaton.findAll("xx违禁品yy"));
        assertEquals(List.of(String.valueOf((char) 0x4E00)), automaton.findAll(String.valueOf((char) 0x4E00)));
        assertEquals(List.of("w42z"), automaton.findAll("xxw42zyy"));
        assertTrue(automaton.findAll("没有问题").isEmpty());
        assertEquals("请购买***谢谢", automaton.mask("请购买违禁品谢谢", List.of("违禁品"), "*"));
    }

    /**
     * 暴力枚举：同一结束位置按词长从长到短出词，全局去重保序。
     *
     * @param words 原始词
     * @param text  原文
     * @return 归一化命中
     */
    private static List<String> brute(List<String> words, String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> dict = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String key = word.toLowerCase(Locale.ROOT);
            if (unique.add(key)) {
                dict.add(key);
            }
        }
        List<String> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int end = 0; end < normalized.length(); end++) {
            List<String> here = new ArrayList<>();
            for (String word : dict) {
                int start = end - word.length() + 1;
                if (start >= 0 && normalized.regionMatches(start, word, 0, word.length())) {
                    here.add(word);
                }
            }
            here.sort((left, right) -> Integer.compare(right.length(), left.length()));
            for (String word : here) {
                if (seen.add(word)) {
                    hits.add(word);
                }
            }
        }
        return hits;
    }

    /**
     * 短词，字母表里带大小写和中文，方便撞上失败指针。
     *
     * @param random 随机源
     * @return 一个词
     */
    private static String randomWord(Random random) {
        String alphabet = "abC你我他";
        int length = 1 + random.nextInt(5);
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    /**
     * 随机正文，长度控制在聊天消息量级。
     *
     * @param random 随机源
     * @return 正文
     */
    private static String randomText(Random random) {
        String alphabet = "abC你我他xyz";
        int length = random.nextInt(80);
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}
