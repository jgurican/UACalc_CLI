package org.uacalc.alg.conlat;

import org.uacalc.alg.*;
import org.uacalc.alg.op.Operation;
import org.uacalc.alg.op.OperationSymbol;
import org.uacalc.alg.op.SimilarityType;
import org.uacalc.ui.tm.ProgressReport;
import org.uacalc.util.*;
import org.uacalc.lat.*;

import java.util.*;
import java.util.logging.*;

/* CongruenceLattice.java 2001/06/04 Ralph Freese */
/* 
 * Changes by RSF: 2001/06/18
 * I changed makePrincipals so that it so that equal principal congruences
 * are indentical. This will result in a big space saving im many cases.
 *
 * Changes by EWK: 2001/06/14
 * This class contains only the core functionality
 * of the calculation itself. No I/O, and no
 * "helper" data holders for I/O.
 * This class is meant to be subclassed.
 * ?? Isn't it faster to use the JI congruences instead of
 * the principals when generating all congruences?
 * I changed it so.
 */

/**
 * A class to represent the congruence lattice of a SmallAlgebra;
 * this is, an algebra with universe
 * the integers from 0 to n-1. This uses the very fast algorithms from
 * my unpublished paper <i>Computing Congruences Efficiently</i>
 * which is available at 
 * {@link <a href="http://www.math.hawaii.edu/~ralph/papers.html">
    http://www.math.hawaii.edu/~ralph/papers.html</a>}.
 *
 * @author Ralph Freese
 * @version $Id$
 */ 
public class CongruenceLattice implements Lattice {

  static Logger logger = Logger.getLogger("org.uacalc.alg.CongruenceLattice");
  static {
    logger.setLevel(Level.FINER);
  }
  
  public static ProgressReport monitor;// make it static and public for now
                                // change it later. TODO

  private SmallAlgebra alg;
  private int algSize;
  private int numOps;
  
  public static final int MAX_DRAWABLE_SIZE = 150;
  public static final int MAX_DRAWABLE_INPUT_SIZE = 2500;
  private boolean nonDrawable = false;

  private Partition zeroCong;
  private Partition oneCong;
  
  private String description;

  /**
   * Hold a BasicLattice view of this, if it is small enough, which
   * in turn holds the diagram.
   */
  private BasicLattice basicLat;

  /** 
   * A map from pairs [i,j] to the array representing Cg(i, j).
   */
  private HashMap<IntArray,Partition> principalCongruencesLookup = null;

  /**
   * A map from principal congruences to pairs [i,j] such that Cg(i, j).
   * is the principal congruence.
   */
  private HashMap<Partition,IntArray> principalCongruencesRep = null;

  /**
   * A map from principal congruences to a Subtrace for this principal.
   * The Subtrace has the TCT type as well.
   */
  private Map<Partition,Subtrace> joinIrredToSubtraceMap = null;


  private Set<Partition> universe = null;
  private Map<Partition,List<Partition>> upperCoversMap = null;
  private List<Partition> principalCongruences = null;
  private List<Partition> joinIrreducibles = null;
  private List<Partition> atoms = null;
  private List<Partition> meetIrreducibles = null;
  private List<Partition> coatoms = null;
  private Map<Partition,Partition> lowerCoverOfJIs = null;
  private Set<Partition> congruencesHash = null;
//  private List<Partition> meetIrredCongruences = null;
  private Set<Integer> typeSet = null;


  /**
   * The size of the universe as it is being computed for the progress
   * bar.
   */
  private int sizeComputed = 0;

  private boolean principalsMade = false;

  private TypeFinder typeFinder = null;

  public CongruenceLattice(SmallAlgebra alg) {
    this.alg = alg;
    //System.out.println("alg = " +alg);
    algSize = alg.cardinality();
    numOps = alg.operations().size();
    //System.out.println("algSize = " + algSize);
    zeroCong = BasicPartition.zero(algSize);
    oneCong = BasicPartition.one(algSize);
  }
  
