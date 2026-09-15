package com.example.im.user;

import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserRepository repo;
  private final RedisTemplate<String,String> redis;
  public UserController(UserRepository repo, RedisTemplate<String,String> redis){this.repo=repo;this.redis=redis;}

  @PostMapping("/register")
  public User register(@RequestBody User user) {
    if(repo.findByUsername(user.getUsername()).isPresent()) throw new IllegalArgumentException("用户名已存在");
    user.setDisplayName(user.getUsername());
    return repo.save(user);
  }

  @PostMapping("/login")
  public Map<String,Object> login(@RequestBody Map<String,String> body) {
    User u=repo.findByUsername(body.get("username")).orElseThrow(()->new IllegalArgumentException("用户不存在"));
    if(!u.getPassword().equals(body.get("password"))) throw new IllegalArgumentException("密码错误");
    redis.opsForValue().set("im:online:"+u.getId(),"1");
    return Map.of("userId",u.getId(),"username",u.getUsername(),"displayName",u.getDisplayName(),"token","demo-token-"+u.getId());
  }
}
