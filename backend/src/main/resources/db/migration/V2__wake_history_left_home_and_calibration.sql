-- V2：行为学习第一期
-- 1) wake_history 记录"出门"时刻（USER_LEFT_HOME 事件写入，本期只积累数据不参与决策）
ALTER TABLE wake_history
    ADD COLUMN left_home_at DATETIME(3) NULL AFTER dismissed_at;