  public void setMonitor(ProgressReport m) { monitor = m; }
  public ProgressReport getMonitor() { return monitor; }
  
  public final boolean monitoring() {
    return monitor != null;
  }
  
  public boolean isTotal() { return true; }

  public SmallAlgebra getAlgebra() { return alg; }

  public boolean isUnary() { return false; }

  public String getDescription() {
    if (description != null) return description;
    return "Congruence Lattice of " + alg;
  }
  
  public void setDescription(String desc) {
    this.description = desc;
  }
  
  public boolean isSmallerThan(int size) {
    if (universe != null) return cardinality() < size;
    if (joinIrreducibles().size() >= size) return false;
    makeUniverse(size);
    if (universe == null) return false;
    return true;
  }
  
  public boolean isDrawable() {
    if (universe != null) return cardinality() <= MAX_DRAWABLE_SIZE;
    if (sizeComputed > 0) return false;
    return isSmallerThan(MAX_DRAWABLE_SIZE + 1);
  }
  
  public BasicLattice getBasicLattice() {
    return getBasicLattice(true);
  }
  
  /**
   * Get the BasicLattice used primarily for drawing.
   * 
   * @return a BasicLattice view
   */
  public BasicLattice getBasicLattice(boolean makeIfNull) {
    if (basicLat == null  && makeIfNull) basicLat = new BasicLattice("", this, true); // maybe a name
    //if (basicLat != null) System.out.println("op size = " + basicLat.operations().size());
    return basicLat;
  }
  
  public org.latdraw.diagram.Diagram getDiagram() {
    if (!isDrawable()) return null;
    return getBasicLattice().getDiagram();
  }
  
  public List<Partition> principals() {
    return principals(null);
  }

  public List<Partition> principals(ProgressReport report) {
    if (!principalsMade) {
      makePrincipals(report);
      principalsMade = true;
    }
    return principalCongruences;
  }
  
  public int cardinality() {
    return universe().size();
  }
  
  public int inputSize() {
    final int card = cardinality();
    if (card < 0) return -1;
    return similarityType().inputSize(card);
  }
  
  public Set<Partition> universe() {
    return universe(null);
  }
  

  public Set<Partition> universe(ProgressReport report) {
    if (universe == null) makeUniverse(report);
    return universe;
  }

  public boolean isSimilarTo(Algebra alg) {
    return alg.similarityType().equals(similarityType());
  }
                                                                                
  public SimilarityType similarityType() {
    return SimilarityType.LATTICE_SIMILARITY_TYPE;
  }

  public Iterator iterator() { return universe().iterator(); }

  public String getName() {
    return "Con(" + getAlgebra().getName() + ")";
  }
  
  public void setName(String v) {
    throw new UnsupportedOperationException();
  }
  
  public boolean joinIrreduciblesFound() {
    return joinIrreducibles != null;
  }

  public List<Partition> joinIrreducibles() {
    return joinIrreducibles(null);
  }
  /**
   * A list of the join irreducibles; constructed if necessary.
   */
  public List<Partition> joinIrreducibles(ProgressReport report) {
    if (joinIrreducibles == null) makeJoinIrreducibles(report);
    return joinIrreducibles;
  }

  public boolean joinIrreducible(Partition part) {
    // make sure the join irreduciles have been make
    joinIrreducibles();
    return lowerCoverOfJIs.get(part) != null;
  }

  public List<Partition> meetIrreducibles() {
    if (meetIrreducibles == null) makeMeetIrreducibles();
    return meetIrreducibles;
  }
  
  public List<Partition> atoms() {
    if (atoms == null) makeAtoms();
    return atoms;
  }
  
  
  public void makeAtoms() {
    atoms = new ArrayList<Partition>();
    for (Partition ji : joinIrreducibles()) {
      boolean isAtom = true;
      for (Partition par : atoms) {
        if (par.leq(ji)) {
          isAtom = false;
          break;
        }
      }
      if (isAtom) atoms.add(ji);
    }
  }
  
