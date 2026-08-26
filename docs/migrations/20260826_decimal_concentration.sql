-- 将已有数据库的烟雾浓度字段升级为两位小数。
-- 新安装直接执行 docs/schema.sql 即可，不需要再执行本文件。

USE smart_smoke;

ALTER TABLE smoke_data
    MODIFY COLUMN concentration DECIMAL(12,2) NOT NULL
    COMMENT '烟雾浓度(ppm，保留两位小数)';

ALTER TABLE alert_record
    MODIFY COLUMN concentration DECIMAL(12,2) NULL
    COMMENT '触发时的浓度(烟雾告警时，保留两位小数)';
