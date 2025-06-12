import com.project.tracking_system.utils.DateParserUtils;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class DateParserUtilsTest {

    @Test
    void parse_BelpostFormat() {
        ZoneId zone = ZoneId.of("Europe/Minsk");
        String raw = "01.02.2024, 10:15";

        ZonedDateTime result = DateParserUtils.parse(raw, zone);

        ZonedDateTime expected = ZonedDateTime.of(2024,2,1,10,15,0,0, zone)
                .withZoneSameInstant(ZoneOffset.UTC);
        assertEquals(expected, result);
    }

    @Test
    void parse_EvropostFormat() {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        String raw = "02.03.2024 14:05:30";

        ZonedDateTime result = DateParserUtils.parse(raw, zone);

        ZonedDateTime expected = ZonedDateTime.of(2024,3,2,14,5,30,0, zone)
                .withZoneSameInstant(ZoneOffset.UTC);
        assertEquals(expected, result);
    }

    @Test
    void parse_InvalidFormat_Throws() {
        ZoneId zone = ZoneId.of("UTC");
        String raw = "invalid";

        assertThrows(java.time.format.DateTimeParseException.class, () -> DateParserUtils.parse(raw, zone));
    }
}
