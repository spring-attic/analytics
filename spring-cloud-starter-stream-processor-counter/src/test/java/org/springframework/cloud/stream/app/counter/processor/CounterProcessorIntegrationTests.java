/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.counter.processor;

import java.util.Collection;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.annotation.Import;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Christian Tzolov
 */
@SuppressWarnings("SpringJavaAutowiringInspection")
@RunWith(SpringRunner.class)
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"debug=false",
				"logging.level.*=INFO",
		})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class CounterProcessorIntegrationTests {

	@Autowired
	protected Processor channels;

	@Autowired
	protected MessageCollector messageCollector;

	@Autowired
	protected SimpleMeterRegistry meterRegistry;

	@TestPropertySource(properties = {
			"counter.name=books",
			"counter.tag.expression.category=#jsonPath(payload,'$..category')",
			"counter.tag.expression.author=#jsonPath(payload,'$..author')"
	})
	public static class CounterPayloadTests extends CounterProcessorIntegrationTests {

		@Test
		public void testOne() {

			channels.input().send(MessageBuilder.withPayload(jsonBooksStore.getBytes()).build());

			Message<?> received = messageCollector.forChannel(channels.output()).poll();

			assertThat(received.getPayload().toString(), equalTo(jsonBooksStore));

			Collection<Counter> referenceCounters =
					meterRegistry.find("books").tag("category", "reference").counters();
			assertThat(referenceCounters.stream().mapToDouble(c -> c.count()).sum(), is(1.0));

			Collection<Counter> fictionCounters =
					meterRegistry.find("books").tag("category", "fiction").counters();
			assertThat(fictionCounters.stream().mapToDouble(c -> c.count()).sum(), is(4.0));

			Collection<Counter> authorTolkienCounters =
					meterRegistry.find("books").tag("author", "J. R. R. Tolkien").counters();
			assertThat(authorTolkienCounters.stream().mapToDouble(c -> c.count()).sum(), is(2.0));
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(CounterProcessorConfiguration.class)
	public static class TestCounterProcessorApplication {

	}

	private static Message<byte[]> message(String payload) {
		return org.springframework.messaging.support.MessageBuilder.withPayload(payload.getBytes()).build();
	}

	private static String jsonBooksStore = "{ \"store\": {\n" +
			"    \"book\": [ \n" +
			"      { \"category\": \"reference\",\n" +
			"        \"author\": \"Nigel Rees\",\n" +
			"        \"title\": \"Sayings of the Century\",\n" +
			"        \"price\": 8.95\n" +
			"      },\n" +
			"      { \"category\": \"fiction\",\n" +
			"        \"author\": \"Evelyn Waugh\",\n" +
			"        \"title\": \"Sword of Honour\",\n" +
			"        \"price\": 12.99\n" +
			"      },\n" +
			"      { \"category\": \"fiction\",\n" +
			"        \"author\": \"Herman Melville\",\n" +
			"        \"title\": \"Moby Dick\",\n" +
			"        \"isbn\": \"0-553-21311-3\",\n" +
			"        \"price\": 8.99\n" +
			"      },\n" +
			"      { \"category\": \"fiction\",\n" +
			"        \"author\": \"J. R. R. Tolkien\",\n" +
			"        \"title\": \"The Lord of the Rings\",\n" +
			"        \"isbn\": \"0-395-19395-8\",\n" +
			"        \"price\": 22.99\n" +
			"      },\n" +
			"      { \"category\": \"fiction\",\n" +
			"        \"author\": \"J. R. R. Tolkien\",\n" +
			"        \"title\": \"The Hobbit\",\n" +
			"        \"isbn\": \"0-395-19395-8\",\n" +
			"        \"price\": 22.99\n" +
			"      }\n" +
			"    ],\n" +
			"    \"bicycle\": {\n" +
			"      \"color\": \"red\",\n" +
			"      \"price\": 19.95\n" +
			"    }\n" +
			"  }\n" +
			"}";

}