  // TODO either do something or may throw UnsupportedOperationException
  public List<Partition> coatoms() {
    meetIrreducibles();
    return coatoms;
  }

  public Object join(Object a, Object b) { 
    return ((Partition)a).join((Partition)b);
  }

  // make this generic
  public Object join(List args) {
    Partition join = zero();
    for (Iterator it = args.iterator(); it.hasNext(); ) {
      join = join.join((Partition)it.next());
    }
    return join;
  }

  public Object meet(Object a, Object b) {
    return ((Partition)a).meet((Partition)b);
  }

  public Object meet(List args) {
    Partition meet = one();
    for (Iterator it = args.iterator(); it.hasNext(); ) {
      meet = meet.meet((Partition)it.next());
    }
    return meet;
  }


  public boolean leq(Object a, Object b) {
    return ((Partition)a).leq((Partition)b);
  }

  public List constantOperations() { return SimpleList.EMPTY_LIST; }

  // TODO fix this
  public List operations() { return null; }

  // TODO fix this
  public Operation getOperation(OperationSymbol sym) { return null; }
  
  //TODO fix this
  public Map<OperationSymbol,Operation> getOperationsMap() { return null; }

  // we will try to convert this to a SmallLattice and find the 
  // congruence of that.
  //public CongruenceLattice con() { return null; }

  public void makePrincipals(ProgressReport report) {
    if (report != null) report.addStartLine("finding principal congruences of " 
                                                        + getAlgebra().getName());
    HashMap<Partition,Partition> pcIdMap = new HashMap<Partition,Partition>();  // to keep equal congruences identical
    principalCongruences = new ArrayList<Partition>();
    //congruencesHash = new HashSet();
    principalCongruencesLookup = new HashMap<IntArray,Partition>();
    principalCongruencesRep = new HashMap<Partition,IntArray>();
    for (int i = 0; i < algSize - 1; i++) {
      for (int j = i + 1; j < algSize; j++) {
        if (Thread.currentThread().isInterrupted()) {
          if (report != null) report.addEndingLine("cancelled ...");
          return;
        }
        Partition partCong = makeCg(i, j);
        if (pcIdMap.get(partCong) == null) {
          pcIdMap.put(partCong, partCong);
          principalCongruences.add(partCong);
          principalCongruencesRep.put(partCong, new IntArray(new int[] {i, j}));
          if (report != null) report.setSize(principalCongruences.size());
        }
        else {
          partCong = pcIdMap.get(partCong);
        }
        principalCongruencesLookup.put(new IntArray(new int[] {i, j}), partCong);
      }
    }
    sortByRank(principalCongruences);
    if (report != null) report.addEndingLine("principal congruences of " 
               + getAlgebra().getName() + ": size = " + principalCongruences.size());
  }

  public boolean universeFound() { return universe != null; }

  private boolean stopMakeUniverse = false;
  public void stopMakeUniverse() { stopMakeUniverse = true; }

  private int makeUniverseK;
  public int getMakeUniverseK() { return makeUniverseK; }
  
  public int getSizeComputed() { return sizeComputed; }

  /*
  public Task getUniverseTask() {
    // if the universe has already been found, there is no task.
    if (universeFound()) return null;
    return new Task() {
        public void stop() { stopMakeUniverse(); }
        public boolean done() { return universeFound(); }
        public int percentDone() { 
          if (joinIrreducibles == null || joinIrreducibles.size() == 0) {
            return 0;
          }
          return 100 * getMakeUniverseK() / joinIrreducibles.size();
        }
        public int amountComputed() { return getSizeComputed(); }
        public int leftToDo() {
          return joinIrreducibles.size() - getMakeUniverseK();
        }
        public void go() {
          final SwingWorker worker = new SwingWorker() {
            public Object construct() {
              makeUniverse();
              return null;
            }
          };
          worker.start();
        }
      };
  }
*/
  
