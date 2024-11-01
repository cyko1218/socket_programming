package computernetwork_assignment;

import java.io.*;
import java.util.Scanner;

public class FTPTestClient {
    public static void main(String[] args) {
        try {
            FTPClient client = new FTPClient("localhost", 21);
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("FTP 클라이언트 시작");
            
            // 서버 연결
            client.connect();
            
            // 로그인
            client.login("user", "password");
            
            while (true) {
                System.out.println("\n사용 가능한 명령어:");
                System.out.println("1. 파일 목록 보기");
                System.out.println("2. 파일 업로드");
                System.out.println("3. 파일 다운로드");
                System.out.println("4. 종료");
                System.out.print("명령어 선택 (1-4): ");
                
                String choice = scanner.nextLine();
                
                switch (choice) {
                    case "1":
                        System.out.println("\n=== 파일 목록 ===");
                        client.listFiles();
                        break;
                        
                    case "2":
                        System.out.print("\n업로드할 파일 이름 입력: ");
                        String uploadFile = scanner.nextLine();
                        client.uploadFile(uploadFile);
                        break;
                        
                    case "3":
                        System.out.print("\n다운로드할 파일 이름 입력: ");
                        String downloadFile = scanner.nextLine();
                        client.downloadFile(downloadFile);
                        break;
                        
                    case "4":
                        client.disconnect();
                        System.out.println("프로그램을 종료합니다.");
                        return;
                        
                    default:
                        System.out.println("잘못된 명령어입니다.");
                }
            }
            
        } catch (IOException e) {
            System.out.println("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
