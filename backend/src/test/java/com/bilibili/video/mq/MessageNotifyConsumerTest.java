package com.bilibili.video.mq;

import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.service.impl.MqReliabilityService;
import com.bilibili.video.ws.MessageWebSocketServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageNotifyConsumerTest {

    @Test
    void onMessage_shouldPushValidJsonWhenContentContainsSpecialCharacters() throws Exception {
        MessageWebSocketServer messageWebSocketServer = mock(MessageWebSocketServer.class);
        MqReliabilityService mqReliabilityService = mock(MqReliabilityService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        MessageNotifyConsumer consumer = new MessageNotifyConsumer(messageWebSocketServer, mqReliabilityService, objectMapper);

        doAnswer(invocation -> {
            Runnable handler = invocation.getArgument(2);
            handler.run();
            return null;
        }).when(mqReliabilityService).consumeWithIdempotency(eq("message_notify"), any(), any(Runnable.class));

        MessageNotifyMessage message = new MessageNotifyMessage(
                100L,
                "message",
                88L,
                "hello \"json\" \\\\ line1\nline2"
        );

        consumer.onMessage(message);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageWebSocketServer).push(eq(100L), payloadCaptor.capture());

        JsonNode jsonNode = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(jsonNode.get("type").asText()).isEqualTo("message");
        assertThat(jsonNode.get("refId").asLong()).isEqualTo(88L);
        assertThat(jsonNode.get("content").asText()).isEqualTo("hello \"json\" \\\\ line1\nline2");
    }
}
