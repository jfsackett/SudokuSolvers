package sudoku.serial

import scala.io.Source
import java.util.Date

/**
 * This program solves Sudoku puzzles.
 * Requires filename as command line parameter.
 * Usage:
 * sudoku.serial.SolveSerialArrayApp [-q] <puzzles filename>
 * flag: -q : quiet mode (don't print puzzles)
 *
 * File contains one or more puzzles; one puzzle per line.
 * Puzzle line format:
 * 67.1...9..5..7...4....9.5.7..1.32....48...35....65.2..1.4.6....3...2..6..9...1.23
 * such that digits known and dots are unknown.
 *
 * @author Joseph Sackett
 */
object SolveSerialArrayApp extends App {
  if (args.length < 1 || args.length > 2) {
    println("Usage:\nsudoku.serial.SolveSerialArrayApp [-q] <puzzles filename>\nflag: -q : quiet mode (don't print puzzles)")
    System.exit(1)
  }

  // Read puzzles from input file.
  val src = Source.fromFile(args(args.length - 1))
  val puzzles = src.getLines().toList
  src.close()

  // Quiet flag indicates whether puzzles are printed.
  val quiet = args.length > 1 && args(0).startsWith("-q")

  // Build row major index into candidate array cells.
  val ixRowMajor = (0 to 8).foldRight(List[List[(Int,Int)]]())((y, xxs) => (0 to 8).foldRight(List[(Int,Int)]())((x, xs) => (y,x) :: xs) :: xxs)

  // Build column major index into candidate array cells.
  val ixColMajor = (0 to 8).foldRight(List[List[(Int,Int)]]())((y, xxs) => (0 to 8).foldRight(List[(Int,Int)]())((x, xs) => (x,y) :: xs) :: xxs)

  // Function converts base 10 number to new base.
  def cvtBase(num10:Int, newBase:Int) : String = {val q = num10 / newBase; val r = num10 % newBase; if (q == 0) r.toString else cvtBase(q, newBase) + r.toString}
  // Converts line index (0..80) to block index tuple.
  def blockTuple(num:Int) : (Int,Int) = {
    val num3 = cvtBase(num, 3)
    val c0 = '0'.toInt
    val t = num3.foldRight((0,0))((x,t) => (t._1+1, t._1 match {
      case 3 => t._2 + 30 * (x - c0)
      case 2 => t._2 + 3 * (x - c0)
      case 1 => t._2 + 10 * (x - c0)
      case 0 => t._2 + (x - c0)
      case default => 0
    }))
    (t._2 / 10, t._2 % 10)
  }
  // Build block major index into candidate array cells (blocks ordered in row major order).
  val ixBlockMajor = (0 to 8).foldRight(List[List[(Int,Int)]]())((y, xxs) => (0 to 8).foldRight(List[(Int,Int)]())((x, xs) => blockTuple(y * 9 + x) :: xs) :: xxs)

  // Build cache mapping indexes to its powerset cell indexes, 2 <= length <= 3.
  val mapSubsets = (ixss : List[List[(Int,Int)]]) => ixss.foldLeft(Map[List[(Int,Int)], List[Set[(Int,Int)]]]())((kv, ixs) => kv + (ixs -> (ixs.toSet.subsets(2).toList ::: ixs.toSet.subsets(3).toList)))
  val kvSubsets = mapSubsets(ixRowMajor) ++ mapSubsets(ixColMajor) ++ mapSubsets(ixBlockMajor)

  // Record start time.
  val startTime =  System.currentTimeMillis()

