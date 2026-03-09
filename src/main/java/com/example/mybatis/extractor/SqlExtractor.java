package com.example.mybatis.extractor;

import com.example.mybatis.config.MyBatisConfigBuilder;
import com.example.mybatis.formatter.SqlFormatter;
import com.example.mybatis.parameter.ClasspathTypeResolver;
import com.example.mybatis.parameter.DummyParameterGenerator;
import com.example.mybatis.parameter.NullParameterMap;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.session.Configuration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MyBatis Mapper XMLからSQL文を抽出するメインクラス。
 *
 * MyBatisのConfiguration APIを使用して、Mapper XMLを正規に解析し、
 * MappedStatementからBoundSqlを取得することでSQL文を抽出する。
 *
 * <h2>処理フロー</h2>
 * <ol>
 *   <li>MyBatisConfigBuilderでConfigurationを構築</li>
 *   <li>Mapper XMLをXMLMapperBuilderで解析 → MappedStatementを登録</li>
 *   <li>各MappedStatementのSqlSourceからBoundSqlを取得</li>
 *   <li>ダミーパラメータを注入して動的SQLを評価</li>
 *   <li>結果をSqlResultオブジェクトとして返却</li>
 * </ol>
 */
public class SqlExtractor {

    private final MyBatisConfigBuilder configBuilder;
    private final ClasspathTypeResolver typeResolver;

    public SqlExtractor() {
        this.configBuilder = new MyBatisConfigBuilder();
        this.typeResolver = null;
    }

    public SqlExtractor(ClasspathTypeResolver typeResolver) {
        this.configBuilder = new MyBatisConfigBuilder();
        this.typeResolver = typeResolver;
    }

    /**
     * 指定されたMapper XMLファイルから全てのSQL文を抽出する。
     *
     * @param mapperFile Mapper XMLファイル
     * @return 抽出されたSQLのリスト
     * @throws IOException ファイル読み込みエラー
     */
    public List<SqlResult> extractAll(File mapperFile) throws IOException {
        configBuilder.addMapper(mapperFile);
        Configuration configuration = configBuilder.getConfiguration();

        return extractFromConfiguration(configuration);
    }

    /**
     * 指定ディレクトリ内の全Mapper XMLファイルからSQLを抽出する。
     *
     * @param directory Mapper XMLファイルが格納されたディレクトリ
     * @return 抽出されたSQLのリスト
     * @throws IOException ファイル読み込みエラー
     */
    public List<SqlResult> extractFromDirectory(File directory) throws IOException {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        File[] xmlFiles = directory.listFiles((dir, name) -> name.endsWith(".xml"));
        if (xmlFiles == null || xmlFiles.length == 0) {
            return Collections.emptyList();
        }

        // まず全XMLファイルをConfigurationに登録
        for (File xmlFile : xmlFiles) {
            try {
                configBuilder.addMapper(xmlFile);
            } catch (Exception e) {
                System.err.println("Warning: Failed to parse " + xmlFile.getName() + ": " + e.getMessage());
            }
        }

        return extractFromConfiguration(configBuilder.getConfiguration());
    }

    /**
     * Configurationに登録されたMappedStatementsからSQLを抽出する共通処理。
     * MyBatisは内部的に同一StatementをフルネームキーとShortキーの両方で登録するため、
     * IDベースで重複を排除する。
     */
    private List<SqlResult> extractFromConfiguration(Configuration configuration) {
        List<SqlResult> results = new ArrayList<>();
        Set<String> processedIds = new HashSet<>();

        for (Object obj : configuration.getMappedStatements()) {
            if (!(obj instanceof MappedStatement)) {
                continue;
            }
            MappedStatement ms = (MappedStatement) obj;

            // 重複排除: 同一IDのStatementは1度だけ処理
            if (!processedIds.add(ms.getId())) {
                continue;
            }

            try {
                SqlResult result = extractFromStatement(ms);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to extract SQL from " + ms.getId() + ": " + e.getMessage());
            }
        }

        results.sort(Comparator.comparing(SqlResult::getFullId));
        return results;
    }

    /**
     * 単一のMappedStatementからSQL文を抽出する。
     */
    private SqlResult extractFromStatement(MappedStatement ms) {
        String fullId = ms.getId();
        String namespace = "";
        String id = fullId;

        int lastDot = fullId.lastIndexOf('.');
        if (lastDot > 0) {
            namespace = fullId.substring(0, lastDot);
            id = fullId.substring(lastDot + 1);
        }

        String commandType = ms.getSqlCommandType().name();
        SqlSource sqlSource = ms.getSqlSource();

        // ダミーパラメータを生成
        Map<String, Object> dummyParams = createDummyParams(ms);

        // BoundSqlを取得
        BoundSql boundSql = sqlSource.getBoundSql(dummyParams);
        String rawSql = boundSql.getSql();
        String formattedSql = SqlFormatter.normalize(rawSql);

        // パラメータマッピング情報を取得
        List<SqlResult.ParameterInfo> paramInfos = boundSql.getParameterMappings().stream()
                .map(pm -> new SqlResult.ParameterInfo(
                        pm.getProperty(),
                        pm.getJavaType() != null ? pm.getJavaType().getSimpleName() : null,
                        pm.getJdbcType() != null ? pm.getJdbcType().name() : null
                ))
                .collect(Collectors.toList());

        return new SqlResult(namespace, id, commandType, formattedSql, paramInfos);
    }

