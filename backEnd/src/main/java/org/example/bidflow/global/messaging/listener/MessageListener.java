package org.example.bidflow.global.messaging.listener;

import org.example.bidflow.global.messaging.dto.MessagePayload;

/**
 * 실제 메시지를 받아 로직 처리(핸들링)하는 Listener
 * Subscribe의 onMessage 호출을 통해 메시지를 처리합니다.
 */

public interface MessageListener {
    void handleMessage(MessagePayload payload);
}
