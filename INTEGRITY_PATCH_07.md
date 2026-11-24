# Patch 07
- Removed four hardcoded research seeds and the UI regeneration action.
- All six explicit verified assignments removed; repository insert/update paths force unverified.
- Room migration 7 to 8 clears historical verification flags and deletes only the four known seed IDs. No destructive migration fallback. Older schemas without migrations may fail to open; no automatic deletion.
- Manual research entries no longer invent confidence or three sources. JSON uses Gson escaping. Archive cards and clipboard no longer advertise unverified confidence.
- FITS permanent persistence and linkage to research records remain pending.
- Android build and Room instrumentation not run. SQL migration checked using SQLite fixture only.
