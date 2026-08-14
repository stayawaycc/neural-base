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
        if (crawlerSourceMapper.selectCount(null) == 0) {
            CrawlerSource github = new CrawlerSource();
            github.setName("GitHub Trending");
            github.setUrl("https://github.com/trending");
            github.setType("html");
            github.setEnabled(true);
            crawlerSourceMapper.insert(github);

            CrawlerSource hn = new CrawlerSource();
            hn.setName("Hacker News");
            hn.setUrl("https://hacker-news.firebaseio.com/v0/topstories.json");
            hn.setType("api");
            hn.setEnabled(true);
            crawlerSourceMapper.insert(hn);
        }
    }
}
