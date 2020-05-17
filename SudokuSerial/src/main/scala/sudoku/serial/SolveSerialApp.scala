package sudoku.serial

import scala.io.Source
import java.util.Date

/**
 * This program solves Sudoku puzzles.
 * Requires filename as command line parameter.
 * Usage:
 * sudoku.serial.SolveSerialApp [-q] <puzzles filename>
 * flag: -q : quiet mode (don't print puzzles)
 *
 * File contains one or more puzzles; one puzzle per line.
 * Puzzle line format:
 * 67.1...9..5..7...4....9.5.7..1.32....48...35....65.2..1.4.6....3...2..6..9...1.23
 * such that digits known and dots are unknown.
 *
 * @author Joseph Sackett
 */
object SolveSerialApp extends App {
  if (args.length < 1 || args.length > 2) {
    println("Usage:\nsudoku.serial.SolveSerialApp [-q] <puzzles filename>\nflag: -q : quiet mode (don't print puzzles)")
    System.exit(1)
  }

  // Read puzzles from input file.
  val src = Source.fromFile(args(args.length - 1))
  val puzzles = src.getLines().toList
  src.close()

  // Quiet flag indicates whether puzzles are printed.
  val quiet = args.length > 1 && args(0).startsWith("-q")

  // Build cache holding powerset row cell indexes, 2 <= length <= 3.
  val ixSubsets = (0 to 8).toSet.subsets.filter(xs => xs.size > 1 && xs.size < 4).map(xs => xs.toSet).toList

  // Record start time.
  val startTime =  System.currentTimeMillis()

  // Loop through and solve each puzzle.
  puzzles.foreach((puzzleStr : String) => {

  // Parse puzzle line into row-major nested list of initial values or zero for unknowns.
  val puzzleNums = puzzleStr.map(c => if (c == '.') 0 else c.asDigit).toList
  val puzzle = puzzleNums.grouped(9).toList

  // Printing Utilities
  val printPuzLine = (xs : List[Int]) => {xs.foreach(x => print(x + "  ")); println}
  val printCandLine = (xss : List[List[Int]]) => {xss.foreach({xs => xs.map(x => print(x)); print("  ")}); println}
  // Output puzzle.
  if (!quiet) {
    println("Puzzle:")
    puzzle.map(printPuzLine)
  }

  // Find row, column & block initial candidate lists.
  val rowCands = puzzle.map(findCands)
  val colCands = puzzle.transpose.map(findCands).transpose
  val blocksCands = transBlocks(transBlocks(puzzle).map(findCands))
  // Combine all candidates and group them by cell.
  val allCands = List(rowCands, colCands, blocksCands).transpose.map(xs => xs.transpose)
  // Find intersection of the candidates in each cell.
  val foldInter = (xss : List[List[Int]]) => xss.foldLeft((1 to 9).toList)((l1, l2) => l1.intersect(l2))
  var puzzleCands = allCands.map(xsss => xsss.map(foldInter))

  // Place the initial list of candidates in the queue.
  var searchQueue = List(puzzleCands)

  var numCandsCurr = 0; var numCandsPrev = 0; var validPuzzle = true; var first = true
  // Loop until solved or search queue empty.
  do {
    // Pop a list of candidates from the queue.
    puzzleCands = searchQueue.head
    searchQueue = searchQueue.drop(1)
    // Loop until no more candidates removed. Scrub singles, block constraints, open and hidden tuples each iteration.
    do {
      // Count initial candidates.
      numCandsCurr = puzzleCands.flatten.flatten.length
      // Loop until no more candidates removed, scrubbing singles each iteration.
      do {
        numCandsPrev = numCandsCurr
        // Scrub singles and uniques from rows, columns & blocks.
        puzzleCands = scrubCandidates(puzzleCands, scrubSingles)
        // Count remaining candidates.
        numCandsCurr = puzzleCands.flatten.flatten.length
      } while (numCandsCurr > 81 && numCandsCurr < numCandsPrev)
      // Scrub block constraints from rows & columns.
      puzzleCands = scrubBlockConstraints(puzzleCands)
      // Scrub open tuples.
      puzzleCands = scrubCandidates(puzzleCands, scrubOpenTuples)
      // Count remaining candidates.
      numCandsCurr = puzzleCands.flatten.flatten.length
      // Scrub hidden tuples, need only be done once per puzzle.
      if (first) { first = false
            puzzleCands = scrubCandidates(puzzleCands, scrubHiddenTuples)
      }
      // Check for puzzle validity & count remaining candidates.
      validPuzzle = isValidCands(puzzleCands)
      numCandsCurr = puzzleCands.flatten.flatten.length
    } while (validPuzzle && numCandsCurr > 81 && numCandsCurr < numCandsPrev)
    if (validPuzzle && numCandsCurr > 81) {
      searchQueue = findSuccessors(puzzleCands) ::: searchQueue    // depth-first
      //searchQueue = searchQueue ::: findSuccessors(puzzleCands)    // breadth-first
    }
  } while ((numCandsCurr > 81 || !validPuzzle) && searchQueue.length > 0)

  // Puzzle solved when 9 * 9 = 81 candidates remain.
  if (numCandsCurr == 81 && validPuzzle) {
    if (!quiet) { println("Solution:"); puzzleCands.map(printCandLine) }
  }
  else if (!quiet) println("Unsolvable Puzzle.") else print('x')

  }) // End puzzles.foreach()

