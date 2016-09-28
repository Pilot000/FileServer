import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//класс серверного обменника, нужны SendData, ServerStopRunnable, FormatterForServer
public class Server {
    static Logger logger = Logger.getLogger("ServerLog");
    Selector selector;
    public static ThreadPoolExecutor executorForWrite =
            (ThreadPoolExecutor) Executors.newFixedThreadPool(3);//для отправки файлов
    private ThreadPoolExecutor executorForRead
            = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);//для чтения запросов и отправки списка
    private InetSocketAddress inetSocketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 8000);
    private boolean isAlive = true;//для основного цикла, в котором работает сервер
    private Set<SelectionKey> keySet;//список ключей полкюченных каналов
    private Path path; //директория для обзора файлов, устанавливается в свойствах запуска(конфиг)
    private String LISTFILES = "listFiles"; // константы запросов
    private String FILE = "file";
    private String ABORT_CONNECTION = "abort_connection";
    private Charset charset = Charset.forName("8859_1");//кодировка для запросов, ответов(текстовых)

    //установка директории при запуске, установка директории и файла для логгирования, настроек логгирования.
    public Server(Path directory) throws IOException {
        path = directory;
        Properties properties = System.getProperties();
        String s = properties.getProperty("user.home");
        try {
            Path p = Paths.get(s);
            File file = new File(p.toString() + "\\FileServer_ID_port-8000(1)");
            if (!file.exists())
                file.mkdir();
        } catch (InvalidPathException e) {
            ConsoleHelper.println("Не получилось найти домашнюю директорию.");
            e.printStackTrace();
            return;
        }
        logger.setUseParentHandlers(false);
        java.util.logging.Formatter formatter = new FormatterForServer();
        FileHandler fileHandler = new FileHandler("%h/FileServer_ID_port-8000(1)/Logs%u.log", 60 * 1024, 5, true);
        fileHandler.setFormatter(formatter);
        logger.addHandler(fileHandler);
        /*ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(formatter);
        logger.addHandler(consoleHandler);*/
    }

    //метод передачи директории серверу и его запуск.
    public static void main(String[] args) {
        if (args[0] != null && !args[0].equals("")) {
            Path directory;
            try {
                directory = Paths.get(args[0]);
            } catch (InvalidPathException e) {
                logger.log(Level.INFO, "", e);
                e.printStackTrace();
                return;
            }
            try {
                Server server = new Server(directory);
                Thread thread = new Thread(new ServerStopRunnable(server));
                thread.setDaemon(true);
                thread.start();
                server.run();
            } catch (BindException e) {
                logger.log(Level.INFO, "555555", e);
            } catch (IOException e) {
                logger.log(Level.INFO, "", e);
                //e.printStackTrace();
            } finally {
                executorForWrite.shutdown();
            }
        }
    }

    //основной метод работы сервера: обход соединений, их прием и передача выполнения операций записи и чтения
    //в другой поток.
    private void run() {
        try {
            selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(inetSocketAddress);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (isAlive) {
                while (selector.select() == 0) {
                    if (!isAlive) break;
                }
                keySet = selector.selectedKeys();
                for (Iterator<SelectionKey> it = keySet.iterator(); it.hasNext(); ) {
                    final SelectionKey key = it.next();
                    it.remove();
                    if (key.isAcceptable()) {
                        acceptClient(serverSocketChannel);
                    } else {
                        key.interestOps(0);
                        handleClient(key);
                        executorForRead.execute(() -> {
                            try {
                                handleClient(key);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }
        } catch (ClosedSelectorException e) {
            logger.log(Level.INFO, "Selector is closed", e);
        } catch (IOException e) {
            logger.log(Level.INFO, "", e);
        } finally {
            executorForRead.shutdown();
        }
    }

    //метод приема соединений и регистрации селектора для соединений.
    private void acceptClient(ServerSocketChannel ssc) throws IOException {
        SocketChannel client = ssc.accept();
        client.configureBlocking(false);
        SelectionKey key = client.register(selector, SelectionKey.OP_READ);
        ClientConnection clientConnection = new ClientConnection(client, key, path);
        key.attach(clientConnection);
    }

    //передача соединения на чтение
    private void handleClient(SelectionKey key) throws IOException {
        ClientConnection client = (ClientConnection) key.attachment();
        if (key.isReadable()) {
            client.read();
        }
        selector.wakeup();
    }

    //метод для остановки сервера
    public void toStopServer() throws IOException {
        isAlive = false;
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            for (SelectionKey key : keySet) {
                ClientConnection clientConnection = (ClientConnection) key.attachment();
                clientConnection.abortClient();
            }
        } catch (NullPointerException e) {
            logger.log(Level.INFO, "Нет активных подключений", e);
        }
        selector.close();
        executorForRead.shutdown();
        executorForWrite.shutdown();
    }

    //класс для обработки чтения и записи соеденений
    class ClientConnection {
        private SocketChannel client;
        private SelectionKey keyClient;
        private ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        private Pattern pattern = Pattern.compile("(?s)GET /?(\\S*).*");// паттерн запроса
        private Path directory;//директория для обзора файлов, устанавливается в свойствах запуска(конфиг)
        private String request;//для хранения запроса

        public ClientConnection(SocketChannel socketChannel, SelectionKey key, Path p) {
            client = socketChannel;
            keyClient = key;
            directory = p;
        }

        //процесс чтения запроса
        private void read() {
            try {
                buffer.clear();
                if (client.read(buffer) == -1 || buffer.get(buffer.position() - 1) == '\n') {
                    processReq();
                } else {
                    keyClient.interestOps(SelectionKey.OP_READ);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //обработка запроса
        private void processReq() {
            buffer.flip();
            request = charset.decode(buffer).toString();
            Matcher get = pattern.matcher(request);
            boolean b = get.matches();
            if (get.matches()) {
                request = get.group(1);
                try {
                    write(request);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        //процесс выдачи ответа исходя из запроса
        private void write(String request) throws IOException {
            if (request.startsWith(LISTFILES)) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                List<String> list = findListFiles(path);
                objectOutputStream.writeObject(list);
                client.write(ByteBuffer.wrap(byteArrayOutputStream.toByteArray()));
                client.write(charset.encode("\n"));
                objectOutputStream.close();
                keyClient.interestOps(SelectionKey.OP_READ);
            } else if (request.startsWith(FILE)) {
                executorForWrite.submit(
                        new SendData(Paths.get(directory.toString() + "\\" + request.split("_")[1]),
                                client, keyClient));
            } else if (request.startsWith(ABORT_CONNECTION)) {
                abortClient();
            }
        }

        public void abortClient() throws IOException {
            client.close();
            keyClient.cancel();
        }

        //метод для создания списка имен файлов в директории.
        private List<String> findListFiles(Path path) throws IOException {
            List<String> list = new ArrayList<>();
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(path)) {
                for (Path p : dir) {
                    if (p.toFile().isFile()) {
                        list.add(p.getFileName().toString());
                    }
                }
            }
            return list;
        }
    }
}


























