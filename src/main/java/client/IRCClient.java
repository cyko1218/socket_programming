package client;

import client.state.RoomState;
import client.state.StateChangeListener;
import client.state.StateManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IRCClient {
    private static final Logger logger = Logger.getLogger(IRCClient.class.getName());

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final MessageHandler messageHandler;
    private final BlockingQueue<String> messageQueue;
    private MessageListener messageListener;
    private String nickname;
    private String currentRoom;
    private boolean isConnected;
    private final StateManager stateManager;
    private boolean isAdmin;
    private final Set<String> joinedRooms = new HashSet<>();  // 참여 중인 방 목록 추적
    private final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();

    public IRCClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.messageQueue = new LinkedBlockingQueue<>();
        this.stateManager = new StateManager();
        this.messageHandler = new MessageHandler(this);
        this.isConnected = true;
    }

    // 방장 상태 확인/설정 메서드
    public boolean isAdmin() {
        if (currentRoom != null) {
            RoomState state = stateManager.getRoomState(currentRoom);
            return state != null && state.isAdmin();
        }
        return false;
    }

    // StateManager 관련 메서드
    public StateManager getStateManager() {
        return stateManager;
    }

    public void addStateChangeListener(StateChangeListener listener) {
        stateManager.addListener(listener);
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
        // 현재 방의 관리자 상태도 업데이트
        if (currentRoom != null) {
            RoomInfo room = rooms.get(currentRoom);
            if (room != null) {
                room.setAdmin(admin);
            }
        }
        // 기존 알림 코드 유지
        if (messageListener != null) {
            if (admin) {
                notifyMessageReceived("방장 권한을 얻었습니다.");
            } else {
                notifyMessageReceived("방장 권한이 해제되었습니다.");
            }
        }
    }
    public boolean roomExists(String roomName) {
        return joinedRooms.contains(roomName);
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void notifyMessageReceived(String message) {
        if (messageListener != null) {
            messageListener.onMessageReceived(message);
        }
    }

    public void setCurrentRoom(String roomName) {
        this.currentRoom = roomName;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public RoomState getRoomInfo(String roomName) {
        return stateManager.getRoomState(roomName);
    }

    public void sendChatMessage(String message) {
        if (currentRoom != null) {
            sendMessage("PRIVMSG " + currentRoom + " :" + message);
            logger.info(String.format("채팅 메시지 전송: %s -> %s: %s", nickname, currentRoom, message));
        } else {
            logger.warning("현재 채팅방이 설정되지 않았습니다.");
        }
    }
    public MessageListener getMessageListener() {
        return messageListener;
    }

    // 채널 참여/퇴장 관련 메서드 수정
    public void joinRoom(String roomName) {
        if (!roomName.startsWith("#")) {
            roomName = "#" + roomName;
        }
        sendMessage("JOIN " + roomName);
        setCurrentRoom(roomName);
        logger.info("Joining room: " + roomName);
    }

    public void leaveRoom(String roomName) {
        if (!roomName.startsWith("#")) {
            roomName = "#" + roomName;
        }
        sendMessage("PART " + roomName);
        stateManager.removeRoom(roomName);
        if (currentRoom != null && currentRoom.equals(roomName)) {
            setCurrentRoom(null);
        }
        logger.info("Leaving room: " + roomName);
    }

    public void sendPrivateMessage(String target, String message) {
        sendMessage("PRIVMSG " + target + " :" + message);
    }

    public void sendChannelMessage(String message) {
        if (currentRoom != null) {
            sendPrivateMessage(currentRoom, message);
        } else {
            logger.warning("채팅방에 참여하지 않은 상태에서 메시지 전송 시도");
        }
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        sendMessage("NICK " + nickname);
    }

    public void sendMessage(String message) {
        if (isConnected && out != null) {
            System.out.println("IRCClient: sending raw message: " + message);
            out.println(message);
            out.flush();
            logger.info("Sent: " + message);
        } else {
            logger.warning("Failed to send message (not connected): " + message);
        }
    }


    public void start() {
        new Thread(this::receiveMessages, "receiver").start();
        new Thread(this::processMessages, "processor").start();
    }

    private void receiveMessages() {
        try {
            String message;
            while (isConnected && (message = in.readLine()) != null) {
                messageQueue.put(message);
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.WARNING, "Connection terminated", e);
            disconnect();
        }
    }

    private void processMessages() {
        try {
            while (isConnected) {
                String message = messageQueue.take();
                messageHandler.handleMessage(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void disconnect() {
        isConnected = false;
        stateManager.clear();  // 상태 정보 초기화
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error during disconnect", e);
        }
    }

    public void setTopic(String channel, String topic) {
        if (!channel.startsWith("#")) {
            channel = "#" + channel;
        }
        sendMessage("TOPIC " + channel + " :" + topic);
    }

    public void queryTopic(String channel) {
        if (!channel.startsWith("#")) {
            channel = "#" + channel;
        }
        sendMessage("TOPIC " + channel);
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void onMessageReceived(String message) {
        // ClientUI에서 오버라이드할 메서드
    }

    // 메시지 전송 관련 메서드
    public void sendCommand(String command) {
        sendMessage(command);
    }

    public void kickUser(String channel, String target) {
        if (channel != null && target != null) {
            String kickCommand = String.format("KICK %s %s", channel, target);
            sendCommand(kickCommand);
        }
    }


    public void banUser(String channel, String nickname) {
        System.out.println("IRCClient: executing ban command for " + nickname + " in " + channel); // 디버그 로그
        sendMessage("MODE " + channel + " +b " + nickname);
    }

    public void unbanUser(String channel, String nickname) {
        // UNBAN을 위한 MODE 메시지 형식: "MODE #channel -b nickname"
        String unbanCommand = String.format("MODE %s -b %s", channel, nickname);
        sendMessage(unbanCommand);
    }

    public void setChannelMode(String channel, String mode, String parameter) {
        System.out.println("IRCClient: executing mode command: " + mode + " for " + channel); // 디버그 로그
        if (parameter != null) {
            sendMessage("MODE " + channel + " " + mode + " " + parameter);
        } else {
            sendMessage("MODE " + channel + " " + mode);
        }
    }

    // MODE 명령어로 비밀번호를 설정하는 메서드
    public void setChannelPassword(String channel, String password) {
        if (!channel.startsWith("#")) {
            channel = "#" + channel;
        }
        sendMessage("MODE " + channel + " +k " + password);
        updateRoomPassword(channel, true);
    }



    // MODE 명령어로 비밀번호를 제거하는 메서드
    public void removeChannelPassword(String channel) {
        if (!channel.startsWith("#")) {
            channel = "#" + channel;
        }
        sendMessage("MODE " + channel + " -k");
        updateRoomPassword(channel, false);
    }

    public void handleNameList(String channel, String[] users) {
        List<String> userList = Arrays.asList(users);
        RoomInfo room = rooms.computeIfAbsent(channel, RoomInfo::new);
        room.updateUsers(userList);
        if (messageListener != null && messageListener instanceof ClientUI) {
            ((ClientUI) messageListener).updateUserList(new ArrayList<>(userList));
            ((ClientUI) messageListener).updateRoomInfo(channel, room.getTopic(), userList.size());
        }
    }

    public void handleTopicUpdate(String channel, String topic) {
        stateManager.updateRoomState(channel, state -> {
            state.updateTopic(topic);
            if (messageListener != null && messageListener instanceof ClientUI) {
                ((ClientUI) messageListener).updateRoomInfo(channel, topic, state.getUserCount());
            }
        });
    }

    public void updateRoomInfo(String roomName, String topic, List<String> users) {
        stateManager.updateRoomState(roomName, state -> {
            if (topic != null) {
                state.updateTopic(topic);
            }
            if (users != null) {
                state.updateUsers(users);
            }
            logger.info(String.format("Updated room info for %s: topic='%s', users=%s",
                    roomName, topic, users));
        });
    }
    public void updateRoomState(String roomName, String topic, List<String> users) {
        stateManager.updateRoomState(roomName, state -> {
            if (topic != null) state.updateTopic(topic);
            if (users != null) state.updateUsers(users);
        });
    }
    // 비밀번호 관련 메서드 수정
    public void updateRoomPassword(String channel, boolean hasPassword) {
        stateManager.updateRoomState(channel, state -> {
            state.setHasPassword(hasPassword);
            logger.info("Updated password status for room " + channel + ": " + hasPassword);
        });
    }

    public class RoomInfo {
        private String name;
        private String topic;
        private List<String> users;
        private boolean isAdmin;
        private boolean hasPassword;  // 새로운 필드 추가

        public RoomInfo(String name) {
            this.name = name;
            this.users = new ArrayList<>();
            this.topic = "Welcome to " + name;
            this.isAdmin = false;
            this.hasPassword = false;  // 초기화
        }

        public void updateUsers(List<String> newUsers) {
            this.users = new ArrayList<>(newUsers);
        }

        public void setTopic(String channel, String topic) {
            if (!channel.startsWith("#")) {
                channel = "#" + channel;
            }
            sendMessage("TOPIC " + channel + " :" + topic);
        }
        public String getTopic() {
            return topic;
        }

        public List<String> getUsers() {
            return users;
        }

        public int getUserCount() {
            return users.size();
        }

        public boolean isAdmin() {
            return isAdmin;
        }

        public void setAdmin(boolean admin) {
            if (currentRoom != null) {
                stateManager.updateRoomState(currentRoom, state -> {
                    state.setAdmin(admin);
                    logger.info("Updated admin status for room " + currentRoom + ": " + admin);
                });
            }
            if (messageListener != null) {
                notifyMessageReceived(admin ? "방장 권한을 얻었습니다." : "방장 권한이 해제되었습니다.");
            }
        }

        public String getName() {
            return name;
        }

        public boolean hasPassword() {
            return hasPassword;
        }

        public void setHasPassword(boolean hasPassword) {
            this.hasPassword = hasPassword;
        }
    }


}