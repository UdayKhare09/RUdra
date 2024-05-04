import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Path serverDirectory = Paths.get("C:/RUdra/Server");

        // Create the directories in the file path if they do not exist
        try {
            Files.createDirectories(serverDirectory);
        } catch (Exception e) {
            System.out.println("Failed to create directories: " + e.getMessage());
        }

        String userFile = "C:/RUdra/Server/users.txt";
        Path userFilePath = Paths.get(userFile);

        if (!Files.exists(userFilePath)) {
            // If the file does not exist, create it
            try {
                Files.createFile(userFilePath);
            } catch (Exception ignored) {
            }
        }
        mainMenu();
    }

    public static void mainMenu() throws Exception {
        new ServerGUI();
        Server.server();
    }
}
