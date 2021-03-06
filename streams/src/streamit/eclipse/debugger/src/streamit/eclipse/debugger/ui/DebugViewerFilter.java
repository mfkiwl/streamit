package streamit.eclipse.debugger.ui;

import java.util.Vector;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.internal.core.LaunchManager;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import streamit.eclipse.debugger.launching.IStreamItLaunchingConstants;
import streamit.eclipse.debugger.launching.StreamItLocalApplicationLaunchConfigurationDelegate;

/**
 * @author kkuo
 */
public class DebugViewerFilter extends ViewerFilter {

    private ILaunch fParent = null;

    public Object[] filter(Viewer viewer, Object parent, Object[] elements) {
        
        // only handle if StreamIt Launch
        if (parent instanceof IJavaThread) {
            fParent = ((IJavaThread) parent).getLaunch();
            if (!StreamItLocalApplicationLaunchConfigurationDelegate.isStreamItLaunch(fParent))
                return elements;
        } else if (!(parent instanceof LaunchManager)) {
            return elements;
        }
        
        // only filter IJavaThread and LaunchManager parents
        Vector filteredElements = new Vector();
        for (int i = 0; i < elements.length; i++) {
            if (select(viewer, parent, elements[i])) {
                filteredElements.add(elements[i]);
            }
        }
        return filteredElements.toArray();
    }

    public boolean select(Viewer viewer, Object parentElement, Object element) {
        // parentElement is usually LaunchManager, Launch, JDIDebugTarget, JDIThread
        // child element is usually Launch, RuntimeProcess, JDIDebugTarget, JDIThread, JDIStackFrame
        
        if (element instanceof IJavaStackFrame) {

            // get cause of launch
            ISourceLocator locator = fParent.getSourceLocator();
            Object o = locator.getSourceElement((IStackFrame) element);

            // only handle if from .java
            if (!(o instanceof ICompilationUnit)) return false;
            return true;

        } else if (element instanceof ILaunch) {
            // filter out HelloWorld6 [StreamIt Application]
            ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
            ILaunch launch = (ILaunch) element;
            ILaunchConfiguration config = launch.getLaunchConfiguration(); 
            try {
                if (config.getType().equals(manager.getLaunchConfigurationType(IStreamItLaunchingConstants.ID_STR_APPLICATION))) return false;
            } catch (CoreException e) {
            }
            return true;            
        }
            
        return false;
    }
}
