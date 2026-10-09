package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.application.BirthInputNormalizer;
import com.sajuppugi.fortune.calculation.application.SajuCalculationUseCase;
import com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType;
import com.sajuppugi.fortune.calculation.domain.BirthInput.Gender;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine.UnsupportedUnknownBirthTimeException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BasicSajuService {
    private final BirthInputNormalizer normalizer;
    private final SajuCalculationUseCase calculation;

    public BasicSajuService(BirthInputNormalizer normalizer, SajuCalculationUseCase calculation) {
        this.normalizer = normalizer;
        this.calculation = calculation;
    }

    public BasicSajuResult calculate(UUID requesterUserId, BasicSajuCommand command) {
        Objects.requireNonNull(requesterUserId, "requesterUserId");
        try {
            CalculationFacts facts = calculation.calculate(normalizer.normalize(
                    command.birthDate(), command.birthTime(), command.birthTimeUnknown(),
                    command.calendarType(), command.leapMonth(), command.gender()), CalculationPolicy.CURRENT);
            return new BasicSajuResult(facts.pillars(), facts.dayMaster(), facts.fiveElements(), facts.tenGods(),
                    facts.twelveStages(), facts.relations(), facts.meta().calculationVersion(),
                    facts.meta().birthTimeKnown());
        } catch (UnsupportedUnknownBirthTimeException exception) {
            throw new ApiException(ErrorCode.BIRTH_TIME_REQUIRED_AT_TERM);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNSUPPORTED_CALENDAR_DATE);
        }
    }

    public record BasicSajuCommand(
            String birthDate,
            String birthTime,
            boolean birthTimeUnknown,
            CalendarType calendarType,
            boolean leapMonth,
            Gender gender) {}

    public record BasicSajuResult(
            CalculationFacts.Pillars pillars,
            CalculationFacts.Stem dayMaster,
            CalculationFacts.FiveElements fiveElements,
            CalculationFacts.TenGods tenGods,
            CalculationFacts.TwelveStages twelveStages,
            CalculationFacts.Relations relations,
            String calculationVersion,
            boolean birthTimeKnown) {}
}
