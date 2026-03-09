package com.example.mybatis.extractor;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SQL文から利用テーブル名とCRUD操作種別を抽出するクラス。
 * JSqlParserを使用してSQLを構文解析し、サブクエリ・エイリアスにも対応する。
 */
public class TableExtractor {

    /**
     * SQL文からテーブル名のみを抽出する（後方互換）。
     */
    public static List<String> extractTables(String sql) {
        try {
            return new ArrayList<>(TablesNamesFinder.findTables(sql));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * SQL文からテーブル利用情報（テーブル名+操作種別）を抽出する。
     *
     * @param sql SQL文（プレースホルダ ? を含んでよい）
     * @return TableUsageのリスト（重複なし）
     */
    public static List<TableUsage> extractTableUsages(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            Set<TableUsage> usages = new LinkedHashSet<>();

            if (statement instanceof Insert) {
                Insert insert = (Insert) statement;
                usages.add(new TableUsage(insert.getTable().getName(), "INSERT"));
                if (insert.getSelect() != null) {
                    addSelectTables(insert.getSelect(), usages);
                }
            } else if (statement instanceof Update) {
                Update update = (Update) statement;
                usages.add(new TableUsage(update.getTable().getName(), "UPDATE"));
                if (update.getWhere() != null) {
                    addExpressionTables(update.getWhere().toString(), usages);
                }
            } else if (statement instanceof Delete) {
                Delete delete = (Delete) statement;
                usages.add(new TableUsage(delete.getTable().getName(), "DELETE"));
                if (delete.getWhere() != null) {
                    addExpressionTables(delete.getWhere().toString(), usages);
                }
            } else if (statement instanceof Select) {
                addSelectTables(statement, usages);
            }

            return new ArrayList<>(usages);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void addSelectTables(Statement selectStatement, Set<TableUsage> usages) {
        try {
            Set<String> tables = TablesNamesFinder.findTables(selectStatement.toString());
            for (String table : tables) {
                usages.add(new TableUsage(table, "SELECT"));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static void addExpressionTables(String whereExpr, Set<TableUsage> usages) {
        // WHERE句内のサブクエリからテーブルを抽出
        if (whereExpr.toUpperCase().contains("SELECT")) {
            try {
                int idx = whereExpr.toUpperCase().indexOf("SELECT");
                // サブクエリ部分を探す
                String sub = whereExpr.substring(idx);
                // 括弧のバランスを考慮せず、JSqlParserに任せる
                Set<String> tables = TablesNamesFinder.findTablesInExpression(whereExpr);
                for (String table : tables) {
                    usages.add(new TableUsage(table, "SELECT"));
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
