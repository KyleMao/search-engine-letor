import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;

/**
 * This class evaluates the score of a query and a document with a specific retrieval model.
 * 
 * @author KyleMao
 *
 */

public class RetrievalEvaluator {

  private DocLengthStore dls;

  private boolean hasBM25;
  private boolean hasIndri;
  private double b;
  private double k_1;
  private double k_3;
  private int mu;
  private double lambda;

  private int N;
  private double avgLenBody;
  private double avgLenTitle;
  private double avgLenUrl;
  private double avgLenInlink;
  private double qtf;

  private long colLenBody;
  private long colLenTitle;
  private long colLenUrl;
  private long colLenInlink;

  /**
   * Initialize the RetrievalEvaluator with BM25 and Indri models.
   * 
   * @param modelBM25 The BM25 retrieval model.
   * @param modelIndri The Indri retrieval model.
   * @throws IOException
   */
  public RetrievalEvaluator(RetrievalModel modelBM25, RetrievalModel modelIndri) throws IOException {

    this.dls = new DocLengthStore(QryEval.READER);

    // Read the BM25 parameters if BM25 model is available
    if (modelBM25 != null) {
      this.hasBM25 = true;
      this.b = modelBM25.getParameter("b");
      this.k_1 = modelBM25.getParameter("k_1");
      this.k_3 = modelBM25.getParameter("k_3");
      this.N = QryEval.READER.numDocs();
      this.avgLenBody =
          (double) QryEval.READER.getSumTotalTermFreq("body") / QryEval.READER.getDocCount("body");
      this.avgLenTitle =
          (double) QryEval.READER.getSumTotalTermFreq("title")
              / QryEval.READER.getDocCount("title");
      this.avgLenUrl =
          (double) QryEval.READER.getSumTotalTermFreq("url") / QryEval.READER.getDocCount("url");
      this.avgLenInlink =
          (double) QryEval.READER.getSumTotalTermFreq("inlink")
              / QryEval.READER.getDocCount("inlink");
      this.qtf = 1.0;
    } else {
      this.hasBM25 = false;
    }

    // Read the Indri parameters if Indri model is available
    if (modelIndri != null) {
      this.hasIndri = true;
      this.mu = (int) modelIndri.getParameter("mu");
      this.lambda = modelIndri.getParameter("lambda");
      this.colLenBody = QryEval.READER.getSumTotalTermFreq("body");
      this.colLenTitle = QryEval.READER.getSumTotalTermFreq("title");
      this.colLenUrl = QryEval.READER.getSumTotalTermFreq("url");
      this.colLenInlink = QryEval.READER.getSumTotalTermFreq("inlink");
    } else {
      this.hasIndri = false;
    }
  }

  /**
   * Get the BM25 score for <q, d>of a specified field.
   * 
   * @param queryStems The stemmed BOW query.
   * @param internalId The internal document ID.
   * @param fieldName The field name.
   * @param featureDisable Specifies which feature is disabled.
   * @return BM25 score.
   * @throws IOException
   */
  public double getFeatureBM25(String[] queryStems, int internalId, String fieldName,
      Set<Integer> featureDisable) throws IOException {

    // Return 0.0 when this score is not needed
    if (!hasBM25) {
      return 0.0;
    }
    if (featureDisable.contains(4) && fieldName.equals("body")) {
      return 0.0;
    }
    if (featureDisable.contains(7) && fieldName.equals("title")) {
      return 0.0;
    }
    if (featureDisable.contains(10) && fieldName.equals("url")) {
      return 0.0;
    }
    if (featureDisable.contains(13) && fieldName.equals("inlink")) {
      return 0.0;
    }

    double score = 0.0;
    TermVector termVector = null;
    try {
      termVector = new TermVector(internalId, fieldName);
    } catch (Exception e) {
      return Double.NaN;
    }

    double docLen = dls.getDocLength(fieldName, internalId);
    double avgLen = getAvglen(fieldName);

    for (int i = 1; i < termVector.stemsLength(); i++) {
      String stem = termVector.stemString(i);
      if (Arrays.asList(queryStems).contains(stem)) {
        // Calculate the BM25 score
        double tf = termVector.stemFreq(i);
        double df = termVector.stemDf(i);
        double idf_weight = Math.log(((double) N - df + 0.5) / (df + 0.5));
        idf_weight = Math.max(idf_weight, 0.0);
        double tf_weight = tf / (tf + k_1 * ((1 - b) + b * docLen / avgLen));
        double user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
        score += idf_weight * tf_weight * user_weight;
      }
    }
    return score;
  }

