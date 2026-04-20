package com.mays.ffxivgatetimer.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class GateTimerToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        GateTimerPanel panel = new GateTimerPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel.getRootPanel(), "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
