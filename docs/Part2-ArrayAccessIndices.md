# Scala Sudoku - Array Access Indices 

## Introduction
This continues my analysis of whether Functional Programming (FP) and Scala are a good choice for large, multi-developer projects? I look at three dimensions of software development by answering these questions:
1. How easy is it to initially develop algorithms over complex data structures?
2. How easily can the program design and source code be understood by other developers?
3. Is the design extensible and code maintainable over time?

My [prior article](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part1-Reprise.md) started looking at a case study Sudoku puzzle solver which I developed six years ago and recently enhanced. That post analyzed its main candidate elimination loop. For each of the above questions, I tagged this high-level logic with one of these development difficulty ratings:  
**Trivial, Simple, Medium, Complicated, Hard**  
This article continues this analysis but on a different component of the solver. The case study Scala source is located in this Sudoku Solver Github repository. The root and two solver README files contain the simple installation and execution instructions. Run either version to get a feel for the program.

## Candidate Data Structure Enhancement
The original Sudoku solver used nested lists to hold a puzzle's candidate values. This <code>List[List[List[Int]]]</code> data structure represents a list of rows holding a list of columns (cells) holding its list of candidate values. When each of these inner candidate lists are decreased to size = 1 the puzzle is solved. The elimination routines map over and reduce these inner lists to accomplish this functionality.  
Although the solver seemed fast (it solved ~200 puzzles/second) I perceived a performance drain from its memory-management burden. Every time any individual candidate list changed, a copy of its row and column of the data structure needed to be recreated because lists are immutable. Even worse, every one of the many transpositions copied the complete data structure. I hypothesized that this proliferation of memory allocation and garbage collection might be a significant performance drain.  
The new solver version attempts to address this by using nested mutable arrays of candidate lists <code>Array[Array[List[Int]]]</code> as the main data structure instead of the nested lists. Scala library's Array class provides constant time access to its elements and optional mutability. The goal was to reduce access time and greatly decrease the amount of memory management. This enhancement held promise but did create one issue since the new design would avoid memory-expensive nested list transpositions. These transpositions originally enabled reusable function literals and dense, clean source code. Without them, how would the code keep its functional design? I will describe the transpositions' importance to motivate the need for replacing them with **array access indices** in the new solver version.

### Nested List Transpositions
The original nested list structure was in row-major order so, after an elimination function was built to work on columns, the structure could easily be transposed and that same function worked on rows.  
Here is a relevant code snippet from the <code>scrubCandidates</code> function of the original version SolveSerialApp.scala:
```
puzzleCands = puzzleCands.map(scrubFn)
puzzleCands = puzzleCands.transpose.map(scrubFn).transpose
transBlocks(transBlocks(puzzleCands).map(scrubFn))
```
The elimination function (<code>scrubFn</code>) mapped over columns in the first line. Following the transpose to column-major order this same function eliminated candidates along the rows. The third line works exactly the same way except I had to build my own custom transposition function to convert to/from "block-major order."
```
// Transform each of the 9 blocks into list.
def transBlocks[A](xss : List[List[A]]) : List[List[A]] = {
  // Regroup into list of three rows each & collect into blocks.
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
```
<code>transBlocks</code> flattens first two dimensions of structure into a single list size = 81 and then splits this into thirds representing the top, middle, and bottom trios. The <code>foldRight</code> applies <code>divTri</code> to also split each of these three lists into thirds and appends every third subset of cells together with its peers from the same block. <code>divTri</code> shows an example of how first-order functions can be passed as parameters: another part of the solver calls <code>divTri</code> to split rows with a different discriminating <code>multiFunc</code>.

