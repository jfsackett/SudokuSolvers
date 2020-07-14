# Scala Sudoku - Conclusion 

This is the final of five articles documenting my experience using Scala and Functional Programming (FP) to develop algorithms over complex data structures. It analyzed the development complexity of using this paradigm and compared it to the more familiar imperative idiom. This series covered a specific case study of initially programming, later understanding, and extending Sudoku puzzle solving algorithms.  
Toward the goal of measuring how applicable Scala and FP are for multi-developer software construction I addressed three questions:
1. How easy is it to initially develop algorithms over complex data structures?
2. How easily can the program design and source code be understood by other developers?
3. Is the design extensible and code maintainable over time?

For each module in the Sudoku solver code, I worked to answer these questions by presenting and describing the design/source code. I ranked their respective development complexity as either **Trivial, Simple, Medium, Complicated, or Hard**.  
The goal was to analyze how much more difficult the FP/Scala programmer load is than traditional approaches. This type of analysis is important when considering adopting a paradigm/language for building large systems due to high software development and maintenance costs.  
The case study is a light, anecdotal pass over this important question of whether FP is a good choice for large projects. It illuminates a few issues which merit further, more complete, research. This final article in the series will collect all of the ranking data. It will attempt an objective analysis of its trends as well as introduce subjective interpretations based on my extensive software development experience and more recent exposure to FP and Scala.

## Development Complexity Rankings
Below are all of the module ratings collected from the prior four blog posts comprising this series; one in each row. The three different rankings associate to different types of programmer interaction with software design and source code. Initial development naturally involves taking specifications and building working artifacts. Follow-on understanding is how difficult it is for later developers to interpret these artifacts. Extensibility is the ease with which changes or new functionality can be added to the existing design and source code.
| Module | Initial Development | Follow-on Understanding | Extensibility |
| --- | --- | --- | --- |
| Main Elimination Loop | Medium | Simple | Simple -|
| Array Access Indices | Simple | Medium | Trivial -|
| Find Initial Candidates | Medium | Complicated | Simple -|
| Scrub Candidates | Simple | Simple | Medium |
| Singles & Uniques | Medium | Medium | Simple |
| Block Constraints | Complicated | Hard | Complicated |
| Matching Open Tuples | Simple | Simple | Trivial |
| Hidden Tuples | Medium | Medium | Medium |
| Candidate State Validity | Simple | Trivial | Simple |
| Solution Search | Simple | Medium | Simple |

### Complexity Rating Trend Analysis
Initial FP/Scala development averages to Simple-Medium complexity. I estimate this as the same as an imperative version of these same module algorithms using nested loops and indexed data structure access/update. It is encouraging that the paradigm does not appear to introduce development complexity at the beginning.  
Almost all modules are either the same or more difficult to understand than to initially develop. Ignoring the two counter examples for now, this trend is unfortunate. In my experience, imperative and object-oriented designs and source code are almost always easier to understand than build from scratch. This is analogous to writing vs. reading prose: reading is simpler. The code is fully commented so this is not the cause. I believe this different complexity slope comes from the fact that FP design and source do not closely model the problem domain. Imperative code is typically a one-to-one port from the general solution algorithm and thus straightforward to understand. Object-oriented design decomposes the domain into entities collaborating to provide the solution. These familiar objects and interaction patterns provide understandable designs/source. FP provides major advantages in allowing very dense code and new types of design reuse, however, its distance from the domain makes it more difficult to understand than its peer paradigms.  
What about the two counter examples where understanding rating decreased from initial development complexity? The second, Candidate State Validity, was almost trivial code to build and thus almost flat complexity slope. The first, Main Elimination Loop, is not functional code but rather a series of nested loops and assignments. It made more sense to build this similar to the general main loop algorithm than try to contort it into FP constructs. So this example supports the hypothesis that imperative code is easier to understand than build.  
Extensibility was easier than initial development on average. Five modules were simpler, four were flat and one was more difficult. This is encouraging as anecdotal evidence that functional designs are extensible. Evolving software to meet changing requirements is critical. It can become expensive and error-prone if the design does not support it. The object-oriented paradigm excels at being extensible due to the design closely modeling the problem domain.

### Complexity Rating Outliers
Manually solving Sudoku puzzles using these same elimination algorithms may be time-consuming for a human but they are simple to use. One might expect the programming complexity of building these algorithms to align with this but this was not always the case. Finding Initial Candidates and Eliminating Block Constraints stand out as difficult code to build and understand. Both of these modules have to contort the blocks into row form for different purposes. These algorithms require more of a random-access type operation than a linear one. It is somewhat unnatural to solve this with a functional design. Furthermore, extending the modules to use the array data structure required a redesign. Both relied on the new array access indices which introduced a level of indirection. Extending the Eliminating Block Constraints design used very different, imperative-type code.  
While most of the development complexity aligned with the simple problem domain, it was not universal. This may indicate some inflexibility in the paradigm leading to more complexity implementing certain types of algorithms.

