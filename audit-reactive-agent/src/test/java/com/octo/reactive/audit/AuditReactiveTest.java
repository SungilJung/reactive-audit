/*
 * Copyright 2014 OCTO Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.octo.reactive.audit;

import com.octo.reactive.audit.lib.AuditReactiveException;
import com.octo.reactive.audit.lib.FileAuditReactiveException;
import com.octo.reactive.audit.lib.Latency;
import com.octo.reactive.audit.lib.SuppressAuditReactive;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import static com.octo.reactive.audit.AuditReactive.*;
import static com.octo.reactive.audit.lib.Latency.*;
import static org.junit.Assert.*;


@SuppressWarnings({"MethodOnlyUsedFromInnerClass", "ResultOfMethodCallIgnored"})
public class AuditReactiveTest
{
	// Because the Aspectj must use the config singleton,
	// it's not possible to inject a specific config instance
	private final AuditReactive config = AuditReactive.config;

	private final int[]   log     = new int[1];
	private final Handler handler = new Handler()
	{
		@Override
		public void publish(LogRecord record)
		{
			if (record.getLevel() != Level.FINE)
				++log[0];
		}

		@Override
		public void flush()
		{
		}

		@Override
		public void close()
		{
		}
	};


	@Test
	public void currentThread_test()
	{
		strict.commit();
		assertTrue(config.isThreadNameMatch(Thread.currentThread().getName()));
	}

	@Test
	public void currentThread_nothing()
	{
		config.begin().threadPattern("(?!)").commit();
		assertFalse(config.isThreadNameMatch(Thread.currentThread().getName()));
	}

	@Test
	public void variableProperties()
	{
		String url = getClass().getResource("/testEnv.properties").toExternalForm();
		new LoadParams(config, url).commit();
		assertEquals(config.getThreadPattern(), System.getProperty("os.name"));
	}

	@Test
	public void setAllParams()
	{
		config.begin()
				.log(Level.FINE)
				.throwExceptions(true)
				.threadPattern("")
				.latencyFile("high")
				.latencyNetwork("medium")
				.latencyCPU("low")
				.bootStrapDelay(10)
				.commit();
		assertEquals(Level.FINE, config.getLogLevel());
		assertEquals(true, config.isThrow());
		assertEquals("", config.getThreadPattern());
		assertEquals(Latency.HIGH, config.getFileLatency());
		assertEquals(Latency.MEDIUM, config.getNetworkLatency());
		assertEquals(LOW, config.getCPULatency());
		assertEquals(10, config.getBootstrapDelay());
		config.begin()
				.log(Level.WARNING)
				.throwExceptions(false)
				.threadPattern("abc")
				.latencyFile("")
				.latencyNetwork("")
				.latencyCPU("")
				.bootStrapDelay(0)
				.commit();
		assertEquals(Level.WARNING, config.getLogLevel());
		assertEquals(false, config.isThrow());
		assertEquals("abc", config.getThreadPattern());
		assertNull(config.getFileLatency());
		assertNull(config.getNetworkLatency());
		assertNull(config.getCPULatency());
		assertEquals(0, config.getBootstrapDelay());
	}

	@Test(expected = IllegalArgumentException.class)
	public void lockTransaction()
	{
		config.begin()
				.seal()
				.log(Level.WARNING);
	}

	@Test
	@SuppressAuditReactive // For accept join
	public void logIfNewThread() throws InterruptedException
	{
		config.begin()
				.threadPattern(".*")
				.latencyFile("LOW")
				.log(Level.INFO)
				.throwExceptions(false)
				.commit();
		addHandler();

		Thread t;

		log[0] = 0;
		Runnable rctx1 = new Runnable()
		{
			@Override
			public void run()
			{
				latencyCall1();
			}
		};
		// First turn, invoke log.
		t = new Thread(rctx1);
		t.setDaemon(true);
		t.start();
		t.join();
		assertEquals(1, log[0]);

		// Second turn, invoke log.
		log[0] = 0;
		Runnable rctx2 = new Runnable()
		{
			@Override
			public void run()
			{
				latencyCall2();
			}
		};
		t = new Thread(rctx2);
		t.setDaemon(true);
		t.start();
		t.join();
		assertEquals(1, log[0]);

		// Third turn, same context, no invoke log.
		log[0] = 0;
		t = new Thread(rctx1);
		t.setDaemon(true);
		t.start();
		t.join();
		assertEquals(0, log[0]);

		log[0] = 0;
		Runnable rctx3 = new Runnable()
		{
			@Override
			public void run()
			{
				latencyCall3();
			}
		};
		t = new Thread(rctx3);
		t.setDaemon(true);
		t.start();
		t.join();
		assertEquals(1, log[0]);

		log[0] = 0;
		Runnable rctx4 = new Runnable()
		{
			@Override
			public void run()
			{
				latencyCall4();
			}
		};
		t = new Thread(rctx4);
		t.setDaemon(true);
		t.start();
		t.join();
		assertEquals(2, log[0]);

		log[0] = 0;
		t = new Thread(rctx3);
		t.setDaemon(true);
		t.start();
		t.join();
		assertEquals(0, log[0]);

		log[0] = 0;
		t = new Thread(rctx4);
		t.setDaemon(true);
		t.start();
		t.join();
		assertEquals(0, log[0]);

		removeHandler();
	}

	@Test
	public void logIfNewLoop()
	{
		config.reset();
		config.begin()
				.threadPattern(".*")
				.latencyFile("LOW")
				.log(Level.INFO)
				.throwExceptions(false)
				.commit();
		addHandler();

		for (int i = 0; i < 5; ++i)
		{
			log[0] = 0;
			latencyCall1();
			if (i == 0) assertEquals(1, log[0]);
			else assertEquals(0, log[0]);

		}
		removeHandler();
	}

	@Test
	public void logIfLevel()
	{
		config.reset();
		config.begin()
				.latencyFile("")
				.commit();
		addHandler();
		log[0] = 0;
		config.logIfNew(LOW, new FileAuditReactiveException(LOW, ""));
		assertEquals(0, log[0]);
		log[0] = 0;
		config.logIfNew(MEDIUM, new FileAuditReactiveException(MEDIUM, ""));
		assertEquals(0, log[0]);
		log[0] = 0;
		config.logIfNew(HIGH, new FileAuditReactiveException(HIGH, ""));
		assertEquals(0, log[0]);
		removeHandler();


		config.begin()
				.latencyFile("LOW")
				.commit();
		addHandler();
		log[0] = 0;
		config.logIfNew(LOW, new FileAuditReactiveException(LOW, ""));
		assertEquals(1, log[0]);
		log[0] = 0;
		config.logIfNew(MEDIUM, new FileAuditReactiveException(MEDIUM, ""));
		assertEquals(1, log[0]);
		log[0] = 0;
		config.logIfNew(HIGH, new FileAuditReactiveException(HIGH, ""));
		assertEquals(1, log[0]);
		removeHandler();

		config.begin()
				.latencyFile("MEDIUM")
				.commit();
		addHandler();
		log[0] = 0;
		config.logIfNew(LOW, new FileAuditReactiveException(LOW, ""));
		assertEquals(0, log[0]);
		log[0] = 0;
		config.logIfNew(MEDIUM, new FileAuditReactiveException(MEDIUM, ""));
		assertEquals(1, log[0]);
		log[0] = 0;
		config.logIfNew(HIGH, new FileAuditReactiveException(HIGH, ""));
		assertEquals(1, log[0]);
		removeHandler();

		config.begin()
				.latencyFile("HIGH")
				.commit();
		addHandler();
		log[0] = 0;
		config.logIfNew(LOW, new FileAuditReactiveException(LOW, ""));
		assertEquals(0, log[0]);
		log[0] = 0;
		config.logIfNew(MEDIUM, new FileAuditReactiveException(MEDIUM, ""));
		assertEquals(0, log[0]);
		log[0] = 0;
		config.logIfNew(HIGH, new FileAuditReactiveException(HIGH, ""));
		assertEquals(1, log[0]);
		removeHandler();
	}

	private void removeHandler()
	{
		config.logger.removeHandler(handler);
	}

	private void addHandler()
	{
		//noinspection ConstantIfStatement
		if (true) // Remove log on console
		{
			for (Handler h : config.logger.getHandlers())
			{
				config.logger.removeHandler(h);
			}
		}
		config.logger.addHandler(handler);
	}

	private void latencyCall1()
	{
		new File("/").getFreeSpace();
	}

	private void latencyCall2()
	{
		new File("/").getFreeSpace();
	}

	private void latencyCall3()
	{
		latencyCall1();
	}

	private void latencyCall4()
	{
		latencyCall1();
		latencyCall2();
	}

	@SuppressWarnings("PointlessBooleanExpression")
	@Test
	public void testPurgeStackTrace() throws NoSuchFieldException, IllegalAccessException
	{
		// If debug mode, test nothing
		Field field = AuditReactiveException.class.getDeclaredField("debug");
		field.setAccessible(true);
		if (((Boolean) field.get(null)) == true) return;
		strict.commit();

		try
		{
			latencyCall1();
			fail();
		}
		catch (AuditReactiveException e)
		{
			StackTraceElement[] stack = e.getStackTrace();
			for (StackTraceElement traceElement : stack)
			{
				assertFalse((traceElement.getClassName().startsWith(auditPackageName)
						&& !traceElement.getClassName().endsWith("Test"))); // For inner unit test
			}
		}
	}
}
