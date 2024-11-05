package model;

import server.ClientHandler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Room {
    private final String name;
    private final Set<ClientHandler> members;
    private final Set<String> bannedUsers;  // 차단된 사용자 닉네임 목록
    private ClientHandler admin;            // 방장
    private String topic;
    private final int maxUsers;
    private String password;                // 비밀번호
    private static final Logger logger = Logger.getLogger(Room.class.getName());

    public Room(String name) {
        this.name = name;
        this.members = ConcurrentHashMap.newKeySet();
        this.bannedUsers = ConcurrentHashMap.newKeySet();
        this.maxUsers = 50;
        this.topic = "Welcome to " + name;
    }
    // 새로운 생성자 추가
    public Room(String name, ClientHandler creator) {
        this(name);  // 기본 생성자 호출
        this.admin = creator;
        this.members.add(creator);
    }

    public void broadcast(String message, ClientHandler sender) {
        // sender가 null일 경우 서버 메시지로 처리
        String prefix = (sender != null) ?
                ":" + sender.getNickname() : ":server";

        System.out.println("Room: broadcasting message " +
                (sender != null ? "from " + sender.getNickname() : "from server") +
                ": " + message);

        for (ClientHandler member : members) {
            try {
                // 일반 채팅 메시지와 시스템 메시지 구분
                String formattedMessage;
                if (message.startsWith(":") || message.startsWith("NOTICE") ||
                        message.startsWith("PART") || message.startsWith("KICK") ||
                        message.startsWith("MODE")) {
                    formattedMessage = message;  // 이미 포맷된 메시지
                } else {
                    formattedMessage = prefix + " PRIVMSG " + name + " :" + message;
                }

                member.sendMessage(formattedMessage);
            } catch (Exception e) {
                System.out.println("Room: error sending to " + member.getNickname());
                e.printStackTrace();
            }
        }
    }

    public boolean addMember(ClientHandler client, String providedPassword) {
        // 차단된 사용자 검사
        if (bannedUsers.contains(client.getNickname())) {
            client.sendMessage(":server 474 " + client.getNickname() + " " + name + " :Cannot join channel (+b)");
            return false;
        }

        // 비밀번호 확인 (비밀번호가 설정된 경우에만)
        if (password != null && !password.equals(providedPassword)) {
            client.sendMessage(":server 475 " + client.getNickname() + " " + name + " :Cannot join channel (+k)");
            return false;
        }
        // 이미 멤버로 존재하는 경우 중복 입장 방지
        if (members.contains(client)) {
            client.sendMessage(":server 443 " + name + " :You are already in the channel");
            return false;
        }

        if (members.size() < maxUsers) {
            if (members.add(client)) {
                // 새 멤버에게 토픽과 멤버 목록 정보 전송
                client.sendMessage(":server 332 " + client.getNickname() + " " + name + " :" + topic);

                // 현재 멤버 목록 전송
                StringBuilder userList = new StringBuilder();
                for (ClientHandler member : members) {
                    if (member == admin) {
                        userList.append("@");  // 방장 표시
                    }
                    userList.append(member.getNickname()).append(" ");
                }
                client.sendMessage(":server 353 " + client.getNickname() + " = " + name + " :" + userList);
                client.sendMessage(":server 366 " + client.getNickname() + " " + name + " :End of /NAMES list");

                return true;
            }
        }

        // 방 입장 실패시
        client.sendMessage(":server 471 " + name + " :Cannot join channel");
        return false;
    }

    public void removeMember(ClientHandler client) {
        if (members.remove(client)) {
            // 방장이 나가는 경우 다음 사람에게 방장 위임
            if (client == admin && !members.isEmpty()) {
                admin = members.iterator().next();
                broadcast(":server NOTICE " + name + " :" + admin.getNickname() +
                        " is now channel operator", null);
            }

            // PART 메시지 전송
            String partMessage = ":" + client.getNickname() + " PART " + name;
            for (ClientHandler member : members) {
                member.sendMessage(partMessage);
            }
            // 퇴장하는 사용자에게도 메시지 전송
            client.sendMessage(partMessage);

            logger.info("User " + client.getNickname() + " has left channel " + name);
        }
    }

    // 방장 권한 확인
    public boolean isAdmin(ClientHandler client) {
        return client == admin;
    }

    // 강퇴 기능
    public boolean kickUser(ClientHandler target, ClientHandler executor) {
        if (!isAdmin(executor)) {
            executor.sendMessage(":server 482 " + executor.getNickname() + " " + name + " :You're not channel operator");
            logger.warning("Kick command failed: " + executor.getNickname() + " is not the channel operator.");
            return false;
        }
        if (target == admin) {
            executor.sendMessage(":server 482 " + executor.getNickname() + " " + name + " :Cannot kick channel operator");
            logger.warning("Kick command failed: Cannot kick the channel operator " + target.getNickname());
            return false;
        }
        if (members.remove(target)) {
            String kickMessage = ":" + executor.getNickname() + " KICK " + name + " " + target.getNickname();
            broadcast(kickMessage, null);
            target.sendMessage(kickMessage);
            logger.info("User " + target.getNickname() + " has been kicked from channel " + name + " by " + executor.getNickname());
            return true;
        }
        logger.warning("Kick command failed: User " + target.getNickname() + " not found in channel " + name);
        return false;
    }

    // 차단 기능
    public boolean banUser(ClientHandler target, ClientHandler executor) {
        if (!isAdmin(executor)) {
            executor.sendMessage(":server 482 " + executor.getNickname() + " " + name + " :You're not channel operator");
            logger.warning("Ban command failed: " + executor.getNickname() + " is not the channel operator.");
            return false;
        }
        if (target == admin) {
            executor.sendMessage(":server 482 " + executor.getNickname() + " " + name + " :Cannot ban channel operator");
            logger.warning("Ban command failed: Cannot ban the channel operator " + target.getNickname());
            return false;
        }
        if (bannedUsers.add(target.getNickname())) {
            kickUser(target, executor);  // 차단 시 강퇴도 함께 수행
            broadcast(":server MODE " + name + " +b " + target.getNickname(), null);
            logger.info("User " + target.getNickname() + " has been banned from channel " + name + " by " + executor.getNickname());
            return true;
        }
        logger.warning("Ban command failed: User " + target.getNickname() + " is already banned in channel " + name);
        return false;
    }

    // 차단 해제
    public boolean unbanUser(String nickname, ClientHandler executor) {
        if (!isAdmin(executor)) {
            executor.sendMessage(":server 482 " + executor.getNickname() + " " + name + " :You're not channel operator");
            return false;
        }
        if (bannedUsers.remove(nickname)) {
            broadcast(":server MODE " + name + " -b " + nickname, null);
            return true;
        }
        return false;
    }

    // 비밀번호 설정
    public boolean setPassword(String newPassword, ClientHandler executor) {
        if (!isAdmin(executor)) {
            executor.sendMessage(":server 482 " + executor.getNickname() + " " + name + " :You're not channel operator");
            logger.warning("Set password command failed: " + executor.getNickname() + " is not the channel operator.");
            return false;
        }
        this.password = newPassword;
        if (newPassword != null) {
            broadcast(":server MODE " + name + " +k " + newPassword, null);
        } else {
            broadcast(":server MODE " + name + " -k", null);
        }
        logger.info("Channel " + name + " password changed to: " + (newPassword != null ? newPassword : "none") + " by " + executor.getNickname());
        return true;
    }

    // Getter 메서드들
    public String getName() {
        return name;
    }

    public String getTopic() {
        return topic;
    }

    public Set<ClientHandler> getMembers() {
        return members;
    }

    public int getUserCount() {
        return members.size();
    }

    public Set<String> getBannedUsers() {
        return bannedUsers;
    }

    public void setTopic(String newTopic, ClientHandler setter) {
        // 방장만 토픽 변경 가능하도록 수정
        if (!isAdmin(setter)) {
            setter.sendMessage(":server 482 " + setter.getNickname() + " " + name + " :You're not channel operator");
            return;
        }

        this.topic = newTopic;
        String topicMessage = String.format(":server 332 * %s :%s", name, newTopic);
        String setterInfo = String.format(":%s!%s@server TOPIC %s :%s",
                setter.getNickname(), setter.getNickname(), name, newTopic);

        for (ClientHandler member : members) {
            member.sendMessage(topicMessage);
            member.sendMessage(setterInfo);
        }

        logger.info(String.format("Channel %s topic changed to: %s by %s",
                name, newTopic, setter.getNickname()));
    }
}