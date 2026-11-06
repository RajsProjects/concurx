# ConcurX

## Concurrent Task Execution & Processing Engine

**Version:** 1.0
**Status:** Initial Architecture
**Language:** Java 21
**Framework:** Spring Boot 4.1.2
**Build Tool:** Maven
**Architecture:** Feature-Oriented Modular Monolith

---

# 1. Project Overview

ConcurX is a concurrent task execution and processing engine designed to accept, schedule, execute, monitor, and recover background tasks.

The system will use Java concurrency mechanisms to manage multiple tasks efficiently while maintaining thread safety, execution control, and resource management.

Spring Boot will provide the application infrastructure and REST API layer, while the core execution engine will remain independent of Spring.

## 1.1 Primary Objectives

* Understand and implement Java concurrency.
* Build a custom task execution engine.
* Manage worker threads and execution resources.
* Implement thread-safe task scheduling.
* Handle task failures and retries.
* Integrate the engine with Spring Boot.
* Measure execution performance.
* Explore distributed execution.

# 2. Architectural Style

ConcurX will follow a Feature-Oriented Architecture within a Modular Monolith.

Each feature represents a distinct application capability.

The application will initially run as a single deployable instance.

## Architectural Principles

1. Feature-oriented organization.
2. Separation of concerns.
3. Loose coupling between features.
4. Explicit component responsibilities.
5. Thread-safe shared state.
6. Independent testing of core components.
7. Functional programming principles where appropriate.
8. Incremental architectural evolution.

# 3. High-Level Architecture

```text
                       CONCURX
                          |
                +---------+---------+
                |                   |
           TASK FEATURE       EXECUTION FEATURE
                |                   |
         Task Submission      Execution Engine
                |                   |
         Task Validation      Worker Manager
                |                   |
         Task Management      Thread Pool
                |                   |
                +--------+----------+
                         |
                    SCHEDULING
                         |
                     Task Queue
                         |
                  Worker Execution
                         |
              +----------+----------+
              |                     |
          MONITORING             RECOVERY
              |                     |
          Task Metrics          Retry Handling
          Execution Logs        Failed Tasks
          Task Status           Recovery
```

# 4. Feature Responsibilities

## 4.1 Task Feature

Responsible for managing the lifecycle of submitted tasks.

Components:

* Task Submission
* Task Validation
* Task Management
* Task Model

Responsibilities:

* Accept task requests.
* Validate task configurations.
* Generate unique task identifiers.
* Maintain task metadata.
* Manage task lifecycle transitions.

The Task feature must not directly manage worker threads.

## 4.2 Execution Feature

Responsible for executing tasks concurrently.

Components:

* Execution Engine
* Worker Manager
* Thread Pool

Responsibilities:

* Coordinate task execution.
* Manage worker lifecycle.
* Control concurrent execution.
* Handle task execution results.
* Support graceful shutdown.

The execution implementation should remain independent of Spring Boot.

## 4.3 Scheduling Feature

Responsible for determining when tasks should execute.

Components:

* Task Scheduler
* Task Queue
* Scheduling Policies

Responsibilities:

* Manage pending tasks.
* Support execution priorities.
* Determine task execution eligibility.
* Coordinate task dispatching.

The queue must support concurrent producers and consumers.

## 4.4 Monitoring Feature

Responsible for observing task execution and system performance.

Components:

* Task Metrics
* Execution Logs
* Task Status

Responsibilities:

* Track submitted tasks.
* Track completed and failed tasks.
* Measure execution duration.
* Monitor worker utilization.
* Expose execution metrics.

## 4.5 Recovery Feature

Responsible for handling execution failures.

Components:

* Retry Handler
* Failed Task Management
* Recovery Coordinator

Responsibilities:

* Identify failed executions.
* Apply retry policies.
* Track retry attempts.
* Handle exhausted retries.
* Recover interrupted tasks.

Recovery must account for application crashes and duplicate execution risks.

# 5. Task Execution Lifecycle

Initial task lifecycle:

```text
       SUBMITTED
           |
           v
        VALIDATED
           |
           v
        QUEUED
           |
           v
       SCHEDULED
           |
           v
        RUNNING
           |
       +---+---+
       |       |
       v       v
   COMPLETED  FAILED
                 |
                 v
              RETRYING
                 |
           +-----+-----+
           |           |
           v           v
       COMPLETED    FAILED
                    (Exhausted)
```

Additional states may include:

* CANCELLED
* TIMED_OUT

State transitions must be controlled to prevent invalid concurrent updates.

# 6. Threading Model

Java concurrency is the central engineering focus of ConcurX.

The initial implementation will use:

* ExecutorService
* ThreadPoolExecutor
* BlockingQueue
* Runnable
* Callable
* Future
* CompletableFuture
* AtomicInteger
* ConcurrentHashMap
* ReentrantLock
* Semaphore
* CountDownLatch

Java 21 virtual threads will be evaluated separately.

## 6.1 Initial Execution Model

```text
          Task Producers
                |
                v
          BlockingQueue
                |
       +--------+--------+
       |        |        |
       v        v        v
    Worker 1 Worker 2 Worker N
       |        |        |
       +--------+--------+
                |
                v
        Execution Results
```

