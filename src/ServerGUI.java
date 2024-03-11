import javax.swing.*;
import java.awt.*;

public class ServerGUI extends JFrame {
    private JLabel lblServerIP, lblServerPort, lblUptime, lblOnlineUsers;
    private Timer timer;
    public ServerGUI() {
        setLayout(new GridLayout(4, 2));
        setResizable(false);

        add(new JLabel("Server IP: "));
        lblServerIP = new JLabel();
        add(lblServerIP);

        add(new JLabel("Server Port: "));
        lblServerPort = new JLabel();
        add(lblServerPort);

        add(new JLabel("Uptime: "));
        lblUptime = new JLabel();
        add(lblUptime);

        add(new JLabel("Online Users: "));
        lblOnlineUsers = new JLabel();
        add(lblOnlineUsers);

        setSize(400, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        timer = new Timer(1000, e -> updateInfo());
        timer.start();
    }

    public void updateInfo() {
        SwingUtilities.invokeLater(() -> {
            lblServerIP.setText(Server.getServerIP());
            lblServerPort.setText(String.valueOf(Server.getServerPort()));
            lblUptime.setText(Server.getUptime());
            lblOnlineUsers.setText(Server.getOnlineUsers());
        });
    }
}