    /**
     * MappedStatementの動的SQL解析に必要なダミーパラメータを生成する。
     * SQLソースがDynamicSqlSourceの場合、条件分岐を通過させるための値を設定する。
     */
    private Map<String, Object> createDummyParams(MappedStatement ms) {
        // まず空のパラメータで試行し、パラメータ名を収集
        // DynamicSqlSourceの場合はOGNL評価でnullだとfalseになるため、
        // 汎用的なダミーMapを返す
        SqlSource sqlSource = ms.getSqlSource();

        if (sqlSource instanceof DynamicSqlSource) {
            // 動的SQLの場合、一般的なパラメータ名でダミー値を事前設定
            // OGNL式の評価で null != true となるように値を入れる
            return new DummyParamMap();
        }

        return DummyParameterGenerator.emptyParams();
    }

    /**
     * 任意のキーに対してダミー値を返すMap実装。
     * MyBatisのOGNL評価でnullを避け、全条件分岐を通過させる。
     */
    private static class DummyParamMap extends HashMap<String, Object> {
        @Override
        public Object get(Object key) {
            Object value = super.get(key);
            if (value != null) {
                return value;
            }
            if (key instanceof String) {
                return DummyParameterGenerator.generateDummyValue((String) key);
            }
            return "dummy";
        }

        @Override
        public boolean containsKey(Object key) {
            return true; // 全てのキーが存在するように見せる
        }
    }

    // ========== Branch pattern extraction methods ==========

    /**
     * 指定されたMapper XMLファイルから全てのSQL文を分岐パターン別に抽出する。
     * 動的SQLの場合はALL_SET（全条件ON）とALL_NULL（全条件OFF）の2パターンを生成する。
     * 静的SQLの場合はALL_SETのみを生成する。
     *
     * @param mapperFile Mapper XMLファイル
     * @return 分岐パターン付きSqlResultのリスト
     * @throws IOException ファイル読み込みエラー
     */
    public List<SqlResult> extractAllWithBranches(File mapperFile) throws IOException {
        MyBatisConfigBuilder branchConfigBuilder = new MyBatisConfigBuilder();
        branchConfigBuilder.addMapper(mapperFile);
        Configuration configuration = branchConfigBuilder.getConfiguration();
        return extractFromConfigurationWithBranches(configuration);
    }

    /**
     * 指定ディレクトリ内の全Mapper XMLファイルからSQLを分岐パターン別に抽出する。
     *
     * @param directory Mapper XMLファイルが格納されたディレクトリ
     * @return 分岐パターン付きSqlResultのリスト
     * @throws IOException ファイル読み込みエラー
     */
    public List<SqlResult> extractFromDirectoryWithBranches(File directory) throws IOException {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        File[] xmlFiles = directory.listFiles((dir, name) -> name.endsWith(".xml"));
        if (xmlFiles == null || xmlFiles.length == 0) {
            return Collections.emptyList();
        }

        MyBatisConfigBuilder branchConfigBuilder = new MyBatisConfigBuilder();
        for (File xmlFile : xmlFiles) {
            try {
                branchConfigBuilder.addMapper(xmlFile);
            } catch (Exception e) {
                System.err.println("Warning: Failed to parse " + xmlFile.getName() + ": " + e.getMessage());
            }
        }
        return extractFromConfigurationWithBranches(branchConfigBuilder.getConfiguration());
    }

    /**
     * Configurationに登録されたMappedStatementsからSQLを分岐パターン別に抽出する。
     */
    private List<SqlResult> extractFromConfigurationWithBranches(Configuration configuration) {
        List<SqlResult> results = new ArrayList<>();
        Set<String> processedIds = new HashSet<>();

        for (Object obj : configuration.getMappedStatements()) {
            if (!(obj instanceof MappedStatement)) {
                continue;
            }
            MappedStatement ms = (MappedStatement) obj;

            if (!processedIds.add(ms.getId())) {
                continue;
            }

            try {
                List<SqlResult> branchResults = extractWithBranches(ms);
                results.addAll(branchResults);
            } catch (Exception e) {
                System.err.println("Warning: Failed to extract SQL from " + ms.getId() + ": " + e.getMessage());
            }
        }

        results.sort(Comparator.comparing(SqlResult::getFullId)
                .thenComparing(r -> r.getBranchPattern() != null ? r.getBranchPattern().ordinal() : 0));
        return results;
    }

