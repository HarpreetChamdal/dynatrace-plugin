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

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.PasswordParameterValue;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import com.dynatrace.jenkins.dashboard.Messages;
import com.dynatrace.jenkins.dashboard.TABuildSetupStatusAction;
import com.dynatrace.jenkins.dashboard.TAGlobalConfiguration;
import com.dynatrace.jenkins.dashboard.utils.BuildVarKeys;
import com.dynatrace.jenkins.dashboard.utils.Utils;
import com.dynatrace.sdk.server.exceptions.ServerResponseException;
import com.dynatrace.sdk.server.sessions.Sessions;
import com.dynatrace.sdk.server.sessions.models.StartRecordingRequest;
import com.dynatrace.sdk.server.testautomation.TestAutomation;
import com.dynatrace.sdk.server.testautomation.models.FetchTestRunsRequest;
import com.sun.jersey.api.client.ClientHandlerException;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.net.ssl.SSLHandshakeException;

public final class DynatraceAppMonBuildEnvStep extends AbstractStepImpl {

	/**
	 * The 1st arg is system profile name, the 2nd is build number
	 */
	private static final String RECORD_SESSION_NAME = "%s_Jenkins_build_%s";
	public final String systemProfile;
	// Test run attributes - no versionBuild attribute because it's taken from
	// the build object
	public final String versionMajor;
	public final String versionMinor;
	public final String versionRevision;
	public final String versionMilestone;
	public final String marker;
	public final Boolean recordSession;

	@DataBoundConstructor
	public DynatraceAppMonBuildEnvStep(final String systemProfile, final String versionMajor, final String versionMinor,
			final String versionRevision, final String versionMilestone, final String marker,
			final Boolean recordSession) {
		this.systemProfile = systemProfile;
		this.versionMajor = versionMajor;
		this.versionMinor = versionMinor;
		this.versionRevision = versionRevision;
		this.versionMilestone = versionMilestone;
		this.marker = marker;
		this.recordSession = recordSession;
	}

