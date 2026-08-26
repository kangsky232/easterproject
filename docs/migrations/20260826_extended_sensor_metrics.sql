-- 为已有数据库增加华为云 IoTDA 上报的扩展传感器字段。
-- 新安装直接执行 docs/schema.sql，不需要再执行本文件。

USE smart_smoke;

ALTER TABLE smoke_data
    ADD COLUMN temperature DECIMAL(12,2) NULL COMMENT '环境温度' AFTER concentration,
    ADD COLUMN humidity DECIMAL(12,2) NULL COMMENT '环境湿度' AFTER temperature,
    ADD COLUMN current_value DECIMAL(12,2) NULL COMMENT '设备电流' AFTER humidity,
    ADD COLUMN wire_temperature DECIMAL(12,2) NULL COMMENT '线缆温度' AFTER current_value,
    ADD COLUMN co_value DECIMAL(12,2) NULL COMMENT '一氧化碳值' AFTER wire_temperature,
    ADD COLUMN beep_status VARCHAR(16) NULL COMMENT '蜂鸣器状态' AFTER co_value;
