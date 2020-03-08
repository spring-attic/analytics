/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.analytics.common;

import java.util.Collection;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.config.SpelExpressionConverterConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNull;
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
public abstract class CounterCommonTests {

	@Autowired
	protected SimpleMeterRegistry meterRegistry;

	@Autowired
	protected CounterService counterService;

	@Test
	public void testCounterSink() {
		assertThat(meterRegistry, notNullValue());
	}

	@TestPropertySource(properties = {
			"counter.name=counter666"
	})
	public static class PlainCounterNameTests extends CounterCommonTests {

		@Test
		public void testCounterSink() {
			IntStream.range(0, 13).forEach(i -> counterService.count(new GenericMessage("hello")));
			assertThat(meterRegistry.find("message.counter666").counter().count(), is(13.0));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.expression.foo='bar'",
			"counter.amount-expression=payload.length()"
	})
	public static class CountWithAmountTest extends CounterCommonTests {

		@Test
		public void testCounterSink() {
			String message = "hello world message";
			double messageSize = Long.valueOf(message.length()).doubleValue();
			counterService.count(new GenericMessage(message));
			assertThat(meterRegistry.find("message.counter666").counter().count(), is(1.0));
			assertThat(meterRegistry.find("counter666").counter().count(), is(messageSize));
		}
	}

	@TestPropertySource(properties = {
			"counter.name-expression=payload"
	})
	public static class ExpressionCounterNameTests extends CounterCommonTests {

		@Test
		public void testCounterSink() {
			IntStream.range(0, 13).forEach(i -> counterService.count(new GenericMessage("hello")));
			assertThat(meterRegistry.find("message.hello").counter().count(), is(13.0));
		}
	}


	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.fixed.foo=bar",
			"counter.tag.fixed.gork=bork"
	})
	public static class FixedTagsTests extends CounterCommonTests {

		@Test
		public void testCounterSink() {

			IntStream.range(0, 13).forEach(i -> counterService.count(new GenericMessage("hello")));

			Meter counterMeter = meterRegistry.find("message.counter666").meter();

			assertThat(StreamSupport.stream(counterMeter.measure().spliterator(), false)
					.mapToDouble(m -> m.getValue()).sum(), is(13.0));

			assertThat(counterMeter.getId().getTags().size(), is(2));
			assertThat(counterMeter.getId().getTag("foo"), is("bar"));
			assertThat(counterMeter.getId().getTag("gork"), is("bork"));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.expression.foo='bar'",
			"counter.tag.expression.gork='bork'"
	})
	public static class LiteralTagExpressionsTests extends CounterCommonTests {

		@Test
		public void testCounterSink() {

			IntStream.range(0, 13).forEach(i -> counterService.count(new GenericMessage("hello")));

			Counter fooCounter = meterRegistry.find("counter666").tag("foo", "bar").counter();
			assertThat(fooCounter.count(), is(13.0));

			Counter gorkCounter = meterRegistry.find("counter666").tag("gork", "bork").counter();
			assertThat(gorkCounter.count(), is(13.0));

			assertThat(fooCounter.getId(), equalTo(gorkCounter.getId()));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.fixed.foo=",
			"counter.tag.expression.tag666=#jsonPath(payload,'$..noField')",
			"counter.tag.expression.test=#jsonPath(payload,'$..test')",
	})
	public static class EmptyTagsTests extends CounterCommonTests {

		@Test
		public void testCounterSink() {

			counterService.count(message("{\"test\": \"Bar\"}"));

			Collection<Counter> fixedTagsCounters = meterRegistry.find("counter666").tagKeys("foo").counters();
			assertThat(fixedTagsCounters.size(), is(0));

			Collection<Counter> expressionTagsCounters = meterRegistry.find("counter666").tagKeys("tag666").counters();
			assertThat(expressionTagsCounters.size(), is(0));

			Collection<Counter> testExpTagsCounters = meterRegistry.find("counter666").tagKeys("test").counters();
			assertThat(testExpTagsCounters.size(), is(1));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.tag.fixed.foo=",
			"counter.tag.expression.tag666=#jsonPath(payload,'$..noField')",
			"counter.tag.expression.test=#jsonPath(payload,'$..test')",
	})
	public static class NullTagsTests extends CounterCommonTests {

		@Test
		public void testCounterSink() {

			counterService.count(message("{\"test\": null}"));

			Collection<Counter> fixedTagsCounters = meterRegistry.find("counter666").tagKeys("foo").counters();
			assertThat(fixedTagsCounters.size(), is(0));

			Collection<Counter> expressionTagsCounters = meterRegistry.find("counter666").tagKeys("tag666").counters();
			assertThat(expressionTagsCounters.size(), is(0));

			Collection<Counter> testExpTagsCounters = meterRegistry.find("counter666").tagKeys("test").counters();
			assertThat(testExpTagsCounters.size(), is(0));
		}
	}

	@TestPropertySource(properties = {
			"counter.name=counter666",
			"counter.messageCounterEnabled=false"
	})
	public static class DisabledMessageCounterTests extends CounterCommonTests {

		@Test
		public void testCounterSink() {
			IntStream.range(0, 13).forEach(i -> counterService.count(new GenericMessage("hello")));
			assertNull(meterRegistry.find("message.counter666").counter());
		}
	}

	private static Message<byte[]> message(String payload) {
		return MessageBuilder.withPayload(payload.getBytes()).build();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({ CounterCommonConfiguration.class, SpelExpressionConverterConfiguration.class })
	public static class TestCounterSinkApplication {

		//@Bean
		//public Object myMockBean() {
		// Create here your custom Mock instances to be used with this ITests
		//}
	}

}
