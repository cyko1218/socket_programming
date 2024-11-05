package client;

import java.util.ArrayList;
import java.util.List;

// client/RoomInfo.java
public class RoomInfo {
    private String name;
    private String topic;
    private List<String> users;
    private boolean isAdmin;
    private boolean hasPassword;

    public RoomInfo(String name) {
        this.name = name;
        this.users = new ArrayList<>();
        this.topic = "No topic set";
        this.isAdmin = false;
        this.hasPassword = false;
    }

    // getters and setters
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public List<String> getUsers() { return users; }
    public void setUsers(List<String> users) { this.users = new ArrayList<>(users); }
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }
    public int getUserCount() { return users.size(); }

    public void setHasPassword(boolean hasPassword) {
        this.hasPassword = hasPassword;
    }
    public boolean hasPassword() {
        return hasPassword;
    }
}