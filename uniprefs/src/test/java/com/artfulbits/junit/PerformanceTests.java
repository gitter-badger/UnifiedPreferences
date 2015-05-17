package com.artfulbits.junit;

import com.artfulbits.benchmark.Meter;
import com.artfulbits.unipref.BuildConfig;

import org.junit.*;
import org.junit.rules.*;

import java.util.logging.Level;

/**
 * Class designed first of all for capturing performance metrics of the tests.<br/> <br/> Logic:<br/> <ul> <li>Inside
 * the test please use {@link Meter} class API for performance metrics capturing. Meter class automatically started in
 * {@link PerformanceTests#setUp()} and finalized in {@link PerformanceTests#tearDown()}.</li> <li>Typical approach is
 * to call {@link Meter#beat(String)} inside the test case.</li> <li>{@link PerformanceTests#warmUp()} - abstract
 * methods for executing warmUp logic. WarmUp excluded from the measurement.</li> </ul>
 */
public abstract class PerformanceTests {
  /* [ CONSTANTS ] ================================================================================================= */

  /** Out tag. */
  public static final String TAG = BuildConfig.APPLICATION_ID.substring(BuildConfig.APPLICATION_ID.lastIndexOf('.'));

	/* [ MEMBERS ] =================================================================================================== */

  /** jUnit test method name extraction. */
  @Rule
  public TestName mTestName = new TestName();

  /** Instance of micro-benchmarking. */
  private Meter mMeter = Meter.getInstance();
  /** Instance of the output buffer. */
  private Meter.Output mOutput;

	/* [ STATIC METHODS ] ============================================================================================ */

  //region Setup and TearDown
  @BeforeClass
  public static void setUpClass() {
    // do nothing for now
  }

  @AfterClass
  public static void tearDownClass() {
    // do nothing for now
  }

	/* [ ABSTRACT ] ================================================================================================== */

  /** Warm up the test before executing it body. */
  protected abstract void warmUp();

	/* [ IMPLEMENTATION & HELPERS ] ================================================================================== */

  /** Meter class configuration adaptation. */
  public void configMeter() {
    meter().setOutput(mOutput);

    meter().getConfig().OutputTag = TAG;
    meter().calibrate();
  }

  /** Get instance of the benchmark tool. */
  public Meter meter() {
    return mMeter;
  }

  /** {@inheritDoc} */
  @Before
  public void setUp() throws Exception {
    mOutput = new Meter.Output() {
      private StringBuilder mLog = new StringBuilder(64 * 1024).append("\r\n");

      @Override
      public void log(final Level level, final String tag, final String msg) {
        mLog.append(level.toString().charAt(0)).append(" : ")
            .append(tag).append(" : ")
            .append(msg).append("\r\n");
      }

      @Override
      public String toString() {
        return mLog.toString();
      }
    };
    mOutput.log(Level.INFO, "->", mTestName.getMethodName());

    mMeter = Meter.getInstance();
    configMeter();
    meter().start("--> " + mTestName.getMethodName());

    warmUp();
    meter().skip("warm up classes");
  }

  /** {@inheritDoc} */
  @After
  public void tearDown() throws Exception {
    meter().finish("<-- " + mTestName.getMethodName());

    mOutput.log(Level.INFO, "<-", mTestName.getMethodName());
    System.out.append(mOutput.toString());
  }
  //endregion
}
