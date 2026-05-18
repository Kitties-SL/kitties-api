package es.kitti.formanalysis.service;

import io.smallrye.mutiny.Uni;

public interface LlmTextAnalysisClient {

    Uni<String> analyzeTextFields(String prompt);
}
