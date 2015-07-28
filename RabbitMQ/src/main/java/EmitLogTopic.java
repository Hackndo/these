/**
 * Created by romain on 28/07/15.
 */

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.util.concurrent.TimeoutException;

public class EmitLogTopic {
    private final static String EXCHANGE_NAME = "topic_logs";

    public static void main(String[] argv) throws java.io.IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        String severity = getSeverity(argv);
        String message = getMessage(argv);
        channel.basicPublish(EXCHANGE_NAME,
                severity,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes());
        System.out.println(" [x] Sent '[" + severity + "] " + message + "'");
        channel.close();
        connection.close();
    }

    private static String getSeverity(String[] strings){
        if (strings.length < 2)
            return "INFO";
        return strings[0];
    }

    private static String getMessage(String[] strings){
        if (strings.length < 1)
            return "Hello World!";
        return joinStrings(strings, " ");
    }

    private static String joinStrings(String[] strings, String delimiter) {
        int length = strings.length;
        if (length < 2) return "";
        StringBuilder words = new StringBuilder(strings[1]);
        for (int i = 2; i < length; i++) {
            words.append(delimiter).append(strings[i]);
        }
        return words.toString();
    }
}
