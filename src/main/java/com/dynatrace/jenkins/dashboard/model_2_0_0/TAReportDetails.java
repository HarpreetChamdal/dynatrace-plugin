/***************************************************
 * Dynatrace Jenkins Plugin

 Copyright (c) 2008-2016, DYNATRACE LLC
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification,
 are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice,
 this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.
 * Neither the name of the dynaTrace software nor the names of its contributors
 may be used to endorse or promote products derived from this software without
 specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 DAMAGE.
 */
package com.dynatrace.jenkins.dashboard.model_2_0_0;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by krzysztof.necel on 2016-05-04.
 */
@XmlRootElement(name = "TAReportDetails")
public class TAReportDetails {

	@XmlElementWrapper(name="testRuns")
	@XmlElement(name="testRun")
	private final List<TestRun> testRuns;

	public TAReportDetails(List<TestRun> testRuns) {
		this.testRuns = testRuns;
	}

	// Required by JAXB
	private TAReportDetails() {
		this.testRuns = new ArrayList<>();
	}

	public List<TestRun> getTestRuns() {
		return testRuns;
	}

	public boolean isEmpty() {
		for (TestRun testRun : testRuns) {
			if (!testRun.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public TestResult getCorrespondingTestResult(TestResult testResult, TestCategory category) {
		for (TestRun testRun : testRuns) {
			if (category == testRun.getCategory()) {
				TestResult result = testRun.getCorrespondingTestResult(testResult);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}
}
