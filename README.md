# SQL/Apollo配置检查工具

用于检查SQL脚本和Apollo配置的自动化工具，支持重复执行兼容性检查、语法检查、最佳实践建议等。

## 功能特性

### SQL检查

#### 1. 重复执行兼容性
| 检查项 | 说明 | 级别 |
|--------|------|------|
| CREATE TABLE | 缺少 IF NOT EXISTS | WARNING |
| DROP TABLE | 缺少 IF EXISTS | WARNING |
| DROP INDEX | 缺少 IF EXISTS | WARNING |
| DROP DATABASE | 缺少 IF EXISTS | WARNING |
| ALTER TABLE | 缺少 IF EXISTS | WARNING |
| INSERT语句 | 无DELETE配合或未使用INSERT IGNORE/REPLACE/ON DUPLICATE KEY UPDATE | WARNING |

#### 2. 语法错误
| 检查项 | 说明 | 级别 |
|--------|------|------|
| FLOAT/DOUBLE/DECIMAL DEFAULT NULL | 浮点类型不建议DEFAULT NULL | ERROR |
| VARCHAR/CHAR长度=0 | 长度不能为0 | ERROR |
| VARCHAR长度>21845 | 长度过大会导致问题 | ERROR |
| TEXT/BLOB/JSON/GEOMETRY DEFAULT | 这些类型不能有DEFAULT值 | ERROR |
| INDEX/KEY空括号 | 索引名后不能为空 | ERROR |
| UNIQUE/FULLTEXT/SPATIAL KEY无名称 | 必须指定索引名 | ERROR |
| COMMENT未闭合 | 单引号未正确闭合 | ERROR |
| AUTO_INCREMENT=0 | 起始值不能为0 | ERROR |
| ENUM/SET定义未闭合 | 语法错误 | ERROR |
| CREATE TABLE AS SELECT | 会丢失列属性、索引、注释 | WARNING |
| DATE/TIME/YEAR DEFAULT CURRENT_TIMESTAMP | 这些类型不支持此语法 | ERROR |
| TEXT/BLOB直接做KEY/INDEX | 不支持，需使用前缀索引 | ERROR |

#### 3. 危险操作
| 检查项 | 说明 | 级别 |
|--------|------|------|
| DROP PRIMARY KEY | 危险操作确认 | WARNING |
| DROP UNIQUE KEY | 危险操作确认 | WARNING |
| TRUNCATE TABLE | 危险操作确认 | WARNING |
| UPDATE无WHERE条件 | 会更新全表 | ERROR |
| DELETE无WHERE条件 | 会删除全表 | ERROR |
| UPDATE/DELETE WHERE 1=1 | 需确认是否需要全表操作 | WARNING |

#### 4. 最佳实践
| 检查项 | 说明 | 级别 |
|--------|------|------|
| INT AUTO_INCREMENT | 建议改用BIGINT | WARNING |
| 未指定CHARSET | 建议utf8mb4 | INFO |
| 未指定ENGINE | 建议InnoDB | INFO |
| MYISAM存储引擎 | 已废弃，建议InnoDB | WARNING |
| MEMORY存储引擎 | 数据不持久化 | WARNING |
| VARCHAR直接做KEY | 建议指定前缀长度 | INFO |
| 联合索引超过6列 | 建议拆分 | WARNING |
| ADD/MODIFY COLUMN | 建议指定位置(AFTER/FIRST) | INFO |
| LIMIT offset,count | 语法已废弃 | WARNING |
| SELECT * | 建议指定具体字段 | WARNING |
| CHAR(1)/VARCHAR(1) | 建议改用ENUM | INFO |
| utf8字符集 | 已废弃，用utf8mb4 | WARNING |
| ZEROFILL | 已废弃 | WARNING |
| 保留字作为列名/表名 | 建议修改 | WARNING |
| 列名/表名长度>64 | 超长会报错 | ERROR |
| 列名/表名以数字开头 | 语法错误 | ERROR |

#### 5. 基础检查
| 检查项 | 说明 | 级别 |
|--------|------|------|
| 中文符号 | 检测到，。；：等中文标点（仅检查可执行 SQL，不检查字符串字面量与注释） | ERROR |
| 结束分号 | 语句缺少分号 | ERROR |

#### 6. 本次 SQL 检查优化点

- **忽略单引号字符串内容**：`INSERT` / `UPDATE` / `WHERE` 等语句中，`'中文'`、`'你好，世界'`、`'it''s 中文，ok'` 这类字符串字面量不再触发中文符号误报。
- **忽略注释内容参与校验**：合法的 `-- ...`、`# ...`、`/* ... */` 注释不会再触发中文符号检查，也不会影响其他正则规则判断。
- **分句更稳健**：只有位于字符串和注释之外的 `;` 才会作为语句结束符，避免 `'a;b'` 或 `/* ... ; ... */` 被错误拆分成多条 SQL。
- **标识符长度校验更准确**：64 字符长度规则只作用于真实的表名、列名、索引名；长度 **等于 64** 允许，**大于 64** 才报错。
- **问题定位保持原始 SQL 上下文**：报错行号优先定位到真实违规行，`sourceLineText` 仍返回原始 SQL 行文本，便于排查。

### Apollo配置检查

#### 1. Apollo自动化脚本检查 (.sql格式)
支持格式：
```
INSERT KEY:xxx VALUE:yyy COMMENT:zzz;
UPDATE KEY:xxx VALUE:yyy;
DELETE KEY:xxx;
```

| 检查项 | 说明 | 级别 |
|--------|------|------|
| 重复KEY | 同一KEY被多次INSERT | ERROR |
| 缺少KEY | 语句中没有KEY定义 | ERROR |
| 缺少VALUE | INSERT/UPDATE缺少VALUE | ERROR |
| 中文冒号 | 使用中文冒号：代替英文冒号: | ERROR |
| 缺少分号 | 语句缺少结束分号 | ERROR |
| KEY格式 | KEY格式不规范 | WARNING |

