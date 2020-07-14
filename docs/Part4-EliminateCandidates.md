# Scala Sudoku - Eliminate Candidates 

## Introduction
This post continues my case study of Scala and Functional Programming's suitability for multi-developer projects processing complex data structures. This covers core candidate eliminations algorithms necessary to efficiently solve Sudoku puzzles. I continue analyzing the development complexity of this Sudoku Puzzle Solver. As with my prior articles, I analyze three dimensions of software development by answering these questions:
1. How easy is it to initially develop algorithms over complex data structures?
2. How easily can the program design and source code be understood by other developers?
3. Is the design extensible and code maintainable over time?

For each of the above questions, I rate the development complexity of a component's design and source code. These are the ratings:  
**Trivial, Simple, Medium, Complicated, Hard**  
This will rate the complexity of the Sudoku candidate elimination logic as built using FP constructs with Scala. Two versions of the solver (written six years apart) give me some perspective on the understanding and extensibility of the original design and are input to rating questions #2 and #3.  
Most people are familiar with Sudoku puzzles and the algorithms for solving them. Puzzle cells have either known or unknown values at the start. Row, column, and block invariants determine the set of initial possible values for the unknown cells. Further application of these same puzzle constraints finds violations between cells' candidates so these values are eliminated. Searching for and removing these invariant violations is candidate elimination functionality. There are a different categories of eliminations and algorithms for each. The following will analyze the development complexity of these different algorithms.

## Candidate Elimination Algorithms
Of these different elimination tactics, many of them are "row-centric" such that a given row contains all of the input state necessary to scrub impossible candidates from the rest of the row/column/block. These row-centric eliminations share a common routine which is parameterized by a first-class function encapsulating the elimination logic. Other eliminations are more global to the candidate structure (e.g., block constraints). This analyzes these different classes of eliminations as well as validity checking and solution search.

### Scrub Candidates
First I look at the general "row scrub" routine of the initial list-based version of the solver. Any algorithm which enforces row-local invariants shares this function by passing its specific elimination algorithm. Looking at the parameter list, this takes the candidate structure `puzzleCandsIn` and a first class function `scrubFn` defining the elimination algorithm.
```
// Scrub rows, columns & blocks of candidates with input function.
def scrubCandidates(puzzleCandsIn : List[List[List[Int]]], 
  scrubFn : List[List[Int]] => List[List[Int]]) : List[List[List[Int]]] = {
  var puzzleCands = puzzleCandsIn
  puzzleCands = puzzleCands.map(scrubFn)
  puzzleCands = puzzleCands.transpose.map(scrubFn).transpose
  transBlocks(transBlocks(puzzleCands).map(scrubFn))
}
```
For readability, this uses a mutable variable `puzzleCands` rather than the equivalent option to chain the functions together. As discussed in my prior blog posts `puzzleCands` is of type `List[List[List[Int]]]` which is a list of rows, each holding a list of cells, each holding a list of candidate values. The first line applies scrubFn to eliminate candidates within each row. The next line transposes the structure to column-major order and scrubs the each row's candidates. The next line transposes the structure to (the Sudoku specific) block-major order and scrubs blocks' candidates. The returned result is the modified candidate structure following elimination function application.  
This is straightforward functional code. It rates as **simple initial development complexity** due to the availability of transpositions. This design and code also rates **simple to understand**. As discussed in my prior posts, it does require understanding that the candidate structure is transposed so row-local algorithms may also be applied to columns and blocks. `transpose` is a library method of `List` and `blockTranspose` is specific to Sudoku puzzles and its complexity is rated elsewhere.  
The "row scrub" code in the new, array version is very simple, just three iterations across the array access indices. The need for and construction of these are detailed in my [array access indices post](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part2-ArrayAccessIndices.md).
```
// Scrub rows, columns & blocks of candidates with input function.
def scrubCandidates(scrubFn : (List[(Int,Int)]) => Unit) : Unit = {
  ixRowMajor.foreach(xs => scrubFn(xs))
  ixColMajor.foreach(xs => scrubFn(xs))
  ixBlockMajor.foreach(xs => scrubFn(xs))
}

scrubCandidates(scrubSingles(currCands, _))
// etc...
```
As with the original list version, `scrubCandidates` is called with different `scrubFn` defining the different elimination algorithms. The example call shows `scrubSingles` partially evaluated to close over the candidate array and define a function taking a list of tuples (i.e., an array index) as its single argument. `scrubFn` is applied to each of the lists of tuples in `ixRowMajor` (indexing the row cells) to eliminate candidates within each row. `scrubFn` is reused by also applying it to the column and block indices; this eliminates candidates in each column and block, respectively. Different candidate elimination algorithms need only create a first-class function with this signature and `scrubCandidates` does the work. Note that `scrubFn` is not a pure function as it directly changes the candidate array's cells. Loosening the FP paradigm is in keeping with the goal of this enhanced version: allow mutability in an attempt to improve performance.  
This FP design for enhancing the array-based solver was **medium extensibility complexity**. While the code itself is straightforward, the need for array indices required a bit of redesign. This caused the function interfaces to change between versions and this ripples into changes in the algorithm functions. We will see these effects below.

