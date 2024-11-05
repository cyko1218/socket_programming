// ClientHandler.java
package server;

import protocol.IRCMessage;
import protocol.IRCParser;
import model.Room;
import model.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 클라이언트 연결을 처리하는 핸들러 클래스
 * 각 클라이언트의 연결마다 새로운 스레드로 동작
 */
public class ClientHandler extends Thread {

    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final IRCServer server;
    private final BufferedReader in;
    private final PrintWriter out;
    private User user;  // nickname 대신 User 객체 사용
    private boolean isRunning;

    /**
     * ClientHandler 생성자
     * @param socket 클라이언트 소켓
     * @param server IRC 서버 인스턴스
     */
    public ClientHandler(Socket socket, IRCServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.user = new User("unknown");  // 임시 사용자 생성
        this.isRunning = true;
    }

    @Override
    public void run() {
        try {
            String message;
            while (isRunning && (message = in.readLine()) != null) {
                System.out.println("Server received raw message: " + message); // 디버그 로깅 추가
                handleMessage(message);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "클라이언트 연결 종료: " + user.getNickname(), e);
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        isRunning = false;
        server.removeClient(this);  // IRCServer의 메소드 시그니처 확인 필요
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "리소스 정리 중 오류 발생", e);
        }
    }

    /**
     * 수신된 메시지 처리
     * @param message 수신된 IRC 메시지
     */
    private void handleMessage(String message) {
        try {
            IRCMessage ircMessage = IRCParser.parse(message);
            if (ircMessage == null) return;

            System.out.println("Parsed command: " + ircMessage.getCommand()); // 디버그 로그

            switch (ircMessage.getCommand()) {
                case "NICK":
                    handleNick(ircMessage);
                    break;
                case "JOIN":
                    handleJoin(ircMessage);
                    break;
                case "PART":
                    handlePart(ircMessage);
                    break;
                case "PRIVMSG":
                    handlePrivMsg(ircMessage);
                    break;
                case "TOPIC":
                    handleTopic(ircMessage);
                    break;
                case "NAMES": // NAMES 명령어 처리 추가
                    handleNames(ircMessage);
                    break;
                case "353": // RPL_NAMREPLY 처리 추가
                    handleNameReply(ircMessage);
                case "LIST":
                    handleList();
                case "MODE":
                    System.out.println("Processing MODE command"); // 디버그 로그
                    handleMode(ircMessage);
                    break;
                case "KICK":
                    System.out.println("Processing KICK command"); // 디버그 로그
                    handleKick(ircMessage);
                    break;
                case "BAN":
                    System.out.println("Processing BAN command"); // 디버그 로그
                    handleBan(ircMessage);
                    break;
                default:
                    logger.warning("Unknown command: " + ircMessage.getCommand());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "메시지 처리 중 오류 발생", e);
            e.printStackTrace(); // 스택 트레이스 출력 추가
        }
    }

    private void handleTopic(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 0) {
            String channelName = message.getParams()[0];
            Room room = server.getRoom(channelName);

            if (room != null) {
                if (message.getText() != null) {
                    // 토픽 설정
                    String newTopic = message.getText();
                    room.setTopic(newTopic, this);
                } else {
                    // 토픽 조회
                    sendMessage(String.format(":server 332 %s %s :%s",
                            getNickname(), channelName, room.getTopic()));
                }
            } else {
                sendMessage(":server 403 " + channelName + " :No such channel");
            }
        }
    }

    /**
     * 닉네임 변경 요청 처리
     * @param message IRC 메시지
     */
    private void handleNick(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 0) {
            String newNick = message.getParams()[0];
            String oldNick = user.getNickname();
            user.setNickname(newNick);
            server.updateClientNickname(oldNick, newNick, this);
            sendMessage(":" + server.getServerName() + " NICK :" + newNick);
        }
    }

    private void handlePrivMsg(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 0
                && message.getText() != null) {
            String target = message.getParams()[0];
            String text = message.getText();

            if (target.startsWith("#")) {
                // 채널 메시지
                Room room = server.getRoom(target);
                if (room != null) {
                    room.broadcast(text, this);
                    logger.info(String.format("채널 메시지 브로드캐스트: %s -> %s: %s",
                            getNickname(), target, text));
                } else {
                    sendMessage(":server 403 " + target + " :No such channel");
                }
            } else {
                // 개인 메시지
                ClientHandler targetClient = server.getClient(target);
                if (targetClient != null) {
                    targetClient.sendMessage(":" + getNickname() + " PRIVMSG " + target + " :" + text);
                } else {
                    sendMessage(":server 401 " + target + " :No such nick/channel");
                }
            }
        }
    }

    public void sendMessage(String message) {
        if (out != null && isRunning) {
            out.println(message);
            logger.fine("메시지 전송: " + message);
        }
    }

    private void handleJoin(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 0) {
            String roomName = message.getParams()[0];

            // 비밀번호 확인
            String password = null;
            if (message.getParams().length > 1) {
                password = message.getParams()[1];
            }

            // 방이 있는지 확인하고 없으면 생성
            Room room = server.getRoom(roomName);
            if (room == null) {
                room = server.createRoom(roomName, this);
                logger.info("새로운 채팅방 생성: " + roomName + ", 방장: " + getNickname());
                // 생성자는 자동으로 방에 추가되어야 함
                room.addMember(this, password);
                // 방 생성 성공 메시지 전송
                sendMessage(":" + getNickname() + " JOIN " + roomName);
            } else {
                // 기존 방 입장
                if (room.addMember(this, password)) {
                    // JOIN 성공 메시지 전송
                    sendMessage(":" + getNickname() + " JOIN " + roomName);
                }
            }
        }
    }

    /**
     * PART 명령어 처리
     */
    private void handlePart(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 0) {
            String roomName = message.getParams()[0];
            Room room = server.getRoom(roomName);
            if (room != null) {
                room.removeMember(this);
                String partMessage = ":" + getNickname() + " PART " + roomName;
                room.broadcast(partMessage, this);
                sendMessage(partMessage);
                logger.info(getNickname() + " left channel: " + roomName);
            } else {
                sendMessage(":server 403 " + roomName + " :No such channel");
            }
        }
    }

    /**
     * LIST 명령어 처리
     */
    private void handleList() {
        Set<String> rooms = server.getRoomList();
        sendMessage(":server 321 " + getNickname() + " Channel :Users Name");
        for (String roomName : rooms) {
            Room room = server.getRoom(roomName);
            if (room != null) {
                sendMessage(":server 322 " + getNickname() + " " + roomName + " " +
                        room.getUserCount() + " :" + room.getTopic());
            }
        }
        sendMessage(":server 323 " + getNickname() + " :End of /LIST");
    }

    /**
     * 채팅방 참여자 목록 전송
     */
    private void sendUserList(Room room) {
        StringBuilder userList = new StringBuilder();
        for (ClientHandler member : room.getMembers()) {
            userList.append(member.getNickname()).append(" ");
        }
        sendMessage(":server 353 " + user.getNickname() + " = " + room.getName() + " :" + userList);
        sendMessage(":server 366 " + user.getNickname() + " " + room.getName() + " :End of /NAMES list");
    }

    public String getNickname() {
        return user.getNickname();
    }

    private void handleMode(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 1) {
            String channelName = message.getParams()[0];
            String mode = message.getParams()[1];
            String password = message.getParams().length > 2 ? message.getParams()[2] : null;

            Room room = server.getRoom(channelName);
            if (room != null && room.isAdmin(this)) { // 방장 확인
                if ("+k".equals(mode) && password != null) { // 비밀번호 설정
                    room.setPassword(password, this);
                    sendMessage(":server MODE " + channelName + " +k " + password);
                } else if ("-k".equals(mode)) { // 비밀번호 해제
                    room.setPassword(null, this);
                    sendMessage(":server MODE " + channelName + " -k");
                } else {
                    sendMessage(":server 472 " + getNickname() + " " + mode + " :Unknown mode flag");
                }
            } else {
                sendMessage(":server 482 " + getNickname() + " " + channelName + " :You’re not channel operator");
            }
        }
    }


    /**
     * 클라이언트 연결 종료
     */
    public void close() {
        isRunning = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "리소스 정리 중 오류 발생", e);
        }
    }

    private void handleKick(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 1) {
            String channelName = message.getParams()[0];
            String targetNickname = message.getParams()[1];

            Room room = server.getRoom(channelName);
            if (room != null && room.isAdmin(this)) { // 방장 확인
                ClientHandler target = server.getClient(targetNickname);
                if (target != null) {
                    room.kickUser(target, this);
                    sendMessage(":server KICK " + channelName + " " + targetNickname);
                } else {
                    sendMessage(":server 401 " + targetNickname + " :No such nick/channel");
                }
            } else {
                sendMessage(":server 482 " + getNickname() + " " + channelName + " :You’re not channel operator");
            }
        }
    }
    private void handleBan(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 1) {
            String channelName = message.getParams()[0];
            String targetNickname = message.getParams()[1];

            Room room = server.getRoom(channelName);
            if (room != null && room.isAdmin(this)) { // 방장 확인
                ClientHandler target = server.getClient(targetNickname);
                if (target != null) {
                    room.banUser(target, this);
                    sendMessage(":server MODE " + channelName + " +b " + targetNickname);
                } else {
                    sendMessage(":server 401 " + targetNickname + " :No such nick/channel");
                }
            } else {
                sendMessage(":server 482 " + getNickname() + " " + channelName + " :You’re not channel operator");
            }
        }
    }
    public void handleCommand(String command) {
        System.out.println("Server received command: " + command); // 디버그 로그

        if (command.startsWith("KICK")) {
            // KICK 처리
            String[] parts = command.split(" ");
            if (parts.length >= 3) {
                String channel = parts[1];
                String target = parts[2];
                System.out.println("Processing KICK: channel=" + channel + " target=" + target); // 디버그 로그
                // KICK 처리 로직
            }
        } else if (command.startsWith("MODE")) {
            // MODE 처리
            System.out.println("Processing MODE command: " + command); // 디버그 로그
            // MODE 처리 로직
        }
    }
    // NAMES 명령어 처리 메서드 추가
    private void handleNames(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 0) {
            String channelName = message.getParams()[0];
            Room room = server.getRoom(channelName);
            if (room != null) {
                StringBuilder userList = new StringBuilder();
                for (ClientHandler member : room.getMembers()) {
                    if (room.isAdmin(member)) {
                        userList.append("@");
                    }
                    userList.append(member.getNickname()).append(" ");
                }
                sendMessage(":server 353 " + getNickname() + " = " + channelName + " :" + userList.toString());
                sendMessage(":server 366 " + getNickname() + " " + channelName + " :End of /NAMES list");
            }
        }
    }

    // RPL_NAMREPLY 처리 메서드 추가
    private void handleNameReply(IRCMessage message) {
        if (message.getParams() != null && message.getParams().length > 2) {
            String channelName = message.getParams()[2];
            Room room = server.getRoom(channelName);
            if (room != null) {
                sendUserList(room);
            }
        }
    }
}