/*
 * *****************************************************************************
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.source.tuner.hydrasdr;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HydraSdrNativeBufferFactoryTest
{
	@Test
	void preservesTimestampAcrossResidualBuffers()
	{
		HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(128_000);
		float[] samples = new float[100];

		assertEquals(0, factory.get(samples, samples, samples.length, 1_000).size());

		List<HydraSdrNativeBuffer> first = factory.get(samples, samples, samples.length, 2_000);
		assertEquals(1, first.size());
		assertEquals(1_000, first.get(0).getTimestamp());

		List<HydraSdrNativeBuffer> second = factory.get(samples, samples, samples.length, 3_000);
		assertEquals(1, second.size());
		assertEquals(1_001, second.get(0).getTimestamp());
	}
}
