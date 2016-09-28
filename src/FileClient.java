import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;

//класс клиента для скачивания файлов, для запуска нужен ConsoleHelper
public class FileClient {
    private ByteBuffer byteBuffer = ByteBuffer.allocate(64 * 1024);
    public static Logger logger = Logger.getLogger("Client");
    private SocketChannel channel;
    private SelectionKey keyClient;
    private int port = 8000;//порт для подключения
    private InetSocketAddress inetSocketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    private String LISTFILES = "listFiles"; // константы запросов
    private String FILE = "file";
    private String ABORT_CONNECTION = "abort_connection";
    private List<String> list; //список файлов для повторного просмотра данных
    private Path p;// домашний путь
    private Charset charset = Charset.forName("UTF-8");//кодировка для запросов, ответов(текстовых)

    //находим домашнюю директорию системы, создаем настройки для логгера.
    public FileClient() throws IOException {
        Properties properties = System.getProperties();
        String s = properties.getProperty("user.home");
        try {
            p = Paths.get(s);
            File file = new File(p.toString() + "\\FileClient");
            if (!file.exists())
                file.mkdir();
        } catch (InvalidPathException e) {
            ConsoleHelper.println("Не получилось найти домашнюю директорию.");
            e.printStackTrace();
            return;
        }
        logger.setUseParentHandlers(false);
        Formatter formatter = new ConsoleHelper.FormatterForClient();
        FileHandler fileHandler = new FileHandler("%h/FileClient/Logs%u.log", 60 * 1024, 5, true);
        fileHandler.setFormatter(formatter);
        logger.addHandler(fileHandler);
        /*ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(formatter);
        logger.addHandler(consoleHandler);*///включить для отладки
    }

    //запуск клиента
    public static void main(String[] args) {
        try {
            new FileClient().run();
        } catch (IOException e) {
            logger.log(Level.INFO, "", e);
        }
    }

    //основной метод работы клиента: подключение, получение и передача данных.
    public void run() throws IOException {
        Selector selector = Selector.open();
        channel = SocketChannel.open();
        channel.configureBlocking(false);
        keyClient = channel.register(selector, SelectionKey.OP_CONNECT);
        logger.info("Попытка подключения");
        channel.connect(inetSocketAddress);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String optionForRead = ""; //опция для выбора подгоотовки к чтению

        try {
            while (channel.isOpen()) {
                while (selector.select() == 0) {
                    if (!channel.isOpen()) {
                        break;
                    }
                }
                Set<SelectionKey> keySet = selector.selectedKeys();
                for (Iterator<SelectionKey> it = keySet.iterator(); it.hasNext(); ) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (key.isWritable()) {
                        while (true) {
                            ConsoleHelper.printInfoUser();
                            String request = bufferedReader.readLine();
                            //исходя от выбора пользователя отправляем запрос, потом
                            // выбираем вариант подготовки для принятия данных
                            if (request.startsWith("1")) {
                                if (list == null) {
                                    channel.write(charset.encode("GET " + LISTFILES + "\n"));
                                    key.interestOps(SelectionKey.OP_READ);
                                    break;
                                }
                                ConsoleHelper.println(list);
                            } else if (request.startsWith("2")) {
                                String mas[] = request.split(" ");
                                if (mas.length > 1) {
                                    channel.write(charset.encode("GET " + FILE + "_" + mas[1] + "\n"));
                                    key.attach(mas[1]);
                                    optionForRead = request;
                                    key.interestOps(SelectionKey.OP_READ);
                                    break;
                                } else {
                                    ConsoleHelper.println("Not correct input");
                                }
                            } else if (request.startsWith("3")) {
                                channel.write(charset.encode("GET " + ABORT_CONNECTION + "\n"));
                                selector.close();
                                break;
                            } else {
                                ConsoleHelper.println("Try one more time, input not correct");
                            }
                        }
                    } else if (key.isReadable()) {
                        read(optionForRead);
                    } else if (key.isConnectable()) {
                        channel.finishConnect();
                        keyClient.interestOps(SelectionKey.OP_WRITE);
                    }
                }
            }
        } catch (ClosedChannelException e) {
            logger.log(Level.INFO, "Закрытие соединения", e);

        } catch (ClosedSelectorException e) {
            logger.log(Level.INFO, "Закрытие соединения", e);

        } finally {
            ConsoleHelper.println("Закрытие клиента...");
            bufferedReader.close();
        }
    }

    // метод для отбора конктретного метода для закачки данных
    public void read(String optionForRead) throws IOException {
        //читаем в буфер пока не получим данные
        while (channel.read(byteBuffer) == 0) {
            byteBuffer.clear();
        }
        byteBuffer.flip();
        String s = charset.decode(byteBuffer).toString();
        //для выовда ответа от сервера используем заголовок None(нет запрашиваемых данных)
        if (!s.startsWith("None")) {
            if (optionForRead.startsWith("2")) {
                //получаем файл
                catchFile(Paths.get(p.toString() + "\\" + (String) keyClient.attachment()), channel, s);
                optionForRead = "";
            } else {
                //читаем список
                readObject(channel, s);
                ConsoleHelper.println(list);

            }
        } else {
            //вывод негативного ответа сервера
            String mas[] = s.split("plit");
            ConsoleHelper.println(mas[1]);
        }
        byteBuffer.clear();
        keyClient.interestOps(SelectionKey.OP_WRITE);
    }

    //метод для скачивания файла
    public void catchFile(Path file, SocketChannel channel, String s) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file.toString());
            while (true) {
                if (s.endsWith("END")) {
                    fileOutputStream.write(
                            ByteBuffer.wrap(byteBuffer.array(), 0, byteBuffer.limit() - 3).array());
                    fileOutputStream.flush();
                    break;
                }
                fileOutputStream.write(byteBuffer.array());
                fileOutputStream.flush();
                byteBuffer.clear();
                channel.read(byteBuffer);
                byteBuffer.flip();
                s = charset.decode(byteBuffer).toString();
            }
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            logger.log(Level.INFO, "Путь для сохранения файла некорректен", e);
            //e.printStackTrace();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Загрузка файла прервана.", e);
            //e.printStackTrace();
        }

    }

    //метод для скачивания списка файлов
    public void readObject(SocketChannel channel, String s) throws IOException {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try {
                while (true) {
                    try {
                        if (byteBuffer.get(byteBuffer.position() - 1) == '\n') {
                            byteArrayOutputStream.write(
                                    ByteBuffer.wrap(byteBuffer.array(), 0, byteBuffer.limit() - 1).array());
                            break;
                        }
                        byteArrayOutputStream.write(byteBuffer.array());
                        byteBuffer.clear();
                        channel.read(byteBuffer);
                    } catch (IndexOutOfBoundsException e) {
                        //трудноуловимый баг, дебаг не помогает.
                        //связан с буфером и чтением из канала
                        byteBuffer.clear();
                        channel.read(byteBuffer);
                        logger.log(Level.INFO, "", e);
                    }
                }
                ByteArrayInputStream byteArrayInputStream
                        = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                Object o = null;
                try {
                    o = objectInputStream.readObject();
                } catch (ClassNotFoundException e) {
                    logger.log(Level.WARNING, "Не найден класс для сериализации.", e);
                }
                objectInputStream.close();
                if (o instanceof List) {
                    list = (List<String>) o;
                }
            } finally {
                byteArrayOutputStream.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Загрузка списка прервана.", e);
        }
    }
}
