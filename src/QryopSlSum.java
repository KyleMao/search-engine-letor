/**
 * This class implements the SUM operator for all retrieval models.
 * 
 * @author KyleMao
 *
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class QryopSlSum extends QryopSl {

  /**
   * It is convenient for the constructor to accept a variable number of arguments. Thus new
   * qryopSum (arg1, arg2, arg3, ...).
   * 
   * @param q A query argument (a query operator).
   */
  public QryopSlSum(Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
  }

  /**
   * Calculate the default score for the specified document if it does not match the query operator.
   * This score is 0 for many retrieval models, but not all retrieval models.
   * 
   * @param r A retrieval model that controls how the operator behaves.
   * @param docid The internal id of the document that needs a default score.
   * @return The default score.
   */
  @Override
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    return 0.0;
  }

  /**
   * Appends an argument to the list of query operator arguments. This simplifies the design of some
   * query parsing architectures.
   * 
   * @param {q} q The query argument (query operator) to append
   * @return void
   * @throws IOException
   */
  @Override
  public void add(Qryop q) throws IOException {
    this.args.add(q);
  }

  /**
   * Evaluates the query operator, including any child operators and returns the result.
   * 
   * @param r A retrieval model that controls how the operator behaves
   * @return The result of evaluating the query
   * @throws IOException
   */
  @Override
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelBM25) {
      return evaluateBM25(r);
    }

    return null;
  }

  /**
   * Evaluates the query operator for Indri retrieval model, including any child operators and
   * returns the result.
   * 
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluateBM25(RetrievalModel r) throws IOException {

    // Initialization
    allocArgPtrs(r);
    QryResult result = new QryResult();

    Map<Integer, Double> docScores = new HashMap<Integer, Double>();
    for (ArgPtr argPtr : argPtrs) {
      for (; argPtr.nextDoc < argPtr.scoreList.scores.size(); argPtr.nextDoc++) {
        int docid = argPtr.scoreList.getDocid(argPtr.nextDoc);
        if (docScores.containsKey(docid)) {
          docScores.put(docid,
              docScores.get(docid) + argPtr.scoreList.getDocidScore(argPtr.nextDoc));
        } else {
          docScores.put(docid, argPtr.scoreList.getDocidScore(argPtr.nextDoc));
        }
      }
    }
    
    for (Map.Entry<Integer, Double> entry : docScores.entrySet()) {
      result.docScores.add(entry.getKey(), entry.getValue());
    }
    
    freeArgPtrs();

    return result;
  }

  /**
   * Return a string version of this query operator.
   * 
   * @return The string version of this query operator.
   */
  @Override
  public String toString() {
    String result = new String();

    for (int i = 0; i < this.args.size(); i++)
      result += this.args.get(i).toString() + " ";

    return ("#SUM( " + result + ")");
  }

  @Override
  public void addWeight(double w) throws IOException {
  }

  @Override
  public boolean needWeight() {
    return false;
  }

  @Override
  public void removeWeight() throws IOException {
  }

}
