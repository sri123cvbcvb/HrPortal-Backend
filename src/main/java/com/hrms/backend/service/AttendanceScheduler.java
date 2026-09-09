package com.hrms.backend.service;

import com.hrms.backend.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
@EnableScheduling
public class AttendanceScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceScheduler.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Scheduled(cron = "${attendance.reminder.morning}")
    public void sendMorningReminder() {
        logger.info("Morning Scheduler triggered at {}", LocalTime.now());

        Map<String, String> notification = new HashMap<>();
        notification.put("type", "SIGN_IN");
        notification.put("title", "Morning Reminder");
        notification.put("message", "Please sign in to mark your attendance.");
        notification.put("time", LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")));

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, notification);
        logger.info("Published morning attendance reminder to RabbitMQ");
    }

    @Scheduled(cron = "${attendance.reminder.evening}")
    public void sendEveningReminder() {
        logger.info("Evening Scheduler triggered at {}", LocalTime.now());

        Map<String, String> notification = new HashMap<>();
        notification.put("type", "SIGN_OUT");
        notification.put("title", "Evening Reminder");
        notification.put("message", "Please sign out before leaving.");
        notification.put("time", LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")));

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, notification);
        logger.info("Published evening attendance reminder to RabbitMQ");
    }
}
