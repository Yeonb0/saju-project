package com.sajuppugi.fortune.generation.port;

import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;

public interface LinerProvider {
    String name();
    LinerResponse generate(LinerRequest request);
}
