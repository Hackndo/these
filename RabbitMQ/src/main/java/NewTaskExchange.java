/**
 * Created by romain on 28/07/15.
 */
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;

import java.util.concurrent.TimeoutException;

public class NewTaskExchange {
    private final static String QUEUE_NAME = "task_queue";
    private final static String EXCHANGE_NAME = "logs";

    public static void main(String[] argv) throws java.io.IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // Create a non-durable, exclusive, auto-delete queue with a generated name
        String queueName = channel.queueDeclare().getQueue();


        /*
         * Create exchange.
         * fanout means it broadcasts it to all known queues
         */
        channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

        // Bind queue with exchange
        channel.queueBind(queueName, EXCHANGE_NAME, "");

        String message = getMessage(argv);

        channel.basicPublish(EXCHANGE_NAME,
                "",
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes());
        System.out.println(" [x] Sent '" + message + "'");
        channel.close();
        connection.close();
    }

    private static String getMessage(String[] strings){
        if (strings.length < 1)
            return "Hello World!";
        return joinStrings(strings, " ");
    }

    private static String joinStrings(String[] strings, String delimiter) {
        int length = strings.length;
        if (length == 0) return "";
        StringBuilder words = new StringBuilder(strings[0]);
        for (int i = 1; i < length; i++) {
            words.append(delimiter).append(strings[i]);
        }
        return words.toString();
    }
}
