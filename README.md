# Scala Sudoku Solvers
### Author: Joseph Sackett

Currently there are two versions of the solver:
1. SudokuSerial - single-threaded version using nested lists to hold intermediate candidate values
2. SudokuSerialArray - single-threaded version using an array to hold candidate values
  
The installation and execution instructions are very simple and this documentation is located in the project directories just below here.  
The two versions have the same interface and high-level solution algorithm but differ by the main data structure holding the candidate values. This single difference significantly affects the elimination logic applied to it.  
Writing these two versions six years apart was an intriguing exercise, as is analyzing the code differences and performance characteristics between them. I will document this analysis going forward.

