import java.io.*;
import java.net.*;
import java.sql.*;
import java.nio.file.*;
import java.util.*;

public class Server {
    private static final int PORT = 12345;
    private Connection conn;

    public Server() {
        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/jnote", "root", "root");
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket, conn).start();
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Server();
    }
}

class ClientHandler extends Thread {
    private Socket clientSocket;
    private Connection conn;
    private static final String SERVER_VAULT_PATH = "server_vaults/";

    public ClientHandler(Socket clientSocket, Connection conn) {
        this.clientSocket = clientSocket;
        this.conn = conn;
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            while (true) {
                FileOperation operation = (FileOperation) in.readObject();

                switch (operation.getType()) {
                    case CREATE_FILE:
                        handleCreateFile(operation, out);
                        break;
                    case DELETE_FILE:
                        handleDeleteFile(operation, out);
                        break;
                    case UPDATE_FILE:
                        handleUpdateFile(operation, out);
                        break;
                    case SYNC_REQUEST:
                        handleSyncRequest(operation, out);
                        break;
                    case DELETE_MULTIPLE:
                        handleDeleteMultiple(operation, out);
                        break;
                }
            }
        } catch (EOFException e) {
            // Client disconnected
            System.out.println("Client disconnected");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void handleCreateFile(FileOperation operation, ObjectOutputStream out) throws IOException {
        String username = operation.getUsername();
        String fileName = operation.getFileName();
        String content = operation.getContent();

        File userVault = new File(SERVER_VAULT_PATH + username);
        if (!userVault.exists()) {
            userVault.mkdirs();
        }

        File newFile = new File(userVault, fileName);
        Files.writeString(newFile.toPath(), content);

        out.writeObject(new FileOperation(OperationType.CREATE_FILE, username, fileName, "SUCCESS"));
        out.flush();
    }

    private void handleDeleteFile(FileOperation operation, ObjectOutputStream out) throws IOException {
        String username = operation.getUsername();
        String fileName = operation.getFileName();

        File fileToDelete = new File(SERVER_VAULT_PATH + username + "/" + fileName);
        boolean success = fileToDelete.delete();

        out.writeObject(new FileOperation(OperationType.DELETE_FILE, username, fileName,
                success ? "SUCCESS" : "FAILED"));
        out.flush();
    }

    private void handleDeleteMultiple(FileOperation operation, ObjectOutputStream out) throws IOException {
        String username = operation.getUsername();
        List<String> fileNames = operation.getFileNames();
        List<String> deletedFiles = new ArrayList<>();

        for (String fileName : fileNames) {
            File fileToDelete = new File(SERVER_VAULT_PATH + username + "/" + fileName);
            if (fileToDelete.delete()) {
                deletedFiles.add(fileName);
            }
        }

        out.writeObject(new FileOperation(OperationType.DELETE_MULTIPLE, username, "",
                deletedFiles));
        out.flush();
    }

    private void handleUpdateFile(FileOperation operation, ObjectOutputStream out) throws IOException {
        String username = operation.getUsername();
        String fileName = operation.getFileName();
        String content = operation.getContent();

        File fileToUpdate = new File(SERVER_VAULT_PATH + username + "/" + fileName);
        Files.writeString(fileToUpdate.toPath(), content);

        out.writeObject(new FileOperation(OperationType.UPDATE_FILE, username, fileName, "SUCCESS"));
        out.flush();
    }

    private void handleSyncRequest(FileOperation operation, ObjectOutputStream out) throws IOException {
        String username = operation.getUsername();
        File userVault = new File(SERVER_VAULT_PATH + username);

        if (!userVault.exists()) {
            userVault.mkdirs();
            out.writeObject(new FileOperation(OperationType.SYNC_RESPONSE, username, "",
                    new ArrayList<>()));
            out.flush();
            return;
        }

        List<FileData> filesData = new ArrayList<>();
        File[] files = userVault.listFiles((dir, name) -> name.endsWith(".txt"));

        if (files != null) {
            for (File file : files) {
                String content = Files.readString(file.toPath());
                filesData.add(new FileData(file.getName(), content));
            }
        }

        out.writeObject(new FileOperation(OperationType.SYNC_RESPONSE, username, "", filesData));
        out.flush();
    }
}