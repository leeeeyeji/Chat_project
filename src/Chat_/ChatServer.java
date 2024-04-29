package Chat_;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ChatServer {
    public static void main(String[] args) {
        //1. 서버소켓을 생성!!
        try (ServerSocket serverSocket = new ServerSocket(12344);) {
            System.out.println("서버가 준비되었습니다.");
            //여러명의 클라이언트의 정보를 기억할 공간
            Map<String, PrintWriter> chatClients = new HashMap<>();

            while (true) {
                //2. accept() 를 통해서 소켓을 얻어옴.   (여러명의 클라이언트와 접속할 수 있도록 구현)
                Socket socket = serverSocket.accept();
                //Thread 이용!!
                //여러명의 클라이언트의 정보를 기억할 공간
                new ChatThread(socket, chatClients).start();

            }
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
}

class ChatThread extends Thread {
    private Socket socket;
    private String id;
    private Map<String, PrintWriter> chatClients;
    private static Map<Integer, Set<String>> rooms = new HashMap<>();
    private static int roomCounter = 1; // 방 번호를 위한 카운터
    private Integer currentRoom = null; // 현재 사용자의 방 번호

    private BufferedReader in;
    PrintWriter out;

    public ChatThread(Socket socket, Map<String, PrintWriter> chatClients) {
        this.socket = socket;
        this.chatClients = chatClients;

        //클라이언트가 생성될 때 클라이언트로 부터 아이디를 얻어오게 하고 싶어요.
        //각각 클라이언트와 통신 할 수 있는 통로얻어옴.
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            //Client가 접속하자마 id를 보낸다는 약속!!
            id = in.readLine();
            //이때..  모든 사용자에게 id님이 입장했다라는 정보를 알려줌.
            broadcast(id + "님이 입장하셨습니다.");
            System.out.println("새로운 사용자의 아이디는 " + id + "입니다.");
            InetAddress clientAddress = socket.getInetAddress();
            System.out.println("새로운 연결: " + clientAddress.getHostAddress());

            //동시에 일어날 수도..
            synchronized (chatClients) {
                chatClients.put(this.id, out);
            }

            sendCommandsList();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    private void sendCommandsList() {
        out.println("명령어 모음:\n" +
                "방 목록 보기 : /list\n" +
                "방 생성 : /create\n" +
                "방 입장 : /join [방번호]\n" +
                "방 나가기 : /exit\n" +
                "접속 종료 : /bye");
    }

    @Override
    public void run() {
        System.out.println(id + "사용자 채팅시작!!");
        //run
        //연결된 클라이언트가 메시지를 전송하면, 그 메시지를 받아서 다른 사용자들에게 보내줌..
        String msg = null;
        try {
            while ((msg = in.readLine()) != null) {
                if ("/bye".equalsIgnoreCase(msg))
                    break;
                if (msg.indexOf("/whisper") == 0) {
                    sendMsg(msg);
                    continue;
                }

                if (msg.startsWith("/create")) {
                    handleCreateRoom();
                } else if (msg.startsWith("/join")) {
                    handleJoinRoom(msg);
                } else if (msg.equals("/exit")) {
                    handleExitRoom();
                } else if (msg.equals("/list")) {
                    handleListRoom();
                } else if (msg.equals("/users")) {
                    handleUsersCommand();
                } else if (msg.equals("/roomusers")) {
                    handleRoomUsersCommand();
                } else if (currentRoom != null){
                    broadcastInRoom(id + " : " + msg);
                } else{
                    broadcast(id + " : " + msg);
                }
            }
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            synchronized (chatClients) {
                chatClients.remove(id);
            }
            broadcast(id + "님이 채팅에서 나갔습니다.");

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void handleCreateRoom() {
        int newRoomId = roomCounter++;
        rooms.put(newRoomId, new HashSet<>());
        rooms.get(newRoomId).add(id);
        currentRoom = newRoomId;
        out.println("방 번호 " + newRoomId + "가 생성되었습니다.");
        broadcastInRoom(id + "님이 방에 입장하셨습니다.");
    }

    private void handleJoinRoom(String msg) {
        try {
            int roomNumber = Integer.parseInt(msg.split(" ")[1]);
            if (rooms.containsKey(roomNumber)) {
                rooms.get(roomNumber).add(id);
                currentRoom = roomNumber;
                broadcastInRoom(id + "님이 방에 입장하셨습니다.");
            } else {
                out.println("방 번호 " + roomNumber + "는 존재하지 않습니다.");
            }
        } catch (NumberFormatException e) {
            out.println("올바른 방 번호를 입력하세요.");
        }
    }

    private void handleExitRoom() {
        if (currentRoom != null && rooms.containsKey(currentRoom)) {
            rooms.get(currentRoom).remove(id);
            broadcastInRoom(id + "님이 방을 나갔습니다.");
            if (rooms.get(currentRoom).isEmpty()) {
                rooms.remove(currentRoom);
                out.println("방 번호 " + currentRoom + "가 삭제되었습니다.");
            }
            currentRoom = null; // 로비로 이동
            out.println("방을 나갔습니다.");
        }
    }

    private void handleListRoom() {
        if (rooms.isEmpty()) {
            out.println("현재 생성된 방이 없습니다.");
        } else {
            out.println("현재 생성된 방 목록:");
            for (Map.Entry<Integer, Set<String>> entry : rooms.entrySet()) {
                out.println("방 번호: " + entry.getKey() + " (사용자 수: " + entry.getValue().size() + ")");
            }
        }
    }

    private void broadcastInRoom(String msg) {
        if (currentRoom != null && rooms.containsKey(currentRoom)) {
            for (String clientId : rooms.get(currentRoom)) {
                PrintWriter clientOut = chatClients.get(clientId);
                if (clientOut != null) {
                    clientOut.println(msg);
                }
            }
        }
    }

    //메시지를 특정 사용자에게만 보내는 메서드
    public void sendMsg(String msg) {
        int firstSpaceIndex = msg.indexOf(" ");
        if (firstSpaceIndex == -1) return; //공백이 없다면....

        int secondSpaceIndex = msg.indexOf(" ", firstSpaceIndex + 1);
        if (secondSpaceIndex == -1) return; //두번재 공백이 없다는 것도 메시지가 잘못된거니까..

        String to = msg.substring(firstSpaceIndex + 1, secondSpaceIndex);
        String message = msg.substring(secondSpaceIndex + 1);

        //to(수신자)에게 메시지 전송.
        PrintWriter pw = chatClients.get(to);
        if (pw != null) {
            pw.println(id + "님으로부터 온 비밀 메시지 : " + message);
        } else {
            System.out.println("오류 : 수신자 " + to + " 님을 찾을 수 없습니다.");
        }
    }
    //users 구현
    private void handleUsersCommand() {
        StringBuilder sb = new StringBuilder("현재 접속 중인 사용자:\n");
        synchronized (chatClients) {
            for (String clientId : chatClients.keySet()) {
                sb.append(clientId).append("\n");
            }
        }
        out.println(sb.toString());
    }

    //roomusers구현
    private void handleRoomUsersCommand() {
        if (currentRoom != null && rooms.containsKey(currentRoom)) {
            StringBuilder sb = new StringBuilder("현재 방의 사용자:\n");
            for (String clientId : rooms.get(currentRoom)) {
                sb.append(clientId).append("\n");
            }
            out.println(sb.toString());
        } else {
            out.println("현재 방이 없거나 비어 있습니다.");
        }
    }


    //메지시를 전체 사용자에게 보내는 메서드
    public void broadcast(String msg) {
        synchronized (chatClients) {
            Iterator<PrintWriter> it = chatClients.values().iterator();
            while (it.hasNext()) {
                PrintWriter out = it.next();
                try {
                    out.println(msg);
                } catch (Exception e) {
                    it.remove();  //브로드케스트 할 수 없는 사용자를 제거한다.
                    e.printStackTrace();
                }
            }
        }
    }
}
