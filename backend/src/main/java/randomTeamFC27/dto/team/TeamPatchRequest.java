package randomTeamFC27.dto.team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * All fields optional — {@code null} means "leave unchanged". Present-but-blank strings
 * are still validated normally where a constraint applies.
 */
public record TeamPatchRequest(
        @Size(max = 120) String name,
        @Pattern(regexp = "[A-Za-z0-9]{1,5}", message = "1-5 harf/rakam olmalı") String code,
        @URL String crestUrl,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "#a1b2c3 gibi bir hex renk kodu olmalı") String colorHex,
        @Min(2) @Max(5) Integer starLevel,
        Long leagueTypeId) {
}
