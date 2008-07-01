/* BigProductAlgebra.java (c) 2005/06/19  Ralph Freese */

package org.uacalc.alg;

import java.util.*;
import java.util.logging.*;
import java.math.BigInteger;
import org.uacalc.util.*;
import org.uacalc.ui.tm.ProgressReport;
import org.uacalc.terms.*;

import org.uacalc.alg.conlat.*;
import org.uacalc.alg.op.AbstractOperation;
import org.uacalc.alg.op.Operation;
import org.uacalc.alg.op.OperationSymbol;
import org.uacalc.alg.sublat.*;

/**
 * This class represents the direct product of <tt>SmallAlgebra</tt>s
 * which is too big to be a <tt>SmallAlgebra</tt>.
 * We use IntArray for the elements of the unverse. After we have a 
 * real element scheme, we'll use that.
 *
 * @author Ralph Freese
 *
 * @version $Id$
 */
public class BigProductAlgebra extends GeneralAlgebra implements Algebra {

  static Logger logger = Logger.getLogger("org.uacalc.alg.BigProductAlgebra");
  static {
    logger.setLevel(Level.FINER);
  }

  protected List<SmallAlgebra> algebras;
  protected int[] sizes;
  protected int numberOfFactors;
  protected List<IntArray> constants;
  protected Map<IntArray,OperationSymbol> constantToSymbol;

  /**
   * -2 indicates the value has not been calculated; -1 that it is too big
   * to be an int.
   */
  protected int cardinality = -2;

  protected List<SmallAlgebra> rootAlgebras;
  protected int[] powers;

  protected BigProductAlgebra() {
    super(null);
  }

  protected BigProductAlgebra(String name) {
    super(name);
  }

  /**
   * Construct the direct product of a List of SmallAlgebra's.
   */
  public BigProductAlgebra(List<SmallAlgebra> algs) {
    this("", algs);
  }

  /**
   * Construct the direct product of a List of SmallAlgebra's.
   */
  public BigProductAlgebra(String name, List<SmallAlgebra> algs) {
    super(name);
    algebras = algs;
    numberOfFactors = algs.size();
    sizes = new int[numberOfFactors];
    int k = 0;
    for (Iterator<SmallAlgebra> it = algs.iterator(); it.hasNext(); k++) {
      sizes[k] = it.next().cardinality();
    }
    makeOperations();
  }

  /**
   * Construct the direct power of a SmallAlgebra.
   */
  public BigProductAlgebra(SmallAlgebra alg, int power) {
    this("", alg, power);
  }

  /**
   * Construct the direct power of a SmallAlgebra.
   */
  public BigProductAlgebra(String name, SmallAlgebra alg, int power) {
    super(name);
    rootAlgebras = new ArrayList<SmallAlgebra>(1);
    rootAlgebras.add(alg);
    setup(rootAlgebras, new int[] {power});
  }

  /**
   * Construct the direct product of a List of SmallAlgebra's raised to 
   * various powers.
   *
   * @param algs  a list of SmallAlgebras.
   *
   * @param powers  an array of powers, one for each algebra in algs.
   */
  public BigProductAlgebra(List<SmallAlgebra> algs, int[] powers) {
    this("", algs, powers);
  }

  /**
   * Construct the direct product of a List of SmallAlgebra's raised to 
   * various powers.
   *
   * @param algs  a list of SmallAlgebras.
   *
   * @param powers  an array of powers, one for each algebra in algs.
   */
  public BigProductAlgebra(String name, List<SmallAlgebra> algs, int[] powers) {
    super(name);
    setup(algs, powers);
  }

