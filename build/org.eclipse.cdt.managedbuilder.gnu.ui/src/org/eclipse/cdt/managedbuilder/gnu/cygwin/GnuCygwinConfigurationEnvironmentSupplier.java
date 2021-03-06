/*******************************************************************************
 * Copyright (c) 2005, 2012 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Intel Corporation - Initial API and implementation
 *     Andrew Gvozdev    - Ability to use different Cygwin versions in different cfg
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.gnu.cygwin;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.envvar.IEnvironmentVariable;
import org.eclipse.cdt.internal.core.Cygwin;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.envvar.IBuildEnvironmentVariable;
import org.eclipse.cdt.managedbuilder.envvar.IConfigurationEnvironmentVariableSupplier;
import org.eclipse.cdt.managedbuilder.envvar.IEnvironmentVariableProvider;
import org.eclipse.cdt.managedbuilder.internal.envvar.BuildEnvVar;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * @noextend This class is not intended to be subclassed by clients.
 */
public class GnuCygwinConfigurationEnvironmentSupplier implements IConfigurationEnvironmentVariableSupplier {
	private static final String ENV_PATH = "PATH"; //$NON-NLS-1$
	private static final String ENV_LANG = "LANG"; //$NON-NLS-1$
	private static final String ENV_LC_ALL = "LC_ALL"; //$NON-NLS-1$
	private static final String ENV_LC_MESSAGES = "LC_MESSAGES"; //$NON-NLS-1$

	private static final String PROPERTY_OSNAME = "os.name"; //$NON-NLS-1$
	private static final String BACKSLASH = java.io.File.separator;

	@Override
	public IBuildEnvironmentVariable getVariable(String variableName, IConfiguration configuration, IEnvironmentVariableProvider provider) {
		if (variableName == null) {
			return null;
		}

		if (!System.getProperty(PROPERTY_OSNAME).toLowerCase().startsWith("windows ")) { //$NON-NLS-1$
			return null;
		}

		if (variableName.equalsIgnoreCase(ENV_PATH)) {
			@SuppressWarnings("nls")
			String path = "${" + Cygwin.ENV_CYGWIN_HOME + "}" + BACKSLASH + "bin";
			return new BuildEnvVar(ENV_PATH, path, IBuildEnvironmentVariable.ENVVAR_PREPEND);

		} else if (variableName.equals(Cygwin.ENV_CYGWIN_HOME)) {
			String home = Cygwin.getCygwinHome();
			// If the variable is not defined still show it in the environment variables list as a hint to user
			if (home == null) {
				home = ""; //$NON-NLS-1$
			}
			IPath homePath = new Path(home);
			IEnvironmentVariable varCygwinHome = CCorePlugin.getDefault().getBuildEnvironmentManager().getVariable(Cygwin.ENV_CYGWIN_HOME, null, false);
			if (varCygwinHome == null || (!homePath.equals(new Path(varCygwinHome.getValue())))) {
				// Contribute if the variable does not already come from workspace environment
				return new BuildEnvVar(Cygwin.ENV_CYGWIN_HOME, homePath.toOSString());
			}
			return null;

		} else if (variableName.equalsIgnoreCase(ENV_LANG)) {
			// Workaround for not being able to select encoding for CDT console -> change codeset to Latin1
			String langValue = System.getenv(ENV_LANG);
			if (langValue == null || langValue.length() == 0) {
				langValue = System.getenv(ENV_LC_ALL);
			}
			if (langValue == null || langValue.length() == 0) {
				langValue = System.getenv(ENV_LC_MESSAGES);
			}
			if (langValue != null && langValue.length() > 0) {
				// langValue is [language[_territory][.codeset][@modifier]], i.e. "en_US.UTF-8@dict"
				// we replace codeset with Latin1 as CDT console garbles UTF
				// and ignore modifier which is not used by LANG
				langValue = langValue.replaceFirst("([^.@]*)(\\..*)?(@.*)?", "$1.ISO-8859-1"); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				langValue = "C.ISO-8859-1"; //$NON-NLS-1$
			}
			return new BuildEnvVar(ENV_LANG, langValue);
		}
		return null;
	}

	@Override
	public IBuildEnvironmentVariable[] getVariables(IConfiguration configuration, IEnvironmentVariableProvider provider) {
		IBuildEnvironmentVariable varHome = getVariable(Cygwin.ENV_CYGWIN_HOME, configuration, provider);
		IBuildEnvironmentVariable varLang = getVariable(ENV_LANG, configuration, provider);
		IBuildEnvironmentVariable varPath = getVariable(ENV_PATH, configuration, provider);

		return new IBuildEnvironmentVariable[] {varHome, varLang, varPath};
	}
}
