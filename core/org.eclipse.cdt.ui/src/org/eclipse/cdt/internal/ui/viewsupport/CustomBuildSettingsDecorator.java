/*******************************************************************************
 * Copyright (c) 2013, 2013 Andrew Gvozdev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Gvozdev - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.viewsupport;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;

import org.eclipse.cdt.core.language.settings.providers.ILanguageSettingsProvider;
import org.eclipse.cdt.core.language.settings.providers.ILanguageSettingsProvidersKeeper;
import org.eclipse.cdt.core.language.settings.providers.LanguageSettingsManager;
import org.eclipse.cdt.core.language.settings.providers.ScannerDiscoveryLegacySupport;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICResourceDescription;

import org.eclipse.cdt.internal.ui.CPluginImages;

/**
 * Determines if a file or folder got customized build settings and if so decorates with the "wrench" overlay.
 */
public class CustomBuildSettingsDecorator implements ILightweightLabelDecorator {
	@Override
	public void decorate(Object element, IDecoration decoration) {
		if (element instanceof IFile || element instanceof IFolder) {
			IResource rc = (IResource) element;
			ICProjectDescription prjDescription = CoreModel.getDefault().getProjectDescription(rc.getProject(), false);
			if (prjDescription != null) {
				ICConfigurationDescription cfgDescription = prjDescription.getDefaultSettingConfiguration();
				if (cfgDescription != null) {
					if (isCustomizedResource(cfgDescription, rc))
						decoration.addOverlay(CPluginImages.DESC_OVR_SETTING);
				}
			}
		}
	}

	private static boolean isCustomizedResource(ICConfigurationDescription cfgDescription, IResource rc) {
		if (!ScannerDiscoveryLegacySupport.isLanguageSettingsProvidersFunctionalityEnabled(rc.getProject())) {
			ICResourceDescription rcDescription = cfgDescription.getResourceDescription(rc.getProjectRelativePath(), true);
			return rcDescription != null;
		}

		if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
			for (ILanguageSettingsProvider provider: ((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders()) {
				for (String languageId : LanguageSettingsManager.getLanguages(rc, cfgDescription)) {
					List<ICLanguageSettingEntry> list = provider.getSettingEntries(cfgDescription, rc, languageId);
					if (list != null) {
						List<ICLanguageSettingEntry> listDefault = provider.getSettingEntries(cfgDescription, rc.getParent(), languageId);
						// != is OK here due as the equal lists will have the same reference in WeakHashSet
						if (list != listDefault)
							return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
		// We don't track state changes
	}

	@Override
	public void dispose() {
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		// We don't track state changes
	}
}