  private void setup(List<SmallAlgebra> algs, int[] powers) {
    this.powers = powers;
    this.rootAlgebras = algs;
    algebras = new ArrayList<SmallAlgebra>();
    numberOfFactors = 0;
    for (int i = 0; i < powers.length; i++) {
      SmallAlgebra alg = algs.get(i);
      final int pow = powers[i];
      numberOfFactors = numberOfFactors + pow;
      for (int j = 0; j < pow; j++) {
        algebras.add(alg);
      }
    }
    sizes = new int[numberOfFactors];
    for (int i = 0; i < numberOfFactors; i++) {
      sizes[i] = algebras.get(i).cardinality();
    }
    makeOperations();
  }

  protected void makeOperations() {
    final int k = algebras.get(0).operations().size();
    operations = new ArrayList<Operation>(k);
    for (int i = 0; i < k; i++) {
      final int arity = 
           ((Operation)algebras.get(0).operations().get(i)).arity();
      final List<Operation> opList = new ArrayList<Operation>(numberOfFactors);
      for (int j = 0; j < numberOfFactors; j++) {
        opList.add(algebras.get(j).operations().get(i));
      }
      //final int[][] argsExpanded = new int[arity][numberOfProducts];
      final int[] arg = new int[arity];
      Operation op = new AbstractOperation(opList.get(0).symbol(), size) {
          // this will act on a list of IntArray's representing elments of
          // the product and return an IntArray.
          // does this need code for zeroary ops??
          public Object valueAt(List args) {
            //List ans = new ArrayList();
            final int[] ans = new int[numberOfFactors];
            for (int j = 0; j < numberOfFactors; j++) {
              int index = 0;
              for (Iterator it = args.iterator(); it.hasNext(); index++) {
                arg[index] = ((IntArray)it.next()).get(j);
              }
              ans[j] = opList.get(j).intValueAt(arg);
            }
            return new IntArray(ans);
          }

          public int[] valueAt(int[][] args) {
            final int[] ans = new int[numberOfFactors];
            for (int j = 0; j < numberOfFactors; j++) {
              for (int index = 0; index < arity; index++) {
                arg[index] = args[index][j];
              }
              ans[j] = opList.get(j).intValueAt(arg);
            }
            return ans;
          }
      };
      operations.add(op);
    }
  }

  public void makeOperationTables() {
    for (Iterator<Operation> it = operations().iterator(); it.hasNext(); ) {
      it.next().makeTable();
    }
  }
  
  public List<IntArray> getConstants() {
    if (constants != null) return constants;
    constants = new ArrayList<IntArray>();
    constantToSymbol = new HashMap<IntArray,OperationSymbol>();
    HashSet<IntArray> hash = new HashSet<IntArray>();
    List<IntArray> emptyList = new ArrayList<IntArray>(); 
    List<Operation> ops = operations();
    for (Operation op : ops) {
      if (op.arity() == 0) {
        IntArray ia = (IntArray)op.valueAt(emptyList);
        if (!hash.contains(ia)) {
          hash.add(ia);
          constants.add(ia);
          constantToSymbol.put(ia, op.symbol());
        }
      }
    }
    return constants;
  }
  
  public OperationSymbol getConstantSymbol(IntArray constant) {
    getConstants();  // make sure these are made
    return constantToSymbol.get(constant);
  }
  
  public Term getConstantTerm(IntArray constant) {
    return new NonVariableTerm(getConstantSymbol(constant),
                               new ArrayList<Term>());
  }

  /**
   * If this is larger than an int, return -1.
   */
  public int cardinality() {
    if (cardinality > -2) return cardinality;
    final BigInteger max = BigInteger.valueOf((long)Integer.MAX_VALUE); 
    BigInteger v = BigInteger.ONE;
    for (int i = 0; i < sizes.length; i++) {
      v = v.multiply(BigInteger.valueOf((long)sizes[i]));
      if (v.compareTo(max) > 0) {
        cardinality = -1;
        break;
      }
    }
    if (cardinality == -1) return cardinality;
    cardinality = v.intValue();
    return cardinality;
  }

  public List<SmallAlgebra> factors() {
    return algebras;
  }

  public int[] getPowers() { return powers; }

