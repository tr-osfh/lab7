package file;

import collection.ServerLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class fileReader {
    private static String[] password;

    public static void read() {
        String filePath = System.getenv("PASS_PATH");

        if (filePath == null || filePath.isBlank()) {
            ServerLogger.getLogger().warning("Переменная окружения не найдена, подключение к базе данных невозможно!");
            System.exit(0);
        }

        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                ServerLogger.getLogger().warning("Файл не найден: " + filePath + " \n подключение к базе данных невозможно!");
            }
            password = Files.readString(path, StandardCharsets.UTF_8).split(" ");
        } catch (IOException e) {
            ServerLogger.getLogger().warning("Ошибка чтения файла");
        }
    }

    public static String[] getPassword(){
        return password;
    }
}