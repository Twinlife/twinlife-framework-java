Coding rules for the twinlife-framework:

## 1. General Principles

- Read the top-level `CODING_GUIDELINES.md` and follow the `General Principles`.
- The module must only contain packages for `org.twinlife.twinlife` and its children.

## 2. Twinlife Services

Services in `org.twinlife.twinlife` package should follow the following rules:

- a service is declared as an interface and it should inherit from `BaseService` interface,
- if it defines an observer, it should inherit from `BaseService.ServiceObserver`,
- the service observer methods are always called from the twinlife executor thread,
- the service implementation class must not be exposed or used by classes outside
  of `org.twinlife.twinlife` package
- a service method must never be called from the main UI thread (unless there is
  a strong reason to bypass this rule, exceptions must be documented),

## 3. Database access

- the database access is internal to the service and direct access outside of
  the service should be avoided.