### Eliminate Singles and Uniques
Folks familiar with solving Sudoku puzzles by hand will recognize these "work horse" elimination types. A *single* is the value of a cell with candidate list size = 1: these cells are known. All other cells in the same row, column, and block must not contain this same value. A *unique* is defined as a value that exists in candidate list size > 1 and only once in a row, column, or block. `scrubSingles` encapsulates the algorithm to find and remove values based on these invariants:
```
// Remove single & unique candidates from a row.
def scrubSingles(rowCands : List[List[Int]]) : List[List[Int]] = {
  // Fold function taking & returning tuple containing (multiple cands, all cands).
  def findMultiples(t : (List[Int], List[Int]), xs : List[Int]) : (List[Int], List[Int]) =
    (xs.filter(x => t._2.contains(x)) ::: t._1, xs ::: t._2)

  // Singles are candidate lists size 1.
  val singles = rowCands.filter(xs => xs.length == 1).flatten
  // Folds across list, finding candidates listed multiple times.
  val mulTuple = rowCands.foldLeft(List[Int](), List[Int]())(findMultiples)
  // Uniques are all candidates diff multiples diff singles.
  val uniques = mulTuple._2.distinct.diff(mulTuple._1).diff(singles)
  // Remove singles & uniques from candidates.
  rowCands.map(xs => if (xs.length == 1) xs else xs.diff(singles))
    .map(xs => if (xs.intersect(uniques) != Nil) xs.intersect(uniques) else xs)
}
```
As stated above, this is reused such that a `rowCands` could actually be a transposed column or block's candidate lists. `singles` are found with a filter. `uniques` are found by taking all candidate values in row and subtracting those existing multiple times in row and also subtracting singles because they are not part of the definition. `mulTuple` is the intermediate holding all candidates and candidates existing in more than one candidate list: it is built by reducing the row with function `findMultiples`. Now that lists of singles and uniques are known, the final statement removes respective constraint violations from `rowCands` and returns result.  
This algorithm is more complicated than the common mechanism of handling singles and uniques in paper and pencil Sudoku puzzles. It rates as **medium initial development complexity**. Revisiting and understanding it many years later relied heavily on the comments as well as a detailed walk through the code. It contains a dozen list functions of a few different types. For a developer with Sudoku domain knowledge, I rate this as **medium follow-on understanding complexity**.  
Extending this algorithm to use the array-based data structure required an interface change due to the introduction of array indices. As shown in building `scrubCandidates` above, the first-class algorithm functions are closures taking an index row (i.e., a `List[(Int,Int)]` of `(y,x)` tuples). The logic within these functions is very similar to the original list version. The following is the array version of the elimination algorithm for singles and uniques:
```
// Remove single & unique candidates from input "row." Row's cells indexed
// using ixs (list of cell coords) into cands vector.
def scrubSingles(cands : Array[Array[List[Int]]], ixs : List[(Int,Int)]) : Unit = {
  // Fold function taking & returning tuple containing (multiple cands, all cands).
  def findMultiples(t : (List[Int],List[Int]), cell : (Int,Int)) : (List[Int], List[Int]) =
    cell match {case (y,x) => (cands(y)(x).filter(v => t._2.contains(v)) ::: t._1, cands(y)(x) ::: t._2)}

  // Build list of known values (singles with candidate lists size 1).
  val singles = ixs.foldLeft(List[Int]())((xs, cell) => cell match {case (y,x) =>
    if (cands(y)(x).length == 1) cands(y)(x).head :: xs else xs})
  // Find row candidates existing multiple times and all row candidates in tuple (mult cands, all cands).
  val mulTuple = ixs.foldLeft(List[Int](),List[Int]())(findMultiples)
  // Uniques are all candidates diff multiples diff singles.
  val uniques = mulTuple._2.distinct.diff(mulTuple._1).diff(singles)

  // Remove singles from other cell candidate lists.
  ixs.foreach((cell : (Int,Int)) => cell match {case (y,x) =>
    cands(y)(x) = if (cands(y)(x).length == 1) cands(y)(x) else cands(y)(x).diff(singles)
  })
  // Remove uniques from other cell candidate lists.
  ixs.foreach((cell : (Int,Int)) => cell match {case (y,x) =>
    cands(y)(x) = if (cands(y)(x).intersect(uniques) != Nil) cands(y)(x).intersect(uniques) else cands(y)(x)
  })
}
```
There is clearly a one-to-one mapping of logic steps between versions. The difference is that, where the list version iterates directly through the row's candidate lists, the array version iterates (up one level of indirection) through an index of cell coordinates to access and mutate the array cell contents.  
Once `scrubCandidates` and the array access indices are in place, extending the design and code for this algorithm was straightforward. This rates as **simple extension complexity**.

