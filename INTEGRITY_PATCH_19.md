# Patch 19 — discovery and commercial assessment quarantine
Removed uncalibrated pattern/discovery generation and patent, market and impact scoring. Public assessment methods return null (unavailable); pattern and discovery lists are empty. Investigation logs distinguish unavailable assessment from zero discoveries. Repository hides legacy discovery records without deleting them and rejects new writes pending evidence validation. This does not migrate historical records or validate other repositories, cloud sync, or astronomical engines.

Standalone JVM regression: five checks passed, using actual engine and domain sources, coroutine library and temporary injection/repository stubs. Android build not executed. These checks verify quarantine, not scientific detection capability.
