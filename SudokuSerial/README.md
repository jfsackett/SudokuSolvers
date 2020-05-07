# Scala Sudoku
### Serial Solver
### Author: Joseph Sackett

#### Installation Requirements
Solving Sudoku puzzles requires Java 11+ and only a single command.  

#### Execution Instructions
1. Install latest Java JDK from Oracle
2. Clone or download this repository from: [jfsackett/SudokuSolvers](https://github.com/jfsackett/SudokuSolvers)
3. Open shell and change to Serial Solver project root: **&lt;install-path&gt;/SudokuSolvers/SudokuSerial**
4. Use Gradle Wrapper to compile & run program by executing shell command:  
   `$ gradlew run --args="input/puzzle1.txt"`
5. Try different puzzles by changing the puzzle input txt file (browse input subdirectory)  
   Note that solving multi-puzzle files produces a great deal of output: suppress this with the -q command line option  
   `$ gradlew run --args="-q input/top95.txt"`
6. Solver will output puzzle solution(s) in regular mode, else *Unsolvable Puzzle.*  
7. Solver will not produce output in quiet mode, except an *x* for unsolvable puzzles

#### Development Instructions
1. Minimally requires Java 11+ and Gradle  
2. Gradle JavaExec does not need wrapper: above commands same except use gradle (not gradlew) 
3. Could optionally be built and executed via IDE with Scala knowledge.
4. Single source code file: **&lt;project-root&gt;/src/main/sudoku/serial/SolveSerialApp.scala**

#### Custom Puzzles
If you would like to solve your own custom puzzles,  
place each puzzle on its own file line with the format:  
67.1...9..5..7...4....9.5.7..1.32....48...35....65.2..1.4.6....3...2..6..9...1.23  
such that digits known and dots are unknown.