### Eliminate Block Constraints
This type of elimination is also familiar to folks who solve Sudoku puzzles by hand. It is easy to understand but considerably more difficult to codify as an FP algorithm. For each block, any candidate value that exists only in a single row or column of that block may not be a candidate outside of the block **and** in that same row or column. Here is the Scala elimination algorithm:
```
// Scrub candidates unique to a block's row/column from that row/column external to block.
def scrubBlockConstraints(puzzleCandsIn : List[List[List[Int]]]) : List[List[List[Int]]] = {
  // Scrub set of three rows with constraints from input block.
  def scrubRows(blockRows : List[List[List[Int]]], inConsts : List[Int], inFinals : List[Int],
    preRows : List[List[List[Int]]], postRows : List[List[List[Int]]]) :
    (List[List[List[Int]]], List[Int], List[Int]) = {
    blockRows match {
      case Nil => (List[List[List[Int]]](), List[Int](), inFinals)
      case bss::bsss =>
        // Get this block row's constraints & finals.
        val consts = bss.filter(xs => xs.length > 1).flatten.distinct
        val finals = bss.filter(xs => xs.length == 1).flatten ::: inFinals
        // Recurse to next block row with constraints, finals and rows to be scrubbed.
        val result = scrubRows(bsss, inConsts ::: consts, finals,
          if (preRows == Nil) Nil else preRows.tail, if (postRows == Nil) Nil else postRows.tail)
        // Build the row for this block, subtracting block constraints.
        val row = (if (preRows == Nil) Nil else preRows.head.map((ys : List[Int]) =>
          if (ys.length == 1) ys else ys.diff(consts.diff(inConsts:::result._2).diff(result._3)))):::bss:::
          (if (postRows == Nil) Nil else postRows.head.map((ys : List[Int]) =>
            if (ys.length == 1) ys else ys.diff(consts.diff(inConsts ::: result._2).diff(result._3))))
        (row :: result._1, consts ::: result._2, result._3)
    }
  }

  // Scrub a list of candidate rows' blocks (transpose to scrub columns' blocks).
  def scrubBlocks(puzzleCands : List[List[List[Int]]]) : List[List[List[Int]]] = {
    // Transform block candidates into rows for use determining constraints.
    val puzzleBlocks = transBlocks(puzzleCands)
    // Loop through all block numbers, updating candidates.
    (0 to 8).foldLeft(puzzleCands)((cands, ix) => {
      // Divide current block into list of rows (3 x 3).
      val blockRowsT = divTri(puzzleBlocks(ix), (xs : List[List[Int]]) => (xs.length - 1) / 3)
      val blockRows = List(blockRowsT._1, blockRowsT._2, blockRowsT._3)
      // Get rows containing current block
      val rows = cands.drop((ix / 3) * 3).take(3)
      // Scrub constraint violations from rows containing current block.
      val result = scrubRows(blockRows, List[Int](), List[Int](), rows.map(xs => xs.take((ix % 3) * 3)),
        rows.map(xs => xs.drop(((ix % 3) + 1) * 3)))
      // Prepend & append other rows above & below those containing current block.
      cands.take((ix / 3) * 3) ::: result._1 ::: cands.drop((ix / 3 + 1) * 3)
    })
  }

  scrubBlocks(scrubBlocks(puzzleCandsIn).transpose).transpose
}
```
Working from bottom to top. `scrubBlocks` eliminates the rows' block constraint violations and then the nested lists are transposed and the columns handled similarly. `scrubBlocks` first creates an intermediate `puzzleBlocks` by transforming the structure into block-major form. It then iterates through each block, removing offending candidates from the rows extending out it. Iteration is over the integer index `ix` because this is needed for div and mod logic to deconstruct and reconstruct the candidate structure. Intermediate `blockRowsT` is built with `divTri` via a custom literal function and the returned tuple is reformed into `blockRows` holding a `List` of the block's rows. `rows` is built to hold the three rows passing through `blockRows`. `scrubRows` takes the rows in the block (`blockRows`) as well as their extensions outside the block (`preRows` and `postRows`). It recursively moves through these three lists, finding candidates existing only in one block row and removing them from corresponding cells in `preRows` and `postRows`.  
Originally building this using functional constructs and later understanding it was messy. Perhaps a more imperative design with a highly nested series of loops would have been preferable (but not FP). This elimination logic rates **complicated to program** and **hard to understand**.  
With the new array structure and indices it made sense to rewrite the block eliminations rather than enhance the existing code. Plus, the existing code was hard to understand so it was preferable to start from scratch. Here is the same functionality built with a different design:
```
// Scrub candidates unique to a block's row/column from that row/column external to block.
def scrubBlockConstraints(cands : Array[Array[List[Int]]]) : Unit = {
  // Scrub set of three rows with constraints from input block.
  ixBlockMajor.foreach(blockCells => {
    // Build index of block row & column cells.
    val blockRows = Array.ofDim[List[(Int,Int)]](3)
    val blockCols = Array.ofDim[List[(Int,Int)]](3)
    var ix = 0
    blockCells.foreach(cell => {
      val blockRow = ix / 3
      blockRows(blockRow) = if (blockRows(blockRow) == null) List(cell) else cell :: blockRows(blockRow)
      val blockCol = ix % 3
      blockCols(blockCol) = if (blockCols(blockCol) == null) List(cell) else cell :: blockCols(blockCol)
      ix += 1
    })
    // Collect row & column candidate values.
    val blockRowCands = Array.ofDim[List[Int]](3)
    val blockColCands = Array.ofDim[List[Int]](3)
    for (ix <- 0 to 2) {
      blockRowCands(ix) = blockRows(ix).foldLeft(List[Int]())((xs, cell) => 
        cell match {case (y,x) => cands(y)(x) ::: xs}).distinct
      blockColCands(ix) = blockCols(ix).foldLeft(List[Int]())((xs, cell) =>
        cell match {case (y,x) => cands(y)(x) ::: xs}).distinct
    }
    // Find each row & column's unique candidate values, by subtracting other rows' values.
    val blockRowUnique = Array.ofDim[List[Int]](3)
    val blockColUnique = Array.ofDim[List[Int]](3)
    for (ix <- 0 to 2) {
      blockRowUnique(ix) = blockRowCands(ix).diff(blockRowCands((ix+1)%3)).diff(blockRowCands((ix+2)%3))
      blockColUnique(ix) = blockColCands(ix).diff(blockColCands((ix+1)%3)).diff(blockColCands((ix+2)%3))
    }
    // Remove candidates unique to a block's row/column from that row/column external to block.
    for (ix <- 0 to 2) {
      if (blockRowUnique(ix).length > 0) {
        val y = blockRows(ix).head._1
        for (x <- 0 to 8) cands(y)(x) = if (!blockRows(ix).contains((y,x))) 
          cands(y)(x).diff(blockRowUnique(ix)) else cands(y)(x)
      }
      if (blockColUnique(ix).length > 0) {
        val x = blockCols(ix).head._2
        for (y <- 0 to 8) cands(y)(x) = if (!blockCols(ix).contains((y,x)))
          cands(y)(x).diff(blockColUnique(ix)) else cands(y)(x)
      }
    }
  })
}
```
These more imperative series of arrays, loops and assignments make the algorithm much clearer but is not a functional design. The outer loop iterates through the 9 block indexes. The first inner loop builds a sub-index of the (y,x) coordinates of the current block's rows and columns. The second loop uses these indices to build lists of all candidate values in the three rows and three columns. The third loop uses div & mod to subtract common candidate values and find those unique to only one of the rows or one of the columns. The final set of loops removes these unique candidate values from the section of each row or column outside of the block where they were found.  
This elimination enhancement reverse-engineered the functionality of the original list version and discarded the former design. This was due to its complexity and inflexibility. This is either a sign of the original design not considering extensibility or a more general artifact of complex functional designs. These factors cause a rating of **complicated enhancement complexity**. This is unfortunate and the cause should be investigated more thoroughly.

