package com.smoke.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smoke.dto.CreateHazardRequest;
import com.smoke.dto.HazardDetailResponse;
import com.smoke.dto.HazardSummaryResponse;
import com.smoke.dto.PageResponse;
import com.smoke.entity.HazardAction;
import com.smoke.entity.HazardTicket;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.HazardActionMapper;
import com.smoke.mapper.HazardTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HazardService {

    private static final Set<String> VALID_STATUSES = Set.of(
            HazardTicket.STATUS_REPORTED,
            HazardTicket.STATUS_PROCESSING,
            HazardTicket.STATUS_PENDING_REVIEW,
            HazardTicket.STATUS_CLOSED);
    private static final Set<String> VALID_PRIORITIES = Set.of(
            HazardTicket.PRIORITY_LOW,
            HazardTicket.PRIORITY_MEDIUM,
            HazardTicket.PRIORITY_HIGH,
            HazardTicket.PRIORITY_URGENT);
    private static final Set<String> OPERATOR_ROLES = Set.of(
            "FIREFIGHTER", "COMMUNITY_ADMIN", "SYSTEM_ADMIN");
    private static final Set<String> REVIEWER_ROLES = Set.of(
            "COMMUNITY_ADMIN", "SYSTEM_ADMIN");

    private final HazardTicketMapper hazardTicketMapper;
    private final HazardActionMapper hazardActionMapper;

    public PageResponse<HazardTicket> list(
            String username, String role, String status, String priority, int page, int pageSize) {
        validatePage(page, pageSize);
        validateOptional(status, VALID_STATUSES, "status 不正确");
        validateOptional(priority, VALID_PRIORITIES, "priority 不正确");
        LambdaQueryWrapper<HazardTicket> query = visibleQuery(username, role)
                .eq(status != null && !status.isBlank(), HazardTicket::getStatus, status)
                .eq(priority != null && !priority.isBlank(), HazardTicket::getPriority, priority)
                .orderByDesc(HazardTicket::getUpdatedAt)
                .orderByDesc(HazardTicket::getCreatedAt);
        Page<HazardTicket> result = hazardTicketMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResponse<>(result.getRecords(), result.getTotal(), page, pageSize);
    }

    public HazardDetailResponse get(Long id, String username, String role) {
        HazardTicket ticket = requireVisibleTicket(id, username, role);
        List<HazardAction> actions = hazardActionMapper.selectList(
                Wrappers.<HazardAction>lambdaQuery()
                        .eq(HazardAction::getTicketId, id)
                        .orderByAsc(HazardAction::getCreatedAt)
                        .orderByAsc(HazardAction::getId));
        return new HazardDetailResponse(ticket, actions);
    }

    public HazardSummaryResponse summary(String username, String role) {
        long reported = count(username, role, HazardTicket.STATUS_REPORTED);
        long processing = count(username, role, HazardTicket.STATUS_PROCESSING);
        long pendingReview = count(username, role, HazardTicket.STATUS_PENDING_REVIEW);
        long closed = count(username, role, HazardTicket.STATUS_CLOSED);
        return new HazardSummaryResponse(
                reported, processing, pendingReview, closed, reported + processing + pendingReview);
    }

    @Transactional
    public HazardTicket create(CreateHazardRequest request, String username) {
        LocalDateTime now = LocalDateTime.now();
        HazardTicket ticket = new HazardTicket();
        ticket.setTicketNo(createTicketNo());
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setLocation(request.location().trim());
        ticket.setPriority(request.priority());
        ticket.setStatus(HazardTicket.STATUS_REPORTED);
        ticket.setReporterUsername(username);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        hazardTicketMapper.insert(ticket);
        createAction(ticket.getId(), HazardAction.TYPE_REPORTED, username, "上报隐患：" + ticket.getTitle());
        return ticket;
    }

    @Transactional
    public HazardTicket claim(Long id, String username, String role) {
        requireOperator(role);
        HazardTicket ticket = requireTicket(id);
        requireStatus(ticket, HazardTicket.STATUS_REPORTED, "只有待接单隐患可以接单");
        ticket.setStatus(HazardTicket.STATUS_PROCESSING);
        ticket.setAssigneeUsername(username);
        ticket.setUpdatedAt(LocalDateTime.now());
        hazardTicketMapper.updateById(ticket);
        createAction(ticket.getId(), HazardAction.TYPE_CLAIMED, username, "已接单，开始整改");
        return ticket;
    }

    @Transactional
    public HazardTicket submitResolution(
            Long id, String resolution, String username, String role) {
        requireOperator(role);
        HazardTicket ticket = requireTicket(id);
        requireStatus(ticket, HazardTicket.STATUS_PROCESSING, "只有整改中的隐患可以提交复核");
        if (!username.equals(ticket.getAssigneeUsername()) && !isReviewer(role)) {
            throw new BusinessException(403, "只能由当前接单人或管理员提交整改结果");
        }
        String trimmed = resolution == null ? "" : resolution.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(400, "整改结果不能为空");
        }
        ticket.setStatus(HazardTicket.STATUS_PENDING_REVIEW);
        ticket.setResolution(trimmed);
        ticket.setUpdatedAt(LocalDateTime.now());
        hazardTicketMapper.updateById(ticket);
        createAction(ticket.getId(), HazardAction.TYPE_SUBMITTED, username, trimmed);
        return ticket;
    }

    @Transactional
    public HazardTicket review(
            Long id, boolean approved, String remark, String username, String role) {
        if (!isReviewer(role)) {
            throw new BusinessException(403, "当前账号没有隐患复核权限");
        }
        HazardTicket ticket = requireTicket(id);
        requireStatus(ticket, HazardTicket.STATUS_PENDING_REVIEW, "只有待复核隐患可以执行复核");
        String trimmed = remark == null ? "" : remark.trim();
        LocalDateTime now = LocalDateTime.now();
        ticket.setReviewerUsername(username);
        ticket.setUpdatedAt(now);
        if (approved) {
            ticket.setStatus(HazardTicket.STATUS_CLOSED);
            ticket.setClosedAt(now);
            hazardTicketMapper.updateById(ticket);
            createAction(ticket.getId(), HazardAction.TYPE_APPROVED, username,
                    trimmed.isBlank() ? "复核通过，隐患已闭环" : trimmed);
        } else {
            if (trimmed.isBlank()) {
                throw new BusinessException(400, "驳回复核时必须填写原因");
            }
            ticket.setStatus(HazardTicket.STATUS_PROCESSING);
            ticket.setResolution(null);
            ticket.setClosedAt(null);
            hazardTicketMapper.updateById(ticket);
            createAction(ticket.getId(), HazardAction.TYPE_REJECTED, username, trimmed);
        }
        return ticket;
    }

    private long count(String username, String role, String status) {
        return hazardTicketMapper.selectCount(
                visibleQuery(username, role).eq(HazardTicket::getStatus, status));
    }

    private LambdaQueryWrapper<HazardTicket> visibleQuery(String username, String role) {
        LambdaQueryWrapper<HazardTicket> query = Wrappers.lambdaQuery();
        if (!hasFullVisibility(role)) {
            query.eq(HazardTicket::getReporterUsername, username);
        }
        return query;
    }

    private HazardTicket requireVisibleTicket(Long id, String username, String role) {
        HazardTicket ticket = requireTicket(id);
        if (!hasFullVisibility(role) && !username.equals(ticket.getReporterUsername())) {
            throw new BusinessException(404, "隐患工单不存在");
        }
        return ticket;
    }

    private HazardTicket requireTicket(Long id) {
        HazardTicket ticket = hazardTicketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(404, "隐患工单不存在");
        }
        return ticket;
    }

    private void createAction(Long ticketId, String type, String operator, String remark) {
        HazardAction action = new HazardAction();
        action.setTicketId(ticketId);
        action.setActionType(type);
        action.setOperatorName(operator);
        action.setRemark(remark);
        action.setCreatedAt(LocalDateTime.now());
        hazardActionMapper.insert(action);
    }

    private void requireOperator(String role) {
        if (!OPERATOR_ROLES.contains(role)) {
            throw new BusinessException(403, "当前账号没有隐患整改权限");
        }
    }

    private boolean isReviewer(String role) {
        return REVIEWER_ROLES.contains(role);
    }

    private boolean hasFullVisibility(String role) {
        return OPERATOR_ROLES.contains(role);
    }

    private void requireStatus(HazardTicket ticket, String expected, String message) {
        if (!expected.equals(ticket.getStatus())) {
            throw new BusinessException(409, message);
        }
    }

    private void validateOptional(String value, Set<String> allowed, String message) {
        if (value != null && !value.isBlank() && !allowed.contains(value)) {
            throw new BusinessException(400, message);
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(400, "page 必须大于 0，pageSize 必须在 1 到 200 之间");
        }
    }

    private String createTicketNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "YH-" + date + "-" + suffix;
    }
}
