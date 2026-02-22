# Firefighting Drone Simulation

## Overview
This project simulates a **firefighting system using autonomous drones** coordinated by a central scheduler. Fire incidents occur over a defined time from an events sheet, drones are dispatched based on priority, and fires may require multiple drones to fully extinguish.

## Main Systems

### FireIncidentSubsystem
- Reads fire events from an input file
- Triggers fire incidents at the correct simulation time
- Sends events to the `Scheduler`
  
### Scheduler
- Central coordinator; maintains 3 **priority queues** (`HIGH`, `MODERATE`, `LOW`)
- State machine: IDLE (no incidents) → DISPATCHING (assigning missions) → MONITORING (tracking active drones) → REFILLING / FAULT_HANDLING as needed.
- Assigns fire missions to drones
- Reschedules partially extinguished fires and tracks per‑zone water remaining.
- Synchronizes drone threads via wait() / notifyAll().

### DroneSubsystem
- Each drone runs in its own thread.
- Explicit state machine: IDLE, ONROUTE, EXTINGUISHING, REFILLING, FAULTED, DECOMMISSIONED.
- Requests missions from scheduler when idle.
- Simulates travel cell‑by‑cell across the grid; arrival triggers extinguishing.
- Extinguishes fire with nozzle open/close delays and water flow rate; water depletes.
- Returns to base and refills when empty; informs scheduler of all state transitions.

### SimulationClock
- Provides a shared notion of simulation time
- Keeps all subsystems synchronized

### FireEvent
- Represents a fire incident
- Tracks severity, required water, and remaining fire


## Key Features

- Priority-based fire scheduling
- Multi-drone coordination
- Partial fire extinguishing with rescheduling
- Centralized thread synchronization
- Simulated time progression
- Water capacity constraints and refilling


## How to Run Application

1. Run the Main class
2. Observe simulation output in the console
3. Kill the threads manually after it has finished

Ensure the csv event file is in the src folder.


## How To Run Tests
All unit tests relevant to iteration 2are located in the `test/Iteration 2/` directory. The tests verify the behavior of the Scheduler, DroneSubsystem, and FireEvent classes, including priority handling, partial mission assignment, and state transitions.

#### Run all tests in the `Iteration 2` package:
1. In the **Project** tool window, navigate to `test/Iteration 2`.
2. Right‑click on the package (or on any test class) and select **Run 'Tests in 'Iteration 2''** (or **Run All Tests**).

#### Run a single test class:
- Open the desired test class (e.g., `SchedulerTest2.java`).
- Click the green triangle in the gutter next to the class declaration or any individual test method, then select **Run 'SchedulerTest'**.
  

## Authors
### 1. Aryan Kumar Singh (101299776)
  #### Iteration 1
  - Worked on the implementation of all 9 classes including the GUI
  - Worked on the Javadoc documentation
  #### Iteration 2
  - Improved GUI for clearly tracking of the fire and drone states with colour, animation, and a table.
  - Integrated a state machine design for scheduler and drones (i.e. classic switch case's implementation)
  - Worked on some Javadoc documentation
    
### 2. Kevin Abeykoon (101301971)
- Worked on the implementation of Drone State, DroneSubsystem, FireEvent, and Scheduler
- Worked on Javadoc documentation for all classes and README
   
### 3. Rayyan Kashif (101274266)
- Worked on the implementation of FireEventSubsystem
- Worked on UML Class Diagrams
  
### 4. Abdullah Khan (101305235)
- Worked on the implementation of DroneSubsystem
- Worked on UML Sequence Diagrams