  // Loop through and solve each puzzle.
  puzzles.foreach((puzzleStr : String) => {

  // Parse puzzle line into 9x9 Array[Array[List[Int]]] of initial candidate lists.
  val initCands = Array.ofDim[List[Int]](9,9)
  for (ix <- 0 to 80) {
    initCands(ix / 9)(ix % 9) = if (puzzleStr.charAt(ix) == '.') List() else List(puzzleStr.charAt(ix).asDigit)
  }

  // Printing utility function.
  val printCandLine = (xss : Array[List[Int]]) => {xss.foreach({xs => if (xs.length == 0) print("0") else xs.map(x => print(x)); print("  ")}); println}
  // Output puzzle.
  if (!quiet) {
    println("Puzzle:")
    initCands.map(printCandLine)
    println()
  }

  // Find initial candidates. Intersect row, column & block candidates, updating array.
  ixRowMajor.foreach(findCands(initCands, _))
  ixColMajor.foreach(findCands(initCands, _))
  ixBlockMajor.foreach(findCands(initCands, _))

  // Place initial array of candidates in the queue.
  var searchQueue = List(initCands)

  var currCands = initCands
  var numCandsCurr = 0; var numCandsPrev = 0; var validPuzzle = true; var first = true
  // Loop until solved or search queue empty.
  do {
    // Pop array of candidates from the queue.
    currCands = searchQueue.head
    searchQueue = searchQueue.drop(1)
    // Loop until no more candidates removed. Scrub singles, block constraints, open and hidden tuples each iteration.
    do {
      // Count initial candidates.
      numCandsCurr = currCands.foldLeft(0)((s,xxs) => s + xxs.foldLeft(0)((t,xs) => t + xs.length))
      // Loop until no more candidates removed, scrubbing singles & uniques each iteration.
      do {
        numCandsPrev = numCandsCurr
        // Scrub singles and uniques from rows, columns & blocks.
        scrubCandidates(scrubSingles(currCands, _))
        // Count remaining candidates.
        numCandsCurr = currCands.foldLeft(0)((l,xxs) => l + xxs.foldLeft(0)((m,xs) => m + xs.length))
      } while (numCandsCurr > 81 && numCandsCurr < numCandsPrev)
      // Scrub block constraints from rows & columns.
      scrubBlockConstraints(currCands)
      // Scrub open tuples.
      scrubCandidates(scrubOpenTuples(currCands, _))
      // Scrub hidden tuples, need only be done once per puzzle.
      if (first) { first = false
        scrubCandidates(scrubHiddenTuples(currCands, _))
      }
      // Check puzzle validity & count remaining candidates.
      validPuzzle = isValidCands(currCands)
      numCandsCurr = currCands.foldLeft(0)((l,xxs) => l + xxs.foldLeft(0)((m,xs) => m + xs.length))
    } while (validPuzzle && numCandsCurr > 81 && numCandsCurr < numCandsPrev)
    if (validPuzzle && numCandsCurr > 81) {
      searchQueue = findSuccessors(currCands) ::: searchQueue    // depth-first
//      searchQueue = searchQueue ::: findSuccessors(currCands)    // breadth-first
    }
  } while ((numCandsCurr > 81 || !validPuzzle) && searchQueue.length > 0)

  // Puzzle solved when 9 * 9 = 81 candidates remain.
  if (numCandsCurr == 81 && validPuzzle) {
    if (!quiet) { println("Solution:"); currCands.map(printCandLine) }
  }
  else if (!quiet) println("Unsolvable Puzzle.") else print('x')

  }) // End puzzles.foreach() loop.

  // Complete & output execution timing.
  val totalTime = System.currentTimeMillis() - startTime
  println("\nTotal Runtime: " + totalTime + " ms")
  if (puzzles.length > 1) {
    println("Number Puzzles: " + puzzles.length)
    println("Average Runtime: " + (totalTime.toFloat / puzzles.length).formatted("%.2f") + " ms")
    if (!quiet) println("Performance may be improved using quiet (-q) command line switch.")
  }
  // End main code.

  // Find row's initial candidate values from the row's knowns; update list of candidates in each unknown cell.
  def findCands(cands : Array[Array[List[Int]]], row : List[(Int,Int)]) = {
    val allVals = (1 to 9).toList
    val rowCands = row.foldLeft(allVals)((cs,cell) => cell match {case (y,x) => if (cands(y)(x).length == 1) cs.diff(cands(y)(x)) else cs})
    row.foreach{case (y,x) => cands(y)(x) = if (cands(y)(x).length == 0) rowCands else if (cands(y)(x).length == 1) cands(y)(x) else rowCands.intersect(cands(y)(x))}
  }

  // Scrub rows, columns & blocks of candidates with input function.
  def scrubCandidates(scrubFn : (List[(Int,Int)]) => Unit) : Unit = {
    ixRowMajor.foreach(xs => scrubFn(xs))
    ixColMajor.foreach(xs => scrubFn(xs))
    ixBlockMajor.foreach(xs => scrubFn(xs))
  }

