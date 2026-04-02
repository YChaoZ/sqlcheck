# SQL/Apollo配置检查工具

用于检查SQL脚本和Apollo配置的自动化工具，支持重复执行兼容性检查、语法检查、最佳实践建议，并在全部检查通过后自动聚合输出脚本。

---

## 快速开始

### 命令行运行

```bash
mvn spring-boot:run
```

### IDEA中运行

右键 `SqlCheckApplication.java` → Run 'SqlCheckApplication.main()'

---

## 配置说明

配置文件位于 `src/main/resources/application.yml`：

```yaml
sqlcheck:
  sql-dir: sql                    # SQL文件输入目录（报告和聚合目录自动建在同级父目录下）
  apollo-dir: apollo              # Apollo配置文件目录
  sql-file-name-pattern: '^(?<database>[a-zA-Z0-9_]+)_(?<type>ddl|dml)_(?<submitter>[a-zA-Z0-9_]+)\.sql$'
  aggregation-skip-prefixes: []   # 跳过聚合的文件名前缀，例如: ["tmp_", "skip_"]
  database-check-enabled: false   # 是否启用数据库前缀检查
  database-name: ""               # 启用数据库前缀检查时必填
```

也可通过命令行参数覆盖：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--sqlcheck.sql-dir=D:/projects/sql"
```

**Windows路径**：使用正斜杠 `D:/projects/sql` 或双反斜杠 `D:\\\\projects\\\\sql`。

---

## 输出目录

| 目录 | 路径 | 说明 |
|------|------|------|
| 报告 | `<sql-dir的父目录>/report/` | HTML + JSON 报告 |
| 聚合 | `<sql-dir的父目录>/aggregation/` | 合并后的SQL脚本 + apollo目录 |

两个目录在每次运行时**自动清空重建**。

---

## SQL文件命名规范

文件名必须满足：`<数据库名>_<ddl|dml>_<提交人>.sql`

例如：`biz_ddl_alice.sql`、`finance_dml_bob.sql`

- **DDL文件**（`_ddl_`）：只允许 `CREATE / ALTER / DROP / RENAME / TRUNCATE`
- **DML文件**（`_dml_`）：只允许 `INSERT / REPLACE / UPDATE / DELETE`

文件名不符合规范报 `WARNING`，文件内语句类型与文件名不符报 `TYPE_MISMATCH ERROR`。

---

## SQL检查规则

### 1. 文件名检查

| 检查项 | 说明 | 级别 |
|--------|------|------|
| 文件名格式 | 不符合 `数据库_ddl|dml_提交人.sql` 命名规范 | WARNING |
| 语句类型与文件名不符 | DDL文件含DML语句，或DML文件含DDL语句 | ERROR |
| 文件编码不是UTF-8 | 存在乱码风险 | ERROR |

### 2. 重复执行兼容性

| 检查项 | 说明 | 级别 |
|--------|------|------|
| CREATE TABLE 缺少 IF NOT EXISTS | 重复执行会报错 | WARNING |
| DROP TABLE 缺少 IF EXISTS | 重复执行会报错 | WARNING |
| DROP INDEX 缺少 IF EXISTS | 重复执行会报错 | WARNING |
| DROP DATABASE 缺少 IF EXISTS | 重复执行会报错 | WARNING |
| ALTER TABLE 缺少 IF EXISTS | 重复执行可能失败 | WARNING |
| TRUNCATE TABLE | 危险操作，数据无法恢复 | WARNING |
| INSERT 无对应 DELETE | 建议添加DELETE或使用 INSERT IGNORE / REPLACE INTO / ON DUPLICATE KEY UPDATE | WARNING |

### 3. 语法错误

| 检查项 | 说明 | 级别 |
|--------|------|------|
| FLOAT/DOUBLE DEFAULT NULL | 浮点类型不建议 DEFAULT NULL，建议 DEFAULT 0 | ERROR |
| DECIMAL DEFAULT NULL | 建议使用 DEFAULT 0 | WARNING |
| VARCHAR(0) / CHAR(0) | 长度不能为0 | ERROR |
| INT(0) | 显示宽度不能为0 | ERROR |
| VARCHAR长度 > 21845 | 可能导致行溢出 | ERROR |
| CHAR长度 > 255 | 超出最大限制 | ERROR |
| TEXT/TINYTEXT/MEDIUMTEXT/LONGTEXT DEFAULT | 大文本类型不能有DEFAULT值 | ERROR |
| BLOB/TINYBLOB/MEDIUMBLOB/LONGBLOB DEFAULT | BLOB类型不能有DEFAULT值 | ERROR |
| JSON DEFAULT | JSON类型不能有DEFAULT值 | ERROR |
| GEOMETRY DEFAULT | GEOMETRY类型不能有DEFAULT值 | ERROR |
| INDEX/KEY 索引名为空 | 索引名后括号为空 | ERROR |
| UNIQUE KEY 无名称 | 必须指定索引名 | ERROR |
| FULLTEXT KEY 无名称 | 必须指定索引名 | ERROR |
| SPATIAL KEY 无名称 | 必须指定索引名 | ERROR |
| COMMENT 单引号未闭合 | 语法错误 | ERROR |
| AUTO_INCREMENT=0 | 起始值0，将从0开始递增 | WARNING |
| AUTO_INCREMENT=其他值 | 起始值只能是0或1 | ERROR |
| CHARSET 非 utf8mb4 | 建议使用 utf8mb4 | WARNING |
| ENUM/SET 定义未闭合 | 语法错误 | ERROR |
| ALTER TABLE ADD INDEX 索引列为空 | 缺少列名 | ERROR |
| ALTER TABLE ADD UNIQUE 索引列为空 | 缺少列名 | ERROR |
| ALTER TABLE DROP INDEX/KEY 无索引名 | 需要指定索引名 | ERROR |
| TIMESTAMP DEFAULT CURRENT_TIMESTAMP 未闭合 | 语法错误 | ERROR |
| DATE/TIME/YEAR DEFAULT CURRENT_TIMESTAMP | 这些类型不支持此语法 | ERROR |
| TEXT/BLOB 直接做 KEY/INDEX | 需使用前缀索引 | ERROR |
| FULLTEXT 索引用于非文本列 | 只支持 CHAR/VARCHAR/TEXT | ERROR |
| SPATIAL 索引用于非几何列 | 只支持 GEOMETRY/POINT 等 | ERROR |
| 表名/列名以数字开头 | 语法错误 | ERROR |
| 表名/列名/索引名长度 > 64 | 超出MySQL限制 | ERROR |
| CREATE TABLE AS SELECT | 会丢失列属性、索引、注释 | WARNING |

### 4. 危险操作

| 检查项 | 说明 | 级别 |
|--------|------|------|
| UPDATE 无WHERE条件 | 会更新全表 | ERROR |
| DELETE 无WHERE条件 | 会删除全表 | ERROR |
| UPDATE WHERE 1=1 | 全表更新确认 | WARNING |
| DELETE WHERE 1=1 | 全表删除确认 | WARNING |
| DROP PRIMARY KEY | 危险操作确认 | WARNING |
| DROP UNIQUE KEY | 危险操作确认 | WARNING |

### 5. 最佳实践

| 检查项 | 说明 | 级别 |
|--------|------|------|
| INT AUTO_INCREMENT | 建议改用BIGINT，防止溢出 | WARNING |
| SMALLINT AUTO_INCREMENT | 范围较小，建议评估 | WARNING |
| TINYINT AUTO_INCREMENT | 范围极小，建议评估 | WARNING |
| VARCHAR长度 > 10000 | 建议评估是否改用TEXT | WARNING |
| VARCHAR(255) | 中文场景建议使用更大长度 | INFO |
| CREATE TABLE 未指定 CHARSET | 建议指定 utf8mb4 | INFO |
| CREATE TABLE 未指定 ENGINE | 建议指定 InnoDB | INFO |
| MYISAM存储引擎 | 已废弃，不支持事务和外键 | WARNING |
| MEMORY存储引擎 | 数据不持久化，重启丢失 | WARNING |
| 联合索引5列 | 建议控制在6列以内 | INFO |
| 联合索引超过6列 | 建议拆分或优化 | WARNING |
| VARCHAR 直接做 KEY/INDEX | 建议指定前缀长度 | INFO |
| CHARSET 非 utf8mb4 | 建议使用 utf8mb4 | WARNING |
| ADD COLUMN 无位置 | 建议指定 AFTER/FIRST | INFO |
| MODIFY COLUMN 无位置 | 建议指定 AFTER/FIRST | INFO |
| CHAR(1) | 建议改用 TINYINT 或 ENUM | INFO |
| VARCHAR(1) | 建议改用 CHAR(1) 或 TINYINT/ENUM | INFO |
| SELECT * | 建议指定具体字段 | WARNING |
| LIMIT offset,count | 语法已废弃，建议使用 LIMIT count OFFSET offset | WARNING |
| utf8 字符集 (COLLATE) | 已废弃，请用 utf8mb4 | WARNING |
| ZEROFILL | 已废弃，且自动添加 UNSIGNED | WARNING |
| NO UNSIGNED | 已废弃，请使用 SIGNED | WARNING |
| CREATE INDEX | 建议改用 ALTER TABLE ADD INDEX（可支持 IF NOT EXISTS） | INFO |
| RENAME TABLE | 确保应用代码已更新引用 | INFO |
| 保留字作为表名/列名/索引名 | 建议修改 | WARNING |

### 6. 基础检查

| 检查项 | 说明 | 级别 |
|--------|------|------|
| 中文标点 | 检测到，。；：等中文标点（不检查字符串字面量和注释内容） | ERROR |

### 7. 数据库前缀检查（需启用）

启用 `database-check-enabled: true` 并配置 `database-name` 后生效：

| 检查项 | 说明 | 级别 |
|--------|------|------|
| 表引用未写成 `数据库名.表名` 形式 | 缺少数据库前缀 | ERROR |
| 数据库名与配置不符 | 使用了错误的数据库名 | ERROR |
| USE 切库语句 | 禁止使用 USE 语句 | ERROR |

---

## Apollo配置检查规则

### 1. Apollo Script YAML 格式（.yml / .yaml）

适用于包含 `insert:` / `update:` / `delete:` / `replace:` 顶级操作符的文件。

**文件格式：**
```yaml
insert:
  app.name:
    value: "test-app"
    comment: 应用名称