  /**
   * Test if this is a power of a single algebra.
   */
  public boolean isPower() {
    return powers != null && powers.length == 1;
  }

  public List<SmallAlgebra> rootFactors() { return rootAlgebras; }
  
  public int getNumberOfFactors() {return numberOfFactors; }

  public SmallAlgebra projection(int k) {
    return algebras.get(k);
  }

  /**
   * Don't use this yet; it is not implemented.
   */
  public BasicPartition projectionKernel(int k) {
    final int projectionSize = sizes[k];
    final int blockSize = cardinality() / projectionSize;
    final int[] arr = new int[cardinality()];
    final int[] tmp = new int[cardinality()];
    for (int i = 0; i < projectionSize; i++) {
    }
    // finish this later.
    return null;
  }



  /**
   * The subalgebra generated by elems given as arrays of int's.
   */
/*
  public Subalgebra sg(List elems) {
    Subalgebra ans = new Subalgebra(this, new int[0]);
    return ans;
  }
*/

  /**
   * Closure of <tt>elems</tt> under the operations. (Worry about
   * nullary ops later.)
   *
   * @param elems a List of IntArray's
   *
   * @return a List of IntArray's.
   */
  public List<IntArray> sgClose(List<IntArray> elems) {
    return sgClose(elems, 0, null);
  }

  /**
   * Closure of <tt>elems</tt> under the operations. (Worry about
   * nullary ops later.)
   *
   * @param elems a List of IntArray's
   *
   * @param termMap a Map from the element to the corresponding term
   *                used to generated it. The generators should be 
   *                already in the Map. In other words the termMap
   *                should have the same number of entries as elems.
   *
   * @return a List of IntArray's.
   */
  public List<IntArray> sgClose(List<IntArray> elems, Map<IntArray, Term> termMap) {
    // TODO: add in constants and terms for constants
    List<IntArray> consts = getConstants();
    Set<IntArray> elemsHS = new HashSet<IntArray>(elems);
    for (IntArray ia : consts) {
      if (!elemsHS.contains(ia)) {
        elems.add(ia);
        if (termMap != null) termMap.put(ia, getConstantTerm(ia));
      }
    }
    return sgClose(elems, 0, termMap);
  }

  /**
   * Closure of <tt>elems</tt> under the operations. (Worry about
   * nullary ops later.)
   *
   * @param elems a List of IntArray's
   *
   * @param termMap a Map from the element to the corresponding term
   *                used to generated it. The generators should be 
   *                already in the Map. In other words the termMap
   *                should have the same number of entries as elems.
   *
   * @param elt an element to search for; if found return the closure
                found so far.
   *
   * @return a List of IntArray's.
   */
  public List<IntArray> sgClose(List<IntArray> elems, 
                                Map<IntArray, Term> termMap, IntArray elt) {
    return sgClose(elems, 0, termMap, elt, null);
  }
  
  public List<IntArray> sgClose(List<IntArray> elems, 
      Map<IntArray, Term> termMap, IntArray elt, ProgressReport report) {
    HashSet<IntArray> elemsHS = new HashSet<IntArray>(elems);
    for (IntArray ia : getConstants()) {
      if (!elemsHS.contains(ia)) {
        elems.add(ia);
        termMap.put(ia, NonVariableTerm.makeConstantTerm(constantToSymbol.get(ia)));
      }
    }
    return sgClose(elems, 0, termMap, elt, report);
  }

  /**
   * Closure of <tt>elems</tt> under the operations. (Worry about
   * nullary ops later.)
   *
   * @param elems a List of IntArray's
   *
   * @param termMap a Map from the element to the corresponding term
   *                used to generated it. The generators should be 
   *                already in the Map. In other words the termMap
   *                should have the same number of entries as elems.
   *
   * @return a List of IntArray's.
   */
  public List<IntArray> sgClose(List<IntArray> elems, int closedMark, 
                               final Map<IntArray,Term> termMap) {
    return sgClose(elems, closedMark, termMap, null, null);
  }



