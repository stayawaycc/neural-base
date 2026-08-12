package com.neuralbase.config;

import com.neuralbase.entity.Kb;
import com.neuralbase.entity.User;
import com.neuralbase.mapper.KbMapper;
import com.neuralbase.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final KbMapper kbMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DataInitializer(UserMapper userMapper, KbMapper kbMapper, PasswordEncoder passwordEncoder,
                           @Value("${neuralbase.auth.default-admin-password}") String adminPassword) {
        this.userMapper = userMapper;
        this.kbMapper = kbMapper;
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
        if (kbMapper.selectCount(null) == 0) {
            Kb defaultKb = new Kb();
            defaultKb.setName("默认知识库");
            defaultKb.setDescription("系统默认知识库");
            kbMapper.insert(defaultKb);
        }
    }
}
