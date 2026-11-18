# 光影评分 · Spring Boot 后端

新版逻辑：网页点击“开始接单”即表示有人工（由心跳保持）；点击“暂停接单”或页面离线表示无人。手机只输入IP和端口即可上传。

这是电脑端项目：接收 Android 图片、显示人工评分工作台、返回人工结果或文字回退指令。DeepSeek 由 Android 直接请求，后端不接收 DeepSeek token 或 prompt。

## 本地运行

- JDK 17 或更高版本；Maven 3.6.3+。
- 用 IDEA 打开本目录的 `pom.xml`，等待 Maven 同步。
- 运行 `com.example.rating.RatingApplication`；或在本目录执行 `mvn spring-boot:run`。
- 电脑浏览器打开 `http://127.0.0.1:8080/`。
- 复制启动终端中“管理员密钥:”后面的内容，粘贴到网页顶部“管理员密钥”输入框，点击“开始接单”。密钥为空时不会发送请求；校验失败会停止自动重试并解锁输入框，修改后再次点击。
- 手机与电脑连接同一局域网，Android 设置服务器地址为 `http://电脑局域网IPv4:8080`，手机不需要任何连接密钥。
- Ubuntu 可运行 `hostname -I` 查看 IP；防火墙需要允许手机访问 TCP 8080。不要填写 127.0.0.1 或 0.0.0.0；模拟器可使用 `http://10.0.2.2:8080`。

执行 `mvn test` 运行测试。执行 `mvn package` 构建 JAR；然后 `java -jar target/image-rating-server-1.0.0.jar`。

## 人工接单与回退

1. 评分页点击“开始接单”，每 3 秒续一次心跳；最后一次心跳超过 15 秒即视为无人。
2. Android 上传原图（JPEG/PNG/GIF/WebP，最大 8 MiB），服务端根据文件头识别格式。图片和任务保存在 H2 数据库。
3. 人工在线：任务进入 `pending`，手机每 2 秒查询结果。网页填写 0–100 分及评语，提交后变为 `rated`。
4. 无人在线、主动暂停全部接单页面、所有心跳过期，或等待超过默认 60 秒：任务变为 `fallback`，Android 收到后直接调用 DeepSeek。超时状态在查询/列表/接单心跳/评分请求时更新，无需定时线程。
5. `rated` 与 `fallback` 都是终态，迟到的人工评分返回 409。多个接单页面中只要有一个在线，即仍算有人接单。
6. 重复上传相同 request_id 和相同内容会返回原任务，内容不同则返回 409。

浏览器后台标签页可能被系统节流，超过 15 秒未发心跳也会视为离线。网页最多展示最近 100 个任务。重启后人工在线状态不恢复，请重新点击接单；未完成任务在下一次状态检查时回退。

## 配置与数据

`src/main/resources/application.properties`：

| 环境变量 | 默认值 | 含义 |
|---|---|---|
| PORT | 8080 | HTTP端口 |
| RATING_WAIT_SECONDS | 60 | 人工最长等待秒数，5–3600 |
| RATING_ADMIN_KEY | 自动生成 | 网页人工操作密钥 |

首次启动在**当前运行目录**生成 `data/keys.properties`，以后复用。H2 数据位于 `data/ratings.mv.db`。请在同一目录启动以复用数据。删除数据前先停止程序；无需额外安装 MySQL。

实验项目使用局域网 HTTP，密钥保存在本地配置中，请用于可信的实验网络。数据库不自动清理历史图片；停止程序后可备份整个 data 目录。图片不上传公网。

## 接口

手机上传和查询不需要Authorization。只有 `/api/admin/` 接口需要 `Authorization: Bearer <管理员密钥>`。JSON请求。请求体最大 12 MiB，POST 需要 Content-Length。

| 方法与路径 | 权限 | 请求/返回 |
|---|---|---|
| GET /health | 无 | 健康检查 |
| POST /api/tasks | 无需密钥 | `{request_id, description, image_base64}` → 任务 |
| GET /api/tasks/{id} | 无需密钥 | 任务状态与人工评分 |
| POST /api/admin/heartbeat | 管理员 | `{session, online}`，online为布尔值 |
| GET /api/admin/tasks | 管理员 | `{online, now, tasks}` |
| GET /api/admin/tasks/{id}/image | 管理员 | 图片原始字节 |
| POST /api/admin/tasks/{id}/rate | 管理员 | `{score, comment}` → 人工完成任务 |

任务字段：`id, description, mime, status, reason, created, deadline, score, comment, completed`。时间为 Unix 秒。`reason` 为 `operator_offline` 或 `human_timeout`。DeepSeek结果留在手机，不回传本项目。

## 目录

- `RatingService.java`：持久化、人工在线租约、状态切换、幂等。
- `RatingController.java`：HTTP接口。
- `AccessFilter.java`：管理员密钥、请求长度检查。
- `resources/static/index.html`：电脑人工评分页，无外部前端依赖。
- `src/test`：服务状态、并发评分、幂等与HTTP接口测试。
