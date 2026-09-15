<div align="center">

# Enterprise IM Full Stack

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.x-4fc08d.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue.svg)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

English | [简体中文](./README_zh-CN.md)

An enterprise-grade Instant Messaging (IM) system built with Spring Boot 3, Vue 3, and WebSocket.

</div>

---

## 🛠️ Tech Stack

- **Frontend**: Vue 3, TypeScript, Vite, Pinia, Vue Router
- **Backend**: Spring Boot 3, Java 21, Spring Security, Spring Data JPA / MyBatis-Plus
- **Real-time Messaging**: WebSocket (STOMP / SockJS)
- **Data & Caching**: MySQL 8.0, Redis 7.0
- **DevOps**: Docker, Docker Compose

---

## 🚀 Quick Start

### Prerequisites
- Node.js >= 18.x
- JDK >= 21
- Docker & Docker Compose

### 1. Start Infrastructure
Start MySQL 8 and Redis 7 containers:
```bash
docker compose up -d