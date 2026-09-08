# Security Policy

请勿在公开 Issue、提交或聊天记录中发送重庆大学账号、密码、Cookie、Token、验证码、真实成绩或未脱敏抓包文件。

安全问题请使用 GitHub 的[私密漏洞报告](https://github.com/13513208952/ReCQU/security/advisories/new)，不要建立公开 Issue。

## 开发要求

- 禁止记录请求头、Cookie、响应中的个人字段；
- 禁止把凭据写入 `BuildConfig`、资源或源码；
- 只信任系统 CA，不允许“信任所有证书”；
- 不绕过证书校验、验证码、访问控制和限流；
- 演示与测试固定使用虚构数据；
- 发布前进行依赖、备份策略、日志和 WebView 安全审计。
