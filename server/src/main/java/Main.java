import collection.CollectionManager;
import console.ConsoleManager;
import seClasses.Dragon;
import connection.Server;

public class Main {
    private static final ConsoleManager consoleManager = new ConsoleManager();
    private final static Integer serverPort = 21213;
    public static void main(String[] args) {
        Server server = new Server(serverPort, consoleManager);
        server.run();
    }
}