  /**
   * Closure of <tt>elems</tt> under the operations. (Worry about
   * nullary ops later.)
   *
   * @param elems a List of IntArray's
   *
   * @param termMap a Map from the element to the corresponding term
   *                used to generated it. The generators should be 
   *                already in the Map. In other words the termMap
   *                should have the same number of entries as elems.
   *
   * @param elt an element to search for; if found return the closure
                found so far.
   *
   * @return a List of IntArray's.
   */
  public List sgClose_old(List elems, int closedMark, final Map termMap, 
                                final  Object elt, ProgressReport report) {
    final List lst = new ArrayList(elems);
    final HashSet su = new HashSet(lst);
    int currentMark = lst.size();
    while (closedMark < currentMark) {
//if (lst.size() > 100000) return lst;
      // close the elements in current
      for (Iterator it = operations().iterator(); it.hasNext(); ) {
        Operation f = (Operation)it.next();
        final int arity = f.arity();
        if (arity == 0) continue;  // worry about constansts later
        int[] argIndeces = new int[arity];
        for (int i = 0; i < arity - 1; i++) {
          argIndeces[i] = 0;
        }
        argIndeces[arity - 1] = closedMark;
        ArrayIncrementor inc =
                    SequenceGenerator.nondecreasingSequenceIncrementor(
                                  argIndeces, currentMark - 1, closedMark);
        //final int[] arg = new int[arity];
        final List arg = new ArrayList(arity);
        for (int i = 0; i < arity; i++) {
          arg.add(null);
        }
        while (true) {
          for (int i = 0; i < arity; i++) {
            arg.set(i, lst.get(argIndeces[i]));
          }
          ArrayIncrementor permInc = PermutationGenerator.listIncrementor(arg);
          while (true) {
            Object v = f.valueAt(arg);
            if (su.add(v)) {
              lst.add(v);
              if (termMap != null) {
                List children = new ArrayList(arity);
                for (int i = 0; i < arity; i++) {
                  //children.set(i, termMap.get(arg.get(i)));
                  children.add(termMap.get(arg.get(i)));
                }
                termMap.put(v, new NonVariableTerm(f.symbol(), children));
                logger.fine("" + v + " from " + f.symbol() + " on " + arg);
              }
              if (v.equals(elt)) return lst;
            }
            if (!permInc.increment()) break;
          }
          if (!inc.increment()) break;
        }
if (false) {
/*
  List middleZero = new ArrayList();
    for (Iterator it2 = lst.iterator(); it2.hasNext(); ) {
      IntArray ia = (IntArray)it2.next();
      if (ia.get(1) == 0) middleZero.add(ia);
    }
  System.out.println("jonsson level so far: "
     + Algebras.jonssonLevelAux(middleZero, 
                             (IntArray)lst.get(0),  (IntArray)lst.get(2)));
*/
}
      }
      closedMark = currentMark;
      currentMark = lst.size();
      if (cardinality() > 0 && currentMark >= cardinality()) break;
System.out.println("so far: " + currentMark);
//if (currentMark > 7) return lst;
    }
    return lst;
  }

