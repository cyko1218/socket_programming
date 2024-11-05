// client/state/RoomState.java
package client.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoomState {
    private final String name;
    private volatile String topic;
    private final Set<String> users;
    private volatile boolean isAdmin;
    private volatile boolean hasPassword;
    private volatile int userCount;
    private final Set<String> bannedUsers;

    public RoomState(String name) {
        this.name = name;
        this.users = ConcurrentHashMap.newKeySet();
        this.bannedUsers = ConcurrentHashMap.newKeySet();
        this.topic = "Welcome to " + name;
        this.isAdmin = false;
        this.hasPassword = false;
        this.userCount = 0;
    }

    // Basic getters
    public String getName() { return name; }
    public String getTopic() { return topic; }
    public Set<String> getUsers() { return users; }
    public boolean isAdmin() { return isAdmin; }
    public boolean hasPassword() { return hasPassword; }
    public int getUserCount() { return userCount; }
    public Set<String> getBannedUsers() { return bannedUsers; }

    // Atomic update methods
    public void updateTopic(String newTopic) {
        this.topic = newTopic;
    }

    public void updateUsers(List<String> newUsers) {
        users.clear();
        users.addAll(newUsers);
        this.userCount = users.size();
    }

    public void addUser(String user) {
        users.add(user);
        this.userCount = users.size();
    }

    public void removeUser(String user) {
        users.remove(user);
        this.userCount = users.size();
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
    }

    public void setHasPassword(boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public void addBannedUser(String user) {
        bannedUsers.add(user);
    }

    public void removeBannedUser(String user) {
        bannedUsers.remove(user);
    }

    @Override
    public String toString() {
        return String.format("RoomState{name='%s', topic='%s', users=%d, admin=%b, hasPassword=%b}",
                name, topic, users.size(), isAdmin, hasPassword);
    }
}