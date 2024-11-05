package client;

import client.state.RoomState;
import client.state.StateChangeListener;
import filetransfer.client.FileTransferManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClientUI extends JFrame implements MessageListener, StateChangeListener {
    private IRCClient client;
    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JList<String> roomList;
    private final DefaultListModel<String> roomListModel;
    private String currentRoom;
    private final JList<String> userList;
    private final DefaultListModel<String> userListModel;
    private final JPopupMenu channelPopupMenu;
    private final JButton kickButton;
    private final JButton banButton;
    private final JButton unbanButton;
    private final JLabel currentTopicLabel;
    private final JLabel userCountLabel;
    private final JLabel topicLabel;
    private final JPanel buttonPanel;

    private JMenuItem setPasswordItem;
    private JMenuItem removePasswordItem;
    private JMenuItem showBanListItem;

    private FileTransferManager fileManager;

    public ClientUI(String host, int port) {
        super("IRC Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);

        // UI 컴포넌트 초기화
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        channelPopupMenu = new JPopupMenu();
        initializeContextMenu();

        inputField = new JTextField();
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 라벨 초기화
        currentTopicLabel = new JLabel("No topic set");
        userCountLabel = new JLabel("Users: 0");
        topicLabel = new JLabel("Topic: ");


        // 관리자 버튼 초기화
        kickButton = new JButton("Kick User");
        banButton = new JButton("Ban User");
        unbanButton = new JButton("Unban User");

        // 초기에는 비활성화
        kickButton.setEnabled(false);
        banButton.setEnabled(false);
        unbanButton.setEnabled(false);

        buttonPanel = createButtonPanel();

        userList.setCellRenderer(new UserListRenderer());
        roomList.setCellRenderer(new RoomListRenderer());

        // FileTransferManager 초기화
        try {
            fileManager = new FileTransferManager(host, 21);
            fileManager.connect();  // login 호출 제거
        } catch (IOException e) {
            appendMessage("Failed to initialize file transfer: " + e.getMessage());
        }

        setupLayout();
        setupEventHandlers();
        connectToServer(host, port);

        setVisible(true);
    }

    private void initializeContextMenu() {
        JMenuItem setPasswordItem = new JMenuItem("Set Password");
        JMenuItem removePasswordItem = new JMenuItem("Remove Password");
        JMenuItem showBanListItem = new JMenuItem("Show Ban List");

        setPasswordItem.addActionListener(e -> showSetPasswordDialog());
        removePasswordItem.addActionListener(e -> removeChannelPassword());
        showBanListItem.addActionListener(e -> showBanList());

        channelPopupMenu.add(setPasswordItem);
        channelPopupMenu.add(removePasswordItem);
        channelPopupMenu.add(showBanListItem);
    }

    private void initializeFileTransfer(String host, int port) {
        try {
            fileManager = new FileTransferManager(host, 21);
            fileManager.connect();  // connect만 호출
        } catch (IOException e) {
            appendMessage("Note: File transfer initialization pending... " + e.getMessage());
        }
    }


    public void updateAdminControls() {
        boolean isAdmin = client != null && client.isAdmin();
        System.out.println("Updating admin controls. Is admin: " + isAdmin); // 디버그 로그

        SwingUtilities.invokeLater(() -> {
            kickButton.setEnabled(isAdmin);
            banButton.setEnabled(isAdmin);

            for (Component comp : channelPopupMenu.getComponents()) {
                if (comp instanceof JMenuItem) {
                    JMenuItem menuItem = (JMenuItem) comp;
                    switch (menuItem.getText()) {
                        case "Set Password":
                        case "Remove Password":
                        case "Show Ban List":
                            menuItem.setEnabled(isAdmin);
                            break;
                    }
                }
            }
        });
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // 채널 정보 패널 (Topic & Users count) - NORTH
        JPanel infoPanel = new JPanel(new BorderLayout());
        JPanel topicPanel = new JPanel(new BorderLayout());

        topicPanel.add(topicLabel, BorderLayout.WEST);
        topicPanel.add(currentTopicLabel, BorderLayout.CENTER);

        JPanel userCountPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        userCountPanel.add(userCountLabel);

        infoPanel.add(topicPanel, BorderLayout.CENTER);
        infoPanel.add(userCountPanel, BorderLayout.EAST);

        // 메인 채팅 영역 - CENTER
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(infoPanel, BorderLayout.NORTH);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        centerPanel.add(chatScrollPane, BorderLayout.CENTER);
        centerPanel.add(inputField, BorderLayout.SOUTH);

        // 오른쪽 패널 (채널 목록 & 사용자 목록) - EAST
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 파일 전송 패널을 상단에 추가
        JPanel fileTransferPanel = createFileTransferPanel();
        rightPanel.add(fileTransferPanel, BorderLayout.NORTH);

        // 채널과 사용자 목록을 중앙에 배치
        JSplitPane listsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // 채널 목록 패널
        JPanel channelPanel = new JPanel(new BorderLayout());
        channelPanel.add(new JLabel("Channels"), BorderLayout.NORTH);
        channelPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);

        // 사용자 목록 패널
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.add(new JLabel("Users"), BorderLayout.NORTH);
        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);


        listsSplitPane.setTopComponent(channelPanel);
        listsSplitPane.setBottomComponent(userPanel);
        listsSplitPane.setResizeWeight(0.5);

        rightPanel.add(listsSplitPane, BorderLayout.CENTER);
        rightPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 0, 5));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        buttonPanel.add(createButton("Join Channel", e -> showJoinDialog()));
        buttonPanel.add(createButton("Leave Channel", e -> leaveSelectedChannel()));
        buttonPanel.add(createButton("Change Nickname", e -> showNickDialog()));
        buttonPanel.add(createButton("Set Topic", e -> showTopicDialog()));
        buttonPanel.add(createButton("Send Whisper", e -> showWhisperDialog()));

        buttonPanel.add(kickButton);
        buttonPanel.add(banButton);
        buttonPanel.add(unbanButton);

        return buttonPanel;
    }

    private JButton createButton(String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        return button;
    }


    private void setupEventHandlers() {
        inputField.addActionListener(e -> sendMessage());

        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = roomList.getSelectedValue();
                if (selected != null) {
                    currentRoom = selected;
                    setTitle("IRC Client - " + currentRoom);
                    updateUserList(); // 사용자 목록 업데이트
                }
            }
        });

        // 채널 목록 컨텍스트 메뉴
        roomList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    handlePopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    handlePopup(e);
                }
            }
        });

        // 관리자 기능 버튼 이벤트 핸들러
        kickButton.addActionListener(e -> showKickDialog());
        banButton.addActionListener(e -> showBanDialog());
        unbanButton.addActionListener(e -> showUnbanDialog());  // unban 버튼 추가

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (fileManager != null) {
                    fileManager.shutdown();
                }
                disconnect();
            }
        });
    }


    private void updateUserList() {
        if (currentRoom != null) {
            // 서버에 채널의 사용자 목록을 요청
            client.sendMessage("NAMES " + currentRoom);
        }
    }


    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            if (message.startsWith("/")) {
                handleCommand(message);
            } else if (currentRoom != null) {
                client.sendPrivateMessage(currentRoom, message);
                appendMessage("Me: " + message);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Please join a channel first or use /w for whisper",
                        "No Channel Selected",
                        JOptionPane.WARNING_MESSAGE);
            }
            inputField.setText("");
        }
    }

    private void handleCommand(String command) {
        String[] parts = command.substring(1).split(" ", 3); // 3개로 분할 (명령어, 대상, 메시지)
        if (parts.length == 0) return;

        try {
            switch (parts[0].toUpperCase()) {
                case "W":
                case "WHISPER":
                    if (parts.length >= 3) {
                        String target = parts[1];
                        String message = parts[2];
                        client.sendPrivateMessage(target, message);
                    } else {
                        appendMessage("Usage: /w <nickname> <message>");
                    }
                    break;

                case "JOIN":
                    if (parts.length > 1) {
                        String channel = parts[1];
                        if (!channel.startsWith("#")) {
                            channel = "#" + channel;
                        }

                        // 비밀번호가 필요한 경우 처리
                        String password = null;
                        if (parts.length > 2) {
                            password = parts[2];
                        } else {
                            // 서버로부터 475 응답을 받은 경우 비밀번호 입력 요청
                            password = JOptionPane.showInputDialog(
                                    this,
                                    "This channel requires a password. Please enter it:",
                                    "Channel Password Required",
                                    JOptionPane.QUESTION_MESSAGE
                            );
                        }

                        if (password != null) {
                            client.joinRoom(channel + " " + password);
                        } else {
                            client.joinRoom(channel);
                        }

                        if (!roomListModel.contains(channel)) {
                            roomListModel.addElement(channel);
                        }
                        roomList.setSelectedValue(channel, true);
                    }
                    break;

                case "PART":
                case "LEAVE":
                    if (parts.length > 1) {
                        String channel = parts[1];
                        if (!channel.startsWith("#")) {
                            channel = "#" + channel;
                        }
                        leaveChannel(channel);
                    } else if (currentRoom != null) {
                        leaveChannel(currentRoom);
                    }
                    break;

                case "NICK":
                    if (parts.length > 1) {
                        client.setNickname(parts[1]);
                    }
                    break;

                case "TOPIC":
                    handleTopicCommand(parts);
                    break;

                case "KICK":
                    if (parts.length >= 2) {
                        String target = parts[1];
                        String reason = parts.length > 2 ? parts[2] : "";
                        if (currentRoom != null) {
                            // KICK 명령어 형식 변환: KICK #channel target [reason]
                            String kickCommand = String.format("KICK %s %s%s",
                                    currentRoom,
                                    target,
                                    reason.isEmpty() ? "" : " :" + reason);
                            client.sendCommand(kickCommand);
                        } else {
                            appendMessage("Error: You must be in a channel to use KICK");
                        }
                    } else {
                        appendMessage("Usage: /kick <nickname> [reason]");
                    }
                    break;
                case "MODE":
                    if (currentRoom == null) {
                        appendMessage("Error: You must be in a channel to set mode");
                        return;
                    }

                    if (parts.length < 2) {
                        appendMessage("Usage: /mode <+/-><mode> [parameter]");
                        return;
                    }

                    String modeString = parts[1];
                    String parameter = parts.length > 2 ? parts[2] : null;

                    // MODE 명령어 형식 변환: MODE #channel +k password 또는 MODE #channel -k
                    StringBuilder modeCommand = new StringBuilder();
                    modeCommand.append("MODE ").append(currentRoom).append(" ").append(modeString);
                    if (parameter != null) {
                        modeCommand.append(" ").append(parameter);
                    }

                    client.sendCommand(modeCommand.toString());
                    break;
                case "BAN":
                    if (currentRoom == null) {
                        appendMessage("Error: You must be in a channel to ban users");
                        return;
                    }
                    if (parts.length < 2) {
                        appendMessage("Usage: /ban <nickname>");
                        return;
                    }
                    // BAN 명령어 전송: BAN #channel nickname
                    client.sendCommand("BAN " + currentRoom + " " + parts[1]);
                    break;

                case "UNBAN":
                    if (currentRoom == null) {
                        appendMessage("Error: You must be in a channel to unban users");
                        return;
                    }
                    if (parts.length < 2) {
                        appendMessage("Usage: /unban <nickname>");
                        return;
                    }
                    // UNBAN 명령어 전송: UNBAN #channel nickname
                    client.sendCommand("UNBAN " + currentRoom + " " + parts[1]);
                    break;
                case "LIST":
                    // 서버에 채널 목록 요청
                    client.sendCommand("LIST");
                    appendMessage("Requesting channel list...");
                    break;
                default:
                    appendMessage("Unknown command: " + command);
            }
        } catch (Exception e) {
            appendMessage("Error executing command: " + e.getMessage());
        }
    }

    private void leaveChannel(String channel) {
        client.leaveRoom(channel);
        roomListModel.removeElement(channel);
        if (channel.equals(currentRoom)) {
            currentRoom = null;
            setTitle("IRC Client");
        }
    }


    private void showJoinDialog() {
        String channelName = JOptionPane.showInputDialog(
                this,
                "Enter channel name to join:",
                "Join Channel",
                JOptionPane.QUESTION_MESSAGE
        );
        if (channelName != null && !channelName.trim().isEmpty()) {
            if (!channelName.startsWith("#")) {
                channelName = "#" + channelName;
            }
            client.joinRoom(channelName);
            if (!roomListModel.contains(channelName)) {
                roomListModel.addElement(channelName);
            }
            roomList.setSelectedValue(channelName, true);
        }
    }

    private void showWhisperDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));

        JTextField nicknameField = new JTextField();
        JTextField messageField = new JTextField();

        panel.add(new JLabel("Recipient's nickname:"));
        panel.add(nicknameField);
        panel.add(new JLabel("Message:"));
        panel.add(messageField);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Send Whisper",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String nickname = nicknameField.getText().trim();
            String message = messageField.getText().trim();

            if (!nickname.isEmpty() && !message.isEmpty()) {
                client.sendPrivateMessage(nickname, message);
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Both nickname and message are required",
                        "Invalid Input",
                        JOptionPane.WARNING_MESSAGE
                );
            }
        }
    }

    private void leaveSelectedChannel() {
        String selectedChannel = roomList.getSelectedValue();
        if (selectedChannel != null) {
            leaveChannel(selectedChannel);
        }
    }

    private void showNickDialog() {
        String nickname = JOptionPane.showInputDialog(
                this,
                "Enter your nickname:",
                "Set Nickname",
                JOptionPane.QUESTION_MESSAGE
        );
        if (nickname != null && !nickname.trim().isEmpty()) {
            client.setNickname(nickname);
        }
    }

    private void showTopicDialog() {
        if (currentRoom == null) {
            JOptionPane.showMessageDialog(this,
                    "Please join a channel first",
                    "No Channel Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String newTopic = JOptionPane.showInputDialog(
                this,
                "Enter new topic for " + currentRoom + ":",
                "Set Channel Topic",
                JOptionPane.QUESTION_MESSAGE
        );

        if (newTopic != null && !newTopic.trim().isEmpty()) {
            client.setTopic(currentRoom, newTopic.trim());
        }
    }

    private void handleTopicCommand(String[] parts) {
        if (currentRoom == null) {
            appendMessage("Error: Please join a channel first");
            return;
        }

        if (parts.length > 1) {
            // 토픽 설정
            client.setTopic(currentRoom, parts[1]);
            appendMessage("Attempting to set topic for " + currentRoom);
        } else {
            // 토픽 조회
            client.queryTopic(currentRoom);
            appendMessage("Querying topic for " + currentRoom);
        }
    }

    private void handlePopup(MouseEvent e) {
        // 마우스 클릭된 위치에서 선택된 항목 확인
        int index = roomList.locationToIndex(e.getPoint());
        if (index != -1) {
            roomList.setSelectedIndex(index);
            String selectedRoom = roomList.getModel().getElementAt(index);

            // 현재 방에 대해서만 컨텍스트 메뉴 표시
            if (selectedRoom != null && selectedRoom.equals(currentRoom)) {
                // 방장 권한이 있는 경우에만 특정 메뉴 아이템 활성화
                boolean isAdmin = client != null && client.isAdmin();
                for (Component component : channelPopupMenu.getComponents()) {
                    if (component instanceof JMenuItem) {
                        JMenuItem menuItem = (JMenuItem) component;
                        switch (menuItem.getText()) {
                            case "Set Password":
                            case "Remove Password":
                            case "Show Ban List":
                                menuItem.setEnabled(isAdmin);
                                break;
                        }
                    }
                }
                // 마우스 클릭 위치에 팝업 메뉴 표시
                channelPopupMenu.show(roomList, e.getX(), e.getY());
            }
        }
    }

    private void handleRoomChange(String newRoom) {
        if (newRoom != null && !newRoom.equals(currentRoom)) {
            currentRoom = newRoom;
            setTitle("IRC Client - " + currentRoom);

            RoomState state = client.getStateManager().getRoomState(currentRoom);
            if (state != null) {
                currentTopicLabel.setText(state.getTopic());
                userCountLabel.setText("Users: " + state.getUserCount());

                userListModel.clear();
                for (String user : state.getUsers()) {
                    userListModel.addElement(user);
                }

                updateAdminControls(state.isAdmin());
            }
        }
    }

    @Override
    public void onMessageReceived(String message) {
        appendMessage(message);

        // MODE 메시지를 통한 방장 권한 변경 확인
        if (message.contains("MODE") && message.contains("+o")) {
            client.setAdmin(true);
            updateAdminControls();
        } else if (message.contains("MODE") && message.contains("-o")) {
            client.setAdmin(false);
            updateAdminControls();
        }

        // 토픽 업데이트 처리
        if (message.contains("332") || (message.contains("TOPIC") && message.contains("#"))) {
            String[] parts = message.split(":", 2);
            if (parts.length > 1) {
                String topic = parts[1].trim();
                SwingUtilities.invokeLater(() -> {
                    currentTopicLabel.setText("Topic: " + topic);
                });
                if (currentRoom != null) {
                    // List<String> 타입으로 수정
                    List<String> currentUsers = new ArrayList<>();
                    for (int i = 0; i < userListModel.size(); i++) {
                        currentUsers.add(userListModel.getElementAt(i));
                    }
                    client.updateRoomInfo(currentRoom, topic, currentUsers);
                }
            }
        }

        // NAMES 응답 처리 (353)
        if (message.contains("353")) {
            String[] parts = message.split("[:=]");  // : 또는 = 으로 분할
            if (parts.length > 2) {  // 채널명과 사용자 목록이 있는 경우
                String channelPart = parts[1].trim();
                String[] channelParts = channelPart.split("\\s+");
                String channel = null;
                // 채널명 찾기 (# 으로 시작하는 부분)
                for (String part : channelParts) {
                    if (part.startsWith("#")) {
                        channel = part;
                        break;
                    }
                }

                if (channel != null) {
                    String[] users = parts[2].trim().split("\\s+");
                    List<String> userList = new ArrayList<>(Arrays.asList(users));

                    // 상태 업데이트
                    RoomState state = client.getStateManager().getRoomState(channel);
                    if (state != null) {
                        state.updateUsers(userList);
                    }

                    // UI 업데이트
                    updateUserList(userList);
                }
            }
        }

        // TOPIC 응답 처리 (332)
        if (message.contains("332")) {
            String[] parts = message.split(":", 2);
            if (parts.length > 1) {
                String[] headerParts = parts[0].split(" ");
                String channel = headerParts[headerParts.length - 1];
                String topic = parts[1].trim();
                currentTopicLabel.setText("Topic: " + topic);  // 토픽 라벨 업데이트
                updateRoomInfo(channel, topic, userListModel.size());
            }
        }

        // 방 입장/퇴장 메시지 처리
        if (message.contains("JOIN") || message.contains("PART")) {
            // 사용자 목록 새로고침 요청
            if (currentRoom != null) {
                client.sendMessage("NAMES " + currentRoom);
            }
        }

        // 비밀번호가 필요한 채널 처리
        if (message.contains("475")) {
            String[] parts = message.split(" ");
            if (parts.length >= 4) {
                String channel = parts[3];
                String password = JOptionPane.showInputDialog(
                        this,
                        "This channel requires a password. Please enter it:",
                        "Channel Password Required",
                        JOptionPane.QUESTION_MESSAGE
                );

                if (password != null && !password.trim().isEmpty()) {
                    client.joinRoom(channel + " " + password.trim());
                }
            }
        }

        // 일반 TOPIC 메시지 처리
        if (message.contains("TOPIC")) {
            String[] parts = message.split(":", 2);
            if (parts.length > 1) {
                String topic = parts[1].trim();
                currentTopicLabel.setText("Topic: " + topic);  // 토픽 라벨 업데이트
            }
        }

    }

    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void connectToServer(String host, int port) {
        try {
            client = new IRCClient(host, port);
            client.setMessageListener(this);
            client.addStateChangeListener((StateChangeListener) this);  // StateChangeListener 등록
            client.start();
            showNickDialog();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to connect to server: " + e.getMessage(),
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }


    private void disconnect() {
        if (client != null) {
            client.disconnect();
        }
        dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ClientUI("localhost", 6667);
        });
    }

    private void showKickDialog() {
        if (currentRoom == null || !client.isAdmin()) {
            System.out.println("Cannot kick: room is null or not admin"); // 디버그 로그
            return;
        }

        System.out.println("Attempting to show kick dialog, isAdmin: " + client.isAdmin()); // 디버그 로그

        String[] users = new String[userListModel.getSize()];
        for (int i = 0; i < userListModel.getSize(); i++) {
            String user = userListModel.getElementAt(i);
            // @ 기호 제거
            users[i] = user.startsWith("@") ? user.substring(1) : user;
        }

        if (users.length == 0) {
            JOptionPane.showMessageDialog(this, "No users to kick");
            return;
        }

        String selectedUser = (String) JOptionPane.showInputDialog(
                this,
                "Select user to kick:",
                "Kick User",
                JOptionPane.QUESTION_MESSAGE,
                null,
                users,
                users[0]
        );

        if (selectedUser != null) {
            System.out.println("Sending kick command for user: " + selectedUser); // 디버그 로그
            client.kickUser(currentRoom, selectedUser);
        }
    }

    private void showBanDialog() {
        if (currentRoom == null || !client.isAdmin()) {
            appendMessage("Error: You must be channel operator to ban users");
            return;
        }

        // 현재 채널의 사용자 목록 가져오기
        String[] users = new String[userListModel.getSize()];
        for (int i = 0; i < userListModel.getSize(); i++) {
            String user = userListModel.getElementAt(i);
            // @ 기호 제거 (방장 표시)
            users[i] = user.startsWith("@") ? user.substring(1) : user;
        }

        if (users.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "No users to ban",
                    "Empty User List",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedUser = (String) JOptionPane.showInputDialog(
                this,
                "Select user to ban:",
                "Ban User",
                JOptionPane.QUESTION_MESSAGE,
                null,
                users,
                users[0]
        );

        if (selectedUser != null) {
            // BAN 명령어 전송
            client.sendCommand("BAN " + currentRoom + " " + selectedUser);
        }
    }

    // Unban 대화상자 표시 메서드
    private void showUnbanDialog() {
        if (currentRoom == null || !client.isAdmin()) {
            appendMessage("Error: You must be channel operator to unban users");
            return;
        }

        String bannedUser = JOptionPane.showInputDialog(
                this,
                "Enter nickname to unban:",
                "Unban User",
                JOptionPane.QUESTION_MESSAGE
        );

        if (bannedUser != null && !bannedUser.trim().isEmpty()) {
            client.sendCommand("UNBAN " + currentRoom + " " + bannedUser.trim());
        }
    }

    private void showSetPasswordDialog() {
        if (currentRoom == null) return;

        String password = JOptionPane.showInputDialog(
                this,
                "Enter new password for " + currentRoom + ":",
                "Set Channel Password",
                JOptionPane.QUESTION_MESSAGE
        );

        if (password != null) {
            if (password.trim().isEmpty()) {
                // 비밀번호 제거
                client.sendCommand("MODE " + currentRoom + " -k");
            } else {
                // 비밀번호 설정
                client.sendCommand("MODE " + currentRoom + " +k " + password.trim());
            }
        }
    }


    public void onStateChanged(String roomName, RoomState state) {
        SwingUtilities.invokeLater(() -> {
            // 채널 목록 업데이트
            if (!roomListModel.contains(roomName)) {
                roomListModel.addElement(roomName);
            }

            // 현재 선택된 방에 대한 정보 업데이트
            if (roomName.equals(currentRoom)) {
                // 토픽 업데이트
                currentTopicLabel.setText(state.getTopic());

                // 사용자 목록 업데이트
                userListModel.clear();
                for (String user : state.getUsers()) {
                    userListModel.addElement(user);
                }

                // 사용자 수 업데이트
                userCountLabel.setText("Users: " + state.getUserCount());

                // 관리자 권한에 따른 버튼 상태 업데이트
                updateAdminControls(state.isAdmin());
            }

            // 채널 목록 렌더링 갱신
            roomList.repaint();
        });
    }

    public void onRoomRemoved(String roomName) {
        SwingUtilities.invokeLater(() -> {
            roomListModel.removeElement(roomName);
            if (roomName.equals(currentRoom)) {
                currentRoom = null;
                setTitle("IRC Client");
                currentTopicLabel.setText("No topic set");
                userCountLabel.setText("Users: 0");
                userListModel.clear();
                updateAdminControls(false);
            }
        });
    }

    private void showFileUploadDialog() {
        if (currentRoom == null) {
            JOptionPane.showMessageDialog(this,
                    "Please join a channel first",
                    "No Channel Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String filename = selectedFile.getName();

            // 진행 상황을 보여줄 다이얼로그
            JProgressBar progressBar = new JProgressBar(0, 100);
            JDialog progressDialog = new JDialog(this, "Uploading File", true);
            progressDialog.add(progressBar);
            progressDialog.setSize(300, 75);
            progressDialog.setLocationRelativeTo(this);

            fileManager.uploadFile(filename, new FileTransferManager.TransferCallback() {
                @Override
                public void onProgress(int percentage) {
                    SwingUtilities.invokeLater(() -> progressBar.setValue(percentage));
                }

                @Override
                public void onSuccess(String message) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        // 채널에 파일 공유 메시지 전송
                        client.sendChatMessage("Shared file: " + filename);
                    });
                }

                @Override
                public void onError(String error) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(ClientUI.this,
                                "Upload failed: " + error,
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
            });

            progressDialog.setVisible(true);
        }
    }

    private void showFileDownloadDialog() {
        // 파일 목록을 가져와서 선택할 수 있는 다이얼로그 표시
        fileManager.listFiles(new FileTransferManager.TransferCallback() {
            @Override
            public void onProgress(int percentage) {
            }

            @Override
            public void onSuccess(String message) {
                // 파일 선택 다이얼로그 표시
                // 실제 구현에서는 파일 목록을 파싱하여 표시해야 함
            }

            @Override
            public void onError(String error) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(ClientUI.this,
                            "Failed to list files: " + error,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void removeChannelPassword() {
        if (currentRoom == null) return;
        client.sendCommand("MODE " + currentRoom + " -k");
    }

    private void showBanList() {
        if (currentRoom == null) return;
        client.sendCommand("MODE " + currentRoom + " +b");
    }

    // 방장 권한 확인
    public void updateAdminControls(boolean isAdmin) {
        SwingUtilities.invokeLater(() -> {
            kickButton.setEnabled(isAdmin);
            banButton.setEnabled(isAdmin);
            unbanButton.setEnabled(isAdmin);

            for (Component comp : channelPopupMenu.getComponents()) {
                if (comp instanceof JMenuItem) {
                    JMenuItem menuItem = (JMenuItem) comp;
                    switch (menuItem.getText()) {
                        case "Set Password":
                        case "Remove Password":
                        case "Show Ban List":
                            menuItem.setEnabled(isAdmin);
                            break;
                    }
                }
            }
        });
    }

    // updateUserList 메소드 수정
    public void updateUserList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();

            // 방장을 맨 위로, 일반 사용자를 그 아래로 정렬
            List<String> sortedUsers = new ArrayList<>(users);
            sortedUsers.sort((a, b) -> {
                boolean aIsOp = a.startsWith("@");
                boolean bIsOp = b.startsWith("@");
                if (aIsOp && !bIsOp) return -1;
                if (!aIsOp && bIsOp) return 1;
                return a.replace("@", "").compareTo(b.replace("@", ""));
            });

            for (String user : sortedUsers) {
                userListModel.addElement(user);
            }

            if (currentRoom != null) {
                RoomState state = client.getStateManager().getRoomState(currentRoom);
                if (state != null) {
                    userCountLabel.setText("Users: " + users.size());
                }
            }

            userList.repaint();
        });
    }

    // UserListRenderer 수정
    private class UserListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            String user = value.toString();
            if (user.startsWith("@")) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setForeground(new Color(0, 100, 0));
            }

            return label;
        }
    }

    private class RoomListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            String roomName = value.toString();
            RoomState state = client.getStateManager().getRoomState(roomName);
            if (state != null) {
                String topic = state.getTopic() != null ? state.getTopic() : "No topic set";
                label.setText(String.format("%s (%s) (%d users)",
                        roomName, topic, state.getUserCount()));

                if (roomName.equals(currentRoom)) {
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                }
            }

            return label;
        }
    }

    public void updateRoomInfo(String roomName, String topic, int userCount) {
        RoomState state = client.getStateManager().getRoomState(roomName);
        if (state != null) {
            state.updateTopic(topic);
            SwingUtilities.invokeLater(() -> {
                if (roomName.equals(currentRoom)) {
                    // 토픽 업데이트
                    currentTopicLabel.setText(topic != null ? topic : "No topic set");
                    // 유저 수 업데이트
                    userCountLabel.setText("Users: " + userCount);
                }
                // 채널 목록 갱신
                roomList.repaint();
            });
        }
    }


    public void updateRoom(String roomName) {
        SwingUtilities.invokeLater(() -> {
            RoomState state = client.getStateManager().getRoomState(roomName);
            if (state != null) {
                userListModel.clear();
                for (String user : state.getUsers()) {
                    userListModel.addElement(user);
                }

                roomList.repaint();

                if (roomName.equals(currentRoom)) {
                    updateAdminControls(state.isAdmin());
                }
            }
        });
    }

    private void updateChannelInfo() {
        if (currentRoom != null) {
            RoomState state = client.getStateManager().getRoomState(currentRoom);
            if (state != null) {
                updateRoomInfo(currentRoom, state.getTopic(), state.getUserCount());
            }
        }
    }

    public void refreshRoomList() {
        SwingUtilities.invokeLater(() -> {
            roomList.repaint();
        });
    }


    // 파일 전송 패널 생성을 위한 새로운 메서드
    private JPanel createFileTransferPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("File Transfer"));

        // 파일 목록을 표시할 JList 추가
        DefaultListModel<String> fileListModel = new DefaultListModel<>();
        JList<String> fileList = new JList<>(fileListModel);
        JScrollPane scrollPane = new JScrollPane(fileList);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 파일 전송 상태를 표시할 레이블
        JLabel statusLabel = new JLabel("Ready");
        panel.add(statusLabel, BorderLayout.NORTH);

        // 버튼 패널
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = createButton("Refresh", e -> refreshFileList(fileListModel));
        JButton uploadButton = createButton("Upload", e -> showFileUploadDialog());
        JButton downloadButton = createButton("Download", e -> {
            String selectedFile = fileList.getSelectedValue();
            if (selectedFile != null) {
                downloadFile(selectedFile);
            }
        });

        buttonsPanel.add(refreshButton);
        buttonsPanel.add(uploadButton);
        buttonsPanel.add(downloadButton);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        return panel;
    }

    // 파일 목록 새로고침
    private void refreshFileList(DefaultListModel<String> fileListModel) {
        if (fileManager != null) {
            fileManager.listFiles(new FileTransferManager.TransferCallback() {
                @Override
                public void onProgress(int percentage) {
                }

                @Override
                public void onSuccess(String message) {
                    SwingUtilities.invokeLater(() -> {
                        // 메시지 파싱하여 파일 목록 추출
                        fileListModel.clear();
                        String[] lines = message.split("\n");
                        for (String line : lines) {
                            if (!line.equals("파일 목록 조회 완료")) {  // 상태 메시지 제외
                                fileListModel.addElement(line);
                            }
                        }
                        appendMessage("File list updated");
                    });
                }

                @Override
                public void onError(String error) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ClientUI.this,
                                "Failed to refresh file list: " + error,
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
        }
    }


    private void downloadFile(String fileEntry) {
        // 파일 정보 문자열에서 실제 파일명만 추출
        String filename = fileEntry.split("\t")[0];  // 탭으로 구분된 첫 번째 부분이 파일명

        // 진행 상황을 보여줄 다이얼로그
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        JDialog progressDialog = new JDialog(this, "Downloading " + filename, true);
        progressDialog.add(progressBar);
        progressDialog.setSize(300, 75);
        progressDialog.setLocationRelativeTo(this);

        fileManager.downloadFile(filename, new FileTransferManager.TransferCallback() {
            @Override
            public void onProgress(int percentage) {
                SwingUtilities.invokeLater(() -> {
                    if (percentage >= 0) {
                        progressBar.setValue(percentage);
                    }
                });
            }

            @Override
            public void onSuccess(String message) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    appendMessage("Downloaded file: " + filename);
                    JOptionPane.showMessageDialog(ClientUI.this,
                            "File downloaded successfully: " + filename,
                            "Download Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                });
            }

            @Override
            public void onError(String error) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(ClientUI.this,
                            "Download failed: " + error,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });

        progressDialog.setVisible(true);
    }
}