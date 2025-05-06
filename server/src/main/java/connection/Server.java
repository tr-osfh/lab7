package connection;

import collection.CollectionManager;
import collection.ServerLogger;
import commands.Command;
import commands.CommandSerializer;
import console.ConsoleManager;
import file.FileManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class Server {
    private final ConsoleManager consoleManager;
    private final int port;
    private final FileManager fm;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private InputStream clientInput;
    private OutputStream clientOutput;

    public Server(int port, FileManager fileManager, ConsoleManager consoleManager) {
        this.port = port;
        this.fm = fileManager;
        this.consoleManager = consoleManager;
    }

    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(100);
            ServerLogger.getLogger().info("Сервер запущен на порту " + port);

            while (running) {
                checkConsoleInput();
                handleClientConnection();
                handleClientData();
            }
        } catch (IOException e) {
            ServerLogger.getLogger().severe("Ошибка в работе сервера");
        } finally {
            closeResources();
        }
    }

    private void handleClientConnection() throws IOException {
        try {
            if (clientSocket == null || clientSocket.isClosed()) {
                clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(100);
                clientInput = clientSocket.getInputStream();
                clientOutput = clientSocket.getOutputStream();
                ServerLogger.getLogger().info("Подключен клиент: " + clientSocket.getRemoteSocketAddress());
            }
        } catch (SocketTimeoutException ignored) {
        }
    }

    private void handleClientData() {
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                byte[] buffer = new byte[4096];
                int bytesRead = clientInput.read(buffer);

                if (bytesRead == -1) {
                    ServerLogger.getLogger().info("Клиент отключился: " + clientSocket.getRemoteSocketAddress());
                    closeClientResources();
                    return;
                }

                if (bytesRead > 0) {
                    Command command = CommandSerializer.deserialize(Arrays.copyOf(buffer, bytesRead));
                    ServerLogger.getLogger().info("От клиента получена команда: " + command.getCommandName());
                    Response response = command.execute();
                    clientOutput.write(CommandSerializer.serialize(response));
                    clientOutput.flush();
                }
            } catch (SocketTimeoutException e) {
            } catch (IOException | ClassNotFoundException e) {
                ServerLogger.getLogger().info("Клиент отключился: Ошибка обработки");
                closeClientResources();
            }
        }
    }

    private void checkConsoleInput() {
        try {
            if (System.in.available() > 0) {
                String line = consoleManager.read().trim().toLowerCase();
                switch (line) {
                    case "exit":
                        fm.saveCSV(CollectionManager.getDragons());
                        running = false;
                        break;
                    case "save":
                        fm.saveCSV(CollectionManager.getDragons());
                        break;
                    default:
                        consoleManager.write("Доступны только команды save, exit");
                }
            }
        } catch (IOException e) {
            ServerLogger.getLogger().warning("Ошибка ввода");
        }
    }

    private void closeClientResources() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            ServerLogger.getLogger().warning("Ошибка закрытия клиентского соединения");
        } finally {
            clientSocket = null;
            clientInput = null;
            clientOutput = null;
        }
    }

    private void closeResources() {
        try {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            closeClientResources();
        } catch (IOException e) {
            ServerLogger.getLogger().warning("Ошибка закрытия ресурсов");
        }
    }
}