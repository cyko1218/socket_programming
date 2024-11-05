// protocol/IRCCommand.java
package protocol;

public class IRCCommand {
    // 기본 IRC 명령어
    public static final String PRIVMSG = "PRIVMSG";
    public static final String JOIN = "JOIN";
    public static final String PART = "PART";
    public static final String NICK = "NICK";
    public static final String QUIT = "QUIT";
    public static final String TOPIC = "TOPIC";
    public static final String LIST = "LIST";
    public static final String NAMES = "NAMES";
    public static final String WHISPER = "WHISPER"; // 클라이언트 내부용 구분자

    // 관리 명령어
    public static final String KICK = "KICK";
    public static final String BAN = "BAN";
    public static final String UNBAN = "UNBAN";
    public static final String MODE = "MODE";

    public static IRCMessage createPrivateMessage(String sender, String target, String message) {
        IRCMessage ircMessage = new IRCMessage(PRIVMSG);
        ircMessage.setPrefix(sender);
        ircMessage.setParams(new String[]{target});
        ircMessage.setText(message);
        return ircMessage;
    }

    public static IRCMessage createJoinMessage(String nickname, String channel) {
        IRCMessage ircMessage = new IRCMessage(JOIN);
        ircMessage.setPrefix(nickname);
        ircMessage.setParams(new String[]{channel});
        return ircMessage;
    }

    public static IRCMessage createPartMessage(String nickname, String channel) {
        IRCMessage ircMessage = new IRCMessage(PART);
        ircMessage.setPrefix(nickname);
        ircMessage.setParams(new String[]{channel});
        return ircMessage;
    }

    public static IRCMessage createNickMessage(String oldNick, String newNick) {
        IRCMessage ircMessage = new IRCMessage(NICK);
        ircMessage.setPrefix(oldNick);
        ircMessage.setText(newNick);
        return ircMessage;
    }

    // Topic 설정을 위한 메소드
    public static IRCMessage createTopicMessage(String channel, String topic) {
        IRCMessage message = new IRCMessage(TOPIC);
        message.setParams(new String[]{channel});
        message.setText(topic);
        return message;
    }

    // Topic 조회를 위한 메소드
    public static IRCMessage createTopicQueryMessage(String channel) {
        IRCMessage message = new IRCMessage(TOPIC);
        message.setParams(new String[]{channel});
        return message;
    }

    // IRC 메시지 형식으로 변환
    public static String formatMessage(IRCMessage message) {
        StringBuilder formatted = new StringBuilder();

        // prefix 추가
        if (message.getPrefix() != null) {
            formatted.append(":").append(message.getPrefix()).append(" ");
        }

        // command 추가
        formatted.append(message.getCommand());

        // parameters 추가
        if (message.getParams() != null) {
            for (String param : message.getParams()) {
                formatted.append(" ").append(param);
            }
        }

        // text 추가 (있는 경우)
        if (message.getText() != null) {
            formatted.append(" :").append(message.getText());
        }

        return formatted.toString();
    }

    // 귓속말 메시지 생성 메소드
    public static IRCMessage createWhisperMessage(String sender, String target, String message) {
        IRCMessage ircMessage = new IRCMessage(PRIVMSG); // IRC 표준에 맞춰 PRIVMSG 사용
        ircMessage.setPrefix(sender);
        ircMessage.setParams(new String[]{target}); // 채널 대신 사용자 지정
        ircMessage.setText(message);
        return ircMessage;
    }

    public static IRCMessage createKickMessage(String sender, String channel, String target) {
        IRCMessage ircMessage = new IRCMessage(KICK);
        ircMessage.setPrefix(sender);
        ircMessage.setParams(new String[]{channel, target});
        return ircMessage;
    }

    public static IRCMessage createBanMessage(String sender, String channel, String target) {
        IRCMessage ircMessage = new IRCMessage(BAN);
        ircMessage.setPrefix(sender);
        ircMessage.setParams(new String[]{channel, target});
        return ircMessage;
    }

    public static IRCMessage createUnbanMessage(String sender, String channel, String target) {
        IRCMessage ircMessage = new IRCMessage(UNBAN);
        ircMessage.setPrefix(sender);
        ircMessage.setParams(new String[]{channel, target});
        return ircMessage;
    }

    public static IRCMessage createModeMessage(String sender, String channel, String mode, String parameter) {
        IRCMessage ircMessage = new IRCMessage(MODE);
        ircMessage.setPrefix(sender);
        if (parameter != null) {
            ircMessage.setParams(new String[]{channel, mode, parameter});
        } else {
            ircMessage.setParams(new String[]{channel, mode});
        }
        return ircMessage;
    }
}