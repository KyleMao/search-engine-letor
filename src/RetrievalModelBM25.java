/**
 * BM25 retrieval model.
 * 
 * @author KyleMao
 *
 */
public class RetrievalModelBM25 extends RetrievalModel {

  private double b;
  private double k_1;
  private double k_3;

  /**
   * Set a retrieval model parameter.
   * 
   * @param parameterName
   * @param value
   * @return Whether the parameter is successfully set.
   */
  @Override
  public boolean setParameter(String parameterName, double value) {
    if (parameterName.equals("b")) {
      this.b = value;
      return true;
    } else if (parameterName.equals("k_1")) {
      this.k_1 = value;
      return true;
    } else if (parameterName.equals("k_3")) {
      this.k_3 = value;
      return true;
    } else {
      System.err.println("Error: Unknown parameter name for retrieval model " + "BM25: "
          + parameterName);
    }

    return false;
  }

  /**
   * Get a retrieval model parameter.
   * 
   * @param parameterName The name of the parameter to set.
   * @return value of the parameter.
   */
  @Override
  public double getParameter(String parameterName) {
    if (parameterName.equals("b")) {
      return this.b;
    } else if (parameterName.equals("k_1")) {
      return this.k_1;
    } else if (parameterName.equals("k_3")) {
      return this.k_3;
    } else {
      System.err.println("Error: Unknown parameter name for retrieval model " + "BM25: "
          + parameterName);
    }

    return 0.0;
  }

}
