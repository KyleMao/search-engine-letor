/**
 * This class implements the SCORE operator for all retrieval models. The single argument to a score
 * operator is a query operator that produces an inverted list. The SCORE operator uses this
 * information to produce a score list that contains document ids and scores.
 *
 * Copyright (c) 2015, Carnegie Mellon University. All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlScore extends QryopSl {

  private String field;

  /**
   * Construct a new SCORE operator. The SCORE operator accepts just one argument.
   * 
   * @param q The query operator argument.
   * @return @link{QryopSlScore}
   */
  public QryopSlScore(Qryop q) {
    this.args.add(q);
  }

  /**
   * Construct a new SCORE operator. Allow a SCORE operator to be created with no arguments. This
   * simplifies the design of some query parsing architectures.
   * 
   * @return @link{QryopSlScore}
   */
  public QryopSlScore() {}

  /**
   * Appends an argument to the list of query operator arguments. This simplifies the design of some
   * query parsing architectures.
   * 
   * @param q The query argument to append.
   */
  public void add(Qryop a) {
    this.args.add(a);
  }

  /**
   * Evaluate the query operator.
   * 
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelBM25) {
      return (evaluateBM25(r));
    }

    return null;
  }

  /**
   * Evaluate the query operator for BM25 retrieval model.
   * 
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluateBM25(RetrievalModel r) throws IOException {

    DocLengthStore dls = new DocLengthStore(QryEval.READER);

    // Evaluate the query argument.
    QryResult result = args.get(0).evaluate(r);

    // No need to save the parameters into SCORE operator because BM25 does not make use of
    // QryopSlScore.defaultScore()
    double b = r.getParameter("b");
    double k_1 = r.getParameter("k_1");
    double k_3 = r.getParameter("k_3");
    this.field = result.invertedList.field;
    double N = QryEval.READER.numDocs();
    double avglen =
        (double) QryEval.READER.getSumTotalTermFreq(field)
            / (double) QryEval.READER.getDocCount(field);
    double qtf = 1.0;
    double df = result.invertedList.df;

    for (int i = 0; i < df; i++) {
      double tf = result.invertedList.postings.get(i).tf;
      double docLen = dls.getDocLength(field, result.invertedList.postings.get(i).docid);
      double idf_weight = Math.log((N - df + 0.5) / (df + 0.5));
      idf_weight = Math.max(idf_weight, 0.0);
      double tf_weight = tf / (tf + k_1 * ((1 - b) + b * docLen / avglen));
      double user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
      double score = idf_weight * tf_weight * user_weight;
      result.docScores.add(result.invertedList.postings.get(i).docid, score);
    }

    // The SCORE operator should not return a populated inverted list.
    // If there is one, replace it with an empty inverted list.
    if (result.invertedList.df > 0)
      result.invertedList = new InvList();

    return result;
  }

  /*
   * Calculate the default score for a document that does not match the query argument. This score
   * is 0 for many retrieval models, but not all retrieval models.
   * 
   * @param r A retrieval model that controls how the operator behaves.
   * 
   * @param docid The internal id of the document that needs a default score.
   * 
   * @return The default score.
   */
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    return 0.0;
  }

  /**
   * Return a string version of this query operator.
   * 
   * @return The string version of this query operator.
   */
  public String toString() {

    String result = new String();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext();)
      result += (i.next().toString() + " ");

    return ("#SCORE( " + result + ")");
  }

  @Override
  public void addWeight(double w) throws IOException {}

  @Override
  public boolean needWeight() {
    return false;
  }

  @Override
  public void removeWeight() throws IOException {}

}