  /**
   * Get the Indri score for <q, d>of a specified field.
   * 
   * @param queryStems The stemmed BOW query.
   * @param internalId The internal document ID.
   * @param fieldName The field name.
   * @param featureDisable Specifies which feature is disabled.
   * @return Indri score.
   * @throws IOException
   */
  public double getFeatureIndri(String[] queryStems, int internalId, String fieldName,
      Set<Integer> featureDisable) throws IOException {

    // Return 0.0 when this score is not needed
    if (!hasIndri) {
      return 0.0;
    }
    if (featureDisable.contains(5) && fieldName.equals("body")) {
      return 0.0;
    }
    if (featureDisable.contains(8) && fieldName.equals("title")) {
      return 0.0;
    }
    if (featureDisable.contains(11) && fieldName.equals("url")) {
      return 0.0;
    }
    if (featureDisable.contains(14) && fieldName.equals("inlink")) {
      return 0.0;
    }

    TermVector termVector = null;
    try {
      termVector = new TermVector(internalId, fieldName);
    } catch (Exception e) {
      return Double.NaN;
    }
    double score = 1.0;
    Set<Integer> hasScore = new HashSet<Integer>();

    double docLen = dls.getDocLength(fieldName, internalId);
    double colLen = getColLen(fieldName);

    for (int i = 1; i < termVector.stemsLength(); i++) {
      String stem = termVector.stemString(i);
      if (Arrays.asList(queryStems).contains(stem)) {
        hasScore.add(Arrays.asList(queryStems).indexOf(stem));
        // Calculate the Indri score
        double tf = termVector.stemFreq(i);
        double ctf = QryEval.READER.totalTermFreq(new Term(fieldName, new BytesRef(stem)));
        double p_mle = ctf / colLen;
        double p = (1 - lambda) * (tf + mu * p_mle) / (docLen + mu) + lambda * p_mle;
        score *= Math.pow(p, 1.0 / (double) queryStems.length);
      }
    }

    // Deal with default scores
    if (hasScore.isEmpty()) {
      score = 0.0;
    } else {
      for (int i = 0; i < queryStems.length; i++) {
        if (!hasScore.contains(i)) {
          double ctf =
              QryEval.READER.totalTermFreq(new Term(fieldName, new BytesRef(queryStems[i])));
          double p_mle = ctf / colLen;
          double p = (1 - lambda) * mu * p_mle / (docLen + mu) + lambda * p_mle;
          score *= Math.pow(p, 1.0 / (double) queryStems.length);
        }
      }
    }

    return score;
  }

  /*
   * Get the average document length for a specified field.
   */
  private double getAvglen(String fieldName) {

    if (fieldName.equals("body")) {
      return avgLenBody;
    } else if (fieldName.equals("title")) {
      return avgLenTitle;
    } else if (fieldName.equals("url")) {
      return avgLenUrl;
    } else if (fieldName.equals("inlink")) {
      return avgLenInlink;
    }

    return 0.0;
  }

  /*
   * Get the average document length for a specified field.
   */
  private double getColLen(String fieldName) {

    if (fieldName.equals("body")) {
      return colLenBody;
    } else if (fieldName.equals("title")) {
      return colLenTitle;
    } else if (fieldName.equals("url")) {
      return colLenUrl;
    } else if (fieldName.equals("inlink")) {
      return colLenInlink;
    }

    return 0.0;
  }

}