### Eliminate Matching Open Tuples
Matching open tuples are defined as equivalent sets of candidate values existing in a group of cells in the same row/column/block such that the cardinality of the value set equals the number of cells where these equivalent tuples exist. These candidate values must exist in one of the cells in the group so they may not exist in cells outside of these and in the same row/column/block. Removing singles (discussed above) is the degenerate form of eliminating matching open tuples. In the case of singles, the cardinality of the set = 1 and the number of cells where the single exists also = 1 so the definition holds. Singles are handled separately for performance reasons: see the main elimination loop in the source code or discussion in my [prior post](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part3-FindInitialCandidates.md).  
Since this elimination is localized to a row/column/block this reuses `scrubCandidates` by passing the following as a first-class function containing the row-based elimination logic:
```
// Finds & removes open tuple values from candidate lists. Open tuples where length == count in row.
def scrubOpenTuples(rowCands : List[List[Int]]) : List[List[Int]] = {
  // Builds map of tuples to the # of occurrences.
  val openTuples = rowCands.foldLeft(Map[List[Int], Int]())((result : Map[List[Int], Int], cands : List[Int]) =>
    if (cands.length > 1 && cands.length <= 4) result.get(cands) match {
      case None => result + (cands -> 1)
      case Some(count) => result + (cands -> (count + 1))
    }
    else result).filter(kv => kv._2 > 1 && kv._1.length == kv._2)

  // Removes open tuple values from other candidate lists.
  if (!openTuples.isEmpty)
    rowCands.map(xs => if (openTuples.keySet.contains(xs)) xs 
    else xs.filter(x => !openTuples.keySet.flatten.contains(x)))
  else rowCands
}
```
The first line creates intermediate `openTuples` by reducing the candidate row into a map from tuple value to number of times that tuple exists in the row and filters this list to keep those where tuple size = tuple count. This matches the definition of open tuples. If any open tuples found, these invariant violations are removed from the other cells.  
This was easy to write and later follow. Its functional form reuses existing code and simplifies the design. This rates as **simple to develop** and **simple to understand**.  
It was very natural to extend this algorithm to the array-based data structure. The following is nearly a one-to-one mapping to the list version:
```
// Finds & removes open tuple values from "row", indexed using ixs. Open tuples where length == count in row.
def scrubOpenTuples(cands : Array[Array[List[Int]]], ixs : List[(Int,Int)]) : Unit = {
  // Count tuple lists (len > 1) into map of (tuple lists -> count) and filter keeping where count == len.
  val openTuples = ixs.foldLeft(Map[List[Int], Int]())((kvs : Map[List[Int], Int], cell : (Int,Int)) =>
    cell match {case (y,x) =>
    if (cands(y)(x).length > 1 && cands(y)(x).length <= 4) kvs.get(cands(y)(x)) match {
      case None => kvs + (cands(y)(x) -> 1)
      case Some(count) => kvs + (cands(y)(x) -> (count + 1))
    }
    else kvs}).filter(kv => kv._2 > 1 && kv._1.length == kv._2)

  // Removes open tuple values from other candidate lists.
  if (!openTuples.isEmpty)
    ixs.foreach((cell : (Int,Int)) => cell match {case (y,x) =>
      cands(y)(x) = if (openTuples.keySet.contains(cands(y)(x))) cands(y)(x) 
        else cands(y)(x).filter(x => !openTuples.keySet.flatten.contains(x))
    })
}
```
As usual, the index `ixs` defines the "row" of tuples. `openTuples` is the reduction of the candidate tuples into a map of value to count. If open tuples found, `ixs.foreach` removes value violations as did the map statement from the list version.  
This design and code lends itself to being easily extended. It rates as **trivial extension complexity** due to its similarity to the list version.

