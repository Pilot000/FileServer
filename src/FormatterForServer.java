import java.util.Date;
import java.util.Properties;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;


//класс для форматировния логов
public class FormatterForServer extends Formatter {
    public static String newLine;

    //устанавливаем разделитель строк такой же как у системы
    static {
        Properties properties = System.getProperties();
        newLine = properties.getProperty("line.separator");
    }

    @Override
    public String format(LogRecord record) {
        String s = String.format("%1$td-%1$tm-%1$tY %1$tT %2$s %3$s" + newLine +
                        "%4$s: %5$s" + newLine, new Date(record.getMillis()), record.getSourceClassName(), record.getSourceMethodName(),
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