  public void makeUniverse(ProgressReport report) {
    makeUniverse(-1, report);
  }
  
  public void makeUniverse(int size) {
    makeUniverse(size, null);
  }
  
  public void makeUniverse() {
    makeUniverse(-1, null);
  }
  
  /**
   * Construct the universe. If this method is interupted, the whole
   * calculation starts over. We might change that if there is enough
   * demand.
   */
  public void makeUniverse(int maxSize, ProgressReport report) {
    final boolean stopIfBig = maxSize > 0 ? true : false;
    
    if (report != null) report.addStartLine("finding the universe of Con(" 
                                                     + getAlgebra().getName() + ")");
    List<Partition> univ = new ArrayList<Partition>(joinIrreducibles(report));
    HashSet<Partition> hash = new HashSet<Partition>(joinIrreducibles());
    sizeComputed = univ.size();
    makeUniverseK = 0;
    stopMakeUniverse = false;
    Iterator<Partition> it = joinIrreducibles().iterator();
    final int size = joinIrreducibles().size();
    int k = 0;
    while (it.hasNext()) {
      k++;
      System.out.println("k = " + k);
      if (Thread.currentThread().isInterrupted()) {
        if (report != null) {
          report.addEndingLine("Cancelled (" + univ.size() + " elements so far)");
          return;
        }
      }
      else {
        if (report != null) {
          report.addLine("pass " + k + " of " + size + ", size: " + univ.size());
          report.setPass(k);
          report.setSize(univ.size());
        }
      }
      makeUniverseK++;
//System.out.println("makeUniverseK = " + makeUniverseK);
//System.out.println("sizeComputed = " + sizeComputed);
      Partition elem = it.next();
      int n = univ.size();
      for (int i = makeUniverseK; i < n; i++) {
        Partition join = elem.join((Partition)univ.get(i));
        if (stopMakeUniverse) return;
        //EWK
	//if (Progress.StopProgram ) {
	//    throw new ComputationStoppedException();
	//}
        if (!hash.contains(join)) {
          //TEMPORARY!! Make normal progress report later.
	  //int s = univ.size();
	  //if ( s % 1000 == 0) {
	  //  Progress.ProgressLogAppend(
	  //    "\n [ Number of congruences is already " + s);
	  //}
          int s = univ.size();
          sizeComputed++;
          if (stopIfBig && s >= maxSize) return;
          if ( s % 10000 == 0) {
            System.out.println("size is " + s);
            //if (monitor != null) monitor.printlnToLog("size is " + s);
          }
          hash.add(join);
          univ.add(join);
        }
      }
    }
    hash.add(zeroCong);
    univ.add(0, zeroCong);
    if (report != null) report.setSize(univ.size());
    universe = new LinkedHashSet<Partition>(univ);
    congruencesHash = hash;
    if (report != null) report.addEndingLine("|Con(" + getAlgebra().getName() + ")| = " + univ.size());
  }

  /**
   * Test if beta is join prime.
   */
  public boolean joinPrime(Partition beta) {
    Partition join = zero();
    for (Iterator it = joinIrreducibles().iterator(); it.hasNext(); ) {
      Partition part = (Partition)it.next();
      if (!beta.leq(part)) {
        join = join.join(part);
        if (beta.leq(join)) return false;
      }
    }
    return true;
  }


