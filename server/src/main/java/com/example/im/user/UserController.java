package com.example.im.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户登录
     *
     * POST /api/users/login
     *
     * 请求示例：
     * {
     *   "username": "demo",
     *   "password": "123456"
     * }
     */
    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginRequest request
    ) {
        // 检查请求参数
        if (request.getUsername() == null
                || request.getUsername().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名和密码不能为空"
            );
        }

        String username = request.getUsername().trim();

        // 查询用户
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "用户名或密码错误"
                        )
                );

        /*
         * 当前项目为开发测试版本，暂时使用明文密码比较。
         * 正式项目应该使用 BCrypt 加密密码。
         */
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())
                && !user.getPassword().equals(request.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        return buildUserResponse(user, "登录成功");
    }

    /**
     * 用户注册
     *
     * POST /api/users/register
     *
     * 请求示例：
     * {
     *   "username": "demo",
     *   "password": "123456",
     *   "displayName": "测试用户"
     * }
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(
            @RequestBody RegisterRequest request
    ) {
        // 检查请求参数
        if (request.getUsername() == null
                || request.getUsername().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名和密码不能为空"
            );
        }

        String username = request.getUsername().trim();

        // 检查用户名是否已经存在
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "用户名已经存在"
            );
        }

        // 创建用户
        User user = new User();

        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        if (request.getDisplayName() == null
                || request.getDisplayName().isBlank()) {
            user.setDisplayName(username);
        } else {
            user.setDisplayName(
                    request.getDisplayName().trim()
            );
        }

        User savedUser = userRepository.save(user);

        return buildUserResponse(savedUser, "注册成功");
    }

    /**
     * 搜索用户
     *
     * GET /api/users/search?keyword=demo
     */
    @GetMapping("/search")
    public List<UserResponse> searchUsers(
            @RequestParam String keyword
    ) {
        String value = keyword == null
                ? ""
                : keyword.trim();

        if (value.isEmpty()) {
            return List.of();
        }

        return userRepository
                .findByUsernameContainingIgnoreCase(value)
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    /**
     * 根据用户 ID 查询用户
     *
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public UserResponse getUser(
            @PathVariable Long id
    ) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "用户不存在"
                        )
                );

        return toUserResponse(user);
    }

    /**
     * 构造登录/注册返回结果
     */
    private Map<String, Object> buildUserResponse(
            User user,
            String message
    ) {
        return Map.of(
                "success", true,
                "message", message,
                "user", toUserResponse(user)
        );
    }

    /**
     * 将 User 转换成不包含密码的 UserResponse
     */
    private UserResponse toUserResponse(User user) {
        String displayName = user.getDisplayName();

        if (displayName == null || displayName.isBlank()) {
            displayName = user.getUsername();
        }

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                displayName
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

    /**
     * 注册请求对象
     */
    public static class RegisterRequest {

        private String username;
        private String password;
        private String displayName;

        public RegisterRequest() {
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

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * 返回给前端的用户信息
     *
     * 注意：这里不返回 password
     */
    public static class UserResponse {

        private Long id;
        private String username;
        private String displayName;

        public UserResponse() {
        }

        public UserResponse(
                Long id,
                String username,
                String displayName
        ) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}