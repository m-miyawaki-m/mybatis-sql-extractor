package com.example.mybatis.config;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * MyBatis Configurationをプログラム的に構築するビルダー。
 * DB接続は不要で、ダミーのDataSourceを使用する。
 */
public class MyBatisConfigBuilder {

    private final Configuration configuration;

    public MyBatisConfigBuilder() {
        // ダミーDataSourceでEnvironmentを構築
        // SQL抽出のみが目的なので実際のDB接続は不要
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:dummy",
                "sa",
                ""
        );
        Environment environment = new Environment(
                "extraction",
                new JdbcTransactionFactory(),
                dataSource
        );
        this.configuration = new Configuration(environment);
    }

    /**
     * Mapper XMLファイルを読み込んでConfigurationに登録する。
     *
     * @param mapperFile Mapper XMLファイル
     * @throws IOException ファイル読み込みエラー
     */
    public void addMapper(File mapperFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(mapperFile)) {
            String resource = mapperFile.getAbsolutePath();
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                    inputStream, configuration, resource, configuration.getSqlFragments()
            );
            mapperBuilder.parse();
        }
    }

    /**
     * Mapper XMLファイルをInputStreamから読み込んでConfigurationに登録する。
     *
     * @param inputStream Mapper XMLのInputStream
     * @param resource    リソース名（識別用）
     */
    public void addMapper(InputStream inputStream, String resource) {
        XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                inputStream, configuration, resource, configuration.getSqlFragments()
        );
        mapperBuilder.parse();
    }

    /**
     * 構築したConfigurationを返す。
     */
    public Configuration getConfiguration() {
        return configuration;
    }
}
