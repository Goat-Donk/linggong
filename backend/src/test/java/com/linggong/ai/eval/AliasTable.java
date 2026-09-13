package com.linggong.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B4 的口语/错别字别名表（{@code /ai/aliases.json}，由 {@code tools/gen_aliases.py} 生成）。
 *
 * <p><b>防过拟合的关键约定</b>：这张表只依据评测集 id 偶数的「推导集 A」推导，
 * 奇数 id 的「验证集 B」留给 B4 的留出验证。若不做这个切分，别名就是从评测集里抄出来的，
 * B4 的提升必然好看，但说明不了任何泛化能力 —— 那是自欺。
 */
public final class AliasTable {

    public static final String DEFAULT_RESOURCE = "/ai/aliases.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AliasTable() {
    }

    public static Map<Long, List<String>> load() {
        return load(DEFAULT_RESOURCE);
    }

    public static Map<Long, List<String>> load(String resource) {
        try (InputStream in = AliasTable.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("别名表不在 classpath 上：" + resource
                        + "；应由 tools/gen_aliases.py 生成到 backend/src/test/resources/ai/");
            }
            JsonNode root = MAPPER.readTree(in);
            Map<Long, List<String>> out = new LinkedHashMap<>();
            JsonNode aliases = root.path("aliases");
            aliases.fieldNames().forEachRemaining(f -> {
                List<String> terms = new ArrayList<>();
                aliases.get(f).path("terms").forEach(t -> terms.add(t.asText()));
                if (!terms.isEmpty()) {
                    out.put(Long.valueOf(f), List.copyOf(terms));
                }
            });
            if (out.isEmpty()) {
                throw new IllegalStateException("别名表为空：" + resource);
            }
            return Map.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException("读取别名表失败：" + resource, e);
        }
    }
}
