# smartGPS 智慧物流项目

这是一个智慧物流前后端项目骨架，包含管理端前端、后端 API、移动端目录和项目文档。

## 主要目录

- `project-rjwm-admin-vue-ts/`：管理端前端。
- `sky-take-out/`：后端 Spring Boot 多模块工程，当前作为智慧物流后端继续改造。
- `mp-weixin/`：移动端/小程序目录。
- `智慧物流_真实版前后端文档包/`：接口、数据库、排期和联调文档。
- `assets/`：项目资源。

## 后端启动

默认启用 `dev` profile，连接本地 PostgreSQL 数据库 `smart_logistics`。

```bash
cd sky-take-out
mvn spring-boot:run -pl sky-server
```

数据库初始化脚本：

```bash
psql -U postgres -d smart_logistics -f sky-server/src/main/resources/sql/backend1_business_init.sql
```

如本地 PostgreSQL 设置了密码，请在启动前配置：

```bash
export DB_PASSWORD=你的数据库密码
```

## 前端启动

```bash
cd project-rjwm-admin-vue-ts
npm install
npm run serve
```

## 演示账号

```txt
shipper / 123456
dispatcher / 123456
warehouse / 123456
admin / 123456
driver / 123456
```

## 接口入口

```txt
http://localhost:8080/api/v1
ws://localhost:8080/api/v1/ws?token=<token>
```