  public boolean isDistributive() {
    for (Iterator it = joinIrreducibles().iterator(); it.hasNext(); ) {
      if (!joinPrime((Partition)it.next())) return false;
    }
    return true;
  }
      


// rsf: I'm putting back the stuff to calculate the lower covers of ji's
//      It's almost free. And I need to have them without calculating
//      the conlat.
// since principalCongruences is sorted by rank, this will be too.
  public void makeJoinIrreducibles(ProgressReport report) {
    if (report != null) report.addStartLine("finding join irreducible congruences of " + getAlgebra().getName());
    joinIrreducibles = new ArrayList<Partition>();
    lowerCoverOfJIs = new HashMap<Partition,Partition>();
    for (Partition part : principals(report)) {
      Partition join = zero();
      for (Partition part2 : principals(report)) {
        if (part2.leq(part) && (!part.equals(part2))) {
          join = join.join(part2);
        }
        if (part.equals(join)) break;
      }
      if (!part.equals(join)) {
        joinIrreducibles.add(part);
        lowerCoverOfJIs.put(part, join);
        if (report != null) report.setSize(joinIrreducibles.size());
      }
      if (Thread.currentThread().isInterrupted()) {
        if (report != null) report.addEndingLine("cancelled ...");
        return;
      }
    }
    if (report != null) report.printEnd("join irreducible congruences of " 
        + getAlgebra().getName() + ": size = " + joinIrreducibles.size());
  }
  
  /**
   * Sort by rank. The rank in size() - numberOfBlocks().
   * 
   * @param lst
   */
  public void sortByRank(final List<Partition> lst) {
    Comparator<Partition> c = new Comparator<Partition>() {
      public int compare(Partition p1, Partition p2) {
        return p2.numberOfBlocks() - p1.numberOfBlocks();
      }
    };
    Collections.sort(lst, c);
  }

  /**
   * If <code>beta</code> is join irreducible, this gives its lower
   * cover; otherwise null.
   */
  public Partition lowerStar(Partition beta) {
    //joinIrreducibles();
    if (joinIrreducibles != null) {
      return lowerCoverOfJIs.get(beta);
    }
    if (beta.equals(zero())) return null;
    Partition alpha = zero();
    final int[][] blocks = beta.getBlocks();
    for (int i = 0; i < blocks.length; i++) {
      final int[] block = blocks[i];
      for (int j = 0; j < block.length; j++) {
        for (int k = j + 1; k < block.length; k++) {
          Partition par = getAlgebra().con().Cg(block[j], block[k]);
          if (!beta.equals(par)) alpha = alpha.join(par);
          if (beta.equals(alpha)) return null;
        }
      }
    }
    return alpha;
  }

  public Map<Partition,List<Partition>> upperCoversMap() {
    if (upperCoversMap == null) makeUpperCovers();
    return upperCoversMap;
  }

  /**
   * Makes the upperCoversMap. Assumes joinIrreducibles have been
   * made.
   */
  private void makeUpperCovers() {
    Map<Partition,List<Partition>> ucMap
          = new HashMap<Partition,List<Partition>>();
    for (Iterator<Partition> it = universe().iterator(); it.hasNext(); ) {
      Partition elem = it.next();
      Set<Partition> hs = new HashSet<Partition>();
      List<Partition> covs = new ArrayList<Partition>();
      for (Iterator<Partition> it2 = joinIrreducibles().iterator(); it2.hasNext(); ) {
        Partition ji = it2.next();
        if (!ji.leq(elem)) {
          Partition join = ji.join(elem);
          if (!hs.contains(join)) {
            hs.add(join);
            boolean above = false;
            for (ListIterator<Partition> it3 = covs.listIterator(); it3.hasNext(); ) {
              Partition cov = it3.next();
              if (cov.leq(join)) {
                above = true;
                break;
              }
              if (join.leq(cov)) it3.remove();
            }
            if (!above) covs.add(join);
          }
        }
      }
      hs = null;
      ucMap.put(elem, covs);
    }
    upperCoversMap = ucMap;
  }

  public Partition Cg(Object a, Object b) {
    return Cg(getAlgebra().elementIndex(a), getAlgebra().elementIndex(b));
  }

  public Partition Cg(int a, int b) {
    if (a == b) return (Partition)zero();
    if (a > b) {
      int c = a;
      a = b;
      b = c;
    }
    if (principalCongruencesLookup != null) {
      Partition p 
         =  principalCongruencesLookup.get(new int[] {a, b});
      if (p != null) return p;
      return makeCg(a, b);
    }
    return makeCg(a, b);
  }

