package com.sajuppugi.fortune.generation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import org.springframework.stereotype.Component;

@Component
public class LinerFactProjector {
    private final ObjectMapper mapper;

    public LinerFactProjector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode project(CalculationFacts facts) {
        ObjectNode safe = mapper.createObjectNode();
        safe.set("pillars", mapper.valueToTree(facts.pillars()));
        safe.set("dayMaster", mapper.valueToTree(facts.dayMaster()));
        safe.set("fiveElements", mapper.valueToTree(facts.fiveElements()));
        safe.set("tenGods", mapper.valueToTree(facts.tenGods()));
        safe.set("twelveStages", mapper.valueToTree(facts.twelveStages()));
        safe.set("relations", mapper.valueToTree(facts.relations()));
        safe.set("luck", mapper.valueToTree(facts.luck()));
        return safe;
    }
}