### Eliminate Hidden Tuples
Just as open tuples are a generalization of singles, hidden tuple elimination is an extension of finding and removing unique values. Unique candidate values exist in only one cell of a row/column/block. With the precondition that all candidates are already found for the row, this unique candidate value must be the solution for its cell. Finding hidden tuples generalizes this. If a subset of candidate values size n is found in exactly n cells and only those cells, some permutation of the values must be the solution for these cells. Therefore any other candidates in these cells are removed as invariant violations.
```
// Build cache holding powerset row cell indexes, 2 <= length <= 4.
val ixSubsets = (0 to 8).toSet.subsets.filter(xs => xs.size > 1 && xs.size < 5).map(xs => xs.toSet).toList

// Finds & removes hidden tuple values from candidate lists. Hidden tuples are a set of candidates S 
// s.t. instances exist in exactly |S| cells.
def scrubHiddenTuples(rowCands : List[List[Int]]) : List[List[Int]] = {
  val cands = rowCands.toArray
  // Function determines whether a set of cells contains all unknown cells (i.e., multiple candidates).
  val multiCands = (xs : Set[Int]) => xs.foldLeft(true)((b, ix) => b && cands(ix).length > 1)
  // Filter out tuple subsets with at least one known cell.
  val ixMultiSubsets = ixSubsets.filter(multiCands)

  // Process all subsets in powerset.
  ixMultiSubsets.foreach(sx => {
    // Collect candidate values from outside of subset cells.
    val elimVals = (0 to 8).foldLeft(Set[Int]())((s : Set[Int], ix : Int) =>
      if (!sx.contains(ix)) s ++ cands(ix) else s).toSeq
    // Collect candidate values remaining in subset cells after subtracting those existing outside subset.
    val candVals = sx.foldLeft(Set[Int]())((s : Set[Int], ix : Int) => s ++ (cands(ix).diff(elimVals)))
    // If subset size == number of remaining candidates, hidden tuple found, remove other candidates.
    if (candVals.size == sx.size) {
      (0 to 8).foreach((ix : Int) => if (sx.contains(ix)) cands(ix) = cands(ix).diff(elimVals))
    }
  })

  cands.toList
}
```
The algorithm for finding hidden tuples looks at each row's powerset of cells. The `subsets` library method creates this but is a bit slow and the powerset does not change between invocations so the subset index is cached outside of the elimination function as `ixSubsets`. Intermediate `ixMultiSubsets` is the index's projection filtering only subsets containing all unknown cell indices (i.e., candidate list size > 1). The algorithm is now straightforward. For each subset of cells in `ixMultiSubsets`, find all candidates in the "row" outside of those cells (`elimVals`). Iterate across subset collecting unique candidates `candVals` by subtracting `elimVals`. If the `candVals.size` equals size of subset then a hidden tuple was found so the extraneous candidates in those cells are removed.  
The algorithm design involved building the separate powerset index and using it for each row/column/block of the puzzle. While looking at all of these subsets is necessary, it's not efficient. Fortunately, hidden tuples exist in the initial candidates and are not created by any other the elimination type. The main elimination loop need only execute this step once so it does not change the overall program's asymptotic performance. Building and using this powerset index in the elimination algorithm rates as **medium initial development complexity**. Following up on the design and source was difficult with only the code comments as a guide. It helped to place the powerset creation logic in the REPL and see what it creates and then the elimination logic followed naturally. This rates as **medium to understand**.  
It was straightforward to extend the algorithm to use the array data structure and access indices:
```
// Build cache mapping indexes to its powerset cell indexes, 2 <= length <= 4.
val mapSubsets = (ixss : List[List[(Int,Int)]]) => 
  ixss.foldLeft(Map[List[(Int,Int)], List[Set[(Int,Int)]]]())((kv, ixs) => kv + (ixs -> 
  (ixs.toSet.subsets(2).toList ::: ixs.toSet.subsets(3).toList ::: ixs.toSet.subsets(4).toList)))
val kvSubsets = mapSubsets(ixRowMajor) ++ mapSubsets(ixColMajor) ++ mapSubsets(ixBlockMajor)

// Finds & removes hidden tuple values from input "row." Hidden tuples are a set of candidates S
// s.t. instances exist in exactly |S| cells.
def scrubHiddenTuples(cands : Array[Array[List[Int]]], ixs : List[(Int,Int)]) : Unit = {
  // Function determines whether a set of cells contains all unknown cells (i.e., multiple candidates).
  val multiCands = (xs : Set[(Int,Int)]) => xs.foldLeft(true)((b, cell) => cell match {case (y,x) =>
    b && cands(y)(x).length > 1
  })
  // Lookup row's tuple subsets from cache and filter out those with at least one known cell.
  val ixSubsets = kvSubsets(ixs).filter(multiCands)

  // Process all subsets in powerset.
  ixSubsets.foreach(sx => {
    // Collect candidate values from outside of subset cells.
    val elimVals = ixs.foldLeft(Set[Int]())((s : Set[Int], cell : (Int,Int)) => cell match {case (y,x) =>
      if (!sx.contains(cell)) s ++ cands(y)(x) else s
    }).toSeq
    // Collect candidate values remaining in subset cells after subtracting those existing outside subset.
    val candVals = sx.foldLeft(Set[Int]())((s : Set[Int], cell : (Int,Int)) => cell match {case (y,x) =>
      s ++ (cands(y)(x).diff(elimVals))
    })
    // If subset size == number of remaining candidates, hidden tuple found, remove other candidates.
    if (candVals.size == sx.size) {
      ixs.foreach((cell : (Int,Int)) => cell match {case (y,x) =>
        if (sx.contains(cell)) cands(y)(x) = cands(y)(x).diff(elimVals)
      })
    }
  })
}
```
The only added complexity is that each "row's" access index element needs its own powerset since it references locations within the whole matrix. The list version shared the same powerset in processing each "row." This one-to-one mapping between array index elements and its powerset is maintained in map `kvSubsets`. Again, this is much easier to visualize in the REPL. For performance gain in both versions, I trim the shorter and longer elements of the powerset. Handling a single element subsets (tuple size = 1) is equivalent to eliminating unique elements. Open tuples size > 4 are unlikely so these are culled. This does not create a hole since search functionality will still find a solution in these very rare cases.  
The enhanced elimination algorithm mirrors the list version. The only additional work is building the large map of powersets and integrating their lookup. This rates as **medium enhancement complexity**.  
This completes the analysis of the candidate elimination functionality. There is another class of eliminations used for solving puzzles manually; wing eliminations. I chose not to program these because the development complexity looked more difficult than their value. The search functionality (described below) will pick up puzzles with these advanced invariant violations.

