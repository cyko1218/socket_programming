// filetransfer/server/FTPServer.java
package filetransfer.server;

import filetransfer.protocol.FTPCommand;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class FTPServer {
    private static final Logger logger = Logger.getLogger(FTPServer.class.getName());
    private final ServerSocket controlServer;
    private final ExecutorService clientExecutor;
    private final String SERVER_FILES_DIR = "server_files/";
    private volatile boolean running;

    public FTPServer(int port) throws IOException {
        this.controlServer = new ServerSocket(port);
        this.clientExecutor = Executors.newCachedThreadPool();
        this.running = true;

        // 서버 파일 디렉토리 생성
        File serverDir = new File(SERVER_FILES_DIR);
        if (!serverDir.exists()) {
            serverDir.mkdir();
        }
    }

    public void start() {
        logger.info("FTP Server started on port " + controlServer.getLocalPort());

        while (running) {
            try {
                Socket clientSocket = controlServer.accept();
                clientExecutor.submit(new ClientHandler(clientSocket));
                logger.info("New client connected: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                if (running) {
                    logger.warning("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        try {
            controlServer.close();
        } catch (IOException e) {
            logger.warning("Error closing server socket: " + e.getMessage());
        }
        clientExecutor.shutdown();
    }

    private class ClientHandler implements Runnable {
        private final Socket controlSocket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        private ServerSocket passiveSocket;

        public ClientHandler(Socket socket) throws IOException {
            this.controlSocket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(socket.getOutputStream(), true);
        }

        @Override
        public void run() {
            try {
                writer.println("220 FTP Server Ready");

                String command;
                while ((command = reader.readLine()) != null) {
                    handleCommand(command);
                }
            } catch (IOException e) {
                logger.warning("Error handling client: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void cleanup() {
            try {
                if (passiveSocket != null && !passiveSocket.isClosed()) {
                    passiveSocket.close();
                }
                controlSocket.close();
            } catch (IOException e) {
                logger.warning("Error during cleanup: " + e.getMessage());
            }
        }

        private void handleCommand(String command) {
            String[] parts = command.split(" ");
            String cmd = parts[0].toUpperCase();

            try {
                switch (cmd) {
                    case FTPCommand.LOGIN:
                        if (parts.length > 1) {
                            writer.println("331 User name okay, need password");
                        } else {
                            writer.println("501 Syntax error in parameters");
                        }
                        break;

                    case FTPCommand.PASS:
                        // 실제 구현에서는 보안을 강화해야 함
                        if (parts.length > 1) {
                            writer.println("230 User logged in successfully");
                        } else {
                            writer.println("501 Syntax error in parameters");
                        }
                        break;
                    case "PASV":
                        handlePassive();
                        break;

                    case FTPCommand.LIST:
                        handleList();
                        break;

                    case FTPCommand.UPLOAD: // STOR
                        if (parts.length > 1) {
                            handleStore(parts[1]); // handleUpload -> handleStore
                        } else {
                            writer.println("501 Syntax error in parameters");
                        }
                        break;

                    case FTPCommand.DOWNLOAD: // RETR
                        if (parts.length > 1) {
                            handleRetrieve(parts[1]); // handleDownload -> handleRetrieve
                        } else {
                            writer.println("501 Syntax error in parameters");
                        }
                        break;

                    case FTPCommand.QUIT:
                        writer.println("221 Goodbye");
                        break;

                    default:
                        writer.println("500 Unknown command");
                }
            } catch (IOException e) {
                logger.warning("Error handling command " + cmd + ": " + e.getMessage());
                writer.println("550 Requested action not taken: " + e.getMessage());
            }
        }
        private void handleList() throws IOException {
            if (passiveSocket == null || passiveSocket.isClosed()) {
                writer.println("425 Use PASV first");
                return;
            }

            writer.println("150 Opening data connection for file list");

            try (Socket dataSocket = passiveSocket.accept();
                 PrintWriter dataWriter = new PrintWriter(dataSocket.getOutputStream(), true)) {

                File dir = new File(SERVER_FILES_DIR);
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        dataWriter.println(String.format("%s\t%d bytes\t%s",
                                file.getName(),
                                file.length(),
                                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                        .format(file.lastModified())));
                    }
                }

                writer.println("226 Transfer complete");
            } finally {
                passiveSocket.close();
                passiveSocket = null;
            }
        }

        private void handleStore(String filename) throws IOException {
            if (passiveSocket == null || passiveSocket.isClosed()) {
                writer.println("425 Use PASV first");
                return;
            }

            writer.println("150 Opening data connection for file upload");

            try (Socket dataSocket = passiveSocket.accept();
                 BufferedInputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
                 BufferedOutputStream fileOut = new BufferedOutputStream(
                         new FileOutputStream(SERVER_FILES_DIR + filename))) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = dataIn.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                }

                writer.println("226 Transfer complete");
                logger.info("File uploaded successfully: " + filename);
            } catch (IOException e) {
                logger.warning("Error during file upload: " + e.getMessage());
                throw e;
            } finally {
                passiveSocket.close();
                passiveSocket = null;
            }
        }

        private void handlePassive() throws IOException {
            // 기존 패시브 소켓이 있다면 닫기
            if (passiveSocket != null && !passiveSocket.isClosed()) {
                passiveSocket.close();
            }

            // 새로운 패시브 모드 서버 소켓 생성
            passiveSocket = new ServerSocket(0);
            int port = passiveSocket.getLocalPort();

            // 서버 IP 주소를 콤마로 구분된 형태로 변환
            String serverIP = controlSocket.getLocalAddress().getHostAddress();
            String[] ipParts = serverIP.split("\\.");

            // 포트 번호를 상위 바이트와 하위 바이트로 분리
            int port1 = port / 256;
            int port2 = port % 256;

            // 패시브 모드 응답: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
            String response = String.format("227 Entering Passive Mode (%s,%s,%s,%s,%d,%d)",
                    ipParts[0], ipParts[1], ipParts[2], ipParts[3], port1, port2);

            writer.println(response);
            logger.info("Passive mode enabled on port: " + port);
        }

        private void handleRetrieve(String filename) throws IOException {
            if (passiveSocket == null || passiveSocket.isClosed()) {
                writer.println("425 Use PASV first");
                return;
            }

            File file = new File(SERVER_FILES_DIR + filename);
            if (!file.exists()) {
                writer.println("550 File not found");
                return;
            }

            writer.println("150 Opening data connection for file download");

            try (Socket dataSocket = passiveSocket.accept();
                 BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));
                 BufferedOutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream())) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }

                writer.println("226 Transfer complete");
                logger.info("File downloaded successfully: " + filename);
            } catch (IOException e) {
                logger.warning("Error during file download: " + e.getMessage());
                throw e;
            } finally {
                passiveSocket.close();
                passiveSocket = null;
            }
        }
    }
}