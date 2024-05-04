import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int PORT = 7415;
    private static final String SERVER_PASSWORD = "uday";
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static final Map<String, ClientHandler> handlers = new ConcurrentHashMap<>();
    private static Instant startTime;

    private static final String ALGORITHM = "AES";

    static final Path userFilePath = Paths.get("C:/RUdra/Server/users.txt");

    static final List<String> users;
    static final String key = "SixteenByteKey12";

    static {
        try {
            users = Files.readAllLines(userFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void server() throws Exception {
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
                System.out.println("Public IP address: " + getPublicIP());

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket);

                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    handlers.put(clientHandler.getUserName(), clientHandler);
                    new Thread(clientHandler).start();
                }
            } catch (Exception e) {
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


        public ClientHandler(Socket socket) throws Exception {
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
                        command = decrypt(command); // decrypt the command
                        handleCommand(command);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    clients.remove(userName);
                    handlers.remove(userName);
                    clientSocket.close();
                    notifyAllUsers("disconnected^" + userName);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private void authenticate() throws Exception {
            String enteredPassword = decrypt(in.readLine());
            userName = decrypt(in.readLine());
            if (!enteredPassword.equals(SERVER_PASSWORD)) {
                out.println(encrypt("WrongServerPassword"));
                clientSocket.close();
                return;
            } else if (!users.contains(userName)) {
                out.println(encrypt("UserNotFound"));
            } else {
                out.println(encrypt("Authenticated"));
            }
            clients.put(userName, out);
            sendUserList();
            notifyAllUsers("connected^" + userName);
        }

        private void handleCommand(String command) throws Exception {
            String[] parts = command.split("\\^", 2);
            if (parts.length != 2) {
                out.println(encrypt("Invalid command"));
                return;
            }

            if (parts.length == 2){
                String receiverUsername = parts[0];
                String message = parts[1];

                PrintWriter receiverOut = clients.get(receiverUsername);
                if (receiverOut == null) {
                    out.println(encrypt("User not found: " + receiverUsername));
                    return;
                }
                try {
                    receiverOut.println(encrypt(userName + "^" + message)); // encrypt the message
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (parts.length == 3){
                String receiverUsername = parts[0];
                String message = parts[1];
                String type = parts[2];
                if(type.equals("image")){
                    byte[] imageBytes = Base64.getDecoder().decode(message);
                    String encodedString = Base64.getEncoder().encodeToString(imageBytes);
                    PrintWriter receiverOut = clients.get(receiverUsername);
                    if (receiverOut == null) {
                        out.println(encrypt("User not found: " + receiverUsername));
                        return;
                    }
                    try {
                        receiverOut.println(encrypt(userName + "^" + encodedString + "^" + type)); // encrypt the message
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if(type.equals("voice")){
                    byte[] voiceBytes = Base64.getDecoder().decode(message);
                    String encodedString = Base64.getEncoder().encodeToString(voiceBytes);
                    PrintWriter receiverOut = clients.get(receiverUsername);
                    if (receiverOut == null) {
                        out.println(encrypt("User not found: " + receiverUsername));
                        return;
                    }
                    try {
                        receiverOut.println(encrypt(userName + "^" + encodedString + "^" + type)); // encrypt the message
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        private void sendUserList() throws Exception {
            out.println(encrypt(String.join(",", clients.keySet())));
        }

        public void kickUser() throws Exception {
            clients.get(userName).println(encrypt("You have been kicked by the server"));
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

    private static void notifyAllUsers(String message) throws Exception {
        for (PrintWriter clientOut : clients.values()) {
            clientOut.println(encrypt(message));
        }
    }

    public static void addUser(String userName) throws IOException {
        if (!users.contains(userName)) {
            users.add(userName);
            try (BufferedWriter writer = Files.newBufferedWriter(userFilePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String user : users) {
                    writer.write(user);
                    writer.newLine();
                }
            }
        }
    }

    public static void removeUser(String userName) throws IOException {
        if (users.contains(userName)) {
            users.remove(userName);
            try (BufferedWriter writer = Files.newBufferedWriter(userFilePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String user : users) {
                    writer.write(user);
                    writer.newLine();
                }
            }
        }
    }

    public static String encrypt(String value) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

        byte[] encryptedValue = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedValue);
    }

    public static String decrypt(String encryptedValue) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

        byte[] originalValue = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
        return new String(originalValue, StandardCharsets.UTF_8);
    }

    public static String getPublicIP() {
        try {
            URL url = new URL("http://checkip.amazonaws.com");
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            return br.readLine().trim();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
