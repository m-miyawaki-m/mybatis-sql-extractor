package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlExtractorBranchTest {

    private File getMapperFile(String name) {
        URL url = getClass().getClassLoader().getResource("mappers/" + name);
        assertNotNull(url, "Test resource not found: mappers/" + name);
        return new File(url.getFile());
    }

    @Test
    void testBranchExtractionDynamicSql() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("dynamic-mapper.xml"));

        List<SqlResult> selectByCondition = results.stream()
                .filter(r -> r.getId().equals("selectByCondition"))
                .toList();

        assertEquals(2, selectByCondition.size(),
                "Dynamic SQL should produce 2 branch patterns");

        SqlResult allSet = selectByCondition.stream()
                .filter(r -> r.getBranchPattern() == BranchPattern.ALL_SET)
                .findFirst().orElseThrow();
        SqlResult allNull = selectByCondition.stream()
                .filter(r -> r.getBranchPattern() == BranchPattern.ALL_NULL)
                .findFirst().orElseThrow();

        assertTrue(allSet.getSql().contains("name = ?"), "ALL_SET should contain name condition");
        assertFalse(allNull.getSql().contains("name = ?"), "ALL_NULL should not contain name condition");
    }

    @Test
    void testBranchExtractionStaticSql() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("simple-mapper.xml"));

        List<SqlResult> selectAll = results.stream()
                .filter(r -> r.getId().equals("selectAll"))
                .toList();

        assertEquals(1, selectAll.size(), "Static SQL should produce only 1 pattern");
        assertEquals(BranchPattern.ALL_SET, selectAll.get(0).getBranchPattern());
    }

    @Test
    void testBranchExtractionDeduplicatesSameSQL() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("simple-mapper.xml"));

        List<SqlResult> selectById = results.stream()
                .filter(r -> r.getId().equals("selectById"))
                .toList();

        assertEquals(1, selectById.size(), "Same SQL for both patterns should be deduplicated");
    }

    @Test
    void testBranchExtractionHasParameterValues() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("dynamic-mapper.xml"));

        SqlResult allSet = results.stream()
                .filter(r -> r.getId().equals("selectByCondition") && r.getBranchPattern() == BranchPattern.ALL_SET)
                .findFirst().orElseThrow();

        assertNotNull(allSet.getParameterValues(), "Should have parameter values");
    }

    @Test
    void testBranchExtractionAllNullForeachSkipped() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("foreach-mapper.xml"));

        List<SqlResult> selectByIds = results.stream()
                .filter(r -> r.getId().equals("selectByIds"))
                .toList();

        assertTrue(selectByIds.size() >= 1, "Should have at least ALL_SET pattern");
    }

    @Test
    void testExistingExtractAllUnchanged() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        assertEquals(5, results.size());
        assertNull(results.get(0).getBranchPattern(), "Existing method should not set branchPattern");
    }
}
