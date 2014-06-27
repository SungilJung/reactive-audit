package com.octo.reactive.audit.java.io;

import com.octo.reactive.audit.ConfigAuditReactive;
import com.octo.reactive.audit.IOTestTools;
import com.octo.reactive.audit.lib.FileAuditReactiveException;
import org.junit.Test;

import java.io.Flushable;
import java.io.IOException;

/**
 * Created by pprados on 06/05/14.
 */
public class FlushableTest
{
	@Test
	public void flush_1() throws IOException
	{
		ConfigAuditReactive.strict.commit();
		new Flushable()
		{
			@Override
			public void flush()
			{

			}
		}.flush();
	}

	@Test(expected = FileAuditReactiveException.class)
	public void flush_2() throws IOException
	{
		ConfigAuditReactive.strict.commit();
		IOTestTools.getTempFileOutputStream().flush();
	}
}
