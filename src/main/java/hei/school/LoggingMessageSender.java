package hei.school;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoggingMessageSender implements MessageSender {

    @Value("${kilasy.sms.fail:false}")
    private boolean fail;

    @Override
    public void send(String phone, String message) {
        if (fail) {
            throw new IllegalStateException("Panne SMS simulée");
        }
        System.out.println("SMS simulé à " + phone + " : " + message);
    }
}
