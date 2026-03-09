package com.example.mybatis.extractor;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SQL文から利用テーブル名を抽出するクラス。
 * JSqlParserを使用してSQLを構文解析し、サブクエリ・エイリアスにも対応する。
 */
public class TableExtractor {

    /**
     * SQL文から利用テーブル名を抽出する。
     *
     * @param sql SQL文（プレースホルダ ? を含んでよい）
     * @return テーブル名のリスト（重複なし、出現順）
     */
    public static List<String> extractTables(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            TablesNamesFinder finder = new TablesNamesFinder();
            List<String> tableList = finder.getTableList(statement);

            Set<String> seen = new LinkedHashSet<>();
            for (String table : tableList) {
                seen.add(table);
            }
            return new ArrayList<>(seen);
        } catch (Exception e) {
            return List.of();
        }
    }
}
