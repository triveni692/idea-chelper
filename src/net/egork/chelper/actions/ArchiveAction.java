package net.egork.chelper.actions;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import net.egork.chelper.configurations.TaskConfiguration;
import net.egork.chelper.configurations.TopCoderConfiguration;
import net.egork.chelper.task.Task;
import net.egork.chelper.task.TopCoderTask;
import net.egork.chelper.codegeneration.CodeGenerationUtilities;
import net.egork.chelper.util.FileUtilities;
import net.egork.chelper.util.Messenger;
import net.egork.chelper.util.Utilities;

import java.io.IOException;

/**
 * @author Egor Kulikov (kulikov@devexperts.com)
 */
public class ArchiveAction extends AnAction {
	public void actionPerformed(AnActionEvent e) {
		if (!Utilities.isEligible(e.getDataContext()))
			return;
		final Project project = Utilities.getProject(e.getDataContext());
		final RunManagerImpl manager = RunManagerImpl.getInstanceImpl(project);
		RunnerAndConfigurationSettings selectedConfiguration =
			manager.getSelectedConfiguration();
		if (selectedConfiguration == null || !Utilities.isSupported(selectedConfiguration.getConfiguration())) {
			Messenger.publishMessage("Configuration not selected or selected configuration not supported",
				NotificationType.ERROR);
			return;
		}
		final RunConfiguration configuration = selectedConfiguration.getConfiguration();
		if (configuration instanceof TaskConfiguration) {
            final Task task = ((TaskConfiguration) configuration).getConfiguration();
            String archiveDir = Utilities.getData(project).archiveDirectory;
            String dateAndContest = getDateAndContest(task);
			final VirtualFile directory = FileUtilities.createDirectoryIfMissing(project, archiveDir + "/" + dateAndContest);
			if (directory == null) {
				Messenger.publishMessage("Cannot create directory '" + archiveDir + "/" + dateAndContest + "' in archive",
					NotificationType.ERROR);
				return;
			}
			CodeGenerationUtilities.createUnitTest(task, project);
			ApplicationManager.getApplication().runWriteAction(new Runnable() {
				public void run() {
					try {
						VirtualFile mainFile = FileUtilities.getFileByFQN(task.taskClass, project);
						if (mainFile != null) {
    						VfsUtil.copyFile(this, mainFile, directory);
	    					mainFile.delete(this);
                        }
                        PsiElement checker = JavaPsiFacade.getInstance(project).findClass(task.checkerClass, GlobalSearchScope.allScope(project));
						VirtualFile checkerFile = checker == null ? null : checker.getContainingFile() == null ? null : checker.getContainingFile().getVirtualFile();
						if (checkerFile != null && mainFile != null && checkerFile.getParent().equals(mainFile.getParent())) {
                            VfsUtil.copyFile(this, checkerFile, directory);
                            checkerFile.delete(this);
                        }
                        for (String testClass : task.testClasses) {
							VirtualFile testFile = FileUtilities.getFileByFQN(testClass, project);
                            if (testFile != null) {
                                VfsUtil.copyFile(this, testFile, directory);
                                testFile.delete(this);
                            }
                        }
                        VirtualFile taskFile = FileUtilities.getFile(project, task.location + "/" + canonize(task.name) + ".task");
                        if (taskFile != null) {
                            VfsUtil.copyFile(this, taskFile, directory);
                            taskFile.delete(this);
                        }
						manager.removeConfiguration(manager.getSelectedConfiguration());
						setOtherConfiguration(manager, task);
						Messenger.publishMessage("Configuration '" + configuration.getName() + "' successfully archived",
							NotificationType.INFORMATION);
					} catch (IOException e) {
						Messenger.publishMessage("Error archiving configuration '" + configuration.getName() +
							"' caused by " + e.getMessage(), NotificationType.ERROR);
						Messenger.publishMessage("Configuration not deleted", NotificationType.WARNING);
					}
				}
			});
		} else if (configuration instanceof TopCoderConfiguration) {
			final TopCoderTask task = ((TopCoderConfiguration) configuration).getConfiguration();
			String archiveDir = Utilities.getData(project).archiveDirectory;
			String dateAndContest = getDateAndContest(task);
			final VirtualFile directory = FileUtilities.createDirectoryIfMissing(project, archiveDir + "/" + dateAndContest);
			if (directory == null)
				return;
			CodeGenerationUtilities.createUnitTest(task, project);
			ApplicationManager.getApplication().runWriteAction(new Runnable() {
				public void run() {
					try {
						VirtualFile mainFile = FileUtilities.getFile(project, Utilities.getData(project).defaultDirectory
							+ "/" + task.name + ".java");
						if (mainFile != null) {
							VfsUtil.copyFile(this, mainFile, directory);
							mainFile.delete(this);
						}
						for (String testClass : task.testClasses) {
							VirtualFile testFile = FileUtilities.getFileByFQN(testClass, project);
							if (testFile != null) {
								VfsUtil.copyFile(this, testFile, directory);
								testFile.delete(this);
							}
						}
						VirtualFile taskFile = FileUtilities.getFile(project, Utilities.getData(project).defaultDirectory + "/" + task.name + ".tctask");
						if (taskFile != null) {
							VfsUtil.copyFile(this, taskFile, directory);
							taskFile.delete(this);
						}
						manager.removeConfiguration(manager.getSelectedConfiguration());
						setOtherConfiguration(manager, task);
						Messenger.publishMessage("Configuration " + configuration.getName() + " successfully archived",
							NotificationType.INFORMATION);
					} catch (IOException e) {
						Messenger.publishMessage("Error archiving configuration '" + configuration.getName() +
							"' caused by " + e.getMessage(), NotificationType.ERROR);
						Messenger.publishMessage("Configuration not deleted", NotificationType.WARNING);
					}
				}
			});
		}
	}

