# Scala Sudoku - Find Initial Candidates 

## Introduction
This is the third series article in my analysis of the Functional Programming (FP) paradigm and Scala language as a good choice for large, multi-developer projects. I look at an example case study of a Sudoku Puzzle Solver to see how easy it is for developers to build, understand, and extend algorithms over complex data structures. I analyze these three dimensions of software development by answering these questions:
1. How easy is it to initially develop algorithms over complex data structures?
2. How easily can the program design and source code be understood by other developers?
3. Is the design extensible and code maintainable over time?

For each of the above questions, I rate the development complexity of a component's design and source code. These are the ratings:  
**Trivial, Simple, Medium, Complicated, Hard**  
The first article looked at the high-level algorithm of the Scala solver which I developed six years ago and recently enhanced. This post analyzes the logic to transform a puzzle read from an input file into its candidate list structure. As discussed earlier, the two versions use different data structures for this purpose. The goal is that the enhancement will improve performance.

## Nested Lists of Initial Candidates 
The original Sudoku solver used nested lists to hold a puzzle's candidate values. This <code>List[List[List[Int]]]</code> data structure represented a list of rows holding a list of columns (cells) holding its list of candidate values. This homogeneous form aligns itself with the list processing strength of FP and Scala due to their shared roots with LISP.

### Puzzle Line to Candidate Lists
The input file contains one or more puzzles; one puzzle per line. Puzzle line format:  
<code>67.1...9..5..7...4....9.5.7..1.32....48...35....65.2..1.4.6....3...2..6..9...1.23</code>  
such that digits are known and dots are unknown.  
The first two steps are to parse this line into the puzzle's known values and then find the possible candidate values for the unknown cells. Here is the source to build this nested list of candidate values.
```
// Parse puzzle line into row-major nested list of initial values or zero for unknowns.
val puzzleNums = puzzleStr.map(c => if (c == '.') 0 else c.asDigit).toList
val puzzle = puzzleNums.grouped(9).toList

// Find the remaining candidate values from a row's knowns; return list of candidates in each unknown cell.
def findCands(row : List[Int]) : List[List[Int]] = {
  val knowns = row.filter(_ > 0)
  val cands = (1 to 9).toList.diff(knowns)
  row.map(x => if (x == 0) cands else List(x))
}

// Find row, column & block candidates.
val rowCands = puzzle.map(findCands)
val colCands = puzzle.transpose.map(findCands).transpose
val blockCands = transBlocks(transBlocks(puzzle).map(findCands))

// Combine all candidates and group them by cell.
val allCands = List(rowCands, colCands, blockCands).transpose.map(_.transpose)

// Find intersection of the candidates in each cell.
val reduceInter = (xss : List[List[Int]]) => xss.reduce(_.intersect(_))
var puzzleCands = allCands.map(_.map(reduceInter))
```
The first line simply parses the input line into a list of numbers and the second groups this into a nested list. The next three lines find the candidates in the three forms (row, column, block-major) by applying the <code>findCands</code> function. <code>allCands</code> groups all candidates for each cell together in adjacent lists. Building this uses the powerful library function <code>transpose</code>. The outer transpose groups each row's candidate lists together and the inner one groups each cell's candidate list together. Finally, these adjacent candidate lists (for each cell, for each form) are intersected into a single candidate list. <code>puzzleCands</code> now has the comprehensive row-major form. This is the main <code>List[List[List[Int]]]</code> structure used throughout the rest of algorithm.

### Complexity Ratings
Finding the row, column, and block form candidates is mostly straightforward code to both build and understand. Transposing the 2D puzzle and mapping the diff function across each row is clear functional code. Building this rates as simple due to original developer's familiarity with the data structure. Understanding this code is medium complexity because a fresh set of eyes must understand the puzzle format, the result of the transpose operations, and the <code>findCands</code> function.  
Combining and grouping all candidates into <code>allCands</code> is a dense series of operations and powerful use of the <code>transpose</code> library function. Applying this function to a 4D structure and then mapping it across its nested 3D structures has a surprising result. It groups each cell's three candidate lists (from the different forms) together in a single list. These nested matrix operations are difficult to visualize as both the initial programmer and trying to understand it later. Even having written this code myself six years ago, I had to construct a simple example in the REPL to deconstruct this line's functionality.  
This grouping operation is the only complex line above code. The snippet averages to rate **medium initial development difficulty**. With only comments as a guide, the above rates as **complicated to understand**. This assumes it is necessary to fully understand the <code>allCands</code> definition. The imperative version of this single grouping operation would be a nested set of verbose loops and operations between intermediate values. This would be more self-documenting but messier than the condensed series of functional operations. This illustrates the irony of FP: the code is often cleaner but belies its hidden complexity.

