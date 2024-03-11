import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Welcome to project RUdra Chat Server CLI.");
        new ServerGUI();
        mainMenu();
    }

    public static void mainMenu() throws IOException {
        System.out.println("Enter \"/start\" to start server");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String userInput = scanner.nextLine();
            if (userInput.equals("/start")) {
                Server.server();
            } else {
                System.out.println("Invalid command");
            }
        }
    }
}
