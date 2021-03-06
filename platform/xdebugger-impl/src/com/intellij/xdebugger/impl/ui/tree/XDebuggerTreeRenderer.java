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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AbstractExpandableItemsHandler;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBInsets;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.ImmediateFullValueEvaluator;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;

/**
 * @author nik
 */
class XDebuggerTreeRenderer extends ColoredTreeCellRenderer {
  private final MyColoredTreeCellRenderer myLink = new MyColoredTreeCellRenderer();
  private boolean myHaveLink;
  private int myLinkOffset;
  private int myLinkWidth;

  private final MyLongTextHyperlink myLongTextLink = new MyLongTextHyperlink();

  public XDebuggerTreeRenderer() {
    Insets myLinkIpad = myLink.getIpad();
    myLink.setIpad(new JBInsets(myLinkIpad.top, 0, myLinkIpad.bottom, myLinkIpad.right));
    Insets myIpad = getIpad();
    setIpad(new JBInsets(myLinkIpad.top, myIpad.left, myLinkIpad.bottom, 0));
  }

  public void customizeCellRenderer(@NotNull final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    myHaveLink = false;
    myLink.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    XDebuggerTreeNode node = (XDebuggerTreeNode)value;
    node.appendToComponent(this);
    setIcon(node.getIcon());

    Rectangle treeVisibleRect = tree.getVisibleRect();
    TreePath path = tree.getPathForRow(row);
    int rowX = path != null ? ((XDebuggerTree.LinkTreeUI)tree.getUI()).getRowX(row, path.getPathCount() - 1) : 0;

    if (myHaveLink) {
      setupLinkDimensions(treeVisibleRect, rowX);
    }
    else {
      if (rowX + super.getPreferredSize().width > treeVisibleRect.x + treeVisibleRect.width) {
        // text does not fit visible area - show link
        if (node instanceof XValueNodeImpl) {
          final String rawValue = DebuggerUIUtil.getNodeRawValue((XValueNodeImpl)node);
          if (!StringUtil.isEmpty(rawValue)) {
            myLongTextLink.setupComponent(rawValue, ((XDebuggerTree)tree).getProject());
            append(myLongTextLink.getLinkText(), myLongTextLink.getTextAttributes(), myLongTextLink);
            setupLinkDimensions(treeVisibleRect, rowX);
            myLinkWidth = 0;
          }
        }
      }
    }
    putClientProperty(AbstractExpandableItemsHandler.DISABLE_EXPANDABLE_HANDLER, myHaveLink ? true : null);
  }

  private void setupLinkDimensions(Rectangle treeVisibleRect, int rowX) {
    Dimension linkSize = myLink.getPreferredSize();
    myLinkWidth = linkSize.width;
    myLink.setBounds(0, 0, linkSize.width, linkSize.height);
    myLinkOffset = Math.min(super.getPreferredSize().width, treeVisibleRect.x + treeVisibleRect.width - myLinkWidth - rowX);
  }

  @Override
  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
    if (tag instanceof XDebuggerTreeNodeHyperlink && ((XDebuggerTreeNodeHyperlink)tag).alwaysOnScreen()) {
      myHaveLink = true;
      myLink.append(fragment, attributes, tag);
    }
    else {
      super.append(fragment, attributes, tag);
    }
  }

  @Override
  protected void doPaint(Graphics2D g) {
    if (myHaveLink) {
      Graphics2D textGraphics = (Graphics2D)g.create(0, 0, myLinkOffset, g.getClipBounds().height);
      try {
        super.doPaint(textGraphics);
      } finally {
        textGraphics.dispose();
      }
      g.translate(myLinkOffset, 0);
      myLink.doPaint(g);
      g.translate(-myLinkOffset, 0);
    }
    else {
      super.doPaint(g);
    }
  }

  @Override
  protected void doDrawString(Graphics2D g, String text, int x, int y) {
    TextLayout layout = createTextLayout(text, g.getFont(), g.getFontRenderContext());
    if (layout != null) {
      layout.draw(g, x, y);
    }
  }

  @Override
  protected int computeStringWidth(String text, Font font) {
    TextLayout layout = createTextLayout(text, font, getFontMetrics(font).getFontRenderContext());
    if (layout != null) {
      return (int)layout.getAdvance();
    }
    return 0;
  }

  @Nullable
  private static TextLayout createTextLayout(String text, Font basefont, FontRenderContext fontRenderContext) {
    if (StringUtil.isEmpty(text)) return null;
    FontPreferences fontPreferences = EditorColorsManager.getInstance().getGlobalScheme().getFontPreferences();
    AttributedString string = new AttributedString(text);
    int start = 0;
    int end = text.length();
    AttributedCharacterIterator it = string.getIterator(new AttributedCharacterIterator.Attribute[0], start, end);
    Font currentFont = basefont;
    int currentIndex = start;
    for(char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
      Font font = basefont;
      if (!font.canDisplay(c)) {
        font = ComplementaryFontsRegistry.getFontAbleToDisplay(c, basefont.getStyle(), fontPreferences).getFont();
      }
      int i = it.getIndex();
      if (!font.equals(currentFont)) {
        if (i > currentIndex) {
          string.addAttribute(TextAttribute.FONT, currentFont, currentIndex, i);
        }
        currentFont = font.deriveFont(basefont.getSize2D());
        currentIndex = i;
      }
    }
    if (currentIndex < end) {
      string.addAttribute(TextAttribute.FONT, currentFont, currentIndex, end);
    }
    return new TextLayout(string.getIterator(), fontRenderContext);
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (myHaveLink) {
      size.width += myLinkWidth;
    }
    return size;
  }

  @Nullable
  @Override
  public Object getFragmentTagAt(int x) {
    if (myHaveLink) {
      return myLink.getFragmentTagAt(x - myLinkOffset);
    }
    return null;
  }

  private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {}

    @Override
    protected void doPaint(Graphics2D g) {
      super.doPaint(g);
    }
  }

  private static class MyLongTextHyperlink extends XDebuggerTreeNodeHyperlink {
    private String myText;
    private Project myProject;

    public MyLongTextHyperlink() {
      super(XDebuggerBundle.message("node.test.show.full.value"));
    }

    public void setupComponent(String text, Project project) {
      myText = text;
      myProject = project;
    }

    @Override
    public boolean alwaysOnScreen() {
      return true;
    }

    @Override
    public void onClick(MouseEvent event) {
      DebuggerUIUtil.showValuePopup(new ImmediateFullValueEvaluator(myText), event, myProject, null);
      event.consume();
    }
  }
}