  /**
   * Closure of <tt>elems</tt> under the operations. (Worry about
   * nullary ops later.)
   *
   * @param elems a List of IntArray's
   *
   * @param termMap a Map from the element to the corresponding term
   *                used to generated it. The generators should be 
   *                already in the Map. In other words the termMap
   *                should have the same number of entries as elems.
   *
   * @param elt an element to search for; if found return the closure
                found so far.
   *
   * @return a List of IntArray's.
   */
  public List sgCloseXX(List elems, int closedMark, final Map termMap, 
                                                  final  Object elt) {
    final List lst = new ArrayList(elems);
    final HashSet su = new HashSet(lst);
    int currentMark = lst.size();
    while (closedMark < currentMark) {
//if (lst.size() > 100000) return lst;
      // close the elements in current
      for (Iterator it = operations().iterator(); it.hasNext(); ) {
        Operation f = (Operation)it.next();
        final int arity = f.arity();
        if (arity == 0) continue;  // worry about constansts later
        int[] argIndeces = new int[arity];
        for (int i = 0; i < arity - 1; i++) {
          argIndeces[i] = 0;
        }
        argIndeces[arity - 1] = closedMark;
        ArrayIncrementor inc =
                    SequenceGenerator.sequenceIncrementor(
                                  argIndeces, currentMark - 1, closedMark);

        //final int[] arg = new int[arity];
        final List arg = new ArrayList(arity);
        for (int i = 0; i < arity; i++) {
          arg.add(null);
        }
        while (true) {
          for (int i = 0; i < arity; i++) {
            arg.set(i, lst.get(argIndeces[i]));
          }
          Object v = f.valueAt(arg);
          if (su.add(v)) {
            lst.add(v);
            if (termMap != null) {
              List children = new ArrayList(arity);
              for (int i = 0; i < arity; i++) {
                //children.set(i, termMap.get(arg.get(i)));
                children.add(termMap.get(arg.get(i)));
              }
              termMap.put(v, new NonVariableTerm(f.symbol(), children));
              //logger.fine("" + v + " from " + f.symbol() + " on " + arg);
            }
            if (v.equals(elt)) return lst;
          }
          if (!inc.increment()) break;
        }
if (false) {
/*
  List middleZero = new ArrayList();
    for (Iterator it2 = lst.iterator(); it2.hasNext(); ) {
      IntArray ia = (IntArray)it2.next();
      if (ia.get(1) == 0) middleZero.add(ia);
    }
  System.out.println("jonsson level so far: "
     + Algebras.jonssonLevelAux(middleZero, 
                             (IntArray)lst.get(0),  (IntArray)lst.get(2)));
*/
}
      }
      closedMark = currentMark;
      currentMark = lst.size();
      if (cardinality() > 0 && currentMark >= cardinality()) break;
System.out.println("so far: " + currentMark);
//if (currentMark > 7) return lst;
    }
    return lst;
  }

