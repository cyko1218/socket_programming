package computernetwork_assignment;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class FTPServer {
    private static final int CONTROL_PORT = 21;
    private static final int DATA_PORT = 20;
    private static final String SERVER_DIR = "server_files/";
    private Map<String, String> users;

    public FTPServer() {
        users = new HashMap<>();
        users.put("user", "password"); // 기본 사용자 설정
        
        // 서버 디렉토리 생성
        File serverDir = new File(SERVER_DIR);
        if (!serverDir.exists()) {
            serverDir.mkdir();
        }
    }

    public void start() {
        try (ServerSocket controlSocket = new ServerSocket(CONTROL_PORT)) {
            System.out.println("FTP 서버가 시작되었습니다. 포트: " + CONTROL_PORT);

            while (true) {
                Socket clientSocket = controlSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler extends Thread {
        private Socket controlSocket;
        private BufferedReader reader;
        private PrintWriter writer;
        private boolean isLoggedIn = false;
        private ServerSocket dataServerSocket;

        public ClientHandler(Socket socket) {
            this.controlSocket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
                writer = new PrintWriter(controlSocket.getOutputStream(), true);

                // 연결 성공 메시지 전송
                sendResponse("220 FTP 서버에 오신 것을 환영합니다.");

                String command;
                while ((command = reader.readLine()) != null) {
                    processCommand(command);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void processCommand(String command) throws IOException {
            String[] parts = command.split(" ");
            String cmd = parts[0].toUpperCase();

            switch (cmd) {
                case "USER":
                    if (parts.length > 1 && users.containsKey(parts[1])) {
                        sendResponse("331 사용자 이름 확인. 비밀번호를 입력하세요.");
                    } else {
                        sendResponse("530 사용자가 존재하지 않습니다.");
                    }
                    break;

                case "PASS":
                    if (parts.length > 1 && checkPassword(parts[1])) {
                        isLoggedIn = true;
                        sendResponse("230 로그인 성공.");
                    } else {
                        sendResponse("530 로그인 실패.");
                    }
                    break;

                case "LIST":
                    if (!isLoggedIn) {
                        sendResponse("530 먼저 로그인하세요.");
                        break;
                    }
                    listFiles();
                    break;

                case "RETR":
                    if (!isLoggedIn) {
                        sendResponse("530 먼저 로그인하세요.");
                        break;
                    }
                    if (parts.length > 1) {
                        sendFile(parts[1]);
                    }
                    break;

                case "STOR":
                    if (!isLoggedIn) {
                        sendResponse("530 먼저 로그인하세요.");
                        break;
                    }
                    if (parts.length > 1) {
                        receiveFile(parts[1]);
                    }
                    break;

                case "QUIT":
                    sendResponse("221 굿바이.");
                    controlSocket.close();
                    break;

                default:
                    sendResponse("502 명령을 인식할 수 없습니다.");
            }
        }

        private boolean checkPassword(String password) {
            // 실제 구현에서는 보안을 강화해야 함
            return password.equals(users.get("user"));
        }

        private void sendResponse(String response) {
            writer.println(response);
        }

        private void listFiles() throws IOException {
            sendResponse("150 파일 목록을 전송합니다.");
            
            // 데이터 연결 설정
            try (ServerSocket dataServer = new ServerSocket(0)) {
                // 클라이언트에게 데이터 포트 정보 전송
                sendResponse("PORT " + dataServer.getLocalPort());
                
                Socket dataSocket = dataServer.accept();
                PrintWriter dataWriter = new PrintWriter(dataSocket.getOutputStream(), true);

                File dir = new File(SERVER_DIR);
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        dataWriter.println(file.getName() + "\t" + file.length() + " bytes");
                    }
                }

                dataSocket.close();
                sendResponse("226 전송 완료.");
            }
        }

        private void sendFile(String filename) throws IOException {
            File file = new File(SERVER_DIR + filename);
            if (!file.exists()) {
                sendResponse("550 파일을 찾을 수 없습니다.");
                return;
            }

            sendResponse("150 파일 전송을 시작합니다.");

            // 데이터 연결 설정
            try (ServerSocket dataServer = new ServerSocket(0)) {
                sendResponse("PORT " + dataServer.getLocalPort());
                
                Socket dataSocket = dataServer.accept();
                BufferedOutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream());
                BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }

                dataOut.close();
                fileIn.close();
                dataSocket.close();
                sendResponse("226 전송 완료.");
            }
        }

        private void receiveFile(String filename) throws IOException {
            sendResponse("150 파일 수신 준비.");

            // 데이터 연결 설정
            try (ServerSocket dataServer = new ServerSocket(0)) {
                sendResponse("PORT " + dataServer.getLocalPort());
                
                Socket dataSocket = dataServer.accept();
                BufferedInputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
                BufferedOutputStream fileOut = new BufferedOutputStream(
                    new FileOutputStream(SERVER_DIR + filename));

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = dataIn.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                }

                fileOut.close();
                dataIn.close();
                dataSocket.close();
                sendResponse("226 파일 수신 완료.");
            }
        }
    }

    public static void main(String[] args) {
        new FTPServer().start();
    }
}