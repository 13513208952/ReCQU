# ReCQU

以课表查看为核心，兼顾考试安排、成绩与绩点的非官方、非商业重庆大学 Android 客户端，目标覆盖 Android 10–16。

> 当前版本通过重庆大学官方 WebView完成登录。真实查询需要用户亲自在官方页面完成登录及手机验证；ReCQU不接收或代填统一身份认证密码。

## 当前进度

- [x] 原生 Kotlin + Jetpack Compose 工程
- [x] Android 10（API 29）至 Android 16（API 36）配置
- [x] 本人课表查询、周次计算与周视图浏览
- [x] 课表和成绩分别进行完整性校验与原子缓存
- [x] 官方综合绩点（GPA）与本人课程成绩查询
- [x] 下一节课、今日课程两个通用 Android 桌面小组件（仅只读完整课表缓存，通过系统桌面手动添加）
- [x] UI 与学校接口之间的 `CampusRepository` 隔离层
- [x] 默认禁用明文流量、敏感数据云备份与应用备份
- [x] 校园网下验证新版统一身份认证和手机二次认证流程
- [x] 官方 WebView 会话一键清除，不把凭据导出到原生存储
- [ ] 完善本人考试安排并增加独立考试缓存
- [ ] 多学期切换

## 架构原则

1. **只在官方页面登录**：不自行收集或上传统一身份认证密码；手机验证码必须由用户手动完成。
2. **本机直连优先**：个人课表、成绩等尽量只在手机与学校服务间传输。
3. **适配器隔离**：学校接口变化只修改 data 层，不污染 UI 和领域模型。
4. **最小权限**：当前仅申请网络权限；模块按需增加能力。
5. **不绕过安全措施**：不规避验证码、身份认证、访问控制和限流。
6. **结果仅供参考**：考试、成绩等始终以学校官方系统为准。

详见：

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/CAMPUS_NETWORK_VALIDATION.md`](docs/CAMPUS_NETWORK_VALIDATION.md)
- [`docs/OFFICIAL_API_CONTRACT.md`](docs/OFFICIAL_API_CONTRACT.md)
- [`docs/UPSTREAM_RESEARCH.md`](docs/UPSTREAM_RESEARCH.md)
- [`SECURITY.md`](SECURITY.md)

## 构建

需要 JDK 17 和 Android SDK 36：

```bash
./gradlew testDebugUnitTest assembleDebug
```

APK 输出到 `app/build/outputs/apk/debug/`。Release 构建默认不配置签名；正式发布者必须自行安全配置发布密钥，不能使用 Debug 证书。

## 独立声明

本项目不是重庆大学官方应用，也不代表学校发布信息。重庆大学名称及相关标识归其权利人所有。课表、考试、成绩与绩点信息均以学校官方系统为准，请在学校规定和法律允许的范围内使用。

## 参与贡献

提交 Issue 或 Pull Request 前请阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md) 和 [`SECURITY.md`](SECURITY.md)。公开材料中不得包含账号、Cookie、Token、验证码、真实成绩或未脱敏流量记录。

## License

本项目采用 [`AGPL-3.0-only`](LICENSE)。第三方项目与接口研究来源记录见 [`docs/UPSTREAM_RESEARCH.md`](docs/UPSTREAM_RESEARCH.md)。
