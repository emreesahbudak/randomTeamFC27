package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.OtpChannel;
import randomTeamFC27.entity.OtpCode;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
            String destination, OtpChannel channel);
}
