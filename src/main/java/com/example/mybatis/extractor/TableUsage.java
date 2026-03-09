package com.example.mybatis.extractor;

/**
 * テーブルの利用情報を保持するクラス。
 * テーブル名とCRUD操作種別（INSERT, SELECT, UPDATE, DELETE）を持つ。
 */
public class TableUsage {

    private final String tableName;
    private final String operation;

    public TableUsage(String tableName, String operation) {
        this.tableName = tableName;
        this.operation = operation;
    }

    public String getTableName() {
        return tableName;
    }

    public String getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        return operation + ": " + tableName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableUsage)) return false;
        TableUsage that = (TableUsage) o;
        return tableName.equals(that.tableName) && operation.equals(that.operation);
    }

    @Override
    public int hashCode() {
        return 31 * tableName.hashCode() + operation.hashCode();
    }
}
