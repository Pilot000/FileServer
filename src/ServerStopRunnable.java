import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

//класс для для мониторинга команды остановки
public class ServerStopRunnable implements Runnable {
    private Server server;

    public ServerStopRunnable(Server serv) {
        server = serv;
    }

    @Override
    public void run() {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (!bufferedReader.readLine().equalsIgnoreCase("abort FileServer_ID_port-8000(1)")) {
            }
            server.toStopServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
