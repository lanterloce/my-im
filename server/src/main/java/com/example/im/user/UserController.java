package com.example.im.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        }
)
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 用户登录
     *
     * POST /api/users/login
     *
     * 请求：
     * {
     *   "username": "demo",
     *   "password": "123456"
     * }
     */
    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginRequest request
    ) {
        if (request.getUsername() == null
                || request.getUsername().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名和密码不能为空"
            );
        }

        User user = userRepository
                .findByUsername(request.getUsername().trim())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "用户名或密码错误"
                        )
                );

        // 当前项目是开发阶段，暂时使用明文密码比较
        if (!user.getPassword().equals(request.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        return Map.of(
                "success", true,
                "message", "登录成功",
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "displayName",
                        user.getDisplayName() == null
                                ? user.getUsername()
                                : user.getDisplayName()
                )
        );
    }

    /**
     * 搜索用户
     *
     * GET /api/users/search?keyword=demo
     */
    @GetMapping("/search")
    public List<User> searchUsers(
            @RequestParam String keyword
    ) {
        String value = keyword == null
                ? ""
                : keyword.trim();

        if (value.isEmpty()) {
            return List.of();
        }

        return userRepository
                .findByUsernameContainingIgnoreCase(value);
    }

    /**
     * 根据 ID 获取用户
     */
    @GetMapping("/{id}")
    public User getUser(
            @PathVariable Long id
    ) {
        return userRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "用户不存在"
                        )
                );
    }

    /**
     * 登录请求对象
     */
    public static class LoginRequest {

        private String username;
        private String password;

        public LoginRequest() {
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}