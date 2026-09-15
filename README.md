# Enterprise IM Full Stack

包含：
- web：Vue 3 + TypeScript + Vite
- server：Spring Boot 3 + Java 21
- MySQL 8
- Redis 7
- WebSocket
- Docker Compose

## 启动基础设施
```bash
docker compose up -d
```

## 启动后端
```bash
cd server
mvn spring-boot:run
```

后端地址：http://localhost:8080  
健康检查：http://localhost:8080/api/health

## 启动前端
```bash
cd web
npm install
npm run dev
```
前端：http://localhost:5173

## 默认数据库
- 数据库：enterprise_im
- 用户：im
- 密码：im123456
- MySQL端口：3306
- Redis端口：6379

当前为可运行基础版：健康检查、用户注册/登录、会话消息、WebSocket广播、Redis缓存示例。生产环境请替换密钥、完善权限、消息可靠投递、审计、限流和部署配置。