  /**
   * This assumes a < b.
   */
  private Partition makeCg(int a, int b) {
    int[] part = new int[algSize];
    for (int i = 0; i < algSize; i++ ) {
      part[i] = -1;
    }
    part[a] = -2;
    part[b] = a;
    SimpleList pairs = SimpleList.EMPTY_LIST;
    pairs = pairs.cons(new int[] {a, b});
    while (!pairs.isEmpty()) {
      //int[] pair = (int[])pairs.first();
      int x = ((int[])pairs.first())[0];
      int y = ((int[])pairs.first())[1];
      pairs = pairs.rest();
      for (Iterator it = alg.operations().iterator(); it.hasNext(); ) {
        Operation f = (Operation)it.next();
        int arity = f.arity();
      	int[] arg = new int[arity];
	      int[] arg2 = arg;
        for (int index = 0; index < arity; index++) {
          arg = arg2;
          for (int k = 0; k < arity; k++ ) {
            arg[k] = 0;
          }
          while (arg != null) {
            arg[index] = x;
            int r = BasicPartition.root(f.intValueAt(arg), part);
            arg[index] = y;
            int s = BasicPartition.root(f.intValueAt(arg), part);
            if (r != s) {
              BasicPartition.joinBlocks(r, s, part);
              pairs = pairs.cons(new int[] {r, s});
            }
            arg = incrementArg(arg, index);
          }
        }
      }
    }
    return new BasicPartition(part);
  }
  
  public Set typeSet() {
    return typeSet(null);
  }

  public Set typeSet(ProgressReport report) {
    if (typeSet == null) makeTypeSet(report);
    return typeSet;
  }
  
  private void makeTypeSet() {
    makeTypeSet(null);
  }

  private void makeTypeSet(ProgressReport report) {
    if (report != null) report.addStartLine("computing TCT types ...");
    typeSet = new HashSet<Integer>();
    for (Partition part : joinIrreducibles(report)) {
      typeSet.add(new Integer(typeJI(part, report)));
    }
    if (report != null) report.addEndingLine("TCT types = " + typeSet);
  }

  public int type(Partition beta) {
    return typeJI(beta, null);
  }

  /**
   * Find the type of beta over its lower cover. Beta is assumed
   * to be join irreducible.
   */
  public int typeJI(Partition beta, ProgressReport report) {
    Subtrace st = getJoinIrredToSubtraceMap().get(beta);
    if (st == null) {
      // we may only need one report line.
      if (report != null) report.addStartLine("finding a subtrace of " + beta);
      st = subtrace(beta);
      //if (st.type() <= 0) getTypeFinder().findType(st);
      if (report != null) report.addEndingLine("subtrace " + beta + " is " + st.toString(true));
    }
    if (st.type() <= 0) {
      if (report != null) report.addStartLine("computing TCT type of " 
          + beta + ", subtrace: " + st.toString(true));
      getTypeFinder().findType(st);
      if (report != null) report.addEndingLine("TCT type is " + st.type());
    }
    return st.type();
  }
  
  public int type(Partition beta, Partition alpha) {
    return type(beta, alpha, null);
  }

  /**
   * Find the type for beta over alpha. Beta is assumed
   * to cover alpha.
   */
  public int type(Partition beta, Partition alpha, ProgressReport report) {
    final Partition gamma = findJoinIrred(alpha, beta);
    return typeJI(gamma, report);
  }

  /**
   * Find a subtrace for beta over its lower cover. Beta is assumed
   * to be join irreducible.
   */
  public Subtrace subtrace(Partition beta) {
    final Map<Partition,Subtrace> smap = getJoinIrredToSubtraceMap();
    if (smap.get(beta) == null) {
      if (monitoring()) monitor.printStart("finding subtrace of " + beta);
      smap.put(beta, getTypeFinder().findSubtrace(beta));
      if (monitoring()) monitor.printEnd("subtrace = " 
          + ((Subtrace)smap.get(beta)).toString(true));
    }
    return (Subtrace)smap.get(beta);
  }
  
