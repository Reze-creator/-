# 泊序 Parking 园区停车充电预约平台

以代码可读性为主的 Java 后端实习项目，配套 Vue 界面。当前流程：**预约 → 模拟预付 → 模拟入场 → 停车或充电 → 离场计费 → 模拟补退差额 → 异常处理与监控**。

按最新要求，**AI 模块暂不实现**。没有引入微服务、Redis、RabbitMQ 或真实设备。全部设备、支付和退款均为本地模拟，不发生真实资金交易。

## 本机启动

项目在 `D:\parking`，使用本机 MySQL，业务库 `parking`，隔离测试库 `parking_test`。本机 `.env` 已配置；真实凭证不放进源码和文档。

```powershell
cd D:\parking
.\scripts\start.ps1 -Background
# 停止
.\scripts\stop.ps1
# 修改源码后重新构建启动
.\scripts\stop.ps1
.\scripts\start.ps1 -Build -Background
```

访问 **http://localhost:8090**。新版首次启动由 Flyway 执行 V2 增量迁移，新增设备、事件、会话、账单、工单和审计表，保留第一阶段账号与预约。

| 账号 | 密码 | 用途 |
| --- | --- | --- |
| demo | Demo@123 | 预约、模拟支付、本人会话账单 |
| admin | Admin@123 | 本人能力，加设备模拟、全平台会话账单、资源管理、运营中心 |

初始化只补缺失记录，不覆盖已有密码；也可注册普通车主。

## 十分钟体验

1. demo 登录，预约明天的充电车位，如 09:00—12:00，提交并模拟支付。
2. 换 admin 登录，打开“设备模拟”，为对应资源发送正常心跳。
3. 选择已支付预约，点击“生成演示批次”，再点击“倒序发送”，无需等到预约日期。
4. 观察事件最终按序号全部应用；再次“重复发送”复用原事件号，不重复结算。
5. 打开“平台会话账单”看最终费用；demo 仅能查看本人数据。
6. 在设备模拟发送故障心跳，运营中心出现工单；先恢复正常心跳，再填写说明关单。
7. 运营中心还能看请求次数、平均及窗口最大耗时、操作审计及请求标识。

普通车位批次包括入场和离场；充电车位包括入场、开始充电、电表、停止充电、离场。手动事件支持补发、失败场景和填写原因后重试或跳过。

## 业务规则

- 预约起止半小时对齐，30分钟至24小时，最多提前30天；API允许跨日，页面选择同日。
- 区间 `[开始,结束)`，相邻不冲突。有效占用包括未过期待支付、预约成功、使用中。
- 待支付保留至 `min(现在+5分钟,预约开始)`；同用户与 requestId 幂等，不延长保留。
- 创建、支付、取消、事件处理遵守“资源锁 → 预约锁”，修正支付跨截止时间与新建竞争问题。
- 开始前且未入场可取消，已支付模拟全额退款；入场后不能取消，离场进入最终结算。
- 事件按预约连续 sequenceNo 处理，缺号等待、业务错误存 FAILED 并阻塞后序；eventId 同内容幂等。
- 电量是会话累计 kWh，不可回退。普通车位不能充电，一次会话只支持一段充电。
- 实际停车不足一分钟进一分，金额=小时单价×分钟÷60；电费=累计电量×入场电价快照，默认1.20元/kWh。
- 最终总额减预付款得到模拟补付或退回。同预约和会话最多一张账单，结算与会话、事件一同提交或回滚。
- 模拟事件可以填未来预约时间，便于快速演示；服务器真实时间仍控制会话认证、预约保留、心跳超时。

## 技术与结构

Java17、Spring Boot3.5.16、MyBatis-Plus3.5.18、MySQL8.0、Flyway、Springdoc、Micrometer；Vue3.5.45、Vite6.4.5、原生CSS。认证是随机会话令牌与BCrypt，不是JWT。

```text
backend/src/main/java/com/parking/
  controller/    路由、参数、当前用户、响应
  service/       业务接口
    impl/        规则编排和事务
  mapper/        数据访问与加锁 SQL
  entity/        数据库实体
  dto/           请求参数
  vo/            对外响应
  common/        预约规则、会话校验、计费、异常与跟踪
  config/        鉴权、初始化、过期和异常扫描
frontend/src/
  App.vue              主界面编排
  SimulatorView.vue    心跳、演示批次、手动事件
  UsageView.vue        会话和账单
  OperationsView.vue   工单、审计和监控
docs/                  设计、接口、验收和压测结果
scripts/               构建、启动、测试和隔离压测
```

坚持 Controller → Service接口 → ServiceImpl → Mapper，构造器注入、DTO/VO/Entity分离。纯计费和会话校验分别位于 BillingRules、SessionRules；关键锁顺序、幂等、乱序、电表、回滚与模拟边界有中文注释。ArchUnit检查分层，Google Java Format与Prettier检查格式。

## 构建测试与压测

```powershell
.\scripts\build.ps1 -MySqlTests
.\scripts\load-test.ps1 -Requests 500 -Concurrency 20
```

完整构建运行格式检查、前端构建、H2与真实MySQL测试并打包。压测在隔离 parking_test 库及8091端口启动临时实例，完成后停止，不操作业务库。测试库不能保存需要保留的数据；接口测试每例会清理业务表，压测只创建测试账号与预约并取消自己的预约。

结果保存在 `docs/performance/latest.json`，含时间、并发、样本量、延迟分位数、吞吐、状态码与并发正确性。结果仅表示本机小数据集，不作为生产容量承诺。

## 资料

- [会话与运营设计](docs/07-会话与运营设计.md)
- [原预约接口](docs/02-接口文档.md)、[会话与运营接口](docs/08-会话与运营接口文档.md)
- [本轮验收报告](docs/09-会话与运营验收报告.md)、[代码阅读指南](docs/10-代码阅读指南.md)
- Apifox导入 `docs/apifox-openapi.json`，在线 `/v3/api-docs` 和 `/swagger-ui/index.html`。
- `docs/设计说明书` 中两份Word文件是第一阶段历史基线；新增实现和已修正边界以本轮设计为准。

接口变更重启后用 `scripts/export-openapi.ps1` 导出。新机器按 `.env.example` 和 `scripts/create-databases.sql` 初始化。默认8090，独立前端开发5173代理 `/api` 到8090。

## 范围

第二阶段及第四阶段中的工单、审计、进程内监控、正确性验证和隔离压测已实现；AI及AI评测暂缓。当前没有真实设备认证、真实支付、生产TLS、分布式调度、外部告警、Prometheus/Grafana、长期指标存储、多段充电或商业峰谷电价。