The initial worker pool will have configurable concurrency limits.

Queue capacity will also be configurable.

## 6.2 Concurrency Requirements

The engine must address:

* Race conditions.
* Memory visibility.
* Thread safety.
* Deadlocks.
* Thread starvation.
* Task cancellation.
* Thread interruption.
* Backpressure.
* Graceful shutdown.

# 7. Communication Between Features

Features must communicate through defined contracts.

For example:

Task Submission must not directly access ThreadPoolExecutor.

Instead:

```text
Task Submission
       |
       v
ExecutionEngine Interface
       |
       v
Execution Implementation
       |
       v
Worker Pool
```

This hides execution implementation details from other features.

Feature boundaries must remain explicit.

# 8. Technology Stack

| Technology        | Purpose                         |
| ----------------- | ------------------------------- |
| Java 21           | Core language and concurrency   |
| Spring Boot 4.1.2 | Application framework           |
| Spring MVC        | REST APIs                       |
| Maven             | Build management                |
| PostgreSQL        | Persistent task storage         |
| Redis             | Future distributed coordination |
| JUnit 5           | Testing                         |
| Mockito           | Mocking                         |
| Docker            | Containerization                |
| Prometheus        | Metrics                         |
| Grafana           | Visualization                   |

# 9. Non-Functional Requirements

## Performance

* Configurable worker count.
* Bounded task queue.
* Controlled concurrent execution.
* Performance benchmarking.

## Reliability

* Controlled task lifecycle.
* Failure handling.
* Retry policies.
* Graceful shutdown.
* Persistent recovery in later phases.

## Maintainability

* Feature-oriented packages.
* Explicit interfaces.
* Independently testable components.
* Minimal unnecessary abstractions.

## Observability

* Execution metrics.
* Task status tracking.
* Error logging.
* Worker utilization monitoring.

# 10. Initial Package Structure

```text
dev.concurx
│
├── ConcurXApplication.java
│
├── task
│   ├── submission
│   ├── management
│   └── model
│
├── execution
│   ├── engine
│   ├── worker
│   └── pool
│
├── scheduling
│
├── monitoring
│
├── recovery
│
├── api
│
└── shared
```

Packages will be introduced incrementally as implementation requirements emerge.

# 11. Development Phases

## Phase 1: Core Java Execution Engine

* Task abstraction.
* Runnable and Callable.
* ExecutorService.
* Custom ThreadPoolExecutor.
* BlockingQueue.
* Task execution.
* Graceful shutdown.

## Phase 2: Advanced Concurrency

* Synchronization.
* Atomic operations.
* Locks.
* Semaphore.
* Producer-consumer architecture.
* CompletableFuture.
* Deadlock prevention.
* Virtual threads.

## Phase 3: Spring Boot Integration

* REST APIs.
* Dependency injection.
* Configuration management.
* Validation.
* Exception handling.
* Application lifecycle.

## Phase 4: Persistence and Reliability

* PostgreSQL integration.
* Task persistence.
* Execution history.
* Retry mechanisms.
* Recovery after restart.
* Transaction management.

## Phase 5: Distributed Execution

* Redis.
* Multiple application instances.
* Distributed coordination.
* Kafka or RabbitMQ.
* Duplicate delivery handling.

## Phase 6: Performance Engineering

* Load testing.
* Throughput measurement.
* Latency measurement.
* Memory profiling.
* Platform threads versus virtual threads.
* Worker pool optimization.

# 12. Architectural Decisions

| ID      | Decision                      | Reason                                   |
| ------- | ----------------------------- | ---------------------------------------- |
| ADR-001 | Feature-Oriented Architecture | Organize around application capabilities |
| ADR-002 | Modular Monolith              | Avoid premature distributed complexity   |
| ADR-003 | Java 21                       | Learn modern Java concurrency            |
| ADR-004 | Spring Boot 4.1.2             | Backend application framework            |
| ADR-005 | Independent Core Engine       | Test concurrency without Spring          |
| ADR-006 | Bounded Task Queue            | Control resource consumption             |
| ADR-007 | Interface-Based Communication | Reduce coupling between features         |

# 13. Initial Scope and Limitations

The first version will:

* Run as a single application instance.
* Execute tasks using a configurable worker pool.
* Maintain an in-memory execution queue.
* Support task lifecycle tracking.
* Handle execution failures.

The first version will NOT attempt to provide:

* Distributed execution.
* Guaranteed exactly-once execution.
* Multi-node scheduling.
* Kubernetes orchestration.
* Production-grade durable messaging.

These capabilities will be evaluated in later phases.

---

# 14. Project Success Criteria

ConcurX will be considered technically successful when:

1. Multiple tasks can execute concurrently.
2. Worker count is configurable.
3. Queue capacity is bounded.
4. Shared state is protected against race conditions.
5. Task execution failures are handled.
6. Shutdown does not silently abandon accepted work.
7. Execution metrics can be measured.
8. Concurrency behavior is covered by automated tests.
9. Performance claims are supported by reproducible benchmarks.
10. Architectural decisions can be explained and justified.

**Project Philosophy:**

Build for correctness first, performance second, and distributed scalability third.