  // Complete & output execution timing.
  val totalTime = System.currentTimeMillis() - startTime
  println("\nTotal Runtime: " + totalTime + " ms")
  if (puzzles.length > 1) {
    println("Number Puzzles: " + puzzles.length)
    println("Average Runtime: " + (totalTime.toFloat / puzzles.length).formatted("%.2f") + " ms")
    if (!quiet) println("Performance may be improved using quiet (-q) command line switch.")
  }
  // End main code.

  // Find the remaining candidate values from a row's knowns; return list of candidates in each unknown cell.
  def findCands(row : List[Int]) : List[List[Int]] = {
    val knowns = row.filter(x => x > 0)
    val cands = (1 to 9).toList.diff(knowns)
    row.map(x => if (x == 0) cands else List(x))
  }

  // Transform each of the 9 blocks into major axis of candidates lists.
  def transBlocks[A](xss : List[List[A]]) : List[List[A]] = {
    // Regroup into list of three rows each & collect common block cells.
    xss.flatten.grouped(27).foldRight(List[List[A]]())((xs : List[A], result) => {
      val blocks = divTri(xs, (xs : List[A]) => (xs.length - 1) % 9 / 3)
      blocks._1 :: blocks._2 :: blocks._3 :: result
    })
  }

  // Divides a list into tuple of 3 lists, based on input function.
  def divTri[A](xss : List[A], multiFunc : (List[A]) => Int) : (List[A], List[A], List[A]) = xss match {
    case Nil => (List[A](), List[A](), List[A]())
    case ys::yss =>
      val res = divTri(yss, multiFunc)
      val num = multiFunc(xss)
      num match {
        case 2 => (ys::res._1, res._2, res._3)
        case 1 => (res._1, ys::res._2, res._3)
        case 0 => (res._1, res._2, ys::res._3)
      }
  }

  // Scrub rows, columns & blocks of candidates with input function.
  def scrubCandidates(puzzleCandsIn : List[List[List[Int]]], scrubFn : List[List[Int]] => List[List[Int]]) : List[List[List[Int]]] = {
    var puzzleCands = puzzleCandsIn
    puzzleCands = puzzleCands.map(scrubFn)
    puzzleCands = puzzleCands.transpose.map(scrubFn).transpose
    transBlocks(transBlocks(puzzleCands).map(scrubFn))
  }

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

