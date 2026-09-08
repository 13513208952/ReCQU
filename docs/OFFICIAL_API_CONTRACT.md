# 官方接口契约（脱敏验证）

以下路径已在用户本人正常登录、手动完成手机二次认证后，以低频请求验证。本文只记录路径、方法和必要字段，不保存真实响应值、Cookie 或 Token。接口不是学校承诺的公开稳定 API，随时可能变化。

所有业务请求均为 `my.cqu.edu.cn` 同源 HTTPS 请求，并携带官方页面已有的 Bearer 会话。

## 用户与会话

- `GET /authserver/simple-user`
  - 使用：`name`、`code`、`deptName`、`type`
  - 安全：响应中存在名为 `password` 的字段，客户端必须通过白名单解析明确忽略。
- `GET /api/resourceapi/session/cur-active-session`
  - 使用：`data.id/year/term`
- `GET /api/resourceapi/session/list`
  - 使用：`sessionVOList[].id/year/term/beginDate/endDate`
- `GET /api/timetable/time/cur-week`
  - `data` 为以学期标识为键、当前周次字符串为值的对象。

## 课表

- 当前选课课表：`GET /api/enrollment/timetable/student`
- 指定学期课表：
  - `POST /api/timetable/class/timetable/student/my-table-detail?sessionId=<id>`
  - JSON 请求体：仅当前登录者自己的学工号字符串数组。

必要字段包括：`courseCode`、`courseName`、`classNbr`、`classTimetableInstrVOList[].instructorName`、`weekDay`、`periodFormat`、`teachingWeekFormat`、`roomBuildingCampusName`、`roomName`。

## 成绩与排名

- `GET /api/sam/score/student/score`
  - `data` 是以学期名称为键的对象；课程位于 `stuScoreHomePgVoS`。
  - 必要字段：`courseName`、`courseCode`、`courseCredit`、`courseNature`、`effectiveScoreShow`、`sessionName`、`instructorName`。
- `GET /api/sam/score/student/studentGpaRanking`
  - 字段：`gpa`、`weightedAvg`、`classRanking`、`majorRanking`、`gradeRanking`。

## 考试

- `GET /api/exam/examTask/get-student-exam-tab-list?studentId=<encrypted>`
- 参数使用官方前端当前实现的 `AES-128-ECB/PKCS#7` 大写十六进制格式。
- 验证账号当前没有考试，因此只确认了响应外层：`status`、`ok`、`msg`、`data[]`；考试项继续兼容上游公开模型并采用容错解析。

## 未接入：课程历史成绩分布

旧版所称“教师评教分数”实为多个用户成绩汇总后的课程历史均分和分布，不是官方教学评价。个人成绩接口不能用于枚举或抓取他人数据。若未来恢复该功能，需要单独的知情同意、去标识、最小样本量、删除机制和合规审查。
