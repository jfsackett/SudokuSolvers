# Scala Sudoku
### Serial Solver
### Author: Joseph Sackett

#### Installation Requirements
Running the program requires only Java 11+ and a single command.  

#### Execution Instructions
1. Install latest Java JDK from Oracle
2. Clone or download this project from: [jfsackett/SudokuSolvers](https://github.com/jfsackett/SudokuSolvers)
3. Open command prompt and navigate to project root: **&lt;install-path&gt;/SudokuSolvers/SudokuSerial**
4. Use Gradle Wrapper to compile & run program  
   Execute at command prompt:  
   `$ gradlew run --args="input/puzzle1.txt"`
5. Try different puzzles by changing the puzzle input txt file (browse input subdirectory)  
   Note that solving the multi-puzzle files produces a great deal of output. Suppress this with the -q command line option.  
   `$ gradlew run --args="-q input/top95.txt"`

#### Development Instructions
