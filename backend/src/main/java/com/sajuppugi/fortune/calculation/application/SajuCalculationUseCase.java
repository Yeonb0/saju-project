package com.sajuppugi.fortune.calculation.application;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;

public interface SajuCalculationUseCase {
    CalculationFacts calculate(BirthInput input, CalculationPolicy policy);
}
