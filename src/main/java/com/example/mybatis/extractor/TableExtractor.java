package com.example.mybatis.extractor;

import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL文から利用テーブル名を抽出するクラス。
 * JSqlParserを使用してSQLを構文解析し、サブクエリ・エイリアスにも対応する。
 */
public class TableExtractor {

    /**
     * SQL文から利用テーブル名を抽出する。
     *
     * @param sql SQL文（プレースホルダ ? を含んでよい）
     * @return テーブル名のリスト（重複なし）
     */
    public static List<String> extractTables(String sql) {
        try {
            return new ArrayList<>(TablesNamesFinder.findTables(sql));
        } catch (Exception e) {
            return List.of();
        }
    }
}
