package com.linggong.ai.eval;

import com.linggong.ai.rule.impl.TokenizerBridge;
import com.linggong.entity.AiRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B0 / B1 两个「非生产」检索方案。B2/B3/B4 走 {@link ProductionBm25}，因为那三个
 * 跑的是同一个生产打分器、只换语料；B0/B1 则是货真价实的算法替换，只能在这里实现。
 *
 * <p>两者都刻意复用生产分词器（{@link TokenizerBridge}），保证与 B2 的差异来自
 * 「打分方式」而不是「分词方式」。
 */
public final class BaselineRetrievers {

    private BaselineRetrievers() {
    }

    // ================= B0：朴素包含 =================

    /**
     * B0 朴素包含：query 的任意 token 以子串形式出现在检索文本里就算命中，
     * <b>命中集按规则 id 排序后截断</b> —— 完全没有相关性排序。
     *
     * <p>这就是「写个 LIKE 查一查」的水平。它<b>不是稻草人</b>：召回可能不低，
     * 但因为没有排序，MRR / NDCG 会塌掉 —— 正好用来说明「有召回没排序等于没有」，
     * 而这句话正是 MRR / NDCG 存在的理由。
     */
    public static RankingRetriever naiveContains(List<AiRule> rules, int topK) {
        List<AiRule> byId = rules.stream()
                .sorted(Comparator.comparing(AiRule::getId))
                .toList();
        List<String> texts = byId.stream().map(Corpus::searchText).toList();
        return query -> {
            Set<String> tokens = new LinkedHashSet<>(TokenizerBridge.tokenize(query));
            if (tokens.isEmpty()) {
                return List.of();
            }
            List<Long> hits = new ArrayList<>();
            for (int i = 0; i < byId.size() && hits.size() < topK; i++) {
                String text = texts.get(i);
                for (String t : tokens) {
                    if (text.contains(t)) {
                        hits.add(byId.get(i).getId());
                        break;
                    }
                }
            }
            return hits;
        };
    }

    // ================= B1：TF-IDF =================

    /**
     * B1 TF-IDF：{@code score(d) = Σ_t tf(t,d)·idf(t)}，再除以文档向量的 L2 范数
     * （等价于「查询端取二值权重的余弦相似度」，也是 sklearn TfidfVectorizer 的默认口径）。
     *
     * <p><b>两个刻意的控制变量</b>：
     * <ul>
     *   <li>IDF 用与生产 BM25 <b>完全相同</b>的公式 {@code log(1 + (n-df+0.5)/(df+0.5))}。
     *       若这里换个 IDF，B1→B2 的差值就混进了「IDF 换了」这个因素，说不清是 BM25 的功劳；</li>
     *   <li>分词器与生产同一个（{@link TokenizerBridge}）。</li>
     * </ul>
     * 于是 B1→B2 剩下的差异就只有 BM25 的两件独门武器：<b>词频饱和 k1</b> 与
     * <b>基于文档长度的归一 b</b>（替换掉这里的 L2 范数）。不过要讲清楚：这两件事
     * 是同时换掉的，所以 B1→B2 的提升无法继续拆到单个参数头上。
     *
     * <p>同样镜像生产的「分数归零即停止」行为，使两个方案的返回条数可比。
     */
    public static RankingRetriever tfIdf(List<AiRule> rules, int topK) {
        int n = rules.size();
        List<Map<String, Integer>> docTf = new ArrayList<>(n);
        Map<String, Integer> df = new HashMap<>();
        for (AiRule r : rules) {
            List<String> tokens = TokenizerBridge.tokenize(Corpus.searchText(r));
            Map<String, Integer> tf = new HashMap<>();
            for (String t : tokens) {
                tf.merge(t, 1, Integer::sum);
            }
            docTf.add(tf);
            for (String t : tf.keySet()) {
                df.merge(t, 1, Integer::sum);
            }
        }
        // 每篇文档的 L2 范数（用 tf·idf 算）与查询无关，建索引时算一次 ——
        // 放在查询循环里重算会让人为放大 B1 的耗时，时间对比就不公平了
        double[] norm = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (Map.Entry<String, Integer> e : docTf.get(i).entrySet()) {
                double w = e.getValue() * idf(n, df.getOrDefault(e.getKey(), 0));
                sum += w * w;
            }
            norm[i] = Math.sqrt(sum);
        }
        return query -> {
            Set<String> qTerms = new LinkedHashSet<>(TokenizerBridge.tokenize(query));
            if (qTerms.isEmpty()) {
                return List.of();
            }
            double[] scores = new double[n];
            for (int i = 0; i < n; i++) {
                Map<String, Integer> tf = docTf.get(i);
                double s = 0;
                for (String t : qTerms) {
                    Integer c = tf.get(t);
                    if (c != null) {
                        s += c * idf(n, df.getOrDefault(t, 0));
                    }
                }
                scores[i] = norm[i] == 0 ? 0 : s / norm[i];
            }
            return topK(rules, scores, topK);
        };
    }

    /** 与生产 BM25 相同的 IDF，保证 B1→B2 的差异只在 tf 与归一方式。 */
    private static double idf(int n, int df) {
        return Math.log(1 + (n - df + 0.5) / (df + 0.5));
    }

    /** 取分数最高的 topK 个，分数归零即停 —— 与生产 {@code retrieve()} 的行为对齐。 */
    static List<Long> topK(List<AiRule> rules, double[] scores, int topK) {
        int n = rules.size();
        List<Long> result = new ArrayList<>();
        boolean[] taken = new boolean[n];
        for (int k = 0; k < Math.min(topK, n); k++) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (!taken[i] && (best < 0 || scores[i] > scores[best])) {
                    best = i;
                }
            }
            if (best < 0 || scores[best] <= 0) {
                break;
            }
            taken[best] = true;
            result.add(rules.get(best).getId());
        }
        return result;
    }
}
