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
package org.apache.pulsar.ecosystem.io.amqp;

import java.util.HashMap;
import java.util.Map;
import javax.jms.JMSContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * Amqp source config test.
 */
public class AmqpSourceConfigTest {

    @Test
    public void testDefaultSessionMode() throws Exception {
        Map<String, Object> paramsMap = getBaseConfig();
        AmqpSourceConfig sourceConfig = AmqpSourceConfig.load(paramsMap);
        sourceConfig.validate();

        Assert.assertEquals(JMSContext.AUTO_ACKNOWLEDGE, sourceConfig.getSessionMode());
    }

    @Test
    public void testClientAcknowledgeSessionMode() throws Exception {
        Map<String, Object> paramsMap = getBaseConfig();
        paramsMap.put("sessionMode", 2);
        AmqpSourceConfig sourceConfig = AmqpSourceConfig.load(paramsMap);
        sourceConfig.validate();

        Assert.assertEquals(JMSContext.CLIENT_ACKNOWLEDGE, sourceConfig.getSessionMode());
    }

    private Map<String, Object> getBaseConfig() {
        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("protocol", "amqp");
        paramsMap.put("host", "localhost");
        paramsMap.put("port", 5672);
        paramsMap.put("queue", "test-queue");

        return paramsMap;
    }
}