  /**
   * Closure of <tt>elems</tt> under the operations. (Worry about
   * nullary ops later.)
   *
   * @param elems a List of IntArray's
   *
   * @param termMap a Map from the element to the corresponding term
   *                used to generated it. The generators should be 
   *                already in the Map. In other words the termMap
   *                should have the same number of entries as elems.
   *
   * @param elt an element to search for; if found return the closure
                found so far.
   *
   * @return a List of IntArray's.
   */
  public List<IntArray> sgClose(List<IntArray> elems, int closedMark, 
                   final Map<IntArray,Term> termMap, final  IntArray elt, ProgressReport report) {
    Closer closer = new Closer(this, elems, termMap);
    closer.setProgressReport(report);
    closer.setElementToFind(elt);
    
    //if (isPower()) {
    //  System.out.println("using Closer");
    //  return closer.sgClosePower();
    //  
    //  /*
    //  SmallAlgebra alg = rootFactors().get(0);
    //  alg.makeOperationTables();
    //  List<Operation> ops = alg.operations();
    //  if (ops.size() > 0 && ops.get(0).getTable() != null) {
    //    return sgClosePower(alg.cardinality(), ops, elems,
    //                                           closedMark, termMap, elt, report);
    //  }
    //  */
    //}
    return closer.sgClose();
    
    /*
    // TODO: here
    //if (monitoring()) monitor.printStart("subpower closing ...");
    if (report != null) report.addStartLine("subpower closing ...");
    final List<IntArray> lst = new ArrayList<IntArray>(elems);// IntArrays
    final List<int[]> rawList = new ArrayList<int[]>(); // the corr raw int[]
    for (Iterator<IntArray> it = elems.iterator(); it.hasNext(); ) {
      rawList.add(it.next().getArray());
    }
    final HashSet<IntArray> su = new HashSet<IntArray>(lst);
    int currentMark = lst.size();
    int pass = 0;
    while (closedMark < currentMark) {
      String str = "pass: " + pass + ", size: " + lst.size();
      if (report != null) {
        report.setPass(pass);
        report.setPassSize(lst.size());
        report.addLine(str);
      }
      else {
        System.out.println(str);
      }
      pass++;
      if (Thread.currentThread().isInterrupted()) return null;
//if (lst.size() > 100000) return lst;
      // close the elements in current
      for (Iterator<Operation> it = operations().iterator(); it.hasNext(); ) {
        Operation f = it.next();
        final int arity = f.arity();
        if (arity == 0) continue;  // worry about constansts later
        int[] argIndeces = new int[arity];
        for (int i = 0; i < arity - 1; i++) {
          argIndeces[i] = 0;
        }
        argIndeces[arity - 1] = closedMark;
        ArrayIncrementor inc =
                    SequenceGenerator.sequenceIncrementor(
                                  argIndeces, currentMark - 1, closedMark);

        final int[][] arg = new int[arity][];
        while (true) {
          if (Thread.currentThread().isInterrupted()) {
            if (report != null) {
              report.setSize(lst.size());
              report.addEndingLine("cancelled ... ");
            }
            return null;
          }
          for (int i = 0; i < arity; i++) {
            arg[i] = rawList.get(argIndeces[i]);
          }
          int[] vRaw = f.valueAt(arg);
          IntArray v = new IntArray(vRaw);
          if (su.add(v)) {
            lst.add(v);
            rawList.add(vRaw);
            if (termMap != null) {
              List<Term> children = new ArrayList<Term>(arity);
              for (int i = 0; i < arity; i++) {
                //children.set(i, termMap.get(arg.get(i)));
                children.add(termMap.get(lst.get(argIndeces[i])));
              }
              termMap.put(v, new NonVariableTerm(f.symbol(), children));
              //logger.fine("" + v + " from " + f.symbol() + " on " + arg);
            }
            final int size = lst.size();
            if (cardinality() > 0 && size == cardinality()) {
              if (report != null) {
                report.setSize(lst.size());
              }
              return lst;
            }
            if (v.equals(elt)) {
              if (report != null) report.addEndingLine("closing done, found " + elt + ", at " + lst.size());
              return lst;
            }
            if (Thread.currentThread().isInterrupted()) return null;
            if (report != null) report.setSize(lst.size());
            else {  // TODO: delete this
              final int s = lst.size();
              if (s % 100 == 0)
                  System.out.println("" + s + ", pass " + pass);
            }
          }
          if (!inc.increment()) break;
        }
      }
      closedMark = currentMark;
      currentMark = lst.size();
      if (cardinality() > 0 && currentMark >= cardinality()) break;
System.out.println("so far: " + currentMark);
//if (currentMark > 7) return lst;
    }
    if (report != null) report.addEndingLine("closing done, size = " + lst.size());
    //if (monitoring()) monitor.printEnd("closing done, size = " + lst.size());
    return lst;
    */
  }

