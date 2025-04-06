import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;

public class Server extends Application {

    private ObservableList<String> videoList = FXCollections.observableArrayList();
    private final File uploadsDir = new File("uploads");
    private static final Map<String, String> uploadedFileHashes = new HashMap<>();
    // ScheduledExecutorService for closing the preview window after 10 seconds
    // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledExecutorService.html
    private final ScheduledExecutorService previewScheduler = Executors.newScheduledThreadPool(1);

    // Bounded queue for file upload tasks
    // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/BlockingQueue.html
    private BlockingQueue<FileUploadTask> uploadQueue;
    private ExecutorService consumerPool;
    private int consumerCount = 1; // default number of consumer threads
    private int queueCapacity = 1; // default maximum queue length

    private static class FileUploadTask {
        String fileName;
        byte[] fileData;

        public FileUploadTask(String fileName, byte[] fileData) {
            this.fileName = fileName;
            this.fileData = fileData;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        TextInputDialog consumerDialog = new TextInputDialog(String.valueOf(consumerCount));
        consumerDialog.setTitle("Configuration");
        consumerDialog.setHeaderText("Enter the number of consumer threads:");
        Optional<String> consumerResult = consumerDialog.showAndWait();
        if (consumerResult.isPresent()) {
            try {
                int count = Integer.parseInt(consumerResult.get());
                if (count > 0) {
                    consumerCount = count;
                }
            } catch (NumberFormatException e) {
            }
        } else {
            Platform.exit();
            return;
        }

        TextInputDialog queueDialog = new TextInputDialog(String.valueOf(queueCapacity));
        queueDialog.setTitle("Configuration");
        queueDialog.setHeaderText("Enter the maximum queue length:");
        Optional<String> queueResult = queueDialog.showAndWait();
        if (queueResult.isPresent()) {
            try {
                int capacity = Integer.parseInt(queueResult.get());
                if (capacity > 0) {
                    queueCapacity = capacity;
                }
            } catch (NumberFormatException e) {
            }
        } else {
            Platform.exit();
            return;
        }

        // Initialize bounded queue and fixed consumer thread pool
        uploadQueue = new ArrayBlockingQueue<>(queueCapacity);
        consumerPool = Executors.newFixedThreadPool(consumerCount);
        for (int i = 0; i < consumerCount; i++) {
            consumerPool.execute(() -> {
                while (true) {
                    try {
                        FileUploadTask task = uploadQueue.take();
                        File outputFile = new File(uploadsDir, task.fileName);
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            fos.write(task.fileData);
                        }
                        Platform.runLater(this::refreshVideoList);
                        // Delay for 3 sec, this is just for demonstration purposes.
                        // Since the video size is too small to observe the queue behavior,
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }
        refreshVideoList();

        // Set up JavaFX ListView to show uploaded videos.
        ListView<String> listView = new ListView<>(videoList);
        listView.setCellFactory(lv -> new VideoListCell());
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                String selectedFileName = listView.getSelectionModel().getSelectedItem();
                if (selectedFileName != null) {
                    playVideo(selectedFileName);
                }
            }
        });

        BorderPane root = new BorderPane(listView);
        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Uploaded Videos");
        primaryStage.setScene(scene);
        primaryStage.show();

        new Thread(this::startServer).start();
        new Thread(this::watchUploadsFolder).start();
    }

    // Refresh the video list in the GUI from the uploads folder.
    private void refreshVideoList() {
        Platform.runLater(() -> {
            videoList.clear();
            File[] files = uploadsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
            if (files != null && files.length > 0) {
                for (File f : files) {
                    videoList.add(f.getName());
                }
            }
        });
    }

    // Opens a new window to play the selected video.
    private void playVideo(String fileName) {
        File videoFile = new File(uploadsDir, fileName);
        if (!videoFile.exists()) {
            return;
        }
        String mediaUrl = videoFile.toURI().toString();
        Media media = new Media(mediaUrl);
        MediaPlayer mediaPlayer = new MediaPlayer(media);
        MediaView mediaView = new MediaView(mediaPlayer);
        mediaView.setFitWidth(800);
        mediaView.setFitHeight(600);
        mediaView.setPreserveRatio(true);

        StackPane videoRoot = new StackPane(mediaView);
        Scene videoScene = new Scene(videoRoot, 800, 600);
        Stage videoStage = new Stage();
        videoStage.setTitle("Playing: " + fileName);
        videoStage.setScene(videoScene);
        videoStage.show();

        videoStage.setOnCloseRequest((WindowEvent event) -> mediaPlayer.stop());
        mediaPlayer.play();
    }