  // Scrub candidates unique to a block's row/column from that row/column external to block.
  def scrubBlockConstraints(puzzleCandsIn : List[List[List[Int]]]) : List[List[List[Int]]] = {
    // Scrub set of three rows with constraints from input block.
    def scrubRows(blockRows : List[List[List[Int]]], inConsts : List[Int], inFinals : List[Int],
      preRows : List[List[List[Int]]], postRows : List[List[List[Int]]]) : (List[List[List[Int]]], List[Int], List[Int]) = {
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
            if (ys.length == 1) ys else ys.diff(consts.diff(inConsts ::: result._2).diff(result._3)))) ::: bss :::
            (if (postRows == Nil) Nil else postRows.head.map((ys : List[Int]) =>
              if (ys.length == 1) ys else ys.diff(consts.diff(inConsts ::: result._2).diff(result._3))))
          (row :: result._1, consts ::: result._2, result._3)
      }
    }

    // Scrub a list of candidate rows' blocks (transpose to scrub columns' blocks).
    def scrubBlocks(puzzleCands : List[List[List[Int]]])() : List[List[List[Int]]] = {
      // Transform block candidates into rows for use determining constraints.
      val puzzleBlocks = transBlocks(puzzleCands)
      // Loop through all block numbers, updating candidates.
      (0 to 8).foldLeft(puzzleCands)((cands, ix) => {
        // Divide current block into list of rows (3 x 3).
        val blockRowsT = divTri(puzzleBlocks(ix), (xs : List[List[Int]]) => (xs.length - 1) / 3)
        val blockRows = List(blockRowsT._1, blockRowsT._2, blockRowsT._3)
        // Get rows containing current block
        val rows = cands.drop((ix / 3) * 3).take(3)
        // Scrub constraints from rows containing current block.
        val result = scrubRows(blockRows, List[Int](), List[Int](), rows.map(xs => xs.take((ix % 3) * 3)), rows.map(xs => xs.drop(((ix % 3) + 1) * 3)))
        // Prepend & append other rows above & below those containing current block.
        cands.take((ix / 3) * 3) ::: result._1 ::: cands.drop((ix / 3 + 1) * 3)
      })
    }

    scrubBlocks(scrubBlocks(puzzleCandsIn).transpose).transpose
  }

  // Finds & removes open tuple values from candidate lists. Open tuples where length == count in row.
  def scrubOpenTuples(rowCands : List[List[Int]]) : List[List[Int]] = {
    // Builds map of open tuples.
    val openTuples = rowCands.foldLeft(Map[List[Int], Int]())((result : Map[List[Int], Int], cands : List[Int]) =>
      if (cands.length > 1 && cands.length <= 4) result.get(cands) match {
        case None => result + (cands -> 1)
        case Some(count) => result + (cands -> (count + 1))
      }
      else result).filter(kv => kv._2 > 1 && kv._1.length == kv._2)

    // Removes open tuple values from other candidate lists.
    if (!openTuples.isEmpty)
      rowCands.map(xs => if (openTuples.keySet.contains(xs)) xs else xs.filter(x => !openTuples.keySet.flatten.contains(x)))
    else rowCands
  }

  // Finds & removes hidden tuple values from candidate lists. Hidden tuples are a set of candidates S s.t. instances exist in exactly |s| cells.
  def scrubHiddenTuples(rowCands : List[List[Int]]) : List[List[Int]] = {
    val cands = rowCands.toArray
    // Function determines whether a set of cells contains all unknown cells (i.e., multiple candidates).
    val multiCands = (xs : Set[Int]) => xs.foldLeft(true)((b, ix) => b && cands(ix).length > 1)
    // Filter out tuple subsets with at least one known cell.
    val ixMultiSubsets = ixSubsets.filter(multiCands)

    // Process all subsets in powerset.
    ixMultiSubsets.foreach(sx => {
      // Collect candidate values from outside of subset cells.
      val elimVals = (0 to 8).foldLeft(Set[Int]())((s : Set[Int], ix : Int) => if (!sx.contains(ix)) s ++ cands(ix) else s).toSeq
      // Collect candidate values remaining in subset cells after subtracting those existing outside subset.
      val candVals = sx.foldLeft(Set[Int]())((s : Set[Int], ix : Int) => s ++ (cands(ix).diff(elimVals)))
      // If subset size == number of remaining candidates, hidden tuple found, remove other candidates.
      if (candVals.size == sx.size) {
        (0 to 8).foreach((ix : Int) => if (sx.contains(ix)) cands(ix) = cands(ix).diff(elimVals))
      }
    })

    cands.toList
  }

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

  // Find the successor states by making candidate assumptions from a given puzzle state.
  def findSuccessors(puzzleCands : List[List[List[Int]]]) : List[List[List[List[Int]]]] = {
    // Build list of candidate cell locations and their contents.
    val candCells = puzzleCands.foldRight((9, 8, List[(Int, Int, List[Int])]()))(
      (rowCands : List[List[Int]], result : (Int, Int, List[(Int, Int, List[Int])])) =>
      rowCands.foldRight((result._1 - 1, 8, result._3))((cands : List[Int], res : (Int, Int, List[(Int, Int, List[Int])])) =>
        (res._1, res._2 - 1, if (cands.length > 1) (res._1, res._2, cands) :: res._3 else res._3)))
    // Stop making assumptions when there is only one unknown cell. Invalid puzzle.
    if (candCells._3.length == 1) return List[List[List[List[Int]]]]()
    // Determine the shortest candidate list and use the find one of that size.
    val minCands = candCells._3.foldLeft(Int.MaxValue)((res : Int, cands : (Int, Int, List[Int])) => res.min(cands._3.length))
    candCells._3.find(_._3.length == minCands) match {
      case None => List[List[List[List[Int]]]]()
      case Some(unkCandCell) =>
        // Determine unchanged row & columns.
        val preRows = puzzleCands.take(unkCandCell._1)
        val unkRow = puzzleCands.drop(unkCandCell._1)(0)
        val postRows = puzzleCands.drop(unkCandCell._1 + 1)
        val preCells = unkRow.take(unkCandCell._2)
        val postCells = unkRow.drop(unkCandCell._2 + 1)
        // Build list of new puzzle candidates as successor states.
        unkCandCell._3.foldRight(List[List[List[List[Int]]]]())((cand : Int, result : List[List[List[List[Int]]]]) =>
          (preRows ::: (preCells ::: List(cand) :: postCells) :: postRows) :: result)
    }
  }
}