package com.neuralbase.config;

import com.neuralbase.entity.CrawlerSource;
import com.neuralbase.entity.User;
import com.neuralbase.mapper.CrawlerSourceMapper;
import com.neuralbase.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final CrawlerSourceMapper crawlerSourceMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DataInitializer(UserMapper userMapper, CrawlerSourceMapper crawlerSourceMapper,
                           PasswordEncoder passwordEncoder,
                           @Value("${neuralbase.auth.default-admin-password}") String adminPassword) {
        this.userMapper = userMapper;
        this.crawlerSourceMapper = crawlerSourceMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            userMapper.insert(admin);
        }
        seedNewsSources();
    }

    private void seedNewsSources() {
        // 按 name 幂等:存量库(已有部分源)也能补齐新源
        seedSource("GitHub Trending", "https://github.com/trending", "html");
        seedSource("Hacker News", "https://hacker-news.firebaseio.com/v0/topstories.json", "api");
        seedSource("Papers with Code", "https://paperswithcode.com", "html");
        seedSource("机器之心", "https://www.jiqizhixin.com/articles", "html");
        seedSource("InfoQ", "https://www.infoq.cn/", "html");
        seedSource("GitHub Star AI", "https://api.github.com/search/repositories", "api");
    }

    private void seedSource(String name, String url, String type) {
        if (crawlerSourceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CrawlerSource>()
                    .eq(CrawlerSource::getName, name)) > 0) {
            return;
        }
        CrawlerSource s = new CrawlerSource();
        s.setName(name);
        s.setUrl(url);
        s.setType(type);
        s.setEnabled(true);
        crawlerSourceMapper.insert(s);
    }
}
