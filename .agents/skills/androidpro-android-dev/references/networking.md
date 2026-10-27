# 网络规范

`NetworkProvider.initialize` 只在 Application 执行一次。Base URL 必须以 `/` 结尾，release 只使用受信任的 HTTPS；不要加入 trust-all TLS。公共 header 与 token 在拦截器集中注入，日志默认只记 BASIC 且随 debug 开关关闭，禁止记录 token、密码和完整个人信息。

Repository 使用以下入口：`get`、`delete`、`deleteJson`、`postJson`、`putJson`、`postForm`、`upload`、`download`。参数、DTO 和返回类型保持明确，不向 ViewModel 暴露 OkHttp Response。后端若有业务码，在 Repository 将传输 DTO 映射为领域结果。

请求返回 `ApiResult.Success` 或 `ApiResult.Failure(NetworkError)`。协程取消必须继续抛出，不能转为失败提示；搜索和筛选等易竞态请求使用 `collectLatest`/`flatMapLatest` 或请求代次，避免旧结果覆盖新状态。

GET 等幂等请求只在明确需要时做有限重试。POST/上传等写操作没有幂等键时禁止自动重放。若增加 token 刷新，需要串行化并发 401、绕开刷新接口自身拦截、限定重放次数，失败后统一清理会话。

下载写入临时文件成功后再原子替换正式文件，业务若需防篡改应校验服务端摘要。上传/下载进度回调来自 I/O 线程，转换为 UI 状态后再显示。
