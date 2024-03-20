import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Welcome to project RUdra Chat Server CLI.");
        new ServerGUI();
        mainMenu();
    }

    public static void mainMenu() throws IOException {
        Server.server();
    }
}