  /**
   * A fast version for powers to compute the 
   * closure of <tt>elems</tt> under the operations. (Worry about
   * nullary ops later.)
   *
   * @param elems a List of IntArray's
   *
   * @param termMap a Map from the element to the corresponding term
   *                used to generated it. The generators should be 
   *                already in the Map. In other words the termMap
   *                should have the same number of entries as elems.
   *
   * @param elt an element to search for; if found return the closure
                found so far.
   *
   * @return a List of IntArray's.
   */
  private final List<IntArray> sgClosePower(final int algSize, 
      List<Operation> ops, List<IntArray> elems, int closedMark, 
      final Map<IntArray,Term> termMap, final  Object elt, ProgressReport report) {
System.out.println("using power");
System.out.println("card = " + cardinality());
//System.out.println("report = " + report);
    //if (monitoring()) monitor.printStart("subpower closing ...");
    if (report != null) {
      report.addStartLine("subpower closing ...");
    }
    final int k = ops.size();
    final int[][] opTables = new int[k][];
    final int[] arities = new int[k];
    final OperationSymbol[] symbols = new OperationSymbol[k];
    for (int i = 0; i < k; i++) {
      Operation op = ops.get(i);
      opTables[i] = op.getTable();
      arities[i] = op.arity();
      symbols[i] = op.symbol();
    }
    final int power = numberOfFactors;
    final List<IntArray> lst = new ArrayList<IntArray>(elems);// IntArrays
    final List<int[]> rawList = new ArrayList<int[]>(); // the corr raw int[]
    for (Iterator<IntArray> it = elems.iterator(); it.hasNext(); ) {
      rawList.add(it.next().getArray());
    }
    final HashSet<IntArray> su = new HashSet<IntArray>(lst);
    int currentMark = lst.size();
    int pass = 0;
    // TODO: mark
    while (closedMark < currentMark) {
      String str = "pass: " + pass + ", size: " + lst.size();
      if (report != null) {
        report.setPass(pass);
        report.setPassSize(lst.size());
        report.addLine(str);
      }
      else {
        System.out.println(str);
      }
      pass++;
      // close the elements in current
      for (int i = 0; i < k; i++) {
        final int arity = arities[i];
        if (arity == 0) continue;  // worry about constansts later
        final int[] opTable = opTables[i];
        final int[] argIndeces = new int[arity];
        for (int r = 0; r < arity - 1; r++) {
          argIndeces[r] = 0;
        }
        argIndeces[arity - 1] = closedMark;
        ArrayIncrementor inc =
                    SequenceGenerator.sequenceIncrementor(
                                  argIndeces, currentMark - 1, closedMark);
        while (true) {
          //for (int i = 0; i < arity; i++) {
          //  arg[i] = rawList.get(argIndeces[i]);
          //}
          final int[] vRaw = new int[power];


          for (int j = 0; j < power; j++) {
            int factor = algSize;
            int index = rawList.get(argIndeces[0])[j];
            for (int r = 1; r < arity; r++) {
              index += factor * rawList.get(argIndeces[r])[j];
              factor = factor * algSize;
            }
            vRaw[j] = opTable[index];
          }
          IntArray v = new IntArray(vRaw);
          if (su.add(v)) {
            lst.add(v);
            rawList.add(vRaw);
            if (termMap != null) {
              List<Term> children = new ArrayList<Term>(arity);
              for (int r = 0; r < arity; r++) {
                //children.set(i, termMap.get(arg.get(i)));
                children.add(termMap.get(lst.get(argIndeces[r])));
              }
              termMap.put(v, new NonVariableTerm(symbols[i], children));
              //logger.fine("" + v + " from " + f.symbol() + " on " + arg);
            }
            final int size = lst.size();
            if (cardinality() > 0 && size == cardinality()) {
              //if (monitoring()) {
              //  monitor.printEnd("done closing, size = " + lst.size());
              //  monitor.setSizeFieldText("" + lst.size());
              //}
              if (report != null) {
                report.setSize(lst.size());
              }
              return lst;
            }
            if (Thread.currentThread().isInterrupted()) return null;
            if (report != null) report.setSize(lst.size());
            if (v.equals(elt)) return lst;
          }
          if (!inc.increment()) break;
        }

if (false) {
/*
  List middleZero = new ArrayList();
    for (Iterator it2 = lst.iterator(); it2.hasNext(); ) {
      IntArray ia = (IntArray)it2.next();
      if (ia.get(1) == 0) middleZero.add(ia);
    }
  System.out.println("jonsson level so far: "
     + Algebras.jonssonLevelAux(middleZero, 
                             (IntArray)lst.get(0),  (IntArray)lst.get(2)));
*/
}
      }
      closedMark = currentMark;
      currentMark = lst.size();
      if (cardinality() > 0 && currentMark >= cardinality()) break;
System.out.println("so far: " + currentMark);
//if (currentMark > 7) return lst;
    }
    if (report != null) {
      report.addEndingLine("done closing, size = " + lst.size());
    }
    //if (monitoring()) {
    //  getMonitor().printEnd("done closing, size = " + lst.size());
    //  getMonitor().setSizeFieldText("" + lst.size());
    //}
    return lst;
  }


