# Scala Sudoku - Reprise 

## Introduction
Six years ago I began exploring the Scala programming language due to its rising popularity and because it compiles to run on the JVM. However, my primary interest was to analyze how well this new language and the functional programming paradigm worked for building non-trivial algorithms over complex data structures. Was it easy to program? Could the design be communicated to other developers? This type of analysis is important when considering adopting a language for building large systems due to high software development and maintenance costs. My blog series from that period answered these questions with my perspective at that time.  
Today, as a more seasoned Scala programmer, I decided to take another look at this topic. Is Scala a good language for large, multi-developer projects? My answer will weave my experiences building Scala software over the past six years with a current case study: I will extend my old Scala Sudoku Solver. This will allow me to addresses an additional development question. Is Scala software maintainable and extensible over time? This begins a series of articles providing this development-focused analysis. It will answer these questions in the context of programming, understanding, and extending the Sudoku solver.

## Background
Scala is a popular functional programming language. It is the primary language used to both build the Spark distributed computing framework as well as program data analytics on top of Spark and used in large projects by a number of big companies. Scala is considered a general purpose language such that programmers are not restricted to writing only functional code. This enables designs using the most-applicable abstraction for a given purpose. For example, Scala allows mutation unlike pure functional programming languages. While a fully immutable program using continuations for side-effects is an accomplishment, relaxing this restriction makes programs both simpler and understandable. Scala also offers object-oriented abstractions for use when the system design indicates this. Java is the dominant OO language running on the JVM but its support for FP looks like a graft to me. This leaves Scala well-positioned to become the primary functional programming language running on the JVM.  
This series documents my experience using Scala as a functional programming language to develop Sudoku solving algorithms. In the spirit of FP it uses mutation and imperative logic as little as possible but relaxes this in a few places to make the code look more natural. The goal is to analyze how difficult FP development is using Scala. I will address these three questions in the context of program design using the functional paradigm and programming in the Scala language:
1. How easy is it to initially develop algorithms over complex data structures?
2. How easily can the program design and source code be understood by other developers?
3. Is the design extensible and code maintainable over time?  
These are mostly subjective questions. This will present the raw information (source code) allowing the reader to form their own conclusions. I will also answer these questions myself by rating the development complexity of the solver components and source code.  
Question 1 and 2 can be addressed at the time of development and documentation. Question 3 has a temporal component. My initial series addressed 1 and 2 but not 3. This series will address 3 by building a new version of the solver which changes the underlying data structure of the initial solver. This will allow me to see how well the prior design extends. This extensibility is a function of both the program design but also the constraints imposed by the programming paradigm; FP in this case.

### Complexity Ratings
I will use these five difficulty ratings for initial programming, follow-up understanding, and extensibility (questions 1, 2 and 3):  
**Trivial, Simple, Medium, Complicated, Hard**  
This continuum of complexity is fuzzy. It must be calibrated to the level of the developers as well as their familiarity with FP.  
These ratings serve to communicate the ideas within this post and are not scientific measurements. Of course, they have nothing to do with algorithm runtime complexity.

### Array Version Comparisons
The new solver offers additional benefits. Now that I have new code executing the same algorithm, I can make comparisons across a couple additional dimensions:  
- Code complexity
- Performance  
Measuring these is a secondary goal of this series. Looking at code complexity will show that different design and coding choices affect measurements of 1 and 2 above. Measuring performance (both abstractly and empirically) is typically very crucial, however, it is not the focus here. My analysis is focused on determining whether FP in general and specifically using the Scala language is a reasonable choice for complex software development.

### Installation and Execution
It is important to have a concrete example from which to develop an understanding. As noted above, there are two examples which have the same runtime interface and the instructions are straightforward. Begin by cloning (or downloading the zip file) from this Sudoku Solver Github repository into a directory. The root and two solver README files' instructions are complete. The installation and execution information is very simple and it's not necessary to repeat it here. Try executing either version to get a feel for the program.

