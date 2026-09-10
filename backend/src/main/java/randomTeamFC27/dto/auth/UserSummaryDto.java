package randomTeamFC27.dto.auth;

import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.User;

public record UserSummaryDto(Long id, String displayName, String email, String phone, Role role) {

    public static UserSummaryDto from(User user) {
        return new UserSummaryDto(user.getId(), user.getDisplayName(), user.getEmail(), user.getPhone(), user.getRole());
    }
}
