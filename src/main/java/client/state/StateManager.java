// client/state/StateManager.java
package client.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

public class StateManager {
    private static final Logger logger = Logger.getLogger(StateManager.class.getName());
    private final Map<String, RoomState> roomStates = new ConcurrentHashMap<>();
    private final List<StateChangeListener> listeners = new ArrayList<>();

    public void addListener(StateChangeListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(StateChangeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void updateRoomState(String roomName, Consumer<RoomState> updater) {
        logger.info("Updating state for room: " + roomName);
        try {
            RoomState state = roomStates.computeIfAbsent(roomName, RoomState::new);
            updater.accept(state);
            notifyStateChange(roomName, state);
            logger.fine("State updated successfully for room: " + roomName);
        } catch (Exception e) {
            logger.severe("Failed to update state for room " + roomName + ": " + e.getMessage());
            throw e;
        }
    }

    public RoomState getRoomState(String roomName) {
        return roomStates.get(roomName);
    }

    public void removeRoom(String roomName) {
        RoomState removedState = roomStates.remove(roomName);
        if (removedState != null) {
            notifyRoomRemoved(roomName);
            logger.info("Removed room state: " + roomName);
        }
    }

    private void notifyStateChange(String roomName, RoomState state) {
        synchronized (listeners) {
            for (StateChangeListener listener : listeners) {
                try {
                    listener.onStateChanged(roomName, state);
                } catch (Exception e) {
                    logger.warning("Failed to notify listener: " + e.getMessage());
                }
            }
        }
    }

    private void notifyRoomRemoved(String roomName) {
        synchronized (listeners) {
            for (StateChangeListener listener : listeners) {
                try {
                    listener.onRoomRemoved(roomName);
                } catch (Exception e) {
                    logger.warning("Failed to notify room removal: " + e.getMessage());
                }
            }
        }
    }

    public Map<String, RoomState> getAllRoomStates() {
        return new ConcurrentHashMap<>(roomStates);
    }

    public void clear() {
        roomStates.clear();
        logger.info("Cleared all room states");
    }
}