#### 2. JSON检查
| 检查项 | 说明 | 级别 |
|--------|------|------|
| 重复key | JSON中发现重复的key | ERROR |
| 语法错误 | JSON格式错误 | ERROR |
| 花括号不匹配 | { 和 } 数量不一致 | ERROR |
| 方括号不匹配 | [ 和 ] 数量不一致 | ERROR |

#### 3. YAML检查
支持常见 Apollo YAML 配置模式：
```yaml
app.id: demo
app:
  id: demo
spring:
  datasource.url: jdbc:mysql://localhost/test
```

| 检查项 | 说明 | 级别 |
|--------|------|------|
| 缩进错误 | 非2的倍数空格缩进 | WARNING |
| 缩进跳跃过大 | 缩进变化异常 | WARNING |
| Tab缩进 | YAML禁止使用Tab | ERROR |
| 括号不匹配 | 花括号/方括号不匹配 | ERROR |
| 重复key路径 | 平铺/嵌套写法归一化后指向同一路径 | ERROR |
| key路径冲突 | 父路径已是值、子路径再次定义，或反向冲突 | ERROR |
| key命名格式 | 按路径段检查起始字符与非法字符 | WARNING |
| 语法错误 | YAML格式错误 | ERROR |

说明：当前实现面向 Apollo 常见 YAML 写法，支持 dotted key 与嵌套 mapping 的混用。对数组、anchor/alias、多文档、merge key、复杂 block scalar 等高级 YAML 特性仅做保守跳过或 best-effort 处理，不等同于完整 YAML parser。若同一路径既被定义为容器又被定义为普通值，优先报告 `KEY_PATH_CONFLICT`，避免与重复 key 告警双重报错。

#### 4. 通用检查
| 检查项 | 说明 | 级别 |
|--------|------|------|
| 混用引号 | 单引号和双引号混用 | WARNING |
| 占位符未闭合 | {{ 或 }} 未配对 | ERROR |

---

## 快速开始

### IDEA中运行

1. 在IDEA中打开项目
2. 右键 `SqlCheckApplication.java` → Run 'SqlCheckApplication.main()'

### 命令行运行

```bash
mvn spring-boot:run
```

---

## 配置方式

配置文件位于 `src/main/resources/application.yml`

### 方式一：配置文件（推荐）

编辑 `application.yml`：

```yaml
sqlcheck:
  sql-dir: D:/IdeaProjects/sql
  apollo-dir: D:/IdeaProjects/apollo
  output-dir: D:/IdeaProjects/output
```

**Windows路径注意**：
- 使用正斜杠 `/` 或双反斜杠 `\\`
- 推荐使用正斜杠：`D:/IdeaProjects/sql`

### 方式二：命令行参数

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--sqlcheck.sql-dir=D:/IdeaProjects/sql"
```

### 方式三：JVM参数

```bash
mvn spring-boot:run -Dsqlcheck.sql-dir=D:/IdeaProjects/sql
```

**优先级**：命令行参数 > JVM参数 > 配置文件

---

## 常见问题

### 1. 配置不生效

**问题**：修改了 `application.yml` 但检查的目录没变

**解决方法**：
1. IDEA中：右键项目 → Maven → Reload Project
2. 命令行：运行 `mvn clean spring-boot:run`
3. 检查是否在 Run Configuration 中覆盖了配置

### 2. Windows路径格式

**问题**：Windows路径 `D:\IdeaProjects\sql` 无法识别

**解决方法**：
- 改用正斜杠：`D:/IdeaProjects/sql`
- 或双反斜杠：`D:\\IdeaProjects\\sql`

### 3. 路径不存在

**问题**：日志显示目录不存在

**解决方法**：
- 检查路径是否正确
- 使用绝对路径测试

### 4. IDEA缓存问题

**问题**：修改代码后运行结果没变化

**解决方法**：
1. Maven → Reload Project
2. 或 `mvn clean` 清理后重新运行

---

## 报告说明

### 严重级别

- **ERROR**: 语法错误或严重问题，必须修复
- **WARNING**: 危险操作或潜在问题，建议修复
- **INFO**: 最佳实践建议，可选修复

### 报告格式

- **HTML报告** (`report.html`): 便于人工查看，显示文件路径、行号、错误详情
- **JSON报告** (`report.json`): 便于程序解析，集成CI/CD

---

## 目录结构

```
sqlcheck/
├── pom.xml
├── src/main/
│   ├── java/
│   │   └── sqlcheck/
│   │   │   ├── SqlCheckApplication.java    # 启动类
│   │   │   ├── config/
│   │   │   │   └── SqlCheckProperties.java # 配置属性
│   │   │   ├── runner/
│   │   │   │   └── SqlCheckRunner.java    # 执行器
│   │   │   ├── service/
│   │   │   │   └── SqlCheckService.java   # 业务逻辑
│   │   │   ├── checker/
│   │   │   │   ├── SqlChecker.java        # SQL检查器
│   │   │   │   └── ApolloChecker.java     # Apollo检查器
│   │   │   ├── model/
│   │   │   │   └── CheckResult.java      # 结果模型
│   │   │   └── report/
│   │   │       └── ReportGenerator.java   # 报告生成
│   │   └── resources/
│   │       └── application.yml            # 配置文件
├── sql/                                  # SQL文件目录
├── apollo/                               # Apollo配置目录
└── output/                               # 报告输出目录
```

---

## 环境要求

- JDK 1.8+
- Maven 3.x
- Spring Boot 2.4.11

## License

MIT