	private void setupBuildVariables(Run<?, ?> build, String serverUrl) {
		final TAGlobalConfiguration globalConfig = GlobalConfiguration.all().get(TAGlobalConfiguration.class);

		List<ParameterValue> parameters = new ArrayList<>(10);
		parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_SYSTEM_PROFILE, systemProfile));
		if (StringUtils.isNotEmpty(versionMajor)) {
			parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_VERSION_MAJOR, versionMajor));
		}
		if (StringUtils.isNotEmpty(versionMinor)) {
			parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_VERSION_MINOR, versionMinor));
		}
		if (StringUtils.isNotEmpty(versionRevision)) {
			parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_VERSION_REVISION, versionRevision));
		}
		parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_VERSION_BUILD,
				Integer.toString(build.getNumber())));
		if (StringUtils.isNotEmpty(versionMilestone)) {
			parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_VERSION_MILESTONE, versionMilestone));
		}
		if (StringUtils.isNotEmpty(marker)) {
			parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_MARKER, marker));
		}
		if (StringUtils.isNotEmpty(serverUrl)) {
			parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_GLOBAL_SERVER_URL, serverUrl));
		}
		if (StringUtils.isNotEmpty(globalConfig.username)) {
			parameters.add(new StringParameterValue(BuildVarKeys.BUILD_VAR_KEY_GLOBAL_USERNAME, globalConfig.username));
		}
		if (StringUtils.isNotEmpty(globalConfig.password)) {
			parameters
					.add(new PasswordParameterValue(BuildVarKeys.BUILD_VAR_KEY_GLOBAL_PASSWORD, globalConfig.password));
		}
		System.out.println("first call to utlis.updateBuildVariables from step 1::::");
		Utils.updateBuildVariables(build, parameters);
	}

	@Extension
	public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(Execution.class);
		}

		@Override
		public String getFunctionName() {
			return "dynatraceAppMonBuildEnvStep";
		}

		private static final boolean DEFAULT_RECORD_SESSION = false;

		public static boolean getDefaultRecordSession() {
			return DEFAULT_RECORD_SESSION;
		}

		@Nonnull
		@Override
		public String getDisplayName() {
			return "Use Dynatrace AppMon to monitor tests";
		}

		public FormValidation doCheckSystemProfile(@QueryParameter final String systemProfile) {
			if (StringUtils.isNotBlank(systemProfile)) {
				return FormValidation.ok();
			} else {
				return FormValidation.error(Messages.RECORDER_VALIDATION_BLANK_SYSTEM_PROFILE());
			}
		}

		public FormValidation doTestDynatraceConnection(@QueryParameter final String systemProfile) {
			try {
				final TestAutomation connection = new TestAutomation(Utils.createClient());
				FetchTestRunsRequest request = new FetchTestRunsRequest(systemProfile);
				// We set many constraints to ENSURE no or few testruns are
				// returned as this is testing the connection only
				request.setVersionBuildFilter("1024");
				request.setVersionMajorFilter("1024");
				request.setMaxBuilds(1);
				try {
					connection.fetchTestRuns(request);
				} catch (ServerResponseException e) {
					switch (e.getStatusCode()) {
					case HTTP_UNAUTHORIZED:
						return FormValidation.warning(Messages.RECORDER_VALIDATION_CONNECTION_UNAUTHORIZED());
					case HTTP_FORBIDDEN:
						return FormValidation.warning(Messages.RECORDER_VALIDATION_CONNECTION_FORBIDDEN());
					case HTTP_NOT_FOUND:
						return FormValidation.warning(Messages.RECORDER_VALIDATION_CONNECTION_NOT_FOUND());
					default:
						return FormValidation
								.warning(Messages.RECORDER_VALIDATION_CONNECTION_OTHER_CODE(e.getStatusCode()));
					}
				}
				return FormValidation.ok(Messages.RECORDER_VALIDATION_CONNECTION_OK());
			} catch (Exception e) {
				e.printStackTrace();
				if (e.getCause() instanceof ClientHandlerException
						&& e.getCause().getCause() instanceof SSLHandshakeException) {
					return FormValidation.warning(Messages.RECORDER_VALIDATION_CONNECTION_CERT_EXCEPTION(e.toString()));
				}
				return FormValidation.warning(Messages.RECORDER_VALIDATION_CONNECTION_UNKNOWN(e.toString()));
			}
		}
	}

	public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Boolean> {

		@Inject
		private transient DynatraceAppMonBuildEnvStep step;

		// public final Boolean recordSession = step.recordSession;
		// public final Boolean recordSession = false;

		@StepContextParameter
		private transient TaskListener listener;

		@StepContextParameter
		private transient Run<?, ?> build;

		@Override
		protected Boolean run() throws Exception {
			Boolean result = true;

			final TAGlobalConfiguration globalConfig = GlobalConfiguration.all().get(TAGlobalConfiguration.class);
			final Sessions sessions = new Sessions(Utils.createClient());
			final PrintStream logger = listener.getLogger();
			// logger.println("host is:"+globalConfig.host);
			try {
				String serverUrl = new URI(globalConfig.protocol, null, globalConfig.host, globalConfig.port, null,
						null, null).toString();

				if (step.recordSession) {
					logger.println("Starting session recording via Dynatrace Server REST interface...");

					StartRecordingRequest request = new StartRecordingRequest(step.systemProfile);
					request.setPresentableName(
							String.format(RECORD_SESSION_NAME, step.systemProfile, build.getNumber()));

					final String sessionNameOut = sessions.startRecording(request);
					logger.println("Dynatrace session " + sessionNameOut + " has been started");
				}

				step.setupBuildVariables(build, serverUrl);
			} catch (Exception e) {
				e.printStackTrace();
				build.addAction(new TABuildSetupStatusAction(true));
				logger.println(
						"ERROR: Dynatrace AppMon Plugin - build set up failed (see the stacktrace to get more information):\n"
								+ e.toString());
			}

			// final PrintStream logger = listener.getLogger();
			/*
			 * logger.println("Dynatrace AppMon Plugin - build tear down...");
			 * try { if (recordSession) { final String storedSessionName =
			 * storeSession(logger); Utils.updateBuildVariable(build,
			 * BuildVarKeys.BUILD_VAR_KEY_STORED_SESSION_NAME,
			 * storedSessionName); } } catch (Exception e) {
			 * e.printStackTrace(); logger.
			 * println("ERROR: Dynatrace AppMon Plugin - build tear down failed (see the stacktrace to get more information):\n"
			 * + e.toString()); }
			 */
			return result;
		}

		private static final long serialVersionUID = 1L;

		/**
		 * @return stored session name
		 */
		/*
		 * private String storeSession(final PrintStream logger) throws
		 * ServerResponseException, ServerConnectionException { logger.
		 * println("Storing session via Dynatrace Server REST interface...");
		 * String sessionName = sessions.stopRecording(step.systemProfile);
		 * logger.println("Dynatrace session " + sessionName +
		 * " has been stored"); return sessionName; }
		 */
	}
}