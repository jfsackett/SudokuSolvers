# Scala Sudoku Solvers
### Author: Joseph Sackett

Currently there are two versions of the solver:
1. [SudokuSerial](./SudokuSerial) - single-threaded version using nested lists to hold intermediate candidate values
2. [SudokuSerialArray](./SudokuSerialArray) - single-threaded version using an array to hold candidate values
  
The installation and execution instructions are very simple and this documentation is located in the project directories just below here.  
The two versions have the same interface and high-level solution algorithm but differ by the main data structure holding the candidate values. This single difference significantly affects the elimination logic applied to it.  
Writing these two versions six years apart was an intriguing exercise. They provided a good case study for whether Functional Programming is a good choice for building, understanding and, extending algorithms over complex data structures. I collected my analysis into a series of five articles which work to answer these questions:
1. How easy is it to initially develop algorithms over complex data structures?
2. How easily can the program design and source code be understood by other developers?
3. Is the design extensible and code maintainable over time?

The case study is a light, anecdotal pass over this important question of whether FP is a good choice for large projects. It illuminates a few issues which merit further, more complete, research.  
Here are the series articles:
- [Part 1 - Reprise](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part1-Reprise.md)
- [Part 2 - Array Access Indices](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part2-ArrayAccessIndices.md)
- [Part 3 - Find Initial Candidates](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part3-FindInitialCandidates.md)
- [Part 4 - Eliminate Candidates](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part4-EliminateCandidates.md)
- [Part 5 - Conclusion](https://github.com/jfsackett/SudokuSolvers/blob/master/docs/Part5-Conclusion.md)

