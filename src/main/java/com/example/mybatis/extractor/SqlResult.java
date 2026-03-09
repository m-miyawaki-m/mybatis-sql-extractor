package com.example.mybatis.extractor;

import java.util.List;
import java.util.Map;

/**
 * Mapper XMLから抽出されたSQL文の結果を保持するデータクラス。
 */
public class SqlResult {

    private final String namespace;
    private final String id;
    private final String sqlCommandType;
    private final String sql;
    private final List<ParameterInfo> parameters;
    private final BranchPattern branchPattern;
    private final Map<String, Object> parameterValues;

    public SqlResult(String namespace, String id, String sqlCommandType, String sql, List<ParameterInfo> parameters) {
        this(namespace, id, sqlCommandType, sql, parameters, null, null);
    }

    public SqlResult(String namespace, String id, String sqlCommandType, String sql,
                     List<ParameterInfo> parameters, BranchPattern branchPattern,
                     Map<String, Object> parameterValues) {
        this.namespace = namespace;
        this.id = id;
        this.sqlCommandType = sqlCommandType;
        this.sql = sql;
        this.parameters = parameters;
        this.branchPattern = branchPattern;
        this.parameterValues = parameterValues;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getId() {
        return id;
    }

    public String getSqlCommandType() {
        return sqlCommandType;
    }

    public String getSql() {
        return sql;
    }

    public List<ParameterInfo> getParameters() {
        return parameters;
    }

    public String getFullId() {
        return namespace + "." + id;
    }

    public BranchPattern getBranchPattern() {
        return branchPattern;
    }

    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }

    /**
     * パラメータ情報を保持するレコード。
     */
    public static class ParameterInfo {
        private final String property;
        private final String javaType;
        private final String jdbcType;

        public ParameterInfo(String property, String javaType, String jdbcType) {
            this.property = property;
            this.javaType = javaType;
            this.jdbcType = jdbcType;
        }

        public String getProperty() {
            return property;
        }

        public String getJavaType() {
            return javaType;
        }

        public String getJdbcType() {
            return jdbcType;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(property);
            if (jdbcType != null && !jdbcType.isEmpty()) {
                sb.append(":").append(jdbcType);
            } else if (javaType != null && !javaType.isEmpty()) {
                sb.append(":").append(javaType);
            }
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(getFullId());
        if (branchPattern != null) {
            sb.append(" [").append(branchPattern.name()).append("]");
        }
        sb.append(" ===\n");
        sb.append("Type: ").append(sqlCommandType).append("\n");
        sb.append("SQL:\n  ").append(sql.replace("\n", "\n  ")).append("\n");
        if (parameters != null && !parameters.isEmpty()) {
            sb.append("Parameters: ").append(parameters).append("\n");
        }
        if (parameterValues != null && !parameterValues.isEmpty()) {
            sb.append("Parameter Values: ").append(parameterValues).append("\n");
        }
        return sb.toString();
    }
}
