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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class Server {
    private final ConsoleManager consoleManager;
    private final int port;
    private final FileManager fm;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    private final ForkJoinPool readPool = new ForkJoinPool();
    private final ExecutorService responsePool = Executors.newCachedThreadPool();

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

            readPool.execute(() -> {
                while (running) {
                    handleClientConnection();
                    checkConsoleInput();
                }
                System.exit(0);
            });

            readPool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            ServerLogger.getLogger().severe("Ошибка в работе сервера");
        } finally {
            closeResources();
        }
    }

    private void handleClientConnection() {
        try {
            Socket clientSocket = serverSocket.accept();
            ServerLogger.getLogger().info("Подключен клиент: " + clientSocket.getRemoteSocketAddress());

            readPool.execute(() -> {
                try (
                        InputStream input = clientSocket.getInputStream();
                        OutputStream output = clientSocket.getOutputStream()
                ) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        final byte[] requestData = Arrays.copyOf(buffer, bytesRead);

                        new Thread(() -> {
                            try {
                                Command command = CommandSerializer.deserialize(requestData);
                                ServerLogger.getLogger().info("Получена команда: " + command.getCommandName());
                                Response response = command.execute();

                                responsePool.execute(() -> {
                                    try {
                                        output.write(CommandSerializer.serialize(response));
                                        output.flush();
                                    } catch (IOException e) {
                                        ServerLogger.getLogger().warning("Ошибка отправки ответа");
                                    }
                                });
                            } catch (ClassNotFoundException | IOException e) {
                                ServerLogger.getLogger().warning("Ошибка обработки команды");
                            }
                        }).start();

                        Arrays.fill(buffer, (byte) 0);
                    }
                } catch (IOException e) {
                    ServerLogger.getLogger().warning("Ошибка клиента");
                } finally {
                    ServerLogger.getLogger().info("Клиент отключился: " + clientSocket.getRemoteSocketAddress());
                    closeClientResources(clientSocket);
                }
            });
        } catch (SocketTimeoutException ignored) {
        } catch (IOException e) {
            ServerLogger.getLogger().warning("Ошибка подключения");
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

    private void closeClientResources(Socket clientSocket) {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                ServerLogger.getLogger().warning("Клиент отключился");
            }
        } catch (IOException e) {
            ServerLogger.getLogger().warning("Ошибка закрытия клиента");
        }
    }

    private void closeResources() {
        try {
            running = false;
            readPool.shutdownNow();
            responsePool.shutdownNow();
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                ServerLogger.getLogger().info("Сервер остановлен");
            }
        } catch (IOException e) {
            ServerLogger.getLogger().warning("Ошибка закрытия сервера");
        }
    }
}