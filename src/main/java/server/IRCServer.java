// server/IRCServer.java
package server;

// 필요한 import
import model.Room;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * IRC 서버의 메인 클래스
 * 클라이언트 연결 및 채팅방을 관리
 */
public class IRCServer {
    // 로거 설정
    private static final Logger logger = Logger.getLogger(IRCServer.class.getName());

    // 서버 기본 설정
    private static final int DEFAULT_PORT = 6667;
    private final String serverName;
    private final int port;

    // 소켓 및 상태 관리
    private ServerSocket serverSocket;
    private boolean isRunning;

    // 클라이언트 및 채팅방 관리
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /**
     * 기본 생성자
     */
    public IRCServer() {
        this("IRC_Server", DEFAULT_PORT);
    }

    /**
     * 사용자 정의 포트를 사용하는 생성자
     * @param serverName 서버 이름
     * @param port 사용할 포트 번호
     */
    public IRCServer(String serverName, int port) {
        this.serverName = serverName;
        this.port = port;
        this.isRunning = false;
    }

    /**
     * 서버 시작
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            logger.info("서버가 포트 " + port + "에서 시작되었습니다.");

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleNewClient(clientSocket);
                } catch (IOException e) {
                    if (isRunning) {
                        logger.log(Level.WARNING, "클라이언트 연결 중 오류 발생", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "서버 시작 중 오류 발생", e);
        }
    }

    /**
     * 새로운 클라이언트 연결 처리
     */
    private void handleNewClient(Socket clientSocket) {
        try {
            ClientHandler client = new ClientHandler(clientSocket, this);
            client.start();
            logger.info("새로운 클라이언트 연결: " + clientSocket.getInetAddress());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "클라이언트 핸들러 생성 실패", e);
            try {
                clientSocket.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "소켓 닫기 실패", ex);
            }
        }
    }

    /**
     * 클라이언트 등록
     * @param nickname 클라이언트 닉네임
     * @param handler 클라이언트 핸들러
     * @return 등록 성공 여부
     */
    public boolean registerClient(String nickname, ClientHandler handler) {
        if (!clients.containsKey(nickname)) {
            clients.put(nickname, handler);
            return true;
        }
        return false;
    }

    /**
     * 클라이언트 제거 (ClientHandler용)
     */
    public void removeClient(ClientHandler client) {
        String nickname = client.getNickname();
        clients.remove(nickname);

        // 모든 채팅방에서 클라이언트 제거
        for (Room room : rooms.values()) {
            room.removeMember(client);
        }
        logger.info("클라이언트 제거됨: " + nickname);
    }
    // 클라이언트 조회
    public ClientHandler getClient(String nickname) {
        return clients.get(nickname);
    }
    /**
     * 닉네임 중복 확인
     */
    public boolean isNicknameTaken(String nickname) {
        return clients.containsKey(nickname);
    }

    /**
     * 클라이언트 닉네임 업데이트
     */
    // 닉네임 변경 시 클라이언트 맵 업데이트
    public void updateClientNickname(String oldNick, String newNick, ClientHandler client) {
        clients.remove(oldNick);
        clients.put(newNick, client);
    }

    /**
     * 채팅방 조회
     */
    public Room getRoom(String name) {
        return rooms.get(name);
    }

    /**
     * 새로운 채팅방 생성
     */
    public Room createRoom(String name, ClientHandler creator) {
        Room room = new Room(name, creator);  // creator를 방장으로 설정
        rooms.put(name, room);
        logger.info("새로운 채팅방 생성: " + name + ", 방장: " + creator.getNickname());
        return room;
    }
    /**
     * 채팅방 제거
     * @param roomName 채팅방 이름
     */
    public void removeRoom(String roomName) {
        Room room = rooms.remove(roomName);
        if (room != null) {
            logger.info("채팅방 제거됨: " + roomName);
        }
    }

    /**
     * 브로드캐스트 메소드 개선
     */
    public void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }
    /**
     * 채팅방 존재 여부 확인
     */
    public boolean roomExists(String roomName) {
        return rooms.containsKey(roomName);
    }

    /**
     * 특정 사용자에게 메시지 전송
     * @param nickname 수신자 닉네임
     * @param message 메시지
     * @return 전송 성공 여부
     */
    public boolean sendToUser(String nickname, String message) {
        ClientHandler client = clients.get(nickname);
        if (client != null) {
            client.sendMessage(message);
            return true;
        }
        return false;
    }

    /**
     * 채팅방 목록 반환
     * @return 채팅방 이름 목록
     */
    public Set<String> getRoomList() {
        return new HashSet<>(rooms.keySet());
    }

    /**
     * 서버 종료
     */
    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            // 모든 클라이언트 연결 종료
            clients.values().forEach(ClientHandler::close);
            // 컬렉션 정리
            clients.clear();
            rooms.clear();
            logger.info("서버가 정상적으로 종료되었습니다.");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "서버 종료 중 오류 발생", e);
        }
    }

    // Getter 메소드들
    public String getServerName() {
        return serverName;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public Map<String, ClientHandler> getClients() {
        return Collections.unmodifiableMap(clients);
    }

    public Map<String, Room> getRooms() {
        return Collections.unmodifiableMap(rooms);
    }
}