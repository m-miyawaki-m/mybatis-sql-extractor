package com.example.mybatis.extractor;

import com.example.mybatis.config.MyBatisConfigBuilder;
import com.example.mybatis.formatter.SqlFormatter;
import com.example.mybatis.parameter.DummyParameterGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
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

    public SqlExtractor() {
        this.configBuilder = new MyBatisConfigBuilder();
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
}
