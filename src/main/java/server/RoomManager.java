package server;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import model.Room;
import java.util.logging.Logger;

public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private static final Logger logger = Logger.getLogger(RoomManager.class.getName());

    // 기존 방 가져오기
    public Room getRoom(String name) {
        return rooms.get(name);
    }

    // 새로운 방 생성
    public Room createRoom(String name, ClientHandler creator) {
        Room room = new Room(name, creator);
        rooms.put(name, room);
        logger.info("새로운 채팅방 생성: " + name + ", 방장: " + creator.getNickname());
        return room;
    }

    /**
     * 사용자를 채팅방에 참가시킴
     */
    public boolean joinRoom(String roomName, ClientHandler client, String password) {
        Room room = rooms.get(roomName);
        if (room == null) {
            // 방이 없으면 새로 생성하고 참가 (생성자가 방장이 됨)
            room = createRoom(roomName, client);
            return true;
        } else {
            // 기존 방에 참가
            return room.addMember(client, password);
        }
    }

    /**
     * 채팅방에서 사용자 퇴장
     */
    public void leaveRoom(String roomName, ClientHandler client) {
        Room room = rooms.get(roomName);
        if (room != null) {
            room.removeMember(client);
            // 방에 아무도 없으면 방 삭제
            if (room.getUserCount() == 0) {
                rooms.remove(roomName);
                logger.info("Room removed: " + roomName);
            }
        }
    }

    /**
     * 사용자 강퇴
     */
    public boolean kickUser(String roomName, ClientHandler target, ClientHandler executor) {
        Room room = getRoom(roomName);
        if (room != null) {
            return room.kickUser(target, executor);
        }
        return false;
    }

    /**
     * 사용자 차단
     */
    public boolean banUser(String roomName, ClientHandler target, ClientHandler executor) {
        Room room = getRoom(roomName);
        if (room != null) {
            return room.banUser(target, executor);
        }
        return false;
    }

    /**
     * 사용자 차단 해제
     */
    public boolean unbanUser(String roomName, String nickname, ClientHandler executor) {
        Room room = getRoom(roomName);
        if (room != null) {
            return room.unbanUser(nickname, executor);
        }
        return false;
    }

    /**
     * 채팅방 비밀번호 설정
     */
    public boolean setRoomPassword(String roomName, String password, ClientHandler executor) {
        Room room = getRoom(roomName);
        if (room != null) {
            return room.setPassword(password, executor);
        }
        return false;
    }

    /**
     * 방장 확인
     */
    public boolean isAdmin(String roomName, ClientHandler client) {
        Room room = getRoom(roomName);
        return room != null && room.isAdmin(client);
    }

    /**
     * 채팅방 목록 조회
     */
    public Set<String> getRoomList() {
        return new HashSet<>(rooms.keySet());
    }

    /**
     * 채팅방 제거
     */
    public void removeRoom(String roomName) {
        rooms.remove(roomName);
        logger.info("채팅방 제거됨: " + roomName);
    }

    /**
     * 채팅방 존재 여부 확인
     */
    public boolean roomExists(String roomName) {
        return rooms.containsKey(roomName);
    }

    /**
     * 차단된 사용자 목록 조회
     */
    public Set<String> getBannedUsers(String roomName, ClientHandler requester) {
        Room room = getRoom(roomName);
        if (room != null && room.isAdmin(requester)) {
            return room.getBannedUsers();
        }
        return new HashSet<>();
    }

    /**
     * 특정 방의 모든 사용자에게 메시지 브로드캐스트
     */
    public void broadcastToRoom(String roomName, String message, ClientHandler sender) {
        Room room = getRoom(roomName);
        if (room != null) {
            room.broadcast(message, sender);
        }
    }
}