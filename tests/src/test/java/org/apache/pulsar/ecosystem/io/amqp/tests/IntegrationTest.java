/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.ecosystem.io.amqp.tests;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.message.JmsObjectMessage;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.junit.Assert;
import org.junit.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;


/**
 * Integration test.
 */
@Slf4j
public class IntegrationTest {

    @Test(timeout = 1000 * 60 * 5)
    public void test() throws Exception {
        Network network = Network.newNetwork();

        PulsarStandaloneContainer standaloneContainer = new PulsarStandaloneContainer(PulsarStandaloneContainer.IMAGE);
        standaloneContainer.setNetwork(network);
        standaloneContainer.withEnv("PULSAR_PREFIX_functionsWorkerEnabled", "true");
        String command = "bin/apply-config-from-env.py /pulsar/conf/standalone.conf "
                + "&& bin/pulsar standalone";
        standaloneContainer.withExposedPorts(8080);
        standaloneContainer.withCommand("sh", "-c", command);

        standaloneContainer.withClasspathResourceMapping(
                "pulsar-io-amqp1_0.nar", "/pulsar/connectors/pulsar-io-amqp1_0.nar", BindMode.READ_ONLY);
        standaloneContainer.withClasspathResourceMapping(
                "amqp1_0-source-config.yaml", "/pulsar/amqp1_0-source-config.yaml", BindMode.READ_ONLY);
        standaloneContainer.withClasspathResourceMapping(
                "amqp1_0-sink-config.yaml", "/pulsar/amqp1_0-sink-config.yaml", BindMode.READ_ONLY);

        standaloneContainer.start();
        log.info("standalone container start.");

        SolaceContainer solaceContainer = new SolaceContainer(SolaceContainer.IMAGE);
        solaceContainer.setNetwork(network);
        List<String> list = new ArrayList<>();
        list.add("solace");
        solaceContainer.setNetworkAliases(list);
        solaceContainer.withExposedPorts(5672, 8080);
        solaceContainer.withEnv("username_admin_globalaccesslevel", "admin");
        solaceContainer.withEnv("username_admin_password", "admin");
        solaceContainer.setShmSize(1024L * 1024 * 1024 * 2);
        solaceContainer.start();
        log.info("solace container start.");

        Container.ExecResult execResult = standaloneContainer.execInContainer(
                "/pulsar/bin/pulsar-admin",
                "sources", "create", "--source-config-file", "/pulsar/amqp1_0-source-config.yaml");
        Assert.assertEquals(execResult.getStdout().trim(), "\"Created successfully\"");
        waitForConnectorRunning(standaloneContainer, true, "amqp1_0-source");
        log.info("amqp1_0 source is running");

        execResult = standaloneContainer.execInContainer(
                "/pulsar/bin/pulsar-admin",
                "sinks", "create", "--sink-config-file", "/pulsar/amqp1_0-sink-config.yaml",
                "--custom-schema-inputs",
                "'{\"user-op-queue-topic\": \"org.apache.pulsar.client.impl.schema.ByteBufferSchema\"}'");
        Assert.assertEquals(execResult.getStdout().trim(), "\"Created successfully\"");
        waitForConnectorRunning(standaloneContainer, false, "amqp1_0-sink");
        log.info("amqp1_0 sink is running");

        String solaceRemoteUri = "amqp://" + solaceContainer.getHost() + ":" + solaceContainer.getMappedPort(5672);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        log.info("start verify data");
        verifyData(100, solaceRemoteUri, countDownLatch);

        log.info("start generate data");
        generateData(100, solaceRemoteUri);

        countDownLatch.await();
        log.info("Finish the integration test.");
    }

    private void generateData(int count, String remoteUri) {
        try {
            ConnectionFactory factory = new JmsConnectionFactory("guest", "guest", remoteUri);
            Destination queue = new JmsQueue("user-op-queue");

            Connection connection = factory.createConnection("guest", "guest");
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer messageProducer = session.createProducer(queue);

            long start = System.currentTimeMillis();
            for (int i = 1; i <= count; i++) {
                if (i % 2 == 0) {
                    TextMessage message = session.createTextMessage("Text - " + i);
                    messageProducer.send(message,
                            DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
                    log.info("send text message {}", i);
                } else {
                    ObjectMessage message = session.createObjectMessage();
                    message.setObject(new User("jack - " + i, i + 10));
                    messageProducer.send(message,
                            DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
                    log.info("Sent object message {}", i);
                }
            }

            long finish = System.currentTimeMillis();
            long taken = finish - start;
            log.info("Sent {} messages in {} ms.", count, taken);

            connection.close();
        } catch (Exception exp) {
//            System.out.println("Caught exception, exiting. error message " + exp.getMessage());
            log.error("Caught exception when producing messages, exiting.", exp);
            System.exit(1);
        }
    }

    private void verifyData(int count, String remoteUri, CountDownLatch countDownLatch) {
        new Thread(() -> {
            try {
                ConnectionFactory factory = new JmsConnectionFactory("guest", "guest", remoteUri);
                Destination queue = new JmsQueue("user-op-queue-pulsar");

                Connection connection = factory.createConnection("guest", "guest");
                connection.start();

                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer messageConsumer = session.createConsumer(queue);

                long start = System.currentTimeMillis();
                for (int i = 1; i <= count; i++) {
                    Message message = messageConsumer.receive();
                    if (message instanceof JmsTextMessage) {
                        Assert.assertEquals(((JmsTextMessage) message).getText(), "Text - " + i);
                        log.info("receive text message {} content {}", i, ((JmsTextMessage) message).getText());
                    } else if (message instanceof JmsObjectMessage) {
                        Assert.assertTrue(((JmsObjectMessage) message).getObject() instanceof User);
                        User user = (User) ((JmsObjectMessage) message).getObject();
                        Assert.assertEquals(user.name, "jack - " + i);
                        Assert.assertEquals(user.age, i + 10);
                        log.info("receive object message {} content {}",
                                i, ((JmsObjectMessage) message).getObject().toString());
                    }
                }
                log.info("Received {} messages in token {} ms.", count, System.currentTimeMillis() - start);
                connection.close();
                countDownLatch.countDown();
            } catch (Exception exp) {
//                System.out.println("Caught exception, exiting. error message " + exp.getMessage());
                log.error("Caught exception when receiving messages, exiting.", exp);
                Assert.fail("Failed to receive messages, error message: " + exp.getMessage());
            }
        }).start();
    }

    private void waitForConnectorRunning(PulsarStandaloneContainer standaloneContainer,
                                         boolean isSource, String name) throws Exception {
        Container.ExecResult execResult;
        do {
            execResult = standaloneContainer.execInContainer(
                    "/pulsar/bin/pulsar-admin",
                    isSource ? "sources" : "sinks", "status", "--name", name
            );
            Thread.sleep(1000);
        } while (!execResult.getStdout().contains("\"running\" : true"));
    }

    @ToString
    @AllArgsConstructor
    private static class User implements Serializable {
        String name;
        int age;
    }

}