## Experimental Runtime Performance
A lesser goal of building these versions was to see whether a different candidate data structure would speed up the solver. As shown in the parallel code review, with a few exceptions, both versions implement the same algorithm. The difference is in the candidate structure itself and its access and update mechanisms. The list version uses library functions for linear access and necessarily avoids mutation for updates. Instead, it wraps unchanged cells around the new instances of changed cells. The array version allows random-access updates directly into cells of a matrix (i.e., array of arrays).  
The list version must allocate a great number of small objects over the execution of the algorithm. It is possible that this memory management overhead becomes a drain on runtime performance. The indexed access to the array cells vs. list scans could also improve performance. Since both versions execute the same algorithm, runtime variation is likely attributable to these differences. Does relaxing the functional paradigm improve performance? This is an interesting question illuminated by testing and comparing these versions but not the main point of this series.  
Here are the results:  
List Version in Puzzles / Sec
| Difficulty / Sample Set Size | EC2 micro | EC2 t3.xlarge | EC2 c5.2xlarge | Laptop |
| --- | --- | --- | --- | --- |
| Easy / 17445 puzzles | 147.71 | 162.97 | 163.51 | 200.00 |
| Medium / 1011 puzzles | 57.44 | 73.52 | 69.63 | 90.78 |
| Hard / 95 puzzles | 12.27 | 19.62 | 20.13 | 23.19 |

Array Version in Puzzles / Sec
| Difficulty / Sample Set Size | EC2 micro | EC2 t3.xlarge | EC2 c5.2xlarge | Laptop |
| --- | --- | --- | --- | --- |
| Easy / 17445 puzzles | 174.22 | 210.44 | 212.22 | 250.75 |
| Medium / 1011 puzzles | 62.89 | 96.54 | 100.00 | 112.23 |
| Hard / 95 puzzles | 13.04 | 26.48 | 28.01 | 29.52 |

I input three puzzle sets, easy, medium, and hard. They varied by numbers and difficulty of contained puzzles. I tested on four different machine configurations. Three were AWS EC2 architectures and one was a laptop. I ran five tests for each combination and normalized results to the average number of Sudoku puzzles solved per second. The top table displays results for the list version and the bottom table is the array version.  
I saw a 10-20% empirical runtime performance improvement across the different platforms. This was a small test and not very  significant but fortunately the numbers improved after doing the work.  ;-)

## Conclusion
Are FP and Scala a good paradigm and language for large, multi-developer projects? How complex is it to build, understand, and extend algorithms over complex data structures? My series of articles analyzed the source code for a Sudoku solver as a case study of such a project. It was manageable in size and representative in difficulty.  
My anecdotal conclusion is that FP/Scala does not significantly change the development complexity profile. However, there were two troubling observations:
1. On average, functional source code was more difficult to understand than to initially develop. This is the opposite of my experience that reading code is almost always easier than programming it.
2. Algorithms lending themselves to random-access are complex to build and even more so to understand and extend later using FP. Due to the matrix puzzle domain, most elimination algorithms were scans along rows, columns, and blocks. However, the two cases of requiring more direct access to the deeply nested immutable data structure made a simple task complex to build.

Both of these are explainable as aspects of the FP paradigm. Using higher-order language features will yield designs and source which take more time to interpret than familiar loops and assignments. Even object-oriented constructs such as classes, inheritance, and polymorphism are understandable due to their similarity to the problem domain. The second issue is explainable since the  immutability and the list-oriented data structures and library functions common in FP confound random-access reads and updates.  
Since Scala is not a pure functional programming language, the enhanced version was able to loosen the FP restrictions to provide  indexed access to the candidate structure. It utilized nested mutable arrays (2D matrix) instead of lists. Would this more flexible data structure increase performance? There was an incremental speed improvement but not significant. This is expected since both use the the same fundamental solver algorithms and thus have equivalent runtime complexity.

## Next Steps
The above illuminates a few issues arising from the development complexity associated with Functional Programming. Further work could generalize this. This would involve a thorough related work search and a scientific comparison of development complexity slope of a control algorithm implemented in multiple programming paradigms. Beyond simply measuring, one could test novel ideas for changing the slope through all types of mechanisms from advanced documentation, design patterns, or even language extensions. The goal is to keep the advantages of afforded by the different programming paradigms while reducing their additional development complexity overhead.

