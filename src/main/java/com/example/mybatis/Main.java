package com.example.mybatis;

import com.example.mybatis.extractor.SqlExtractor;
import com.example.mybatis.extractor.SqlResult;
import com.example.mybatis.formatter.SqlFormatter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * MyBatis SQL Extractor のCLIエントリーポイント。
 *
 * 使用法:
 *   java -jar mybatis-sql-extractor.jar [options] <input>
 *
 * オプション:
 *   --input <path>    Mapper XMLファイルまたはディレクトリ（必須）
 *   --output <path>   出力先ファイル（省略時は標準出力）
 *   --format <type>   出力形式: text（デフォルト）/ json
 *   --formatted       SQLを整形して出力
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String inputPath = null;
        String outputPath = null;
        String format = "text";
        boolean formatted = false;

        // コマンドライン引数の解析
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input":
                case "-i":
                    if (i + 1 < args.length) inputPath = args[++i];
                    break;
                case "--output":
                case "-o":
                    if (i + 1 < args.length) outputPath = args[++i];
                    break;
                case "--format":
                case "-f":
                    if (i + 1 < args.length) format = args[++i];
                    break;
                case "--formatted":
                    formatted = true;
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
                default:
                    // 最後の引数をinputとして扱う
                    if (inputPath == null && !args[i].startsWith("-")) {
                        inputPath = args[i];
                    }
                    break;
            }
        }

        if (inputPath == null) {
            System.err.println("Error: input path is required.");
            printUsage();
            System.exit(1);
        }

        try {
            File input = new File(inputPath);
            if (!input.exists()) {
                System.err.println("Error: input path does not exist: " + inputPath);
                System.exit(1);
            }

            SqlExtractor extractor = new SqlExtractor();
            List<SqlResult> results;

            if (input.isDirectory()) {
                results = extractor.extractFromDirectory(input);
            } else {
                results = extractor.extractAll(input);
            }

            // 出力
            String output;
            if ("json".equalsIgnoreCase(format)) {
                output = toJson(results);
            } else {
                output = toText(results, formatted);
            }

            if (outputPath != null) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
                    writer.print(output);
                }
                System.out.println("Output written to: " + outputPath);
            } else {
                System.out.println(output);
            }

            System.err.println("Extracted " + results.size() + " SQL statements.");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String toText(List<SqlResult> results, boolean formatted) {
        StringBuilder sb = new StringBuilder();
        for (SqlResult result : results) {
            sb.append("=== ").append(result.getFullId()).append(" ===\n");
            sb.append("Type: ").append(result.getSqlCommandType()).append("\n");
            sb.append("SQL:\n");

            String sql = formatted ? SqlFormatter.format(result.getSql()) : result.getSql();
            for (String line : sql.split("\n")) {
                sb.append("  ").append(line).append("\n");
            }

            if (result.getParameters() != null && !result.getParameters().isEmpty()) {
                sb.append("Parameters: ").append(result.getParameters()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String toJson(List<SqlResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < results.size(); i++) {
            SqlResult r = results.get(i);
            sb.append("  {\n");
            sb.append("    \"namespace\": \"").append(escapeJson(r.getNamespace())).append("\",\n");
            sb.append("    \"id\": \"").append(escapeJson(r.getId())).append("\",\n");
            sb.append("    \"type\": \"").append(r.getSqlCommandType()).append("\",\n");
            sb.append("    \"sql\": \"").append(escapeJson(r.getSql())).append("\",\n");
            sb.append("    \"parameters\": [");

            List<SqlResult.ParameterInfo> params = r.getParameters();
            if (params != null && !params.isEmpty()) {
                sb.append("\n");
                for (int j = 0; j < params.size(); j++) {
                    SqlResult.ParameterInfo p = params.get(j);
                    sb.append("      {");
                    sb.append("\"property\": \"").append(escapeJson(p.getProperty())).append("\"");
                    if (p.getJavaType() != null) {
                        sb.append(", \"javaType\": \"").append(escapeJson(p.getJavaType())).append("\"");
                    }
                    if (p.getJdbcType() != null) {
                        sb.append(", \"jdbcType\": \"").append(escapeJson(p.getJdbcType())).append("\"");
                    }
                    sb.append("}");
                    if (j < params.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("    ");
            }
            sb.append("]\n");
            sb.append("  }");
            if (i < results.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void printUsage() {
        System.out.println("MyBatis SQL Extractor - Extract SQL from Mapper XML files");
        System.out.println();
        System.out.println("Usage: mybatis-sql-extractor [options] <input>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --input, -i <path>    Mapper XML file or directory (required)");
        System.out.println("  --output, -o <path>   Output file (default: stdout)");
        System.out.println("  --format, -f <type>   Output format: text (default), json");
        System.out.println("  --formatted           Format SQL with indentation");
        System.out.println("  --help, -h            Show this help message");
    }
}
