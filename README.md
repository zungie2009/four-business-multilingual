# OOP Blueprint Script Runtime (Multi-Business Application)

A lightweight, self-contained Java 21 web application built according to the **LLM Object-Oriented Blueprint Construction Contract V1**. 

The runtime hosts four fully isolated, polymorphic business applications within a single process—**Consulting**, **Property Management**, **Restaurant**, and **Marketplace**—using zero external framework or database dependencies.

---

## Architecture Overview

* **Polymorphic Blueprint Dispatch:** Each business (Robert, Sofia, Marie, Hans) is driven by its own immutable `BaseBlueprint` concrete implementation (`ConsultingBlueprint`, `PropertyBlueprint`, etc.) controlling menu navigation, schema definitions, and locale handling.
* **Encapsulated Multi-Tenancy:** Each user partition is strictly isolated. Data for one tenant cannot leak into or be queried by another.
* **Pure File-System Persistence:** Records are serialized to plain JSON files directly on disk under `./data/{userId}/{entityId}/`. No SQL drivers or external database binaries are required.
* **Zero External Dependencies:** Native Java 21 standard library execution (`com.sun.net.httpserver` HTTP server, built-in file I/O).

---

## Directory Structure

```text
.
├── src/
│   └── org/
│       └── roberttu/
│           └── Runner.java       # Core application, HTTP server, and domain blueprints
├── bin/                           # Compiled byte-code classes (generated at build)
├── data/                          # Isolated JSON persistence partitions (generated at runtime)
└── README.md
