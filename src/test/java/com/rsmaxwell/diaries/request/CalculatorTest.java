
package com.rsmaxwell.diaries.request;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.rsmaxwell.diaries.requestor.CalculatorRequest;

class CalculatorTest {
	@Test
	void appHasAGreeting() {
		CalculatorRequest classUnderTest = new CalculatorRequest();
		assertNotNull(classUnderTest);
	}
}
