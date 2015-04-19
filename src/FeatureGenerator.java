import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

  private static int N_RESULT = 100;
  private static int N_FEATURE = 18;
  private Map<String, String> params;
  private Set<Integer> featureDisable;
  private Map<String, Double> pageRankScores;
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
    this.pageRankScores = new HashMap<String, Double>();
    if (!featureDisable.contains(3)) {
      Scanner scanner =
          new Scanner(new BufferedReader(new FileReader(this.params.get("letor:pageRankFile"))));
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        String externalId = line.substring(0, line.lastIndexOf('\t'));
        double score = Double.parseDouble(line.substring(line.lastIndexOf('\t') + 1));
        pageRankScores.put(externalId, score);
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

    // Create the output file
    File trainFeatureFile = new File(params.get("letor:trainingFeatureVectorsFile"));
    if (!trainFeatureFile.exists()) {
      trainFeatureFile.createNewFile();
    }
    BufferedWriter writer = new BufferedWriter(new FileWriter(trainFeatureFile.getAbsoluteFile()));

    // Read the training queries
    Scanner queryScanner =
        new Scanner(new BufferedReader(new FileReader(params.get("letor:trainingQueryFile"))));

    while (queryScanner.hasNextLine()) {
      String qLine = queryScanner.nextLine();
      String queryId = qLine.substring(0, qLine.indexOf(':'));
      String query = qLine.substring(qLine.indexOf(':') + 1);

      // Open the relevance judgment file
      Scanner relevanceScanner =
          new Scanner(new BufferedReader(new FileReader(params.get("letor:trainingQrelsFile"))));

      // Store all the external IDs, relevances, feature vectors for documents in the same query
      List<String> externalIds = new ArrayList<String>();
      List<Integer> relevances = new ArrayList<Integer>();
      List<Double[]> featureVectors = new ArrayList<Double[]>();

      while (relevanceScanner.hasNextLine()) {
        String rLine = relevanceScanner.nextLine();
        String[] parts = rLine.split(" ");
        if (parts[0].equals(queryId)) {
          externalIds.add(parts[2]);
          relevances.add(Integer.parseInt(parts[3]));
          featureVectors
              .add(calculateFeatures(query, parts[2], QryEval.getInternalDocid(parts[2])));
        }
      }
      relevanceScanner.close();

      normalizeFeature(featureVectors);
      writeFeature(writer, queryId, relevances, externalIds, featureVectors);
    }
    queryScanner.close();
    writer.close();
  }

  public void generateTestData() throws IOException {

    RetrievalModel modelBM25 = getModel("BM25");

    // Create the output file
    File testFeatureFile = new File(params.get("letor:testingFeatureVectorsFile"));
    if (!testFeatureFile.exists()) {
      testFeatureFile.createNewFile();
    }
    BufferedWriter writer = new BufferedWriter(new FileWriter(testFeatureFile.getAbsoluteFile()));

    // Read the test queries
    Scanner queryScanner =
        new Scanner(new BufferedReader(new FileReader(params.get("queryFilePath"))));

    while (queryScanner.hasNextLine()) {
      // Get initial BM25 ranking
      String qLine = queryScanner.nextLine();
      String queryId = qLine.substring(0, qLine.indexOf(':'));
      String query = qLine.substring(qLine.indexOf(':') + 1);
      Qryop qTree = QryEval.parseQuery(query, modelBM25);
      QryResult result = qTree.evaluate(modelBM25);
      DocScore docScore = new DocScore(result);

      List<String> externalIds = new ArrayList<String>();
      // Store all the feature vectors for documents in the same query
      List<Double[]> featureVectors = new ArrayList<Double[]>();

      for (int i = 0; i < N_RESULT && i < docScore.scores.size(); i++) {

      }
    }

  }

  /*
   * Returns a feature vector for the <q, d> pair.
   */
  private Double[] calculateFeatures(String query, String externalId, int internalId)
      throws IOException {

    Double[] f = new Double[N_FEATURE];

    Document d = QryEval.READER.document(internalId);
    String rawUrl = d.get("rawUrl");
    String[] queryStems = QryEval.tokenizeQuery(query);

    f[0] = getSpamScore(d);
    f[1] = getUrlDepth(rawUrl);
    f[2] = getWikiScore(rawUrl);
    f[3] = getPageRankScore(externalId);

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

    // Term overlap scores for <q, d> in 4 fields
    f[6] = getTermOverlapScore(queryStems, internalId, "body");
    f[9] = getTermOverlapScore(queryStems, internalId, "title");
    f[12] = getTermOverlapScore(queryStems, internalId, "url");
    f[15] = getTermOverlapScore(queryStems, internalId, "inlink");

    f[16] = 0.0;
    f[17] = 0.0;

    return f;
  }

  /*
   * Returns the spam score for a document.
   */
  private double getSpamScore(Document d) {

    if (featureDisable.contains(0)) {
      return 0.0;
    } else {
      return Integer.parseInt(d.get("score"));
    }
  }

  /*
   * Returns the URL depth for a document.
   */
  private double getUrlDepth(String url) {

    if (featureDisable.contains(1)) {
      return 0.0;
    } else {
      return (url.length() - url.replace("/", "").length());
    }
  }

  /*
   * Returns the FromWikipedia score for a document.
   */
  private double getWikiScore(String url) {

    if ((!featureDisable.contains(2)) && url.contains("wikipedia.org")) {
      return 1.0;
    } else {
      return 0.0;
    }
  }

  /*
   * Returns the PageRank score for a document.
   */
  private double getPageRankScore(String externalId) {

    if (featureDisable.contains(3)) {
      return 0.0;
    } else if (pageRankScores.containsKey(externalId)) {
      return pageRankScores.get(externalId);
    } else {
      return Double.NaN;
    }
  }

  /*
   * Returns the term overlap score for <q, d> of a specified field.
   */
  private double getTermOverlapScore(String[] queryStems, int internalId, String fieldName)
      throws IOException {

    // Return 0.0 when this score is not needed
    if (featureDisable.contains(6) && fieldName.equals("body")) {
      return 0.0;
    }
    if (featureDisable.contains(9) && fieldName.equals("title")) {
      return 0.0;
    }
    if (featureDisable.contains(12) && fieldName.equals("url")) {
      return 0.0;
    }
    if (featureDisable.contains(15) && fieldName.equals("inlink")) {
      return 0.0;
    }

    double score = 0.0;
    TermVector termVector = null;
    try {
      termVector = new TermVector(internalId, fieldName);
    } catch (Exception e) {
      return Double.NaN;
    }
    for (int i = 1; i < termVector.stemsLength(); i++) {
      String stem = termVector.stemString(i);
      if (Arrays.asList(queryStems).contains(stem)) {
        score += 1.0;
      }
    }
    // Change to percentage
    score /= (double) queryStems.length;

    return score;
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

  /*
   * Write the feature vectors for SVM-rank.
   */
  private static void writeFeature(BufferedWriter writer, String qId, List<Integer> rels,
      List<String> externalIds, List<Double[]> vectors) throws IOException {

    for (int i = 0; i < rels.size(); i++) {
      String line = String.format("%d qid:%s", rels.get(i) + 3, qId);
      Double[] featureVector = vectors.get(i);
      for (int j = 0; j < featureVector.length; j++) {
        line += (" " + (j + 1) + ":" + featureVector[j]);
      }
      line += (" # " + externalIds.get(i));
      writer.write(line + '\n');
    }
  }

  private static void normalizeFeature(List<Double[]> featureValues) {

    Double[] minValues = new Double[N_FEATURE];
    Double[] maxValues = new Double[N_FEATURE];

    // Get the value range to this feature
    for (int i = 0; i < N_FEATURE; i++) {
      minValues[i] = Double.MAX_VALUE;
      maxValues[i] = Double.MIN_VALUE;

      for (Double[] f : featureValues) {
        if (!Double.isNaN(f[i])) {
          if (f[i] < minValues[i]) {
            minValues[i] = f[i];
          }
          if (f[i] > maxValues[i]) {
            maxValues[i] = f[i];
          }
        }
      }
    }

    // Calculate the normalized feature
    for (Double[] f : featureValues) {
      for (int i = 0; i < N_FEATURE; i++) {
        if (minValues[i].equals(maxValues[i]) || Double.isNaN(f[i])) {
          f[i] = 0.0;
        } else {
          f[i] = (f[i] - minValues[i]) / (maxValues[i] - minValues[i]);
        }
      }
    }
  }

}
