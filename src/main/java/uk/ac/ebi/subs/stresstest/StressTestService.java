package uk.ac.ebi.subs.stresstest;

import java.nio.file.Path;

public interface StressTestService {

    void submitJsonInDir(Path path);
}
