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
package com.dynatrace.jenkins.steps;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.collections.CollectionUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import com.dynatrace.jenkins.dashboard.TAReportingBuildAction_2_0_0;
import com.dynatrace.jenkins.dashboard.TATestRunIdsAction;
import com.dynatrace.jenkins.dashboard.model_2_0_0.TAReport;
import com.dynatrace.jenkins.dashboard.model_2_0_0.TAReportDetails;
import com.dynatrace.jenkins.dashboard.model_2_0_0.TestStatus;
import com.dynatrace.jenkins.dashboard.rest.TAReportRetriever;
import com.dynatrace.jenkins.dashboard.rest.TAReportRetrieverByBuildNumber;
import com.dynatrace.jenkins.dashboard.rest.TAReportRetrieverByTestRunId;
import com.dynatrace.jenkins.dashboard.utils.BuildVarKeys;
import com.dynatrace.jenkins.dashboard.utils.TAReportDetailsFileUtils;
import com.dynatrace.jenkins.dashboard.utils.Utils;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * Archiving for gatling reports.
 */
public final class DynatraceAppMonReportingRecorder extends AbstractStepImpl {
    
	public final Boolean modifyStatusIfDegraded;
	public final Boolean modifyStatusIfVolatile;
	public final String statusNameIfDegraded;
	public final String statusNameIfVolatile;
	public final Boolean printXmlReportForDebug;

	@DataBoundConstructor
	public DynatraceAppMonReportingRecorder(final Boolean modifyStatusIfDegraded, final Boolean modifyStatusIfVolatile,
							   final String statusNameIfDegraded, final String statusNameIfVolatile,
							   final Boolean printXmlReportForDebug) {
		this.modifyStatusIfDegraded = modifyStatusIfDegraded;
		this.modifyStatusIfVolatile = modifyStatusIfVolatile;
		this.statusNameIfDegraded = statusNameIfDegraded;
		this.statusNameIfVolatile = statusNameIfVolatile;
		this.printXmlReportForDebug = printXmlReportForDebug;
	}
	
	private TAReportRetriever createReportRetriever(Run<?,?> build, PrintStream logger, String systemProfile) {
		final TATestRunIdsAction testRunIdsAction = build.getAction(TATestRunIdsAction.class);
		final List<String> testRunIds = testRunIdsAction == null ? null : testRunIdsAction.getTestRunIds();

		if (CollectionUtils.isEmpty(testRunIds)) {
			return new TAReportRetrieverByBuildNumber(systemProfile, logger, printXmlReportForDebug, build.getNumber());
		}
		return new TAReportRetrieverByTestRunId(systemProfile, logger, printXmlReportForDebug, testRunIds);
	}
	
	/**
	 * Mark the build as unstable or failure basing on Dynatrace Report
	 */
	
	private void modifyBuildStatus(Run<?, ?> build, TAReport report) {
		if (modifyStatusIfVolatile) {
			modifyBuildStatusIfVolatile(build, report);
		}
		if (modifyStatusIfDegraded) {
			modifyBuildStatusIfDegraded(build, report);
		}
	}
	
	/**
	 * Mark the build as unstable or failure if there was any volatile test (basing on Dynatrace Report)
	 */
	
	private void modifyBuildStatusIfVolatile(Run<?, ?> build, TAReport report) {
		if (report.getVolatileCount() > 0) {
			if (DescriptorImpl.MODIFY_BUILD_STATUS_UNSTABLE.equals(statusNameIfVolatile)) {
				build.setResult(Result.UNSTABLE);
			} else if (DescriptorImpl.MODIFY_BUILD_STATUS_FAILURE.equals(statusNameIfVolatile)) {
				build.setResult(Result.FAILURE);
			}
		}
	}
	
	/**
	 * Mark the build as unstable or failure if there was any degraded test (basing on Dynatrace Report)
	 */
	
	private void modifyBuildStatusIfDegraded(Run<?, ?> build, TAReport report) {
		if (report.getDegradedCount() > 0) {
			if (DescriptorImpl.MODIFY_BUILD_STATUS_UNSTABLE.equals(statusNameIfDegraded)) {
				build.setResult(Result.UNSTABLE);
			} else if (DescriptorImpl.MODIFY_BUILD_STATUS_FAILURE.equals(statusNameIfDegraded)) {
				build.setResult(Result.FAILURE);
			}
		}
	}
	

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        
    	private static final boolean DEFAULT_PRINT_XML_REPORT_FOR_DEBUG = false;

		private static final String MODIFY_BUILD_STATUS_UNSTABLE = "UNSTABLE";
		private static final String MODIFY_BUILD_STATUS_FAILURE = "FAILURE";
    	
    	public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "dynatraceAppMonTestResults";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Publish Test Results";
        }       

		public static boolean getDefaultPrintXmlReportForDebug() {
			return DEFAULT_PRINT_XML_REPORT_FOR_DEBUG;
		}

		public static String getModifyBuildStatusUnstable() {
			return MODIFY_BUILD_STATUS_UNSTABLE;
		}

		public static String getModifyBuildStatusFailure() {
			return MODIFY_BUILD_STATUS_FAILURE;
		}
        
    }
    
    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Boolean> {
        @Inject
        private transient DynatraceAppMonReportingRecorder step;
        
        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Run<?, ?> build;

        @StepContextParameter
        private transient Launcher launcher;

        @Override
        protected Boolean run() throws Exception {
            
        	final PrintStream logger = listener.getLogger();

    		if (!Utils.isValidBuild(build, logger, "results won't be fetched from Dynatrace Server")) {
    			return true;
    		}

    		//final Map<String, String> envVars = build.getBuildVariables();
    		EnvVars envVars = build.getEnvironment(listener);
    		final String systemProfile = envVars.get(BuildVarKeys.BUILD_VAR_KEY_SYSTEM_PROFILE);
    		final String storedSessionName = envVars.get(BuildVarKeys.BUILD_VAR_KEY_STORED_SESSION_NAME);

    		try {
    			final TAReportRetriever reportRetriever = step.createReportRetriever(build, logger, systemProfile);

    			final TAReportDetails reportDetails = reportRetriever.fetchReport();
    			final Map<TestStatus, Integer> summary = Utils.createReportAggregatedSummary(reportDetails);
    			final TAReport report = new TAReport(summary, build);

    			logger.println("Report summary: " + summary + ".");
    			TAReportDetailsFileUtils.persistReportDetails(build, reportDetails);
    			TAReportingBuildAction_2_0_0 buildAction = new TAReportingBuildAction_2_0_0(build, storedSessionName, report);
    			build.addAction(buildAction);

    			step.modifyBuildStatus(build, report);

    		} catch (InterruptedException e) {
    			build.setResult(Result.ABORTED);
    			logger.println("Build has been aborted - results won't be fetched from Dynatrace Server");
    			Thread.currentThread().interrupt();
    		} catch (Exception e) {
    			e.printStackTrace();
    			logger.println("Fetching data from Dynatrace Server REST interface failed\n" + e.toString());
    		}

    		return true;
        }

        private static final long serialVersionUID = 1L;
    }
    
}