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
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.dynatrace.jenkins.dashboard.Messages;
import com.dynatrace.jenkins.dashboard.TATestRunIdsAction;
import com.dynatrace.jenkins.dashboard.utils.BuildVarKeys;
import com.dynatrace.jenkins.dashboard.utils.Utils;
import com.dynatrace.sdk.server.testautomation.TestAutomation;
import com.dynatrace.sdk.server.testautomation.models.CreateTestRunRequest;
import com.dynatrace.sdk.server.testautomation.models.TestCategory;
import com.dynatrace.sdk.server.testautomation.models.TestMetaData;
import com.dynatrace.sdk.server.testautomation.models.TestRun;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * Archiving for gatling reports.
 */
public class DynatraceAppMonTestRunRegistrationBuildStep extends AbstractStepImpl {
    
	private final String category;
	private final String platform;

	@DataBoundConstructor
	public DynatraceAppMonTestRunRegistrationBuildStep(String category, String platform) {
		this.category = category;
		this.platform = platform;
	}
	
	public String getCategory() {
		return category;
	}

	public String getPlatform() {
		return platform;
	}
	
	//perform fn originally
	
	
	private void updateTestRunIdsAction(Run build, String newTestRunId) {
		TATestRunIdsAction testRunIdsAction = build.getAction(TATestRunIdsAction.class);
		if (testRunIdsAction == null) {
			testRunIdsAction = new TATestRunIdsAction();
			build.addAction(testRunIdsAction);
		}
		testRunIdsAction.getTestRunIds().add(newTestRunId);
	}

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        
    	private static final String DEFAULT_CATEGORY = TestCategory.UNIT.getInternal();
    	
    	public DescriptorImpl() {
            super(Execution.class);
        }
    	
    	public static String getDefaultCategory() {
			return DEFAULT_CATEGORY;
		}

        @Override
        public String getFunctionName() {
            return "dynatraceAppMonRegisterTestRun";
        }

        @Override
        public String getDisplayName() {
        	return Messages.BUILD_STEP_DISPLAY_NAME();
        }
        
        public ListBoxModel doFillCategoryItems() {
			ListBoxModel model = new ListBoxModel();
			for (TestCategory category : TestCategory.values()) {
				model.add(category.getInternal());
			}
			return model;
		}
    }
    
    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Boolean> {
        
    	@Inject
        private transient DynatraceAppMonTestRunRegistrationBuildStep step;
        
        @StepContextParameter
        private transient Launcher launcher;
        
        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Run<?, ?> build;
        

        @Override
        protected Boolean run() throws Exception {
        	final PrintStream logger = listener.getLogger();
        	
			if (!Utils.isValidBuild(build, logger, "test run won't be registered")) {
    			return true;
    		}

    		logger.println("Registering test run via Dynatrace Server REST interface...");
    		try {
    			//Map<String, String> variables = build.getBuildVariables();
    			EnvVars variables = build.getEnvironment(listener);
    			String systemProfile = variables.get(BuildVarKeys.BUILD_VAR_KEY_SYSTEM_PROFILE);
    			//logger.println("systemprofilevalue:"+systemProfile);
    			String versionMajor = variables.get(BuildVarKeys.BUILD_VAR_KEY_VERSION_MAJOR);
    			String versionMinor = variables.get(BuildVarKeys.BUILD_VAR_KEY_VERSION_MINOR);
    			String versionRevision = variables.get(BuildVarKeys.BUILD_VAR_KEY_VERSION_REVISION);
    			String versionBuild = Integer.toString(build.getNumber());
    			String versionMilestone = variables.get(BuildVarKeys.BUILD_VAR_KEY_VERSION_MILESTONE);
    			String marker = variables.get(BuildVarKeys.BUILD_VAR_KEY_MARKER);

    			logger.println("1-systemprofilevalue:"+systemProfile);
    			
    			Map<String, String> additionalInformation = new HashMap<>();
    			additionalInformation.put("JENKINS_JOB", build.getUrl());

    			final TestAutomation restEndpoint = new TestAutomation(Utils.createClient());
    			CreateTestRunRequest request = new CreateTestRunRequest(systemProfile, versionBuild);
    			request.setVersionMajor(versionMajor);
    			request.setVersionMinor(versionMinor);
    			request.setVersionRevision(versionRevision);
    			request.setVersionBuild(versionBuild);
    			request.setVersionMilestone(versionMilestone);
    			request.setCategory(TestCategory.fromInternal(step.category));
    			request.setPlatform(step.platform);
    			request.setMarker(marker);
    			request.setAdditionalMetaData(new TestMetaData(additionalInformation));
    			TestRun testRun = restEndpoint.createTestRun(request);

    			System.out.println("FIRST call to utlis.updateBuildVariable from step 2::::");
    			Utils.updateBuildVariable(build, BuildVarKeys.BUILD_VAR_KEY_TEST_RUN_ID, testRun.getId());
    			step.updateTestRunIdsAction(build, testRun.getId());
    			logger.println("2.BUILD_VAR_KEY_TEST_RUN_ID:"+variables.get(BuildVarKeys.BUILD_VAR_KEY_TEST_RUN_ID));
    			logger.println("Registered test run with ID=" + testRun.getId());    	
    			//logger.println("category:" + step.category);
    			//logger.println("platform:" + step.platform);
    			return true;
    		} catch (Exception e) {
    			e.printStackTrace();
    			logger.println("ERROR: Dynatrace AppMon Plugin - build step execution failed (see the stacktrace to get more information):\n" + e.toString());
    			return false;
    		}
            
            
        }

        private static final long serialVersionUID = 1L;
    }
    
     
}