/**
 * Created by romain on 28/07/15.
 */
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;

import java.util.concurrent.TimeoutException;

public class Worker {
    private final static String QUEUE_NAME = "task_queue";
    private final static int sleepTime = 3000;

    public static void main(String[] argv)
            throws java.io.IOException,
            java.lang.InterruptedException, TimeoutException {

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // Save messages to disk even if RabbitMQ is down
        boolean durable = true;
        channel.queueDeclare(QUEUE_NAME, durable, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C [Sleep time : " + sleepTime + "]");
        QueueingConsumer consumer = new QueueingConsumer(channel);

        // A worker has to ack when it has finished processing a message
        boolean autoAck = false;

        channel.basicConsume(QUEUE_NAME, autoAck, consumer);

        // One message at a time. While it's processing a message, it wont receive any new message
        channel.basicQos(1);

        while (true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
            String message = new String(delivery.getBody());
            System.out.println(" [x] Received '" + message + "'");
            dotWork(message);
            System.out.println(" [x] Processed '" + message + "'");
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(),false);
        }
    }

    private static void dotWork(String message) throws InterruptedException {
        for (char ch: message.toCharArray()) {
            if (ch == '.') {
                Thread.sleep(sleepTime);
            }
        }
    }
}