	private String getDateAndContest(TopCoderTask task) {
		return (task.contestName.length() == 0 ? "unsorted" : task.contestName);
//		String yearAndMonth = task.date;
//		int position = yearAndMonth.indexOf('.');
//		if (position != -1)
//			position = yearAndMonth.indexOf('.', position + 1);
//		if (position != -1)
//			yearAndMonth = yearAndMonth.substring(0, position);
//		return canonize(yearAndMonth) + "/" + canonize(task.date + " - " + (task.contestName.length() == 0 ? "unsorted" : task.contestName));
	}

	private String getDateAndContest(Task task) {
		return (task.contestName.length() == 0 ? "unsorted" : task.contestName);
//		String yearAndMonth = task.date.trim();
//		int position = yearAndMonth.indexOf('.');
//		if (position != -1) {
//			position = yearAndMonth.indexOf('.', position + 1);
//		}
//		if (position != -1) {
//			yearAndMonth = yearAndMonth.substring(0, position);
//		}
//		if (yearAndMonth.length() == 0) {
//			return canonize(task.contestName.length() == 0 ? "unsorted" : task.contestName);
//		}
//        return canonize(yearAndMonth) + "/" + canonize(task.date + " - " + (task.contestName.length() == 0 ? "unsorted" : task.contestName));
    }

    public static String canonize(String filename) {
        filename = filename.replaceAll("[\\\\?%*:|\"<>/]", "-");
		while (filename.endsWith("."))
			filename = filename.substring(0, filename.length() - 1);
		return filename;
    }

    public static void setOtherConfiguration(RunManagerImpl manager, Task task) {
		RunConfiguration[] allConfigurations = manager.getAllConfigurations();
        for (RunConfiguration configuration : allConfigurations) {
            if (configuration instanceof TaskConfiguration) {
                Task other = ((TaskConfiguration) configuration).getConfiguration();
                if (!task.contestName.equals(other.contestName))
                    continue;
                manager.setActiveConfiguration(new RunnerAndConfigurationSettingsImpl(manager, configuration, false));
                return;
            }
        }
        for (RunConfiguration configuration : allConfigurations) {
			if (configuration instanceof TaskConfiguration || configuration instanceof TopCoderConfiguration) {
				manager.setActiveConfiguration(new RunnerAndConfigurationSettingsImpl(manager, configuration, false));
				return;
			}
		}
	}

	public static void setOtherConfiguration(RunManagerImpl manager, TopCoderTask task) {
		RunConfiguration[] allConfigurations = manager.getAllConfigurations();
		for (RunConfiguration configuration : allConfigurations) {
			if (configuration instanceof TopCoderConfiguration) {
				TopCoderTask other = ((TopCoderConfiguration) configuration).getConfiguration();
				if (!task.contestName.equals(other.contestName))
					continue;
				manager.setActiveConfiguration(new RunnerAndConfigurationSettingsImpl(manager, configuration, false));
				return;
			}
		}
		for (RunConfiguration configuration : allConfigurations) {
			if (configuration instanceof TaskConfiguration || configuration instanceof TopCoderConfiguration) {
				manager.setActiveConfiguration(new RunnerAndConfigurationSettingsImpl(manager, configuration, false));
				return;
			}
		}
	}
}
