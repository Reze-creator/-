# 使用Apifox测试

## 导入

启动项目后，在Apifox创建项目，选择导入OpenAPI/Swagger，导入`D:\parking\docs\apifox-openapi.json`。也可从`http://localhost:8090/v3/api-docs`导入在线文档。前置URL为`http://localhost:8090`，选择本机开发环境，不使用Mock环境。

## 配置环境变量

| 变量 | 值 |
|---|---|
| token | 登录返回的data.token |
| resourceId | 查询资源返回的某一条id |
| bookingId | 创建预约返回的data.id |
| requestId | apifox-booking-001，每个新业务尝试更换 |
| eventId | apifox-enter-001，每个新设备事件更换，重发保持原值 |
| incidentId | 管理员工单列表返回的id |
| startAt | 测试时未来日期，例如明天09:00，带+08:00 |
| endAt | 对应日期12:00，带+08:00 |

需要登录的接口：Auth选择Bearer Token，Token填`{{token}}`。注意不要同时在Token输入框再次拼接Bearer；也可直接设Header `Authorization: Bearer {{token}}`。

## 推荐顺序

1. 登录：demo / Demo@123，保存token。
2. 查询园区、资源，保存resourceId。
3. 设置未来startAt、endAt，查询指定时段资源，确认available=true。查询参数中的+08:00需要URL编码；Apifox参数编辑器会处理，也可使用UTC的Z结尾格式。
4. 创建预约，保存bookingId，状态应为PENDING_PAYMENT。
5. 查询本人预约；再提交相同资源相同时段的新requestId，应返回409 RESOURCE_UNAVAILABLE。
6. 模拟支付bookingId，状态变为CONFIRMED；再次支付仍只有一条支付记录。
7. 开始前取消，状态CANCELLED、paymentStatus=REFUNDED。
8. 新requestId再次预约原时段，应成功。
9. 用另一个普通账号访问原预约，返回404；用admin登录，可以查看全量预约，但不能替其他人支付/取消。

会话与运营流程请另建一笔预约并支付，然后按[新增接口文档](08-会话与运营接口文档.md)用 admin 发送入场、开充、电表、停充、离场事件。设备心跳路径使用 resourceId，事件处理路径使用 eventId，关闭工单路径使用 incidentId。设备事件返回200时仍需检查 data.status。
10. 管理员停用资源，新预约返回409；再次启用后可以预约。

同一requestId输入不变是重试，会返回已有预约；测试“时段冲突”时必须更换requestId。日期过期、格式未对齐时400属于预期校验。

## 前端与源码

默认访问http://localhost:8090即可使用打包前端。修改源码后执行`scripts/build.ps1`并重启服务；仅修改前端开发时可在frontend运行npm run dev，使用5173的开发服务器。

后端源码入口：controller/BookingController → service/BookingService → service/impl/BookingServiceImpl → mapper/BookingMapper、ResourceMapper。时段规则在common/BookingRules。预约创建使用TransactionTemplate，以便在唯一键失败事务回滚后读取幂等结果；支付和取消使用@Transactional。

## 常见问题

- 401：token过期或未填，重新登录。
- 409 RESOURCE_UNAVAILABLE：已有有效重叠预约；换时段、资源或取消之前预约。
- 409 IDEMPOTENCY_CONFLICT：同一requestId改变参数；新尝试换新请求号。
- 支付返回EXPIRED：5分钟保留期或预约开始时间已到，重新预约。
- 看不到新前端：修改后重新构建并重启，开发时也可直接使用5173。
- 8090占用：修改.env中的SERVER_PORT并重启；前端开发代理也要同步修改。
- 数据库错误：确认MySQL服务在运行，本机.env账号可以访问parking。不要直接用建表脚本覆盖已有业务库。
