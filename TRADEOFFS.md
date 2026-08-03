# Technical Tradeoffs and Design Decisions

## Language Choice: Java

I chose Java for its strong compile-time type safety and ecosystem. The static type system catches errors during compilation, preventing them from causing run-time crashes.
The JVM provides excellent memory management, high performance and tuning/optimization capabilities. 

On the other hand, Java is verbose. Compared to modern options, Java requires the more lines of code for the same functionality. Although, the use of the Springboot framework and modern language features like records have reduced the amount of boilerplate to be reasoned.

## Architecture: Database-backed asynchronous processing

I designed the system to be asynchronous because the job results are not needed immediately, hence, they can be executed later on when their scheduled times arrive. The client is notified of the job's acceptance via a return of the submitted job's id.

Down the line, jobs are executed using virtual threads. I chose virtual threads over fixed thread pools because they allow for the execution of a large number of blocking tasks without needing a huge number of OS threads. Submitted jobs are blocking http requests and not CPU bound tasks which makes virtual threads the perfect choice. 

I chose to use a database store to provide a persistent source of truth for jobs no matter how far or near their execution times may be.

For the sake of simplicity, this system is a single node system with no distributed capabilities. 

Also, the Scheduler, DependencyGraph, IndexedPriorityQueue and JobDispatcher are all singletons. 

## Priority Scheduling: Indexed Priority Queue

I chose the Indexed Priority Queue for my scheduling data structure out of others like Timing wheels. This is because indexed priority queues allow for the comparison of other factors except scheduling time, of which timing wheels offer solely. Java has an in-built PriorityQueue data structure, but it is not indexed and has no capability for reclassifying jobs with evolved effective priorities. Hence, lookups and reclassification would be O(n) and O(nlog(n)) respectively, at best.

To tackle this, a custom Indexed Priority Queue was chosen. This was designed to have a custom reclassifyAll() method which runs every 30 seconds. This method uses a bottom-up heap construction approach which makes reclassification O(n).

## DAG Resolution: Hybrid Database and In-memory Approach

A hybrid approach of using the Database and in-memory data structures for the DAG was chosen. The database was used as the source of truth from where the in-memory data structures obtained pending jobs and their dependencies. The in-memory structures provide fast dependency resolution in the system. Upon restarting the system, the in-memory DAG stores are rebuilt by incoming jobs polled by the scheduler from the database. Another upside to this design is that the in-memory only stores dependency information about jobs that are due (obtained via scheduler polling) and outgoing jobs from the DAG trigger a clean-up of its metadata, thereby making efficient use of the system memory.

## Solving Starvation

To solve starvation, each job instance has a non-persisted field called effective priority which is computed from the priority, scheduled time and created time of the job. Every 30 seconds, the computing method is called and the effective priority field is reassigned. This computation is not blocking and is fairly trivial, so an O(n) operation for thousands of jobs is deemed to perform well. The indexed priority queue calls this method and then reclassifies jobs in the queue. That is how starvation is handled.