### Check Candidate State Validity
Some puzzles are invalid (i.e., unsolvable) from the start. Function `isValidCands` identifies candidate lists reduced into a state which violate the Sudoku invariants. This is used to find incorrect "search" suppositions and ultimately whether a puzzle is unsolvable.
```
// Checks for invalid puzzle states (i.e., empty candidate cells & duplicate singles).
def isValidCands(puzzleCands : List[List[List[Int]]]) : Boolean = {
  // Checks if a row is valid.
  def isValid(row : List[List[Int]]) : Boolean =
    row.foldRight((true, Set[Int]()))((cands : List[Int], res : (Boolean, Set[Int])) =>
      ((res._1 && !(cands.length == 0) && (if (cands.length == 1) !res._2.contains(cands(0)) else true)),
        if (cands.length == 1) res._2 ++ cands else res._2))._1

  // Check validity of rows, columns & blocks.
  puzzleCands.foldRight(true)((row : List[List[Int]], valid : Boolean) => valid && isValid(row)) &&
  puzzleCands.transpose.foldRight(true)((xss : List[List[Int]], valid : Boolean) => valid && isValid(xss)) &&
  transBlocks(puzzleCands).foldRight(true)((xss : List[List[Int]], valid : Boolean) => valid && isValid(xss))
}
```
This takes the complete candidate structure as input, checks row validity, and then uses transpositions to check columns and blocks. `isValid` checks puzzle invariants and reduces the "row" into a valid flag. Violations are zero length candidate lists or multiple solved cells (length = 1) sharing the same value. This code is straightforward due to the use of transpositions. It rates as **simple to initially develop** and **trivial to understand** as a follow-on developer.  
This extends naturally to use the array structure:
```
// Checks for invalid puzzle states (i.e., empty candidate cells & duplicate singles).
def isValidCands(cands : Array[Array[List[Int]]]) : Boolean = {
  // Checks if a row is valid.
  def isValid(ix : List[(Int,Int)]) : Boolean =
    ix.foldLeft((true, Set[Int]()))((res, cell) => cell match {case (y,x) =>
      ((res._1 && !(cands(y)(x).length == 0) && 
        (if (cands(y)(x).length == 1) !res._2.contains(cands(y)(x)(0)) else true)),
        if (cands(y)(x).length == 1) res._2 ++ cands(y)(x) else res._2)})._1

  // Check validity of rows, columns & blocks.
  ixRowMajor.foldRight(true)((ix : List[(Int,Int)], valid : Boolean) => valid && isValid(ix)) &&
  ixColMajor.foldRight(true)((ix : List[(Int,Int)], valid : Boolean) => valid && isValid(ix)) &&
  ixBlockMajor.foldRight(true)((ix : List[(Int,Int)], valid : Boolean) => valid && isValid(ix))
}
```
As with other modules, this can not transpose the candidate value structure to check validity of columns and blocks. It uses the access indexes to efficiently traverse the nested arrays and applies the same logic. This rates as **simple enhancement complexity**.

