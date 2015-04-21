import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;
import java.util.StringTokenizer;

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

    SvmRank svmRank = new SvmRank(params);

    // train
    svmRank.trainSvm();

    // generate testing data for top 100 documents in initial BM25 ranking
    featureGenerator.generateTestData();

    // produce scores for the test data
    svmRank.predict();

    // re-rank the initial ranking and output new result
    writeResults(params);

    // print running time and memory usage
    long endTime = System.currentTimeMillis();
    System.out.println("Running Time: " + (endTime - startTime) + " ms");
    printMemoryUsage(false);
  }

  /**
   * parseQuery converts a query string into a query tree.
   * 
   * @param qString A string containing a query
   * @param r The retrieval model for the query
   * @return currentOp
   * @throws IOException
   */
  protected static Qryop parseQuery(String qString, RetrievalModel r) throws IOException {

    Qryop currentOp = null;
    Stack<Qryop> stack = new Stack<Qryop>();

    // Add a default query operator to an unstructured query. This
    // is a tiny bit easier if unnecessary whitespace is removed.
    qString = qString.trim();
    // Add a default operators for BM25 retrieval model
    if (r instanceof RetrievalModelBM25) {
      qString = "#SUM(" + qString + ")";
    } else {
      return null;
    }

    // Tokenize the query.
    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token = null;

    // Each pass of the loop processes one token. To improve
    // efficiency and clarity, the query operator on the top of the
    // stack is also stored in currentOp.
    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();

      if (token.matches("[ ,(\t\n\r]")) {
        // Ignore most delimiters.
      } else if (token.equalsIgnoreCase("#sum")) {
        currentOp = new QryopSlSum();
        stack.push(currentOp);
      } else if (token.startsWith(")")) { // Finish current query operator.
        // If the current query operator is not an argument to
        // another query operator (i.e., the stack is empty when it
        // is removed), we're done (assuming correct syntax - see
        // below). Otherwise, add the current operator as an
        // argument to the higher-level operator, and shift
        // processing back to the higher-level operator.
        stack.pop();
        if (stack.empty())
          break;
        Qryop arg = currentOp;
        if (arg.args.size() > 0) {
          currentOp = stack.peek();
          currentOp.add(arg);
        }
      } else {
        // Lexical processing of the token before creating the query term, and check to see whether
        // the token specifies a particular field (e.g., apple.title).
        String[] termAndField = token.split("\\.");
        String term;
        String field;
        if (termAndField.length == 2) {
          field = termAndField[1];
          if (!(field.equalsIgnoreCase("url") || field.equalsIgnoreCase("keywords")
              || field.equalsIgnoreCase("title") || field.equalsIgnoreCase("body") || field
                .equalsIgnoreCase("inlink"))) {
            term = token;
            field = "body";
          } else {
            term = termAndField[0];
          }
        } else {
          term = token;
          field = "body";
        }

        String[] processedToken = tokenizeQuery(term);
        if (processedToken.length > 1) {
          System.err.println("Error: Invalid query term.");
          return null;
        } else if (processedToken.length > 0) {
          currentOp.add(new QryopIlTerm(processedToken[0], field));
        }
      }
    }

    // A broken structured query can leave unprocessed tokens on the
    // stack, so check for that.
    if (tokens.hasMoreTokens()) {
      System.err.println("Error:  Query syntax is incorrect.  " + qString);
      return null;
    }

    return currentOp;
  }

  private static void writeResults(Map<String, String> params) throws IOException {

    // create the output file
    File evalOut = new File(params.get("trecEvalOutputPath"));
    if (!evalOut.exists()) {
      evalOut.createNewFile();
    }
    BufferedWriter writer = new BufferedWriter(new FileWriter(evalOut.getAbsoluteFile()));

    // Get the scanners ready
    Scanner idScanner =
        new Scanner(new BufferedReader(
            new FileReader(params.get("letor:testingFeatureVectorsFile"))));
    Scanner scoreScanner =
        new Scanner(new BufferedReader(new FileReader(params.get("letor:testingDocumentScores"))));

    String lastQueryId = "";
    DocScore docScore = null;
    while (idScanner.hasNextLine()) {
      String line = idScanner.nextLine();
      String queryIdSeg = line.split(" ")[1];
      String queryId = queryIdSeg.substring(queryIdSeg.indexOf(':') + 1);
      if (!queryId.equals(lastQueryId)) {
        // A query is finished, sort the scores and write the results
        if (docScore != null) {
          docScore.sort();
          writeQueryResult(writer, docScore, lastQueryId);
        }
        lastQueryId = queryId;
        docScore = new DocScore();
      }
      String externalId = line.substring(line.indexOf('#') + 2);
      double score = scoreScanner.nextDouble();
      docScore.add(externalId, score);
    }
    idScanner.close();
    scoreScanner.close();

    docScore.sort();
    writeQueryResult(writer, docScore, lastQueryId);
    writer.close();
  }

  /**
   * Write the query results into a file.
   * 
   * @param writer The writer used to write out the results.
   * @param docScore The scores of top ranked documents.
   * @param queryId A String specifying the ID of the query.
   * @throws IOException
   */
  private static void writeQueryResult(BufferedWriter writer, DocScore docScore, String queryId)
      throws IOException {
    for (int i = 0; i < docScore.scores.size(); i++) {
      String line =
          String.format("%s Q0 %s %d %f zexim", queryId, docScore.getExternalDocid(i), i + 1,
              docScore.getDocidScore(i));
      writer.write(line + '\n');
    }
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
