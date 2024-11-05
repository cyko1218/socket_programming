// model/User.java
package model;

/**
 * 사용자 정보를 관리하는 클래스
 */
public class User {
    private String nickname;
    private String username;
    private String realname;
    private String host;

    public User(String nickname) {
        this.nickname = nickname;
        this.username = nickname;
        this.realname = nickname;
        this.host = "*";
    }

    // Getters and Setters
    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealname() {
        return realname;
    }

    public void setRealname(String realname) {
        this.realname = realname;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}