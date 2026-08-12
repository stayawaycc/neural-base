package com.neuralbase.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestKbFixture implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public TestKbFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.update(
            "INSERT INTO kb (id, name, description) VALUES (1, '测试知识库', 'fixture') ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.execute(
            "SELECT setval(pg_get_serial_sequence('kb','id'), GREATEST((SELECT COALESCE(MAX(id),1) FROM kb),1), true)");
    }
}
