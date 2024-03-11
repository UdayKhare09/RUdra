import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int PORT = 8080;
    private static final String SERVER_PASSWORD = "gandu";
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, PrintWriter>> groups = new HashMap<>();
    private static final Map<String, ClientHandler> handlers = new ConcurrentHashMap<>();
    private static Instant startTime;
    public static void server() throws IOException {
        Thread serverThread = new Thread(ClientConnector::connector);
        serverThread.start();
        startTime = Instant.now();
        while (true) {
            Scanner scanner = new Scanner(System.in);
            String command = scanner.nextLine();
            if (command.startsWith("/kick")) {
                String[] parts = command.split(" ", 2);
                if (parts.length == 2) {
                    String username = parts[1];
                    ClientHandler handler = handlers.get(username);
                    if (handler != null) {
                        handler.kickUser();
                    } else {
                        System.out.println("User not found: " + username);
                    }
                } else {
                    System.out.println("Invalid kick command. Use '/kick username'");
                }
            } else if (command.startsWith("/list")) {
                System.out.println("Connected Users: " + String.join(", ", clients.keySet()));
            } else if (command.startsWith("/groups")) {
                System.out.println("Available Groups: " + String.join(", ", groups.keySet()));
            } else if (command.startsWith("/exit")) {
                //stop all threads and close all sockets
                ClientConnector.running = false;
                for (ClientHandler handler : handlers.values()) {
                    handler.kickUser();
                }
                serverThread.interrupt();
                Main.mainMenu();
            } else {
                System.out.println("Invalid command.");
            }
        }
    }

    private static class ClientConnector {
        private static volatile boolean running = true;
        public static void connector() {
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("Server is running on port " + PORT);
                System.out.println("and IP address " + InetAddress.getLocalHost().getHostAddress());

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket);

                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    handlers.put(clientHandler.getUserName(), clientHandler);
                    new Thread(clientHandler).start();
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            } finally {
                running = false;
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final BufferedReader in;
        private final PrintWriter out;
        private String userName;
        private String currentGroup;

        public ClientHandler(Socket socket) throws IOException {
            this.clientSocket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
            authenticate();
        }

        @Override
        public void run() {
            try {
                out.println("Welcome, " + userName + "!");
                while (true) {
                    String command = in.readLine();
                    if (command != null) {
                        handleCommand(command);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clients.remove(userName);
                    handlers.remove(userName);
                    if (currentGroup != null) {
                        groups.get(currentGroup).remove(userName);
                        broadcastToGroup(currentGroup, userName + " has left the group.");
                    }
                    broadcast(userName + " has left the chat.");
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void authenticate() throws IOException {
            out.println("Enter the server password:");
            String enteredPassword = in.readLine();
            if (!enteredPassword.equals(SERVER_PASSWORD)) {
                out.println("Incorrect password. Connection rejected.");
                clientSocket.close();
                return;
            }

            out.println("Enter your username:");
            userName = in.readLine();
            clients.put(userName, out);

        }

        private void handleCommand(String command) {
            if (command.startsWith("/join")) {
                joinGroup(command);
            } else if (command.startsWith("/leave")) {
                leaveGroup();
            } else if (command.startsWith("/groups")) {
                listGroups();
            } else if (command.startsWith("/private")) {
                sendPrivateMessage(command);
            } else if (command.startsWith("/list")) {
                sendUserList();
            } else if (command.startsWith("/create")) {
                createGroup(command);
            } else if (command.startsWith("/remove")) {
                removeGroup(command);
            } else {
                sendMessageToGroup(command);
            }
        }

        private void joinGroup(String command) {
            // Format: /join groupName
            String[] parts = command.split(" ", 2);
            if (parts.length == 2) {
                String groupName = parts[1];

                groups.computeIfAbsent(groupName, k -> new HashMap<>());
                groups.get(groupName).put(userName, out);
                currentGroup = groupName;

                broadcastToGroup(groupName, userName + " has joined the group.");
            } else {
                out.println("Invalid join command. Use '/join groupName'");
            }
        }

        private void leaveGroup() {
            if (currentGroup != null) {
                groups.get(currentGroup).remove(userName);
                broadcastToGroup(currentGroup, userName + " has left the group.");
                currentGroup = null;
            } else {
                out.println("You are not currently in any group.");
            }
        }

        private void listGroups() {
            if (groups.isEmpty()) {
                out.println("There are no available groups. Use '/create groupName' to create a group.");
            } else out.println("Available Groups: " + String.join(", ", groups.keySet()));
        }

        private void createGroup(String command) {
            // Format: /create groupName
            String[] parts = command.split(" ", 2);
            if (parts.length == 2) {
                String groupName = parts[1];

                if (!groups.containsKey(groupName)) {
                    groups.put(groupName, new HashMap<>());
                    out.println("Group '" + groupName + "' created.");
                } else {
                    out.println("Group '" + groupName + "' already exists.");
                }
            } else {
                out.println("Invalid create command. Use '/create groupName'");
            }
        }

        private void removeGroup(String command) {
            // Format: /remove groupName
            String[] parts = command.split(" ", 2);
            if (parts.length == 2) {
                String groupName = parts[1];

                if (groups.containsKey(groupName)) {
                    groups.remove(groupName);
                    out.println("Group '" + groupName + "' removed.");
                } else {
                    out.println("Group '" + groupName + "' does not exist.");
                }
            } else {
                out.println("Invalid remove command. Use '/remove groupName'");
            }
        }

        private void sendPrivateMessage(String command) {
            // Format: /private recipientUsername privateMessage
            String[] parts = command.split(" ", 3);
            if (parts.length == 3) {
                String recipient = parts[1];
                String privateMessage = parts[2];

                PrintWriter recipientWriter = clients.get(recipient);
                if (recipientWriter != null) {
                    recipientWriter.println("[Private from " + userName + "]: " + privateMessage);
                } else {
                    out.println("User '" + recipient + "' not found or not available.");
                }
            } else {
                out.println("Invalid private message format. Use '/private recipientUsername privateMessage'");
            }
        }

        private void sendUserList() {
            out.println("User List: " + String.join(", ", clients.keySet()));
        }

        private void sendMessageToGroup(String message) {
            if (currentGroup != null) {
                broadcastToGroup(currentGroup, userName + ": " + message);
            } else {
                out.println("You are not currently in any group. Use '/join groupName' to join a group.");

                if (groups.isEmpty()) {
                    out.println("There are no available groups. Use '/create groupName' to create a group.");
                } else {
                    out.println("Available Groups: " + String.join(", ", groups.keySet()));
                }
            }
        }

        private void broadcastToGroup(String groupName, String message) {
            Map<String, PrintWriter> groupMembers = groups.get(groupName);
            for (PrintWriter memberWriter : groupMembers.values()) {
                memberWriter.println(message);
            }
        }

        private void broadcast(String message) {
            for (PrintWriter writer : clients.values()) {
                writer.println(message);
            }
        }

        public void kickUser() throws IOException {
            clients.get(userName).println("You have been kicked from the server.");
            clients.remove(userName);
            clientSocket.close();
        }

        public String getUserName() {
            return userName;
        }
    }

    public static String getServerIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "Unknown";
        }
    }

    public static int getServerPort() {
        return PORT;
    }

    public static String getUptime() {
        if (startTime == null) {
            return "Server not started";
        }
        Duration uptime = Duration.between(startTime, Instant.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String getOnlineUsers() {
        return String.join(", ", clients.keySet());
    }
}
