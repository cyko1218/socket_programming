package computernetwork_assignment;

import java.io.*;
import java.net.*;

public class FTPClient {
    private Socket controlSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String host;
    private int controlPort;
    private static final String CLIENT_DIR = "client_files/";

    public FTPClient(String host, int controlPort) {
        this.host = host;
        this.controlPort = controlPort;
        
        // 클라이언트 디렉토리 생성
        File clientDir = new File(CLIENT_DIR);
        if (!clientDir.exists()) {
            clientDir.mkdir();
        }
    }

    public void connect() throws IOException {
        controlSocket = new Socket(host, controlPort);
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        writer = new PrintWriter(controlSocket.getOutputStream(), true);

        // 서버로부터 환영 메시지 수신
        String response = reader.readLine();
        System.out.println("서버 응답: " + response);
    }

    public void login(String username, String password) throws IOException {
        // 사용자 이름 전송
        writer.println("USER " + username);
        String response = reader.readLine();
        System.out.println("서버 응답: " + response);

        // 비밀번호 전송
        writer.println("PASS " + password);
        response = reader.readLine();
        System.out.println("서버 응답: " + response);
    }

    public void listFiles() throws IOException {
        writer.println("LIST");
        String response = reader.readLine();
        System.out.println("서버 응답: " + response);

        // 데이터 포트 정보 수신
        response = reader.readLine();
        if (response.startsWith("PORT")) {
            int dataPort = Integer.parseInt(response.split(" ")[1]);
            
            // 데이터 연결 설정
            Socket dataSocket = new Socket(host, dataPort);
            BufferedReader dataReader = new BufferedReader(
                new InputStreamReader(dataSocket.getInputStream()));

            String line;
            while ((line = dataReader.readLine()) != null) {
                System.out.println(line);
            }

            dataSocket.close();
            response = reader.readLine();
            System.out.println("서버 응답: " + response);
        }
    }

    public void downloadFile(String filename) throws IOException {
        writer.println("RETR " + filename);
        String response = reader.readLine();
        System.out.println("서버 응답: " + response);

        response = reader.readLine();
        if (response.startsWith("PORT")) {
            int dataPort = Integer.parseInt(response.split(" ")[1]);
            
            // 데이터 연결 설정
            Socket dataSocket = new Socket(host, dataPort);
            BufferedInputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
            BufferedOutputStream fileOut = new BufferedOutputStream(
                new FileOutputStream(CLIENT_DIR + filename));

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
            }

            fileOut.close();
            dataIn.close();
            dataSocket.close();
            response = reader.readLine();
            System.out.println("서버 응답: " + response);
        }
    }

    public void uploadFile(String filename) throws IOException {
        File file = new File(CLIENT_DIR + filename);
        if (!file.exists()) {
            System.out.println("파일을 찾을 수 없습니다: " + filename);
            return;
        }

        writer.println("STOR " + filename);
        String response = reader.readLine();
        System.out.println("서버 응답: " + response);

        response = reader.readLine();
        if (response.startsWith("PORT")) {
            int dataPort = Integer.parseInt(response.split(" ")[1]);
            
            // 데이터 연결 설정
            Socket dataSocket = new Socket(host, dataPort);
            BufferedOutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream());
            BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }

            fileIn.close();
            dataOut.close();
            dataSocket.close();
            response = reader.readLine();
            System.out.println("서버 응답: " + response);
        }
    }

    public void disconnect() throws IOException {
        writer.println("QUIT");
        String response = reader.readLine();
        System.out.println("서버 응답: " + response);
        controlSocket.close();
    }

    public static void main(String[] args) {
        try {
            FTPClient client = new FTPClient("localhost", 21);
            client.connect();
            client.login("user", "password");
            
            // 파일 목록 조회
            client.listFiles();
            
            // 파일 업로드 테스트
            client.uploadFile("test.txt");
            
            // 파일 다운로드 테스트
            client.downloadFile("test.txt");
            
            client.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}