  /**
   * Find a subtrace for beta over alpha. Beta is assumed
   * to cover alpha.
   */
  public Subtrace subtrace(Partition beta, Partition alpha) {
    final Partition gamma = findJoinIrred(beta, alpha);
    return subtrace(gamma);
  }

  public Map<Partition,Subtrace> getJoinIrredToSubtraceMap() {
    if (joinIrredToSubtraceMap == null) 
      joinIrredToSubtraceMap = new HashMap<Partition,Subtrace>();
    return joinIrredToSubtraceMap;
  }

  public TypeFinder getTypeFinder() {
    if (typeFinder == null) typeFinder = new TypeFinder(getAlgebra());
    return typeFinder;
  }

  /**
   * Increment the constants in arg which are in all positions
   * except <code>index</code>.
   */
  private int[] incrementArg(int[] arg, int index) {
    int length = arg.length;
    int max = algSize - 1;
    if (length < 2) return null;
    for (int i = 0; i < length; i++) {
      if (i == index) continue;
      if (arg[i] < max) {
        arg[i]++;
        return arg;
      }
      arg[i] = 0;
    }
    return null;
  }

  /**
   * Find a pair [a, b] (as an IntArray) which generates part.
   * Gives null if part is not principal.
   */
  public IntArray generatingPair(Partition part) {
    principals();
    return principalCongruencesRep.get(part);
  }


  /**
   * This finds a meet irreducible congruence which is maximal with
   * respect to being above <code>a</code> and not above <code>b</code>.
   * Note if <code>a</code> is covered by <code>b</code> then
   * <code>[a,b]</code> transposes up to <code>[m,m*]</code>.
   */
  public Partition findMeetIrred (Partition a, Partition b) {
    if (b.leq(a)) return null;
    Iterator<Partition> it = joinIrreducibles().iterator();
    while (it.hasNext()) {
      Partition j = it.next().join(a);
      if (!b.leq(j)) a = j;
    }
    return a;
  }

/* old version, could be expensive.
  public Partition findMeetIrred (Partition a, Partition b) {
    if (b.leq(a)) return null;
    Iterator it = meetIrredCongruences.iterator();
    while (it.hasNext()) {
      Partition m = (Partition)it.next();
      if (a.leq(m) && !b.leq(m)) a = m;
    }
    return a;
  }
*/

  /**
   * This finds a join irreducible congruence which is minimal with
   * respect to being below <code>b</code> and not below <code>a</code>.
   * Note if <code>a</code> is covered by <code>b</code> then
   * <code>[a,b]</code> transposes down to <code>[j,j_*]</code>.
   */
  public Partition findJoinIrred (Partition a, Partition b) {
    if (b.leq(a)) return null;
    Iterator<Partition> it = joinIrreducibles().iterator();
    while (it.hasNext()) {
      Partition ji = it.next();
      if (ji.leq(b) && !ji.leq(a)) b = ji;
    }
    return b;
  }

  private void makeMeetIrreducibles() {
    meetIrreducibles = new ArrayList<Partition>();
    coatoms = new ArrayList<Partition>();
    Iterator<Partition> it = universe().iterator();
    while (it.hasNext()) {
      Partition elem = it.next();
      List<Partition> ucs = upperCoversMap().get(elem);
      if (ucs.size() == 1) {
        meetIrreducibles.add(elem);
        if (ucs.get(0).equals(one())) coatoms.add(elem);
      }
    }
  }

  public final Partition zero() { return zeroCong; }
  public final Partition one() { return oneCong; }
  
