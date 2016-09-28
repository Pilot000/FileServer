import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.logging.Level;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

//класс для отправки данных в новом потоке
public class SendData implements Runnable {
    private Path file;
    private SocketChannel client;
    private SelectionKey keyClient;
    private Charset charset = Charset.forName("8859_1");//кодировка для запросов, ответов(текстовых)

    public SendData(Path p, SocketChannel sc, SelectionKey key) {
        file = p;
        client = sc;
        keyClient = key;
    }

    //отправка файлов
    @Override
    public void run() {
        try {
            FileInputStream fileInputStream = new FileInputStream(file.toString());
            try {

                byte[] b = new byte[5 * 1024];
                while (fileInputStream.available() > 0) {
                    fileInputStream.read(b);
                    client.write(ByteBuffer.wrap(b));
                }
                client.write(charset.encode("END"));
                saveStatistic(file.toString());
            } finally {
                fileInputStream.close();
                keyClient.interestOps(SelectionKey.OP_READ);
            }
        } catch (FileNotFoundException e) {
            Server.logger.log(Level.WARNING, "Файл не найден.", e);
            String response = "404 Файл не найден";
            try {
                client.write(Charset.forName("UTF-8").encode("Noneplit" + response));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //метод для записи статистики в файл
    public void saveStatistic(String fileName) {
        try {
            String homeWay = System.getProperties().getProperty("user.home");
            File statistic = new File(homeWay + "\\FileServerStatistic.txt");
            File tempFile = new File(homeWay + "\\myTempFile.txt");
            if (!statistic.exists()) {
                try {
                    statistic.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            tempFile.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
            FileInputStream fileInputStream = new FileInputStream(statistic);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            try {
                boolean overlap = false;
                if (fileInputStream.available() > 0) {
                    while (bufferedReader.ready()) {
                        String s = bufferedReader.readLine();
                        String mas[] = s.split(" ");
                        if (mas[0].equalsIgnoreCase(fileName)) {
                            writer.write(mas[0] + " " + (Integer.parseInt(mas[1]) + 1) + FormatterForServer.newLine);
                            writer.flush();
                            overlap = true;
                            continue;
                        }
                        writer.write(s + FormatterForServer.newLine);
                        writer.flush();
                    }
                }
                if (!overlap) {
                    writer.write(fileName + " " + 1 + FormatterForServer.newLine);
                    writer.flush();
                }
            } finally {
                bufferedReader.close();
                writer.close();
                fileInputStream.close();
            }
            Files.move(tempFile.toPath(), statistic.toPath(), REPLACE_EXISTING);
        } catch (FileNotFoundException e) {
            Server.logger.log(Level.INFO, "Файл для записи статистики не найден.", e);
        } catch (IOException e) {
            Server.logger.log(Level.INFO, "Данные статистики не были записаны.", e);
        }
    }
}