## Main Elimination Loop Analysis
In order to answer the main questions, I will present code snippets and rate their development complexity. The main solution loop is a good place to start. Readers familiar with Sudoku puzzles and the common solution mechanism will find the algorithm easy to understand. As one does when solving by hand, my algorithm finds prospective candidates for the unknown cells and then eliminates these candidates until a single one remains in each cell. There are multiple different types of eliminations based on puzzle constraints. I will discuss these eliminations in detail later. It is sufficient to abstract away the specifics for now and present just the candidate elimination algorithm.  
Here is the high-level pseudo-code (not Scala) algorithm:  
```
for each puzzle in file
  cands <- findCandidates(puzzle)
  candsStack.push(cands)
  while (size(cands) > 81 or not valid(cands)) and not empty(candsStack)
    cands <- candsStack.pop
    while size(cands) < prevSize and valid(cands)
      prevSize = size(cands)
      cands <- elimType1(cands)
      cands <- elimType2(cands)
      cands <- elimType3(cands)
    if size(cands) > 81 and valid(cands)
      candsStack.pushAll(findSuccessorStates(cands))
  if size(cands) = 81 and valid(cands)
    emit(cands)
  else emit "Invalid puzzle."
```
A puzzle is a 9x9 grid of either known or unknown cell values. The first step is to find all possible candidate values for the puzzle's unknown values based on the Sudoku puzzle constraints. cands is a data structure built by findCandidates() holding a collection of possible candidates for each cell. A known cell has only one candidate value and an unknown cell has more than one value. The nested while loops are the solver logic engine.  
The inner while loop iterates through the elimination logic working to remove candidate values until all cells have a single candidate (the solution state). The outer while loop is responsible for search functionality. If the inner loop is unable to remove any more candidates the solver must make a conjecture and then continue elimination from there. More concretely, findSuccessorStates(cands) takes one of the unknown cells in cands with multiple candidates and builds the different states where that cell is solved. These states are placed on the candsStack and effect a breadth-first search. A successful solution has 81 candidates and is valid based on the Sudoku puzzle constraints. An invalid puzzle is eliminated to the point that it breaks the puzzle constraints and the search queue is empty.  
This algorithm lends itself to straightforward imperative logic. Changing all of this to FP would be unnatural. I therefore decided to keep the while loops and mutation in the Scala source code. The underlying data structures are lists and the iteration across them is very functional. Here is the Scala main elimination loop:  
```
puzzles.foreach((puzzleStr : String) => {
  // Parse puzzle line into 9x9 List[List[List[Int]]] of initial candidate lists.
  val puzzleNums = puzzleStr.foldRight(List[Int]())((ch : Char, puzzNums : List[Int]) => 
    (if (ch == '.') 0 else ch.asDigit) :: puzzNums)
  val puzzle = puzzleNums.grouped(9).toList
  // Find row, column & block candidates.
  val rowCands = puzzle.map(findCands)
  val colCands = puzzle.transpose.map(findCands).transpose
  val blocksCands = transBlocks(transBlocks(puzzle).map(findCands))
  // Combine all candidates and group them by cell.
  val allCands = List(rowCands, colCands, blocksCands).transpose.map(xs => xs.transpose)
  // Find intersection of the candidates in each cell.
  val foldInter = (xss : List[List[Int]]) =>
    xss.foldLeft((1 to 9).toList)((l1, l2) => l1.intersect(l2))
  var puzzleCands = allCands.map(xsss => xsss.map(foldInter))
  // Place the initial list of candidates in the queue.
  var searchQueue = List(puzzleCands)

  var numCandsCurr = 0; var numCandsPrev = 0; var validPuzzle = true; var first = true
  // Loop until solved or search queue empty.
  do {
    // Pop a list of candidates from the queue.
    puzzleCands = searchQueue.head
    searchQueue = searchQueue.drop(1)
    // Loop until no more candidates removed:
    // scrub singles, block constraints, open and hidden tuples each iteration.
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
      puzzleCands = scrubCandidates(puzzleCands, scrubHiddenTuples)
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
    println("Solution:"); puzzleCands.map(printCandLine)
  }
  else println("Unsolvable Puzzle.")
})
```

