# 部署说明（nginx）

本目录放生产部署相关文件，配合前端 `frontend/dist`（构建产物，不入库）与后端（8080）使用。

## 文件

| 文件 | 作用 |
|---|---|
| `nginx.conf` | 生产 nginx 配置：托管 dist + SPA 回退 + 反代 `/api`、`/uploads` |

## 一、构建前端

```bash
cd frontend
npm run build          # 产出 frontend/dist（含 hash 指纹的静态资源）
```

## 二、本机 nginx（便携版，已装）

- 位置：`tools/nginx-1.26.2/`（官方 Windows 便携版，解压即用，无需管理员、无需安装服务）
- 配置：已把本目录 `nginx.conf` 复制为 `tools/nginx-1.26.2/conf/nginx.conf`
- 监听：`http://localhost:8088`（本机 80 端口被系统进程占用，故用 8088）

```bash
cd tools/nginx-1.26.2
./nginx.exe -t              # 校验配置
./nginx.exe                 # 启动（Windows 版前台运行）
./nginx.exe -s reload       # 改配置后热重载
./nginx.exe -s stop         # 停止
```

访问 `http://localhost:8088` 即可，前后端同源、无需 CORS。

## 三、正式部署（服务器 nginx）

1. 安装 nginx（Linux 发行版包管理器或 Windows 解压版均可）；
2. 用本目录 `nginx.conf` 替换默认配置，把 `root` 改成实际 `frontend/dist` 路径、`listen` 改回 `80`；
3. `nginx -t` 校验，`nginx -s reload` 生效。

## 关键点

- **去前缀**：后端接口没有 `/api` 前缀，所以 `location /api/ { proxy_pass http://127.0.0.1:8080/; }`
  末尾的 `/` 会把 `/api/` 替换成 `/`，等价于 vite dev 的 `rewrite`。
- **保留路径**：`/uploads/**` 由后端 `MvcConfig` 映射本地目录，`proxy_pass` 不带 URI 即原样透传完整路径。
- **同源免 CORS**：前端与接口经 nginx 同源代理，浏览器不触发 CORS，`authorization` 头（裸 token）原样透传。
- **SPA 回退**：`try_files $uri $uri/ /index.html` 是 history 模式路由的必需品，否则深链/刷新会 404。
- **长缓存**：`/assets/` 带 hash，可 `expires 30d`；`index.html` 不缓存保证发版即时生效。