### Solution Search
This is the fail-safe mechanism to handle puzzles requiring supposition or those requiring an unsupported elimination (e.g., x-wing, xy-wing). It could theoretically solve a puzzle from the start but this would be tremendously inefficient. The search algorithm is expected:
```
1) Find structure's shortest candidate list (ex: size = n in cell x)
2) Create n new structures with each of the different cell's candidate options as single solution for cell x
3) Place these new candidate structures in queue (breadth-first search) or on stack (depth-first search): this uses queue
4) Loop while queue not empty:
   a) currCands <- pop(queue)
   b) Apply eliminations
   c) If currCands invalid, continue
   d) If currCands solution, emit, exit
   e) Apply steps 1-3 above to currCands
5) Emit: Unsolvable puzzle.
```
The conditionals and straightforward search logic from this pseudocode are part of the main elimination loop. Its development complexity analysis is part of the discussion in my [initial post](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part1-Reprise.md). The branching logic of Step 2 is accomplished by this function:
```
// Build successor states by making candidate assumptions from input state.
def findSuccessors(puzzleCands : List[List[List[Int]]]) : List[List[List[List[Int]]]] = {
  // Build list of candidate cell locations and their contents.
  val candCells = puzzleCands.foldRight((9, 8, List[(Int, Int, List[Int])]()))(
    (rowCands : List[List[Int]], result : (Int, Int, List[(Int, Int, List[Int])])) =>
    rowCands.foldRight((result._1 - 1, 8, result._3))
    ((cands : List[Int], res : (Int, Int, List[(Int, Int, List[Int])])) =>
      (res._1, res._2 - 1, if (cands.length > 1) (res._1, res._2, cands) :: res._3 else res._3)))._3
  // Stop making assumptions when there is only one unknown cell. Invalid puzzle.
  if (candCells.length == 1) return List[List[List[List[Int]]]]()
  // Determine the shortest candidate list and use the find one of that size.
  val minCands = candCells.foldLeft(Int.MaxValue)((res : Int, cands : (Int, Int, List[Int])) =>
    res.min(cands._3.length))
  candCells.find(_._3.length == minCands) match {
    case None => List[List[List[List[Int]]]]()
    case Some(unkCandCell) =>
      // Determine unchanged row & columns.
      val preRows = puzzleCands.take(unkCandCell._1)
      val unkRow = puzzleCands.drop(unkCandCell._1)(0)
      val postRows = puzzleCands.drop(unkCandCell._1 + 1)
      val preCells = unkRow.take(unkCandCell._2)
      val postCells = unkRow.drop(unkCandCell._2 + 1)
      // Build list of new puzzle candidates as successor states.
      unkCandCell._3.foldRight(List[List[List[List[Int]]]]())((cand:Int, result:List[List[List[List[Int]]]]) =>
        (preRows ::: (preCells ::: List(cand) :: postCells) :: postRows) :: result)
  }
}
```
The goal of this method is to fork the candidate structure into multiple new instances with one of the cells solved with each of its candidate options, hence the quad-dimension list return value. For performance reasons it makes sense to fork on the shortest candidate list. Index `candCells` is built by nested `foldRight` iteration over rows then columns of candidate structure. Its result is a list of tuples containing y, x coordinates and associated set of candidates. `minCands` is the smallest candidate list size value from `candCells`. The `candCells.find` statement finds the first element with list size = minCands and then builds all of the successor structures from its coordinates and candidate list. `preRows` and `postRows` hold the unchanged rows before and after the row containing the cell from which the successor states are built. Similarly, `preCells` and p`ostCells` are the unchanged cells in the row before and after this cell. The last statement builds the successor states by these wrapping unchanged elements around new cells containing a single value from this unknown cell's candidate list.  
This source code demonstrates another case of a deeply nested immutable data structure making a simple task look complex. The functionality is simply cloning the structure multiple times and replacing the value of a single cell in these copies. This would be trivial to accomplish and understand in other programming paradigms. With FP, the Scala code above rates **simple initial development complexity** and **medium follow-on understanding complexity**.  
Finding successor states in the array version is much not as clumsy because we can clone and edit the candidate structure:
```
// Find the successor states by making candidate assumptions from a given puzzle state.
def findSuccessors(cands : Array[Array[List[Int]]]) : List[Array[Array[List[Int]]]] = {
  // Deep clone of candidate array.
  def cloneCands(candsOld : Array[Array[List[Int]]]) : Array[Array[List[Int]]] = {
    val candsNew = candsOld.clone
    for (ix <- 0 to 8) {
      candsNew(ix) = candsNew(ix).clone
    }
    candsNew
  }

  // Find first, shortest candidate list (size 2+) and its cell coords.
  val (len, cell) = ixRowMajor.foldLeft((Integer.MAX_VALUE,(0,0)))((r:(Int,(Int,Int)), row:List[(Int,Int)]) =>
    if (r._1 == 2) r else
    row.foldLeft(r)((r : (Int,(Int,Int)), cell : (Int,Int)) => cell match {case (y,x) =>
      if (r._1 == 2) r else if (cands(y)(x).length > 1 && cands(y)(x).length < r._1)
        (cands(y)(x).length, cell) else r
    }))

  // If shortest candidate list found, return list of cloned arrays, each with a single candidate from list.
  if (len < Integer.MAX_VALUE) {
    val (y,x) = cell
    cands(y)(x).foldLeft(List[Array[Array[List[Int]]]]())((xsss, c) => 
      {val newCands = cloneCands(cands); newCands(y)(x) = List(c); newCands :: xsss})
  }
  else List[Array[Array[List[Int]]]]()
}
```
This works as expected. It rates as **simple to extend**.

