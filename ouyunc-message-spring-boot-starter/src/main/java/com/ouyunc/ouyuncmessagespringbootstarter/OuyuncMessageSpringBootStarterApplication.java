package com.ouyunc.ouyuncmessagespringbootstarter;

import com.ouyunc.message.MessageServer;
import com.ouyunc.message.StandardMessageServer;
import com.ouyunc.message.StartServer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OuyuncMessageSpringBootStarterApplication implements ApplicationRunner {

    private static String[] args1;

    public static void main(String[] args) {
        args1 = args;
        SpringApplication.run(OuyuncMessageSpringBootStarterApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        MessageServer server = new StandardMessageServer();
        server.start(args1);
    }
}
