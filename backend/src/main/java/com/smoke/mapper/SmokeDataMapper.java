package com.smoke.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smoke.entity.SmokeData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SmokeDataMapper extends BaseMapper<SmokeData> {

    @Select("""
            <script>
            SELECT ranked.id, ranked.device_id, ranked.message_id, ranked.concentration,
                   ranked.temperature, ranked.humidity, ranked.current_value, ranked.wire_temperature,
                   ranked.co_value, ranked.beep_status, ranked.timestamp, ranked.created_at
            FROM (
                SELECT s.*, ROW_NUMBER() OVER (
                    PARTITION BY s.device_id ORDER BY s.timestamp DESC, s.id DESC
                ) AS latest_rank
                FROM smoke_data s
                WHERE s.device_id IN
                <foreach collection='deviceIds' item='deviceId' open='(' separator=',' close=')'>
                    #{deviceId}
                </foreach>
            ) ranked
            WHERE ranked.latest_rank = 1
            </script>
            """)
    List<SmokeData> selectLatestByDeviceIds(@Param("deviceIds") List<String> deviceIds);
}
