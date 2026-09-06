# 部署说明

本目录放生产部署相关文件，与前端 `frontend/dist`（构建产物，不入库）配合使用。

## 文件

| 文件 | 作用 |
|---|---|
| `nginx.conf` | 生产 nginx 配置：托管 dist + SPA 回退 + 反代 `/api`、`/uploads` |
| `preview.mjs` | 零依赖本地预览服务器，模拟 nginx 行为，用于本机验证生产构建 |

## 一、构建前端

```bash
cd frontend
npm run build          # 产出 frontend/dist（含 hash 指纹的静态资源）
```

## 二、本机预览（无需装 nginx）

1. 先启动后端（8080）
2. 再启动预览服务器：

```bash
node deploy/preview.mjs     # 默认 http://localhost:4173
```

`preview.mjs` 等价复刻 nginx 的三件事：

- 托管 `frontend/dist`；
- SPA history 回退（`/job/8`、`/feed` 等前端路由直接访问/刷新回退到 `index.html`）；
- 反代 `/api/*`（去 `/api` 前缀）与 `/uploads/*`（保留路径）到 `127.0.0.1:8080`。

## 三、正式部署（nginx）

1. 安装 nginx（Linux 发行版包管理器或 Windows 解压版均可）；
2. 用 `nginx.conf` 替换默认配置，把 `root` 改成实际 `frontend/dist` 路径；
3. `nginx -t` 校验配置，`nginx -s reload` 生效。

## 关键点

- **去前缀**：后端接口没有 `/api` 前缀，所以 `location /api/ { proxy_pass http://127.0.0.1:8080/; }`
  末尾的 `/` 会把 `/api/` 替换成 `/`，等价于 vite dev 的 `rewrite`。
- **保留路径**：`/uploads/**` 由后端 `MvcConfig` 映射本地目录，`proxy_pass` 不带 URI 即原样透传完整路径。
- **同源免 CORS**：前端与接口经 nginx 同源代理，浏览器不触发 CORS，`authorization` 头（裸 token）原样透传。
- **SPA 回退**：`try_files $uri $uri/ /index.html` 是 history 模式路由的必需品，否则深链/刷新会 404。