    /**
     * 単一のMappedStatementから分岐パターン別にSQL文を抽出する。
     * DynamicSqlSourceの場合、ALL_SET/ALL_NULLの2パターンを試行する。
     * 同一SQLが生成される場合はALL_SETのみに重複排除する。
     */
    private List<SqlResult> extractWithBranches(MappedStatement ms) {
        String fullId = ms.getId();
        String namespace = "";
        String id = fullId;

        int lastDot = fullId.lastIndexOf('.');
        if (lastDot > 0) {
            namespace = fullId.substring(0, lastDot);
            id = fullId.substring(lastDot + 1);
        }

        String commandType = ms.getSqlCommandType().name();
        SqlSource sqlSource = ms.getSqlSource();
        boolean isDynamic = sqlSource instanceof DynamicSqlSource;

        List<SqlResult> results = new ArrayList<>();

        // ALL_SET pattern (always produced)
        Map<String, Object> allSetParams = createAllSetParams(ms);
        BoundSql allSetBoundSql = sqlSource.getBoundSql(allSetParams);
        String allSetRawSql = allSetBoundSql.getSql();
        String allSetFormattedSql = SqlFormatter.normalize(allSetRawSql);
        List<SqlResult.ParameterInfo> allSetParamInfos = extractParameterInfos(allSetBoundSql);
        Map<String, Object> allSetParamValues = collectParameterValues(allSetBoundSql, allSetParams);

        results.add(new SqlResult(namespace, id, commandType, allSetFormattedSql,
                allSetParamInfos, BranchPattern.ALL_SET, allSetParamValues));

        // ALL_NULL pattern (only for DynamicSqlSource)
        if (isDynamic) {
            try {
                Map<String, Object> allNullParams = new NullParameterMap();
                BoundSql allNullBoundSql = sqlSource.getBoundSql(allNullParams);
                String allNullRawSql = allNullBoundSql.getSql();
                String allNullFormattedSql = SqlFormatter.normalize(allNullRawSql);

                // Deduplicate: if ALL_NULL produces the same SQL as ALL_SET, skip it
                if (!allNullFormattedSql.equals(allSetFormattedSql)) {
                    List<SqlResult.ParameterInfo> allNullParamInfos = extractParameterInfos(allNullBoundSql);
                    Map<String, Object> allNullParamValues = collectParameterValues(allNullBoundSql, allNullParams);

                    results.add(new SqlResult(namespace, id, commandType, allNullFormattedSql,
                            allNullParamInfos, BranchPattern.ALL_NULL, allNullParamValues));
                }
            } catch (Exception e) {
                System.err.println("Warning: ALL_NULL extraction failed for " + fullId
                        + " (e.g. foreach with null collection): " + e.getMessage());
            }
        }

        return results;
    }

    /**
     * ALL_SETパターン用のダミーパラメータを生成する。
     * ClasspathTypeResolverが設定されている場合はparameterTypeのクラス情報から
     * 型に基づいたダミー値を生成し、DummyParamMapにマージする。
     */
    private Map<String, Object> createAllSetParams(MappedStatement ms) {
        DummyParamMap params = new DummyParamMap();

        if (typeResolver != null) {
            Class<?> paramType = ms.getParameterMap().getType();
            if (paramType != null && paramType != Object.class) {
                try {
                    Map<String, Object> typedParams = typeResolver.generateAllSetParams(paramType);
                    params.putAll(typedParams);
                } catch (Exception e) {
                    // フォールバック: 型解決に失敗してもDummyParamMapで動作する
                    System.err.println("Warning: Failed to resolve parameter type "
                            + paramType.getName() + ": " + e.getMessage());
                }
            }
        }

        return params;
    }

    /**
     * BoundSqlからパラメータ情報リストを抽出するヘルパー。
     */
    private List<SqlResult.ParameterInfo> extractParameterInfos(BoundSql boundSql) {
        return boundSql.getParameterMappings().stream()
                .map(pm -> new SqlResult.ParameterInfo(
                        pm.getProperty(),
                        pm.getJavaType() != null ? pm.getJavaType().getSimpleName() : null,
                        pm.getJdbcType() != null ? pm.getJdbcType().name() : null
                ))
                .collect(Collectors.toList());
    }

    /**
     * BoundSqlのパラメータマッピングに基づき、使用されたパラメータ値を収集するヘルパー。
     */
    private Map<String, Object> collectParameterValues(BoundSql boundSql, Map<String, Object> params) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (var pm : boundSql.getParameterMappings()) {
            String property = pm.getProperty();
            // MyBatis内部のforeach変数（__frch_*）はユーザーに無意味なので除外
            if (property.startsWith("__frch_")) {
                continue;
            }
            Object value = params.get(property);
            values.put(property, value);
        }
        return values;
    }
}
