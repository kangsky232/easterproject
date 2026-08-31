package com.smoke.service;

import com.smoke.dto.CreateHazardRequest;
import com.smoke.entity.HazardAction;
import com.smoke.entity.HazardTicket;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.HazardActionMapper;
import com.smoke.mapper.HazardTicketMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HazardServiceTest {

    @Mock
    private HazardTicketMapper hazardTicketMapper;

    @Mock
    private HazardActionMapper hazardActionMapper;

    @Test
    void createPersistsReportedTicketAndAuditAction() {
        when(hazardTicketMapper.insert(any(HazardTicket.class))).thenAnswer(invocation -> {
            HazardTicket ticket = invocation.getArgument(0);
            ticket.setId(9L);
            return 1;
        });
        HazardService service = service();

        HazardTicket ticket = service.create(
                new CreateHazardRequest("楼道堆物", "消防通道被纸箱占用", "2号楼5层", "HIGH"),
                "resident-a");

        assertEquals(HazardTicket.STATUS_REPORTED, ticket.getStatus());
        assertEquals("resident-a", ticket.getReporterUsername());
        assertTrue(ticket.getTicketNo().startsWith("YH-"));
        ArgumentCaptor<HazardAction> action = ArgumentCaptor.forClass(HazardAction.class);
        verify(hazardActionMapper).insert(action.capture());
        assertEquals(HazardAction.TYPE_REPORTED, action.getValue().getActionType());
        assertEquals(9L, action.getValue().getTicketId());
    }

    @Test
    void firefighterCanClaimReportedTicket() {
        HazardTicket ticket = ticket(HazardTicket.STATUS_REPORTED);
        when(hazardTicketMapper.selectById(3L)).thenReturn(ticket);
        HazardService service = service();

        HazardTicket result = service.claim(3L, "fire-a", "FIREFIGHTER");

        assertEquals(HazardTicket.STATUS_PROCESSING, result.getStatus());
        assertEquals("fire-a", result.getAssigneeUsername());
        verify(hazardTicketMapper).updateById(ticket);
    }

    @Test
    void anotherFirefighterCannotSubmitSomeoneElsesResolution() {
        HazardTicket ticket = ticket(HazardTicket.STATUS_PROCESSING);
        ticket.setAssigneeUsername("fire-a");
        when(hazardTicketMapper.selectById(3L)).thenReturn(ticket);
        HazardService service = service();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitResolution(3L, "已清理", "fire-b", "FIREFIGHTER"));

        assertEquals(403, error.getCode());
    }

    @Test
    void administratorApprovalClosesTicketAndRecordsAudit() {
        HazardTicket ticket = ticket(HazardTicket.STATUS_PENDING_REVIEW);
        ticket.setResolution("已更换损坏的灭火器");
        when(hazardTicketMapper.selectById(3L)).thenReturn(ticket);
        HazardService service = service();

        HazardTicket result = service.review(3L, true, "现场复核通过", "admin-a", "COMMUNITY_ADMIN");

        assertEquals(HazardTicket.STATUS_CLOSED, result.getStatus());
        assertEquals("admin-a", result.getReviewerUsername());
        assertTrue(result.getClosedAt() != null);
        ArgumentCaptor<HazardAction> action = ArgumentCaptor.forClass(HazardAction.class);
        verify(hazardActionMapper).insert(action.capture());
        assertEquals(HazardAction.TYPE_APPROVED, action.getValue().getActionType());
    }

    @Test
    void rejectedReviewReturnsTicketToProcessing() {
        HazardTicket ticket = ticket(HazardTicket.STATUS_PENDING_REVIEW);
        ticket.setResolution("仅上传了文字说明");
        when(hazardTicketMapper.selectById(3L)).thenReturn(ticket);
        HazardService service = service();

        HazardTicket result = service.review(3L, false, "请补充现场清理结果", "admin-a", "SYSTEM_ADMIN");

        assertEquals(HazardTicket.STATUS_PROCESSING, result.getStatus());
        assertNull(result.getResolution());
    }

    @Test
    void residentCannotReadAnotherResidentsTicket() {
        HazardTicket ticket = ticket(HazardTicket.STATUS_REPORTED);
        ticket.setReporterUsername("resident-a");
        when(hazardTicketMapper.selectById(3L)).thenReturn(ticket);
        HazardService service = service();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.get(3L, "resident-b", "RESIDENT"));

        assertEquals(404, error.getCode());
    }

    private HazardService service() {
        return new HazardService(hazardTicketMapper, hazardActionMapper);
    }

    private HazardTicket ticket(String status) {
        HazardTicket ticket = new HazardTicket();
        ticket.setId(3L);
        ticket.setTicketNo("YH-20260831-TEST0001");
        ticket.setStatus(status);
        ticket.setReporterUsername("resident-a");
        return ticket;
    }
}
