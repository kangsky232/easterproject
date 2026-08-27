package com.smoke.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smoke.entity.DingTalkRecipient;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DingTalkRecipientMapper extends BaseMapper<DingTalkRecipient> {

    @Insert("""
            INSERT INTO dingtalk_recipient
                (user_id, display_name, enabled, first_seen_at, last_seen_at, created_at, updated_at)
            VALUES
                (#{userId}, #{displayName}, 1, NOW(), NOW(), NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                display_name = VALUES(display_name),
                enabled = 1,
                last_seen_at = NOW(),
                updated_at = NOW()
            """)
    int upsertActiveRecipient(@Param("userId") String userId,
                              @Param("displayName") String displayName);
}
