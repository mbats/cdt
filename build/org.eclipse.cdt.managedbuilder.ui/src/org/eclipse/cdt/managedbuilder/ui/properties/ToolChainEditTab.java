/*******************************************************************************
 * Copyright (c) 2007 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.ui.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.cdt.core.settings.model.ICResourceDescription;
import org.eclipse.cdt.managedbuilder.core.BuildException;
import org.eclipse.cdt.managedbuilder.core.IBuilder;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IFileInfo;
import org.eclipse.cdt.managedbuilder.core.IFolderInfo;
import org.eclipse.cdt.managedbuilder.core.IResourceInfo;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.internal.core.ToolChain;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;

public class ToolChainEditTab extends AbstractCBuildPropertyTab {
	
	private static final String NO_TC = Messages.getString("StdProjectTypeHandler.0");  //$NON-NLS-1$
	private static final IToolChain[] r_tcs = ManagedBuildManager.getRealToolChains();
	private static final IBuilder[]    r_bs = ManagedBuildManager.getRealBuilders();
	private static final ITool[]    r_tools = ManagedBuildManager.getRealTools();
	
	private Text text;
	private Button b_dispCompatible;
	private Combo  c_toolchain;
	private Combo  c_builder;
	private Combo  c_tool; 
	private Button button_edit;
	private Group tools_group;
	
	private IBuilder[] v_bs;
	private IToolChain[] v_tcs;
	private ITool[] v_tools;

	private IConfiguration cfg;
	private IResourceInfo ri;
	
	public void createControls(Composite parent) {
		super.createControls(parent);
		usercomp.setLayout(new GridLayout(2, false));
		b_dispCompatible = setupCheck(usercomp, Messages.getString("ToolChainEditTab.0"), 2, GridData.BEGINNING); //$NON-NLS-1$
		b_dispCompatible.setSelection(true);

		setupLabel(usercomp, Messages.getString("ToolChainEditTab.1"), 2, GridData.BEGINNING); //$NON-NLS-1$
		c_toolchain = new Combo(usercomp, SWT.READ_ONLY | SWT.DROP_DOWN | SWT.BORDER);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		c_toolchain.setLayoutData(gd);
		c_toolchain.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				modifyToolchain();
			}});
		c_toolchain.setEnabled(!page.isForFile());
		
		setupLabel(usercomp, Messages.getString("ToolChainEditTab.2"), 2, GridData.BEGINNING); //$NON-NLS-1$
		c_builder = new Combo(usercomp, SWT.READ_ONLY | SWT.DROP_DOWN | SWT.BORDER);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		c_builder.setLayoutData(gd);
		c_builder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				modifyBuilder();
			}});
		c_builder.setEnabled(page.isForProject());

		// make table for tools list
		if (page.isForFile()) {
			Group g = setupGroup(usercomp, Messages.getString("ToolChainEditTab.5"), 2, GridData.FILL_BOTH); //$NON-NLS-1$
			c_tool = new Combo(g, SWT.READ_ONLY | SWT.DROP_DOWN | SWT.BORDER);
			gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = 2;
			c_tool.setLayoutData(gd);
			c_tool.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					saveToolSelected();
				}});
		} else { // Folder or Project
			tools_group = setupGroup(usercomp, Messages.getString("ToolChainEditTab.3"), 2, GridData.FILL_BOTH); //$NON-NLS-1$

			text = new Text(tools_group, SWT.BORDER | SWT.WRAP | SWT.MULTI |
					SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
			gd = new GridData(GridData.FILL_BOTH);
			gd.grabExcessHorizontalSpace = true;
			text.setLayoutData(gd);

			button_edit = new Button(tools_group, SWT.PUSH);
			GridData gdb = new GridData(GridData.VERTICAL_ALIGN_CENTER);
			gdb.grabExcessHorizontalSpace = false;
			gdb.horizontalAlignment = SWT.FILL;
			gdb.widthHint = 80;
			button_edit.setLayoutData(gdb);
			button_edit.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent event) {
					modifyTools();
				}});

			button_edit.setLayoutData(new GridData(GridData.END));
			button_edit.setText(Messages.getString("ToolChainEditTab.4")); //$NON-NLS-1$
		}
	}
	public void updateData(ICResourceDescription rcfg) {
		cfg = getCfg(rcfg.getConfiguration());
		ri = cfg.getResourceInfo(rcfg.getPath(), false);
		updateData();
	}
	private void updateData() {	
		v_tcs   = new IToolChain[r_tcs.length];
		v_bs    = new IBuilder[r_bs.length];
		v_tools = new ITool[r_tools.length];
		
		IToolChain tc = null;
		if (ri instanceof IFolderInfo)
			tc = ManagedBuildManager.getRealToolChain(((IFolderInfo)ri).getToolChain());
		int cnt = 0;
		int pos = -1;
		
		c_toolchain.removeAll();
		boolean isMng = cfg.getBuilder().isManagedBuildOn();
		for (int i=0; i<r_tcs.length; i++) {
			if (r_tcs[i].isSystemObject() && 
			    !( 
					  ((ToolChain)r_tcs[i]).isPreferenceToolChain() &&
			           !isMng)
			     ) // NO TOOLCHAIN 
				continue;
			
			if (b_dispCompatible.getSelection() &&
				(ri instanceof IFolderInfo) && 
			  ! ((IFolderInfo)ri).isToolChainCompatible(r_tcs[i]))
					continue;
			c_toolchain.add(r_tcs[i].getUniqueRealName());
			v_tcs[cnt] = r_tcs[i];
			if (r_tcs[i].equals(tc)) pos = cnt;
			cnt++;
		}
		if (pos != -1) {
			c_toolchain.select(pos);
			c_builder.setEnabled(page.isForProject());
		}
		else {
			c_toolchain.setText(EMPTY_STR); // unprobable case
			c_builder.setEnabled(false);
		}
		
		IBuilder b = ManagedBuildManager.getRealBuilder(cfg.getBuilder());
		cnt = 0;
		pos = -1;
		c_builder.removeAll();
		for (int i=0; i<r_bs.length; i++) {
			if (r_bs[i].isSystemObject()) continue;
			if (b_dispCompatible.getSelection() && 
				! cfg.isBuilderCompatible(r_bs[i]))
					continue; // not compatible builder
			c_builder.add(r_bs[i].getUniqueRealName());
			v_bs[cnt] = r_bs[i];
			if (r_bs[i].equals(b)) pos = cnt;
			cnt++;
		}
		if (pos != -1)
			c_builder.select(pos);
		else
			c_builder.setText(EMPTY_STR);
		
		cnt = 0;
		for (int i=0; i<r_tools.length; i++) {
			if (r_tools[i].isSystemObject()) continue;
			if (r_tools[i].isAbstract()) continue;
			v_tools[cnt++] = r_tools[i];
		}
		ITool[] tmp = new ITool[cnt];
		System.arraycopy(v_tools, 0, tmp, 0, cnt);
		Arrays.sort(tmp, BuildListComparator.getInstance());
		v_tools = tmp;
		
		if (page.isForFile()) { // Edit tool in combo for File
			c_tool.removeAll();
			ITool[] tools = ri.getTools();
			ITool curr = null, real = null;
			if (tools != null && tools.length > 0) {
				for (int i=0; i<tools.length; i++) {
					if (tools[i] != null && !tools[i].getCustomBuildStep()) {
						curr = tools[i];
						real = ManagedBuildManager.getRealTool(curr); 
						break;
					}
				}
			}
			pos = (curr == null) ? v_tools.length : -1;
			
			for (int i=0; i<v_tools.length; i++) {
				if (pos == -1 && real.matches(v_tools[i])) {
					pos = i;
					c_tool.add(curr.getUniqueRealName()); // tool from v_tools may 
				} else {
					c_tool.add(v_tools[i].getUniqueRealName());
				}
			}
			c_tool.add(Messages.getString("ToolChainEditTab.6")); //$NON-NLS-1$
			c_tool.select(pos);
		} else if (((ToolChain)tc).isPreferenceToolChain()){ // display tools list for Folder and Project
			tools_group.setVisible(false);
		} else {
			tools_group.setVisible(true);
			String s = EMPTY_STR;
			ITool[] tools = ri.getTools();
			for (int i = 0; i < tools.length; i++)
				s = s + tools[i].getUniqueRealName() + "\n"; //$NON-NLS-1$
			text.setText(s);
		}
	}
	
    public void checkPressed(SelectionEvent e) {
    	updateData();
    }
	public void performApply(ICResourceDescription src,
			ICResourceDescription dst) {
		IConfiguration cfg1 = getCfg(src.getConfiguration());
		IConfiguration cfg2 = getCfg(dst.getConfiguration());
		IBuilder b = cfg1.getBuilder();
		cfg2.changeBuilder(b, ManagedBuildManager.calculateChildId(b.getId(), null), b.getUniqueRealName());
		IResourceInfo ri1 = cfg1.getResourceInfo(src.getPath(), false);
		IResourceInfo ri2 = cfg2.getResourceInfo(dst.getPath(), false);
		if (ri1 instanceof IFolderInfo && ri2 instanceof IFolderInfo) { 
			IFolderInfo fi1 = (IFolderInfo)ri1;
			IFolderInfo fi2 = (IFolderInfo)ri2;
			IToolChain tc = fi1.getToolChain();
			ITool[] tools1 = fi1.getTools();
			ITool[] tools2 = fi2.getTools();
			try {
				fi2.changeToolChain(tc, ManagedBuildManager.calculateChildId(tc.getId(), null), tc.getUniqueRealName());
				fi2.modifyToolChain(tools2, tools1);
			} catch (BuildException e) {}
		} else if (ri1 instanceof IFileInfo && ri2 instanceof IFileInfo) {
			((IFileInfo)ri2).setTools(ri1.getTools());
		}
	}
	
	protected void performDefaults() {
		if (page.isForProject()) {
//			1.Per-project : change to the "default" tool-chain defined in the extension
//			super-class of the project configuration. NOTE: the makefile project case might
//			need a special handling in this case.
			
			IConfiguration cfg1 = cfg.getParent();
			if (cfg1.getToolChain() == null) {
				if (cfg.getToolChain() != null) {
					IToolChain tc1 = cfg.getToolChain().getSuperClass();
					if (tc1 != null) {
						IBuilder b = tc1.getBuilder();
						cfg.changeBuilder(b, ManagedBuildManager.calculateChildId(b.getId(), null), b.getUniqueRealName());
						try {
							((IFolderInfo)ri).modifyToolChain(ri.getTools(), tc1.getTools());
						} catch (BuildException e) {}
					}
				} else {
					cfg1 = ManagedBuildManager.getPreferenceConfiguration(true);
				}
			} else {
				IBuilder b = cfg1.getBuilder();
				cfg.changeBuilder(b, ManagedBuildManager.calculateChildId(b.getId(), null), b.getUniqueRealName());
				IResourceInfo ri1 = cfg1.getResourceInfo(ri.getPath(), false);
				if (!((ToolChain)cfg1.getToolChain()).isPreferenceToolChain())
					copyFI(ri1, ri);
				else {
					cfg1 = ManagedBuildManager.getPreferenceConfiguration(false);
					IToolChain tc = cfg1.getToolChain();
					try {
						((IFolderInfo)ri).changeToolChain(tc, ManagedBuildManager.calculateChildId(tc.getId(), null), tc.getUniqueRealName());
						((IFolderInfo)ri).modifyToolChain(ri.getTools(), tc.getTools());
						
					} catch (BuildException e) {}
				}
			}
		} else if (page.isForFolder()) {
//			2.per-folder : change to the same tool-chain as the one used by the parent
//			folder.
			IResourceInfo ri1 = cfg.getResourceInfo(ri.getPath().removeLastSegments(1), false);
			copyFI(ri1, ri);
			
		} else if (page.isForFile()) {
//			3.per-file : change to the tool from the parent folder's tool-chain suitable
//			for the given file. NOTE: the custom build step tool should be preserved!
			IResourceInfo ri1 = cfg.getResourceInfo(ri.getPath().removeLastSegments(1), false);
			String ext = ri.getPath().getFileExtension();

			ITool[] ts1 = ri1.getTools();
			ITool newTool = null; 
			for (int i=0; i<ts1.length; i++) {
				if (ts1[i].isInputFileType(ext)) {
					newTool = ts1[i];
					break;
				}				
			}
			
			ITool[] tools = ri.getTools();
			int pos = -1;
			for (int i=0; i<tools.length; i++) {
				if (tools[i] != null && !tools[i].getCustomBuildStep()) {
					pos = i; 
					break;
				}
			}

			if (newTool != null) {
				if (pos == -1) { // 1: NO TOOL -> tool 
					ITool[] ts2 = new ITool[tools.length + 1];
					System.arraycopy(tools, 0, ts2, 0, tools.length);
					ts2[tools.length] = newTool;
					tools = ts2;
				} else          // 2: tool -> tool
					tools[pos] = newTool;
			} else if (pos != -1){ // 3: tool -> NO TOOL;
				ITool[] ts2 = new ITool[tools.length - 1];
				if (pos > 0)
					System.arraycopy(tools, 0, ts2, 0, pos-1);
				if (pos < ts2.length)
					System.arraycopy(tools, pos+1, ts2, pos, ts2.length - pos);
				tools = ts2;
			}
			((IFileInfo)ri).setTools(tools);
		}			
		updateData();
	}
	
	private void copyFI(IResourceInfo src, IResourceInfo dst) {
		if (src == null || dst == null)
			return;
		if (src instanceof IFolderInfo && dst instanceof IFolderInfo) { 
			IFolderInfo fi1 = (IFolderInfo)src;
			IFolderInfo fi = (IFolderInfo)dst;
			IToolChain tc = fi1.getToolChain();
			ITool[] tools1 = fi1.getTools();
			ITool[] tools2 = fi.getTools();
			try {
				fi.changeToolChain(tc, ManagedBuildManager.calculateChildId(tc.getId(), null), tc.getUniqueRealName());
				fi.modifyToolChain(tools2, tools1);
			} catch (BuildException e) {}
		}
	}
	
	protected void updateButtons() {} // Do nothing. No buttons to update.
	
	private void saveToolSelected() {
		int pos = c_tool.getSelectionIndex();
		ITool[] oldTools = ri.getTools();
		if (oldTools != null && oldTools.length > 0) {
			if (pos == (c_tool.getItemCount() - 1)) {// NO TOOL selected
				List newTools = new ArrayList(oldTools.length);
				for (int i=0; i<oldTools.length; i++)
					if (oldTools[i].getCustomBuildStep()) // ignore all tools except customBuild's
						newTools.add(oldTools[i]);
				((IFileInfo)ri).setTools((ITool[])newTools.toArray(new ITool[newTools.size()]));
			} else { // modify only one (selected) tool 
				for (int i=0; i<oldTools.length; i++) {
					if (oldTools[i].getCustomBuildStep()) // skip custom steps
						continue;
					if ( ! oldTools[i].matches(v_tools[pos])) { // really changed ?
						oldTools[i] = v_tools[pos];
						((IFileInfo)ri).setTools(oldTools);
					}
					break; // exit after first not-custom tool met
				}
			}
		} else { // old list was empty.
			if (pos == (c_tool.getItemCount() - 1)) // NO TOOL selected
				((IFileInfo)ri).setTools(new ITool[0]);
			else
				((IFileInfo)ri).setTools(new ITool[] {v_tools[pos]});				
		}
	}
	
	private void modifyTools() {
		ToolSelectionDialog d = new ToolSelectionDialog(usercomp.getShell());
		d.all = v_tools;
		d.fi = (IFolderInfo)ri; 
		int result = d.open();
		if (result == 0 && (d.removed.size() > 0 || d.added.size() > 0)) {
			try {
				((IFolderInfo)ri).modifyToolChain(
						(ITool[])d.removed.toArray(new ITool[d.removed.size()]), 
						(ITool[])d.added.toArray(new ITool[d.added.size()]));
			} catch (BuildException b) {}
			updateData();
		}
	}
	
	private void modifyToolchain() {
		if (ri instanceof IFolderInfo) {
			int x = c_toolchain.getSelectionIndex();
			try {
				if (NO_TC.equals(c_toolchain.getItem(x))) {
					((IFolderInfo)ri).changeToolChain(null, null, null);
				} else {
					IToolChain tc = v_tcs[x];
					if (tc == null) return;
					((IFolderInfo)ri).changeToolChain(tc, ManagedBuildManager.calculateChildId(tc.getId(), null), tc.getUniqueRealName());
			}
			} catch (BuildException be) {}
			updateData();
		}
	}
	
	private void modifyBuilder() {
		int x = c_builder.getSelectionIndex();
		IBuilder b = v_bs[x];
		cfg.changeBuilder(b, ManagedBuildManager.calculateChildId(b.getId(), null), b.getUniqueRealName());
		updateData();
	}
}