## Summary
This analyzed the core puzzle solving design and code. Given a set of possible candidates for each cell, successively applying eliminations based on puzzle invariant violations eventually reduces the puzzle to its solution state. This is easily understood as the same algorithms used to manually solve Sudoku puzzles. Invalid puzzles reduce to an easily identifiable invalid state.  
The development complexity of these candidate elimination modules rate:  
| Module | Initial Development | Follow-on Understanding | Extensibility |
| --- | --- | --- | --- |
| Scrub Candidates | Simple | Simple | Medium |
| Singles & Uniques | Medium | Medium | Simple |
| Block Constraints | Complicated | Hard | Complicated |
| Matching Open Tuples | Simple | Simple | Trivial |
| Hidden Tuples | Medium | Medium | Medium |
| Candidate State Validity | Simple | Trivial | Simple |
| Solution Search | Simple | Medium | Simple |

My next article will collect all development ratings and make some general analysis observations. It is unfortunate that eliminating block constraints was an unnatural fit for FP and Scala. Block constraints are easy to understand but enforcing them requires accessing the blocks' state as well as their overlapping rows and columns. Building these types of algorithms over complex data structures is a fundamental software development need. Finding an outlier where the paradigm falls down indicates a need for more investigation. Building "wing" elimination logic would be another complex example case for the future.  
In addition to collecting and summarizing the development complexity ratings, my final article in this series will have some fun comparing the runtime performance of these two versions. My initial reason for enhancing the solver was to determine whether direct update of the candidate structure as well as avoiding the considerable garbage collection overhead of the nested lists would improve performance. Was this development effort worth it?  
Stay tuned...

