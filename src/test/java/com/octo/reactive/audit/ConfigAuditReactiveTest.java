package com.octo.reactive.audit;

import com.octo.reactive.audit.lib.AuditReactiveException;
import com.octo.reactive.audit.lib.SuppressAuditReactive;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.*;


/**
 * Created by pprados on 07/05/2014.
 */
public class ConfigAuditReactiveTest
{
	// Because the Aspectj must use the config singleton,
	// it's not possible to inject a specific config instance
	private ConfigAuditReactive config = ConfigAuditReactive.config;

	private int[]   log     = new int[1];
	private Handler handler = new Handler()
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
	public void testCurrentThread_test()
	{
		assertTrue(config.isThreadNameMatch(Thread.currentThread().getName()));
	}

	@Test
	public void testCurrentThread_nothing()
	{
		config.begin().threadPattern("(?!)").commit();
		assertFalse(config.isThreadNameMatch(Thread.currentThread().getName()));
	}

	@Test
	public void setAllParams()
	{
		config.begin()
				.log(Level.FINE)
				.throwExceptions(true)
				.threadPattern("")
				.bootStrapDelay(10)
				.commit();
		assertEquals(Level.FINE, config.getLogLevel());
		assertEquals(true, config.isThrow());
		assertEquals("", config.getThreadPattern());
		assertEquals(10, config.getBootstrapDelay());
		config.begin()
				.log(Level.WARNING)
				.throwExceptions(false)
				.threadPattern("abc")
				.bootStrapDelay(0)
				.commit();
		assertEquals(Level.WARNING, config.getLogLevel());
		assertEquals(false, config.isThrow());
		assertEquals("abc", config.getThreadPattern());
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
				.log(Level.INFO)
				.throwExceptions(false)
				.commit();
		config.logger.addHandler(handler);

		Thread t;

		log[0] = 0;
		// FIXME: ecriture java8 rejeté par aspectj 8.0
		Runnable rctx1 = new Runnable()
		{
			@Override
			public void run()
			{
				latencyCall1();
			}
		};
		t = new Thread(rctx1);
		t.start();
		t.join();
		assertEquals(1, log[0]);

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
		t.start();
		t.join();
		assertEquals(1, log[0]);

		log[0] = 0;
		t = new Thread(rctx1);
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
		t.start();
		t.join();
		assertEquals(2, log[0]);

		log[0] = 0;
		t = new Thread(rctx3);
		t.start();
		t.join();
		assertEquals(0, log[0]);

		log[0] = 0;
		t = new Thread(rctx4);
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

	private void removeHandler()
	{
		config.logger.removeHandler(handler);
	}

	private void addHandler()
	{
		if (true) // FIXME : remove log on console
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

	@Test
	public void testPurgeStackTrace() throws NoSuchFieldException, IllegalAccessException
	{
		// If debug mode, test nothing
		Field field = AuditReactiveException.class.getDeclaredField("debug");
		field.setAccessible(true);
		if (((Boolean) field.get(null)) == true) return;
		ConfigAuditReactive.strict.commit();

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
				assertFalse((traceElement.getClassName().startsWith(ConfigAuditReactive.auditPackageName)
						&& !traceElement.getClassName().endsWith("Test"))); // For inner unit test
			}
		}
	}
}
