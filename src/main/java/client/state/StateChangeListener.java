// client/state/StateChangeListener.java
package client.state;

public interface StateChangeListener {
    void onStateChanged(String roomName, RoomState state);
    void onRoomRemoved(String roomName);
}