# SLA-Aware Priority Queuing Architecture

## 🎯 Objective
To ensure that critical, user-facing inference requests are processed with strict latency guarantees, while allowing background tasks to utilize spare GPU capacity without causing starvation or SLA violations.

## 📐 Design: Earliest Deadline First (EDF)
Instead of a rigid Priority Queue (which can lead to absolute starvation of low-priority tasks), Velo-Sentinel implements an **SLA-aware queue** utilizing the **Earliest Deadline First (EDF)** scheduling algorithm within its `AdaptiveBatcher`.

### 1. Priority Tiers & SLAs
We have introduced a `PriorityTier` enum with predefined SLA timeouts:
- **`REALTIME`**: SLA = 100ms (e.g., synchronous user UI actions)
- **`INTERACTIVE`**: SLA = 500ms (e.g., chat completions, live analysis)
- **`BACKGROUND`**: SLA = 5000ms (e.g., bulk processing, report generation)

### 2. Task Deadlines
When a request enters the `AdaptiveBatcher`, it computes a strict `deadline`:
```java
long deadline = System.currentTimeMillis() + priorityTier.getSlaMs();
```

### 3. Queue Structure
The `AdaptiveBatcher` utilizes a `PriorityBlockingQueue<InferenceTask>`.
- The queue automatically sorts tasks based on their `deadline` (ascending order).
- **Why this works**: 
  - A `REALTIME` task submitted now has a deadline of `now + 100ms`.
  - A `BACKGROUND` task submitted now has a deadline of `now + 5000ms`.
  - The `REALTIME` task is processed first.
  - *Starvation Prevention*: If a `BACKGROUND` task sits in the queue for 4950ms, its deadline is now only 50ms away. If a *new* `REALTIME` task arrives, its deadline is 100ms away. The queue intelligently prioritizes the ancient `BACKGROUND` task to prevent it from violating its 5000ms SLA!

### 4. SLA Veto (Load Shedding)
If the queue is severely backed up and a task is pulled off the queue *after* its deadline has already passed, the batcher will **VETO (drop)** the request immediately with a `TimeoutException`. This prevents the gateway from wasting expensive GPU cycles computing a result the client no longer cares about.

## 🛠️ Integration
The `PriorityTier` is accepted as an optional field in the `InferenceRequest` REST payload. If omitted, the gateway defaults to `INTERACTIVE` for backwards compatibility with legacy API consumers.
