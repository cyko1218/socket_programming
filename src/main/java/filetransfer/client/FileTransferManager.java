// client/FileTransferManager.java
package filetransfer.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class FileTransferManager {
    private static final Logger logger = Logger.getLogger(FileTransferManager.class.getName());
    private final FTPClient ftpClient;
    private final ExecutorService transferExecutor;
    private final Path baseDirectory;
    private static final String CLIENT_FILES_DIR = "client_files";

    public FileTransferManager(String host, int port) {
        this.ftpClient = new FTPClient(host, port);
        this.transferExecutor = Executors.newFixedThreadPool(3);
        this.baseDirectory = Paths.get(CLIENT_FILES_DIR).toAbsolutePath();
        createBaseDirectory();
    }

    private Path normalizePath(String filename) {
        Path filePath = baseDirectory.resolve(filename).normalize();
        if (!filePath.startsWith(baseDirectory)) {
            throw new SecurityException("Invalid file path: attempted to access outside base directory");
        }
        return filePath;
    }

    private void createBaseDirectory() {
        try {
            Files.createDirectories(baseDirectory);
            logger.info("Created base directory: " + baseDirectory);
        } catch (IOException e) {
            logger.severe("Failed to create base directory: " + e.getMessage());
        }
    }


    public void connect() throws IOException {
        try {
            ftpClient.connect();
            // 익명 로그인은 connect 시점에 자동으로 수행
            logger.info("Connected to FTP server and logged in anonymously");
        } catch (IOException e) {
            logger.warning("Failed to connect: " + e.getMessage());
            throw e;
        }
    }

    public void uploadFile(String filename, TransferCallback callback) {
        transferExecutor.submit(() -> {
            try {
                Path filePath = normalizePath(filename);
                if (!Files.exists(filePath)) {
                    throw new FileNotFoundException("File not found: " + filename);
                }

                long fileSize = Files.size(filePath);
                callback.onProgress(0);

                // 파일 크기를 기반으로 진행률 계산
                ftpClient.uploadFile(filename, bytesTransferred -> {
                    int percentage = (int) ((bytesTransferred * 100) / fileSize); // bytesTransferred는 long 타입이므로 계산 가능
                    callback.onProgress(percentage);  // 진행률 업데이트
                });

                callback.onSuccess("File uploaded successfully: " + filename);
                logger.info("File uploaded: " + filename);
            } catch (Exception e) {
                String errorMsg = "Upload failed: " + e.getMessage();
                callback.onError(errorMsg);
                logger.severe(errorMsg);
            }
        });
    }

    public void downloadFile(String filename, TransferCallback callback) {
        transferExecutor.submit(() -> {
            try {
                Path filePath = normalizePath(filename);

                // 파일이 이미 존재하는 경우 새로운 파일명 생성
                if (Files.exists(filePath)) {
                    String baseName = filename.substring(0, filename.lastIndexOf('.'));
                    String extension = filename.substring(filename.lastIndexOf('.'));
                    int counter = 1;

                    while (Files.exists(filePath)) {
                        String newFilename = String.format("%s(%d)%s", baseName, counter++, extension);
                        filePath = normalizePath(newFilename);
                    }

                    // 새 파일명으로 다운로드할 것임을 알림
                    logger.info("File exists, downloading as: " + filePath.getFileName());
                }

                callback.onProgress(0);

                // 다운로드 진행률 처리
                ftpClient.downloadFile(filename, filePath, bytesTransferred -> {
                    // 진행률을 1%에서 99% 사이로 표시
                    int progress = Math.min(99, (int)(bytesTransferred / 1024)); // 1KB 당 1%로 계산
                    callback.onProgress(progress);
                });

                callback.onProgress(100);
                callback.onSuccess("File downloaded successfully as: " + filePath.getFileName());
                logger.info("File downloaded: " + filePath.getFileName());
            } catch (Exception e) {
                String errorMsg = "Download failed: " + e.getMessage();
                callback.onError(errorMsg);
                logger.severe(errorMsg);
            }
        });
    }


    public void listFiles(TransferCallback callback) {
        transferExecutor.submit(() -> {
            try {
                callback.onProgress(0);
                List<String> files = ftpClient.listFiles();
                StringBuilder result = new StringBuilder();
                for (String file : files) {
                    result.append(file).append("\n");
                }
                callback.onSuccess(result.toString());
            } catch (IOException e) {
                callback.onError("파일 목록 조회 실패: " + e.getMessage());
                logger.warning("파일 목록 조회 실패: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        try {
            ftpClient.disconnect();
        } catch (IOException e) {
            logger.warning("Error during FTP disconnect: " + e.getMessage());
        }
        transferExecutor.shutdown();
        try {
            if (!transferExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                transferExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            transferExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public interface TransferCallback {
        void onProgress(int percentage);
        void onSuccess(String message);
        void onError(String error);
    }


}