update:
  app.version:
    value: "1.0.1"
    comment: 更新版本
delete:
  old.key:
```

**结构说明：**
- 层0：操作符（`insert` / `update` / `delete` / `replace`），可重复出现
- 层1：配置项名称直接作为YAML key（如 `app.name:`），空值表示容器节点
- 层2：`value`（配置值）和 `comment`（说明，可选）

| 检查项 | 说明 | 级别 |
|--------|------|------|
| 缺少配置项key | 操作符下没有配置项名称 | ERROR |
| insert/update/replace 缺少 value | 必须提供配置值 | ERROR |
| 重复key | 同一文件中相同配置项名称出现多次 | ERROR |
| key格式不规范 | 应以字母开头，仅含字母、数字、下划线、点、连字符 | WARNING |
| Tab缩进 | YAML禁止使用Tab | ERROR |
| 中文标点 | 检测到中文冒号或其他中文符号 | ERROR |
| 未识别的顶级操作符 | 非 insert/update/delete/replace | WARNING |

### 2. 通用YAML配置文件（.yml / .yaml）

适用于普通Spring配置风格的YAML文件（不含操作符）。

**文件格式：**
```yaml
app:
  id: demo-app
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo
app.name: flat-style-key
```

| 检查项 | 说明 | 级别 |
|--------|------|------|
| Tab缩进 | YAML禁止使用Tab | ERROR |
| 缩进非2的倍数 | 建议使用2空格缩进 | WARNING |
| 缩进跳跃过大 | 前后行缩进差超过4个空格 | WARNING |
| 重复key路径 | 平铺与嵌套写法归一化后指向同一路径 | ERROR |
| key路径父子冲突 | 父路径已定义为值，子路径又重新定义（或反向） | ERROR |
| key命名格式 | 路径段应以字母开头，仅含字母、数字、下划线、连字符 | WARNING |
| 括号不匹配 | 花括号 `{}` 或方括号 `[]` 不匹配 | ERROR |
| 混用单双引号 | 同一行混用单引号和双引号 | WARNING |

> 说明：当前实现针对Apollo常见YAML写法，支持dotted key与嵌套mapping混用。数组、anchor/alias、多文档等高级YAML特性做保守跳过处理，不等同于完整YAML解析器。

### 3. Apollo Script SQL 格式（.sql）

适用于包含 `INSERT KEY:xxx VALUE:yyy` 语法的文件。

**文件格式：**
```
INSERT KEY:app.name VALUE:test-app COMMENT:应用名称;
UPDATE KEY:app.version VALUE:1.0.1;
DELETE KEY:old.key;
```

| 检查项 | 说明 | 级别 |
|--------|------|------|
| 缺少KEY | 语句中没有KEY定义 | ERROR |
| 缺少VALUE | INSERT/UPDATE/REPLACE缺少VALUE | ERROR |
| 重复KEY | 同一文件中相同KEY出现多次 | ERROR |
| KEY格式不规范 | 应以字母开头，仅含字母、数字、下划线、点、连字符 | WARNING |
| 中文冒号 | 使用了中文冒号`：` | ERROR |
| 中文标点 | 检测到其他中文符号 | ERROR |
| 缺少结束分号 | 语句末尾缺少 `;` | ERROR |
| 未识别的语句格式 | 非 INSERT/UPDATE/DELETE/REPLACE 开头 | WARNING |

### 4. JSON格式（.json）

| 检查项 | 说明 | 级别 |
|--------|------|------|
| 重复key | JSON对象中相同key出现多次 | ERROR |
| 混用单双引号 | 同一行混用单引号和双引号 | WARNING |

---

## SQL脚本聚合

SQL检查全部通过（无ERROR）后，自动执行聚合：

- 按 `数据库 + 类型（ddl/dml）+ 子目录` 分组，合并同组文件
- 同一输出文件内按提交人顺序分段，段头写入 `-- submitter: xxx`
- `aggregation-skip-prefixes` 匹配的文件**直接复制**到聚合目录，不参与合并
- `apollo/` 目录整体复制到聚合目录下的 `apollo/` 子目录
- 所有输出文件统一为 **UTF-8 + Unix换行(LF)**

> 聚合仅在**无任何ERROR**时运行，WARNING不阻断聚合。

---

## 报告说明

每次运行生成两种报告（位于 `<sql-dir的父目录>/report/`）：

- **report.html**：便于人工查看，显示文件、行号、问题描述
- **report.json**：便于程序解析，可集成CI/CD

### 严重级别

| 级别 | 含义 |
|------|------|
| ERROR | 必须修复，会阻断聚合 |
| WARNING | 建议修复，不阻断聚合 |
| INFO | 最佳实践建议，可选处理 |

---

## 目录结构

```
sqlcheck/
├── pom.xml
├── src/main/
│   ├── java/sqlcheck/
│   │   ├── SqlCheckApplication.java       # 启动类
│   │   ├── config/SqlCheckProperties.java # 配置属性
│   │   ├── runner/SqlCheckRunner.java     # 启动执行器
│   │   ├── service/SqlCheckService.java   # 主流程编排
│   │   ├── checker/
│   │   │   ├── SqlChecker.java            # SQL检查器
│   │   │   └── ApolloChecker.java         # Apollo检查器
│   │   ├── aggregate/SqlScriptAggregator.java  # 脚本聚合器
│   │   ├── model/CheckResult.java         # 结果模型
│   │   └── report/ReportGenerator.java    # 报告生成器
│   └── resources/application.yml          # 默认配置
├── sql/                                   # SQL文件输入目录
├── apollo/                                # Apollo配置输入目录
├── report/                                # 报告输出目录（自动生成）
└── aggregation/                           # 聚合脚本输出目录（自动生成）
```

---

## 环境要求

- JDK 1.8+
- Maven 3.x
- Spring Boot 2.4.11

## License

MIT
