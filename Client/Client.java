import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Client {

    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }

        // Asking for Server IP Address, with a default IP to my network.
        String serverIp = JOptionPane.showInputDialog(null, "Enter the server IP address:", "192.168.0.176");
        if (serverIp == null || serverIp.trim().isEmpty()) {
            System.out.println("No server IP entered. Exiting.");
            return;
        }

        // Number of producer threads/instances.
        int numberOfProducers = 0;
        while (numberOfProducers <= 0) {
            String input = JOptionPane.showInputDialog("Enter the number of producer threads/instances:");
            if (input == null) {
                System.out.println("Input cancelled. Exiting.");
                return;
            }
            try {
                numberOfProducers = Integer.parseInt(input);
                if (numberOfProducers <= 0) {
                    JOptionPane.showMessageDialog(null, "Please enter a positive integer.");
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid input. Please enter a valid integer.");
            }
        }

        List<File> folderList = new ArrayList<>();
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setMultiSelectionEnabled(true);
        folderChooser.setDialogTitle("Select " + numberOfProducers + " folder(s) for Producers");

        int returnValue = folderChooser.showOpenDialog(null);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File[] selectedFolders = folderChooser.getSelectedFiles();
            if (selectedFolders.length < numberOfProducers) {
                System.out.println("You selected " + selectedFolders.length + " folder(s). Please select at least "
                        + numberOfProducers + " folders.");
                return;
            }
            for (int i = 0; i < numberOfProducers; i++) {
                folderList.add(selectedFolders[i]);
                System.out.println("Producer " + (i + 1) + " folder: " + selectedFolders[i].getAbsolutePath());
            }
        } else {
            System.out.println("Folder selection cancelled. Exiting.");
            return;
        }

        // For each folder, start a producer thread that reads all MP4 files from that
        // folder.
        for (File folder : folderList) {
            new Thread(() -> {
                File[] videoFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
                if (videoFiles == null || videoFiles.length == 0) {
                    System.out.println("No MP4 files found in folder: " + folder.getAbsolutePath());
                    return;
                }
                for (File videoFile : videoFiles) {
                    sendFile(videoFile, serverIp);
                }
            }).start();
        }
    }

    private static void sendFile(File fileToSend, String serverIp) {
        System.out.println("Uploading: " + fileToSend.getName());
        try (Socket socket = new Socket(serverIp, SERVER_PORT);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream()); // queue status
                FileInputStream fis = new FileInputStream(fileToSend)) {

            dos.writeUTF(fileToSend.getName());
            long fileSize = fileToSend.length();
            dos.writeLong(fileSize);

            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, read);
            }

            String response = dis.readUTF(); // Wait for server's final response
            if ("QUEUE_FULL".equals(response)) {
                System.out.println("File upload dropped: \"" + fileToSend.getName() + "\". Server queue full.");
            } else if ("FILE_OK".equals(response)) {
                System.out.println("Uploaded " + fileToSend.getName());
            } else if ("DUPLICATE".equals(response)) {
                System.out.println("Duplicate upload for: \"" + fileToSend.getName() + "\" detected. File not saved.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
