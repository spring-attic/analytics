/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.stream.app.counter.sink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
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
				"logging.level.*=INFO",
		})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class CounterSinkIntegrationTests {

	@Autowired
	protected Sink sink;

	@Autowired
	protected SimpleMeterRegistry meterRegistry;

	@TestPropertySource(properties = {
			"counter.name=counter666"
	})
	public static class PlainCounterNameTests extends CounterSinkIntegrationTests {

		@Test
		public void testCounterSink() {
			IntStream.range(0, 13).forEach(i -> sink.input().send(new GenericMessage("hello")));
			assertThat(meterRegistry.find("message.counter666").counter().count(), is(13.0));
		}
	}

	@TestPropertySource(properties = {
			"counter.name-expression=payload"
	})
	public static class ExpressionCounterNameTests extends CounterSinkIntegrationTests {

		@Test
		public void testCounterSink() {
			IntStream.range(0, 13).forEach(i -> sink.input().send(new GenericMessage("hello")));
			assertThat(meterRegistry.find("message.hello").counter().count(), is(13.0));
		}
	}


	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.fixed.foo=bar",
			"counter.tag.fixed.gork=bork"
	})
	public static class FixedTagsTests extends CounterSinkIntegrationTests {

		@Test
		public void testCounterSink() {

			IntStream.range(0, 13).forEach(i -> sink.input().send(new GenericMessage("hello")));

			Meter counterMeter = meterRegistry.find("message.counter666").meter();

			assertThat(StreamSupport.stream(counterMeter.measure().spliterator(), false)
					.mapToDouble(m -> m.getValue()).sum(), is(13.0));

			assertThat(counterMeter.getId().getTags().size(), is(3));
			assertThat(counterMeter.getId().getTag("foo"), is("bar"));
			assertThat(counterMeter.getId().getTag("gork"), is("bork"));
			assertThat(counterMeter.getId().getTag("counterType"), is("message"));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.expression.foo='bar'",
			"counter.tag.expression.gork='bork'"
	})
	public static class CounterWithNameAndTagsTests2 extends CounterSinkIntegrationTests {

		@Test
		public void testCounterSink() {

			for (int i = 0; i < 13; i++) {
				sink.input().send(new GenericMessage("hello"));
			}

			Counter fooCounter = meterRegistry.find("counter666").tag("foo", "bar").counter();
			assertThat(fooCounter.count(), is(13.0));

			Counter gorkCounter = meterRegistry.find("counter666").tag("gork", "bork").counter();
			assertThat(gorkCounter.count(), is(13.0));

			assertThat(fooCounter.getId(), equalTo(gorkCounter.getId()));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.fields=test,test2"
	})
	public static class FieldValueCounterJsonTests extends CounterSinkIntegrationTests {

		@Test
		public void testCounterSink() {

			sink.input().send(message("{\"test\": \"Bar\"}"));
			sink.input().send(message("{\"test\": \"Foo\"}"));
			sink.input().send(message("{\"test\": \"Bar\"}"));

			sink.input().send(message("{\"test2\": [\"Gork\", \"Gork\", \"Gork\"]}"));

			Counter fooCounter = meterRegistry.find("counter666").tag("test", "Foo").counter();
			assertThat(fooCounter.count(), is(1.0));

			Counter barCounter = meterRegistry.find("counter666").tag("test", "Bar").counter();
			assertThat(barCounter.count(), is(2.0));

			Counter gorkCounter = meterRegistry.find("counter666").tag("test2", "Gork").counter();
			assertThat(gorkCounter.count(), is(3.0));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.fixed.foo=",
			"counter.tag.fields=noField",
			"counter.tag.expression.tag666=#jsonPath(payload,'$..noField')",
			"counter.tag.expression.test=#jsonPath(payload,'$..test')",
	})
	public static class EmptyTagsTests extends CounterSinkIntegrationTests {

		@Test
		public void testCounterSink() {

			sink.input().send(message("{\"test\": \"Bar\"}"));

			Collection<Counter> fixedTagsCounters = meterRegistry.find("counter666").tagKeys("foo").counters();
			assertThat(fixedTagsCounters.size(), is(0));

			Collection<Counter> fieldsTagsCounters = meterRegistry.find("counter666").tagKeys("noField").counters();
			assertThat(fieldsTagsCounters.size(), is(0));

			Collection<Counter> expressionTagsCounters = meterRegistry.find("counter666").tagKeys("tag666").counters();
			assertThat(expressionTagsCounters.size(), is(0));

			Collection<Counter> testExpTagsCounters = meterRegistry.find("counter666").tagKeys("test").counters();
			assertThat(testExpTagsCounters.size(), is(1));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=books",
			"counter.tag.expression.category=#jsonPath(payload,'$..category')",
			"counter.tag.expression.author=#jsonPath(payload,'$..author')"
	})
	public static class FieldValueCounterJsonTests2 extends CounterSinkIntegrationTests {

		@Test
		public void testCounterSink() {

			sink.input().send(message(jsonBooksStore));

			Collection<Counter> referenceCounters = meterRegistry.find("books").tag("category", "reference").counters();
			assertThat(referenceCounters.stream().mapToDouble(c -> c.count()).sum(), is(1.0));

			Collection<Counter> fictionCounters = meterRegistry.find("books").tag("category", "fiction").counters();
			assertThat(fictionCounters.stream().mapToDouble(c -> c.count()).sum(), is(4.0));

			Collection<Counter> authorTolkienCounters = meterRegistry.find("books").tag("author", "J. R. R. Tolkien").counters();
			assertThat(authorTolkienCounters.stream().mapToDouble(c -> c.count()).sum(), is(2.0));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.fields=test"
	})
	public static class FieldValueCounterPojoTests2 extends CounterSinkIntegrationTests {

		@Test
		public void testCounterSink() {

			TestPojoList testPojo = new TestPojoList();
			List<String> test = new ArrayList<>();
			test.add("Foo");
			test.add("Bar");
			test.add("Foo");
			testPojo.setTest(test);

			Message<TestPojoList> message = MessageBuilder.withPayload(testPojo).build();
			sink.input().send(message);

			Counter fooCounter = meterRegistry.find("counter666").tag("test", "Foo").counter();
			assertThat(fooCounter.count(), is(2.0));

			Counter barCounter = meterRegistry.find("counter666").tag("test", "Bar").counter();
			assertThat(barCounter.count(), is(1.0));
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(CounterSinkConfiguration.class)
	public static class TestCounterSinkApplication {

		//@Bean
		//public Object myMockBean() {
		// Create here your custom Mock instances to be used with this ITests
		//}
	}

	private static class TestPojoList {
		private List<String> test;

		public List<String> getTest() {
			return this.test;
		}

		public void setTest(List<String> test) {
			this.test = test;
		}
	}

	private static Message<byte[]> message(String payload) {
		return MessageBuilder.withPayload(payload.getBytes()).build();
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
