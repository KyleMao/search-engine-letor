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
  private String modelPath;

  /**
   * Initialize an SVM object with parameters.
   * 
   * @param params The parameters read from the parameter file.
   */
  public SvmRank(Map<String, String> params) {

    this.params = params;
    this.modelPath = this.params.get("letor:svmRankModelFile");
  }

  /**
   * Train SVM-Rank based on the training features.
   * 
   * @throws Exception
   */
  public void trainSvm() throws Exception {

    // Read parameters used
    String svmRankLearnPath = params.get("letor:svmRankLearnPath");
    String svmRankParamC = params.get("letor:svmRankParamC");
    String trainFeatureFile = params.get("letor:trainingFeatureVectorsFile");

    // Run svm_rank_learn from within Java to train the model
    // svmRankLearnPath is the location of the svm_rank_learn utility,
    // which is specified by letor:svmRankLearnPath in the parameter file.
    // svmRankParamC is the value of the letor:svmRankParamC parameter.
    Process cmdProc =
        Runtime.getRuntime().exec(
            new String[] {svmRankLearnPath, "-c", svmRankParamC, trainFeatureFile, modelPath});

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

  public void predict() throws Exception {

    // Read parameters used
    String svmRankClassifyPath = params.get("letor:svmRankClassifyPath");
    String testFeatureFile = params.get("letor:testingFeatureVectorsFile");
    String testDocScorePath = params.get("letor:testingDocumentScores");

    // Run svm_rank_classify from within Java to use the model to do prediction
    Process cmdProc =
        Runtime.getRuntime().exec(
            new String[] {svmRankClassifyPath, testFeatureFile, modelPath, testDocScorePath});

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
