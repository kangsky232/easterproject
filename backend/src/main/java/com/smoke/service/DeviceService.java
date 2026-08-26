package com.smoke.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smoke.dto.BindDeviceRequest;
import com.smoke.dto.CurrentReadingResponse;
import com.smoke.dto.DeviceCredentialResponse;
import com.smoke.dto.DeviceSummaryResponse;
import com.smoke.dto.PageResponse;
import com.smoke.dto.TrendPointResponse;
import com.smoke.dto.UpdateDeviceRequest;
import com.smoke.entity.Device;
import com.smoke.entity.SmokeData;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.DeviceMapper;
import com.smoke.mapper.SmokeDataMapper;
import com.smoke.security.DeviceCredentialCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final SmokeDataMapper smokeDataMapper;
    private final AlertService alertService;

    public PageResponse<DeviceSummaryResponse> list(
            String keyword, Integer status, int page, int pageSize) {
        validatePage(page, pageSize);
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(400, "status 只能是 0 或 1");
        }

        LambdaQueryWrapper<Device> query = Wrappers.<Device>lambdaQuery()
                .eq(Device::getBound, 1)
                .eq(status != null, Device::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), nested -> nested
                        .like(Device::getDeviceId, keyword.trim())
                        .or().like(Device::getDeviceName, keyword.trim())
                        .or().like(Device::getLocation, keyword.trim()))
                .orderByAsc(Device::getId);
        Page<Device> result = deviceMapper.selectPage(new Page<>(page, pageSize), query);
        Map<String, SmokeData> latestReadings = latestReadings(result.getRecords());
        List<DeviceSummaryResponse> records = result.getRecords().stream()
                .map(device -> toSummary(device, latestReadings.get(device.getDeviceId())))
                .toList();
        return new PageResponse<>(records, result.getTotal(), page, pageSize);
    }

    public DeviceSummaryResponse get(Long id) {
        Device device = requireDevice(id);
        SmokeData latest = latestReading(device.getDeviceId());
        return toSummary(device, latest);
    }

    @Transactional
    public Device bind(BindDeviceRequest request) {
        Device existing = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceId, request.deviceId()));
        if (existing != null && Integer.valueOf(1).equals(existing.getBound())) {
            throw new BusinessException(409, "设备编号已绑定");
        }

        Device device = existing == null ? new Device() : existing;
        device.setDeviceId(request.deviceId());
        device.setDeviceName(request.deviceName());
        device.setLocation(request.location());
        device.setStatus(0);
        device.setBound(1);
        device.setUnbindTime(null);
        String accessToken = DeviceCredentialCodec.generate();
        device.setDeviceTokenHash(DeviceCredentialCodec.hash(accessToken));
        if (device.getSmokeThreshold() == null) {
            device.setSmokeThreshold(2000);
        }
        device.setBindTime(LocalDateTime.now());
        if (existing == null) {
            try {
                deviceMapper.insert(device);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(409, "设备编号已绑定");
            }
        } else {
            int updated = deviceMapper.update(null, Wrappers.<Device>lambdaUpdate()
                    .set(Device::getDeviceName, device.getDeviceName())
                    .set(Device::getLocation, device.getLocation())
                    .set(Device::getStatus, 0)
                    .set(Device::getBound, 1)
                    .set(Device::getUnbindTime, null)
                    .set(Device::getSmokeThreshold, device.getSmokeThreshold())
                    .set(Device::getDeviceTokenHash, device.getDeviceTokenHash())
                    .set(Device::getBindTime, device.getBindTime())
                    .eq(Device::getId, device.getId())
                    .eq(Device::getBound, 0));
            if (updated == 0) {
                throw new BusinessException(409, "设备编号已绑定");
            }
        }
        device.setDeviceAccessToken(accessToken);
        return device;
    }

    @Transactional
    public DeviceCredentialResponse rotateAccessToken(Long id) {
        Device device = requireDevice(id);
        String accessToken = DeviceCredentialCodec.generate();
        device.setDeviceTokenHash(DeviceCredentialCodec.hash(accessToken));
        deviceMapper.updateById(device);
        return new DeviceCredentialResponse(device.getDeviceId(), accessToken);
    }

    @Transactional
    public void unbind(Long id) {
        Device device = requireDevice(id);
        device.setBound(0);
        device.setStatus(0);
        device.setUnbindTime(LocalDateTime.now());
        deviceMapper.updateById(device);
        alertService.resolveDeviceAlerts(device.getDeviceId(), "SYSTEM_UNBIND");
    }

    public CurrentReadingResponse current(Long id) {
        Device device = requireDevice(id);
        SmokeData latest = smokeDataMapper.selectOne(Wrappers.<SmokeData>lambdaQuery()
                .eq(SmokeData::getDeviceId, device.getDeviceId())
                .orderByDesc(SmokeData::getTimestamp)
                .last("LIMIT 1"));

        return new CurrentReadingResponse(
                device.getId(),
                device.getDeviceId(),
                device.getDeviceName(),
                latest == null ? null : latest.getConcentration(),
                latest == null ? null : latest.getTimestamp(),
                device.getSmokeThreshold(),
                Integer.valueOf(1).equals(device.getStatus()));
    }

    public List<SmokeData> history(Long id, LocalDateTime start, LocalDateTime end, int limit) {
        Device device = requireDevice(id);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(400, "开始时间不能晚于结束时间");
        }
        if (limit < 1 || limit > 1000) {
            throw new BusinessException(400, "limit 必须在 1 到 1000 之间");
        }

        var query = Wrappers.<SmokeData>lambdaQuery()
                .eq(SmokeData::getDeviceId, device.getDeviceId())
                .ge(start != null, SmokeData::getTimestamp, start)
                .le(end != null, SmokeData::getTimestamp, end)
                .orderByDesc(SmokeData::getTimestamp)
                .last("LIMIT " + limit);
        return smokeDataMapper.selectList(query);
    }

    public List<TrendPointResponse> trend(
            Long id, LocalDateTime start, LocalDateTime end, int bucketMinutes) {
        Device device = requireDevice(id);
        LocalDateTime effectiveEnd = end == null ? LocalDateTime.now() : end;
        LocalDateTime effectiveStart = start == null ? effectiveEnd.minusHours(24) : start;
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new BusinessException(400, "开始时间不能晚于结束时间");
        }
        if (ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) > 31) {
            throw new BusinessException(400, "趋势查询时间范围不能超过 31 天");
        }
        if (bucketMinutes < 1 || bucketMinutes > 1440) {
            throw new BusinessException(400, "bucketMinutes 必须在 1 到 1440 之间");
        }
        long expectedBuckets = ChronoUnit.MINUTES.between(effectiveStart, effectiveEnd) / bucketMinutes + 1;
        if (expectedBuckets > 2000) {
            throw new BusinessException(400, "趋势数据点不能超过 2000 个，请增大 bucketMinutes");
        }

        List<SmokeData> readings = smokeDataMapper.selectList(Wrappers.<SmokeData>lambdaQuery()
                .eq(SmokeData::getDeviceId, device.getDeviceId())
                .ge(SmokeData::getTimestamp, effectiveStart)
                .le(SmokeData::getTimestamp, effectiveEnd)
                .orderByAsc(SmokeData::getTimestamp)
                .last("LIMIT 100001"));
        if (readings.size() > 100000) {
            throw new BusinessException(400, "趋势范围内数据过多，请缩小查询时间范围");
        }

        LocalDateTime origin = LocalDateTime.of(1970, 1, 1, 0, 0);
        Map<LocalDateTime, TrendAccumulator> buckets = new TreeMap<>();
        for (SmokeData reading : readings) {
            long elapsedMinutes = ChronoUnit.MINUTES.between(origin, reading.getTimestamp());
            LocalDateTime bucketStart = origin.plusMinutes((elapsedMinutes / bucketMinutes) * bucketMinutes);
            buckets.computeIfAbsent(bucketStart, ignored -> new TrendAccumulator())
                    .add(reading.getConcentration());
        }
        return buckets.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();
    }

    @Transactional
    public Device updateThreshold(Long id, Integer threshold) {
        Device device = requireDevice(id);
        device.setSmokeThreshold(threshold);
        deviceMapper.updateById(device);
        return device;
    }

    @Transactional
    public DeviceSummaryResponse update(Long id, UpdateDeviceRequest request) {
        Device device = requireDevice(id);
        device.setDeviceName(request.deviceName().trim());
        device.setLocation(request.location().trim());
        deviceMapper.updateById(device);
        return toSummary(device, latestReading(device.getDeviceId()));
    }

    private Map<String, SmokeData> latestReadings(List<Device> devices) {
        if (devices.isEmpty()) {
            return Map.of();
        }
        List<String> deviceIds = devices.stream().map(Device::getDeviceId).toList();
        Map<String, SmokeData> readings = new HashMap<>();
        for (SmokeData reading : smokeDataMapper.selectLatestByDeviceIds(deviceIds)) {
            readings.put(reading.getDeviceId(), reading);
        }
        return readings;
    }

    private SmokeData latestReading(String deviceId) {
        return smokeDataMapper.selectOne(Wrappers.<SmokeData>lambdaQuery()
                .eq(SmokeData::getDeviceId, deviceId)
                .orderByDesc(SmokeData::getTimestamp)
                .orderByDesc(SmokeData::getId)
                .last("LIMIT 1"));
    }

    private DeviceSummaryResponse toSummary(Device device, SmokeData latest) {
        return new DeviceSummaryResponse(
                device.getId(),
                device.getDeviceId(),
                device.getDeviceName(),
                device.getLocation(),
                Integer.valueOf(1).equals(device.getStatus()),
                device.getSmokeThreshold(),
                device.getBattery(),
                device.getLastHeartbeat(),
                latest == null ? null : latest.getConcentration(),
                latest == null ? null : latest.getTimestamp());
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(400, "page 必须大于 0，pageSize 必须在 1 到 200 之间");
        }
    }

    private Device requireDevice(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null || !Integer.valueOf(1).equals(device.getBound())) {
            throw new BusinessException(404, "设备不存在或已解绑");
        }
        return device;
    }

    private static final class TrendAccumulator {
        private BigDecimal sum = BigDecimal.ZERO;
        private BigDecimal minimum;
        private BigDecimal maximum;
        private long samples;

        private void add(BigDecimal value) {
            sum = sum.add(value);
            minimum = minimum == null || value.compareTo(minimum) < 0 ? value : minimum;
            maximum = maximum == null || value.compareTo(maximum) > 0 ? value : maximum;
            samples++;
        }

        private TrendPointResponse toResponse(LocalDateTime bucketStart) {
            BigDecimal average = sum.divide(BigDecimal.valueOf(samples), 2, RoundingMode.HALF_UP);
            return new TrendPointResponse(bucketStart, average, minimum, maximum, samples);
        }
    }
}
