package com.example.mybatis.formatter;

import java.util.regex.Pattern;

/**
 * SQL文を整形するフォーマッター。
 * MyBatisから取得した生SQLを読みやすい形式に変換する。
 */
public class SqlFormatter {

    private static final Pattern MULTI_SPACES = Pattern.compile("\\s+");
    private static final Pattern LEADING_TRAILING_SPACES = Pattern.compile("^\\s+|\\s+$");

    /**
     * SQL文を整形する。
     * - 連続する空白を単一スペースに変換
     * - キーワード前で改行を挿入
     * - インデントを付与
     */
    public static String format(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        // まず連続する空白を正規化
        String normalized = MULTI_SPACES.matcher(sql).replaceAll(" ").trim();

        // キーワードの前で改行を挿入
        StringBuilder formatted = new StringBuilder();
        String upper = normalized.toUpperCase();

        String[] keywords = {"SELECT", "FROM", "WHERE", "AND", "OR", "ORDER BY",
                "GROUP BY", "HAVING", "INSERT INTO", "VALUES", "UPDATE", "SET",
                "DELETE FROM", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "JOIN",
                "ON", "LIMIT", "OFFSET", "UNION"};

        // シンプルなアプローチ: キーワードを検出して改行
        String[] tokens = normalized.split("(?i)(?=\\b(?:SELECT|FROM|WHERE|AND|OR|ORDER BY|GROUP BY|HAVING|INSERT INTO|VALUES|UPDATE|SET|DELETE FROM|LEFT JOIN|RIGHT JOIN|INNER JOIN|OUTER JOIN|JOIN|ON|LIMIT|OFFSET|UNION)\\b)");

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isEmpty()) continue;

            if (i > 0) {
                formatted.append("\n");
            }

            // インデントレベル判定
            String tokenUpper = token.toUpperCase().trim();
            if (tokenUpper.startsWith("AND ") || tokenUpper.startsWith("OR ")
                    || tokenUpper.startsWith("ON ")) {
                formatted.append("  ").append(token);
            } else if (tokenUpper.startsWith("LEFT ") || tokenUpper.startsWith("RIGHT ")
                    || tokenUpper.startsWith("INNER ") || tokenUpper.startsWith("OUTER ")
                    || tokenUpper.startsWith("JOIN ")) {
                formatted.append("  ").append(token);
            } else {
                formatted.append(token);
            }
        }

        return formatted.toString();
    }

    /**
     * SQL文を単一行に正規化する（比較やJSON出力用）。
     */
    public static String normalize(String sql) {
        if (sql == null) return null;
        return MULTI_SPACES.matcher(sql).replaceAll(" ").trim();
    }
}
