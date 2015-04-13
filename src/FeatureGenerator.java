import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.lucene.document.Document;

/**
 * This class generates feature vectors and writes them to file.
 * 
 * @author KyleMao
 *
 */

public class FeatureGenerator {

  private static int N_FEATURE = 18;
  private Map<String, String> params;
  private Set<Integer> featureDisable;
  private Map<Integer, Double> pageRankScores;
  private RetrievalEvaluator retrievalEvaluator;

  /**
   * Initialize a FeatureGenerator.
   * 
   * @param params The parameters read from the .param file.
   * @throws IOException
   */
  public FeatureGenerator(Map<String, String> params) throws IOException {

    this.params = params;

    // Read the disabled features
    this.featureDisable = new HashSet<Integer>();
    if (this.params.containsKey("letor:featureDisable")) {
      String[] numStrings = this.params.get("letor:featureDisable").split(",");
      for (String numString : numStrings) {
        featureDisable.add(Integer.parseInt(numString) - 1);
      }
    }

    // Read page rank scores
    this.pageRankScores = new HashMap<Integer, Double>();
    if (!featureDisable.contains(3)) {
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

    // Read retrieval model parameters and store them in a RetrievalEvaluator.
    RetrievalModel modelBM25 = null;
    RetrievalModel modelIndri = null;
    if (!(featureDisable.contains(4) && featureDisable.contains(7) && featureDisable.contains(10) && featureDisable
        .contains(13))) {
      modelBM25 = getModel("BM25");
    }
    if (!(featureDisable.contains(5) && featureDisable.contains(8) && featureDisable.contains(11) && featureDisable
        .contains(14))) {
      modelIndri = getModel("Indri");
    }
    retrievalEvaluator = new RetrievalEvaluator(modelBM25, modelIndri);
  }

  /**
   * Generates training data for SVM-rank and write the feature vectors to file.
   * 
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
      List<Double[]> featureVectors = new ArrayList<Double[]>();
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

  private Double[] calculateFeatures(String query, int internalId) throws IOException {

    Double[] f = new Double[N_FEATURE];

    Document d = QryEval.READER.document(internalId);
    // Spam score for the document
    if (featureDisable.contains(0)) {
      f[0] = 0.0;
    } else {
      f[0] = (double) Integer.parseInt(d.get("score"));
    }

    String rawUrl = d.get("rawUrl");
    // Url depth for the document
    if (featureDisable.contains(1)) {
      f[1] = 0.0;
    } else {
      f[1] = (double) (rawUrl.length() - rawUrl.replace("", "").length());
    }

    // FromWikipedia score for the document
    if ((!featureDisable.contains(2)) && rawUrl.contains("wikipedia.org")) {
      f[2] = 1.0;
    } else {
      f[2] = 0.0;
    }

    // PageRank score for the document
    f[3] = getPageRankScore(internalId);

    String[] queryStems = QryEval.tokenizeQuery(query);
    
    // BM25 scores for <q, d> in 4 fields
    f[4] = retrievalEvaluator.getFeatureBM25(queryStems, internalId, "body", featureDisable);
    f[7] = retrievalEvaluator.getFeatureBM25(queryStems, internalId, "title", featureDisable);
    f[10] = retrievalEvaluator.getFeatureBM25(queryStems, internalId, "url", featureDisable);
    f[13] = retrievalEvaluator.getFeatureBM25(queryStems, internalId, "inlink", featureDisable);
    
    // Indri scores for <q, d> in 4 fields
    f[5] = retrievalEvaluator.getFeatureIndri(queryStems, internalId, "body", featureDisable);
    f[8] = retrievalEvaluator.getFeatureIndri(queryStems, internalId, "title", featureDisable);
    f[11] = retrievalEvaluator.getFeatureIndri(queryStems, internalId, "url", featureDisable);
    f[14] = retrievalEvaluator.getFeatureIndri(queryStems, internalId, "inlink", featureDisable);

    return f;
  }

  /*
   * Returns the PageRank score for a document.
   */
  private double getPageRankScore(int internalId) {

    if (featureDisable.contains(3) || (!pageRankScores.containsKey(internalId))) {
      return 0.0;
    } else {
      return pageRankScores.get(internalId);
    }
  }

  /*
   * Reads the parameters for a retrieval model and returns the model.
   */
  private RetrievalModel getModel(String modelName) {

    RetrievalModel model = null;

    if (modelName.equals("BM25")) {
      model = new RetrievalModelBM25();
      model.setParameter("b", Double.parseDouble(params.get("BM25:b")));
      model.setParameter("k_1", Double.parseDouble(params.get("BM25:k_1")));
      model.setParameter("k_3", Double.parseDouble(params.get("BM25:k_3")));
    } else if (modelName.equals("Indri")) {
      model = new RetrievalModelIndri();
      model.setParameter("mu", Integer.parseInt(params.get("Indri:mu")));
      model.setParameter("lambda", Double.parseDouble(params.get("Indri:lambda")));
    }

    return model;
  }

}
