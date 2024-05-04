import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class ServerGUI extends JFrame {
    private JLabel lblServerIP, lblServerPublicIP, lblUptime, lblOnlineUsers;
    private Timer timer;

    public ServerGUI() {
        setResizable(false);
        setTitle("Server");

        Panel mainPanel = new Panel();
        mainPanel.setLayout(new GridLayout(4, 2));

        mainPanel.add(new JLabel("Server IP: "));
        lblServerIP = new JLabel();
        mainPanel.add(lblServerIP);

        mainPanel.add(new JLabel("Server Public IP: "));
        lblServerPublicIP = new JLabel();
        mainPanel.add(lblServerPublicIP);

        mainPanel.add(new JLabel("Uptime: "));
        lblUptime = new JLabel();
        mainPanel.add(lblUptime);

        mainPanel.add(new JLabel("Online Users: "));
        lblOnlineUsers = new JLabel();
        mainPanel.add(lblOnlineUsers);

        setSize(400, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        timer = new Timer(1000, e -> updateInfo());
        timer.start();

        // Add this at the end of the ServerGUI constructor
        JButton manageUsersButton = new JButton("Manage Users");
        manageUsersButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        manageUsersButton.addActionListener(e -> {
            JDialog manageUsersDialog = new JDialog(this, "Manage Users", true);
            manageUsersDialog.setLayout(new GridLayout(3, 2));

            JTextField userNameField = new JTextField();
            JButton addUserButton = new JButton("Add User");
            addUserButton.addActionListener(ae -> {
                String userName = userNameField.getText();
                if (!userName.isEmpty()) {
                    try {
                        Server.addUser(userName);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            });

            JButton removeUserButton = new JButton("Remove User");
            removeUserButton.addActionListener(ae -> {
                String userName = userNameField.getText();
                if (!userName.isEmpty()) {
                    try {
                        Server.removeUser(userName);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            });

            manageUsersDialog.add(new JLabel("Username: "));
            manageUsersDialog.add(userNameField);
            manageUsersDialog.add(addUserButton);
            manageUsersDialog.add(removeUserButton);
            manageUsersDialog.setResizable(false);
            manageUsersDialog.pack();
            manageUsersDialog.setLocationRelativeTo(this);
            manageUsersDialog.setVisible(true);
        });
        add(mainPanel, BorderLayout.CENTER);
        add(manageUsersButton, BorderLayout.SOUTH);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void updateInfo() {
        SwingUtilities.invokeLater(() -> {
            lblServerIP.setText(Server.getServerIP());
            lblServerPublicIP.setText(Server.getPublicIP());
            lblUptime.setText(Server.getUptime());
            lblOnlineUsers.setText(Server.getOnlineUsers());
        });
    }
}
