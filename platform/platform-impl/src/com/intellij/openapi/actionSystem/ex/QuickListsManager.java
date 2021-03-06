/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.BundledQuickListsProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.PathUtilRt;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Set;

public class QuickListsManager implements ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance(QuickListsManager.class);
  static final String FILE_SPEC = StoragePathMacros.ROOT_CONFIG + "/quicklists";

  private static final String LIST_TAG = "list";

  private final ActionManager myActionManager;
  private final SchemesManager<QuickList, QuickList> mySchemesManager;

  public QuickListsManager(@NotNull ActionManager actionManager, @NotNull SchemesManagerFactory schemesManagerFactory) {
    myActionManager = actionManager;
    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC,
                                                                  new BaseSchemeProcessor<QuickList>() {
                                                                    @NotNull
                                                                    @Override
                                                                    public QuickList readScheme(@NotNull Element element) {
                                                                      return createItem(element);
                                                                    }

                                                                    @Override
                                                                    public Element writeScheme(@NotNull QuickList scheme) {
                                                                      Element element = new Element(LIST_TAG);
                                                                      scheme.writeExternal(element);
                                                                      return element;
                                                                    }
                                                                  },
                                                                  RoamingType.PER_USER);
  }

  public static QuickListsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(QuickListsManager.class);
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{mySchemesManager.getRootDirectory()};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return IdeBundle.message("quick.lists.presentable.name");
  }

  @NotNull
  private static QuickList createItem(@NotNull Element element) {
    QuickList item = new QuickList();
    item.readExternal(element);
    return item;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "QuickListsManager";
  }

  @Override
  public void initComponent() {
    for (BundledQuickListsProvider provider : BundledQuickListsProvider.EP_NAME.getExtensions()) {
      for (String path : provider.getBundledListsRelativePaths()) {
        try {
          InputStream inputStream = DecodeDefaultsUtil.getDefaultsInputStream(provider, path);
          if (inputStream == null) {
            // Error shouldn't occur during this operation thus we report error instead of info
            LOG.error("Cannot read quick list from " +  path);
          }
          else {
            Element element = JDOMUtil.load(inputStream);
            QuickList item = createItem(element);
            item.getExternalInfo().setHash(JDOMUtil.getTreeHash(element, true));
            item.getExternalInfo().setPreviouslySavedName(item.getName());
            item.getExternalInfo().setCurrentFileName(PathUtilRt.getFileName(path));
            mySchemesManager.addNewScheme(item, false);
          }
        }
        catch (Exception e) {
          LOG.error("Cannot read quick list from " + path + ": " + e.getMessage(), e);
        }
      }
    }
    mySchemesManager.loadSchemes();
    registerActions();
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  public QuickList[] getAllQuickLists() {
    Collection<QuickList> lists = mySchemesManager.getAllSchemes();
    return lists.toArray(new QuickList[lists.size()]);
  }

  private void registerActions() {
    // to prevent exception if 2 or more targets have the same name
    Set<String> registeredIds = new THashSet<String>();
    for (QuickList list : mySchemesManager.getAllSchemes()) {
      String actionId = list.getActionId();
      if (registeredIds.add(actionId)) {
        myActionManager.registerAction(actionId, new InvokeQuickListAction(list));
      }
    }
  }

  private void unregisterActions() {
    for (String oldId : myActionManager.getActionIds(QuickList.QUICK_LIST_PREFIX)) {
      myActionManager.unregisterAction(oldId);
    }
  }

  public void setQuickLists(@NotNull QuickList[] quickLists) {
    mySchemesManager.clearAllSchemes();
    unregisterActions();
    for (QuickList quickList : quickLists) {
      mySchemesManager.addNewScheme(quickList, true);
    }
    registerActions();
  }

  private static class InvokeQuickListAction extends QuickSwitchSchemeAction {
    private final QuickList myQuickList;

    public InvokeQuickListAction(@NotNull QuickList quickList) {
      myQuickList = quickList;
      myActionPlace = ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION;
      getTemplatePresentation().setDescription(myQuickList.getDescription());
      getTemplatePresentation().setText(myQuickList.getName(), false);
    }

    @Override
    protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
      ActionManager actionManager = ActionManager.getInstance();
      for (String actionId : myQuickList.getActionIds()) {
        if (QuickList.SEPARATOR_ID.equals(actionId)) {
          group.addSeparator();
        }
        else {
          AnAction action = actionManager.getAction(actionId);
          if (action != null) {
            group.add(action);
          }
        }
      }
    }

    @Override
    protected boolean isEnabled() {
      return true;
    }
  }
}
