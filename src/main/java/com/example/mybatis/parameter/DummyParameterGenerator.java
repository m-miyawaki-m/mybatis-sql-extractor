package com.example.mybatis.parameter;

import java.util.*;

/**
 * BoundSql取得時に渡すダミーパラメータを生成するクラス。
 *
 * MyBatisの動的SQL（if, choose, foreach等）を評価するには
 * パラメータオブジェクトが必要。全条件分岐を通すために
 * 適切なダミー値を生成する。
 */
public class DummyParameterGenerator {

    /**
     * 全ての動的SQL条件を通過させるためのダミーパラメータMapを生成する。
     * MyBatisはMapをパラメータとして受け取り、OGNLで評価する。
     *
     * @param parameterNames 必要なパラメータ名の一覧
     * @return ダミー値が設定されたMap
     */
    public static Map<String, Object> generateDummyMap(Collection<String> parameterNames) {
        Map<String, Object> params = new HashMap<>();
        for (String name : parameterNames) {
            params.put(name, generateDummyValue(name));
        }
        return params;
    }

    /**
     * パラメータ名からダミー値を推測して生成する。
     * 名前のパターンに基づいて適切な型の値を返す。
     */
    public static Object generateDummyValue(String parameterName) {
        String lower = parameterName.toLowerCase();

        // リスト系（foreachで使われるパターン）
        if (lower.endsWith("list") || lower.endsWith("ids") || lower.endsWith("items")
                || lower.equals("collection") || lower.equals("array")) {
            return Arrays.asList(1, 2, 3);
        }

        // ID系
        if (lower.endsWith("id") || lower.equals("id")) {
            return 1;
        }

        // 数値系
        if (lower.contains("count") || lower.contains("num") || lower.contains("amount")
                || lower.contains("size") || lower.contains("limit") || lower.contains("offset")
                || lower.contains("age") || lower.contains("price")) {
            return 1;
        }

        // ブーリアン系
        if (lower.startsWith("is") || lower.startsWith("has") || lower.startsWith("can")
                || lower.contains("flag") || lower.contains("enable") || lower.contains("active")) {
            return true;
        }

        // 日付系
        if (lower.contains("date") || lower.contains("time") || lower.contains("created")
                || lower.contains("updated")) {
            return "2024-01-01";
        }

        // デフォルトは文字列
        return "dummy_" + parameterName;
    }

    /**
     * 空のパラメータMap（静的SQLの場合に使用）。
     */
    public static Map<String, Object> emptyParams() {
        return new HashMap<>();
    }
}
