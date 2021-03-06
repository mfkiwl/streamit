package streamittest;

import java.io.*;
import java.util.*;

/**
 * All messages (informational and error) that are generated by the
 * regression tests are passed to this class.
 **/

public class ResultPrinter {
    /** to turn on verbose output **/
    static final boolean VERBOSE         = false;
    /**
     * File where stderr from executing processes is stored. In additon,
     * any process that exits with non zero exit value has its stdout and stderr
     * dumped as well.
     **/
    static final String ERROR_FILE       = "regtest_errors.txt";
    /** File where the successful tests are written (to put a carrot in the reg test results). **/
    static final String SUCCESS_FILE      = "regtest_success.txt";
    /** file where lines are written to direct the performance reaper script. **/
    static final String PERFORMANCE_FILE      = "regtest_perf_script.txt";
 
    static FileWriter errorStream    = null;
    static FileWriter successStream  = null;
    static FileWriter performanceStream  = null;

    /**
     * Print an informational message
     **/
    public static void printInfo(String message) {
	if (VERBOSE) {
	    System.out.println(message);
	}
    }

    /**
     * Print an error message.
     **/
    public static void printError(String message) {
	// make the filewriter if necessary
	if (errorStream == null) {
	    MakeFileWriters();
	}
	try {
	    // write to the output file
	    errorStream.write(message + "\n");
	} catch (Exception e) {
	    System.out.println("Error printing to the error output");
	    e.printStackTrace();
	    throw new RuntimeException();
	}
	if (VERBOSE) {
	    System.out.println(message);
	}
    }

    /**
     * Print a success message (so that we can also report on
     * how many tests passed as opposed to how many failed.
     **/
    public static void printSuccess(String message) {
	// make the filewriter if necessary
	if (successStream == null) {
	    MakeFileWriters();
	}
	try {
	    // write to the output file
	    successStream.write(message + "\n");
	    successStream.flush();
	} catch (Exception e) {
	    System.out.println("Error printing to the success output");
	    e.printStackTrace();
	    throw new RuntimeException();
	}
	if (VERBOSE) {
	    System.out.println(message);
	}
    }
    /**
     * Print a command to the performance script so that we
     * can automatically gather performance numbers for raw.
     * @param directory Directory where the test can be found
     * @param filename  Filename of the file to compile
     * @param options   String with the options to pass to the compiler
     * @param initializationOutputs The number of outputs that are
     *                              generated by any initialization of the
     *                              filters.
     * @param steadyStateOutputs    The number of outputs generated in the steady
     *                              state.
     **/
    public static void printPerformance(String directory,
					String filename,
					String options,
					int initializationOutputs,
					int steadyStateOutputs) {
	String DELIM = ":";
	String message = (directory + DELIM +
			  filename + DELIM +
			  options + DELIM +
			  initializationOutputs + DELIM +
			  steadyStateOutputs);
	// make the filewriter if necessary
	if (performanceStream == null) {
	    MakeFileWriters();
	}

	try {
	    // write to the output file
	    performanceStream.write(message + "\n");
	    performanceStream.flush();
	} catch (Exception e) {
	    System.out.println("Error printing to the performance script");
	    e.printStackTrace();
	    throw new RuntimeException();
	}
	if (VERBOSE) {
	    System.out.println(message);
	}
    }
	

    /**
     * Create a filewriter to write error messages to.
     **/
    public static void MakeFileWriters() {
	try {
	    // create the file writer for the error messages
	    errorStream       = new FileWriter(ERROR_FILE);
	    // create the file writer for the results file
	    successStream     = new FileWriter(SUCCESS_FILE);
	    // create the file writer for the performance script file
	    performanceStream = new FileWriter(PERFORMANCE_FILE);
	    
	} catch (Exception e) {
	    System.out.println("Error -- failed to create FileWriter:" + e.getMessage());
	    e.printStackTrace();
	}
    }	

    /**
     * flushes any output to disk.
     **/
    public static void flushFileWriter() {
	try {
	    if (errorStream != null) {
		// force disk flush
		errorStream.flush();
	    }
	    if (successStream != null) {
		// force disk flush
		successStream.flush();
	    }
	    if (performanceStream != null) {
		// force disk flush
		performanceStream.flush();
	    }
	} catch (Exception e) {
	    System.out.println("Error -- failed to flush filewriter:" + e.getMessage());
	    e.printStackTrace();
	}
    }

}
	