  // Remove single & unique candidates from input "row." Row's cells indexed using ixs (list of cell coords) into cands vector.
  def scrubSingles(cands : Array[Array[List[Int]]], ixs : List[(Int,Int)]) : Unit = {
    // Fold function taking & returning tuple containing (multiple cands, all cands).
    def findMultiples(t : (List[Int],List[Int]), cell : (Int,Int)) : (List[Int], List[Int]) = cell match {case (y,x) =>
      (cands(y)(x).filter(v => t._2.contains(v)) ::: t._1, cands(y)(x) ::: t._2)}

    // Build list of known values (singles with candidate lists size 1).
    val singles = ixs.foldLeft(List[Int]())((xs, cell) => cell match {case (y,x) => if (cands(y)(x).length == 1) cands(y)(x).head :: xs else xs})
    // Find row candidates existing multiple times as well as all row candidates, in tuple (multiple cands, all cands).
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
         blockRowCands(ix) = blockRows(ix).foldLeft(List[Int]())((xs, cell) => cell match {case (y,x) => cands(y)(x) ::: xs}).distinct
         blockColCands(ix) = blockCols(ix).foldLeft(List[Int]())((xs, cell) => cell match {case (y,x) => cands(y)(x) ::: xs}).distinct
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
           for (x <- 0 to 8) cands(y)(x) = if (!blockRows(ix).contains((y,x))) cands(y)(x).diff(blockRowUnique(ix)) else cands(y)(x)
         }
         if (blockColUnique(ix).length > 0) {
           val x = blockCols(ix).head._2
           for (y <- 0 to 8) cands(y)(x) = if (!blockCols(ix).contains((y,x))) cands(y)(x).diff(blockColUnique(ix)) else cands(y)(x)
         }
       }
    })
  }

  // Finds & removes open tuple values from input "row", indexed using ixs. Open tuples where length == count in row.
  def scrubOpenTuples(cands : Array[Array[List[Int]]], ixs : List[(Int,Int)]) : Unit = {
    // Count tuple lists (len > 1) into map of (tuple lists -> count) and filter keeping where count == len.
    val openTuples = ixs.foldLeft(Map[List[Int], Int]())((kvs : Map[List[Int], Int], cell : (Int,Int)) => cell match {case (y,x) =>
      if (cands(y)(x).length > 1 && cands(y)(x).length <= 4) kvs.get(cands(y)(x)) match {
        case None => kvs + (cands(y)(x) -> 1)
        case Some(count) => kvs + (cands(y)(x) -> (count + 1))
      }
      else kvs}).filter(kv => kv._2 > 1 && kv._1.length == kv._2)

    // Removes open tuple values from other candidate lists.
    if (!openTuples.isEmpty)
      ixs.foreach((cell : (Int,Int)) => cell match {case (y,x) =>
        cands(y)(x) = if (openTuples.keySet.contains(cands(y)(x))) cands(y)(x) else cands(y)(x).filter(x => !openTuples.keySet.flatten.contains(x))
      })
  }

  // Finds & removes hidden tuple values from input "row." Hidden tuples are a set of candidates S s.t. instances exist in exactly |s| cells.
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

  // Checks for invalid puzzle states (i.e., empty candidate cells & duplicate singles).
  def isValidCands(cands : Array[Array[List[Int]]]) : Boolean = {
    // Checks if a row is valid.
    def isValid(ix : List[(Int,Int)]) : Boolean =
      ix.foldLeft((true, Set[Int]()))((res, cell) => cell match {case (y,x) =>
        ((res._1 && !(cands(y)(x).length == 0) && (if (cands(y)(x).length == 1) !res._2.contains(cands(y)(x)(0)) else true)),
          if (cands(y)(x).length == 1) res._2 ++ cands(y)(x) else res._2)})._1

    // Check validity of rows, columns & blocks.
    ixRowMajor.foldRight(true)((ix : List[(Int,Int)], valid : Boolean) => valid && isValid(ix)) &&
    ixColMajor.foldRight(true)((ix : List[(Int,Int)], valid : Boolean) => valid && isValid(ix)) &&
    ixBlockMajor.foldRight(true)((ix : List[(Int,Int)], valid : Boolean) => valid && isValid(ix))
  }

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
    val (len, cell) = ixRowMajor.foldLeft((Integer.MAX_VALUE,(0,0)))((r : (Int,(Int,Int)), row : List[(Int,Int)]) =>
      if (r._1 == 2) r else
      row.foldLeft(r)((r : (Int,(Int,Int)), cell : (Int,Int)) => cell match {case (y,x) =>
        if (r._1 == 2) r else if (cands(y)(x).length > 1 && cands(y)(x).length < r._1) (cands(y)(x).length, cell) else r
      }))

    // If shortest candidate list found, return list of cloned candidate arrays, each with a single candidate from list.
    if (len < Integer.MAX_VALUE) {
      val (y,x) = cell
      cands(y)(x).foldLeft(List[Array[Array[List[Int]]]]())((xsss, c) => {val newCands = cloneCands(cands); newCands(y)(x) = List(c); newCands :: xsss})
    }
    else List[Array[Array[List[Int]]]]()
  }
}