### Initial Development Complexity
This Scala code is quite a bit more complicated than the pseudo-code solution algorithm and this step-up is normal as source code must handle 100% of the details. There is a third, nested while loop solely there for performance optimizations. An equivalent Java solution would look more like the pseudo-code algorithm. The main reason for the added complexity is the nested list structure <code>List[List[List[Int]]]</code> holding cell candidates. This is a list of rows holding a list of columns holding a list of candidates. The nested list structure is natural to functional programming and enables dense iterations and reductions. A good example of this is how the same findCands function is applied to the rows, columns, and blocks. To find candidates for columns and blocks, these need only be transposed to become the major axis and then apply the same findCands function before transposing back to the original row-major shape. A future post will discuss this in more detail.  
So how difficult was the initial programming of this Scala code above? I personally rate it as **medium programming complexity**. Most of the effort went into establishing the loop boundary conditions and this would be necessary for any programming paradigm so these main elimination loops rate similarly complex for functional vs. imperative programming. Looking ahead, all of the functions called by the main loop or applied to the nested list structure are much more complex.

### Design Understanding Complexity
Because I had not seen this code for six years I also had a small taste of trying to understand it cold. Almost half of the code is comments. I found these necessary because the dense, functional transformations and reductions (map & fold) are definitely not self-documenting. I also had a descriptive blog post from the original time period which explained the candidate elimination algorithm as the pseudo-code does above.  
With the comments and documentation, I expect a good functional programmer would find this source code above **simple to understand** as long as they don't trace into a function nor get mired in the dense functional operations. Much of the complexity is pushed down to the functions called or applied within these elimination loops. It is not necessary to understand every nuance of "how" someone else's program is accomplishing its functionality as long as you understand "what" it is doing and "why." If it becomes necessary to understand the details, accomplish this in a later pass. In other words, "Programs are like onions."  
FP source is not as self-documenting as imperative code. It takes more documentation and comments to communicate a program's design. For example, the pseudo-code algorithm above is very helpful for understanding functional code. Writing this extra documentation is time-consuming and essentially involves porting FP to imperative logic.

### Extensibility Complexity
Instead of using nested lists <code>List[List[List[Int]]]</code> to hold the candidate values, the new version uses a a 2D array of candidate lists <code>Array[Array[List[Int]]]</code> of dimension 9x9. This new data structure more closely models the Sudoku matrix and it could improve algorithm performance due to the direct cell access provided by the Array structure.  
So how difficult was it to integrate this data structure and new access source code into the same algorithm? The main elimination loops were very similar. Here are some of the differences:
- Counting the number of remaining candidates  
<code>numCandsCurr = currCands.foldLeft(0)((l,xxs) => l + xxs.foldLeft(0)((m,xs) => m + xs.length))</code>  
- Elimination routines required an extra, indexing parameter  
<code>scrubCandidates(scrubSingles(currCands, _))</code>  
Counting remaining candidates must change from simply flattening the nested lists and accumulating as shown in the full source code above. Iterating across the Array in the new version involves nested fold reductions. The elimination routines iterate across the rows, columns, and within the blocks of the candidate structure. As I will show in a future post, an index of array cell coordinates becomes necessary in the new version. This index must be passed into the elimination functions.  
Changing the data structure permeated the details of the elimination routines but not the main algorithm code. These changes were localized to the implementation and insulated the high-level interface of the main elimination loops. This made the above code simple to enhance.  

## Summary
This post is the first in a series to analyze the applicability of FP and Scala to large-scale development. This general topic is addressed by examining a specific case-study involving building, understanding, and extending a Sudoku solver built using Scala. This post analyzed the complexity of the Sudoku solver's main elimination loops. It tagged each of three dimensions with one of these ratings Trivial, Simple, Medium, Complicated, Hard.  
Here are the ratings for this component's design and source code:
- Initial Development - **Medium**
- Design Understanding - **Simple**
- Program Extensibility - **Simple**  
My findings indicate that the solver's high-level algorithm is straightforward to build, communicate, and extend using Scala. I believe this is primarily due to it being designed and programmed as an imperative algorithm using while loops as well as mutability. FP does not use these logic structures. It is necessary to dig into the next level of detail to see more functional code and get more ratings as data to answer the questions.  
My next blog entry will peel off the next layer. I will look at the indices used to efficiently access the array cells in the new version of the solver. More soon.