  public static void main(String[] args) throws java.io.IOException,
                                   org.uacalc.io.BadAlgebraFileException {
    if (args.length == 0) return;
    System.out.println("reading " + args[0]);
    SmallAlgebra alg = org.uacalc.io.AlgebraIO.readAlgebraFile(args[0]);
    System.out.println("The alg \n" + alg);
    BigProductAlgebra alg2 = new BigProductAlgebra("", alg, 10);
    System.out.println("The alg2 \n" + alg2);
/*
    int[] a0 = new int[] {1,2,3};

    int[] a1 = new int[] {1,1,2};
    int[] a2 = new int[] {1,2,1};
    int[] a3 = new int[] {2,1,1};

    int[] a4 = new int[] {0,1,2};
    int[] a5 = new int[] {1,0,2};
    int[] a6 = new int[] {1,2,0};

    int[] a7 = new int[] {0,0,1};
    int[] a8 = new int[] {0,1,0};
    int[] a9 = new int[] {1,0,0};
*/
    int[] g0 = {1,1,1,2,0,1,1,0,0,1};
    int[] g1 = {2,1,2,1,1,0,2,0,1,0};
    int[] g2 = {3,2,1,1,2,2,0,1,0,0};

    List lst = new ArrayList(3);
    lst.add(new IntArray(g0));
    lst.add(new IntArray(g1));
    lst.add(new IntArray(g2));
    List ans = alg2.sgClose(lst);
    System.out.println("ans size = " + ans.size());
    for (Iterator it = ans.iterator(); it.hasNext(); ) {
      System.out.println("  " + it.next());
    }
    //SubProductAlgebra alg3 = new SubProductAlgebra("test", alg2, lst);
    //System.out.println("alg3 con size = " + alg3.con().cardinality());



/*
    ArrayList lst = new ArrayList();
    lst.add(alg);
    lst.add(alg);
//    lst.add(alg);
//    lst.add(alg);
    System.out.println("prod of " + lst.size() + " algebras");
    SmallAlgebra alg2 = new BigProductAlgebra(lst);

    org.uacalc.io.AlgebraWriter writer 
         = new org.uacalc.io.AlgebraWriter((SmallAlgebra)alg2, "/tmp/goo.xml");
    writer.writeAlgebraXML();
    

    SmallAlgebra alg3 = new BigProductAlgebra(lst);
    alg2.makeOperationTables();
    TypeFinder tf = new TypeFinder(alg2);
    long t = System.currentTimeMillis();
    int k = alg2.con().joinIrreducibles().size();
    t = System.currentTimeMillis() - t;
    System.out.println("number of jis is " + k);
    System.out.println("to find the jis it took " + t);
    t = System.currentTimeMillis();
    //System.out.println("size " + alg.con().universe().size());
    HashSet types = tf.findTypeSet();
    t = System.currentTimeMillis() - t;
    System.out.println("type set = " + types);
    System.out.println("it took " + t);
    System.out.println("--- Without tables ---");
    tf = new TypeFinder(alg3);
    t = System.currentTimeMillis();
    k = alg3.con().joinIrreducibles().size();
    t = System.currentTimeMillis() - t;
    System.out.println("number of jis is " + k);
    System.out.println("to find the jis it took " + t);
    t = System.currentTimeMillis();
    //System.out.println("size " + alg.con().universe().size());
    types = tf.findTypeSet();
    t = System.currentTimeMillis() - t;
    System.out.println("type set = " + types);
    System.out.println("it took " + t);
*/

  }

}


