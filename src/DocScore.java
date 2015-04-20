import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class stores the external document IDs and their corresponding scores for a specific query.
 * 
 * @author KyleMao
 *
 */
public class DocScore {

  // A little utility class to create a <extdocid, score> object.
  private class DocScoreEntry implements Comparable<DocScoreEntry> {
    private String extId;
    private Double score;

    private DocScoreEntry(String extId, double score) {
      this.extId = extId;
      this.score = score;
    }

    // compareTo method used for sorting
    @Override
    public int compareTo(DocScoreEntry ds) {

      int scoreComp = ds.score.compareTo(this.score); // In descending order of score
      if (scoreComp != 0) {
        return scoreComp;
      } else {
        // In ascending order of external document ID
        return this.extId.compareTo(ds.extId);
      }
    }
  }

  public List<DocScoreEntry> scores;

  /**
   * Initialize the scores ArrayList.
   */
  public DocScore() {
    scores = new ArrayList<DocScoreEntry>();
  }

  /**
   * Get raw query results and create sorted document scores.
   * 
   * @param result Raw query results.
   * @throws IOException
   */
  public DocScore(QryResult result) throws IOException {
    scores = new ArrayList<DocScoreEntry>();
    ScoreList scoreList = result.docScores;
    for (int i = 0; i < scoreList.scores.size(); i++) {
      scores.add(new DocScoreEntry(QryEval.getExternalDocid(scoreList.getDocid(i)), scoreList
          .getDocidScore(i)));
    }

    Collections.sort(scores);
  }

  /**
   * Add a score entry to the score list.
   * 
   * @param externalId The external document ID.
   * @param score The score corresponding to the document.
   */
  public void add(String externalId, double score) {
    scores.add(new DocScoreEntry(externalId, score));
  }

  /**
   * Sort the scores.
   */
  public void sort() {
    Collections.sort(scores);
  }

  /**
   * Get the n'th external document id.
   * 
   * @param n
   * @return External ID of the corresponding document.
   */
  public String getExternalDocid(int n) {
    return this.scores.get(n).extId;
  }

  /**
   * Get the score of the n'th document.
   * 
   * @param n
   * @return Score of the corresponding document.
   */
  public double getDocidScore(int n) {
    return this.scores.get(n).score;
  }

}