    // Server method: continuously listens for client connections.
    private void startServer() {
        int port = 5000;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handles a client connection by reading file uploads.
    private void handleClient(Socket clientSocket) {
        try (DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(clientSocket.getInputStream())) {
            while (true) {
                try {
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();

                    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                    DigestInputStream disWithHash = new DigestInputStream(dis, sha256);

                    byte[] data = new byte[(int) fileSize];
                    int totalRead = 0;
                    while (totalRead < fileSize) {
                        int read = disWithHash.read(data, totalRead, (int) (fileSize - totalRead));
                        if (read == -1)
                            break;
                        totalRead += read;
                    }

                    byte[] fileHashBytes = sha256.digest();
                    String fileHash = Base64.getEncoder().encodeToString(fileHashBytes);

                    FileUploadTask task = new FileUploadTask(fileName, data);
                    if (uploadedFileHashes.containsKey(fileName) && uploadedFileHashes.get(fileName).equals(fileHash)) {
                        dos.writeUTF("DUPLICATE");
                    } else if (!uploadQueue.offer(task)) {
                        System.out.println("File dropped due to full queue (leaky bucket design): " + fileName);
                        dos.writeUTF("QUEUE_FULL");// reply upload status to server

                    } else {
                        uploadedFileHashes.put(fileName, fileHash);
                        dos.writeUTF("FILE_OK");
                    }
                } catch (IOException e) {
                    break;
                }
            }
        } catch (IOException ex) {
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignore) {
            }
        }
    }

    // Monitors the uploads folder for changes and refreshes the GUI.
    private void watchUploadsFolder() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path path = uploadsDir.toPath();
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
            while (true) {
                WatchKey key = watchService.take();
                boolean refreshNeeded = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE ||
                            event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        refreshNeeded = true;
                    }
                }
                if (refreshNeeded) {
                    refreshVideoList();
                }
                if (!key.reset()) {
                    break;
                }
            }
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    // ListCell for immediate video preview on mouse hover.
    private class VideoListCell extends ListCell<String> {
        private ScheduledFuture<?> autoCloseTask;
        private Stage previewStage;
        private MediaPlayer previewMediaPlayer;

        public VideoListCell() {
            setOnMouseEntered(event -> {
                if (!isEmpty()) {
                    showPreview(getItem());
                    autoCloseTask = previewScheduler.schedule(() -> Platform.runLater(this::closePreview), 10,
                            TimeUnit.SECONDS);
                }
            });

            setOnMouseExited(event -> {
                if (autoCloseTask != null) {
                    autoCloseTask.cancel(false);
                    autoCloseTask = null;
                }
                closePreview();
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item);
        }

        private void showPreview(String fileName) {
            if (previewStage != null)
                return;
            File videoFile = new File("uploads", fileName);
            if (!videoFile.exists()) {
                return;
            }
            String mediaUrl = videoFile.toURI().toString();
            Media media = new Media(mediaUrl);
            previewMediaPlayer = new MediaPlayer(media);
            previewMediaPlayer.setStopTime(Duration.seconds(10));
            MediaView mediaView = new MediaView(previewMediaPlayer);
            mediaView.setFitWidth(400);
            mediaView.setFitHeight(300);
            mediaView.setPreserveRatio(true);
            StackPane root = new StackPane(mediaView);
            Scene scene = new Scene(root, 400, 300);
            previewStage = new Stage();
            previewStage.setTitle("Preview: " + fileName);
            previewStage.setScene(scene);
            previewStage.show();
            previewMediaPlayer.play();
        }

        private void closePreview() {
            if (previewMediaPlayer != null) {
                previewMediaPlayer.stop();
                previewMediaPlayer.dispose();
                previewMediaPlayer = null;
            }
            if (previewStage != null) {
                previewStage.close();
                previewStage = null;
            }
        }
    }
}
