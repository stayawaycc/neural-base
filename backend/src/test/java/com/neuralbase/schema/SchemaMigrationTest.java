package com.neuralbase.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    private Set<String> tableNames() throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    @Test
    void allBusinessTablesExist() throws Exception {
        Set<String> expected = Set.of("users", "kb", "document", "chat_session",
            "chat_message", "baguwen", "daily_news", "crawl_task", "crawler_source", "vector_store");
        Set<String> actual = tableNames();
        assertTrue(actual.containsAll(expected), "缺少表: " + expected.stream().filter(e -> !actual.contains(e)).toList());
    }

    @Test
    void pgvectorExtensionEnabled() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM pg_extension WHERE extname = 'vector'")) {
            rs.next();
            assertTrue(rs.getInt(1) == 1, "pgvector 扩展未启用");
        }
    }

    @Test
    void vectorStoreEmbeddingIs4096Dims() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT format_type(atttypid, atttypmod) FROM pg_attribute " +
                 "WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'")) {
            rs.next();
            assertTrue(rs.getString(1).equals("vector(4096)"), "embedding 列类型应为 vector(4096)，实际: " + rs.getString(1));
        }
    }
}
