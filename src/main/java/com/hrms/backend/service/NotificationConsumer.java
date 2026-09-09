package com.hrms.backend.service;

import com.hrms.backend.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveMessage(Map<String, String> notification) {
        logger.info("Received notification from RabbitMQ: {}", notification);

        // Broadcast to all clients subscribed to /topic/reminders
        messagingTemplate.convertAndSend("/topic/reminders", notification);

        logger.info("Broadcasted notification to WebSocket topic /topic/reminders");
    }
}
