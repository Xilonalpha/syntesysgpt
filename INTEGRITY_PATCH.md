# Scientific integrity containment patch

Not a validated science release. Legacy Pure TIC and exoplanet cache reads quarantined; old data retained. Synthetic scout and curated Deep Hunt disabled; manual archived TCE inspection remains limited. No independent TLS or FFI search implemented. Existing research/discovery exports and other engines require further audit. Android build not verified.

Modified files:
- app/src/main/java/com/synthesis/data/repository/xilon/PureTicCatalogRepository.kt
- app/src/main/java/com/synthesis/data/repository/xilon/XilonRepository.kt
- app/src/main/java/com/synthesis/service/worker/TessMastPollingWorker.kt
- app/src/main/java/com/synthesis/viewmodel/xilon/XilonViewModel.kt
- app/src/main/java/com/synthesis/ui/components/exoplanet/Tic167418703RechartsView.kt
- app/src/main/java/com/synthesis/ui/components/exoplanet/Tic141913165RechartsView.kt
- app/src/main/java/com/synthesis/data/repository/exoplanet/UnvettedHuntRepository.kt
- app/src/main/java/com/synthesis/service/FitsPdfReportGenerator.kt
- app/src/main/java/com/synthesis/service/ExtraterrestrialLifeService.kt
- app/src/main/java/com/synthesis/data/service/gemini/GeminiAstrophysicsService.kt
- app/src/main/java/com/synthesis/engine/validation/TessHeasarcValidationEngine.kt
- app/src/main/java/com/synthesis/ui/screens/xilon/XilonDiscoveryScreen.kt
- app/src/main/java/com/synthesis/data/repository/exoplanet/TessArchiveSurveyRepository.kt
- app/src/main/java/com/synthesis/data/repository/SynthesisRepository.kt

Additional containment:
- app/src/main/java/com/synthesis/data/repository/exoplanet/AdvancedDiscoveryRepository.kt
- app/src/main/java/com/synthesis/engine/xilon/XilonTransitDetector.kt
- app/src/main/java/com/synthesis/viewmodel/xilon/XilonViewModel.kt
- app/src/main/java/com/synthesis/ui/screens/xilon/XilonDiscoveryScreen.kt
- app/src/main/java/com/synthesis/engine/exoplanet/ExoplanetCandidateFinder.kt
- app/src/main/java/com/synthesis/service/AgencyReportProtocolService.kt
- app/src/main/java/com/synthesis/service/NasaExoFopExporter.kt
- app/src/main/java/com/synthesis/data/repository/xilon/XilonRepository.kt
- app/src/main/java/com/synthesis/service/FitsPdfReportGenerator.kt
- app/src/main/java/com/synthesis/ui/components/exoplanet/Tic167418703RechartsView.kt
- app/src/main/java/com/synthesis/ui/components/exoplanet/Tic141913165RechartsView.kt

Formal agency exports and scientific PDF exports are blocked. Advanced curated datasets are hidden pending provenance review. Old research records may still exist elsewhere; do not use for submissions. Build attempt: GradleWrapperMain missing from supplied wrapper JAR.
