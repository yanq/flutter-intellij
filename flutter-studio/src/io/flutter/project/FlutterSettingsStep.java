/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.project.DomainToPackageExpression;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBScrollPane;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterProjectType;
import io.flutter.module.settings.ProjectType;
import io.flutter.sdk.FlutterSdk;
import java.awt.Container;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configure Flutter project parameters that relate to platform-specific code.
 * These values are preserved (by the model) for use as defaults when other projects are created.
 */
public class FlutterSettingsStep extends ModelWizardStep<FlutterProjectModel> {
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JBScrollPane myRoot;
  private JPanel myRootPanel;
  private ValidatorPanel myValidatorPanel;

  private JTextField myCompanyDomain;
  private LabelWithEditButton myPackageName;
  private JCheckBox myKotlinCheckBox;
  private JCheckBox mySwiftCheckBox;
  private JLabel myLanguageLabel;
  private JPanel mySamplePanel;
  private ProjectType myProjectTypeForm;
  private boolean hasEntered = false;
  private FocusListener focusListener;

  public FlutterSettingsStep(FlutterProjectModel model, String title, Icon icon) {
    super(model, title, icon);
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    myRoot = StudioWizardStepPanel.wrappedWithVScroll(myRootPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRoot);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    FlutterProjectModel model = getModel();
    myKotlinCheckBox.setText(FlutterBundle.message("module.wizard.language.name_kotlin"));
    mySwiftCheckBox.setText(FlutterBundle.message("module.wizard.language.name_swift"));

    TextProperty packageNameText = new TextProperty(myPackageName);

    Expression<String> computedPackageName = new DomainToPackageExpression(model.companyDomain(), model.projectName()) {
      @Override
      public String get() {
        return super.get().replaceAll("_", "");
      }
    };
    BoolProperty isPackageSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.listen(packageNameText, value -> isPackageSynced.set(value.equals(computedPackageName.get())));

    myBindings.bindTwoWay(new TextProperty(myCompanyDomain), model.companyDomain());

    // The wizard changed substantially in 3.5. Something causes this page to not get properly validated
    // after it is added to the Swing tree. Here we check that we have to validate the tree, then do so.
    // It only needs to be done once, so we remove the listener to prevent possible flicker.
    ApplicationInfo info = ApplicationInfo.getInstance();
    if (info.getMajorVersion().equals("3") && info.getMinorVersion().equals("4")) { // "0" while debugging
      return; // Do nothing for 3.4
    }
    focusListener = new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        super.focusGained(e);
        Container parent = myRoot;
        while (parent != null && !(parent instanceof JBLayeredPane)) {
          parent = parent.getParent();
        }
        if (parent != null) {
          parent.validate();
        }
        myCompanyDomain.removeFocusListener(focusListener);
      }
    };
    myCompanyDomain.addFocusListener(focusListener);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRoot;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myCompanyDomain;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onEntering() {
    if (hasEntered) {
      return;
    }
    // The language options are not used for modules. Since their values are persistent we cannot
    // establish bindings before knowing the project type.
    if (getModel().isModule()) {
      myLanguageLabel.setVisible(false);
      myKotlinCheckBox.setVisible(false);
      mySwiftCheckBox.setVisible(false);
    }
    else {
      myLanguageLabel.setVisible(true);
      myKotlinCheckBox.setVisible(true);
      mySwiftCheckBox.setVisible(true);
      myBindings.bindTwoWay(new SelectedProperty(myKotlinCheckBox), getModel().useKotlin());
      myBindings.bindTwoWay(new SelectedProperty(mySwiftCheckBox), getModel().useSwift());
    }
    //noinspection OptionalGetWithoutIsPresent
    FlutterProjectType projectType = getModel().projectType().get().get();
    if (projectType == FlutterProjectType.APP) {
      FlutterSdk sdk = FlutterSdk.forPath(getModel().flutterSdk().get());
      if (sdk != null) {
        myProjectTypeForm.setSdk(sdk);
      }
    }
    mySamplePanel.setVisible(projectType == FlutterProjectType.APP);
    myProjectTypeForm.getProjectTypeCombo().setSelectedItem(projectType);
    myProjectTypeForm.getProjectTypeCombo().setVisible(false);
    hasEntered = true;
  }

  @Override
  protected void onProceeding() {
    getModel().setSample(myProjectTypeForm.getSample());
  }
}
