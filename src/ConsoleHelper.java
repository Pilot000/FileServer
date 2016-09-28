import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

//класс для взаимодействия с консолью
public class ConsoleHelper {
    public static String newLine;

    //устанавливаем разделитель строк такой же как у системы
    static {
        Properties properties = System.getProperties();
        newLine = properties.getProperty("line.separator");
    }

    //вывод сообщения перед вводом пользователя
    public static void printInfoUser() {
        System.out.println("Выберите опцию(только номер)" + newLine +
                "1.Запрос списка доступных файлов." + newLine +
                "2.Скачать один файл и сохранить его локально." + newLine +
                "(при выборе опции через пробел укажите имя файла)" + newLine +
                "3.Завершить сессию.");
        System.out.println();
    }

    //вывод строки в консоль
    public static void println(String message) {
        System.out.println(message);
    }

    //вывод списка в консоль
    public static void println(List<String> list) {
        for (String s : list) {
            System.out.println(s);
        }
    }

    //класс для форматировния логов
    static class FormatterForClient extends Formatter {

        @Override
        public String format(LogRecord record) {
            String s = String.format("%1$td-%1$tm-%1$tY %1$tT %2$s %3$s" + newLine +
                            "%4$s: %5$s" + newLine,
                    new Date(record.getMillis()), record.getSourceClassName(), record.getSourceMethodName(),
                    record.getLevel(), record.getMessage());
            if (record.getThrown() != null) {
                StringBuilder builder = new StringBuilder(s);
                builder.append(record.getThrown().toString());
                builder.append(newLine);
                for (StackTraceElement st : record.getThrown().getStackTrace()) {
                    builder.append(st.toString());
                    builder.append(newLine);
                }
                s = builder.toString();
            }
            return s;
        }
    }
}
