/*
 * Copyright 2013 Hewlett-Packard Development Company, L.P
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.alm.ali.idea.action.taskboard;

import com.hp.alm.ali.idea.content.taskboard.TaskBoardSettingsDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

public class TaskBoardSettingsAction extends AnAction {

    public TaskBoardSettingsAction() {
        super("Settings", "Task Board Settings", IconLoader.getIcon("/general/settings.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = getEventProject(e);
        if (project != null) {
            TaskBoardSettingsDialog settings = new TaskBoardSettingsDialog(project);
            settings.setVisible(true);
        }
    }
}
