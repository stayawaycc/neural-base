package com.neuralbase.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.auth.LoginRequest;
import com.neuralbase.common.Result;
import com.neuralbase.entity.User;
import com.neuralbase.mapper.UserMapper;
import com.neuralbase.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String COOKIE_NAME = "nb_token";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final long tokenExpireHours;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                          @Value("${neuralbase.auth.token-expire-hours}") long tokenExpireHours) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenExpireHours = tokenExpireHours;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<String>> login(@Valid @RequestBody LoginRequest req,
                                                HttpServletResponse response) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401, "用户名或密码错误"));
        }
        String token = jwtUtil.generateToken(user.getUsername());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie(token).build().toString());
        return ResponseEntity.ok(Result.ok(token));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie("").maxAge(Duration.ZERO).build().toString());
        return Result.ok(null);
    }

    @GetMapping("/me")
    public Result<User> me(Authentication authentication) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, authentication.getName()));
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    private ResponseCookie.ResponseCookieBuilder authCookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofHours(tokenExpireHours));
    }
}
