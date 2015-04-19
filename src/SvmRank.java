import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * This class evokes SVM-Rank to do training and prediction.
 * 
 * @author KyleMao
 *
 */

public class SvmRank {

  private Map<String, String> params;

  /**
   * Initialize an SVM object with parameters.
   * 
   * @param params The parameters read from the parameter file.
   */
  public SvmRank(Map<String, String> params) {

    this.params = params;
  }

  public void trainSvm() throws Exception {

    // Read parameters used
    String svmRankLearnPath = params.get("letor:svmRankLearnPath");
    String svmRankParamC = params.get("letor:svmRankParamC");
    String qrelsFeatureOutputFile = params.get("letor:trainingFeatureVectorsFile");
    String modelOutputFile = params.get("letor:svmRankModelFile");

    // Run svm_rank_learn from within Java to train the model
    // svmRankLearnPath is the location of the svm_rank_learn utility,
    // which is specified by letor:svmRankLearnPath in the parameter file.
    // svmRankParamC is the value of the letor:svmRankParamC parameter.
    Process cmdProc =
        Runtime.getRuntime().exec(
            new String[] {svmRankLearnPath, "-c", svmRankParamC, qrelsFeatureOutputFile,
                modelOutputFile});

    // The stdout/stderr consuming code MUST be included.
    // It prevents the OS from running out of output buffer space and stalling.

    String line;
    // Consume stdout and print it out for debugging purposes
    BufferedReader stdoutReader =
        new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
    while ((line = stdoutReader.readLine()) != null) {
      System.out.println(line);
    }
    // Consume stderr and print it for debugging purposes
    BufferedReader stderrReader =
        new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
    while ((line = stderrReader.readLine()) != null) {
      System.out.println(line);
    }

    // Get the return value from the executable. 0 means success, non-zero
    // indicates a problem
    int retValue = cmdProc.waitFor();
    if (retValue != 0) {
      throw new Exception("SVM Rank crashed.");
    }
  }
}
