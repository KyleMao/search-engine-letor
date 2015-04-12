import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * QryEval illustrates the architecture for the portion of a search engine that evaluates queries
 * for a LeToR system.
 * 
 * @author KyleMao
 *
 */

public class QryEval {

  private static String usage = "Usage:  java " + System.getProperty("sun.java.command")
      + " paramFile\n\n";
  private static int MAX_RESULT = 100;

  // The index file reader is accessible via a global variable. This
  // isn't great programming style, but the alternative is for every
  // query operator to store or pass this value, which creates its
  // own headaches.

  public static IndexReader READER;

  // Create and configure an English analyzer that will be used for
  // query parsing.

  public static EnglishAnalyzerConfigurable analyzer = new EnglishAnalyzerConfigurable(
      Version.LUCENE_43);
  static {
    analyzer.setLowercase(true);
    analyzer.setStopwordRemoval(true);
    analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  /**
   * @param args The only argument is the path to the parameter file.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {

    // must supply parameter file
    if (args.length < 1) {
      fatalError(usage);
    }

    long startTime = System.currentTimeMillis();

    Map<String, String> params = readParam(args[0]);

    // open the index
    READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));
    if (READER == null) {
      fatalError(usage);
    }

    FeatureGenerator featureGenerator = new FeatureGenerator(params);

    // generate training data
    featureGenerator.generateTrainData();

    // print running time and memory usage
    long endTime = System.currentTimeMillis();
    System.out.println("Running Time: " + (endTime - startTime) + " ms");
    printMemoryUsage(false);
  }

  /**
   * Given a query string, returns the terms one at a time with stopwords removed and the terms
   * stemmed using the Krovetz stemmer.
   * 
   * Use this method to process raw query terms.
   * 
   * @param query String containing query
   * @return Array of query tokens
   * @throws IOException
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();
    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }
    return tokens.toArray(new String[tokens.size()]);
  }

  /**
   * Read in the parameter file. One parameter per line in format of key=value.
   * 
   * @param paramPath
   * @return A map of parameters for the search engine
   * @throws IOException
   */
  private static Map<String, String> readParam(String paramPath) throws IOException {

    Map<String, String> params = new HashMap<String, String>();
    Scanner scan = new Scanner(new File(paramPath));
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split("=");
      params.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());
    scan.close();

    // parameters required for this example to run
    if (!(params.containsKey("indexPath") && params.containsKey("queryFilePath")
        && params.containsKey("trecEvalOutputPath") && params.containsKey("retrievalAlgorithm"))) {
      fatalError("Error: Parameters were missing.");
    }

    return params;
  }

  /**
   * Get the external document id for a document specified by an internal document id. If the
   * internal id doesn't exists, returns null.
   * 
   * @param iid The internal document id of the document.
   * @throws IOException
   */
  static String getExternalDocid(int iid) throws IOException {
    Document d = QryEval.READER.document(iid);
    String eid = d.get("externalId");
    return eid;
  }

  /**
   * Finds the internal document id for a document specified by its external id, e.g.
   * clueweb09-enwp00-88-09710. If no such document exists, it throws an exception.
   * 
   * @param externalId The external document id of a document.s
   * @return An internal doc id suitable for finding document vectors etc.
   * @throws Exception
   */
  static int getInternalDocid(String externalId) throws Exception {
    Query q = new TermQuery(new Term("externalId", externalId));

    IndexSearcher searcher = new IndexSearcher(QryEval.READER);
    TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
    searcher.search(q, collector);
    ScoreDoc[] hits = collector.topDocs().scoreDocs;

    if (hits.length < 1) {
      throw new Exception("External id not found.");
    } else {
      return hits[0].doc;
    }
  }

  /**
   * Print a message indicating the amount of memory used. The caller can indicate whether garbage
   * collection should be performed, which slows the program but reduces memory usage.
   * 
   * @param gc If true, run the garbage collector before reporting.
   * @return void
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc) {
      runtime.gc();
    }

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Write an error message and exit. This can be done in other ways, but I wanted something that
   * takes just one statement so that it is easy to insert checks without cluttering the code.
   * 
   * @param message The error message to write before exiting.
   * @return void
   */
  static void fatalError(String message) {
    System.err.println(message);
    System.exit(1);
  }

}
