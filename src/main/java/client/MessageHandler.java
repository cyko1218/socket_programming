// client/MessageHandler.java
package client;

import client.state.RoomState;
import protocol.IRCCommand;
import protocol.IRCMessage;
import protocol.IRCParser;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

public class MessageHandler {
    private static final Logger logger = Logger.getLogger(MessageHandler.class.getName());
    private final IRCClient client;

    public MessageHandler(IRCClient client) {
        this.client = client;
    }

    public void handleMessage(String rawMessage) {
        logger.info("수신된 메시지: " + rawMessage);
        IRCMessage message = IRCParser.parse(rawMessage);
        if (message == null) {
            logger.warning("메시지 파싱 실패: " + rawMessage);
            return;
        }

        try {
            switch (message.getCommand()) {
                case "PRIVMSG":
                    handlePrivMsg(message);
                    break;
                case "JOIN":
                    handleJoin(message);
                    break;
                case "PART":
                    handlePart(message);
                    break;
                case "NICK":
                    handleNick(message);
                    break;
                case "321": // RPL_LISTSTART
                    handleListStart();
                    break;

                case "322": // RPL_LIST
                    handleListItem(message);
                    break;

                case "323": // RPL_LISTEND
                    handleListEnd();
                    break;
                case "332":  // RPL_TOPIC
                    handleTopic(message);
                    break;
                case "353":  // RPL_NAMREPLY
                    handleNameReply(message);
                    break;
                case "366":  // RPL_ENDOFNAMES
                    handleEndOfNames(message);
                    break;
                case "KICK":
                    handleKick(message);
                    break;
                case "MODE":
                    handleMode(message);
                    break;
                default:
                    if (message.getText() != null) {
                        notifyUI("서버: " + message.getText());
                    }
            }
        } catch (Exception e) {
            logger.warning("메시지 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePrivMsg(IRCMessage message) {
        try {
            String sender = null;
            String target = null;
            String text = message.getText();

            if (message.getPrefix() != null) {
                sender = message.getPrefix().split("!")[0];
            }

            if (message.getParams() != null && message.getParams().length > 0) {
                target = message.getParams()[0];
            }

            if (text != null && sender != null && target != null) {
                if (!target.startsWith("#")) {
                    if (target.equals(client.getNickname())) {
                        notifyUI(String.format("(귓속말) %s: %s", sender, text));
                    } else if (sender.equals(client.getNickname())) {
                        notifyUI(String.format("(귓속말→%s) %s", target, text));
                    }
                } else {
                    if (!sender.equals(client.getNickname())) {
                        notifyUI(String.format("%s: %s", sender, text));
                    }
                }
                logger.info(String.format("메시지 수신: %s -> %s: %s", sender, target, text));
            }
        } catch (Exception e) {
            logger.warning("PRIVMSG 처리 중 오류: " + e.getMessage());
        }
    }

    private void handleJoin(IRCMessage message) {
        String user = message.getPrefix() != null ? message.getPrefix().split("!")[0] : "Unknown";

        if (message.getParams() != null && message.getParams().length > 0) {
            final String channelName = message.getParams()[0];
            final boolean isNewRoom = message.getText() != null && message.getText().contains("@");

            client.getStateManager().updateRoomState(channelName, state -> {
                state.addUser(user);
                if (isNewRoom) {
                    state.setAdmin(true);
                    client.setAdmin(true);
                }
            });

            if (isNewRoom) {
                notifyUI(String.format("새로운 채팅방 '%s'을 생성했습니다. 당신이 방장입니다.", channelName));
            }

            notifyUI(String.format("→ %s님이 %s에 입장하셨습니다.", user, channelName));

            if (user.equals(client.getNickname())) {
                requestRoomState(channelName);
            }
        }
    }

    private void handlePart(IRCMessage message) {
        String user = message.getPrefix() != null ? message.getPrefix().split("!")[0] : "Unknown";

        if (message.getParams() != null && message.getParams().length > 0) {
            final String channelName = message.getParams()[0];
            final boolean isCurrentUser = user.equals(client.getNickname());

            client.getStateManager().updateRoomState(channelName, state -> {
                state.removeUser(user);
            });

            if (isCurrentUser) {
                client.getStateManager().removeRoom(channelName);
            }

            notifyUI(String.format("← %s님이 %s에서 퇴장하셨습니다.", user, channelName));
        }
    }

    private void handleListStart() {
        notifyUI("Channel list:");
    }
    private void handleListItem(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length >= 3) {
            String channel = message.getParams()[1];
            String userCount = message.getParams()[2];
            String topic = message.getText() != null ? message.getText() : "No topic";

            notifyUI(String.format("  %s (%s users) - %s",
                    channel, userCount, topic));
        }
    }

    private void handleListEnd() {
        notifyUI("End of channel list");
    }
    private void handleNick(IRCMessage message) {
        String oldNick = message.getPrefix() != null ? message.getPrefix().split("!")[0] : "Unknown";
        String newNick = message.getText();

        if (newNick != null) {
            notifyUI(String.format("* %s님이 %s(으)로 닉네임을 변경하셨습니다.",
                    oldNick, newNick));
        }
    }

    private void handleTopic(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 1) {
            String channel = message.getParams()[1];
            String topic = message.getText();

            client.getStateManager().updateRoomState(channel, state -> {
                state.updateTopic(topic);
            });

            notifyUI(String.format("채널 %s의 주제: %s", channel, topic));
        }
    }

