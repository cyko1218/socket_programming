package filetransfer.client;

import filetransfer.protocol.FTPCommand;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FTPClient {
    private static final Logger logger = Logger.getLogger(FTPClient.class.getName());
    private Socket controlSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String host;
    private int controlPort;
    private static final String CLIENT_DIR = "client_files/";
    private static final int BUFFER_SIZE = 8192;
    private final Path clientDirectory;
    private int dataPort; // 패시브 모드 포트 저장


    public interface TransferProgressCallback {
        void onProgress(long bytesTransferred);
    }


    public FTPClient(String host, int controlPort) {
        this.host = host;
        this.controlPort = controlPort;
        this.clientDirectory = Paths.get("client_files").toAbsolutePath();
        createClientDirectory();
    }

    private void createClientDirectory() {
        try {
            Files.createDirectories(clientDirectory);
            logger.info("Client directory created or verified: " + clientDirectory);
        } catch (IOException e) {
            logger.severe("Failed to create client directory: " + e.getMessage());
        }
    }

    private Path normalizePath(String filename) {
        Path filePath = Paths.get(CLIENT_DIR, filename).normalize();
        if (!filePath.startsWith(CLIENT_DIR)) {
            throw new SecurityException("Invalid file path: attempted to access outside client directory");
        }
        return filePath;
    }

    public void connect() throws IOException {
        controlSocket = new Socket(host, controlPort);
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        writer = new PrintWriter(controlSocket.getOutputStream(), true);

        String response = reader.readLine();
        if (!response.startsWith("220")) {
            throw new IOException("FTP connection failed: " + response);
        }
        logger.info("Connected to FTP server: " + response);

        enterPassiveMode(); // 패시브 모드 진입
    }

    public void login(String username, String password) throws IOException {
        // 사용자 이름 전송
        writer.println(FTPCommand.LOGIN + " " + username);
        String response = reader.readLine();
        if (!response.startsWith("331")) {
            throw new IOException("Login failed: " + response);
        }

        // 비밀번호 전송
        writer.println(FTPCommand.PASS + " " + password);
        response = reader.readLine();
        if (!response.startsWith("230")) {
            throw new IOException("Authentication failed: " + response);
        }
        logger.info("Logged in successfully as: " + username);
    }

    private void enterPassiveMode() throws IOException {
        writer.println("PASV");
        String response = reader.readLine();
        if (!response.startsWith("227")) {
            throw new IOException("패시브 모드 진입 실패: " + response);
        }

        // 패시브 모드 응답에서 포트 번호 추출
        int openBracket = response.indexOf('(');
        int closeBracket = response.indexOf(')', openBracket);
        String[] numbers = response.substring(openBracket + 1, closeBracket).split(",");

        if (numbers.length != 6) {
            throw new IOException("잘못된 PASV 응답 형식");
        }

        try {
            int port1 = Integer.parseInt(numbers[4]);
            int port2 = Integer.parseInt(numbers[5]);
            this.dataPort = (port1 * 256) + port2;

            logger.info("패시브 모드 포트 설정됨: " + this.dataPort);
        } catch (NumberFormatException e) {
            throw new IOException("잘못된 PASV 응답 형식: " + e.getMessage());
        }
    }

    public void uploadFile(String filename, TransferProgressCallback callback) throws IOException {
        // 전송 시작 전에 패시브 모드 진입
        enterPassiveMode();

        File file = new File(CLIENT_DIR + filename);
        Path filePath = normalizePath(filename);

        if (!file.exists()) {
            throw new IOException("File not found: " + filename);
        }

        writer.println(FTPCommand.UPLOAD + " " + filename);
        String response = reader.readLine();
        if (!response.startsWith("150")) {
            throw new IOException("Upload failed: " + response);
        }

        // 패시브 모드 포트를 사용하여 데이터 연결
        try (Socket dataSocket = new Socket(host, dataPort);
             InputStream fileIn = Files.newInputStream(filePath);
             OutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream())) {

            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesTransferred = 0;
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
                totalBytesTransferred += bytesRead;

                if (callback != null) {
                    callback.onProgress(totalBytesTransferred);
                }
            }
            dataOut.flush();
        }

        response = reader.readLine();
        if (!response.startsWith("226")) {
            throw new IOException("업로드 실패: " + response);
        }
        logger.info("파일 업로드 성공: " + filename);
    }

    public void downloadFile(String filename, Path filePath, TransferProgressCallback callback) throws IOException {
        // 패시브 모드 진입
        enterPassiveMode();

        writer.println(FTPCommand.DOWNLOAD + " " + filename);
        String response = reader.readLine();
        if (!response.startsWith("150")) {
            throw new IOException("Download failed: " + response);
        }

        // 임시 파일로 먼저 다운로드
        Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");

        try (Socket dataSocket = new Socket(host, dataPort);
             InputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
             OutputStream fileOut = Files.newOutputStream(tempFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesTransferred = 0;
            int bytesRead;

            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalBytesTransferred += bytesRead;

                if (callback != null) {
                    callback.onProgress(totalBytesTransferred);
                }
            }
            fileOut.flush();

            response = reader.readLine();
            if (!response.startsWith("226")) {
                Files.deleteIfExists(tempFile);
                throw new IOException("Download failed: " + response);
            }

            // 다운로드가 성공적으로 완료되면 임시 파일을 실제 파일로 이동
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("File downloaded successfully: " + filePath.getFileName());
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);  // 오류 발생 시 임시 파일 삭제
            logger.warning("Error during file download: " + e.getMessage());
            throw e;
        }
    }

    public List<String> listFiles() throws IOException {
        enterPassiveMode();  // 패시브 모드 진입

        writer.println(FTPCommand.LIST);
        String response = reader.readLine();
        if (!response.startsWith("150")) {
            throw new IOException("파일 목록 조회 실패: " + response);
        }

        List<String> files = new ArrayList<>();
        try (Socket dataSocket = new Socket(host, dataPort);  // dataPort 사용
             BufferedReader dataReader = new BufferedReader(
                     new InputStreamReader(dataSocket.getInputStream()))) {

            String line;
            while ((line = dataReader.readLine()) != null) {
                files.add(line);
                logger.fine("Listed file: " + line);
            }
        }

        response = reader.readLine();
        if (!response.startsWith("226")) {
            throw new IOException("파일 목록 조회 실패: " + response);
        }
        return files;
    }

    private int getDataPort(String response) {
        // 응답에서 포트 번호 추출 (예: "150 Opening data connection on port 20")
        String[] parts = response.split(" ");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("port")) {
                return Integer.parseInt(parts[i + 1]);
            }
        }
        return 20; // 기본 FTP 데이터 포트
    }

    public void disconnect() throws IOException {
        if (writer != null) {
            writer.println(FTPCommand.QUIT);
            writer.close();
        }
        if (reader != null) reader.close();
        if (controlSocket != null) controlSocket.close();
        logger.info("Disconnected from FTP server");
    }
}