## Matrix of Initial Candidates
The recently enhanced Sudoku solver uses 9x9 nested Arrays instead of nested Lists to hold the candidate lists. The goal of this modification is to improve performance. The full background for this choice and need for external indices to navigate the arrays is discussed in my prior post. These array access indices (<code>ixRowMajor, ixColMajor, ixBlockMajor</code>) are used throughout the new version including the snippet below. This parses each line into the puzzle's known values and then finds the possible candidate values for the unknown cells. This is the same "find initial candidates" functionality as the list version above but using a structure allowing direct cell access as well as mutation of their values.
```
// Parse puzzle line into 9x9 Array[Array[List[Int]]] of initial candidate lists.
val initCands = Array.ofDim[List[Int]](9,9)
for (ix <- 0 to 80) {
  initCands(ix / 9)(ix % 9) = if (puzzleStr.charAt(ix) == '.') List() else List(puzzleStr.charAt(ix).asDigit)
}

// Find row's initial candidate values from the row's knowns; update list of candidates in each unknown cell.
def findCands(cands : Array[Array[List[Int]]], row : List[(Int,Int)]) = {
  val allVals = (1 to 9).toList
  val rowCands = row.foldLeft(allVals)((cs,cell) => cell match {case (y,x) => if (cands(y)(x).length == 1) cs.diff(cands(y)(x)) else cs})
  row.foreach{case (y,x) => cands(y)(x) = if (cands(y)(x).length == 0) rowCands else if (cands(y)(x).length == 1) cands(y)(x) else rowCands.intersect(cands(y)(x))}
}

// Find initial candidates. Intersect row, column & block candidates, updating array.
ixRowMajor.foreach(findCands(initCands, _))
ixColMajor.foreach(findCands(initCands, _))
ixBlockMajor.foreach(findCands(initCands, _))
```
The for loop at the top parses the puzzle string into the array using div & mod for the array indices. This imperative code was an easy port from the list version.
The rest of the code builds the candidate lists. Without the powerful transpose operation, it traverses the nested arrays along "rows" defined by the indices. These virtual rows are actually rows, columns, and blocks. foreach "row," first find the list of unknowns into rowCands then update each cell with the intersection of its previous contents and rowCands. As we add loops and mutability the code becomes more imperative and verbose.
One Scala syntax nuisance is that "tuples cannot be directly destructured in method or function parameters." So instead of a clear function parameter like <code>((y,x):(Int,Int)) =></code> it is necessary to pattern match with <code>case(y,x) =></code> every time. This is noisy to both write and read.

### Enhancement Complexity
Once we had the array access indices concept understood and globally defined, these enhancements were natural. The ratings amortize the indices' development complexity into where they are used. Matrix initial candidates functionality rates as **simple enhancement complexity**. It still uses some functional logical structures but is not the the clean functional code of the original version. My goal is to improve performance by relaxing the FP paradigm so this is not an issue. Scala does not restrict the developer to FP.

## Summary
This code takes an input puzzle line and converts it into the main candidate data structure used throughout each version. It only needs to be executed once for each puzzle. The development complexity ratings are a step up from the prior components.
- Initial Development - **Medium**
- Design/Code Understanding - **Complicated**
- Program Extensibility - **Simple**

The initial version's nested lists are tailored to FP but, as shown above, this source can obfuscate the functionality. The result is that version's higher difficulty ratings, both for development and understanding.  
This is the third set of ratings. As this number increases a pattern may emerge. This is a specific type of program: algorithmic data transformation. Knowing Scala's strengths, this is a good application. If this introduced some bias, I will need to look at Scala in other parts of the stack as well.  
Now that the candidate structures are built for each version, my next article will dig into the details of the eliminations in my next post. My first post showed their high-level calls from the main algorithm loop. For readers who solve Sudoku puzzles by hand, these will be very familiar and fun to see in Scala.  

