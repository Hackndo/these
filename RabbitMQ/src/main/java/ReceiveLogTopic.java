import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ReceiveLogTopic {

    private static final String EXCHANGE_NAME = "topic_logs";

    public static void main(String[] argv)
            throws IOException,
            InterruptedException, TimeoutException {

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName = channel.queueDeclare().getQueue();

        if (argv.length < 1){
            System.err.println("Usage: ReceiveLogDirect [binding_key]");
            System.exit(1);
        }

        for(String binding_key : argv){
            channel.queueBind(queueName, EXCHANGE_NAME, binding_key);
        }

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(queueName, true, consumer);
/*
Salut,
Je viens remercier l'auteur de l'épreuve très instructive et intéressante. Par ailleurs, j'ajoute mon grain de sel

Pour l'authent, avec le file_get_contents, j'ai utilisé le wrapper php://input qui me permettait d'avoir comme résultat une chaine contenant les variables passées en POST.
Il fallait alors envoyer un POST intéressant, ce que j'ai fait en envoyant :
a: a.0cc175b9c0f1b6a831c399e269772661.
login: a=a
password: a
auth: php://input

Ainsi, la chaine reçue par file_get_contents est
a=a.0cc175b9c0f1b6a831c399e269772661.&login=a%3Da&password=a&auth=php://input
Donc avec les replace, on a dans le champ 0 un 'a=a' et dans le champ 1 le md5 (qui est le md5 de 'a')
Et ça passait :)

Voilà, sinon le reste classiquement comme tout le monde

 */
        while (true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
            String message = new String(delivery.getBody());
            String routingKey = delivery.getEnvelope().getRoutingKey();
            System.out.println(" [x] Received '[" + routingKey + "] " + message + "'");
        }
    }
}