### Array Access Indices
The new, nested Array structure allows constant-time cell access as well as mutation. It essentially relaxes the FP paradigm to improve performance by avoiding the memory-intensive transposition operations. A good design keeps interface well separated from implementation so this data structure change should not alter the solver algorithm. As shown above, the solver algorithm eliminations rely on applying the same function to the candidate structure in different forms. Without the transpositions, how can we keep this same functional logic?  
The solution is to add a level of indirection and indices. The original version iterated directly along the data structure to perform its elimination logic. The enhanced version builds indices in advance. These define the cell iteration order for row-major, column-major, and block-major forms. An index is a nested list of (y,x) tuples constructed as global, immutable singletons. Now the same functional logic maps and reduces in the same row, column, and block-major orders but with a small runtime cost of an additional level of indirection through an index.  
These global indices are built at the start of the enhanced source in SolveSerialArrayApp.scala. It's easiest to visualize them by executing this same code in the Scala REPL:
```
scala> val ixRowMajor = (0 to 8).foldRight(List[List[(Int,Int)]]())((y, xxs) => 
     |     (0 to 8).foldRight(List[(Int,Int)]())((x, xs) => (y,x) :: xs) :: xxs)

ixRowMajor: List[List[(Int, Int)]] = 
List(List((0,0), (0,1), (0,2), (0,3), (0,4), (0,5), (0,6), (0,7), (0,8)), 
List((1,0), (1,1), (1,2), (1,3), (1,4), (1,5), (1,6), (1,7), (1,8)), ... elided ...
List((8,0), (8,1), (8,2), (8,3), (8,4), (8,5), (8,6), (8,7), (8,8)))
```
This builds the row-major index by performing a nested reduction on the integer sequences representing the row numbers and then column numbers. Looking at the (y,x) values in the <code>ixRowMajor</code> nested tuple lists shows that this represents row-major iteration order.  
Here is the column-major index:
```
scala> val ixColMajor = (0 to 8).foldRight(List[List[(Int,Int)]]())((y, xxs) =>
     |     (0 to 8).foldRight(List[(Int,Int)]())((x, xs) => (x,y) :: xs) :: xxs)

ixColMajor: List[List[(Int, Int)]] = 
List(List((0,0), (1,0), (2,0), (3,0), (4,0), (5,0), (6,0), (7,0), (8,0)), 
List((0,1), (1,1), (2,1), (3,1), (4,1), (5,1), (6,1), (7,1), (8,1)), ... elided ...
List((0,8), (1,8), (2,8), (3,8), (4,8), (5,8), (6,8), (7,8), (8,8)))
```
Naturally, this is built using the same nested reduction but swapping the order of the x and y coordinates in the produced tuple.  
The block-major index defines the block by block iteration order. This is clear by looking at the order of tuples in the nested lists result <code>ixBlockMajor</code>:
```
scala>   def cvtBase(num10:Int, newBase:Int) : String = {val q = num10 / newBase; val r = num10 % newBase; if (q == 0) r.toString else cvtBase(q, newBase) + r.toString}
cvtBase: (num10: Int, newBase: Int)String

scala>   def blockTuple(num:Int) : (Int,Int) = {
     |     val num3 = cvtBase(num, 3)
     |     val c0 = '0'.toInt
     |     val t = num3.foldRight((0,0))((x,t) => (t._1+1, t._1 match {
     |       case 3 => t._2 + 30 * (x - c0)
     |       case 2 => t._2 + 3 * (x - c0)
     |       case 1 => t._2 + 10 * (x - c0)
     |       case 0 => t._2 + (x - c0)
     |       case default => 0
     |     }))
     |     (t._2 / 10, t._2 % 10)
     |   }
blockTuple: (num: Int)(Int, Int)

scala>   val ixBlockMajor = (0 to 8).foldRight(List[List[(Int,Int)]]())((y, xxs) =>
     |     (0 to 8).foldRight(List[(Int,Int)]())((x, xs) => blockTuple(y * 9 + x) :: xs) :: xxs)

ixBlockMajor: List[List[(Int, Int)]] = 
List(List((0,0), (0,1), (0,2), (1,0), (1,1), (1,2), (2,0), (2,1), (2,2)), 
List((0,3), (0,4), (0,5), (1,3), (1,4), (1,5), (2,3), (2,4), (2,5)), ... elided ...
List((6,6), (6,7), (6,8), (7,6), (7,7), (7,8), (8,6), (8,7), (8,8)))
Building this is only slightly more complex than the other two. It begins with the same nested reductions of the integer sequences but calls the blockTuple function to produce the tuple. This converts the absolute cell index (0..80) to base 3 and applies some div & mod logic to make the conversion.
```
Future articles will show how these indices are used by the original logic. These same elimination algorithms will iterate via these indices and no longer need to repeatedly transpose the candidate structure. I will now answer the analysis questions for the above source code.

## Development Complexity Analysis
Most of this article is about building a replacement navigation mechanism so the original solver logic efficiently works with the new array-based candidate value structure. The general goal is to insulate the fundamental algorithm from the data storage form. The original source code used transpositions and the enhanced, array-based version uses indices.  
The transposition of the nested lists to column-major form is a trivial library function call. The block-major form required the code shown above in <code>transBlock</code> and <code>divTri</code>. While very short, this is more complex but still simple due to its combined use of the <code>foldRight</code> reduction, the first-class function <code>multiFunc</code> and recursion in <code>divTri</code>. These average to **simple initial development** complexity.  
Understanding this same code with only a few comments is a step up. Even remembering what I did took me a bit since this Scala source code is not self-documenting. I rate this source code at **medium developer understanding complexity**. This dense source code could certainly use more "why" and "how" comments to assist the next developer.  
Building a fast new version necessitated array access indices. Designing and constructing the enhancements was trivial. Integrating them into the solver logic is a bit more involved but (as I will show) still pretty straightforward.

## Summary
This article detailed the transposition source code in the original version and motivated the need for replacing this with indices and indirection to efficiently enhance the solver. It then presented the source to create these array access indices.  
Here are my difficulty ratings for these elements:
- Initial Development - **Simple**
- Design Understanding - **Medium**
- Program Extensibility - **Trivial**
These transposition and index components are building blocks of the original and enhanced versions of the solver, respectively. I will show how these are used in the functional design and source code throughout these versions. My next post will cover the development complexity of the candidate discovery routines.  
In the meantime, happy Scala programming & Sudoku solving. =)

