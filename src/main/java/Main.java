import client.ClientUI;
import server.IRCServer;
import filetransfer.server.FTPServer;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // IRC 서버 시작
        new Thread(() -> {
            IRCServer server = new IRCServer();
            server.start();
        }).start();

        // FTP 서버 시작
        new Thread(() -> {
            try {
                FTPServer ftpServer = new FTPServer(21);
                ftpServer.start();
                System.out.println("FTP 서버가 성공적으로 시작되었습니다.");
            } catch (Exception e) {
                System.err.println("Failed to start FTP server: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();

        // 잠시 대기 (서버들 시작 시간)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 클라이언트 UI 시작
        SwingUtilities.invokeLater(() -> {
            new ClientUI("localhost", 6667); // IRC 포트는 6667 유지
        });
    }
}