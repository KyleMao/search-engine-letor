import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * This class generates feature vectors and writes them to file.
 * 
 * @author KyleMao
 *
 */

public class FeatureGenerator {

  private static int N_FEATURE = 18;
  private Map<String, String> params;
  private Map<Integer, Double> pageRankScores;

  /**
   * Initialize a FeatureGenerator, read the PageRank scores into a Map.
   * 
   * @param params The parameters read from the .param file.
   * @throws FileNotFoundException
   */
  public FeatureGenerator(Map<String, String> params) throws FileNotFoundException {

    this.params = params;

    // Read page rank scores
    this.pageRankScores = new HashMap<Integer, Double>();
    Scanner scanner =
        new Scanner(new BufferedReader(new FileReader(this.params.get("letor:pageRankFile"))));
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      int internalId;
      try {
        internalId = QryEval.getInternalDocid(line.substring(0, line.lastIndexOf('\t')));
        double score = Double.parseDouble(line.substring(line.lastIndexOf('\t') + 1));
        pageRankScores.put(internalId, score);
      } catch (Exception e) {
      }
    }
    scanner.close();
  }

  /**
   * Generates training data for SVM-rank and write the feature vectors to file.
   * @throws Exception 
   */
  public void generateTrainData() throws Exception {

    Scanner queryScanner =
        new Scanner(new BufferedReader(new FileReader(params.get("letor:trainingQueryFile"))));
    while (queryScanner.hasNextLine()) {
      String qLine = queryScanner.nextLine();
      String queryId = qLine.substring(0, qLine.indexOf(':'));
      String query = qLine.substring(qLine.indexOf(':') + 1);

      Scanner relevanceScanner =
          new Scanner(new BufferedReader(new FileReader(params.get("letor:trainingQrelsFile"))));
      // Store all the feature vectors for documents in the same query
      List<Integer[]> featureVectors = new ArrayList<Integer[]>();
      while (relevanceScanner.hasNextLine()) {
        String rLine = relevanceScanner.nextLine();
        String[] parts = rLine.split(" ");
        if (parts[0].equals(queryId)) {
          featureVectors.add(calculateFeatures(query, QryEval.getInternalDocid(parts[2])));
        }
      }
      relevanceScanner.close();
    }
    queryScanner.close();
  }
  
  private Integer[] calculateFeatures(String query, int internalId) throws IOException {
    
    Integer[] f = new Integer[N_FEATURE];
    String[] tokens = QryEval.tokenizeQuery(query);
    return f;
  }
}
