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
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int PORT = 8080;
    private static final String SERVER_PASSWORD = "gandu";
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
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

        public ClientHandler(Socket socket) throws IOException {
            this.clientSocket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
            authenticate();
        }

        @Override
        public void run() {
            try {
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
                    clientSocket.close();
                    notifyAllUsers("disconnected^"+userName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void authenticate() throws IOException {
            String enteredPassword = in.readLine();
            if (!enteredPassword.equals(SERVER_PASSWORD)) {
                out.println("Incorrect password. Connection rejected.");
                clientSocket.close();
                return;
            }
            userName = in.readLine();
            clients.put(userName, out);
            sendUserList();
            notifyAllUsers("connected^"+userName);
        }

        private void handleCommand(String command) {
            String[] parts = command.split("\\^", 2);
            if (parts.length != 2) {
                out.println("Invalid command. Use 'receiver's_username^message'");
                return;
            }

            String receiverUsername = parts[0];
            String message = parts[1];

            PrintWriter receiverOut = clients.get(receiverUsername);
            if (receiverOut == null) {
                out.println("User not found: " + receiverUsername);
                return;
            }
            receiverOut.println(userName + "^" + message);
        }

        private void sendUserList() {
            out.println(String.join(",", clients.keySet()));
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
    private static void notifyAllUsers(String message) {
        for (PrintWriter clientOut : clients.values()) {
            clientOut.println(message);
        }
    }
}
