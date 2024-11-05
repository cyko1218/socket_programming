// model/Message.java
package model;

import java.time.LocalDateTime;

public class Message {
    private final String sender;
    private final String content;
    private final LocalDateTime timestamp;
    private final String target;  // 수신자 또는 채널명
    private final MessageType type;

    public enum MessageType {
        PRIVATE_MESSAGE,
        CHANNEL_MESSAGE,
        SYSTEM_MESSAGE
    }

    public Message(String sender, String content, String target, MessageType type) {
        this.sender = sender;
        this.content = content;
        this.target = target;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    // 시스템 메시지용 생성자
    public static Message createSystemMessage(String content) {
        return new Message("System", content, null, MessageType.SYSTEM_MESSAGE);
    }

    // 채널 메시지용 생성자
    public static Message createChannelMessage(String sender, String content, String channel) {
        return new Message(sender, content, channel, MessageType.CHANNEL_MESSAGE);
    }

    // 개인 메시지용 생성자
    public static Message createPrivateMessage(String sender, String content, String target) {
        return new Message(sender, content, target, MessageType.PRIVATE_MESSAGE);
    }

    // Getters
    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getTarget() {
        return target;
    }

    public MessageType getType() {
        return type;
    }

    @Override
    public String toString() {
        switch (type) {
            case SYSTEM_MESSAGE:
                return String.format("[시스템] %s", content);
            case CHANNEL_MESSAGE:
                return String.format("[%s] %s: %s", target, sender, content);
            case PRIVATE_MESSAGE:
                return String.format("[PM from %s] %s", sender, content);
            default:
                return content;
        }
    }
}