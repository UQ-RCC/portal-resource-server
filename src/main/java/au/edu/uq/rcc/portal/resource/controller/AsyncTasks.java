package au.edu.uq.rcc.portal.resource.controller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.annotation.ManagedBean;

/**
 * A servlet listener to keep a thread pool active during the life of the application
 *
 * @author jrigby
 */
@ManagedBean
public class AsyncTasks implements ServletContextListener {

    private static ExecutorService executor;

    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
	System.out.println("-------------------------------------------------------@AsyncTasks: shutting down");
        executor.shutdown();
    }

    @Override
    public void contextInitialized(ServletContextEvent arg0) {
	System.out.println("-----------------------------------------------------@AsyncTasks: create new threadpool");
        executor = Executors.newCachedThreadPool();
    }

    public static ExecutorService getExecutorService() {
        return executor;
    }

}
