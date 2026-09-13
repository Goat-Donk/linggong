package com.linggong.ai.rule.impl;

import java.util.List;

/**
 * 让评测代码用上生产分词器的桥接类。
 *
 * <p>{@link Bm25ContentRetriever#tokenize(String)} 是<b>包级可见</b>的，评测代码在
 * {@code com.linggong.ai.eval} 包下够不着。这里把本类放在<b>测试源集</b>的同一个包里，
 * 于是同包可见性成立 —— 生产代码一行都不用改。
 *
 * <p><b>为什么不干脆把 tokenize 改成 public</b>：那等于为了测试放宽生产 API 的可见性，
 * 是不必要的让步。也<b>绝不允许</b>在评测里另写一份分词器副本：B1/B3 必须与 B2 共用
 * 完全相同的分词，否则「提升」里会混进分词差异，对照就不成立了。
 */
public final class TokenizerBridge {

    private TokenizerBridge() {
    }

    /** 与生产 {@code Bm25ContentRetriever} 完全同一个方法，不是副本。 */
    public static List<String> tokenize(String text) {
        return Bm25ContentRetriever.tokenize(text);
    }
}
