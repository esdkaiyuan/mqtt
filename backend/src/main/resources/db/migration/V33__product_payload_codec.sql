-- T-55：产品级 Payload Codec（数据解析映射）。
-- 用于把非 Alink 报文映射成 Alink JSON，再进入既有物模型解析链路（属性 / 事件 / 告警 / 规则 / 场景 / 历史）。
-- 纯声明式模板，不支持脚本执行。

ALTER TABLE product
    ADD COLUMN payload_codec TEXT NULL
        COMMENT 'Payload Codec JSON（T-55）；NULL=按原始 Alink 解析' AFTER payload_format;