package com.octo.reactive.audit.java.io;

import com.octo.reactive.audit.FileAudit;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

import static com.octo.reactive.audit.lib.Latency.HIGH;
import static com.octo.reactive.audit.lib.Latency.LOW;

/**
 * Created by pprados on 18/06/2014.
 */
@Aspect
public class RandomAccessFileAspect extends FileAudit
{
	@Before("call(java.io.RandomAccessFile.new(..))")
	public void new_(JoinPoint thisJoinPoint)
	{
		latency(LOW, thisJoinPoint);
	}

	@Before("call(* java.io.RandomAccessFile.read*(..))")
	public void read(JoinPoint thisJoinPoint)
	{
		latency(HIGH, thisJoinPoint);
	}

	@Before("call(* java.io.RandomAccessFile.skipByte*(int))")
	public void skipByte(JoinPoint thisJoinPoint)
	{
		latency(LOW, thisJoinPoint);
	}

	@Before("call(* java.io.RandomAccessFile.write*(..))")
	public void write(JoinPoint thisJoinPoint)
	{
		latency(HIGH, thisJoinPoint);
	}

	@Before("call(* java.io.RandomAccessFile.seek(long))")
	public void seek(JoinPoint thisJoinPoint)
	{
		latency(HIGH, thisJoinPoint);
	}

//	@Before("call(* java.io.RandomAccessFile.close())")
//	public void close(JoinPoint thisJoinPoint)
//	{
//		latency(LOW, thisJoinPoint);
//	}

	// FIXME
	@Pointcut("call(* java.io.RandomAccessFile.format(..))")
	public void format()
	{
	}
}