    private void handleNameReply(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 2) {
            String channel = message.getParams()[2];
            String[] users = message.getText().trim().split("\\s+");
            ArrayList<String> userList = new ArrayList<>(Arrays.asList(users));

            // StateManager를 통해 상태 업데이트
            client.getStateManager().updateRoomState(channel, state -> {
                state.updateUsers(userList);

                // 방장 권한 확인 (현재 클라이언트가 @로 시작하는지)
                boolean isAdmin = false;
                for (String user : userList) {
                    if (user.startsWith("@") &&
                            user.substring(1).equals(client.getNickname())) {
                        isAdmin = true;
                        break;
                    }
                }
                state.setAdmin(isAdmin);
                client.setAdmin(isAdmin);
            });

            // UI 업데이트
            if (client.getMessageListener() instanceof ClientUI) {
                ClientUI ui = (ClientUI) client.getMessageListener();
                SwingUtilities.invokeLater(() -> {
                    ui.updateUserList(userList);
                    RoomState state = client.getStateManager().getRoomState(channel);
                    if (state != null) {
                        ui.updateRoomInfo(channel, state.getTopic(), userList.size());
                        ui.updateAdminControls(state.isAdmin());
                    }
                });
            }
        }
    }

    private void handleEndOfNames(IRCMessage message) {
        // Names 목록이 끝났음을 표시
    }

    private void notifyUI(String message) {
        if (client != null) {
            client.notifyMessageReceived(message);
            logger.info("UI 알림: " + message);
        }
    }

    // 귓속말 전송을 위한 새로운 메소드
    public void sendWhisper(String target, String message) {
        try {
            IRCMessage whisperMsg = IRCCommand.createWhisperMessage(
                    client.getNickname(),
                    target,
                    message
            );
            client.sendMessage(IRCCommand.formatMessage(whisperMsg));
            // UI에 보낸 귓속말 표시
            client.notifyMessageReceived(String.format("(귓속말→%s) %s", target, message));
        } catch (Exception e) {
            logger.warning("귓속말 전송 중 오류: " + e.getMessage());
            client.notifyMessageReceived("귓속말 전송 실패: " + e.getMessage());
        }
    }
    private void handleKick(IRCMessage message) {
        if (message.getParams() == null || message.getParams().length < 2) {
            return;
        }

        final String channel = message.getParams()[0];
        final String target = message.getParams()[1];
        final String kicker = message.getPrefix() != null ? message.getPrefix().split("!")[0] : "Unknown";
        final String reason = message.getText();
        final boolean isCurrentUser = target.equals(client.getNickname());

        client.getStateManager().updateRoomState(channel, state -> {
            state.removeUser(target);
            if (isCurrentUser) {
                state.setAdmin(false);
            }
        });

        if (isCurrentUser) {
            client.setAdmin(false);
            client.getStateManager().removeRoom(channel);
        }

        if (reason != null && !reason.isEmpty()) {
            notifyUI(String.format("%s님이 %s님을 %s 채널에서 강퇴했습니다. (사유: %s)",
                    kicker, target, channel, reason));
        } else {
            notifyUI(String.format("%s님이 %s님을 %s 채널에서 강퇴했습니다.",
                    kicker, target, channel));
        }
    }

    private void handleBan(IRCMessage message) {
        if (message.getParams() == null || message.getParams().length < 2) {
            logger.warning("Invalid BAN message format: " + message);
            return;
        }

        String channel = message.getParams()[0];
        String target = message.getParams()[1];
        String banner = message.getPrefix() != null ? message.getPrefix().split("!")[0] : "Unknown";

        // 밴 당한 사용자가 자신인 경우
        if (target.equals(client.getNickname())) {
            // 해당 채널에서 강제 퇴장
            if (client.roomExists(channel)) {
                client.leaveRoom(channel);
            }
        }

        notifyUI(String.format("%s님이 %s님을 %s 채널에서 차단했습니다.",
                banner, target, channel));
    }

    private void handleUnban(IRCMessage message) {
        if (message.getParams() == null || message.getParams().length < 2) {
            logger.warning("Invalid UNBAN message format: " + message);
            return;
        }

        String channel = message.getParams()[0];
        String target = message.getParams()[1];
        String unbanner = message.getPrefix() != null ? message.getPrefix().split("!")[0] : "Unknown";

        notifyUI(String.format("%s님이 %s님의 %s 채널 차단을 해제했습니다.",
                unbanner, target, channel));
    }

    private void handleMode(IRCMessage message) {
        if (message.getParams() == null || message.getParams().length < 2) {
            return;
        }

        String channel = message.getParams()[0];
        String mode = message.getParams()[1];
        String parameter = message.getParams().length > 2 ? message.getParams()[2] : null;

        client.getStateManager().updateRoomState(channel, state -> {
            switch (mode) {
                case "+k":
                    state.setHasPassword(true);
                    notifyUI(String.format("%s 채널에 비밀번호가 설정되었습니다.", channel));
                    break;
                case "-k":
                    state.setHasPassword(false);
                    notifyUI(String.format("%s 채널의 비밀번호가 제거되었습니다.", channel));
                    break;
                case "+o":
                    if (parameter != null && parameter.equals(client.getNickname())) {
                        state.setAdmin(true);
                        client.setAdmin(true);
                    }
                    break;
                case "-o":
                    if (parameter != null && parameter.equals(client.getNickname())) {
                        state.setAdmin(false);
                        client.setAdmin(false);
                    }
                    break;
            }
        });

        // UI 업데이트
        if (client.getMessageListener() instanceof ClientUI) {
            ClientUI ui = (ClientUI) client.getMessageListener();
            SwingUtilities.invokeLater(() -> {
                ui.updateAdminControls();
                ui.updateRoom(channel);
            });
        }
    }
    private void requestRoomState(String channel) {
        client.sendMessage("NAMES " + channel);
        client.sendMessage("MODE " + channel);
        client.sendMessage("TOPIC " + channel);
    }
}