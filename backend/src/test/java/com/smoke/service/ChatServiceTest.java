package com.smoke.service;

import com.smoke.dto.ChatResponse;
import com.smoke.mapper.AlertRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private AlertRecordMapper alertRecordMapper;

    @Test
    void answersEvacuationQuestionFromSystemKnowledgeBase() {
        ChatService service = new ChatService(alertRecordMapper);

        ChatResponse response = service.answer("发生火情后怎么疏散？", null);

        assertTrue(response.answer().contains("119"));
        assertTrue(response.source().contains("KNOWLEDGE_BASE"));
    }
}