  /**
   * Find a set of meet irreducible irredundantly meeting
   * to zero.
   * 
   * @return
   */
  public List<Partition> irredundantMeetDecomposition() {
    final List<Partition> decomp = new ArrayList<Partition>();
    if (getAlgebra().cardinality() == 1) return decomp;
    Partition theta = oneCong;
    for (Partition atom : atoms()) {
      Partition mi = findMeetIrred(zeroCong, atom);
      if (!theta.leq(mi)) {
        theta = theta.meet(mi);
        decomp.add(mi);
        if (theta.equals(zeroCong)) break;
      }
    }
    return makeIrredundantMeet(decomp);
  }
      
  public List<Partition> irredundantMeetDecompositionOld() {
    final List<Partition> decomp = new ArrayList<Partition>();
    Partition meet = oneCong;
    while (!meet.equals(zeroCong)) {   
      final int[][] blocks = meet.getBlocks();
      final int k = blocks.length;
      int a = -1;
      int b = -1;
      for (int i = 0; i < k; i++) {
        if (blocks[i].length > 1) {
          a = blocks[i][0];
          b = blocks[i][1];
          break;
        }
      }
      Partition mi = zeroCong;
      for (Partition ji : joinIrreducibles()) {
        if (!ji.isRelated(a, b)) {
          final Partition jiPlus = mi.join(ji);
          if (!jiPlus.isRelated(a, b)) mi = jiPlus;
        }
      }
      if (!meet.leq(mi)) {
        meet = meet.meet(mi);
        decomp.add(mi);
      }
    }
    return makeIrredundantMeet(decomp);
  }

  public List<Partition> makeIrredundantMeet(List<Partition> list) {
    SimpleList lst = new SimpleList(list);
    final Partition bot = (Partition)meet(lst);
    List<Partition> ans = new ArrayList<Partition>();
    Partition ansMeet = oneCong;
    while (!lst.isEmpty()) {
      Partition a = (Partition)lst.first();
      lst = lst.rest();
      Partition b = (Partition)meet(ansMeet, meet(lst));
      if (!b.equals(bot)){
        ans.add(a);
        ansMeet = (Partition)meet(ansMeet, a);
      }
    }
    return ans;
  }
    
  
  /*
   * Find a subtrace for Cg(a, b), which is assumed to be join
   * irreducible.
   */
  //Subtrace findSubtrace(int a, int b) {
  //  return findSubtrace(a, b, zero());
  //}

  /*
   * Find a subtrace for Cg(a, b), which is assumed to be join
   * irreducible.
   *
   *
   * @param alpha  a congruence not above Cg(a, b).
   */
  /*
  Subtrace findSubtrace(int a, int b, Partition alpha) {
    Partition part = Cg(a,b);
    if (a == b) throw new RuntimeException("a and b cannot be equal");
    Partition partStar = (Partition)lowerCoverOfJIs.get(part);
    if (partStar == null) {
      throw new RuntimeException("Cg(" + a + ", " + b + ") is not ji");
    }
    alpha = alpha.join(partStar);
    a = alpha.representative(a);
    b = alpha.representative(b);
    if (b < a) {
      int c = a;
      a = b;
      b = c;
    }
    Set<IntArray> visited = new HashSet<IntArray>();
    int[] pair = new int[] {a, b};
    IntArray pairIA = new IntArray(pair);
    visited.add(pairIA);
    SimpleList universe = SimpleList.EMPTY_LIST;
    HashSet<IntArray> genHashSet = new HashSet<IntArray>();
    int[] roots = alpha.representatives();
    final int rootsSize = roots.length;
    for (int i = 0; i < rootsSize; i++) {
      int[] tmp = new int[] {roots[i], roots[i]};
      universe = universe.cons(tmp);
      genHashSet.add(new IntArray(tmp));
    }
    universe = universe.cons(pair);
    genHashSet.add(pairIA);
    int newCount = 0;
    int lastNewCount = rootsSize + 1;
    SimpleList old = universe;
    SimpleList lastNewAndOld = universe;

    return null;
  }
*/
  public void makeOperationTables() {}

  public boolean isIdempotent() { return true; }


}
